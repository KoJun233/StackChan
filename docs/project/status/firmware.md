# 固件工作流

- 状态：STABLE
- 最后更新：2026-07-29
- 当前分支：`codex/data-001-personal-data-lifecycle`
- 基准提交：`2a3b712`
- 最后验证提交：`2a3b712`
- 当前实机镜像：`b05d60f`

## 当前目标

保持 CoreS3 `b05d60f`、WakeNet、触摸取消、SCV1、动态机械眼与 `motion_disabled` 稳定；`DATA-001` 不改固件，下一项设备能力属于 `INT-009` 连续对话。

## 已完成

- INT-007 默认表情改为独立实现的 M5Unified/M5GFX 双缓冲机械眼，覆盖八个稳定状态以及眨眼、视线、聆听脉冲、思考扫描、播报嘴型和有界反馈动画；未复制 RoboEyes GPL 源码。
- 新增 `expression_a` / `expression_b` 两个 1.5 MiB 分区。同源认证下载写入非活动槽，整体摘要、清单、状态集合、逐图摘要和 PNG 头全部通过后才原子更新 NVS；损坏自动回退内置表情。
- 切换新包后擦除旧槽，停用后擦除双槽；静态宠物包不改变既有低亮度、低频内置机械眼屏保，`motion_disabled` 保持不变。
- `731a68c` 完成 M5Unified CoreS3 显示、触摸、麦克风和扬声器封装，且不初始化运动执行器。
- 完成本地 `Hi, Stack Chan` WakeNet、最长 8 秒且静音提前结束的 16 kHz 单声道录音、`SCV1` 上传/解析和 WAV 播放。
- `731a68c` 最初完成 300 秒低亮度动态屏保、触摸/唤醒/播报退出屏保和状态表情。
- 完成严格 `speak_reminder`、固定同源音频下载、20 项队列和按真实播放结果发送 ACK。
- `b6dbaf8` 将 CoreS3 PSRAM 从错误的 Octal 模式修正为实际硬件使用的 Quad 模式。
- 协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四个 Quad PSRAM profile 均在 `b6dbaf8` 构建通过并嵌入版本 `b6dbaf8`。
- 已将 `b6dbaf8` 的 LAN HTTP 完整镜像刷入 COM3；启动确认 8 MB PSRAM、加密 NVS、M5StackChan/CoreS3 硬件、WakeNet 和 `motion_disabled` 正常。
- `e9f072a` 将空闲屏保改为进入时只关闭一次背光，不清屏、不移动瞳孔、不周期性重绘；触摸、语音状态和提醒恢复正常亮度与当前表情。
- `e9f072a` 保持既有严格 USB 配网协议不变，因此管理后台可以通过 Web Serial 直接使用该协议；没有增加远程 Wi-Fi 修改入口。
- 协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四个 Quad PSRAM profile 均在 `e9f072a` 构建通过并嵌入版本 `e9f072a`。
- 用户明确确认 CoreS3、`COM3`、LAN HTTP development profile 和 `e9f072a` 后，已将完整镜像刷入 COM3；bootloader、应用、分区表、OTA data 和 WakeNet 模型分区均通过写入 SHA 校验。
- `e9f072a` 启动确认 8 MB Quad PSRAM / 80 MHz、PSRAM 内存测试、M5StackChan/CoreS3 外设、加密 NVS、WakeNet、LAN HTTP profile 和 `motion_disabled` 正常。
- 设备成功连接保存的 Wi-Fi，数据库持续收到 `e9f072a` / `motion_disabled` 心跳，最近一次核对为 `2026-07-19 21:46:51+08:00`。
- 设备静置后在约 301.7 秒只记录一次 `Idle display backlight off`，期间没有 panic 或重启，确认不再周期性整屏重绘。
- `b4876fb` 按 ADR 0010 将屏保改为 300 秒后从亮度 160 降到 24，每 2.5 秒仅擦除并重画两只瞳孔的 26 像素小区域；八帧固定路径的水平和垂直偏移均不超过 8 像素。
- 触摸、WakeNet 唤醒、状态切换、回复播放和提醒播报会立即恢复正常亮度并完整重画当前表情；屏保不初始化任何运动执行器，`motion_disabled` 保持不变。
- 新增独立瞳孔路径模块和 Unity 回归测试，并将屏保状态的读取和更新统一放在显示互斥锁内，避免退出屏保与低频刷新发生数据竞争。
- `06a67ab` 将默认 WakeNet 检测模式从 `DET_MODE_90` 调整为 `DET_MODE_95`，把开始说话/静音阈值默认值从 `700/450` 降为 `350/200`，并记录峰值平均能量、阈值和采集样本数等非敏感诊断数据。
- `06a67ab` 删除实体回溯已确认会在 `dl_convq_queue_bzero` 触发 `LoadProhibited` 的 `wakenet->clean(model)`；每个语音回合结束后由语音任务销毁并重建 WakeNet 模型。
- `06a67ab` 增加 `voice_control_configure()` 和严格 `configure_voice_detection` 命令处理，支持在线切换普通/灵敏唤醒以及两项阈值；扬声器播放结束后还会确认麦克风恢复成功。
- `ce27ec9` 将“灵敏”模式的单唤醒词检测阈值显式降至 `0.50`，并在模型创建和重建时记录实际阈值千分值；普通模式继续使用 `DET_MODE_90` 默认阈值。
- `ce27ec9` 让 WakeNet 短帧完成检测直接读取 M5Unified 的录音状态，不再等待 UI 互斥锁，避免约 32 ms 片段在首次观察前结束而误报 `ESP_ERR_TIMEOUT`。
- `b2e8db2` 将该轮询从会在 100 Hz tick 下取整为 0 的 `pdMS_TO_TICKS(1)` 改为至少阻塞一个 FreeRTOS tick，避免高优先级语音任务挤占 CPU0 idle task 并触发任务看门狗。
- `e33a0d4` 在麦克风请求入队后先阻塞一个 FreeRTOS tick，让低优先级 M5Unified 麦克风任务运行；重新检查时若录音状态已经归零，将其视为短帧成功完成，只有仍在录音且超过上限才返回超时。
- 当前任务增加 `CONFIG_STACKCHAN_WAKE_WORD_MODEL` 和独立模型选择模块，只接受精确的小写 WakeNet 名称；首选模型缺失时只回退到 `wn9l_histackchan_tts3`，绝不替换为任意语音模型。
- 语音任务会验证候选模型接口、实例、采样块、16 kHz 采样率和单声道格式；首选模型创建或格式检查失败时显式回退，并在模型块大小变化时安全更换内部采集缓冲区。
- 新增模型包校验和回归脚本，拒绝链接、非法名称、空文件、默认模型覆盖和分区余量不足；本地模型目录已加入 Git 忽略。
- 恢复本任务时确认状态文件中的 `ROOT` 已因仓库历史压缩而不可解析；已按 Git 事实把当前基准修正为 `dae6015`。
- 当前任务将模型分区扩展为永久 `model` 出厂槽和 `model_a` / `model_b` OTA 槽；运行时始终写入非活动槽，校验 SHA-256 和模型包后才切换。
- 固件保存 pending 安装、活动槽和补报状态到 NVS；新模型重启后须在 20 秒内完成 WakeNet 健康确认，第二次 pending 启动会自动恢复上一健康槽或出厂模型。
- 安装成功和回退状态保存在 NVS，并在每次 WebSocket 重连时幂等补报；固定同源下载、设备命令 ACK 和 `motion_disabled` 行为保持不变。
- INT-001 在每次 WakeNet 命中时生成 UUID v4，并通过语音 HTTP 头和 WebSocket 阶段事件复用同一回合 ID。
- 阶段事件进入 16 项有界、非阻塞队列；队列满、WebSocket 离线或诊断发送失败只丢弃诊断，不阻断语音主流程或改变 `motion_disabled`。
- 固件只上报白名单阶段、0..300000 毫秒单调相对耗时和白名单失败码；不发送音频、识别文本、回复、URL 或认证信息。
- 播放成功后分别上报播放完成和麦克风恢复；麦克风重启失败与普通播放失败使用不同安全失败码。
- 用户已确认 INT-001 的三个成功回复清晰播放，聆听/处理/待机表情正常，管理端 1 个 `NO_SPEECH` 与 3 个成功时间线符合预期。
- INT-002 将可见状态枚举从具体绘图代码中分离；WebSocket 离线覆盖当前阶段，重连后恢复当前阶段，非法内部状态安全映射为可恢复错误。
- 成功、没听清和可恢复错误分别显示有界的绿色、青色和橙色反馈；离线使用持续灰色反馈，低亮度瞳孔屏保只从待机或离线进入。
- 用户明确授权 `adbd75e`、CoreS3、`COM3` 和 LAN HTTP Quad profile 后，已从该干净提交重建并刷入完整镜像；bootloader、分区表、应用、OTA data 和出厂语音模型五个区域均通过独立摘要校验，保存 Wi-Fi 和设备身份的 NVS 未擦除。
- INT-006 增加严格 `configure_interaction` 与 `stop_audio` 解析；音量映射到 M5Unified 0..255，夜间模式降低非屏保亮度，停止命令复用既有可中断播放和麦克风恢复路径。
- 首轮验收确认远程 `stop_audio` 能停止播放，但未复用触摸取消的阶段上报；当前工作树改为调用统一回合取消入口，同时请求 HTTP/播放中断并上报 `CANCELLED`，避免服务端留下 `RESPONSE_READY`。
- 用户明确确认 CoreS3、`COM3`、LAN HTTP Quad development profile 和 `c1d7383` 后，从干净提交构建并完整刷写 bootloader、分区表、应用、OTA data 和语音模型；五个区域独立 `verify_flash` 全部匹配，NVS 未擦除。
- `c1d7383` 启动确认 8 MB Quad PSRAM、加密 NVS、CoreS3 显示/触摸/麦克风/扬声器、LAN HTTP、WakeNet“小峰小峰”、WebSocket 和 `motion_disabled`；数据库收到新鲜心跳，观察窗口未见 panic、栈溢出、看门狗或重启循环。
- 实机启动确认应用版本 `adbd75e`、ESP-IDF 5.3.3、16 MB Flash、8 MB PSRAM / 80 MHz 及内存测试、CoreS3 显示/触摸/麦克风/扬声器、LAN HTTP、`wn9_xiao3feng1xiao3feng1_tts3` 和 `motion_disabled` 正常；数据库收到该版本的最近心跳，启动窗口未见 panic、看门狗或重启循环。
- 首轮用户 smoke test 确认没听清和屏保通过；正常回合在处理后落到离线几何，离线几何使用的蓝灰色也与正常交互难以区分，因此正常回合和离线状态未通过验收。
- 根因是 WebSocket 离线无条件覆盖活动阶段和短暂反馈、成功态仅持续 0.6 秒，以及离线颜色 `#7A8798` 本身偏蓝。修复后离线只覆盖待机，活动阶段与短暂反馈优先显示，离线改为中性灰 `#8C8C8C`，成功延长至 1.2 秒，并记录非敏感 WebSocket 连接变化日志。
- 用户明确授权 `abd6a22`、CoreS3、`COM3` 和 LAN HTTP Quad profile 后，已从该干净提交重建并刷入完整镜像；bootloader、分区表、应用、OTA data 和语音模型五个区域均通过独立 `verify_flash`，保存 Wi-Fi 和设备身份的 NVS 未擦除。
- 实机启动确认应用版本 `abd6a22`、ESP-IDF 5.3.3、8 MB PSRAM / 80 MHz 及内存测试、CoreS3 显示/触摸/麦克风/扬声器、LAN HTTP、WakeNet、WebSocket 和 `motion_disabled` 正常；45 秒窗口未见 panic、看门狗或重启循环，数据库收到该版本的最近心跳。
- 用户正常对话复测中，两轮均在 `Speech captured` 后报告 `A stack overflow in task voice_control has been detected`，随后以 `RTC_SW_CPU_RST` 重启；数据库只收到唤醒与聆听阶段，服务端没有收到语音 HTTP 请求。灰色离线是重启和网络恢复期间的正确连接态，不是此次失败的根因。
- `voice_control` 的同步录音与 HTTP/TCP 路径共用同一任务；真实编译器栈报告显示 `run_voice_turn` 本地帧 8176 字节、完整已知本地路径 10416 字节，旧 12288 字节任务仅剩 1872 字节给外部库。
- 本地修复将语音任务栈提高到 32768 字节，并新增独立验证与回归脚本；预算检查确认外部余量 22352 字节，12288 字节夹具被拒绝、32768 字节夹具通过。协议测试与 LAN HTTP Quad 完整镜像均构建通过，镜像分别为 `0x37880` 和 `0x142d90`、应用分区余量 93% 和 58%，LAN 镜像确认 16 MB Flash、Quad PSRAM 与 LAN HTTP。
- 用户明确授权 `216d383`、CoreS3、`COM3` 和 LAN HTTP Quad profile 后，已从该干净提交重建并刷入完整镜像；bootloader、分区表、应用、OTA data 和语音模型五个区域均通过独立 `verify_flash`，NVS 未擦除。
- 启动确认应用版本 `216d383`、ESP-IDF 5.3.3、8 MB PSRAM / 80 MHz 及内存测试、CoreS3 显示/触摸/麦克风/扬声器、LAN HTTP、WakeNet、WebSocket 和 `motion_disabled` 正常；40 秒窗口未见 panic、栈溢出、看门狗或重启循环，数据库收到该版本的新鲜心跳。
- 用户完成两轮正常对话后，串口分别记录 32000 和 40000 个样本的 `Speech captured`，随后均恢复 WakeNet 监听，未出现栈溢出、panic、看门狗或软件复位。数据库两轮状态均为 `COMPLETED`，完整阶段链包含 `REQUEST_RECEIVED`、`ASR_COMPLETED`、`LLM_COMPLETED`、`TTS_COMPLETED`、`PLAYBACK_STARTED`、`PLAYBACK_COMPLETED` 和 `LISTENING_RESUMED`。之后一次独立未说话唤醒以 `NO_SPEECH` 正常结束。
- 用户确认上述两轮均正常播放语音回复，成功反馈和返回待机的实体显示符合预期；正常回合人工验收完成。
- 用户随后按 runbook 临时制造语音模型调用失败并恢复原配置，确认可恢复异常反馈和恢复后的正常回合均正常；用户要求不再读取机器证据，被动串口监听已停止。

