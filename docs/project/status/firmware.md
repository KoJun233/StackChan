# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-09-08
- 当前分支：`codex/live-voice-v46-integration`
- 基准提交：`227b369`
- 最后验证提交：`4444860`
- 最后验证范围：段间播放预取 LAN HTTP Quad 保留 NVS 安装、WebSocket 启动与一分钟在线稳定性、三组任务栈回归通过
- 当前实机镜像：`4444860` LAN HTTP Quad（NVS 保留、WebSocket 在线、保持 `motion_disabled`）

## 当前目标

`a70fb9c` 已解决整块 WAV 上传阻塞，但两轮 80,044/72,044 字节请求仍需 286/436 ms。用户确认采用本地 VAD 门控的边录边传：新固件把录音窗口改为 100 ms，只在本地检测到语音或显式按住说话后打开固定同源 `/api/v1/device/voice/turn/live`，以 HTTP chunked WAV 在录音期间发送 PCM；约 800 ms 静音判定只保留最后 100 ms 静音，目标是把停录后的请求体尾部降到约 100 ms。1 KiB HTTP 写入、Wi-Fi/LwIP 优先 PSRAM、上传期关闭省电、八秒 PSRAM 兼容缓存和 32 KiB 语音任务栈保持不变；实时请求在终止前无法打开/写入时才回退完整 WAV，终止后不重试以免重复对话。服务端会按实际长度规范化开放 WAV 头后再进入既有 ASR/SCV2，因此 ASR、Agent、TTS 延迟不在该 100 ms 目标内。

首轮 `b2303e8` 实机验证确认请求体尾部已降至 17–136 ms，但同步 HTTP 写入与 I2S 采集位于同一任务，网络写入让两轮采集墙钟时间比有效样本多约 1–1.8 秒；主请求最终只有 9,600/32,000 PCM 字节（0.3/1.0 秒），ASR 分别误识别为“嗯”和“No”。PSRAM 全程约 7.7–8.0 MiB 可用，无分配失败、崩溃或复位，因此不是机器人内存不足。

`628acd0` 已把 HTTP 打开/写入迁到 Core 1 的 24 KiB PSRAM 上传任务，Core 0 语音任务只连续采集并发布已完成窗口；起始 VAD 同时要求连续两个 100 ms 窗口。用户确认两轮识别恢复正常。短首段发布后用户确认初始响应变快，但最新两轮播放阶段分别为 17.123/76.379 秒，实际 PCM 只有 12.000/52.220 秒，额外停顿为 5.123/24.159 秒。服务端已在设备播完前 flush 后续段，根因是 `handle_streaming_turn_frame` 在 HTTP 解析回调内同步播放 WAV，播放时不能继续读取响应；不是内存不足。

当前候选让 SCV2 解析器把完整帧的 PSRAM 所有权移交给深度 1 的队列，由 Core 1 独立 24 KiB PSRAM 播放任务顺序播放；网络任务可并行预取下一帧。完成、错误与触摸取消均有终止/释放路径，日志新增每段缓冲、间隔、播放、栈和 PSRAM 数值，不含正文或音频。HTTP RX/TX 缓冲继续保持 1 KiB；最多只预取一个待播帧，不扩大协议单帧或总段数上限。

首次将 `4aa700a` 保留 NVS 刷入后，8 MiB PSRAM、Wi-Fi、身份、WakeNet、LAN HTTP 和 `motion_disabled` 均正常，但 WebSocket 的 8 KiB 内部栈申请失败。第一版纠正把命令队列移到任务启动之后，实机进一步证明 WebSocket 初始化期预分配的 RX/TX 缓冲仍会把最大内部连续块压到 6.5–7.5 KiB；启用组件官方动态缓冲后可用总量增加约 8 KiB，但连续块稳定为 7680 字节。静态 DRAM 与旧镜像相同，播放任务和队列此时尚未创建，故不是 PSRAM 或总内存不足；最终纠正使用 7168 字节 WebSocket 栈落入实测连续块，同时保持动态 1 KiB 缓冲、WebSocket 先启动、命令队列后建和初始化数据门控，并在连接事件记录真实最低剩余栈。

