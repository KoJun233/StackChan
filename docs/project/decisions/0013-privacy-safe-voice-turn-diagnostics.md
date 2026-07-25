# 0013：语音回合使用隐私安全的阶段诊断

- 状态：ACCEPTED
- 日期：2026-07-26
- 工作流：服务端、前端、固件

## 背景

现有机器人已经能在本地唤醒、录音、上传语音，并由服务端完成 ASR、LLM 和 TTS 后播放回复。但一次失败只能从分散日志推断发生在录音、网络、ASR、LLM、TTS、播放还是麦克风恢复阶段，用户也无法在管理端看到机器人当前处理到了哪里。

语音内容、识别文本和模型回复都具有高隐私敏感度。诊断能力不能以长期保存原始音频、完整文本、供应商响应或认证载荷为代价。

## 决策

- 每次本地唤醒创建一个随机 UUID `turn_id`。新固件在语音上传请求的 `X-StackChan-Turn-Id` 头和 WebSocket 阶段事件中复用该 ID；旧固件不发送时由服务端生成 ID。
- 设备只上报严格白名单阶段：`WAKE_DETECTED`、`LISTENING`、`SPEECH_CAPTURED`、`UPLOAD_STARTED`、`PLAYBACK_STARTED`、`PLAYBACK_COMPLETED`、`LISTENING_RESUMED` 和 `FAILED`。
- 服务端记录自身可直接确认的阶段：`REQUEST_RECEIVED`、`ASR_COMPLETED`、`LLM_COMPLETED`、`TTS_COMPLETED` 和 `FAILED`。
- 设备阶段事件只允许 `turn_id`、共享 WebSocket `sequence`、阶段、相对唤醒时刻的 `elapsed_ms`，以及失败事件的白名单 `failure_code`。不接受自由文本或额外字段。
- 失败码只表达可操作类别：无语音、离线、内存不足、上传失败、响应无效、播放失败、麦克风恢复失败、ASR 不可用、LLM 不可用、TTS 不可用和内部错误；不得包含异常正文或供应商响应。
- PostgreSQL 仅保存设备 ID、回合 ID、状态、阶段来源、服务端接收时间、设备相对耗时和安全失败码。禁止在诊断表中保存原始音频、识别文本、模型回复、供应商响应、API key、JWT 或刷新令牌。
- 诊断数据保留 7 天，由服务端定时删除过期回合并级联删除阶段事件。
- 管理端在设备总览下展示最近回合时间线、阶段耗时与安全失败类别。接口不返回对话内容。
- 成功回合在设备确认麦克风恢复并上报 `LISTENING_RESUMED` 后标记 `COMPLETED`；服务端完成 TTS 后先标记 `RESPONSE_READY`。这样旧固件仍能完成语音请求，但不会被误报为已完成播放。

## 兼容与发布顺序

- 服务端先发布：继续接受没有 `X-StackChan-Turn-Id` 的旧上传，同时开始接受新的严格阶段事件。
- 管理端随后发布：没有阶段数据时显示空状态，不影响设备列表和安全停止。
- 固件最后发布：新事件属于附加诊断；事件队列满、WebSocket 离线或发送失败不得阻断本地安全状态和既有语音主流程。
- 服务端未知或非法阶段事件仍按既有严格协议返回 `invalid_event`；新固件不得向尚未升级的服务端发送新事件。

## 原因

由设备生成贯穿本地与云端的同一回合 ID，才能把唤醒、录音、服务端处理、播放和恢复关联起来。设备使用单调相对耗时而不是未经校时的墙上时间，可避免时钟漂移，同时让服务端接收时间保留审计顺序。把内容数据排除在诊断模型之外，可以在提升可维护性的同时保持最小化收集原则。

## 影响

- Flyway 增加语音回合与阶段事件表以及必要索引。
- 设备 WebSocket v1 增加严格的 `voice_turn_stage` 入站事件；既有心跳和命令确认保持不变。
- 语音上传增加可选的 `X-StackChan-Turn-Id` 请求头；SCV1 响应结构保持不变。
- 固件增加有界、非阻塞诊断事件队列；不会刷写设备、启用运动或改变 LAN HTTP / 生产 HTTPS 边界。

## 替代关系

补充 [0007](0007-device-voice-and-durable-reminders.md) 的语音回合协议与 [0012](0012-configurable-device-voice-detection.md) 的安全诊断约束；不改变 [0011](0011-explicit-speech-access-modes.md) 的 ASR/TTS 路由决策。
