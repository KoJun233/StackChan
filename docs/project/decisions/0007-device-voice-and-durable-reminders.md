# 0007：设备本地唤醒、服务端语音适配与持久提醒

- 状态：ACCEPTED
- 日期：2026-07-19
- 工作流：服务端、前端、固件
- 相关提交：`2384587`、`ac7c171`、`cbd5095`、`7fb9575`、`731a68c`、`b6dbaf8`

## 背景

StackChan 需要语音唤醒、语音识别、语音回复、空闲屏保和指定时间主动播报，同时必须保持设备凭据、密钥和运动安全边界。

## 决策

设备使用 ESP-SR 的本地 “Hi, Stack Chan” WakeNet 模型，只在唤醒后上传有界 WAV。服务端以独立加密配置调用 OpenAI-compatible ASR/TTS，并复用现有 LLM 会话。提醒持久化到 PostgreSQL，由确定性调度器向在线设备发送固定同源的 `speak_reminder` 命令；设备播放完成后通过 ACK 确认。

## 原因

本地唤醒避免持续上传麦克风数据；服务端适配器保护供应商密钥并保持可配置性；持久提醒和播放 ACK 可避免服务重启或网络抖动造成静默丢失。

## 影响

设备音频 HTTP 接口必须验证设备 JWT，语音和提醒音频只能使用固定同源路径。提醒调度不得绕过静默/设备在线边界，固件始终保留 `motion_disabled`。未来如改为持续云端监听、任意音频 URL 或非持久调度，必须有用户批准的新 superseding ADR。

## 来源

- [设备协议 v1](../../protocol/device-v1.md)
- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)

## 替代关系

无。
