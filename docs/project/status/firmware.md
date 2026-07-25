# 固件工作流

- 状态：ACTIVE
- 最后更新：2026-07-26
- 当前分支：`codex/custom-wake-word`
- 基准提交：`dae6015`
- 最后验证提交：`dae6015`

## 当前目标

在保留音频回合、提醒协议、动态瞳孔屏保、既有检测参数和 `motion_disabled` 的前提下，为 ESP-SR 内置 WakeNet 模型提供双槽 OTA、重启健康确认和自动回退。

## 已完成

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

## 正在进行

固件代码、分区表和协议保持完成状态。CoreS3 运行 `0398073` LAN HTTP Quad 镜像，具备永久 `model` 与 `model_a` / `model_b` 三槽；链接映射确认 WakeNet9、WakeNet9l 和 WakeNet9s 接口均已进入当前镜像。“小峰小峰”模型 OTA 已通过重启健康确认并由设备补报安装成功，无需再次刷写固件。

## 下一步操作

用户实体呼叫“小峰小峰”完成声学验收；若需要验证回退，再在受控测试窗口按 runbook 模拟候选槽健康失败，不改写出厂槽。

## 阻塞项

没有固件代码、模型文件、首次刷写或 OTA 状态阻塞。实体环境中的“小峰小峰”命中效果仍需用户确认。不得记录 Wi-Fi 密码、Workspace ID、配对码、语音供应商密钥、设备 Token、模型服务认证载荷或原始音频。

## 关键文件

- `firmware/`
- `scripts/verify-wake-word-model-package.ps1`
- `scripts/test-wake-word-model-package.ps1`
- `docs/runbooks/custom-wake-word-model.md`
- `docs/runbooks/physical-device-smoke-test.md`
- `firmware/main/wake_model_ota.c`
- `firmware/partitions.csv`

## 验证命令与最近结果

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

## 相关设计、计划和决策

- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)
- [0004：LAN HTTP/WS 仅限显式编译的开发固件/profile](../decisions/0004-lan-http-development-only.md)
- [0007：设备本地唤醒、服务端语音适配与持久提醒](../decisions/0007-device-voice-and-durable-reminders.md)
- [0008：管理后台通过 USB 配网，空闲屏保关闭背光](../decisions/0008-browser-usb-provisioning-and-screen-off-idle.md)
- [0010：空闲屏保采用低亮度、小区域、低频移动瞳孔](../decisions/0010-low-brightness-local-pupil-screensaver.md)
- [0012：机器人本地唤醒与录音判定参数由管理员配置](../decisions/0012-configurable-device-voice-detection.md)
- [0014：自定义唤醒词采用离线生成并随固件打包的 WakeNet 模型](../decisions/0014-packaged-custom-wake-word-model.md)
- [0015：运行时生成并安全 OTA 自定义唤醒模型](../decisions/0015-runtime-wake-model-generation-and-ota.md)
- [0016：唤醒词仅从 ESP-SR 内置模型目录选择并安全 OTA](../decisions/0016-built-in-esp-sr-wake-model-catalog.md)
- [0009：服务端增加阿里云百炼原生语音适配器](../decisions/0009-native-dashscope-speech-adapter.md)

## 安全与兼容性约束

- 未经用户明确确认，不得刷写固件。
- 不记录 Wi-Fi 密码、配对码、设备 Token 或完整设备配置载荷。
