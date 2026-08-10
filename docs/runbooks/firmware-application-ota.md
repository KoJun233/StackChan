# 应用固件 OTA 与回退 Runbook

本流程只升级 ESP-IDF application 分区。它不擦除或改写 NVS、唤醒模型和表情资源分区，也不授权连接串口或完整刷写。

## 发布顺序

1. 先备份运行数据库并完成隔离恢复检查。
2. 只发布新 server，确认健康接口、管理网页和 Flyway V27 正常。
3. 用现有旧固件验证 WebSocket 自动重连、心跳新鲜且健康中心显示“需 USB 引导”；此时不得生成固件升级任务。
4. 只有用户逐次明确确认设备、串口、profile、提交和“保留 NVS”后，才可按[实体设备冒烟测试](physical-device-smoke-test.md)完整刷入一次 OTA-capable 镜像。生产使用 HTTPS profile；LAN HTTP Quad 仅用于可信局域网开发。
5. 引导后确认设备心跳版本、RSSI、`applicationOtaSupported=true` 和 `motion_disabled`，再进入应用 OTA 验证。

## 构建与导入

从干净任务提交构建与目标传输模式一致的 Quad profile。LAN 开发示例：

```powershell
Push-Location firmware
idf.py -B build-lan-http-quad -D IDF_TARGET=esp32s3 -D SDKCONFIG="sdkconfig.profile-lan-http-quad" -D SDKCONFIG_DEFAULTS="sdkconfig.defaults;sdkconfig.lan-http.defaults" build
if ($LASTEXITCODE -ne 0) { throw 'LAN HTTP Quad firmware build failed' }
Pop-Location
```

在“机器人设备 → 健康中心”选择生成的 `stackchan_firmware.bin`。输入 ESP app descriptor 中的精确版本；服务端会再次验证项目名、嵌入版本、大小和 SHA-256。不要导入 `bootloader.bin`、`partition-table.bin` 或完整 merged image。

## 单设备升级

1. 确认设备在线、命令通道已连接、OTA 能力为 true，且没有活动升级任务。
2. 选择目标发布，并在确认框逐字输入页面显示的当前固件版本。
3. 发起后观察任务 `READY -> INSTALLING`。`command_ack accepted=true` 只表示 artifact 已下载、校验、写入非活动槽并选为下次启动，不表示新镜像健康。
4. 等待设备重启。健康启动应上报 `INSTALLED`，心跳显示目标版本且持续跨过至少两个心跳周期；确认 WakeNet、普通语音、触摸取消和 `motion_disabled`。
5. 若新镜像在 30 秒内未完成初始化确认，bootloader 应恢复旧应用；设备随后上报 `ROLLED_BACK`。保留任务失败码和版本即可，不记录 token、下载 URL、音频、转写、回复正文或完整协议载荷。

## 回退演练

回退演练需要单独的实体刷写/OTA 授权。使用可明确阻止初始化健康确认的测试构建，不得把故障开关带入发布 profile。验收点为：新槽进入 pending verify、30 秒内未确认、设备重启到上一应用版本、NVS 身份仍有效、WebSocket 自动恢复、任务最终为 `ROLLED_BACK`、运动始终保持禁用。

## 故障处理

- 页面显示“需 USB 引导”：这是旧固件兼容行为，不能从服务端强行开启能力位。
- 任务停在 `READY`：检查设备在线与命令通道；不要创建第二个活动任务。
- 任务进入 `INSTALLING` 但 ACK 为空，同时设备重启后仍报告旧版本：不要等待页面、刷新页面或反复重试。页面刷新不参与设备任务生命周期；先停止活动任务并检查脱敏串口。若出现 `device_transport` 栈溢出，旧安装器不能通过 OTA 自我修复，必须取得新的精确授权后完整 USB 刷入修复版本并保留 NVS。
- `INSTALLING` 超过 15 分钟：服务端会把已接受命令标为安全超时失败；先核对设备实际版本，再决定是否重试。
- artifact 返回 404：确认请求设备拥有该任务且任务仍为 `READY/INSTALLING`；不要改成公开下载。
- 新镜像回退：保留旧槽运行，先修复构建或初始化问题；不要清除 NVS 作为首选恢复手段。

任何生产 OTA 都必须保持 HTTPS/WSS 边界。LAN HTTP 下的设备 token 可被同网段观察，因此只可用于显式开发环境。
