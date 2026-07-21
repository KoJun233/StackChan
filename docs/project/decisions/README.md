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
