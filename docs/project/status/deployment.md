# 部署工作流

- 状态：BLOCKED
- 最后更新：2026-09-29
- 当前分支：`codex/body-motion-five-actions`
- 基准提交：`2f75da3`
- 最后验证提交：`2f75da3`
- 固件提交：`d18b3cd`
- 最后验证范围：BODY-002 服务端镜像、停写备份和隔离 V53 恢复、现有 8080 发布；`8a81b07` 五模板日志与现场观察，`c018d25` 点头回归、远程和头顶触摸停动及自动 `WAKE` 端到端结果；`ffd0217` 靠近/离开成对串口事件通过；版本标识 `d18b3cd` 双构建、保留 NVS OTA、在线且动作禁用通过，服务端状态变化与自动返场待验。状态字段以主线基准提交作可解析锚点；最终单提交自身的哈希无法写入同一提交

## BODY-002 当前部署

软件与保留 NVS OTA 已部署；剩余现场验证等待用户回到设备旁。当前不执行更多发布或实体动作，恢复时先确认 `d18b3cd` 在线和默认禁用，再按[全局总览](overview.md)的在场与自动返场步骤逐项验收。

最新固件 `d18b3cd` LAN HTTP Quad 镜像 1,659,952 字节，SHA-256 `6C6E6002841742CAB467EDFCDD81DEF9DABB68F51FDF9072653908E9EDE13983`；保留 NVS OTA `78b3d0b6-6656-49b8-a74c-5866f61459ad` 为 `INSTALLED`。新鲜心跳确认在线、校准保留、动作 `motion_disabled / DISABLED`、自动开关关闭。上一版 `ffd0217` 在用户约 5 秒近测时，串口先后捕获 `present=yes` 和 `present=no`，但旧 25 秒心跳可漏掉两次短暂事件；本版改为在场变化时提前心跳，待数据库和工作日链路实测。以下 `c018d25` 记录为舵机验收历史。

最新验收补充：用户在点头运行中轻触头顶，命令 `83edccbd-2c05-4bb4-9620-d0c414c7dfa3` 返回 `STOPPED / TOUCH_STOP`，设备随后为 `motion_disabled / DISABLED`；临时测试账号数为零。头顶触摸急停已实机通过，语音和断线停动仍待验。

当前 `c018d25` LAN HTTP Quad 镜像 1,659,344 字节，SHA-256 `10624AB541F189023AC2B6ECF67DFE1F57CFEFF224283A72A1DD2504CB860A2E`；保留 NVS 应用 OTA `77e1bae4-a1bd-4e0b-802d-cd6bc946186f` 为 `INSTALLED`。串口确认启动默认禁用、校准保留、Wi-Fi/WebSocket 重连；API 与数据库确认在线、命令可用、自动开关关闭。`c018d25` 点头回归 `COMPLETED / NONE`，远程中断 `STOPPED / NONE`，显式工作开始的自动 `WAKE` 为 `COMPLETED / NONE`；测试后工作状态 `OFF`、动作 `motion_disabled / DISABLED`、自动开关关闭，临时测试账号数 0。五模板的实物转动由用户在先前 `8a81b07` 统一观察并确认没有问题；两版本舵机轨迹相同。接近感应虽然能初始化，在正确窗口近测时分档未变化，自动 `LOOK_USER` 未验收。语音/断线停止与其余自动场景没有本版实体证据，不宣称全部通过。

`8a81b07` LAN HTTP Quad 镜像 1,658,960 字节，SHA-256 `1ADF09E193315A351CAF310218F7571BD526FAE6A8E9461B371402DF56991FB0`；保留 NVS 的应用 OTA `dde07742-d37e-45de-9155-5338c6a0fb50` 为 `INSTALLED`。串口确认启动默认禁用、校准保留、接近/环境光能力恢复及 Wi-Fi/WebSocket 重连；数据库和 API 确认在线、动作 `motion_disabled / DISABLED`、自动开关关闭。五模板各一次设备结果 `COMPLETED / NONE`，每轮后显式禁用；实体观感和安全停止仍待用户现场验收。2026-09-28 的临时测试管理员均由脚本清理，不保留凭据。以下旧镜像与旧传感器故障是历史记录，不代表当前部署。

