# 固件工作流

- 状态：BLOCKED
- 最后更新：2026-10-01
- 当前分支：`codex/body-motion-acceptance-20260929`
- 基准提交：`0914e43`
- 最后验证提交：`0914e43`
- 最后验证范围：生产安全状态机六组宿主测试及 500 次竞争、两套提交绑定构建、三组实际栈预算和回归通过；`1b46c28` 普通语音回复结束后仍 `ARMED`，单次点头 `COMPLETED / NONE`，用户反馈正常；五模板和主要自动触发历史证据保留
- 最近记录的实机镜像：`1b46c28` LAN HTTP Quad（保留 NVS OTA 已安装、在线、已校准、反馈有效、低频自动动作恢复、`ARMED`；制品 SHA-256 见修复记录）

## 当前目标

2026-10-01 等待实际使用证据：用户最新答复为“尚未自然使用”。连续三轮复核都只有相同的健康快照，现行服务器无重启、无错误日志，设备新鲜 `1b46c28 / ARMED / NONE`、故障计数 0、低频自动开关开启；发布后语音回合和动作命令均为 0。实现、完整回归、部署及已通过的实体路径已交付，没有新的产品失败可独立修复。整体目标 BLOCKED，等待正常使用后的实际反馈；真实运动中本地语音抢占仍未验，按用户要求暂不继续音频工作。下一条精确操作：用户按平常需求使用后提供一次有帮助、打扰或不自然的具体感受，Agent 再核对对应链路并决定修复；用户也可在明确愿意补验时提供现场条件。软件测试和重复空闲轮询不能代替这项证据。

以下为已完成的发布阶段与历史验收快照；当前等待结论以上段为准。

2026-10-01 服务器控制范围更新后，固件仍为 `1b46c28`、源码和安装制品不变。用户新确认在旁且空间条件满足，仅重新启用反馈探测并恢复自动开关，未再发转动模板；新鲜设备/API 为 `ARMED / NONE`、校准和反馈有效、故障计数 0。当前服务器版本及发布过程见[今日安静修复](../../runbooks/device-quiet-voice-20261001.md)。

空闲语音保留先前显式动作许可的修复已完成、部署并现场验证：实际安全状态机测试、两套提交绑定构建及三组实际栈预算通过，`1b46c28` 经保留 NVS OTA 安装。用户重新在旁后显式启用并正常说话，回复结束及失败续聊后仍 `ARMED / NONE`、故障计数 0；单次点头 `COMPLETED / NONE`，用户反馈正常，低频自动动作已恢复。下一条精确操作：用户审核任务分支并继续自然使用记录；运行中真正本地语音抢占未验，不重复已通过模板。交付压缩为一个中文任务提交，详见[修复记录](../../runbooks/body-motion-audio-permit-20261001.md)。

完成 BODY-002 五个固定动作的软件验证、固件部署及逐项实机验收。用户已授权持续访问、部署和测试；需要人工观察真实转动时请用户描述现象。

当前现场结论：用户已完成在场与自动返场验收，确认固定抬头后回正，并选择保留不跟随的固定回应。断线停动用户看到中途停止；自动待办点头、结束工作困倦和网页文字思考均转动回正、无异常。`stop_audio` 的设备结果为 `STOPPED / VOICE_STOP`，但用户未看清停动瞬间，且尚未验证真正本地语音回合；用户本轮要求暂不继续音频工作。2026-09-29 自然使用启动时，用户确认头部周围安全，设备新鲜报告 `motion_armed / ARMED`，自动动作开启；用户观察启用后头部安稳。22:36 一次设备唤醒以 `NO_SPEECH` 结束，22:38 设备已为 `motion_disabled / DISABLED`，与语音开始时禁用舵机的固件逻辑一致，不能确认是否误唤醒。自动动作开关仍开，但目前不能执行；不在无人确认时重新启用。详见[现场验收](../../runbooks/body-motion-20260929-field-acceptance.md)与[自然使用启动记录](../../runbooks/companion-natural-use-20260929-start.md)。

以上为当前状态；以下 9 月 29 日与旧版本诊断是历史证据，不代表现行固件仍在空闲语音后禁用。

## BODY-002 历史进度

2026-09-29 本轮更新：服务端在 `10:57:29 UTC`、`10:57:37 UTC` 依次记录靠近和离开。工作日返场自动 `LOOK_USER` 命令 `ecca7407-54d3-4290-a29c-0a30437b04fa` 为 `COMPLETED / NONE`，用户观察到转动并回正；其轨迹按代码本来就是水平中位、俯仰抬头，LTR553 不具备位置追踪。音频停止命令抢占点头 `adf7081d-19bb-4ca5-8772-649014dc9738` 为 `STOPPED / VOICE_STOP`，实物瞬间未看清；断线点头 `87f06ce1-fe6d-4a6e-8012-ef93949d3fa8` 为 `STOPPED / NONE`，用户确认中途停下。原阈值 10/45 分钟、工作日 `OFF`、自动开关和动作禁用均已恢复，服务健康且测试账号为零。以下为本轮以前的诊断历程。

`c2d6c7a` 把 LTR553 接近通道改为 M5Stack StackChan 示例的 40 kHz / 50 ms，并读回确认 LED、脉冲、测量周期和活动模式，保留 NVS OTA `5bdea269-5f0d-4f73-b488-c2b7c123e82c` 已安装。用户随后完成 5–10 cm、1–2 cm、移开的分档测试：串口靠近时多次达到 `16–31` 和 `32–99`，移开后回到 `0–15`；但旧在场阈值 `100`，因此 `body_present=false`。这证明传感器会随靠近改变，旧阈值与本机实测范围不符。