## 正在进行

INT-008 已合入 `master`；`b05d60f` 已完整刷入 CoreS3 并通过五区回读、启动、安全状态、WebSocket 和默认机械眼人工验收。DATA-001 只修改管理端、服务端和部署备份能力，不修改 SCV1、设备阶段、音频正文或触摸取消语义，本分支不修改或刷写固件。

DATA-001 LAN server 发布到 Flyway V22 后，CoreS3 自动恢复 `b05d60f / motion_disabled` 新鲜心跳；未连接 COM3、未刷写、未 OTA，固件与设备凭据保持不变。

## 下一步操作

保持 `b05d60f / motion_disabled` 稳定运行。DATA-001 合入后，再从最新 `master` 为 `INT-009` 设计跟进聆听阶段、退出事件和旧固件兼容；未获得设备、端口、profile 与提交的逐次确认前不得刷写。

## 阻塞项

固件无阻塞；默认机械眼已人工通过，自定义资源包实体测试因没有素材暂缓。不得记录 Wi-Fi 密码、Workspace ID、配对码、图片载荷、语音供应商密钥或设备 Token。

## 关键文件

- `firmware/`
- `scripts/verify-wake-word-model-package.ps1`
- `scripts/test-wake-word-model-package.ps1`
- `docs/runbooks/custom-wake-word-model.md`
- `docs/runbooks/physical-device-smoke-test.md`
- `firmware/main/wake_model_ota.c`
- `firmware/main/voice_service.c`
- `firmware/main/device_transport.c`
- `firmware/main/device_protocol.c`
- `firmware/main/interaction_state.c`
- `firmware/main/touch_interaction.c`
- `firmware/main/companion_hardware.cpp`
- `firmware/main/face_animation.c`
- `firmware/main/expression_pack.c`
- `firmware/main/voice_control.c`
- `firmware/partitions.csv`