保持实机 CoreS3 默认 `motion_disabled`；WORK-001 顶部长按显式启停已通过实机验收，环境光按能力位工作，当前设备未上报该能力时保持安全默认亮度。未经独立授权不启用或执行身体动作。

WORK-002 不修改设备协议或固件。服务端只使用现有心跳 `sequence` 和现有隐私安全身体诊断生成匿名聚合；当前 `424cb49` 无需 OTA。

WORK-003 同样不修改固件或设备协议。完成结论复用现有提醒音频和 ACK 链路，CoreS3 无需 OTA，身体动作继续保持禁用。

WORK-004 只增加服务端个人待办、管理页面、只读 Agent Tool 和确认式语音动作；截止提醒继续使用现有提醒协议。CoreS3 无需 OTA，未连接串口、未刷写固件、未启用或执行身体动作。

## 已实现的服务地址快捷更新

- USB 严格解析器新增 `update_server` 命令，只接受类型、目标服务地址和一次性配对码三个字段；Wi-Fi 字段、额外字段、缺失配对码和不安全地址全部拒绝。
- 快捷更新不会调用 Wi-Fi 配置写入，也不会在连接目标服务前清除旧身份；它等待现有 Wi-Fi、向目标服务重新 claim 同一硬件身份，仅在成功后保存新身份。
- 新身份保存成功后先返回非秘密 `complete`，再重启以便传输任务重新加载地址和凭据。Wi-Fi 或 claim 失败时，原 Wi-Fi 和已保存身份保持不变。
- 完整配网兼容路径、URL 安全边界、一次性配对码和日志脱敏边界不变；未增加服务端远程重定向接口。

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
- 触摸触发喜爱，IMU 加速度突变触发摇晃眩晕。MEDIA-002 固件未初始化 K151 的 LTR-553ALS-WA，因此该版本如实上报 `proximity_supported=false`；BODY-001 将在驱动和回退完成后显式恢复能力。
- 静态 PNG 启用后仍保持原 A/B 资源包和全屏解码路径，并在诊断中标记当前非动态渲染。

## 已完成的 MEDIA-003

- 默认 `STACKCHAN_MEDIA003_BACKEND=native`，不包含候选组件；ESP-IDF 5.4.4 完整固件仍为 1,581,488 字节，与 MEDIA-002 最终稳定制品同尺寸。
- EAF profile 固定官方 `espressif/esp_lv_eaf_player` 0.3.0、LVGL 9.4 和 ESP-IDF 5.5.5，使用独立依赖锁。项目自有 18 帧 RLE 素材只在约 1.8 秒开机窗口显示，之后隐藏并恢复 native。
- 自有 EAF 为 53,854 字节；同口径 ESP-IDF 5.5.5 native 为 1,605,088 字节，EAF RLE-only 为 1,669,504 字节，净增 64,416 字节，最小 3 MiB 应用分区仍有 47% 余量。
- Emote profile 固定 `espressif2022/esp_emote_gfx` 3.0.5，只执行 init/deinit 并记录初始化时间和保留堆，不注册 flush、不接管显示。其 1,615,776 字节仅代表 lifecycle 链接成本，不是完整渲染性能。
- 候选依赖、sdkconfig 和生成器全部位于 `firmware/experiments/media003`；根依赖锁、稳定工具链、表达协议和服务端均不改变。
- `71868da` 已通过 LAN HTTP 应用 OTA 安装，任务为 `INSTALLED`，NVS、OTA 能力和 `motion_disabled` 保留；本轮未主动演练回退。
- 用户确认开机 EAF 片段后恢复 native、连续三次唤醒对话和回答声音正常、TTS 播放中触摸停止及下一回合正常，无黑屏、卡住或自动重启。
- 稳定心跳约 55/60 FPS、绘制 1097 μs、传输 22627 μs、锁等待 10 μs、音频 underrun 0、最低空闲堆 7,735,712 字节。

## 已完成的 MEDIA-004