`ffd0217` 将在场阈值调为 `16`、离开阈值调为 `15`，只计入 LTR553 标记为新数据的连续三个样本，并把分档日志限制为最多约两秒一次；在场切换仍只记布尔分类。ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 两套构建通过，LAN 镜像 1,659,776 字节，SHA-256 `90A64AD914A3FB1924A47C14CC01C830F407B2F7D5AFA87936A8D4928C8168EA`；保留 NVS OTA `bb05eb95-2363-41b5-b3c6-fe5fc200c69b` 为 `INSTALLED`。串口确认配置读回匹配、启动默认禁用、校准保留、Wi-Fi/WebSocket 在线；静置约 40 秒新鲜上报均为 `body_present=false`，动作 `motion_disabled / DISABLED`、自动开关关闭。用户实测的成对事件现已通过：先在 `2026-09-29T01:18:59.085420Z` 记录 `present=yes`，移开后在 `01:19:01.093942Z` 记录 `present=no`。监听程序在用户操作前运行，随后正常停止，错误输出为空；设备端判定通过，但旧版 25 秒心跳可能漏掉短暂在场，自动 `LOOK_USER` 尚未通过。

2026-09-29 传输修复：服务端 `DeviceEventService.recordHeartbeat` 只在心跳时调用工作日在场更新。`d18b3cd` 在固件传输层比较当前接近能力和在场布尔值与上次成功上报值，变化时最迟按 100 ms 轮询、至少间隔 1 秒提前心跳；25 秒常规心跳保留。ESP-IDF 5.5.5 protocol、LAN HTTP Quad 构建与传输栈预算通过；LAN 镜像 1,659,952 字节、SHA-256 `6C6E6002841742CAB467EDFCDD81DEF9DABB68F51FDF9072653908E9EDE13983`。保留 NVS OTA `78b3d0b6-6656-49b8-a74c-5866f61459ad` 为 `INSTALLED`，新鲜上报显示 `d18b3cd` 在线、校准保留、动作禁用、接近能力为 true，工作日 `OFF`。用户暂时无法进行新版近测，监听已停止；自动开关仍关闭。下次用户就位后运行 `pwsh -NoProfile -File .\scripts\observe-body-presence.ps1 -Seconds 600`，获取数据库 true→false，再验工作日自动返场。后者需先在工作日会话内记录在场，再记录离开；管理 API 返场时间最短 5 分钟，当前设置 45 分钟，测试后须恢复原设置。定向服务端回归 22 项通过，覆盖心跳状态转发、达到返场时间后触发 `LOOK_USER` 请求及未 armed 时拒绝。

头顶触摸急停实机验收：用户在 `c018d25` 点头开始后轻触头顶，命令 `83edccbd-2c05-4bb4-9620-d0c414c7dfa3` 于 2026-09-28 13:57 UTC 返回 `STOPPED / TOUCH_STOP`，随后的新鲜心跳为 `motion_disabled / DISABLED`、失败码 `TOUCH_STOP`。数据库确认临时测试管理员为零。通用 `NOD_SMALL` 验收脚本只接受 `COMPLETED / NONE`，因此对这次预期的中断抛出断言；命令结果和设备状态均符合急停预期。没有重复触摸测试。语音和断线期间停动仍未实机验收。

历史诊断中的 60 kHz / 100 ms 配置已由 `c2d6c7a` 改为 M5Stack StackChan 示例的 40 kHz / 50 ms，并通过寄存器读回及实测分类变化核对。参见[五动作计划](../body-motion-completion-plan.md)。

当前 `c018d25` 按 M5Stack 官方 LTR5XX 库的接近模式 `0x02` 和从 `0x8D` 单独读取两字节实现，前版恢复了相机/LTR553 共用电源，并将红外 LED/脉冲恢复器件默认值。两套 ESP-IDF 5.5.5 构建通过；LAN 镜像 1,659,344 字节、SHA-256 `10624AB541F189023AC2B6ECF67DFE1F57CFEFF224283A72A1DD2504CB860A2E`，保留 NVS OTA `77e1bae4-a1bd-4e0b-802d-cd6bc946186f` 为 `INSTALLED`。串口确认启动默认禁用、校准保留、传感器初始化、WebSocket 重连；应用 API 在线、自动开关关闭。点头回归 `5626c440-5e68-4088-a818-d1782d95b114` 为 `COMPLETED / NONE`；约 1.5 秒后远程停止的点头 `a370936e-0ae2-49e2-8217-0f2a7db6b462` 为 `STOPPED / NONE`，随后上报 `motion_disabled / DISABLED`。显式开始工作触发自动 `WAKE` 命令 `f105626d-629c-4f54-8dde-6da270b8c142` 为 `COMPLETED / NONE`；测试结束工作状态 `OFF`、自动开关和动作均禁用，临时管理员计数 0。无人靠近及用户在屏幕下方传感器窗口约 1–2 cm 多次贴近后，串口接近分档均为最低档、数据库 `present=false`；传感器 I2C 初始化成功不足以证明在场判定可用，自动 `LOOK_USER` 不验收且保持自动开关关闭。用户无需继续重复手势测试；下一条精确操作是核查器件光学/硬件或增加安全的本地自检证据，再决定是否启用该场景。语音与断线期间停止尚无本版实机证据，不能标为通过。

