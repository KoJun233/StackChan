# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-23
- 当前分支：`codex/media-002-expression-experience`
- 基准提交：`19bd459`
- 最后验证提交：`41b8827`
- 当前实机镜像：`41b8827-perf9`

## 当前目标

在不改变运动安全、语音和 OTA 边界的前提下，把 CoreS3 硬件与显示实现完整迁移到官方 BSP + LVGL 9.4，用 160×160 原生动态球体替代默认 10 FPS 机械眼，并保留静态 PNG 兼容模式。

## 已完成

- ESP-SR 本地唤醒、VAD/录音、SCV1 上传、完整 WAV 播放和连续对话状态机。
- 触摸按住说话、阶段取消、播放停止和八状态机械眼/自定义 PNG 表情。
- USB 配网、加密 NVS、设备 JWT/WebSocket、严格命令解析和 `motion_disabled`。
- 内置唤醒模型三槽 OTA、表情资源 A/B 切换和应用固件 `factory/ota_0/ota_1` OTA。
- `device_transport` 任务栈已加固；真实应用 OTA 和普通交互通过。
- 完整合并历史见[里程碑索引](../milestones.md)。

## 已完成的 MEDIA-002B/C

- 统一 UI 时钟绘制 160×160 RGB565 局部画布，状态切换只更新目标姿态；系统/情绪/物理层使用 180–800 ms 平滑插值，待机眨眼、呼吸和说话脉动连续计算。
- 默认目标 60 FPS；音频繁忙降至 30，绘制或锁超预算逐级降至 30/20，稳定十秒后恢复。心跳上报实际/目标 FPS、耗时、丢帧、堆水位、活动层和原因。
- 完整覆盖 12 种角色情绪、8 种系统/交互表现、6 种生命周期/物理行为；错误/离线/更新固定颜色且优先级最高。
- 触摸触发喜爱，IMU 加速度突变触发摇晃眩晕。CoreS3 无独立接近传感器，明确上报 `proximity_supported=false`。
- 静态 PNG 启用后仍保持原 A/B 资源包和全屏解码路径，并在诊断中标记当前非动态渲染。

## 正在进行

- 官方 `m5stack_core_s3` BSP 4.0.0、`esp_lvgl_port` 2.9.0 和 LVGL 9.4 已替代 M5Unified/M5GFX；显示、触摸、AW88298、ES7210 与 BMI270 均收敛到官方 BSP。
- UI 使用 `esp_timer` 微秒级唤醒、160×160 局部失效和 24 行双 DMA 缓冲。四层球体底层最多 30 Hz 更新，眼睛、轨道和前景效果仍可按 60 Hz 更新；每五秒记录 LVGL flush 次数、像素和等待时间用于诊断。
- `41b8827-perf9` 已由用户通过保留 NVS 的应用 OTA 安装。固定 60 FPS 待机实测约 55 FPS，场景更新约 1041 μs、LVGL 刷新约 11077 μs、锁等待约 462 μs、音频 underrun 0、最低空闲堆约 7.56 MiB；用户确认画面流畅度正常。
- 40 MHz SPI 下 160×160 RGB565 的理论传输下限约 10.24 ms，实测 LVGL 刷新约 11.1 ms 已接近总线边界；本任务不再以未验证 SPI 超频、扩大 DMA 缓冲或全根节点失效换取名义 60 FPS。
- 官方音量 API 的 `0..100` 是用户刻度，不是 dB 百分比。自定义曲线把旧 M5Unified 的平方 PCM 音量换算为 dB，并加入 CoreS3 BSP 约 `+11.39 dB` 固定模拟链路增益；`0` 保持静音，`100` 对应迁移前满量程。用户确认 `perf9` 回答音量恢复正常。
- `MEDIA-002D` 固件实现和实机调校已完成，当前仅剩双 profile、任务栈预算和文档的最终回归。

## 下一步操作

保持 `perf9` 显示与音量实现不变，等待交付整理。PNG 兼容、IMU 摇晃和三十分钟稳定性可按 runbook 继续补充记录，但不重复刷写已验收镜像。

## 阻塞项

- 自动化无代码阻塞。60 FPS 是调度上限而不是硬件承诺，稳定真机结果约为 55 FPS；接近行为受硬件能力限制，当前不会触发。
- 用户明确要求本轮不做应用 OTA 回退演练；自定义八图实体安装和未来多资源包常驻仍需单独设计或逐次授权。

## 关键文件

- `firmware/main/device_transport.c`
- `firmware/main/device_protocol.c`
- `firmware/main/voice_service.c`
- `firmware/main/voice_protocol.c`
- `firmware/main/voice_control.c`
- `firmware/main/firmware_ota.c`
- `firmware/main/expression_pack.c`
- `firmware/main/companion_hardware.cpp`
- `firmware/main/idf_component.yml`
- `firmware/dependencies.lock`
- `firmware/sdkconfig.defaults.*`

## 验证命令与最近结果

