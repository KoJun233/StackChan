# DEVICE-008 软件候选与现场验收

- 文档状态：ACTIVE
- 最后更新：2026-10-04
- 分支：`codex/device-interaction-upgrade`
- Git 基线：`effa511`
- 当前范围：软件实现、回归、编译与同源候选，以及明确授权后的V56部署/应用OTA安装；相机和实体运动未开启
- 已安装历史组合：companion007-d3459d2/V55 + d8b6444；不作为本轮成绩

## 软件事实与原因分析

原舵机在每次总线操作/反馈之后相对等待25 ms，通信和调度延迟会累积到运动时长；对舵机自身受速运动又逐点插值，容易出现断续目标与段间停顿。本轮改为根据绝对已过时间的五次规划，192 raw/s速度上界，迟到合并过期目标，记录有界聚合时序；保留所有硬件反馈、限位和 watchdog。41 种注入延迟证明规划时间/终点/单调性，不能证明实体卡顿的唯一根因。供电、机械负载、反馈总线延迟与实机表现仍待测。

本地跟随和摸头动作只在已经 armed、校准、在线、状态新鲜/允许、无主动语音/模态等条件下触发。被动 WakeNet 采样可与空闲检测共存；头部实际动作必须先暂停采样并获得严格音频门禁。动作不能自动 armed，运动中的摸头仍急停；非中心位置切入模板先验证全预算，不够则先拒绝，不延长总 watchdog。

双眼使用独立 LVGL实现。图集是生产绘图代码经真实 LVGL宿主渲染，未开摄像头或取真机截图。中文字体 28073 字、979760 B；完整 Source/OFL/制品摘要在 `firmware/main/fonts/README.md`。遇到不支持的字或无效UTF-8，确认键禁用，避免在缺字内容上执行。

## 软件验证证据

| 范围 | 结果 | 证据/复现 |
| --- | --- | --- |
| 服务端完整回归 | 663/663，零失败/错误/跳过 | `.tools/stackchan-companion-tests-7b81046f0963495eb42dda49b75648e3`；`./scripts/test-server-linux.ps1` |
| 最后 V56 追加SQL | 33/33 | `.tools/stackchan-companion-tests-50da1d5e17f9498f91e5ea7cfff29d36`；`-Tests 'DeviceUiPersistenceTest,CompanionConsentPersistenceTest,WorkdaySettingsServiceTest'` |
| 事务通知/同意周期 | 定向74/74 | `.tools/stackchan-companion-tests-962a3d180d234c4eb2925144eb31e731` |
| 固件严格 UI解析 | ASan/UBSan六组170检查 | `python scripts/test-device-ui-protocol.py` |
| 运动/跟随/触摸/情绪 | 全部宿主通过 | `test-motion-planner.py` 41场景；`test-body-local.py`三组；`test-face-tracking.py`四策略/三实际采集代码模拟；`test-expression-lifecycle.py`五组；`test-ui-touch-ownership.py`四组；`test-voice-interaction.py`；`test-body-audio-safety.py`六组/500竞争 |
| 模型编译裁剪 | 两实际图及四畸形容器通过 | `python scripts/test-face-compile-requirements.py` |
| 表情视觉 | 31帧，12情绪/系统/模式/行为可区分 | `.tools/eyes-qa/`；`python scripts/test-robot-eyes-renderer.py` |
| 卡片/菜单视觉 | 14帧，实际中文/长滚动/状态禁用通过 | `.tools/device-ui-qa/`；`python scripts/test-device-ui-renderer.py` |
| 前端基线 | 41文件147/147 | 未改前端产品代码；生产构建由候选镜像日志另证 |
| 检查器 | 文档7/7、四类栈预算回归通过 | `pnpm docs:check:test`；`scripts/test-firmware-*-stack-budget.ps1`，UI十场景 |

服务端保留既有 Surefire fork退出超时警告，断言通过/BUILD SUCCESS；前端保留既有代理 ECONNREFUSED/Vite退出警告。测试容器只用临时数据库与模拟硬件，没有调用真实相机/舵机。

## 构建与容量

固定环境：IDF5.5.5、BSP4.0.0、LVGL9.4.0、ESP-SR2.4.6，human_face_detect0.5.0/ESP-DL3.3.13。两版构建使用独立 sdkconfig，开启 `STACKCHAN_STACK_USAGE=ON`。