用户在设备旁按 `NOD_SMALL`→`LOOK_USER`→`THINK`→`DROWSY`→`WAKE` 观察了 `8a81b07`，五个命令 `bc5df0a9-6213-4e6d-b75e-f8bf154dbee8`、`acc26ab5-cb86-4981-85bd-e8648ca57bb6`、`3d3cb94e-e109-4c06-a348-1503f7f6bc36`、`187fc304-2d34-4ac8-8c9e-dd888f7c7045`、`5431da9c-a2a9-4842-8e7c-5b910ff3c4c1` 各为 `COMPLETED / NONE`；用户反馈“都没问题了”，可视为五模板实际转动及观感通过。前三项之后心跳仍暂报 `RUNNING`，验证脚本按安全前置拒绝第四项并立即禁用；脚本现等待心跳回到 `ARMED` 才继续。随后再启用，完成后两项并显式禁用。串口只读捕获未见舵机错误。该轮自动动作关闭；后续接近与停止路径的最新状态以本节首段为准；若异常立即禁用并记录。

`8a81b07` 避免剩余旧模板经过这台 K151 尚未通过严格反馈验证的校准中位以下区域：`WAKE`、`LOOK_USER`、`DROWSY` 改为安全中位及上方轨迹，`THINK` 与前版相同，`NOD_SMALL` 沿用已完成的上行 20°→回中；当已保存中位低于官方默认零位时固件拒绝向下目标。CoreS3 的 LTR553 与相机位于同一排线，固件在探测 LTR553 前接通 BSP camera 3.3 V 电源轨。两套 ESP-IDF 5.5.5 构建通过；LAN 镜像 1,658,960 字节，SHA-256 `1ADF09E193315A351CAF310218F7571BD526FAE6A8E9461B371402DF56991FB0`，最小应用分区余量 47%。保留 NVS 的应用 OTA `dde07742-d37e-45de-9155-5338c6a0fb50` 为 `INSTALLED`；启动串口确认默认禁用、校准保留、接近/环境光/触摸均支持，WebSocket 在线。数据库和 API 确认自动动作关闭。此版本设备自测 `LOOK_USER` 命令 `1bb879d6-7aee-4c7c-bf65-dcbade669b3e`、`THINK` 命令 `0fe2298c-3188-46d8-9b83-793008267170`、`DROWSY` 命令 `0b1541b2-c642-480c-b545-f029ed260c83`、`WAKE` 命令 `c9317279-d7d0-4d28-9dbc-c2d6225a6b64`、`NOD_SMALL` 命令 `00e7d903-f36b-4ca4-95d8-71610c607508` 均为 `COMPLETED / NONE`；结束后显式禁用。一次启用后验证脚本在 45 秒心跳边界误报，设备实际已启用但未发模板，立即禁用；脚本已用 120 秒的执行后确认窗口修复，前置时效仍为 45 秒。五动作的物理观感及安全抢占仍待统一现场验收；不能仅凭设备结果宣称真实可用。下一条精确操作：用户确认仍在设备旁且空间留空后，逐项观察五模板的方向、幅度、连贯与回中，任一异常立即停测并禁用；随后按 runbook 核验触摸、语音、断线、重启和自动触发。

`f369822` 的现场点头 `35da85d2-9b78-49ba-8fed-c2c4f2e18235` 为 `COMPLETED / NONE`，用户看到约 20° 上下转动、回正、无其他异常，较旧版卡顿减轻但仍略微一卡一卡。`0360bfb` 将固件固定帧调整为每约 50 ms 下发一次、每个 SCSCL 目标给 60 ms，使相邻目标衔接；严格反馈、停止和看门狗保留。ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 构建通过；LAN 镜像 1,657,984 字节、SHA-256 `EB0D3CC810855749DD32488EA02D8C458F5937BDE6DFC654658AB2852CB5BB1F`，最小分区余量 47%。保留 NVS OTA `c67b696e-9edf-479c-8780-67646b1f5762` 已安装、启动在线且默认禁用；独立日志自测 `25300abe-9068-4cbf-b2b5-2b5f9784fa2c` 和用户在旁的复测 `1ea79dc0-d252-4967-865f-82d611c87ffa` 均 `COMPLETED / NONE`，两次均显式禁用。现场自然度答复尚未收到；收到前不认定体验通过。LTR553 仍为身份读取失败，其余四模板未试，自动动作关闭。

`2bc6bfb` 第二次在用户旁复测命令 `7ee7889b-7631-478f-bd0c-1c7414aeec39` 为 `COMPLETED / NONE`，用户看见约 20° 上下转动并回正，没有左右转动（点头只驱动俯仰轴），但明显“一卡一卡”。确认原固件每约 100 ms 插值一次，而 SCSCL 目标时间 20 ms，舵机可能在两次目标之间停住。`f369822` 把固定轨迹细分为约 25 ms 目标间隔，并让等待根据剩余时间休眠；20 ms 舵机目标时间、8 raw 到位容差、触摸/语音/断线停止和看门狗均保持。ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 构建通过；LAN 镜像 1,657,984 字节、SHA-256 `849CC8A986D679B9FC46EC5C14DF94EF52B4A91B9A18A30A2A74546B5C69B91D`，最小分区余量 47%。保留 NVS 的应用 OTA `7ecc62d0-b0be-4a8f-8139-819ef4150e10` 已安装、默认禁用并重连；独立日志自测 `a00cd05a-845a-4167-ad78-eb206fb8102a` 和第二次现场复测 `35da85d2-9b78-49ba-8fed-c2c4f2e18235` 均为 `COMPLETED / NONE`，每次结束均显式禁用。等待用户回答平滑度与异常现象；在这之前不把点头标为体验通过，不试其余四模板。LTR553 仍报身份读取 `ESP_ERR_INVALID_STATE`。