- 根固件统一升级为 ESP-IDF 5.5.5，并正式依赖 `espressif/esp_lv_eaf_player` 0.3.0；软件 JPEG 关闭，只接受 RLE4。
- `expression_pack` 同时校验 V1 `SCEPKG1` 与 V2 `SCEPKG2`，V1/V2 复用原 A/B 分区和原子切换状态，不修改分区表或 NVS 布局。
- V2 严格验证 manifest、连续偏移、总/片段 SHA-256、EAF checksum、160×160 尺寸、帧/块表、RLE4 解码长度和全部资源上限。
- 正式播放器从已验证分区把单片段复制到 PSRAM，使用 LVGL EAF object 一次播放；完成、缺失、拒绝或高优先级交互到来时删除对象、释放内存并恢复原生动态球体。
- 开机、唤醒和角色切换三个事件有独立片段；同一行为不会循环重播。心跳显式上报 `lifecycle_clips_supported=true`，旧固件继续兼容。
- ESP-IDF 5.5.5 protocol profile 已编译通过；使用独立 sdkconfig 的 LAN HTTP Quad 候选也已编译通过，软件 JPEG 保持关闭。未连接设备、未发起 OTA。

## 正在进行

- BODY-001 已初始化 LTR-553 接近/环境光、Si12T 顶部触摸、PY32 舵机电源和 UART1 反馈舵机；心跳只输出能力、在场布尔、环境光分桶、运动状态与固定失败码，不输出原始传感器流。
- 上电和重启始终 `motion_disabled` 且舵机断电；校准只读取无扭矩反馈并保存中心，显式启用前再次无扭矩探测，不因重连自动恢复启用。
- 只支持 `WAKE`、`LOOK_USER`、`NOD_SMALL`、`THINK`、`DROWSY` 五个固件模板，固定幅度/速度/时长并回中；偏航中心 ±20°、俯仰 10°..80°，音频、离线、升级、错误、触摸、语音和看门狗均可拒绝或停止。
- `d1abe9d` 两次应用 OTA 均由 bootloader 安全回退到 `fe95767`。第二次在 COM3 捕获到新镜像约 1.8 秒时于 BMI270 错误路径触发 `A stack overflow in task main`；崩溃发生在 `body_hardware_init()` 之前，未初始化或驱动舵机。构建使用的显式 profile 仍为 3584 字节，覆盖了仓库 defaults；官方 StackChan 为 8192 字节。本分支将 defaults 提升到 8192，并增加 CMake 硬失败，阻止旧 profile 再次产出候选。
- 用户已通过网页 OTA 安装 `2108f78`，启动、语音、网络和 8192 字节主任务栈正常，且保持 `motion_disabled`。首次无动作中位校准确实到达设备，但心跳记录 `servo_feedback_supported=false / FEEDBACK_FAULT`；经授权只读连接 COM3 后复现一次，失败计数从 0 增至 1，确认不是页面丢命令。当前修复将 PY32 pin 0 配置与官方实现对齐、上电等待由 100 ms 提高到 250 ms、增加三次无扭矩反馈重试和可定位失败阶段的非原始值日志。
- 用户随后通过网页 OTA 安装 `839e146`。避开语音播报后，经逐次授权在 COM3 实时复现无动作校准，三次均精确失败于 `yaw_torque_off`；相邻失败只间隔约 70 ms，确认帧头扫描中的 `pdMS_TO_TICKS(5)` 在当前 100 Hz tick 下取整为 0，扫描在舵机回包前瞬时结束。本分支已改为 50 ms 真实时钟截止，并保证每次读取至少阻塞 1 tick。
- 用户安装 `5e14d73` 后，数据库确认校准命令到达且保持 `motion_disabled`。刷新管理页并获批实时监听后，COM3 再次捕获三次 `yaw_torque_off`，相邻约 130 ms，证明真实时钟等待已生效但舵机不返回写指令 ACK。M5Stack 官方 StackChan 实现同样不依赖 `EnableTorque()` 返回值，而是继续读取反馈；当前修复改为写指令只确认 UART 发送成功，再读取扭矩寄存器确认 yaw/pitch 已关闭，最后读取两个当前位置。目标位置写入仍由后续位置反馈验证。
- 用户授权 Agent 通过 COM3 保留 NVS 反复直刷并自动测试。严格 USB 命令只接受无参数 `calibrate_body_center`，继续受语音、升级和错误门禁约束。逐层诊断确认 UART1 1 Mbps、ID 1/2、SCS 大端包、PY32 pin 0 配置均与官方一致；INA226 显示底座电池源存在。最终根因是按需开启 VM 后原 250 ms 等待不足以覆盖 TPS61088 升压轨和两路舵机冷启动，导致反馈读发生过早。改为 1200 ms 后，`9ae97ba` 连续两次完成 yaw/pitch 中位读取和持久化，均返回 `body_calibration=complete` 并保持 `motion_disabled`。
- WORK-001 在 Si12T 顶部触摸释放时识别 1500 ms 长按，只生成无参数、带序列号的严格 `workday_toggle` 设备事件；服务端在线鉴权后决定启停，传感器本身不能自动开始工作模式。
- LTR-553 环境光继续只保留 DARK/DIM/NORMAL/BRIGHT 分档；分档变化把显示亮度限制在 18%..78%，夜间模式和低亮度屏保仍有更高优先级，变暗不会停止工作模式。
- ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 工作树构建通过，大小分别为 `0x3a140` 和 `0x190fb0`，最小应用分区余量 92%/48%；环境光档位需连续三次稳定后才调整屏幕亮度。未连接、刷写或移动设备。