```powershell
# 已按 development.md 激活既有 ESP-IDF 后，在 firmware 目录运行。
python "$env:IDF_PATH/tools/idf.py" -B build-device008-lan -D SDKCONFIG=build-device008-lan/sdkconfig -D 'SDKCONFIG_DEFAULTS=sdkconfig.defaults;sdkconfig.lan-http.defaults' -D STACKCHAN_STACK_USAGE=ON build
python "$env:IDF_PATH/tools/idf.py" -B build-device008-protocol -D SDKCONFIG=build-device008-protocol/sdkconfig -D 'SDKCONFIG_DEFAULTS=sdkconfig.defaults;sdkconfig.lan-http.defaults;sdkconfig.test.defaults' -D STACKCHAN_STACK_USAGE=ON build
```

默认全 ESP-DL/字体试验镜像超出原3 MiB应用槽；现已保留实际五算子（包含 LUT/RequantizeLinear辅助依赖）、36个S8 C/ESP32-S3内核及唯一使用的 RGB565→RGB888转换，启用 -Os。完整模型191296B及中文字库保留，不修改托管组件或神经网络。构建必须由 `check_sizes.py` 再次检查，最终二进制大小/摘要以候选 manifest为准，不以试验构建替代最终HEAD。

应用/OTA原槽仍 `0x300000`，候选剩余约1%；今后变化必须重新检查，不能自主改分区。协议版不包含模型/中文字库并使用采集桩，不能安装它来验收用户功能。

### 栈预算

`./scripts/verify-firmware-{voice,transport,provisioning,device-ui}-stack-budget.ps1 -BuildDirectory build-device008-lan` 通过。选定项目路径：voice4784/32768 B、上传2256/24576、播放160/24576、transport9008/32768、配网9648/20480；UIFlash224/8192内部、HTTP1216/24576 PSRAM、门禁432/6144 PSRAM、视觉656/12288内部、触摸2256/4096内部。每类独立余量门槛保留，优化克隆取最大，Flash桥接从内部栈访问NVS。

这是项目路径加库调用余量检查，**不是全第三方调用图上界或实机高水位**。现场须测峰值与连续块，检测/声音/UI同时运行；资源不足时停止跟随并显示本地失败码，不能自动改变权限或电源。

## 同源候选

最终一个中文任务提交后运行 `./scripts/build-companion-candidate.ps1`。脚本拒绝未提交/未跟踪源代码，从Git archive构建本地镜像，提取停止容器中的 app.jar/public，不发布。`.tools/device008-<HEAD前12位>-*/manifest.json` 记录 sourceCommit、sourceArchiveSha256、image/imageId、jarSha256、consoleIndexSha256、expectedSchema=56。同一HEAD重建双固件，并在manifest追加两个bin/ELF/partition/SRmodel的SHA-256、长度及构建配置指纹。

构建脚本只制作候选、不启动服务或安装固件；2026-10-03另获用户明确许可后，以下已安装记录对应冻结候选。运行环境、TLS和认证继续沿用现行设置，不把凭据放入制品清单、文档或日志。

## 获许可后的精确验收顺序

1. 核对最终来源和摘要，确认当前LAN服务器/设备。按已有备份流程保存V55恢复点，然后部署同源服务器/控制台V56；核对健康、迁移和旧设备语音确认。不要切换部署模式。
2. 只在已许可的应用 OTA流程安装 LAN用户固件，保留NVS、校准、WakeNet/SR模型和原分区；不执行编译日志提供的全Flash命令。启动核对版本、在线、舵机禁用/自动关闭、相机默认关闭。
3. 空闲左/右滑输入模式、上滑菜单；PTT长按与松手、录音提交、播放触摸停止；屏保唤醒/关闭弹窗后的释放不穿透。重启验证输入模式保留，相机关闭，手动亮度回到自动策略。
4. 待办、提醒、记忆、一次关心和伙伴相关确认：显示内容/时间/伙伴，确认只执行一次，取消无写入，停声只停提示音。检查过期、长标题滚动、离线、重复点击、后台编辑、换伙伴再切回、重连和卡片替换。旧设备仍能完整语音确认。
5. 菜单音量/夜间/安静/工作/休息/伙伴：保存成功与失败提示，控制台→菜单和菜单→控制台同步；无电池时显示未知，不伪造电量。
6. 取得本地相机测试许可并由菜单开启，舵机保持禁用，先测眼神、无脸/多人连续/短失联/长失联/启停，验证录音、播放、菜单/卡片、离线/安静/错误/更新会暂停。测实际帧率/推理时延、PSRAM峰值和内存连续块；无图像、坐标日志或上传。
7. 只有单独得到实体运动许可并确认校准/安全范围后，测试轻点头及有限人脸跟随。测目标节拍、总线/反馈时延、段间停顿、供电与负载；保留至少六秒头部间隔和唤醒空档。触摸/语音/断线优先停，任何不确定反馈立即关闭许可。不能把宿主41延迟场景或视觉测试记作实体流畅通过。