用户在设备彻底断电时确认从正前方**向下可轻松拨动约 90°**，已放回先前校准的正前方并开机。只读诊断版 `2abfbbd` 的启动日志显示已保存俯仰中位相对 M5Stack 默认原始零位 620 属于 `below_factory_zero`；这不是原始位置值，也不足以单独证明实体角度，但解释了旧 `NOD_SMALL` 从校准中位再下行 10° 可能进入不适合这台设备的区域。接近/环境光传感器的初始化定位为 `identity_read / ESP_ERR_INVALID_STATE`，能力仍为 `no`。`2abfbbd` protocol/LAN HTTP Quad 构建通过；LAN 镜像 1,657,888 字节，SHA-256 `68D3DD253E37E2AB14BB635EB3380E55BD0010A46473F3A80978AF44F1199CB5`，保留 NVS OTA `b019a0f2-2824-4cd4-b45a-095de027e80a` 已安装，启动默认禁用并重连。

`2bc6bfb` 将 `NOD_SMALL` 改为相对校准正前方抬头 20°、再回到正前方（模板值 65°→45°，两帧各 1000 ms），避免旧下行目标；仍保留 8 raw 逐帧到位要求及实际位移要求。ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 构建通过，LAN 镜像 1,657,872 字节，SHA-256 `CF113B35554B63DF25D944FBA9A064910187F1217BCD65355B87F7501449DDFF`，最小分区余量 47%。保留 NVS 的应用 OTA `3343c39a-e099-465b-9828-c156a6a2203b` 已安装，启动日志确认默认禁用、校准保留、在线。一次独立设备日志自测命令 `3221a14b-0488-4f39-9873-a5fe4b3cdffa` 返回 `COMPLETED / NONE`；串口确认开始时两轴在校准中位、供电正常，未见未到位或保护错误，结束后显式禁用为 `motion_disabled / DISABLED`。已请求用户只观察下一次真实转动的方向、幅度、回正和异常；**尚无这一候选的现场观察结果**，其它四模板未试，自动动作关闭。下一条精确操作：用户确认在旁后，重新启用并仅执行一次 `NOD_SMALL`，同步捕获最终命令结果和用户实体观察，随后禁用；若两者一致通过，再逐一重设旧下行模板并按 runbook 验证。另查 LTR553 的 `ESP_ERR_INVALID_STATE`，不把自动返场标为完成。

历史失败：`67f8f67` 第二次启用成功，日志自测点头命令 `684cbfe5-b767-4f99-948a-f512e307e00b` 返回 `FAILED / FEEDBACK_FAULT`；COM3 为 `pitch_target_recovery_stalled frame=1 progress=clear`，再次读取差值 17–32 raw、目标位于舵机 EEPROM 设置限位内、电压和温度正常、电流正常、负载偏高、死区不足。相同目标的有界重发未带来至少 2 raw 的进展；已立即禁用。上一轮 `VOICE_STOP` 仅是语音抢占，与本次未到位分别记录。此前仅按模板数值 35° 判断目标位于官方建议的 5°–85° 范围；由于本项目坐标是相对用户校准中位的 45°，该推断不成立，现已撤回。下行未到位的确切机械或电气原因仍未证明；此前停止重复试转并转向零位检查的决定见上方后续结果。

2026-09-28 官方对照及自测：对照 M5Stack StackChan `hal_servo.cpp` 的 SCSCL `WritePos(..., 20, 0)` 与飞特官方 SCS 协议实现，`cc94073` 将原来跨整帧的舵机目标时间改为 20 ms，并由固件每约 100 ms 插值下发固定安全帧。用户纠正前一轮观察：`09db322` 点头实际明显接近 20° 且回正，无异常；但命令 `cef5de6f-0e1c-49b8-b12f-6bf9ec2daf4f` 仍为 `FAILED / FEEDBACK_FAULT`，串口 `pitch_target_far_idle frame=1`。`cc94073` 自测命令 `656803da-3dcb-45b2-8a9e-66fa7df7f365` 同样失败，差值为 17–32 raw，方向为目标未到，舵机已停；目标位于舵机角度设置内，电压正常。`6a49bc7` 自测命令 `41b28384-2b90-4692-a1d1-0626f4782033` 同样失败，差值 17–32 raw，负载偏高，电流、温度、电压正常，死区小于差值。每次故障后均禁用；不能把用户肉眼觉得自然当作设备反馈达标，也不能放宽到会让静止通过的旧容差。

候选 `67f8f67` 在保持 8 raw 容差和停止门控的前提下，对舵机已停、俯仰未到位且负载、电流、温度、电压安全的情况最多重发两次相同目标，每次等 200 ms；无至少 2 raw 的继续进展就失败并断电。ESP-IDF 5.5.5 LAN HTTP Quad 与 protocol 构建通过；LAN 镜像 1,657,216 字节、SHA-256 `8B0D7ADB637AD65AC0F81C75E64BD7006D5EE82C9225993959B6441D7EDC4AFF`，最小分区余量 47%。保留 NVS 的 OTA 任务 `7acc424c-1c6f-435f-b8c6-90207ea33f2e` 已安装；串口与 API 确认启动默认禁用、K151 电源关闭、校准保留、在线、自动动作关闭。首次启用随后遇到 `VOICE_STOP`，设备已自动退回禁用，**没有下发 `NOD_SMALL`**。本轮自测未产生新版动作日志；下一条精确操作是在设备确实音频空闲、现场安全时同步 COM3，仅自测一次 `NOD_SMALL` 并立即禁用，先核对最终结果和恢复分类；日志通过后再请用户判断实物。若仍是未到位且无进展，停止重复软件试转，转向机械负载或中位校准检查。五模板均未通过。最新启动还报告接近和环境光能力为 `no`，`LOOK_USER` 的自动返场场景不能视为可用，需另查 LTR-553 初始化。

