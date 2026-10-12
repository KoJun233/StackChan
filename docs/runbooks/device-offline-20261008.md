# 2026-10-08：主机地址变化导致设备离线

- 文档状态：COMPLETED
- 日期：2026-10-08（Asia/Shanghai）
- 工作流：部署、固件
- 关联：[部署状态](../project/status/deployment.md)、[固件状态](../project/status/firmware.md)、[原部署](topic-continuity-20261005.md)

## 问题与定位

用户反馈完成配置后机器人不在线。当前服务端 topic-continuity-ac91476/V57 启动正常，health=ok，容器没有 OOM 或重启循环。设备最后旧心跳为 2026-10-06 02:07:38。

主机以太网 IPv4 已从 192.168.1.6 变为 192.168.1.5；新地址健康正常，旧地址三秒请求不可达。被动 COM3 采集 90 秒得到 39 条脱敏诊断，应用持续刷新且 WebSocket 失败后按 60 秒间隔重连，没有观察到 panic/watchdog。这证明设备应用已启动，离线与旧服务地址失效一致。

## 修复

使用既有 `scripts/relocate-device-server.py --port COM3 --server-origin http://192.168.1.5:8080 --apply`，收到严格白名单回执 complete。固件先暂停通信并用原刷新凭据验证新地址属于现有服务，再原子保存地址并软件重启。没有重新配对、轮换刷新凭据、修改 Wi-Fi、刷写固件或清除 NVS；身份、校准和 SR 模型保留。

运行脚本与现行 r3 固件的相关源码一致，修复前通过固件工作流最小五类宿主验证：startup、face-tracking、device-server-relocation、device-transport-maintenance、voice-interaction。地址迁移验证覆盖严格字段解析、保留设备 ID/刷新凭据、五种失败不保存/不重启和通信恢复。此处未修改产品代码，未重新部署服务端或改变模式。

## 实机验证

地址更新后的被动采集为 60 秒、42 条脱敏诊断：21:15:04 一次预期 RTC_SW_CPU_RST；21:15:06 应用启动，21:15:09 Wi-Fi 和 WebSocket 连接。相同设备 ID 上收到新鲜心跳（首次核验年龄 22 秒），固件仍为 device008-ui-r3-b872f2f，motion_disabled/DISABLED，活动 OTA 任务 0。启动日志校准有效、舵机 power=off。有限观察窗口没有异常日志或额外复位，不代表长期稳定性或完整语音验收。

新控制台 `http://192.168.1.5:8080/` 返回 200，新地址 health=ok。原镜像/V57 与数据保持，不发送开启相机或实体运动命令。服务端完整 699/699、前端 150/150 属于原软件交付成绩，本次仅重新执行上述最小验证和运行检查。

## 证据与后续操作

本机证据保存于 `.tools/device-offline-20261008-{serial,relocation,reconnect}.log` 和 `.tools/device-offline-20261008-receipt.json`；串口只记录脱敏白名单，地址更新脚本不打印认证材料。用户源文件、缓存和日志不覆盖或暂存；无外推。

用户刷新新入口确认在线并做一次真实语音交互；待续卡片、跨天接续和十四天自然观察仍按原验收执行。如再次更换主机地址，应核验实际 IPv4、固件 profile 和新地址健康后，通过同一身份保留流程迁移；不得盲目重配对、清 NVS 或降级信任模式。路由器的 DHCP 地址保留未在本次修改。