## 下一步操作

请用户完成两轮独立唤醒；串口继续监听每段 `buffered_ms/gap_ms/playback_ms`，并与 PCM 时长和服务端总播放阶段对比。

## 阻塞项

- 当前无实现或安装阻塞；只剩用户两轮实机段间停顿验收。既有 I2S 重复停用告警继续作为单独观察项保留。

- MEDIA-004 V2 实体激活因用户暂无 EAF 素材延期，不能视为失败或通过。
- BODY-001 无动作中位校准已连续两次实机通过；真实运动方向、模板限位和动作停止尚未实机验证，继续受实体动作独立授权边界约束。
- WORK-001 顶部长按启动/停止已实机通过；当前设备未上报接近、环境光和舵机反馈能力，相关能力缺失降级已生效，但环境光实体档位仍不能写成实机通过。
- 摄像头、NFC、红外、舞蹈、连续旋转、任意角度协议、模型运动权限和未校准舵机保持冻结。

## 关键文件

- `firmware/main/device_transport.c`
- `firmware/main/device_provisioning.c`
- `firmware/main/device_provisioning.h`
- `firmware/main/device_protocol.c`
- `firmware/main/voice_service.c`
- `firmware/main/voice_protocol.c`
- `firmware/main/voice_control.c`
- `firmware/main/firmware_ota.c`
- `firmware/main/expression_pack.c`
- `firmware/main/lifecycle_clip_player.cpp`
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

- 2026-09-08 WebSocket 启动内存纠正与安装：`4444860` LAN HTTP Quad 为 1,650,016 字节（`0x192d60`）、SHA-256 `23E94ACCF3CC72CC2D95B5DE6408E372924D61A72675FC38AE2A1A8853826536`、分区余量 48%，镜像校验有效。经 COM3 保留 NVS 覆盖后，8 MiB PSRAM、Wi-Fi/身份、LAN HTTP、WakeNet 和 `motion_disabled` 保留；7168 字节 WebSocket 栈在 7680 字节最大连续块下成功创建，连接事件实测仍余 4000 字节栈并稳定在线超过一分钟。服务端数据库收到 `4444860 / motion_disabled` 新心跳。

- 2026-09-07 段间播放预取候选：ESP-IDF 5.5.5 protocol 和 LAN HTTP Quad 完整构建通过；LAN 应用 1,649,632 字节（`0x192be0`）、SHA-256 `C4865B29FA43CB8DEC0DE7B65E18A25CCB6B9D220681C73DB36EB994316395C0`、分区余量 48%。配网、传输、语音三组栈负例/正例通过；带 `-fstack-usage` 的真实 protocol 编译确认语音、上传、播放任务外部调用余量分别为 28,000、22,320、24,416 字节。工作树制品为 `ddc2c30-dirty`，只作提交前构建证据，尚未连接或刷写。