## 恢复与未验证项

应用OTA沿原A/B与健康确认机制；未通过启动健康则回滚。V56数据库迁移不自动降级，切回镜像前确认模式兼容/恢复点。发生资源、采集或体验问题先关本地跟随和舵机许可，保留有界错误码/聚合时序，按具体链路修复。

未验证：实际神经网络推理输出/3 Hz目标、图像方向、PSRAM/DMA峰值、显示回执真实LCD时序、边触/非标准触摸和实际运动自然度。应用OTA与启动/连接已通过，详见下方记录；没有相机开启、实体运动、量化交互成绩或外部推送记录。

关联：[实施计划](../project/device-interaction-upgrade.md)、[ADR0071](../project/decisions/0071-device-eyes-cards-and-local-tracking.md)、[协议](../protocol/device-ui.md)、[当前总览](../project/status/overview.md)。

## 2026-10-03 已授权安装记录（Asia/Shanghai）

用户明确允许部署同源V56服务端并通过应用OTA安装 `device008-a840d0d`，保留NVS/校准、舵机禁用、相机默认关闭。本次许可不包含相机开启、实体运动、部署模式切换、凭据轮换或Git外部推送。

- 固定候选源码 `a840d0d17194c8aecedfd9b7d19b9e1ca6a7bb4b`；冻结目录 `.tools/device008-a840d0d17194-e887dff4c6694a4c88a88ba92e8930dd`，source.tar/JAR/index和两版固件全部长度/摘要复核通过。安装后只补文档并压缩同一个任务提交，制品来源不会被文档提交标识代替。
- 停写V55备份/隔离恢复V56：`./scripts/deploy-body-motion.ps1 -CandidateImage stackchan-foundation-server:device008-a840d0d17194 -BuildVersion device008-a840d0d -ExpectedSchema 56`。环境仅版本改变、原卷与LAN模式保留；备份 `stackchan-release-20261003030629` 含数据库、Skill、原镜像和DPAPI配置，恢复验证数据计数、加密字段、原管理员及隔离测试登录通过。原数据库没有被恢复覆盖。安装后 `backup-runner.sh verify-latest` 再通过。
- 运行镜像ID `sha256:dfc6d4e86df7b373f6410c07357e272d23d9769df2fde93d8752da9ce39e9780`；健康ok、DB V56、控制台HTTP200。运行JAR SHA256 `A5EFA75B42CCFD0CFB8CAE0C7E8AA17F5B0B1F3E37A8ADE3D304CC94778E8D30`、index `D6AC49BDA9087AEC2889330FCCC9B099227BB6DAA38B4799F2AD366CA11C43E7` 与manifest一致。
- 只安装LAN功能版应用bin：版本 `device008-a840d0d`，3,111,408字节，SHA256 `13F6135313F11A461D71E2F71655F7C7DF99949A96AF6E42822763A5075FB72E`。OTA任务 `ddd049cd-2dc4-44d8-8764-9bb520b77615` 于03:09:10 +08:00进入INSTALLED，失败码为空；协议测试版、bootloader、分区表及SR模型均未刷写。临时安装管理员已清理（剩余0）。
- COM3只读脱敏启动采集150秒，不发串口指令或复位。启动 `motion_disabled`，`calibrated=yes power=off`，新固件WebSocket重连；最终心跳新版本、DISABLED、自动动作false且新鲜，没有READY/INSTALLING任务，服务最近错误/异常行0。校准/NVS保留来自应用OTA边界及启动读回，不宣称逐字节NVS审计。
- 启动观测：Wi-Fi初始化后内部空闲24,411字节、最大连续14,336；WebSocket事件回调执行任务当时累计最小空闲栈3,996字节（IDF接口返回字节）；该任务为device_ws/7 KiB，不是32 KiB的device_transport任务。这是该次启动时的高水位，未覆盖语音/菜单/检测并发，不能作为完整运行余量已达标；相机开启前仍需峰值与连续块测量。
- 只读设备UI GET：认证HTTP200、v1、8192字节内UTF-8/no-store与字段边界；无认证HTTP401。使用仅内存60秒认证，不保存或打印密钥、令牌、角色私有内容；未替用户创建/确认个人数据。此检查不代替真实设备SHOWN回执或触摸验收。

本机脱敏证据 `.tools/device008-ota-install.log`、`.tools/device008-ota-startup.log`、`.tools/device008-install-receipt.json`。这是首次安装历史；后来离线与恢复详见下方记录。相机默认关闭且没有发送开启指令，尚未独立读取运行相机状态。相机与实体运动测试需另获许可，不以软件模拟或成功安装记作现场功能通过。