用户断电确认俯仰向下自由行程约 90°后，`2abfbbd` 只读诊断经保留 NVS OTA `b019a0f2-2824-4cd4-b45a-095de027e80a` 安装，启动日志将已保存俯仰中位归类为官方默认零位以下，并定位 LTR553 身份读取 `ESP_ERR_INVALID_STATE`。后续 `2bc6bfb` 将点头调整为相对校准中位上行 20°、再回中；LAN HTTP Quad 镜像 1,657,872 字节，SHA-256 `CF113B35554B63DF25D944FBA9A064910187F1217BCD65355B87F7501449DDFF`，应用 OTA `3343c39a-e099-465b-9828-c156a6a2203b` 已安装，NVS 保留，Wi-Fi/WebSocket 在线。自动动作关闭。第一次独立日志自测 `3221a14b-0488-4f39-9873-a5fe4b3cdffa` 和第二次用户在旁时命令 `7ee7889b-7631-478f-bd0c-1c7414aeec39` 均为 `COMPLETED / NONE`，每次都显式禁用至 `motion_disabled / DISABLED`；第二次实体反馈仍待用户回答。其余四模板与自动返场尚未验收。

`67f8f67` 在语音空闲后已执行一次点头日志自测，命令 `684cbfe5-b767-4f99-948a-f512e307e00b` 为 `FAILED / FEEDBACK_FAULT`，COM3 为 `pitch_target_recovery_stalled frame=1`；有界复核没有位置进展，随后显式禁用。固件保持在线、自动动作关闭。下行机械负载或校准中位尚待确认；不继续反复试转，其他四模板未试。

2026-09-28 当前镜像 `67f8f67` LAN HTTP Quad 1,657,216 字节，SHA-256 `8B0D7ADB637AD65AC0F81C75E64BD7006D5EE82C9225993959B6441D7EDC4AFF`；保留 NVS 的 OTA 任务 `7acc424c-1c6f-435f-b8c6-90207ea33f2e` 已安装。此前 `cc94073` 与 `6a49bc7` 镜像均经保留 NVS OTA 安装并在线，分别做一次日志自测，第二帧仍 `FEEDBACK_FAULT`，差值 17–32 raw；`6a49bc7` 显示负载偏高但电流、电压、温度正常。当前串口和 API 确认 `67f8f67` 默认禁用、校准保留、在线、自动动作关闭；首次启用遭遇 `VOICE_STOP`，未发新版点头。下一条精确操作：音频空闲且现场安全时仅做一次同步串口点头自测，日志达标后才请用户观察实体；其余四模板未试。

2026-09-28 `3426caa` 同步轻点头返回 `FAILED / FEEDBACK_FAULT`，COM3 为 `pitch_target_far_moving frame=1 progress=clear`；用户仍见约 5°、无异常，已禁用。`09db322` LAN HTTP Quad 镜像 1,655,392 字节，SHA-256 `4A982A605AFF7C1B9D7682771CB23FE91635F2EA66A55A504777E20457F2E06E`，保留 NVS 的 OTA 任务 `e8e54cc3-1b01-4a1a-b7fd-31dd651990d8` 为 `INSTALLED`。串口及新鲜 API 确认启动默认禁用、K151 断电、校准保留、在线、自动动作关闭。下一条精确操作：用户确认实时在旁后只执行一次新版 `NOD_SMALL` 并同步 COM3/实体观察；未通过前不试其它模板。

