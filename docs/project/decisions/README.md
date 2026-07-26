# 架构决策记录

本目录保存会跨会话持续生效、且不应在没有明确决策的情况下被改变的架构选择。

- `PROPOSED`：正在讨论，尚未成为实现约束。
- `ACCEPTED`：已由用户确认，当前实现与后续工作必须遵守。
- `SUPERSEDED`：已被新的 ADR 替代；必须链接到替代记录。
- `REJECTED`：已评估但未采用；保留原因，不能作为当前实现依据。

## 决策索引

| ADR | 状态 | 决策 |
| --- | --- | --- |
| [0001](0001-fantastic-admin-frontend.md) | ACCEPTED | 浏览器管理端使用 Vue 3 与 Fantastic-admin。 |
| [0002](0002-java-spring-ai-alibaba-backend.md) | ACCEPTED | 后端保持 Java 21 / Spring Boot，并使用 Spring AI Alibaba-compatible 集成调用 LLM。 |
| [0003](0003-configurable-llm-provider.md) | ACCEPTED | LLM 提供方配置由管理员管理，秘密加密保存且不返回明文。 |
| [0004](0004-lan-http-development-only.md) | ACCEPTED | `http://LAN_IP:8080` 与 `ws://` 仅限显式编译的 LAN 开发固件/profile。 |
| [0005](0005-secure-production-boundary.md) | ACCEPTED | 生产环境经可信代理保持 HTTPS-only，LAN 与生产 Compose 不可组合。 |
| [0006](0006-chat-retry-idempotency.md) | ACCEPTED | 聊天重试按 `clientMessageId` 对账、重放并保持幂等。 |
| [0007](0007-device-voice-and-durable-reminders.md) | ACCEPTED | 设备本地唤醒，服务端完成 ASR/TTS，并用持久调度与播放 ACK 交付提醒。 |
| [0008](0008-browser-usb-provisioning-and-screen-off-idle.md) | SUPERSEDED | 管理后台通过物理 USB 直传 Wi-Fi 配置，空闲屏保关闭背光且不周期性重绘；由 0010 替代。 |
| [0009](0009-native-dashscope-speech-adapter.md) | ACCEPTED | 服务端显式适配阿里云百炼 Fun-ASR WebSocket 与 Qwen-Audio-TTS 原生协议。 |
| [0010](0010-low-brightness-local-pupil-screensaver.md) | ACCEPTED | 保留 USB 配网边界，空闲屏保改为低亮度、小区域、低频移动瞳孔。 |
| [0011](0011-explicit-speech-access-modes.md) | ACCEPTED | ASR/TTS 分别显式选择实时 WebSocket 或非实时 HTTP，模型名不参与路由且不自动回退。 |
| [0012](0012-configurable-device-voice-detection.md) | ACCEPTED | 管理员配置机器人本地唤醒灵敏度和录音能量阈值，设备重连补发并安全重建 WakeNet。 |
| [0013](0013-privacy-safe-voice-turn-diagnostics.md) | ACCEPTED | 语音回合以同一 ID 关联设备与服务端阶段，只保存短期隐私安全诊断元数据。 |
| [0014](0014-packaged-custom-wake-word-model.md) | SUPERSEDED | 自定义唤醒词使用离线生成并随固件打包；由 0015 的运行时模型 OTA 替代。 |
| [0015](0015-runtime-wake-model-generation-and-ota.md) | SUPERSEDED | 网页在线生成或上传本地 WakeNet 包；由 0016 的固定内置模型目录替代。 |
| [0016](0016-built-in-esp-sr-wake-model-catalog.md) | ACCEPTED | 用户只选择 ESP-SR 2.4.6 内置唤醒词，服务端可信打包并通过三槽 OTA 自动启用或回退。 |
| [0017](0017-local-user-visible-interaction-states.md) | ACCEPTED | 设备在本地把交互阶段和连接状态映射为可区分、可恢复的可见状态。 |
| [0018](0018-touch-control-and-voice-turn-cancellation.md) | ACCEPTED | 触摸只产生本地控制事件，语音回合以同一 ID 幂等取消并抑制晚到回复。 |