## 2026-10-03 离线诊断与修复候选

以下为当时离线/待许可候选快照；当前安装与在线事实由2026-10-04记录替代。

用户报告设备离线、无法验收。22:20 +08:00新鲜检查服务健康ok/V56且容器刚重启，设备最后心跳03:20；主机以太网192.168.1.6，旧192.168.1.4:8080不可达、新地址HTTP200。只读串口90秒无新日志；普通重启22:31 +08:00明确报 `Wi-Fi monitor initialization failed: ESP_ERR_NO_MEM internal_free=34407 internal_largest=12800` 和 `Transport task did not start: ESP_ERR_NO_MEM`，没有崩溃。校准yes、舵机power=off。第一阶段成功安装与当次启动不能代替后来冷启动稳定性，当前设备仍离线。

修复代码：预留voice/transport/provisioning与ui_flash内部栈；voice_task入口等待激活，网络/UI成功后才加载WakeNet，消除其内部临时分配与Wi-Fi初始化竞争。默认关闭视觉不再预留12 KiB内部工作栈，首次明确本地启用才创建，失败/创建中关闭不打开采集门禁。关键UI Flash栈继续在Wi-Fi前预留，避免网络初始化后碎片不足；UI/语音失败不能确认新OTA健康。

电脑IP变更的维修为仅物理USB `relocate_server` 两字段请求，先静默传输与在途续期（20秒等待、打断60秒退避），再向新origin以原refresh认证续期，成功才保存并重启。原设备ID/refresh及Wi-Fi、校准保持；失败不保存并恢复传输，旧在途续期不能覆盖新origin。此操作不是重新配对或凭据轮换；生产继续由固件origin验证要求HTTPS。旧已完成OTA报告仍来自原持久记录，没有伪造修复OTA任务或清NVS。

软件验证：`test-firmware-startup.py`执行真实app_main与voice_task等待入口，覆盖四种失败不确认OTA；`test-face-tracking.py`四组策略/四组实际采集代码，增加零默认栈、失败重试/并发关闭；`test-device-server-relocation.py`真实解析/处理覆盖严格字段与五个失败无保存/重启；`test-device-transport-maintenance.py`实际等待/退避与旧续期保存前门禁；原voice交互通过。实际四栈脚本通过，新增provisioning→relocate→refresh→解析路径11488/20480 B、第三方余量8992；transport9024/32768，其他栈额度未减。宿主不等于新固件现场已验证。

候选：最终单个中文任务提交后构建LAN/协议版，LAN版本 `device008-r1-<提交前7位>`；`.tools/device008-offline-repair/manifest.json` 固定sourceCommit、bin SHA/长度、配置/ELF/分区摘要、已运行V56镜像来源及待安装标记。预览编译3 MiB槽检查通过，最终大小以manifest为准；不安装preview/协议版，不更改原冻结a840d0d制品。

已只读核对分区与原冻结表一致（SHA256 12ABDA22950E4D36E1E5B125385629BAF45FB48E289328FFB421EB6593D3C130），OTA两个VALID记录seq25/26，CRC依据IDF otatool公式校验；当前ota_1地址0x610000、大小0x300000。读取范围仅公开分区/OTA元数据，不读NVS；没有执行write_flash、地址迁移或外推。证据 `.tools/device008-offline-serial.log`、`device008-offline-restart.log`、`device008-live-partitions.bin`、`device008-live-otadata.bin`。

下一操作需明确USB仅应用槽刷写与物理origin迁移许可，旧应用OTA许可不当作新USB维修许可。获许可后：

1. 核对manifest、LAN bin版本/SHA、COM3对应设备及新鲜启动槽/CRC；用esptool verify_flash验证当前0x610000应用仍与原a840d0d冻结bin一致。若槽/状态/版本变化，停止写入并重核，不能猜地址。
2. 只向确认的ota_1应用地址写修复bin，flash参数keep；不运行IDF提供的完整flash命令、不写bootloader、分区表、otadata、NVS或SR模型。旧冻结app仍保留作为人工维修恢复点。USB覆盖已VALID应用槽不具备新应用OTA的自动健康回滚，失败时保留证据并用原应用恢复；不能宣称OTA新任务INSTALLED。
3. 普通重启采集脱敏启动日志，必须没有Wi-Fi NO_MEM、校准有效、电源off且Wi-Fi可连。物理地址迁移 `python scripts/relocate-device-server.py --port COM3 --server-origin http://192.168.1.6:8080 --apply`；缺少apply只输出计划、不接串口。仅成功回执后等待重连，认证不通过不得重配/改密钥。
4. 核对新版本/新鲜心跳/服务健康、保持motion_disabled/DISABLED和自动false，再做第二次普通重启稳定性核验。先恢复在线再让用户操作屏幕；相机/实体运动保持关闭。以后应由用户在路由器固定服务器DHCP租约或使用稳定主机名，本次不擅自修改电脑网卡/路由器。