- 2026-09-05 边录边传实机纠正：`b2303e8` 两轮主请求分别为 9,600/32,000 PCM 字节，尾部 136/17 ms，ASR 分别为“嗯”和“No”；另有 9,600/6,400 字节连续对话短请求被供应商以 HTTP 400 拒绝。设备 PSRAM 始终约 7.7–8.0 MiB 可用且无复位，排除内存耗尽。根因是每次同步 HTTP 打开/写入暂停同任务 I2S 采样。纠正候选以独立 24 KiB PSRAM 上传任务并发写入，并要求两个连续 100 ms 起始窗口；protocol 为 `0x3a140`、余量 92%，LAN HTTP Quad 为 `0x1927a0`、余量 48%。双任务栈负例/正例及真实预算通过：语音 32,768 字节栈外部余量 28,016，上传 24,576 字节栈外部余量 22,320。待 COM3 安装和纠正复测。

- 2026-09-04 边录边传候选：新增本地 VAD 命中后的 chunked WAV 实时上传、100 ms 录音窗口、约 800 ms 静音判定、100 ms 尾部保留、开放长度 WAV 头和终止前有界回退；服务端新增固定同源 live 端点并在 ASR 前规范化实际长度。语音相关定向 18/18、排除既有 8 个 Windows loopback 类后的服务端 458/458、三组任务栈负例/正例和文档检查通过。带 `-fstack-usage` 的 protocol build 为 237,888 字节（`0x3a140`，92% 余量），实时采集最坏本地路径 4,752 字节、外部调用余量 28,016 字节；LAN HTTP Quad 工作树 build 为 1,647,712 字节（`0x192460`，48% 余量）。首次并行 LAN build 在第三方 esp-dsp 编译单元触发 GCC 内部错误，改用 `ninja -j1` 后完整通过；未连接或刷写 CoreS3。

- 2026-09-04 `a70fb9c` 实机收口：从干净提交构建的 LAN HTTP Quad app descriptor 为 `a70fb9c`，应用 1,644,400 字节（`0x191770`）、SHA-256 `966101854AF06D3089C174F9619D625F2D326FACD7AE3C03A0D3C5E594C2FEA0`。经用户明确授权通过 COM3 写入 bootloader、分区表、factory app、OTA data 和 srmodels，全部写后哈希通过；未擦除或写入 `0x9000` NVS。启动确认 ESP-IDF 5.5.5、8 MiB PSRAM、`WiFi/LWIP prefer SPIRAM`、LAN HTTP、保留的 Wi-Fi/身份、WakeNet 和 `motion_disabled`。第一轮主请求 80,044/80,044 字节，连接 18 ms、上传 286 ms；第二轮主请求 72,044/72,044 字节，连接 17 ms、上传 436 ms；两轮均约在上传完成后 6.3 秒开始正常回复播放，无写超时、崩溃或复位。期间连续对话附加请求 16,044 字节/106 ms 和 80,044 字节/345 ms 也完整上传。内部 SRAM 历史最低值为 36 字节，说明水位仍紧但未再阻塞上传；末尾一次短附加请求上传后在回复阶段 `ESP_FAIL`，设备安全恢复并重新监听 WakeNet，另有既有 I2S 重复停用告警，均作为独立观察项。全程未启用或执行身体动作。

- 2026-09-04 内部 SRAM 第二轮修复：启用 ESP-IDF 5.5.5 官方 `CONFIG_SPIRAM_TRY_ALLOCATE_WIFI_LWIP`，语音 HTTP RX/TX 缓冲和上传块均限制为 1 KiB，保留 15 秒写超时并增加 `opened` 内存快照；回归脚本新增配置与上限门禁。三组负例/正例栈预算全部通过；带 `-fstack-usage` 的真实分析构建确认配网、语音、传输任务外部调用余量分别为 8,704、28,112、23,904 字节。生成的 protocol 与 LAN HTTP Quad 配置均包含 PSRAM 网络分配和 8,192 字节主任务栈；工作树构建分别为 237,888 字节（`0x3a140`，92% 余量，SHA-256 `5745737569EF4184DFE717C06E392C02F90F1A0271BBFAE6AAFF267BBDB6CC33`）和 1,644,400 字节（`0x191770`，48% 余量，SHA-256 `9E4C1C403265D4D47F3467F2480AF1C27B6E58EE0588A5617F2CB1A687F9F4F3`）。制品版本为 `e9f5916-dirty`，只作提交前构建证据，未连接或刷写设备。