2026-09-27 `0a703ca` 用户旁观轻点头 `b5773752-333b-48c3-8fd5-1df1e9c93887` 为 `FAILED / FEEDBACK_FAULT`，COM3 定位 `pitch_target_far frame=1`；用户见约 5° 先上再下、无异常，已禁用。新诊断版 `3426caa` LAN HTTP Quad 1,655,264 字节，SHA-256 `4CEE9C1F2F72A6E6FA24D04B38DBB1F69EC91449178E708B2053264ED459AF9F`，保留 NVS OTA 任务 `cd3823b5-e847-472c-ac78-40a14c82fd92` 为 `INSTALLED`。串口及新鲜 API 确认默认禁用、K151 电源关闭、校准保留、在线、自动动作关闭。下一条精确操作：用户再次确认在旁后，同步串口只测一次轻点头并读取反向失败分类；其余四模板尚未试。

2026-09-27 用户确认头部在断电时可轻松转约 20°。`0a703ca` 保留 NVS 的应用 OTA 任务 `53fcf254-bc24-4313-aea2-a20edff386bd` 已 `INSTALLED`；串口和 API 确认新固件默认禁用、K151 电源关闭、校准保留、在线、命令通道可用、自动动作关闭。下一条精确操作：用户确认仍在设备旁且头部已放正后，只执行一次 `NOD_SMALL` 并同步观察和串口诊断；未通过前不试其它模板。

2026-09-27 `0a703ca` 安装前记录：LAN HTTP Quad 镜像 1,654,672 字节，SHA-256 `9E37F08670E86CCAF406BBA98FDD6C7915AEB7E421EEDA692F77783038A707BD`；当时设备运行 `24c62a7`、在线且动作禁用，OTA 只读预检通过。用户随后回复断电手动检查结果，安装状态见上方最新记录。

此前机械行程阻塞已解除，诊断固件已安装；五模板完整验收仍未执行。

2026-09-27 `24c62a7` 首次放大轨迹实测：用户在旁观察，`NOD_SMALL` 命令 `9ef90e4a-c594-4851-a4ab-f9861cddfba7` 为 `FAILED / FEEDBACK_FAULT`；串口为 `pitch_target_near frame=0`，用户仍见约 5° 转动、无异常。已显式禁用，自动动作仍关闭。下一条精确操作：确认断电后的机械行程；在排除机械受限前不把固定目标直接加至 70°。

2026-09-27 `24c62a7` 已安装：LAN HTTP Quad 1,654,224 字节，SHA-256 `EEDE17DA9B0A127060C97BCFE20E76142C7D2A8C34DCE28421E7774B38CC68D6`；OTA 任务 `3d99e8a2-5163-4d6e-8894-6db0d9835d8d` 为 `INSTALLED`，NVS 保留。串口、心跳和受保护 API 确认重启默认禁用、K151 电源关闭、校准保留、在线、自动动作关闭。由于用户上次在场观察距今数小时，已请求再次确认；收到后只做一次放大轨迹的轻点头实测。

2026-09-27 `b5c46d7` 已安装：LAN HTTP Quad 1,654,192 字节，SHA-256 `D33A6813B6B4BD425F56CA2644B080349F522C3FD69DED1BDA957B7900A19CB1`；OTA 任务 `4c8957e4-dade-4dac-bc0b-beaf85af8e1e` 为 `INSTALLED`，NVS 保留。串口和心跳确认启动默认禁用、校准保留、在线、自动动作关闭。用户在旁观察的两次 `NOD_SMALL` 都 `FAILED / FEEDBACK_FAULT`，串口同为 `pitch_target frame=1`，供电来源 `present`；第二次用户看见约 5° 上下转动，无异常且头部回正。每次均已禁用。新轨迹候选待构建和安装，目标是更明显且给反向更长时间；未通过前不试其余模板。

2026-09-27 实体结果推翻软件完成：`6b7d836` 的 `NOD_SMALL` 命令 `d6609265-4a51-41ca-aadd-c2498dfe93aa` 返回 `COMPLETED / NONE`，用户确认头部完全未动。串口捕获两路迟到写 ACK 均被跳过；旧版反馈容差 48 raw 使 19 raw 的首帧位移在静止时也能通过。已显式禁用，自动动作仍关闭。新候选将要求目标寄存器读回、收紧反馈容差和观测真实位移；下一条精确操作是构建、校验和保留 NVS 安装该候选，再在用户旁观时做一次诊断动作。

