# 0012：机器人本地唤醒与录音判定参数由管理员配置

- 状态：ACCEPTED
- 日期：2026-07-21
- 工作流：服务端、前端、固件

## 背景

CoreS3 实体测试确认两个独立问题：WakeNet 普通模式的唤醒命中率偏低，录音开始能量阈值 `700` 对当前麦克风输入偏高；语音失败后调用 ESP-SR `wakenet->clean(model)` 还会在 `dl_convq_queue_bzero` 中触发 `LoadProhibited` 并重启设备。固定参数既不适合不同环境，也不利于根据非敏感能量日志继续校准。

## 决策

- `speech_provider_settings` 同时保存设备本地语音参数：`wake_sensitivity`、`speech_start_threshold` 和 `speech_silence_threshold`。
- 唤醒灵敏度只允许 `NORMAL` 或 `SENSITIVE`。普通模式使用 WakeNet `DET_MODE_90` 的模型默认阈值；灵敏模式使用 `DET_MODE_95` 并把单唤醒词触发阈值显式设为 `0.50`，以补偿 CoreS3 当前麦克风输入与自定义 `Hi, Stack Chan` 模型预设阈值组合下的低命中率。
- 默认使用 `SENSITIVE`，开始说话阈值为 `350`，静音阈值为 `200`。
- 开始说话阈值限制在 `100..5000`，静音阈值限制在 `50..4000`，且静音阈值必须小于开始说话阈值。
- 管理员保存语音配置后，服务端通过严格的 `configure_voice_detection` WebSocket 命令向在线设备广播；设备每次重新连接时服务端再次发送当前配置。
- 固件只在语音任务内部切换 WakeNet 模型。每个语音回合结束后销毁并重新创建模型，不再调用已在实体设备上确认会崩溃的 `clean()`。
- 固件只记录采集样本数、峰值平均能量和使用的阈值，不记录音频内容或识别文本。
- 固件启动或重建 WakeNet 模型时记录当前触发阈值的千分值，便于区分后台灵敏度是否真正生效；该日志不包含音频或识别内容。
- 扬声器播放结束后必须确认麦克风重新启动；恢复失败时该轮返回失败并记录非秘密错误。
- WakeNet 短帧的完成检测不等待显示/UI 互斥锁。录音请求入队后，高优先级语音任务先阻塞一个 FreeRTOS tick，让 M5Unified 的低优先级麦克风任务处理请求；重新运行时若录音状态已归零，应视为短帧已经成功完成，而不是要求必须观察到极短的“正在录音”中间状态。后续仍在录音时按 tick 轮询并保留超时上限，既避免误报 `ESP_ERR_TIMEOUT`，也防止挤占 CPU0 idle task。

## 原因

动态配置允许不同房间、说话距离和环境噪声使用合适参数。重连补发避免把配置可靠性依赖于一次在线广播。由语音任务独占 WakeNet 生命周期可避免 WebSocket 线程直接操作模型，同时绕开 ESP-SR 当前模型的 `clean()` 空指针崩溃。

## 影响

- Flyway 增加 v10 迁移；旧配置自动获得新的安全默认值。
- 现有“语音配置”页面增加一个本地唤醒与录音区域，不新增菜单或路由。
- 新固件继续保留离线默认值；旧固件会忽略无法识别的新命令，升级期间不会改变运动安全状态。
- 参数命令仍携带 `command_id` 并使用既有 `command_ack`；该确认不属于提醒交付，不改变提醒状态。
- `motion_disabled`、设备 JWT、LAN HTTP 开发边界和生产 HTTPS-only 边界均保持不变。

## 来源

- CoreS3 实体复现：8 次唤醒约命中 2 次，唤醒后录音返回 `ESP_ERR_NOT_FOUND`。
- `b4876fb` ELF 回溯：`voice_task` → `model_clean` → `dl_convq_queue_bzero`，异常地址 `0x10`。
- 用户明确要求修复崩溃、提高唤醒灵敏度并把唤醒模式与录音阈值放到管理页面灵活配置。

## 替代关系

补充 [0007](0007-device-voice-and-durable-reminders.md) 的固定本地唤醒与录音判定参数；不改变 ASR/TTS 协议路由决策 [0011](0011-explicit-speech-access-modes.md)。