2026-09-28 `3426caa` 同步诊断：用户在旁观察的轻点头 `fa2afc8e-2ae8-4a4f-8d71-493c725c12f3` 为 `FAILED / FEEDBACK_FAULT`；COM3 `Servo start pose: yaw=centered pitch=centered`、`stage=pitch_target_far_moving frame=1 progress=clear`。第二帧开始后俯仰已明显运动，但帧截止仍离目标超过 16 raw 且舵机继续运动；用户仍见约 5° 先上再下，无异常。另一次命令 `5ad47d54-8790-440e-8936-cc667bab511e` 在用户未碰头顶时 `STOPPED / TOUCH_STOP`；单个 SI12T 非零读数即停动是合理嫌疑，原因未证实。两次后均禁用。

修复候选 `09db322` 将舵机目标运动时间设为应用帧时长的 75%，保留剩余时间做机械到位和严格反馈检查；顶部触摸须连续两次采样确认，真正触摸仍停动并记录本地分类日志。ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 构建通过；LAN 镜像 1,655,392 字节、SHA-256 `4A982A605AFF7C1B9D7682771CB23FE91635F2EA66A55A504777E20457F2E06E`，最小分区余量 47%。配网、传输栈回归及预算通过；语音栈回归通过，旧 `build-voice-stack-analysis` 报告来自 2026-07-30、缺当前函数，改用 `build-body002-stack-analysis` 的当前函数报告通过。应用 OTA 任务 `e8e54cc3-1b01-4a1a-b7fd-31dd651990d8` 为 `INSTALLED`，串口和新鲜心跳/API 确认默认动作禁用、K151 电源关闭、校准保留、在线、自动动作关闭。下一条精确操作：用户再次确认在旁并留空后同步 COM3，只测一次 `NOD_SMALL`，核对实体幅度/方向/回中、结果码和异常，然后禁用。五模板仍未通过。

2026-09-27 反向诊断版安装：`0a703ca` 用户在旁观察的 `NOD_SMALL` 命令 `b5773752-333b-48c3-8fd5-1df1e9c93887` 为 `FAILED / FEEDBACK_FAULT`；COM3 捕获供电来源 `present`、迟到写 ACK 跳过、`stage=pitch_target_far frame=1`。第一帧逐项到位检查已通过，反向第二帧实际位置距目标超过 16 raw。用户见先上再下约 5°、最终回正、无异响卡顿或持续用力；故障后显式禁用，回正不能证明主动第三帧。新提交 `3426caa` 对任一未到位帧读取寄存器 66，区分仍在动和已停；只记录原始位置相对上帧的 `none/small/clear` 分类以及起始姿态 `centered/offset/far`，不输出原始值。ESP-IDF 5.5.5 LAN HTTP Quad 与 protocol 构建通过；LAN 镜像 1,655,264 字节、SHA-256 `4CEE9C1F2F72A6E6FA24D04B38DBB1F69EC91449178E708B2053264ED459AF9F`，分区余量 47%；语音、传输、配网栈预算通过。保留 NVS 的 OTA 任务 `cd3823b5-e847-472c-ac78-40a14c82fd92` 为 `INSTALLED`；串口和 API 确认启动默认禁用、K151 电源关闭、校准保留、在线、自动动作关闭。下一条精确操作：用户再次确认在旁、头部放正后，同步串口只测一次 `NOD_SMALL`，取得反向第二帧运动分类后再决定是修时间、供电或运动控制。五模板仍未通过。

2026-09-27 机械行程与诊断版安装：用户断电手动轻推，确认头部可轻松转约 20°，降低固定机械限位造成 5° 未到位的可能性。`0a703ca` 镜像经先前大小、SHA-256 和当前设备版本只读预检后，保留 NVS 的应用 OTA 任务 `53fcf254-bc24-4313-aea2-a20edff386bd` 为 `INSTALLED`。串口确认启动 `motion_disabled`、K151 电源关闭、校准保留、Wi-Fi 和 WebSocket 重连；新鲜心跳与 API 确认 `0a703ca` 在线、命令通道可用、动作与自动动作禁用。下一条精确操作：用户再次确认在旁、头部放正且周围留空后，同步 COM3 采集，仅显式启用并执行一次 `NOD_SMALL`，观察实际幅度、主动回中和异常；随后禁用并结合 `pitch_target_near_idle` / `pitch_moving_read` / `pitch_target_near_moving_timeout` 或成功结果判定下一步。五模板尚未通过。

2026-09-27 放大轨迹首次实体复测：用户重新确认在机器人旁并可观察，`24c62a7` 显式启用后命令 `9ef90e4a-c594-4851-a4ab-f9861cddfba7` 返回 `FAILED / FEEDBACK_FAULT`。同步 COM3 捕获 `Servo power source: state=present`、两路迟到写 ACK 被跳过、`Servo motion unavailable: stage=pitch_target_near frame=0`。第一帧目标寄存器读回正确，实际俯仰位置与目标相差 9–16 raw，严格反馈正确拒绝完成；第二帧和回中帧未执行。用户仍观察约 5° 转动，没有异常。已立即显式禁用，API 为 `motion_disabled / DISABLED`，自动动作关闭。用户建议目标 70°；在确认未到位原因前不扩大目标，先请用户断电后轻试机械行程，并核查舵机运动状态和有界完成时序。五模板仍未通过。

