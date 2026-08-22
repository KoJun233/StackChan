# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-23
- 当前分支：`codex/media-003-eaf-emote-evaluation`
- 基准提交：`67e5ad3`
- 最后验证提交：`67e5ad3`
- 当前实机镜像：`71868da`

## 当前目标

在不改变已验收原生渲染、运动安全、语音和 OTA 边界的前提下，比较 native、官方 EAF player 与 `esp_emote_gfx`，判断有限生命周期动画是否值得增加一种受控资源格式。

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

- 默认 `STACKCHAN_MEDIA003_BACKEND=native`，不包含候选组件；ESP-IDF 5.4.4 完整固件仍为 1,581,488 字节，与 MEDIA-002 最终稳定制品同尺寸。
- EAF profile 固定官方 `espressif/esp_lv_eaf_player` 0.3.0、LVGL 9.4 和 ESP-IDF 5.5.5，使用独立依赖锁。项目自有 18 帧 RLE 素材只在约 1.8 秒开机窗口显示，之后隐藏并恢复 native。
- 自有 EAF 为 53,854 字节；同口径 ESP-IDF 5.5.5 native 为 1,605,088 字节，EAF RLE-only 为 1,669,504 字节，净增 64,416 字节，最小 3 MiB 应用分区仍有 47% 余量。
- Emote profile 固定 `espressif2022/esp_emote_gfx` 3.0.5，只执行 init/deinit 并记录初始化时间和保留堆，不注册 flush、不接管显示。其 1,615,776 字节仅代表 lifecycle 链接成本，不是完整渲染性能。
- 候选依赖、sdkconfig 和生成器全部位于 `firmware/experiments/media003`；根依赖锁、稳定工具链、表达协议和服务端均不改变。
- `71868da` 已通过 LAN HTTP 应用 OTA 安装，任务为 `INSTALLED`，NVS、OTA 能力和 `motion_disabled` 保留；本轮未主动演练回退。
- 用户确认开机 EAF 片段后恢复 native、连续三次唤醒对话和回答声音正常、TTS 播放中触摸停止及下一回合正常，无黑屏、卡住或自动重启。
- 稳定心跳约 55/60 FPS、绘制 1097 μs、传输 22627 μs、锁等待 10 μs、音频 underrun 0、最低空闲堆 7,735,712 字节。

## 下一步操作

完成生成器、目标 profile、既有协议/语音/传输、文档和 diff 最终回归，将实体证据压回唯一任务提交。保持 `71868da` 运行；不刷 Emote lifecycle profile，不建设正式多片段资源协议。

## 阻塞项

- 自动化和实体成功路径均无阻塞。EAF 依赖 ESP-IDF 5.5 及以上，默认 native 工具链仍为 5.4.4。
- 多资源包常驻、正式 EAF 资源协议和 Emote 显示接管不在本次原型范围。

## 关键文件

- `firmware/main/device_transport.c`
- `firmware/main/device_protocol.c`
- `firmware/main/voice_service.c`
- `firmware/main/voice_protocol.c`
- `firmware/main/voice_control.c`
- `firmware/main/firmware_ota.c`
- `firmware/main/expression_pack.c`
- `firmware/main/companion_hardware.cpp`
- `firmware/main/media003_backend_probe.cpp`
- `firmware/experiments/media003/README.md`
- `firmware/experiments/media003/generate-eaf-benchmark.mjs`
- `firmware/experiments/media003/eaf_probe/`
- `firmware/experiments/media003/emote_probe/`
- `firmware/main/idf_component.yml`
- `firmware/dependencies.lock`
- `firmware/sdkconfig.defaults.*`

## 验证命令与最近结果

- 提交绑定 EAF 候选 `71868da` 完整构建通过：1,669,504 字节，SHA-256 `7E794FDECA4A4CC005C87389A691C3B86FBD783B931E5086607F479394880206`，app descriptor 版本为 `71868da`，镜像校验有效。
- `71868da` 应用 OTA 为 `INSTALLED`；用户确认 EAF→native、三次语音、声音、触摸取消、下一回合和稳定性正常。心跳约 55/60 FPS、音频 underrun 0、最低空闲堆 7,735,712 字节。
- MEDIA-003 EAF 生成器测试 3/3 通过；确定性 EAF 大小 53,854 字节，SHA-256 `ABD64A59F59CAFF9CEE7A65921781BF6FE28B150AD876ADDF8CF52505690FE81`，仓库内嵌制品与生成结果逐字节一致。
- 默认 ESP-IDF 5.4.4 LAN HTTP Quad 完整构建通过：1,581,488 字节，SHA-256 `6E86F2F867015806C8048F9AD9DAB78A85862AB89EF98CB2F9F63DF3589B4133`。
- ESP-IDF 5.5.5 native 完整构建通过：1,605,088 字节，SHA-256 `D72FC2B4E639300B9E3A34EFA80AE5A98B7541B994536E2B171BDB8C02C5F9BF`。
- ESP-IDF 5.5.5 EAF RLE-only 完整构建通过：1,669,504 字节，SHA-256 `F53774CBE72BDDD005B8BDF9196C008DB4D3A0E5842D32D1CF43ECF86BD98283`。
- ESP-IDF 5.5.5 Emote lifecycle 完整构建通过：1,615,776 字节，SHA-256 `338DCC7EE268CC5D3B167C6C990EC847B72672EDFAEEBCE1FFE7867C0E350A51`；不作为可视固件候选。
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
- [0039：原生连续渲染与有限 EAF 生命周期片段](../decisions/0039-native-renderer-with-bounded-eaf-lifecycle-clips.md)
- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)
- [动态球形表情实机验收](../../runbooks/dynamic-expression-smoke-test.md)
- [MEDIA-003 EAF 实机验收](../../runbooks/media003-eaf-smoke-test.md)

## 安全与兼容性约束

- 未经用户逐次确认设备、端口、profile、提交和 NVS 策略，不连接 COM3、不刷写、不发起 OTA。
- 设备继续拒绝任意 URL、跨源制品、未知命令字段和运动启用。
- 不记录 Wi-Fi 密码、配对码、设备 Token、音频、转写或供应商密钥。