## 验证命令与最近结果

- BASE-008 文档刷新未修改固件源码、profile 或分区表；配网 8192 拒绝/16384 接受、语音 12288 拒绝/32768 接受的危险预算夹具通过，配网真实报告为 `16384 / 7648 / 8736`。干净工作树没有忽略的 `build-voice-stack-analysis` `.su` 文件，因此未重复读取语音真实报告；INT-007 已验证的 `32768 / 10384 / 22384` 构建证据保持有效。未连接 COM3 或刷写。
- INT-007 ESP-IDF 5.3.3 协议测试和带 `-fstack-usage` 的分析 profile 构建通过，镜像 `0x37880`、应用分区余量 93%；LAN HTTP Quad 完整镜像构建通过，镜像 `0x14ade0`、余量 57%，分区表包含两个 1.5 MiB 表达资源槽。Unity 用例已编入协议镜像但未上板执行。
- INT-007 配网栈预算为 `16384 / 7648 / 8736` 字节，语音栈预算为 `32768 / 10384 / 22384` 字节；两项危险夹具拒绝回归和唤醒模型包安全回归均通过。未连接 COM3 或刷写。
- INT-007 server/V18 发布后，旧 CoreS3 `2465427 / motion_disabled` 在约 5 秒心跳窗口内自动恢复连接，确认 server-first 兼容路径可用；固件、分区表和 NVS 均未改动。
- 经用户明确授权，从干净 `8394cb3` 构建并完整写入 CoreS3 `COM3` 的 bootloader、分区表、应用、OTA data 和语音模型；五区独立 `verify_flash` 均为 digest matched，NVS 未擦除。启动确认版本、ESP-IDF 5.3.3、8 MB Quad PSRAM/80 MHz 及内存测试、CoreS3 外设、WakeNet 和 `motion_disabled`，未见 panic、栈溢出或看门狗。
- `8394cb3` 启动后 WebSocket 每次连接约两秒即停止并退避至 60 秒，数据库只收到间歇心跳。根因是默认资源状态仍同步擦除两个 1.5 MiB 分区并在 WebSocket 事件回调内发送 ACK；本地修复将无活动包的清理改为幂等立即成功，并把真实清理排入传输任务。修复后协议/LAN HTTP Quad 镜像构建为 `0x37880` / `0x14ae40`、余量 93% / 57%，配网栈 `16384 / 7648 / 8736`、语音栈 `32768 / 10384 / 22384` 及唤醒模型包回归通过，尚未刷写。
- 经用户明确授权，从干净 `b05d60f` 重新完整写入 CoreS3 `COM3` 的 bootloader、分区表、应用、OTA data 和语音模型；五区即时哈希与独立 `verify_flash` 均匹配，NVS 未擦除。启动确认版本、ESP-IDF 5.3.3、8 MB Quad PSRAM/80 MHz 及内存测试、CoreS3 外设、WakeNet 和 `motion_disabled`；42 秒窗口只建立一次 WebSocket，后续 32 秒没有断线、退避、panic、栈溢出或看门狗，数据库连续收到 `b05d60f / motion_disabled` 心跳。
- 用户人工确认默认机械眼正常且没有问题；因当前没有多余自定义表情素材，用户选择暂缓八状态资源包生成、启用和恢复默认实体测试，后续仅在素材可用或发现缺陷时继续。
- INT-006 使用 ESP-IDF 5.3.3 构建协议测试 profile 成功，镜像 `0x37880`、应用分区余量 93%；新增严格音量/夜间模式和停止播报 Unity 用例已编译进镜像，两项栈预算 fixture 回归通过。未上板执行、未连接或刷写设备。
- INT-006 server/V17 发布后，旧 CoreS3 `717a8b1 / motion_disabled` 恢复新鲜心跳，确认 server-first 兼容路径可用；未连接 COM3、刷写或 OTA。
- INT-006 LAN HTTP Quad 完整应用镜像为 `0x143c20`、应用分区余量 58%，嵌入版本 `c1d7383`；bootloader、分区表、应用、OTA data 和语音模型五区写入及独立校验通过，NVS 未擦除。启动、WebSocket、WakeNet 和 `c1d7383 / motion_disabled` 心跳通过。
- INT-006 验收修复使用 ESP-IDF 5.3.3 构建协议测试与 LAN HTTP Quad profile 成功，应用镜像分别为 `0x37880` 和 `0x143c40`、分区余量 93% 和 58%。经用户明确授权，从干净 `2465427` 完整刷写 bootloader、分区表、应用、OTA data 和语音模型，五个区域独立 `verify_flash` 全部匹配且 NVS 未擦除。
- `2465427` 启动确认 8 MB Quad PSRAM / 80 MHz 及内存测试、加密 NVS、CoreS3 显示/触摸/麦克风/扬声器、WakeNet“小峰小峰”、LAN HTTP、WebSocket 和 `motion_disabled` 正常；数据库收到新鲜心跳，35 秒窗口未见 panic、栈溢出或看门狗。
- 用户确认远程停止、提醒和主动问候等 INT-006 六项实机验收全部正常；当前固件无需再次刷写。
- INT-003 使用 ESP-IDF 5.3.3 和独立 sdkconfig 构建四个 Quad profile：协议测试 `0x37880`/93%，默认 HTTPS `0x1549f0`/56%，LAN HTTP `0x143980`/58%，测试证书 HTTPS `0x143de0`/58%。公开 ESP-IDF 测试证书已删除，未创建或保留私钥；Unity 用例只编译进协议镜像，未上板执行。
- INT-003 两项栈预算回归通过：provisioning 8192 字节被拒绝/16384 字节接受，voice 12288 字节被拒绝/32768 字节接受；唤醒模型包安全回归通过。
- INT-003 经用户明确确认 `717a8b1`、CoreS3、`COM3` 和 LAN HTTP Quad development profile 后，从干净提交重建并完整刷写 bootloader、分区表、应用、OTA data 和语音模型；五个区域独立 `verify_flash` 全部匹配，NVS 未擦除。启动确认 PSRAM、CoreS3 显示/触摸/麦克风/扬声器、LAN HTTP、WakeNet、WebSocket 和 `motion_disabled` 正常，未见 panic、栈溢出、看门狗或重启循环；数据库收到 `717a8b1` 新鲜心跳。
- INT-003 实体人工验收四项全部通过：屏保首触只唤醒；在线待机长持至少 600 ms 可按住说话并在松手后处理；聆听、处理和播放阶段短触可取消；播放中的短触会立即停止声音。运行库同时记录真实 `TOUCH_STARTED` 和 `CANCELLED`。
- 本地上传增量不改变设备协议、分区表或固件源码；未执行构建、刷写或模型 OTA。部署后数据库继续收到 `0398073` / `motion_disabled` 心跳。
- 本次内置目录改造不修改固件源码、分区表或设备协议；当前 `0398073` 映射中 `esp_wn_handle_from_name` 同时链接 `wakenet9_quantized`、`wakenet9l_quantized` 和 `wakenet9s_quantized`，可运行“小峰小峰”的 WakeNet9 模型。
- 2026-07-26 管理员选择“小峰小峰”后，设备接受安装命令、完成模型槽写入和重启健康确认，服务端任务进入 `INSTALLED`；随后数据库恢复 `0398073` / `motion_disabled` 心跳，未执行完整固件刷写。
- 当前任务协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四个 Quad profile 均构建通过，镜像分别为 `0x37880`、`0x153260`、`0x1421e0` 和 `0x1428d0`，应用分区余量分别为 93%、56%、58% 和 58%。
- 测试证书 HTTPS 构建在 Windows 上两次因 ESP-IDF 工具链子进程无诊断中止，降低并行度后同一目录增量构建完成；公开测试证书随后删除，未创建或保留私钥。
- 自定义模型包回归通过：有效组合被接受，缺少元数据、默认模型同名覆盖、非法模型名和分区余量不足均被拒绝。
- 用仓库内 `wn9l_ja_konnichihaesp_tts3` 模拟外部首选模型，校验器和 ESP-SR 实际打包结果均为 `585211` 字节；生成分区同时包含该模型和默认 `wn9l_histackchan_tts3`，余量 `463365` 字节。
- 当前任务树的模型选择、语音控制和 Unity 测试对象均编译通过；Unity 用例已编入协议镜像但未刷入设备执行。
- 协议测试、默认 HTTPS 和 LAN HTTP 三个 Quad profile 完整构建通过，应用镜像分别为 `0x37880`、`0x1515b0` 和 `0x140460`，应用分区余量分别为 93%、56% 和 58%。
- 两项 provisioning stack budget check 通过：8192 字节回归样例被拒绝，16384 字节任务预算通过，已知本地路径 7648 字节，外部余量 8736 字节。
- `git diff --check` 和 `pnpm docs:check` 通过；文档检查使用仓库记录的临时 Node 引导，Node v24.14.0 仅产生 engine 提示且检查成功。
- 2026-07-26 经用户明确确认 CoreS3、`COM3`、LAN HTTP Quad 和 `0398073` 后，已从独立目录构建并完整刷写；未向外部服务提交唤醒短语或模型，生产 HTTPS-only 边界未改变。
- `0398073` LAN HTTP Quad 应用镜像为 `0x1421e0`，应用分区余量 58%；bootloader、分区表、应用、OTA data 和出厂模型五个区域写入后均通过独立 `verify_flash`。
- 启动确认应用版本 `0398073`、ESP-IDF 5.3.3、三个 1 MiB 模型槽、8 MB PSRAM / 80 MHz、PSRAM 内存测试、加密 NVS、CoreS3 外设、出厂 `wn9l_histackchan_tts3`、LAN HTTP 和 `motion_disabled`；数据库收到该版本心跳。
- 协议测试 profile：在干净提交 `b4876fb` 构建通过并嵌入 `b4876fb`，镜像 `0x37880`，应用分区余量 93%；屏保 Unity 测试代码已编译但未上板执行。
- 默认 HTTPS profile：在干净提交 `b4876fb` 构建通过并嵌入 `b4876fb`，镜像 `0x1506a0`，应用分区余量 56%。
- LAN HTTP profile：在干净提交 `b4876fb` 构建通过并嵌入 `b4876fb`，镜像 `0x13f570`，应用分区余量 58%。
- 测试证书 HTTPS profile：使用 ESP-IDF 自带的公开测试证书临时构建并嵌入 `b4876fb`，镜像 `0x13f9f0`，应用分区余量 58%；验证后证书已删除，未创建或保留私钥。
- 两项 provisioning stack budget check 在 `b4876fb` 通过：8192 字节回归样例被拒绝，16384 字节任务预算通过，已知本地路径 7648 字节，外部余量 8736 字节。
- COM3 新刷写：用户确认 CoreS3、`COM3`、LAN HTTP development profile 和 `b4876fb` 后，bootloader、应用、分区表、OTA data 和 WakeNet 模型分区全部写入并通过哈希校验。
- COM3 新启动：应用版本 `b4876fb`；识别 8 MB Quad PSRAM / 80 MHz，PSRAM 内存测试通过；加密 NVS、M5StackChan/CoreS3 显示/触摸/麦克风/扬声器、WakeNet 和 `motion_disabled` 正常；未出现 panic、assert 或重启循环。
- COM3 新联网与唤醒：用户通过 USB 完成服务地址更新；2026-07-20 22:38（Asia/Shanghai）数据库持续收到 `b4876fb` / `motion_disabled` 心跳，WebSocket 已恢复。22:40 实体唤醒、聆听/思考表情、录音上传和 ASR 通过；外部 LLM 超时导致该轮没有回复音频。
- COM3 刷写：ESP32-S3 revision v0.2；`e9f072a` 的 bootloader、应用、分区表、OTA data 和 WakeNet 模型分区全部写入并通过 SHA 校验。
- COM3 启动：识别 8 MB Quad PSRAM、80 MHz、内存测试通过；应用版本 `e9f072a`；M5GFX 自动识别 `board_M5StackChan`；CoreS3 显示/触摸/麦克风/扬声器初始化成功；NVS 加密；WakeNet 成功加载并监听 `Hi, Stack Chan`；`motion_disabled` 保持不变。
- COM3 联网：保存的 Wi-Fi 成功连接；数据库最新心跳为 `e9f072a` / `motion_disabled`。未记录 SSID、密码、Token 或完整配置载荷。
- COM3 屏保：静置约 301.7 秒后只出现一次背光关闭日志，期间无 panic 或重启；触摸恢复和肉眼无闪烁仍待用户确认。
- `06a67ab` 协议测试 profile 构建通过，镜像 `0x37880`；默认 HTTPS、LAN HTTP 和测试证书 HTTPS 镜像分别为 `0x150e20`、`0x13fce0` 和 `0x140180`，四种镜像均嵌入 `06a67ab`。
- 两项 provisioning stack budget check 在 `06a67ab` 通过；测试证书使用 ESP-IDF 公开样例并在构建后删除，未创建或保留私钥。
- `06a67ab` LAN HTTP 完整镜像已刷入 CoreS3 的 `COM3`；bootloader、应用、分区表、OTA data 和 WakeNet 模型均通过设备端哈希校验。
- `06a67ab` 启动确认应用版本、8 MB Quad PSRAM / 80 MHz、PSRAM 内存测试、加密 NVS、CoreS3 显示/触摸/麦克风/扬声器、LAN HTTP、灵敏 WakeNet、`350/200` 配置和 `motion_disabled` 正常；服务端心跳已更新为 `06a67ab`。
- `06a67ab` 实体复测：150 秒内用户多次呼叫无 WakeNet 命中且无 panic 或重启；末尾捕获一次 `WakeNet microphone capture failed: ESP_ERR_TIMEOUT`。
- `ce27ec9` 首次 LAN 重建误用了 Octal 生成配置，刷入后启动立即报告 PSRAM line mode 错误并重启；随后使用 `sdkconfig.profile-lan-http-quad` 和显式 `PROJECT_VER=ce27ec9` 重新构建、覆盖刷写，五个分区再次通过哈希校验并恢复 8 MB Quad PSRAM、联网和心跳。NVS 与硬件未损坏。
- `ce27ec9` 正确 Quad 固件启动后实际阈值为 `0.496`；在在线监听中连续捕获 3 次唤醒和录音并在每轮后恢复 WakeNet，但因零 tick 轮询持续报告 CPU0 `voice_control` 任务看门狗。
- `b2e8db2` 协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四种 Quad profile 构建通过并嵌入该提交，镜像分别为 `0x37880`、`0x150f10`、`0x13fdd0` 和 `0x1402e0`。
- 两项 provisioning stack budget check 在 `b2e8db2` 通过；测试证书使用 ESP-IDF 公开样例并在构建后删除，未创建或保留私钥。
- `b2e8db2` 的正确 Quad LAN HTTP 完整镜像已刷入 `COM3`，五个分区通过写入校验；启动确认版本、8 MB Quad PSRAM、Wi-Fi/NVS、灵敏 WakeNet、`threshold_milli=496` 和 `motion_disabled`。数据库在 2026-07-21 22:29:38（Asia/Shanghai）收到该版本心跳。
- `b2e8db2` 上板监听不再出现 CPU0 任务看门狗，但持续出现 `WakeNet microphone capture failed: ESP_ERR_TIMEOUT`；根因是麦克风短帧已在首个轮询 tick 内完成，旧逻辑未观察到“正在录音”中间状态。
- `e33a0d4` 协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四种 Quad profile 构建通过并嵌入该提交，镜像分别为 `0x37880`、`0x150f20`、`0x13fde0` 和 `0x1402f0`；四个 profile 均确认 `CONFIG_SPIRAM_MODE_QUAD=y` 且未启用 Octal。
- 两项 provisioning stack budget check 在 `e33a0d4` 通过：8192 字节回归样例被拒绝，16384 字节任务预算通过，已知本地路径 7648 字节，外部余量 8736 字节。测试证书使用 ESP-IDF 公开样例，构建后已删除，未创建或保留私钥。
- `e33a0d4` 的 bootloader、应用、分区表、OTA data 和 WakeNet 模型分区已写入 COM3 并全部通过设备端哈希校验；没有擦除保存 Wi-Fi 和设备身份的 NVS。
- `e33a0d4` 启动确认 8 MB PSRAM / 80 MHz、PSRAM 内存测试、应用版本、M5StackChan/CoreS3 外设、LAN HTTP profile、`threshold_milli=496` 和 `motion_disabled`；Wi-Fi 与 WebSocket 自动恢复，数据库在 2026-07-21 22:49:25（Asia/Shanghai）收到该版本心跳。
- 首个 180 秒监听窗口与后续 120 秒窗口均为 `ESP_ERR_TIMEOUT=0`、任务看门狗 `0`、panic `0`。首个窗口命中两次唤醒并两次重建/恢复 WakeNet；一次在 Wi-Fi 未连接时安全返回 `ESP_ERR_INVALID_STATE`，另一次峰值能量 `313` 低于开始阈值 `350` 并安全返回 `ESP_ERR_NOT_FOUND`。后续窗口无新唤醒命中。
- INT-001 协议测试 profile 完整构建通过，镜像 `0x37880`，应用分区余量 93%；新增严格阶段编码 Unity 用例已编译进镜像，未上板执行。
- INT-001 两项 provisioning stack budget check 通过：8192 字节回归样例被拒绝，16384 字节任务预算通过，已知本地路径 7648 字节，外部余量 8736 字节。
- INT-001 最新 `master` 合并回归：ESP-IDF 5.3.3 协议测试 profile 从重配置完整构建通过，镜像 `0x37880`、应用分区余量 93%；两项栈预算和唤醒模型包安全回归通过。只执行编译，未连接或刷写设备。
- INT-002 ESP-IDF 5.3.3 协议测试 profile 完整编译通过，镜像 `0x37880`、应用分区余量 93%；新增状态解析 Unity 用例已编译进镜像，未连接或刷写设备。
- INT-002 默认 HTTPS、LAN HTTP 和测试证书 HTTPS Quad profile 完整编译通过，镜像分别为 `0x153d20`、`0x142cd0` 和 `0x143110`，应用分区余量分别为 56%、58% 和 58%；测试证书使用 ESP-IDF 公开样例，构建后已删除且未创建或保留私钥。
- INT-002 两项 provisioning stack budget check 通过：8192 字节回归样例被拒绝，16384 字节任务预算通过，已知本地路径 7648 字节，外部余量 8736 字节；唤醒模型包回归、文档检查与 7 项文档测试、LAN Compose 静态验证和 `git diff --check` 通过。
- `adbd75e` LAN HTTP Quad 实机重建镜像为 `0x142cd0`，应用分区余量 58%；配置确认 Quad PSRAM、16 MB Flash 和 LAN HTTP，五个烧录区域全部 `verify_flash` 匹配且不包含 NVS。启动日志与数据库心跳共同确认 `adbd75e` / `motion_disabled` 在线。
- 首轮验收修复后的协议测试与 LAN HTTP Quad profile 完整编译通过，镜像分别为 `0x37880` 和 `0x142d90`、应用分区余量 93% 和 58%；协议镜像包含扩展的离线仲裁用例，LAN 镜像确认 Quad PSRAM、16 MB Flash 和 LAN HTTP。配网任务栈预算保持 16384 字节任务、7648 字节已知本地路径和 8736 字节外部余量；模型包、文档 7/7、LAN Compose 和差异检查通过。
- `abd6a22` LAN HTTP Quad 完整镜像已刷入 CoreS3 `COM3`，五个区域独立 `verify_flash` 全部匹配且不包含 NVS。启动确认版本、PSRAM、CoreS3 外设、LAN HTTP、WakeNet、WebSocket 和 `motion_disabled`，45 秒内无 panic、看门狗或重启循环；数据库收到 `abd6a22` / `motion_disabled` 新鲜心跳。
- 用户复测的脱敏串口窗口捕获两次一致的 `voice_control` 栈溢出：录音分别完成 40000 和 64000 个样本后，任务在进入同步 HTTP 上传路径时触发栈保护并以 `RTC_SW_CPU_RST` 重启。服务端阶段链均未出现 `REQUEST_RECEIVED`。
- 修复后的语音栈预算回归为 12288 字节拒绝、32768 字节接受；真实 `-fstack-usage` 报告为任务 32768 字节、已知本地路径 10416 字节、外部余量 22352 字节。`216d383` LAN HTTP Quad 完整镜像已刷入 CoreS3 `COM3`，五个区域独立 `verify_flash` 匹配且 NVS 未擦除；启动、数据库心跳和两轮正常回合机器验证通过，两轮均完整播放并恢复监听，未再发生栈溢出或复位。
- 用户人工确认正常回合、橙色可恢复异常和恢复后的下一轮均符合预期；按用户要求未继续读取异常轮机器证据，监听已停止且 `COM3` 已释放。