2026-09-27 诊断候选 `0a703ca` 构建记录（随后已安装，见上）：在保留固定轨迹和 8 raw 到位容差的前提下，任一帧仅当实际位置接近目标且舵机寄存器 66 表示仍在运动时，最多追加 250 ms 可中断的反馈轮询；舵机已停、寄存器读失败、目标不匹配或超时仍立即失败并断扭矩。失败日志区分 `pitch_target_near_idle`、`pitch_moving_read` 和 `pitch_target_near_moving_timeout`，不输出原始位置。ESP-IDF 5.5.5 LAN HTTP Quad 与 protocol 构建通过；LAN 镜像 1,654,672 字节，SHA-256 `9E37F08670E86CCAF406BBA98FDD6C7915AEB7E421EEDA692F77783038A707BD`，最小应用分区余量 47%；生成配置和语音、传输、配网栈预算通过。安装前的 OTA 只读预检确认 `24c62a7` 在线、动作禁用、目标制品匹配。该诊断逻辑只处理“在标称时长边界尚未走完”的可能性，不证明实体动作已通过。

此前阻塞交接已由用户确认自由机械行程并完成诊断版 OTA 解除；当前剩余项是新版实体动作验收。

2026-09-27 放大轨迹候选发布：`24c62a7` LAN HTTP Quad 1,654,224 字节，SHA-256 `EEDE17DA9B0A127060C97BCFE20E76142C7D2A8C34DCE28421E7774B38CC68D6`，protocol 同步构建通过，语音/传输/配网栈预算通过。保留 NVS 的应用 OTA 任务 `3d99e8a2-5163-4d6e-8894-6db0d9835d8d` 为 `INSTALLED`；串口确认启动默认动作禁用、K151 断电、校准保留、Wi-Fi/WebSocket 连接，心跳与 API 确认在线、命令通道可用、自动动作关闭。用户上次实体观察距今数小时，已请求重新确认在机器人旁；收到确认后下一条精确操作是同步串口采集、显式启用反馈并仅执行一次 `NOD_SMALL`，核对幅度、反向到位、回中和异常。未通过前不试其它模板。

2026-09-27 第二帧运动诊断：`b5c46d7` LAN HTTP Quad（1,654,192 字节，SHA-256 `D33A6813B6B4BD425F56CA2644B080349F522C3FD69DED1BDA957B7900A19CB1`）和 protocol 构建、栈预算通过；应用 OTA 任务 `4c8957e4-dade-4dac-bc0b-beaf85af8e1e` 为 `INSTALLED`，重启仍默认禁用、校准保留、设备在线。用户在旁观察的 `NOD_SMALL` 命令 `0499b8fc-aed7-4ddf-a9ff-c6d9f15a2c32` 和重复命令 `0b23cd43-0e79-4679-9d2a-40b405f964fb` 均 `FAILED / FEEDBACK_FAULT`，串口两次一致为 `Servo power source: state=present`、扭矩 ACK 处理成功、`stage=pitch_target frame=1`。这说明目标寄存器读回成功、首帧位置在 8 raw 容差内、反向第二帧未到位。用户第二次看见约 5° 上下转动，确认无异响、卡顿、持续用力或发热且头部已回正；两次已立即禁用，自动动作仍关闭。用户希望幅度更明显。下一候选把 `NOD_SMALL` 固定俯仰轨迹设为 55°→35°→45°，分别 650/1000/700 ms，并把该模板看门狗延长到 3000 ms 加既有上电等待和余量；反馈要求不放宽。下一条精确操作：完成候选构建及发布预检，保留 NVS OTA 安装后请用户观察一次更明显、较慢的轻点头；未通过前不试其它四模板。

2026-09-27 最新实体测试：用户重新确认在机器人旁，`6b7d836` 显式启用及反馈探测成功；`NOD_SMALL` 命令 `d6609265-4a51-41ca-aadd-c2498dfe93aa` 返回 `COMPLETED / NONE`，但用户确认头部**完全没动**。COM3 只捕获到两路有效的迟到写 ACK 被跳过，无其它错误；动作随即被显式禁用，自动动作继续关闭。代码复核证实旧位置容差为 48 raw，而轻点头第一帧仅移动 19 raw，即完全静止也可通过全部逐帧反馈，构成错误完成报告。源码候选把容差改为 8 raw，逐帧读回目标寄存器与实际位置，并要求动作全程观测到相对起点的真实位移；失败必须回报 `FAILED / FEEDBACK_FAULT`。同时仅记录供电来源分类与故障阶段，不记录原始位置。下一条精确操作：构建与验证该候选，保留 NVS 安装，观察一次轻点头的实体位移和目标寄存器结果；若仍不动，按 `goal_mismatch` 或 `pitch_target` 阶段继续定位供电/舵机，而不声称动作完成。