- 2026-09-04 语音上传实机验证：从干净 `0e7c641` 构建 LAN HTTP Quad，app descriptor 为 `0e7c641`，应用 1,644,000 字节（`0x1915e0`）、SHA-256 `F1AB679ECBF72534EE83769162FA5E2F33F2FAEABA0AE19DA8BF80409C35C543`。经用户明确授权通过 COM3 写入 bootloader、分区表、factory app、OTA data 和 srmodels，全部写后哈希通过；未擦除或写入 `0x9000` NVS。启动确认 ESP-IDF 5.5.5、8 MiB PSRAM、LAN HTTP、保留的 Wi-Fi/身份、WakeNet 和 `motion_disabled`。第一轮捕获 88,044 字节，在 49,152 字节处于 59.676 秒后 `ESP_ERR_HTTP_WRITE_DATA`；第二轮捕获 64,044 字节，在 8,192 字节处于 30.729 秒后同错。两轮内部 SRAM 历史最低值均为 32 字节，服务端均未进入语音控制器日志。主机尚余 7.76/31.84 GiB，服务容器 576.6 MiB/3.825 GiB、CPU 0.42%、未 OOM，排除主机或 JVM 总内存不足。

- 2026-09-03 语音上传延迟修复：移除语音路径的整块 `esp_http_client_set_post_field`，改用 `open/write/fetch/flush` 的 4 KiB 显式分块流程；上传期间临时使用 `WIFI_PS_NONE` 和 15 秒单次发送超时，结束后恢复原状态。日志覆盖内部 SRAM 当前空闲、最大连续块、历史最低值、PSRAM 空闲、发送字节和耗时。流式上下文迁到 PSRAM后，`-fstack-usage` 实测语音本地调用链为 4,656 字节、32,768 字节任务栈外部库余量 28,112 字节；12,288 字节负例拒绝、32,768 字节正例接受。ESP-IDF 5.5.5 protocol 为 237,888 字节（`0x3a140`）、余量 92%、SHA-256 `18D3F3564272A891B69ABB935333C9B33757600819E31A9FE093FD32FA7157F7`；LAN HTTP Quad 为 1,644,000 字节（`0x1915e0`）、余量 48%、SHA-256 `6FB1E81C38A331397DFBB599BBC68CFEDE2A159E47A0E8281A20A2FCEE7C60F4`。未连接设备、未刷写、未发起 OTA。

- 2026-08-30 WORK-001 实机入口：用户自行安装 `424cb49` LAN HTTP Quad，设备持续在线并保持 `motion_disabled / DISABLED`。临时启用周日后，1500 ms 顶部长按成功启动 `ACTIVE_PRESENT`，第二次长按停止为 `OFF`，跨调度周期稳定。COM3 经授权只读监听 30 秒无异常输出，未发送串口命令；当前心跳未上报接近、环境光和舵机反馈能力，固件按能力缺失路径降级。全程未启用或执行身体动作。