## 相关设计、计划和决策

- [下一阶段可执行任务清单](../todo.md)
- [宠物表情资源包生成与验收](../../runbooks/expression-resource-packs.md)
- [0022：表情采用内置机械眼与可校验的版本化资源包](../decisions/0022-versioned-expression-resource-packs.md)
- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)
- [0004：LAN HTTP/WS 仅限显式编译的开发固件/profile](../decisions/0004-lan-http-development-only.md)
- [0007：设备本地唤醒、服务端语音适配与持久提醒](../decisions/0007-device-voice-and-durable-reminders.md)
- [0008：管理后台通过 USB 配网，空闲屏保关闭背光](../decisions/0008-browser-usb-provisioning-and-screen-off-idle.md)
- [0010：空闲屏保采用低亮度、小区域、低频移动瞳孔](../decisions/0010-low-brightness-local-pupil-screensaver.md)
- [0012：机器人本地唤醒与录音判定参数由管理员配置](../decisions/0012-configurable-device-voice-detection.md)
- [0013：语音回合使用隐私安全的阶段诊断](../decisions/0013-privacy-safe-voice-turn-diagnostics.md)
- [0014：自定义唤醒词采用离线生成并随固件打包的 WakeNet 模型](../decisions/0014-packaged-custom-wake-word-model.md)
- [0015：运行时生成并安全 OTA 自定义唤醒模型](../decisions/0015-runtime-wake-model-generation-and-ota.md)
- [0016：唤醒词仅从 ESP-SR 内置模型目录选择并安全 OTA](../decisions/0016-built-in-esp-sr-wake-model-catalog.md)
- [0017：交互阶段在设备本地映射为可区分的可见状态](../decisions/0017-local-user-visible-interaction-states.md)
- [0018：触摸控制采用本地事件队列与幂等语音回合取消](../decisions/0018-touch-control-and-voice-turn-cancellation.md)
- [0009：服务端增加阿里云百炼原生语音适配器](../decisions/0009-native-dashscope-speech-adapter.md)

## 安全与兼容性约束

- 未经用户明确确认，不得刷写固件。
- 不记录 Wi-Fi 密码、配对码、设备 Token 或完整设备配置载荷。
