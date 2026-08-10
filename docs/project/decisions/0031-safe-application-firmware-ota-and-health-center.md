# 安全的应用固件 OTA 与健康中心

- 状态：ACCEPTED
- 日期：2026-08-07
- 工作流：全局
- 相关提交：`9377b8d`

## 背景

CoreS3 已有 `factory`、`ota_0`、`ota_1` 和 `otadata` 分区，但此前只能经 USB 完整刷写应用。后续升级需要保留设备 NVS 身份和用户设置，同时必须兼容仍未实现应用 OTA 的旧固件。管理员也需要一个不暴露凭据、对话正文或供应商完整响应的运行健康视图。

## 决策

- 管理员只能手工导入 ESP-IDF 应用 `.bin` 并手工选择单台在线设备发起升级；服务端验证 ESP 镜像头、`stackchan_firmware` 项目名、嵌入版本、大小和 SHA-256，按摘要不可变保存。
- 只有心跳显式声明 `application_ota_supported=true` 的新固件可接收升级。旧五/六字段心跳继续被接受并保持能力为 false，服务端不得向旧固件发送 OTA 命令。
- 命令只携带任务 ID、版本、摘要和大小，不携带 URL。设备从已配置 server origin 派生固定同源 artifact 路径，并使用现有设备 Bearer token 下载；重定向、跨源地址和额外协议字段均拒绝。
- 设备只写入 `esp_ota_get_next_update_partition()` 返回的非活动应用槽，流式校验长度与 SHA-256，并再次验证目标分区的项目名和版本。NVS、唤醒模型与表情资源分区都不是应用 OTA 写入目标。
- 所有发布 profile 开启 ESP-IDF bootloader application rollback。设备把待确认任务写入独立 NVS namespace；新镜像只有在 CoreS3 硬件、WakeNet、传输和配网任务均成功初始化后才标记有效。30 秒内未确认则重启，交给 bootloader 回退。安装或回退结果持久化并在每次 WebSocket 重连时幂等上报。
- 健康中心只显示服务端版本、Flyway 版本、设备版本/RSSI/在线与 OTA 能力、供应商最近一次人工连通测试、备份状态、待处理计数和近期安全失败码。它不主动探测供应商，也不返回秘密、URL、音频、转写、回复正文或完整异常载荷。
- 首次把旧设备升级为 OTA-capable 固件仍需逐次获得设备、端口、profile、提交和 NVS 保留方式的明确刷写授权。应用 OTA 不扩大该 USB 授权。

## 原因

固定同源下载和双端摘要验证避免服务端命令成为任意下载入口；显式能力位让 server-first 发布对旧固件安全；A/B 应用槽、bootloader rollback 和初始化后健康确认共同覆盖下载损坏、启动崩溃和初始化失败。人工导入与逐设备版本复述降低误刷风险，同时保留可审计任务状态。

## 影响

- 服务端数据库新增固件发布与升级任务，并在设备记录上增加 RSSI 和应用 OTA 能力。
- 新固件必须以包含 rollback bootloader 配置的完整 USB 镜像做一次引导；此后应用升级只写非活动应用槽并保留 NVS。
- 健康中心不能替代实体启动、音频和 `motion_disabled` 验收；回退演练和首次 USB 引导仍按 runbook 执行。
- 生产部署继续保持 HTTPS-only；LAN HTTP OTA 仅限可信局域网开发。

## 替代关系

无。