2026-09-27 固件修复发布：`6b7d836` LAN HTTP Quad（1,653,488 字节，SHA-256 `D700D8DE3D1851F52F1707B8C17E954EF9F0C0E7954580D2098B99433B945570`）和 protocol 均构建通过；语音、传输、配网栈预算通过。应用 OTA 任务 `8a64223c-f82c-4405-bec3-cf19721628ac` 为 `INSTALLED`。串口确认重启默认 `motion_disabled`、K151 断电、校准保留、Wi-Fi 与 WebSocket 重连；新鲜心跳及 API 确认 `6b7d836` 在线、动作禁用、自动动作关闭。修复逻辑只有在同 ID、零错误和校验和有效时跳过迟到的零参数写 ACK；其余错误继续拒绝并断电。实体结果见上方最新测试。

2026-09-27 最新实机诊断：用户在设备旁确认头部留空后，`32d4553` 显式启用成功，`NOD_SMALL` 命令 `194fec0e-a598-4afc-b46c-16dd4c673acb` 为 `FAILED / FEEDBACK_FAULT`；用户确认头部完全没动、没有异常，随即禁用，心跳为 `motion_disabled / DISABLED`。同步 COM3 白名单日志捕获 `Servo response rejected: stage=length id=1` 和 `Servo motion unavailable: stage=yaw_read`。当前证据证明扭矩读回阶段收到长度不符的包，但尚不证明实际长度或来源。源码候选新增实际/预期长度分类记录，并只在校验通过时跳过可能迟到的零参数写 ACK，继续等待读反馈；仍保持默认禁用、固定模板与反馈失败断电。下一条精确操作：完成构建与静态验证，保留 NVS OTA 安装候选，在用户旁观时先复测 `NOD_SMALL`，未通过前不试其余模板。

2026-09-27 联网恢复：COM3 捕获 Wi-Fi 已连但 WebSocket 持续失败；电脑旧 DHCP 地址 `.4` 被拒绝并改为 `.6`，`.4` 的 ARP 与 ESP32-S3 实机 MAC 一致，原服务器地址已指向机器人本身。一次性固件 `12ab254` 经 COM3 只写 factory 应用和 OTA 选择分区，把加密 NVS 的服务器 origin 改到当前开发电脑 `.6:8080`，保留身份令牌、Wi-Fi 和校准；设备重新上报在线。随后移除迁移钩子，正常固件 `32d4553` 经应用 OTA 任务 `6fc6943c-c0c4-4d68-a138-942d1e7d2640` 安装。新鲜心跳/API 确认在线、动作禁用、自动动作关闭、校准保留。下一条精确操作：用户重新在设备旁后，在串口白名单监听下受控复现一次 `NOD_SMALL`，获取具体舵机反馈阶段；未取得正常完成前不测其他模板。

2026-09-27 早前复核：`03b734e` 安装后，用户在场观察的第二次 `NOD_SMALL` 命令 `484ee9fc-4616-4056-94a1-5ff3bb7bc189` 仍为 `FAILED / FEEDBACK_FAULT`，用户再次确认完全没动、无异常；已立即禁用。之后开启 COM3 只读诊断，曾再次显式启用反馈探测，但未发送第三次动作；串口会话已终止，未取得扭矩失败阶段。当时数据库最后心跳为 2026-09-24 14:29:43 UTC，最后上报状态 `motion_disabled / DISABLED`、自动动作关闭；限时只读监听未捕获日志。单字节寄存器读取不足以解决故障，不能标记为完成。后续联网恢复见上方最新记录。

2026-09-24 实机记录：用户在场摆正头部后，无目标位置的校准与首次反馈启用均未见主动转动或异常；重启后心跳为 `b22cf94 / motion_disabled / DISABLED`，校准保留。旧版首次 `NOD_SMALL` 命令 `cded9c7e-acbb-46b8-a286-c659b2d8d860` 于入队前因 `AUDIO_BUSY` 被拒绝；用户确认头部完全没动。WakeNet 暂停握手修复已通过应用 OTA 安装为 `936967f`。用户再次在旁时显式启用和无动作反馈探测通过；新版唯一一次 `NOD_SMALL` 命令 `36df295e-b5aa-4d7e-a3be-66597cd76056` 入队后以 `FAILED / FEEDBACK_FAULT` 结束，用户确认头部完全没动、无异响或持续用力。已立即禁用，自动动作保持关闭。校准和探测能读取位置，但动作路径把单字节扭矩寄存器请求为两字节；修复候选 `03b734e` 改为单字节并记录失败阶段，待安装和实体复测。五模板及抢占测试未通过。

五个固定模板保留。动作执行现在用与反馈探测一致的可中断 1200 ms VM 上电等待，并把它计入看门狗；停止代数防止入队后已停止的动作再开始。两路扭矩、逐帧反馈、软限位及断电路径维持固件最终裁决；断电失败不得回报完成。每条受理动作经有界队列产生不含原始位置的最终结果事件，ACK 仍只表示入队。重启仍默认 `motion_disabled`，语音开始仍停动并禁用。

ESP-IDF 5.5.5 的 protocol 与 LAN HTTP Quad 从干净候选提交 `233ee31` 重建通过；三组任务栈预算静态门槛通过，固件 Unity 协议用例仅编入 protocol 镜像，未在设备上运行。前两次 OTA 任务 `9f3a3d0d-e82c-4d2c-ab15-ccbd280a3198`、`f53145e2-97bb-4e6b-8905-dc200abc7fc1` 自动回退；第二次串口定位创建 USB 配网任务返回 `ESP_ERR_NO_MEM`。第三次 `e14fb36` 镜像先预留传输和配网栈，任务 `9826a8d3-22e6-4851-a5ff-a4073306ea53` 仍回退；这次预留已成功，Wi-Fi 初始化返回 `ESP_ERR_NO_MEM`。详见[发布记录](../../runbooks/body-motion-20260924-release.md)。

