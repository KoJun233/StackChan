# 固件工作流

- 状态：STABLE
- 最后更新：2026-08-13
- 当前分支：`codex/evt-002-interactive-notifications`
- 基准提交：`82fdde3`
- 最后验证提交：`82fdde3`
- 当前实机镜像：`7e7c55f`

## 当前目标

维持已验收的 CoreS3 固件运行态。EVT-002 只在服务端记录用户回执并复用现有语音确认链路，不修改固件协议或镜像。

## 已完成

- ESP-SR 本地唤醒、VAD/录音、SCV1 上传、完整 WAV 播放和连续对话状态机。
- 触摸按住说话、阶段取消、播放停止和八状态机械眼/自定义 PNG 表情。
- USB 配网、加密 NVS、设备 JWT/WebSocket、严格命令解析和 `motion_disabled`。
- 内置唤醒模型三槽 OTA、表情资源 A/B 切换和应用固件 `factory/ota_0/ota_1` OTA。
- `device_transport` 任务栈已加固；真实应用 OTA 和普通交互通过。
- 完整合并历史见[里程碑索引](../milestones.md)。

## 正在进行

没有固件修改。实机继续运行 `7e7c55f / motion_disabled / OTA=true`，最近应用 OTA 为 `INSTALLED`。

## 下一步操作

保持设备不变。EVT-002 如获授权部署，可用现有语音回合测试“知道了 / 稍后提醒 / 已完成”的提案与确认，不需要连接 COM3 或刷写固件。

## 阻塞项

- 当前软件路线没有固件阻塞。
- 应用 OTA 回退演练、自定义八图实体安装和未来多资源包常驻仍需单独设计或逐次授权。

## 关键文件

- `firmware/main/device_transport.c`
- `firmware/main/device_protocol.c`
- `firmware/main/voice_service.c`
- `firmware/main/firmware_ota.c`
- `firmware/main/expression_pack.c`
- `firmware/sdkconfig.defaults.*`

## 验证命令与最近结果

- `cf26fd7 -> 7e7c55f`：应用下载、摘要校验、写入、重启和启动确认通过，任务为 `INSTALLED`。
- 新镜像跨两个心跳周期稳定，NVS、网络、WakeNet 和 `motion_disabled` 保留。
- 用户确认普通唤醒对话、播放中触摸停止和后续再次对话正常。
- `e8f3035` 为合入任务提交；`7e7c55f` 只作为压缩前实机版本，不作为新分支基线。

## 相关设计、计划和决策

- [当前任务清单](../todo.md)
- [设备协议 v1](../../protocol/device-v1.md)
- [0004：LAN HTTP 仅限开发固件](../decisions/0004-lan-http-development-only.md)
- [0018：触摸取消](../decisions/0018-touch-control-and-voice-turn-cancellation.md)
- [0027：有界连续对话](../decisions/0027-bounded-continuous-conversation.md)
- [0031：应用固件 OTA](../decisions/0031-safe-application-firmware-ota-and-health-center.md)
- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)

## 安全与兼容性约束

- 未经用户逐次确认设备、端口、profile、提交和 NVS 策略，不连接 COM3、不刷写、不发起 OTA。
- 设备继续拒绝任意 URL、跨源制品、未知命令字段和运动启用。
- 不记录 Wi-Fi 密码、配对码、设备 Token、音频、转写或供应商密钥。