- 2026-08-29 WORK-001 整体固件：ESP-IDF 5.5.5 protocol profile 为 237,888 字节（`0x3a140`）、余量 92%、SHA-256 `82E30272EF2231CD2765E9EB90137F587FAF3A3798DF4884DF0A1B5035D8C767`；LAN HTTP Quad 为 1,642,416 字节（`0x190fb0`）、余量 48%、SHA-256 `60A480C0700A4278F3B0A9730669460FD765C78D5EAD4C4D00C8A31A14C7D550`。配网、语音、传输三组栈预算回归与静态预算全部通过；制品版本为 `6c750ff-dirty`，只作工作树构建证据，不是安装候选，未连接或操作 CoreS3。
- 2026-08-29 BODY-001 冷启动修复：官方原理图确认 `VM=5V` 由 `BAT+` 经 TPS61088 升压且由 PY32 `VM_EN` 控制，INA226 `0x41` 监测电池源。提交 `9ae97ba` 通过 COM3 仅写 `0x10000` 应用分区并校验，未写 NVS；自动 USB 诊断在语音空闲后连续两次返回 `body_calibration=complete`，两次均记录 `battery_source=present`、两路有效反馈和 `motion remains disabled`。根因是按需上电后的 250 ms 冷启动等待不足，延长至 1200 ms 后稳定恢复。全程未启用或执行身体动作。
- 2026-08-29 BODY-001 写 ACK 诊断：用户通过网页 OTA 安装 `5e14d73`。第一次点击发生在串口监听前，数据库确认固件在线、`FEEDBACK_FAULT` 和失败计数 2；第二次监听期间点击未下发，失败计数未变，刷新管理页后第三次获批点击成功到达。COM3 捕获三次 `stage=yaw_torque_off`，时间约 840467/840597/840727 ms，130 ms 间隔证明 50 ms 等待已生效。对照 M5Stack 官方 `hal_servo.cpp`，官方发送 `EnableTorque()` 后不依赖写 ACK，继续用 `ReadPos()` 判断反馈；当前修复对 SCS 写入只要求 UART 发送成功，扭矩开关改为寄存器读回验证，校准仍须确认 yaw/pitch 均无扭矩后才读取位置。protocol 为 `0x3a140`、余量 92%；LAN HTTP Quad 为 `0x190630`、余量 48%；三组任务栈回归和静态预算通过。未启用动作。
- 2026-08-29 BODY-001 回包等待诊断：用户安装 `839e146` 后校准仍为 `FEEDBACK_FAULT`。第一次实时点击恰逢机器人通知播报，被语音安全门阻止而未进入校准；停止发送通知并重新逐次获批后，COM3 捕获三次 `yaw_torque_off`，时间分别约 385464/385534/385604 ms。根因是 100 Hz FreeRTOS 下 5 ms 转换为 0 tick，固定 16 次帧头扫描没有等待回包。修复改用 50 ms 单调时钟截止和最少 1 tick 读取，protocol 与 LAN HTTP Quad 工作树构建通过；LAN 大小 `0x190530`、分区余量 48%。未启用动作。
- 2026-08-29 BODY-001 校准反馈诊断：数据库确认运行 `2108f78`、`body_motion_supported=true`、`servo_feedback_supported=false`、`body_calibrated=false` 和 `FEEDBACK_FAULT`。经用户明确授权只读连接 COM3；连接触发 `USB_UART_CHIP_RESET`，设备正常重启到 `2108f78`，启动主栈余量 4168 字节、保持 `motion_disabled`。用户按提示只点击一次校准后失败计数从 0 增至 1，未启用动作。官方 PY32/舵机实现核对确认寄存器、UART、ID 和大端协议一致，但官方上电等待 200 ms 且回包允许帧头重同步；加固后的 250 ms 等待、三次无扭矩反馈重试、帧头重同步和分阶段日志已通过 protocol 与 LAN HTTP Quad 工作树构建，LAN 大小 `0x190510`、分区余量 48%。
- 2026-08-29 BODY-001 主任务栈修复：使用全新任务专属 sdkconfig 完成 ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 双 profile 构建，生成配置均为 8192 字节；protocol 为 237,888 字节、应用分区余量 92%、SHA-256 `42F40E01DB67BA7BA6311810ACDBC840392773DC5F3FA549D1E9EE71F9E98892`，LAN 为 1,639,088 字节、余量 48%、SHA-256 `265F60BBCC767ACBD04A3F9A65F0A261E0A4C35FF3EB7E5C89359859704D768D`。旧 3584 字节 profile 已被新增 CMake 门禁明确拒绝；配网、语音、传输三组栈预算回归和静态预算，以及文档检查均通过。两份制品来自提交前工作树，只作构建证据，不作为 OTA 候选；未连接或操作 CoreS3。
- 2026-08-29 BODY-001 首个实体候选：`d1abe9d` 下载、摘要校验和 OTA 分区写入成功，但两次启动均自动回退。COM3 复现确认新镜像 app descriptor 正确，随后 BMI270 初始化失败并触发 `main` 任务栈溢出；bootloader 回到 `fe95767`，NVS、Wi-Fi、身份、语音和 `motion_disabled` 保留。根因为生成 profile 仍配置 3584 字节主任务栈；不是下载损坏、K151 I2C 总线或身体任务崩溃。
- 2026-08-29 BODY-001：protocol profile 编译通过，244,624 字节、应用分区余量 92%、SHA-256 `DC7925D95E98BA3B23660271311BF84179D3C04E51B9D07B254D1786EC0F90DF`；LAN HTTP Quad 编译通过，1,652,400 字节、余量 47%、SHA-256 `E5E0643B95F8F368F397898AC19EC3E8110B2367F4ECCE7C9AE9B985A807B434`。配网、语音、传输三组任务栈回归和静态预算均通过；未连接或操作 CoreS3。