后续从干净提交 `b22cf94` 构建候选，首次投递因旧版下载阶段 Wi-Fi PHY 内存错误而在 ACK 前重启；悬挂任务在旧版在线、动作禁用且未受理的条件下记为失败。同镜像第二次投递任务 `604ef042-381e-4c84-b597-017f53eb8a7e` 已 `INSTALLED`，设备心跳 `b22cf94 / motion_disabled / DISABLED`，传输、配网与 Wi-Fi 初始化日志正常。首次 `NOD_SMALL` 已因 `AUDIO_BUSY` 在入队前拒绝，没有实体转动。新 `936967f` 镜像安装后解决了该拒绝，但动作入队后 `FEEDBACK_FAULT`，用户仍未见转动。`03b734e` 安装后的首个动作同样反馈故障。下一条精确操作：恢复设备连接，在用户旁观下对白名单串口日志和一次受控动作进行同步诊断；其间保持动作与自动动作关闭。

## 历史交接（文档阶段）

用户要求后续 Agent 完善 `WAKE`、`LOOK_USER`、`NOD_SMALL`、`THINK`、`DROWSY` 全部五个动作，本轮仅产出[可执行交接计划](../body-motion-completion-plan.md)，不参与代码开发。计划已列出每项的软件、实体与体验完成层级、冷启动与语音门控风险、实施顺序和逐项验收。设备仍为 `motion_disabled`；没有任何实体动作被本轮执行。

## 最近发布核对（历史）

2026-09-15 服务端与控制台已发布 V51；未改动或刷写固件。一台设备在发布后重新上报心跳，身体状态仍为 DISABLED。用户实际唤醒、对话、听感及动作验收未执行；以下为历史固件记录，不表示本轮新刷写。

## 本轮功能评审交接（历史）

2026-09-13 已完成[桌面机器人功能评审报告](../feature-review-2026-09-13.md)，覆盖 37 项能力、12 组相似功能合并边界、低价值风险与九项深化建议。用户确认核心为自然聊天、准确记忆、多个独立伙伴和轻量主动开场；尚未正式使用，开发测试不作为需求或留存证明。

本轮先标记并恢复旧状态事实：最新主线 `54e41f9` 已合入 PR #40，原“等待推送/合并”交接已结束；当前仅新增报告与文档事实修正，不修改业务实现或运行状态。`4444860` 存在但不是当前分支祖先；其 `firmware/` 与本轮主线无差异，继续仅作为历史实机制品来源。以下旧任务过程和验证均按历史阅读，不能视为本轮新验收。

完成：代码/设计/路由交叉审查、产品定位追问、报告与文档索引；`git diff --check`、`pnpm docs:check`、`pnpm docs:check:test` 通过。使用开发环境约定的临时 pnpm 引导，无依赖或 Node engine 改动。

未完成：真实日常使用观察、报告建议对应的实现、实体对话/动作验证和全栈迁移演练；本轮未读取运行库最终观察结果。无报告阻塞，产品价值证据仍缺少真实使用。下一条精确操作：用户阅读报告第 7/8 节，选定下一轮改造或真实使用观察任务；Agent 不自动开始实现、推送、部署或固件操作。

## 历史目标

本轮只交付功能评审及事实文档；后续实现等待用户选定任务。下述旧目标保留为历史背景。

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

- BODY-002 固件 `d18b3cd` 在线、动作禁用；服务端在场变化、五项主要自动触发与断线本地停动已完成现场验收。真正本地语音回合停动按用户本轮要求暂不继续测试；日常陪伴价值仍待实际使用反馈。

### 以下为 BODY-001 等历史进行记录，不代表当前待办

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

保持 `motion_disabled / DISABLED` 和自动开关关闭；下一次真实工作会话发生本地语音回合或自动 `NOD_SMALL`、`THINK`、`DROWSY` 时，读取固定结果码并请用户描述实际观感。无需重复已通过的五模板、返场和断线测试。

旧语音任务的两轮独立唤醒与串口段间监听属于历史上下文，不作为 BODY-002 开始条件。

## 阻塞项

- 无固件构建或部署阻塞；剩余语音与自然陪伴场景依赖真实使用窗口，不能用固定动作日志代替体验结论。

### 以下为旧工作流阻塞记录，不代表 BODY-002 当前状态

- MEDIA-004 V2 实体激活因用户暂无 EAF 素材延期，不能视为失败或通过。
- BODY-001 无动作中位校准已连续两次实机通过；真实运动方向、模板限位和动作停止尚未实机验证，继续受实体动作独立授权边界约束。
- WORK-001 顶部长按启动/停止已实机通过；当前设备未上报接近、环境光和舵机反馈能力，相关能力缺失降级已生效，但环境光实体档位仍不能写成实机通过。
- 摄像头、NFC、红外、舞蹈、连续旋转、任意角度协议、模型运动权限和未校准舵机保持冻结。

## 关键文件

- [五动作交接计划](../body-motion-completion-plan.md)
- `firmware/main/body_hardware.cpp`
- `firmware/main/safety_state.c`
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

- 2026-09-29 BODY-002：`d18b3cd` ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 构建通过，传输栈回归/预算通过；保留 NVS OTA `INSTALLED`，新鲜心跳在线、校准保留、动作禁用。服务端 22 项定向测试通过；`pnpm docs:check`、`pnpm docs:check:test` 与 `git diff --check` 通过。实体 true→false 未在本版用户旁观时取得，不标通过。

- 以下条目均为历史软件/实机证据。

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