## 2026-10-04 已授权USB修复与恢复在线

用户对具体COM3仅当前应用槽安装、迁移origin至 `http://192.168.1.6:8080` 和两次重启验证明确答复“允许”。未授权相机开启、实体运动、部署模式切换、凭据轮换或外推。本次只恢复设备连接，不替用户确认生产数据或把屏幕交互记作通过。

- 冻结修复来源 `714c030386464d1599620876497322e777ce9346`；LAN版 `device008-r1-714c030`，3,112,288字节，SHA256 `83DF3160E9BD8179E6CDB1A86BFDD23FAD898A3E011E1A1466DCFB1B8D235ED4`，原3 MiB槽剩余33,440字节。安装前source.tar及全部bin/ELF/config/分区等长度与摘要匹配manifest；真实启动、跟随、地址迁移、传输维护、语音回归及四种实际栈报告再次通过。捆绑Node24.19.0/pnpm11.19.0，仅执行已有脚本；文档检查通过。
- 目标COM3，ESP32-S3 rev0.2、MAC `44:1b:f6:df:5e:c4`；新鲜读取公开分区与OTA元数据，分区表匹配原冻结表，seq25/26均VALID且CRC正确，当前ota_1为0x610000/0x300000。先esptool verify_flash原a840d0d应用摘要通过，再以flash mode/frequency/size keep仅向0x610000写修复应用；写入hash验证及独立verify_flash读回均通过。未写bootloader、分区表、otadata、NVS或SR模型，公开分区与otadata前后逐字节一致；原应用保留为人工恢复点。此USB安装没有新增OTA任务或自动健康回滚，不把旧OTA INSTALLED目标改成修复版本。
- 第一次普通USB复位启动（00:40 +08:00）Wi-Fi初始化成功：internal_free=40471/internal_largest=31744，先前NO_MEM消失；WebSocket连接、修复版本新鲜心跳，校准yes/舵机power=off。这次迁移前已连接现行服务器，因此先前主机旧.4不可达是网络事实，未读取设备原NVS origin，不能认定IP变化已经阻断其实际配置；已证实的启动故障为Wi-Fi内存不足。
- 物理USB严格origin迁移收到 `complete`；只有固件用原身份在拟定origin正常认证续期并成功保存才返回此回执。没有重新配对/轮换refresh或修改Wi-Fi/校准。第二次普通USB复位（00:42 +08:00）再次成功初始化Wi-Fi：internal_free=40479/internal_largest=31744；约8秒建立WebSocket，90秒脱敏观察无NO_MEM、异常复位、崩溃或watchdog。两次USB普通复位不是实际断电或长期稳定性测试。
- 服务镜像仍为 `stackchan-foundation-server:device008-a840d0d17194` / 原imageId，产品源码与a840d0d一致、DB V56健康，没有重复部署/迁移/恢复。设备UUID不变，新鲜心跳 `device008-r1-714c030` / motion_disabled / DISABLED / calibrated=true，自动动作false，无READY/INSTALLING任务；旧OTA任务仍为INSTALLED / device008-a840d0d。相机按已验证启动路径默认关闭、未发送开启操作，未独立读取相机遥测。
- 00:50 +08:00最终只读核验，最新心跳00:50:11、年龄约16.6秒；第二次重启约七分钟后仍在线。该观察只证明本次有限时段启动/连接恢复，不宣称长期稳定性、输入模式持久化或真实语音体验已验收。

本机脱敏证据：`.tools/device008-r1-usb-install.log`、`device008-r1-readback.log`、`device008-r1-{preflight,postflight}-{partitions,otadata}.bin`、`device008-r1-first-startup.log`、`device008-r1-origin-relocation.log`、`device008-r1-second-startup.log` 和 `device008-r1-install-receipt.json`。冻结manifest保留构建时的待安装标记，另用安装receipt记录这次执行结果；不覆盖历史制品或原安装记录。安装后文档与交接压缩入原单个中文任务提交，冻结来源714c030仍独立可复验。

下一条精确操作：用户在已恢复在线的修复版进行上方第3–5步屏幕验收（左滑PTT、右滑WAKE、上滑菜单、固定内容确认/取消及设置保存结果）。实际屏幕触摸/显示回执、语音功能、相机推理并发峰值与运动自然度不由本次连接核验替代；相机和实体运动仍保持关闭，其测试需独立许可。稳定服务器DHCP租约/主机名尚未配置，本次未修改网卡或路由器。