2026-09-27 `6b7d836` 已安装：LAN HTTP Quad 1,653,488 字节，SHA-256 `D700D8DE3D1851F52F1707B8C17E954EF9F0C0E7954580D2098B99433B945570`；OTA 任务 `8a64223c-f82c-4405-bec3-cf19721628ac` 为 `INSTALLED`，NVS 保留。串口、心跳和受保护 API 确认重启后动作默认禁用、校准保留、在线、自动动作关闭。下一条精确操作：用户确认在设备旁后进行一次同步串口的 `NOD_SMALL` 实体复测；其余模板待该项通过后再测。

2026-09-27 实机诊断：`32d4553` 在用户旁观时再次显式启用并执行 `NOD_SMALL`，命令 `194fec0e-a598-4afc-b46c-16dd4c673acb` 返回 `FAILED / FEEDBACK_FAULT`，头部没有转动或异常。COM3 同步捕获舵机 1 读回长度拒绝和 `yaw_read` 失败阶段；已立即禁用，自动动作继续关闭。修复候选尚未安装。下一条精确操作：构建并校验只跳过有效零参数写 ACK 的固件候选，NVS 保留应用 OTA 安装，在用户旁观时复测首个模板。

2026-09-27 局域网恢复：开发电脑原 `.4` DHCP 租约被拒，当前 `.6`；`.4` 的 ARP 与机器人 ESP32-S3 的 COM3 芯片地址一致。设备当时 Wi-Fi 已连接，但 WebSocket 持续退避失败。一次性迁移固件 `12ab254` 仅将加密 NVS 服务器 origin 指向当前开发电脑，COM3 只写 factory 应用与 OTA 选择分区，未触及 NVS、Wi-Fi、凭据和校准；设备重新上线。移除迁移钩子后正常固件 `32d4553`（1,652,880 字节，SHA-256 `AD71BFCC2C1DD57EC45AE6C6680D419CA1BEFB0F34340DCC3E9854393A7B802B`）经应用 OTA 任务 `6fc6943c-c0c4-4d68-a138-942d1e7d2640` 安装。新鲜心跳/API 确认在线、命令通道可用、动作禁用、自动动作关闭、校准保留。下一条精确操作：用户在设备旁时同步采集首个动作的舵机失败阶段；不启用自动动作。

2026-09-27 早前复核：`03b734e` LAN HTTP Quad（1,652,880 字节，SHA-256 `EA386F57EA2EE874D14ECA39C344B9AEEE6AFD850515B34061A5D8FCF363E3F3`）应用 OTA 任务 `6547c15e-1208-4de1-acd6-37f0f2e1aa3f` 为 `INSTALLED`，安装后心跳和 API 曾确认在线、校准保留、动作禁用及自动动作关闭。复测 `NOD_SMALL` 仍 `FAILED / FEEDBACK_FAULT`，用户确认头部未动；随后已禁用。当时心跳自 2026-09-24 14:29:43 UTC 起未更新，自动动作开关仍关闭；后续恢复见上方最新记录。

2026-09-24 最新固件发布：`936967f` LAN HTTP Quad 镜像（SHA-256 `AF63F6B36008540CE1B52E296BE63D0F73BF4098C69E20CCA7E6EA4033984734`）通过应用 OTA 任务 `b79d4818-45b4-402d-a99f-bfcae5653598` 安装，NVS 保留。只读串口与设备心跳确认正常启动、联网、动作禁用、自动动作关闭。它解决旧版第一次 `NOD_SMALL` 被 `AUDIO_BUSY` 拒绝的唤醒监听时序；新固件第一次入队动作以 `FEEDBACK_FAULT` 失败，用户确认头部未动，已禁用。下一条精确操作：构建并安装单字节扭矩反馈候选 `03b734e`，然后在用户旁观时复测首个模板。