- 2026-08-28 经用户明确授权，将提交绑定的 LAN HTTP Quad 候选通过 USB `COM3` 安装到当前 CoreS3；app descriptor 为 `fe95767`，制品 1,618,560 字节，SHA-256 `0FBFA97917748757FAF2EB6AE87B8C88EE24D2DC1C22760DD7DCA95E59962B44`。只写 bootloader、应用、分区表、OTA data 和 srmodels，未全片擦除、未写 NVS 分区；启动确认 NVS 加密、Wi-Fi 配置、设备身份、WakeNet、显示/触摸/音频、BMI270 与 `motion_disabled` 保留，设备随后连接当前服务并上线。
- 2026-08-27 USB 服务地址快捷更新：ESP-IDF 5.5.5 protocol profile 编译通过，应用大小 `0x3bb10`（244,496 字节）、最小 3 MiB 应用分区余量 92%；LAN HTTP Quad profile 编译通过，制品 `0x18b280`（1,618,560 字节）、最小应用分区余量 49%，SHA-256 `B4950956B69C385B6E7269341DCC2B46E6746C9B3E14C2793470856904C9E98F`。三组配网、语音和传输任务栈回归与静态预算全部通过。该制品来自提交前工作树，只作为构建证据，不作为安装候选；未连接或操作 CoreS3。
- MEDIA-004 ESP-IDF 5.5.5 protocol profile 编译通过，应用大小 237,776 字节；独立 sdkconfig 的 LAN HTTP Quad profile 编译通过，制品 1,618,336 字节、最小 3 MiB 应用分区余量 49%，SHA-256 `BBF266A5FB77A47198FDC1EC4D22D9962DE1F32C054F69EC7FE4908AC84263F2`。该制品来自未提交工作树，只作为构建证据，不作为 OTA 候选。
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
- [工作日桌面陪伴 V1 开发设计](../workday-companion-v1.md)
- [0041：私用优先的确定性工作日陪伴闭环](../decisions/0041-private-first-deterministic-workday-companion.md)
- [设备协议 v1](../../protocol/device-v1.md)
- [0004：LAN HTTP 仅限开发固件](../decisions/0004-lan-http-development-only.md)
- [0018：触摸取消](../decisions/0018-touch-control-and-voice-turn-cancellation.md)
- [0027：有界连续对话](../decisions/0027-bounded-continuous-conversation.md)
- [0031：应用固件 OTA](../decisions/0031-safe-application-firmware-ota-and-health-center.md)
- [0037：有序分段语音播放](../decisions/0037-ordered-streaming-voice-playback.md)
- [0049：本地 VAD 门控的边录边传](../decisions/0049-local-vad-gated-live-voice-upload.md)
- [0038：分层动态球形表情与兼容资源包](../decisions/0038-layered-expression-rendering-and-resident-appearance-catalog.md)
- [0039：原生连续渲染与有限 EAF 生命周期片段](../decisions/0039-native-renderer-with-bounded-eaf-lifecycle-clips.md)
- [0040：版本化表情资源容器与互斥活动槽](../decisions/0040-versioned-expression-pack-container.md)
- [表情资源包 V2](../../protocol/expression-pack-v2.md)
- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)
- [动态球形表情实机验收](../../runbooks/dynamic-expression-smoke-test.md)
- [MEDIA-003 EAF 实机验收](../../runbooks/media003-eaf-smoke-test.md)
- [EAF 生命周期资源包实机验收](../../runbooks/eaf-lifecycle-pack-smoke-test.md)

## 安全与兼容性约束

- 未经用户逐次确认设备、端口、profile、提交和 NVS 策略，不连接 COM3、不刷写、不发起 OTA。
- 设备继续拒绝任意 URL、跨源制品、未知命令字段和运动启用。
- 不记录 Wi-Fi 密码、配对码、设备 Token、音频、转写或供应商密钥。