## 2026-10-04 屏幕反馈与迭代访谈

用户定性确认PTT/WAKE切换、交互及唤醒正常；控制中心能打开，但点击选项会死机重启，要求大图标入口/独立配置页，改善文字和双眼颗粒及流畅度。原连接修复不代表这些配置点击路径已通过。只读90秒COM3被动脱敏采集 `.tools/device008-menu-passive-diagnostic.log` 无相关行，没有复位或发送串口命令，也没有panic，因此根因仍未确认。

源码证实WAKE右下✓用于提前提交、默认30–60自适应/播放20硬限、14px/1bpp文字，以及dirty时整页删除重建。瞬时DB20/20为IDLE_SLEEP屏保，不是活动实测。详细事实、原LCD带宽/容量边界与用户已选Q1–Q9见[显示与交互设计](../project/device-display-refinement.md)。当前等待整套设计共识再实施，未制作或安装此轮固件；相机/运动继续关闭，不改现行服务或生产数据。


## 2026-10-04 显示候选的软件验证与待安装边界

Q1–Q10整套设计已确认并授权软件实施，详见[新设计及软件证据](../project/device-display-refinement.md#本轮软件结果与验证)。用户在600秒COM3被动采集中点击普通配置，回复“这次没有重启”；窗口无panic，间歇性重启根因仍未知。两页圆图标/独立配置、保留对象回执、松手一次提交、退出/重开继续保存、固定ID重试、WAKE轻点结束/下滑取消、灰阶菜单及192px双眼脏区已完成。

完整LVGL ASan/UBSan/泄漏、真实pointer/生产worker回执，RGB565菜单22场景，双眼31场景及240次局部/全绘逐像素对照通过（2352980/8847360像素）。实际wrapper 400区间与ready恰一次、相位隔离、模拟task/ISR边界通过；不代替实机DMA/ISR测量。最小五类宿主及情绪/触摸归属/500音频动作竞争通过，原四种实际栈/四检查器回归及新增项目/LVGL/port显示栈通过，第三方完整调用/实体高水位仍未测。

预览LAN 3139312B、原3MiB槽剩余6416B；协议版亦编译通过（最终提交绑定版本将重新编译）。候选提交、源码归档、最终双制品/配置摘要与实际大小由 `.tools/device-display-candidate/manifest.json` 固定。仅应用候选可安装，协议测试版不可安装；预览/软件成绩不能代替安装后的启动、点击稳定性、讲话听感或实测60。服务器/V56/设备714c030、原恢复manifest和分区/身份/校准保留，相机/运动继续关闭。

下一条精确操作：取得针对具体显示候选的安装许可，遵循应用OTA或仅应用USB流程核验来源、启动/在线，再多次操作所有配置，采集脱敏 `Expression frame diagnostics`、刷新像素/等待、操作/构建栈与连续内存。相位generation相同的相邻窗口才可用完成计数差统计该场景实际完成FPS；最近128个同相位区间输出nearest-rank p50/p95/p99。旧actual_fps仍为渲染调用统计，audio_errors为既有codec/写入失败计数，均不是LCD扫描同步/完整XRUN探针。尚未取得这轮新应用安装许可，不外推。

候选冻结前只读在线核验：2026-10-04 18:25（Asia/Shanghai），公开 `/api/v1/health` 返回ok；原服务器镜像仍a840d0d。设备714c030最新心跳18:25:20，查询年龄22秒，body_motion_state=DISABLED、校准有效。没有把这些旧已安装版本成绩记为显示候选通过。

## 2026-10-04 首轮显示候选授权安装与实测（跨至10月5日）

用户明确答复“允许安装这个候选，再实测配置稳定性及待机、录音、讲话帧率”。固定来源b760f9281d5eeb755f6223ae13f3b0614fa0a8d2；LAN `device008-ui-b760f92`，3139312B，SHA256 4658F376324AD12D575379C300AA34CF9D5DF6169083DAFE0AE84756546FFD2E。source.tar和两版制品摘要复核、最小五类宿主和docs检查通过。仅应用OTA，未写bootloader/分区/NVS/模型/校准，未开运动或相机，未部署服务/外推。

`install-body-motion-ota.ps1 -Install` 严格指定当前714c030、目标b760f92、冻结bin大小与SHA，任务cb688c12-6377-4cb9-aefd-692d281956e5于2026-10-04 23:47:02 +08:00 INSTALLED，失败码空，release摘要/大小匹配；临时安装管理员剩余0。启动Wi-Fi internal_free32467/internal_largest22528，WebSocket连接，校准有效/舵机power=off；2026-10-05 00:17核验新版本心跳年龄16秒、motion_disabled/DISABLED、health=ok/V56。现行服务器a840d0d保持。

COM3无串口命令/复位的1200秒脱敏捕获，23:47:00至00:06:00新固件有效观察约19分钟，仅预期OTA软件复位、无panic/watchdog。36次菜单构建覆盖八页，14.028–28.172ms是构建耗时，不是端到端点击延迟。用户答复菜单与返回正常、唤醒提交和取消正常，但音量不能调，等一分钟没语音回复，菜单不好看。

显示测量：用设备单调时间、只合并相邻相同phase和generation窗口。待机122窗口/610.67秒加权60.651FPS（59.8–61.8）；处理13窗口/65.01秒60.621FPS。p95最高快照19.003ms/p99 19.659ms来自各最近128区间，不冒称全会话百分位；统计是最终SPI完成而非LCD扫描同步。录音短静止样本无新传输，讲话未开始，完整录音/讲话目标待补。UI最小空闲栈1820B、codec错误0，不替代完整余量或听感。

语音原回合上传尾部28.375秒/总30.982秒，内部采样free最低1463B/largest736B、历史minimum16B。只查询安全阶段元数据：ASR587ms、LLM1045ms、TTS612ms均已完成，设备没有PLAYBACK_STARTED，随后用户触摸取消；不读取或记录转录/回复文本。不能称完整语音验收通过。用户本地跟随操作记录一条关闭成功和两条开启资源失败；Agent未发送相机开启，失败门禁没有启用采集，不记相机成绩。

首轮证据 `.tools/device-display-candidate/physical-{ota-install.log,startup-and-interaction.log,install-receipt.json,analysis.json}`，源码/制品manifest保持冻结；后续文档提交不作为已安装源码。

## 2026-10-05 现场反馈的软件修正候选

音量明确根因是固件精确HTTP URL白名单缺UI路由；此前宿主替代URL函数，漏集成。已加入仅state/settings/UUID确认卡片/shown路径，真实endpoint进入ASan/UBSan全LVGL测试，非法查询/尾随/穿越仍拒绝，验证状态首次获取、滑动松手仅一次且音量真的变化。菜单关闭清理控件但保留在途请求/结果。

语音仍待现场证明根因；为实测内部堆压力释放显示栈占用，UI6KiB/LVGL8KiB用PSRAM，不减容量，Flash/NVS栈与DMA仍内部。小块上传TCP_NODELAY，接收解码/分配失败主动清除客户端归属并关连接、保留错误码，避免IDF忽略ON_DATA返回值继续等超时；新增脱敏分片/失败数值日志。生产函数故障注入通过，不能记作现场语音已恢复。

用户明确选择简洁watchOS样式：64px低饱和圆图标、分页点、减少顶栏/提示，配置细滑条和大触摸区。22个新目录RGB565场景、真实交互/endpoint、流接收错误、原最小/显示完成与选五栈通过；最终单提交绑定双制品、容量/摘要/源码归档冻结 `.tools/device-display-r2/manifest.json`，未安装。

下一操作：新修订具体制品获安装许可后，仅应用OTA；启动先测内部free/largest和UI状态/音量保存，再验证完整WAKE或PTT→上传→首分片→播放及听感，补录音/讲话完成帧间隔。首轮候选许可已执行，另一新二进制不继承许可。保留首轮及714c030恢复点，不改服务/V56/身份/校准，不开相机/实体运动、不外推。

## 2026-10-05 第二次显示修订授权安装与持续许可

用户已明确允许本次候选，并表示“后面你都可以直接安装，不用问我”，对本项目后续固件安装给予持续许可。后续软件及来源检查通过后可直接安装，不重复询问；仍保持现有数据保留、安全及外部推送边界。

冻结来源 `1de3d7a7c86527e6f5613b5251e838a7b880b570`，LAN `device008-ui-r2-1de3d7a`，3139488B，SHA256 `F1E5EE401FE1E646C4CA4C7F58D4B31C298BB4776212A87A68D472BB48374406`；原3MiB槽剩6240B。42个冻结源码/制品/测试/预览摘要、最小五类宿主和docs检查再次通过。仅应用OTA，未改NVS/校准/模型/分区/身份；没有相机或实体动作命令、服务部署或外推。

任务 `4f3ad6a3-05de-459f-9cb3-b108477bd79f` 于2026-10-05 00:47:19（Asia/Shanghai）INSTALLED，failure_code空；新版本心跳/校准/运动DISABLED、health=ok/V56通过，临时安装管理员剩余0。Wi-Fi初始化internal_free46823/internal_largest31744，较首轮32467/22528增加约14KiB内部空间；菜单和显示栈容量不减，Flash/NVS栈/DMA仍内部。

本轮被动采集600秒，仅1次复位（预期应用OTA复位1次），异常日志0条；UI最小空闲栈2616B、内部free/largest最低采样21911/7680B，codec写入错误最高0。活跃待机59个同代次窗口/295.29秒，最终SPI完成率60.761FPS，窗口60.0–62.2；最近128区间p95最高快照19.215ms/p99 19.762ms。这是最终SPI完成计数，百分位为最近128区间的最高快照，不是LCD扫描同步/全会话百分位。本轮尚未收到用户音量/语音复验结果；没有菜单构建或录音/讲话阶段样本，不能记为通过。已发用户音量30/70松手返回重进、WAKE约15秒说话→轻点提交→完整回复听感复验，不替用户创建生产待办或合成虚假语音验收。

证据 `.tools/device-display-r2/physical-{ota-install.log,startup-and-interaction.log,install-receipt.json,analysis.json,voice-stages.json}`；构建manifest保持历史快照，独立receipt记录实际安装与持续许可，旧b760f92/714c030恢复点保留。下一条操作是接收用户屏幕/语音复验并对照日志定位，必要时完成软件验证后直接安装修订，不再重复索取固件许可。

## 2026-10-05 第三次菜单与动画修订安装

用户确认r2功能可用，要求先搜索开源陪伴机器人菜单/表情资料，再继续改善视觉。参考/许可证取舍见[视觉计划](../project/device-visual-polish.md)；自行实现原生几何和动作，没有复制GPL实现。两页四入口和原设置/手势保持。

LAN/协议双构建、原最小五类宿主、完整LVGL交互/endpoint/ASan/UBSan/泄漏、22菜单场景、31眼睛静态场景、240压力对照和640动画帧逐像素局部/全刷对照、唤醒/情绪生命周期、显示完成wrapper/语音流故障注入、实际选定五栈与分区/ELF/容量通过。栈仍是选定项目/LVGL路径估计，第三方/RTOS/ISR完整深度不在该证明内。

来源 `b872f2ff185474cac90b372d1b3419873196f1db`，LAN `device008-ui-r3-b872f2f`，3141344B，3MiB槽余4384B，SHA256 `96961C58D9407DA0A4D2256C98A785ADECF2453BBDA185F648EDD3E74E9F086F`。任务 `76d97a9e-3d3c-400f-a9dd-6c6cbdfad9ba` 于2026-10-05T01:47:04.647469+08:00 INSTALLED，无失败码；新心跳/校准/DISABLED/health=ok/V56、临时安装管理员0通过。来源/双制品/日志/预览53项摘要复核，独立receipt绑定300秒采集/分析/安装日志和冻结manifest。

本版300秒被动采集完成，活跃待机57同代次窗口/285.28秒，最终SPI完成率60.544FPS，窗口60.0–61.677。最近128区间p95最高快照19.261ms/p99 23.597ms，不是全会话百分位或LCD扫描同步。0条异常日志、0条捕获复位；UI最小空闲栈2536B，内部free/largest最低采样22055/7680B，codec写入错误最高0。本次串口开始晚于OTA启动，未捕获启动ROM/复位/初始化空间；启动健康由独立OTA回执及新版本心跳证明。listening没有可计量同代次窗口；processing没有可计量同代次窗口；speaking没有可计量同代次窗口。本次没有新的菜单构建样本，端到端点击延迟未测。尚无本版用户观感反馈，不将软件GIF当设备成绩，不把r2‘可以了’当r3验收。

捕获初次使用缺pyserial的普通Python未开始；改用现有IDF Python依赖后被动记录，未发命令或复位，未重复安装补造启动日志。来源冻结manifest和source.tar保持不变，独立安装receipt绑定新证据。未开相机/实体运动、未改身份/校准/NVS/分区/服务器/V56、未外推。下一条操作：按用户观感继续视觉迭代，并在真实录音/讲话操作时补本版帧率。

## 2026-10-05 用户验收与任务分支推送许可

用户对当前r3反馈“可以了基本没问题了”，明确要求推送。该反馈作为功能/观感定性接受；录音/讲话同代次FPS及相机/实体运动仍无新增量化成绩。现行服务器a840d0d/V56、固件冻结来源b872f2f与应用摘要保持，不再刷写或部署。

远端master已刷新仍effa511，任务相对master保留一个中文提交；实施、工作流状态和验收交接共同压缩，推送前重跑最小五类宿主、差异及文档检查。只推送 `codex/device-interaction-upgrade`，PR创建、人工审核及最终合并由用户进行，不推送master。用户components.d.ts、缓存和JVM日志不暂存/清理。