用户授权持续部署及测试后，已把 `stackchan-foundation-server:body002-think-7d8c55a` 发布到现有 LAN HTTP 开发环境。只重建 server；原配置、数据卷和其余容器保持。新停写快照 `stackchan-release-20260924211747` 的隔离恢复通过，运行库到 V53，8080 健康和页面通过。三次固件 OTA 回退后，通过串口定位并修复任务预留与 Wi-Fi 常驻缓冲的启动内存冲突；`b22cf94` 镜像最终任务 `604ef042-381e-4c84-b597-017f53eb8a7e` 为 `INSTALLED`，设备心跳已确认新版、在线且 `motion_disabled / DISABLED`。完整证据、限制和恢复边界见[本次发布](../../runbooks/body-motion-20260924-release.md)。五动作及语音尚待新固件实机观察；下一条精确操作以上方 `936967f` 最新发布记录为准。

以下为 2026-09-21 控制台发布的历史记录。

## 当前目标

维持已发布的 BODY-002 服务端、管理端与 `d18b3cd` 固件，完成剩余自动动作和安全停止的实机验收。

## 已完成

BODY-002 服务端与管理端已发布到既有 LAN 8080，停写备份及 V53 隔离恢复通过。`d18b3cd` 固件经保留 NVS 应用 OTA 安装；OTA 任务 `78b3d0b6-6656-49b8-a74c-5866f61459ad` 为 `INSTALLED`，设备在线、校准保留、动作禁用。旧控制台发布详情见[发布手册](../../runbooks/console-ux-20260921-release.md)。

## 正在进行

等待用户今晚回家旁观时完成新版在场状态 true→false 的服务端验收，再测自动 `LOOK_USER` 及其余未验场景。监听进程已停止，不留后台测试。

## 下一步操作

先运行 `pwsh -NoProfile -File .\scripts\observe-body-presence.ps1 -Seconds 600`，用户靠近屏幕下方传感器 1–2 cm 约 5 秒并移开，核验数据库 true→false；之后再按[实机流程](../../runbooks/k151-body-motion-smoke-test.md)及[五动作计划](../body-motion-completion-plan.md)验自动与停动。工作日 `OFF`、自动开关和动作禁用是当前安全基线。

## 阻塞项

无构建或部署阻塞。剩余实机验收等待用户回到设备旁；本版数据库在场变化及自动 `LOOK_USER` 暂无现场结果。

## 关键文件

`firmware/main/device_transport.c`、`scripts/observe-body-presence.ps1`、`scripts/install-body-motion-ota.ps1`、[固件状态](firmware.md)、[BODY-002 发布](../../runbooks/body-motion-20260924-release.md)。

## 验证命令与最近结果

ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 构建通过；LAN 应用 1,659,952 字节、SHA-256 `6C6E6002841742CAB467EDFCDD81DEF9DABB68F51FDF9072653908E9EDE13983`。保留 NVS OTA 安装且新鲜上报 `d18b3cd / motion_disabled / DISABLED`。服务端 22 项定向回归通过；`pnpm docs:check`、`pnpm docs:check:test`、`git diff --check` 通过。控制台 142 项回归、停写备份与 V53 隔离恢复的历史证据见上方发布段。

## 相关设计、计划和决策

[五动作计划](../body-motion-completion-plan.md)、[本次发布](../../runbooks/body-motion-20260924-release.md)、[实机流程](../../runbooks/k151-body-motion-smoke-test.md)、[备份手册](../../runbooks/personal-data-backup.md)。

## 安全与兼容性约束

保留现有 LAN HTTP 开发模式，生产 HTTPS 边界不变。秘密只在进程内复用，不打印或落盘；临时管理员计数为零。当前自动动作关闭、工作日 `OFF`、舵机禁用。用户授权本任务分支提交推送；不创建 PR、不合并、不直接推送 `master`。