- 官方 BSP + LVGL 迁移使用 ESP-IDF 5.4.4/GCC 14.2 完整构建通过；依赖锁定 `m5stack_core_s3 4.0.0`、`esp_lvgl_port 2.9.0`、`lvgl 9.4.0`，不再包含 M5Unified/M5GFX。
- 新 protocol profile 构建通过：应用大小 `0x397b0`，最小应用分区余量 93%；新增连续帧率、诊断与硬件迁移代码均已编译，LVGL examples 未构建。
- 性能修正后的 protocol profile 构建通过：应用大小 `0x397b0`，最小应用分区余量 93%。独立 LAN HTTP Quad 测试版本 `41b8827-perf1` 构建通过：应用大小 `0x1819a0`，最小 3 MiB 应用分区余量 `0x17e660`（50%）；bootloader 大小 `0x57b0`、余量 31%。
- 最终实机版本 `41b8827-perf9` 的 LAN HTTP Quad 构建通过：制品 1,581,488 字节，SHA-256 `D5911FE9CAF747799DFE7C1D3D5745611D7B23AA0114D210EAD4973931761699`；用户确认画面流畅度和音量正常。
- 2026-08-23 最终 protocol profile 完整重建通过：应用大小 `0x397b0`（235,440 字节）、最小应用分区余量 93%，SHA-256 `78D18EE2A50815270BE3A9AA2DBB50C7B454632FA10AF920027C1F763578E0F0`。LAN HTTP Quad profile 复核为 `0x1821b0`、余量 50%；三组任务栈预算全部通过。

- `cf26fd7 -> 7e7c55f`：应用下载、摘要校验、写入、重启和启动确认通过，任务为 `INSTALLED`。
- 新镜像跨两个心跳周期稳定，NVS、网络、WakeNet 和 `motion_disabled` 保留。
- 用户确认普通唤醒对话、播放中触摸停止和后续再次对话正常。
- `e8f3035` 为合入任务提交；`7e7c55f` 只作为压缩前实机版本，不作为新分支基线。
- INT-013 ESP32-S3 协议测试 profile 编译通过；候选大小 `0x37880`，最小应用分区余量 93%。
- `test-firmware-voice-stack-budget.ps1` 与 `verify-firmware-voice-stack-budget.ps1` 通过：语音任务 12288 字节拒绝、32768 字节接受，已知本地用量 10656、余量 22112。
- `bd818f0` 通过应用 OTA 从 `7e7c55f` 安装，任务为 `INSTALLED`；三个心跳周期稳定、原设备身份直接重连、`motion_disabled / OTA=true`，服务端最近日志无错误。
- 用户确认 SCV2 分段播放和后续对话正常，但播放中触摸未停止；修复候选 `29e8c36` 的协议 profile、语音栈预算和 LAN HTTP Quad 构建通过，应用大小 `0x14f3a0`，SHA-256 为 `D87B60D9C5988D37153928BD746A04284BC26241B32133FBF1D7E7A7078B2AD2`。
- `29e8c36` 通过应用 OTA 从 `bd818f0` 安装，任务为 `INSTALLED`，无失败码；设备继续以 `motion_disabled / OTA=true` 上报。用户确认播放中触摸立即停止、后续分段被丢弃且下一回合正常。
- MEDIA-002 ESP32-S3 protocol profile 编译通过；候选大小 `0x37880`、最小应用分区余量 93%，新增动态心跳、严格表情命令和状态优先级测试均已编译。
- MEDIA-002 LAN HTTP Quad profile 编译通过；候选大小 `0x150900`、最小应用分区余量 56%。制品尚未部署或安装，提交绑定摘要在最终提交后生成。
- 首个动态候选 `d65811d` 在 UI 任务持续 SPI/I2C 工作时触发 Core 0 task watchdog，设备自动回退；未清除 NVS。`759a91f` 将 UI 降至优先级 2、固定 Core 1、增加启动宽限并限制 IMU 轮询后成功安装，任务为 `INSTALLED`，设备身份、网络、WakeNet、OTA 和 `motion_disabled` 保留。
- `759a91f` 实机稳定上报动态诊断：目标 30、实际约 19 FPS，绘制约 16460 us、传输约 11541 us、最低空闲堆约 7.74 MB、降帧原因为 `DRAW_BUDGET`。当前候选改为按帧开始时间计算下一截止时间，并将触摸采样与 2 ms UI 调度节拍分离。
- 新视觉候选 protocol profile 编译通过：`0x37880`、最小应用分区余量 93%；LAN HTTP Quad profile 编译通过：`0x1513f0`、最小应用分区余量 56%。尚未应用 OTA。

## 相关设计、计划和决策

- [当前任务清单](../todo.md)
- [设备协议 v1](../../protocol/device-v1.md)
- [0004：LAN HTTP 仅限开发固件](../decisions/0004-lan-http-development-only.md)
- [0018：触摸取消](../decisions/0018-touch-control-and-voice-turn-cancellation.md)
- [0027：有界连续对话](../decisions/0027-bounded-continuous-conversation.md)
- [0031：应用固件 OTA](../decisions/0031-safe-application-firmware-ota-and-health-center.md)
- [0037：有序分段语音播放](../decisions/0037-ordered-streaming-voice-playback.md)
- [0038：分层动态球形表情与兼容资源包](../decisions/0038-layered-expression-rendering-and-resident-appearance-catalog.md)
- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)
- [动态球形表情实机验收](../../runbooks/dynamic-expression-smoke-test.md)

## 安全与兼容性约束

- 未经用户逐次确认设备、端口、profile、提交和 NVS 策略，不连接 COM3、不刷写、不发起 OTA。
- 设备继续拒绝任意 URL、跨源制品、未知命令字段和运动启用。
- 不记录 Wi-Fi 密码、配对码、设备 Token、音频、转写或供应商密钥。
