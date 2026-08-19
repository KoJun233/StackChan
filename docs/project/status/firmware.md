# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-19
- 当前分支：`codex/int-013-streaming-tts`
- 基准提交：`13987b0`
- 最后验证提交：`29e8c36`
- 当前实机镜像：`bd818f0`

## 当前目标

交付 `INT-013` SCV2 增量响应解析、严格有序 WAV 分段播放、触摸取消后的晚到分片丢弃，并保留对旧服务端 SCV1 的协商回退。

## 已完成

- ESP-SR 本地唤醒、VAD/录音、SCV1 上传、完整 WAV 播放和连续对话状态机。
- 触摸按住说话、阶段取消、播放停止和八状态机械眼/自定义 PNG 表情。
- USB 配网、加密 NVS、设备 JWT/WebSocket、严格命令解析和 `motion_disabled`。
- 内置唤醒模型三槽 OTA、表情资源 A/B 切换和应用固件 `factory/ota_0/ota_1` OTA。
- `device_transport` 任务栈已加固；真实应用 OTA 和普通交互通过。
- 完整合并历史见[里程碑索引](../milestones.md)。

## 正在进行

- 新固件优先请求 SCV2，同时以较低权重接受 SCV1；四字节 magic 决定增量解析或兼容完整缓冲。
- SCV2 只接受 START、1–8 个从 0 连续的 AUDIO 以及匹配计数的 COMPLETE，或唯一 ERROR 终止；每段 WAV 完整校验后立即播放。
- `bd818f0` 首次实机验证确认 SCV2 分段顺序和下一回合正常，但发现播放中触摸未停止当前音频。
- 根因是取消路径先等待 HTTP 客户端取消、最后才请求本地扬声器停止，且只在触摸释放时判定短按；`29e8c36` 改为忙碌阶段按下即取消，并先设置本地停止标志再终止 HTTP。用户复测确认触摸立即停止、晚到分段不再播放且下一回合正常。

## 下一步操作

INT-013 固件成功路径验收已完成；保持 CoreS3 `29e8c36 / motion_disabled / OTA=true`，等待任务分支人工审核与合并，不再执行设备操作。

## 阻塞项

- INT-013 当前无实现或实体验收阻塞。
- 用户明确要求本轮不做应用 OTA 回退演练；自定义八图实体安装和未来多资源包常驻仍需单独设计或逐次授权。

## 关键文件

- `firmware/main/device_transport.c`
- `firmware/main/device_protocol.c`
- `firmware/main/voice_service.c`
- `firmware/main/voice_protocol.c`
- `firmware/main/voice_control.c`
- `firmware/main/firmware_ota.c`
- `firmware/main/expression_pack.c`
- `firmware/sdkconfig.defaults.*`

## 验证命令与最近结果

- `cf26fd7 -> 7e7c55f`：应用下载、摘要校验、写入、重启和启动确认通过，任务为 `INSTALLED`。
- 新镜像跨两个心跳周期稳定，NVS、网络、WakeNet 和 `motion_disabled` 保留。
- 用户确认普通唤醒对话、播放中触摸停止和后续再次对话正常。
- `e8f3035` 为合入任务提交；`7e7c55f` 只作为压缩前实机版本，不作为新分支基线。
- INT-013 ESP32-S3 协议测试 profile 编译通过；候选大小 `0x37880`，最小应用分区余量 93%。
- `test-firmware-voice-stack-budget.ps1` 与 `verify-firmware-voice-stack-budget.ps1` 通过：语音任务 12288 字节拒绝、32768 字节接受，已知本地用量 10656、余量 22112。
- `bd818f0` 通过应用 OTA 从 `7e7c55f` 安装，任务为 `INSTALLED`；三个心跳周期稳定、原设备身份直接重连、`motion_disabled / OTA=true`，服务端最近日志无错误。
- 用户确认 SCV2 分段播放和后续对话正常，但播放中触摸未停止；修复候选 `29e8c36` 的协议 profile、语音栈预算和 LAN HTTP Quad 构建通过，应用大小 `0x14f3a0`，SHA-256 为 `D87B60D9C5988D37153928BD746A04284BC26241B32133FBF1D7E7A7078B2AD2`。
- `29e8c36` 通过应用 OTA 从 `bd818f0` 安装，任务为 `INSTALLED`，无失败码；设备继续以 `motion_disabled / OTA=true` 上报。用户确认播放中触摸立即停止、后续分段被丢弃且下一回合正常。

## 相关设计、计划和决策

- [当前任务清单](../todo.md)
- [设备协议 v1](../../protocol/device-v1.md)
- [0004：LAN HTTP 仅限开发固件](../decisions/0004-lan-http-development-only.md)
- [0018：触摸取消](../decisions/0018-touch-control-and-voice-turn-cancellation.md)
- [0027：有界连续对话](../decisions/0027-bounded-continuous-conversation.md)
- [0031：应用固件 OTA](../decisions/0031-safe-application-firmware-ota-and-health-center.md)
- [0037：有序分段语音播放](../decisions/0037-ordered-streaming-voice-playback.md)
- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)

## 安全与兼容性约束

- 未经用户逐次确认设备、端口、profile、提交和 NVS 策略，不连接 COM3、不刷写、不发起 OTA。
- 设备继续拒绝任意 URL、跨源制品、未知命令字段和运动启用。
- 不记录 Wi-Fi 密码、配对码、设备 Token、音频、转写或供应商密钥。
