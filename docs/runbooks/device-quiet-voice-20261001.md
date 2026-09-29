# 今日安静的语音与页面范围统一

- 文档状态：ACTIVE
- 日期：2026-10-01
- 任务分支：`codex/body-motion-acceptance-20260929`，主线基准 `0914e43`
- 关联：[BODY-002 交接](../project/body-motion-completion-plan.md)、[固件语音许可修复](body-motion-audio-permit-20261001.md)

## 问题与实现

完成性复核发现“今天安静点”在页面作用于整台设备，语音路由却只暂停当前角色的主动聊天。按 BODY-002 已选定范围，语音现在调用既有 `DeviceQuietTodayService`：本设备所有伙伴的主动聊天、随机无声表情和自动身体动作暂停到设备当地次日零点，普通提醒继续。“今天别主动聊”、按分钟暂停及“恢复主动聊天”继续只影响当前伙伴。

新增严格整句“恢复设备陪伴／提前恢复设备陪伴”，只解除设备今日安静；角色暂停、原有开关、时段、额度和舵机许可继续生效，不启用舵机。两种设备控制先取消当前会话旧待确认操作。引用、否定、提问、附加话题不能触发；设备级服务缺失时明确未改设置，不退回角色暂停冒充成功。

业务修复仅修改 `VoiceActionCoordinator` 的路由、注入及回复，新增四组单元回归和一项真实 Spring/PostgreSQL 集成验证。不修改 ASR/TTS 服务、模型、计费、配置或固件，不新增实体转动测试。

## 软件验证

修复前，隔离 Linux 完整 `mvn test` 共 144 个 Surefire 报告、606 项，零失败、零错误、零跳过，退出码 0。此前受 Windows 连接异常影响的 `ICloudCalDavClientTest`（4）、`DeviceEventServiceTest`（4）、`ConversationServiceTest`（11）、`PairingServiceTest`（7）、`NotificationMcpTransportTest`（1）均实际运行到断言并通过。旧版 594 项是历史范围，不作为本次结果。

修复后 Windows 定向回归 52/52 通过：`VoiceActionCoordinatorTest`、`DeviceQuietTodayServiceTest`、`ProactiveInteractionServiceTest`、`SilentPresenceServiceTest`、`ReminderDeliveryServiceTest`、`BodyMotionAutoServiceTest`。干净候选 `3a5980c98d30ba02b74eca9afe3f7662fd6382e5` 的完整 Linux 回归共 145 个报告、611 项，零失败、零错误、零跳过；容器终态退出码 0、未 OOM。报告目录为忽略的 `.tools/body-quiet-linux-regression-20261001`，最终压缩提交的服务端源码须与该候选完全相同。

新增 `DeviceQuietVoicePersistenceTest` 使用真实 Spring 注入和独立 PostgreSQL，创建虚构设备、两个伙伴和会话：从 A 暂停伙伴主动聊天，从 B 开启设备今日安静，再从 A 恢复设备陪伴。数据库断言设备级暂停到当地次日零点、另一设备不受影响、A 的角色暂停保留、舵机仍禁用。测试实际运行并通过，不使用真实语音、模型或生产设备。

Linux 采用 [Testcontainers 官方容器内运行方式](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/)：只读挂载 `server`，容器内复制 `pom.xml`、`src`，连接 Docker socket；Docker Desktop 使用 `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`。固定镜像 `maven@sha256:2b4496088e7b80ae10a8c9f74e574ea21380325a006ec684532ad6bad5bc7273`、Java 21/Maven 3.9.16、2 CPU／2 GiB。宿主 Maven repository 仅为只读依赖种子，生产环境和 `.env` 不传入，测试使用自己的 PostgreSQL。原始报告保存于忽略目录 `.tools/body-linux-regression-20261001`，对外只汇总计数、状态和非敏感类名。

容器内 `MAVEN_OPTS=-Xmx640m`，完整命令为 `mvn -B -ntp -Dmaven.repo.local=/maven-repository '-DargLine=-Xmx768m -XX:ActiveProcessorCount=2' test`。两个测试容器均已结束，报告已复制到宿主；旧 Windows 31 项连接异常不再作为当前完整回归阻塞。

## 制品与发布边界

发布基线 `body-motion-readiness-20260929` JAR SHA-256 为 `837AE63FF171C6263D4F956835E000F89FD2A2FFA9869F551BF344C81615D804`。ASR 调查工作树有其他工作流未提交修改，其目标 JAR 也与线上七个条目不同；不能用该新 JAR 或主线旧语音源码覆盖现行实现。

只读复制运行容器的实际 JAR，严格核对指纹。`scripts/build-device-quiet-overlay.py` 只替换从本任务干净源码编译并测试的 `VoiceActionCoordinator*.class`，增加来源元数据；逐条核验其他类、依赖、配置和静态资源的内容完全相同。不覆盖、暂存或清理 ASR 工作树；本次不冒充完整语音源码已纳入当前分支。保留基线及回退镜像，后续正式整合语音源码另行处理。

在本任务干净提交、对应编译与回归完成后执行：

```powershell
$sourceCommit = (git rev-parse HEAD).Trim()
python scripts/build-device-quiet-overlay.py --baseline .tools/device-quiet-release-20261001/baseline.jar --baseline-sha256 837AE63FF171C6263D4F956835E000F89FD2A2FFA9869F551BF344C81615D804 --classes server/target/classes/com/kj/stackchan/voiceaction --commit $sourceCommit --output .tools/device-quiet-release-20261001/candidate.jar
```

以实际运行的基线镜像 `sha256:bc48d584e28633c28c6026df180a539ce3f64b1902eeed30deb59e7fb0d34ccd` 构建，仅加入候选 JAR。核验只替换 3 个协调器类并增加来源元数据，其他原有条目内容全部相同。最终 JAR SHA-256 为 `ab411bcbeb0909a307cc37f13e39728e37a564e21cd9877a5b12fb4c0b55d5fb`，来源元数据绑定上述 `3a5980c` 候选；后续任务压缩只更新部署辅助脚本及文档，不改服务端源码。镜像为 `stackchan-foundation-server:device-quiet-voice-20261001`，ID `sha256:c30740e851fc8e8f565c2786c4150f0e14ba834899485b2c780eb50e14374372`。

发布前通过管理 API 关闭自动动作和舵机，另读新鲜心跳确认 `DISABLED / NONE`。执行：

```powershell
./scripts/deploy-body-motion.ps1 -CandidateImage stackchan-foundation-server:device-quiet-voice-20261001 -BuildVersion device-quiet-voice-20261001 -ExpectedSchema 53 -PreserveConsole
```

首次预检在停机前拒绝了环境比对：PowerShell 参数 `$CandidateImage` 与旧局部变量大小写相同，导致镜像信息被强制转为字符串。局部变量改为 `$candidateImageInfo` 后再执行成功；环境比对没有放宽。实际停写备份包 `stackchan-release-20261001020634` 保留，隔离 V53 恢复、数据计数、配置解密和虚构管理员登录均通过。既有环境、挂载和控制台保持，原 8080 于 2026-10-01 02:07:12（Asia/Shanghai）启动新镜像。

发布后服务健康 `ok`、镜像 ID 和 JAR 指纹一致、重启计数 0；从本次启动至收尾复核，错误日志计数 0。HTTP 首页 SHA-256 为 `98e7d9f3ab0a678674d962bf6c49d71548508398d534f81d590cb5e36d19cff5`，与基线一致；18 个引用资源的前次验证保留，本次未重新逐项获取。数据库仍 V53，模式仍 LAN HTTP development，生产仍遵守 HTTPS-only。

服务器替换使设备按本地断线规则禁用。用户新确认“仍在旁，可以保持这些条件”后，仅启用反馈探测并恢复低频自动开关，没有发送转动模板。最终新鲜 API/数据库一致：`1b46c28` 在线、命令可用、`motion_armed / ARMED`、反馈和校准有效、失败码 `NONE`、故障计数 0、自动开关 `true`、工作日 `OFF`、临时管理员 0。这些是收尾快照；断线、重启或安全停止后仍不会自动重新启用。

回退仅恢复发布基线镜像和原构建版本，保留本次备份；本次无数据库迁移。执行回退会再次断开设备并禁用舵机，恢复动作仍需现场条件。发布脚本在 `-PreserveConsole` 的替换失败路径保留原镜像恢复逻辑，本次未触发回退。

## 完成边界

本轮实现、完整回归、隔离恢复、部署和恢复低频动作已完成；本任务已以唯一中文任务提交推送分支；下一条精确操作为用户创建 PR、审核和合并，再按平常需求自然使用。完整回归和虚构设备控制只证明软件，实际说出这两条新设备控制指令的识别及听感未现场验证。实体五模板和普通语音后点头已有现场证据，真正运行中本地语音抢占、自然动作及主动开场的陪伴价值仍未验证；该发布阶段当时保持 ACTIVE；当前等待状态见下方记录，不要求用户重复点头。


## 逐项完成复核

2026-10-01 发布后超过 25 分钟，运行镜像、零重启及零错误日志与收尾一致，健康 `ok`；设备新鲜 `1b46c28 / ARMED / NONE`、故障计数 0、自动动作开关 `true`、工作 `OFF`。从服务器启动起没有新增语音回合或动作命令。该证据支持空闲运行稳定，不能证明使用中的响应或陪伴体验。

| 要求 | 当前证据 | 判定与剩余操作 |
| --- | --- | --- |
| 五个固定动作能执行、回正 | [五模板设备终态与用户现场观察](body-motion-20260929-field-acceptance.md)；[新固件语音后点头](body-motion-audio-permit-20261001.md) | 已有现场证据，不重复模板测试 |
| 五项主要自动业务触发 | 上述现场记录中的工作开始、确认返场、待办完成、工作结束及较长网页文字回复 | 已有设备与用户观察；自然触发是否恰当仍未评估 |
| 固件优先拒绝/停止不允许的动作 | 生产安全状态机六组宿主测试和 500 次竞争；两套构建与实际栈预算；头顶触摸及断线现场结果 | 软件与部分实体路径通过；真正本地语音回合中断的实体证据仍缺失，按用户要求不继续音频工作 |
| 普通说话后身体动作仍可用 | 新固件普通回复和一次失败续聊后 `ARMED / NONE`；随后点头 `COMPLETED / NONE`、用户反馈正常 | 针对性现场回归通过；收尾后的空闲快照保持正常 |
| 今天安静点语音/页面范围一致 | 定向 52/52、完整 611/611、真实 Spring/PostgreSQL 双伙伴范围断言；实际发布协调器类与受测类一致 | 软件及部署通过；这两条新设备控制语句的真实识别和听感未评估 |
| 部署可核实、配置和数据可恢复 | 镜像/JAR/HTTP 首页指纹；停写备份和隔离 V53 完整恢复；发布后健康、零重启和零错误日志 | 通过；保留回退镜像和备份，任务实现与交接同一个中文提交 |
| 日常使用有陪伴价值且不打扰 | 用户最新确认尚未自然使用，发布后也没有新的自然回合/动作 | 未评估；等待平常使用后的实际观感，自动化计数不能代填 |

软件、部署和已有现场证据没有新的失败项。剩余自然体验及未执行的实体语音抢占需要用户现场使用证据；本次不发送动作、不触发收费模型，也不调整音频设置。该发布阶段当时保持 ACTIVE；当前等待状态见下方记录，等待新的自然使用反馈后再决定是否修复。


## 等待条件与恢复入口

2026-10-01 等待实际使用证据：用户最新答复为“尚未自然使用”。连续三轮复核都只有相同的健康快照，现行服务器无重启、无错误日志，设备新鲜 `1b46c28 / ARMED / NONE`、故障计数 0、低频自动开关开启；发布后语音回合和动作命令均为 0。实现、完整回归、部署及已通过的实体路径已交付，没有新的产品失败可独立修复。整体目标 BLOCKED，等待正常使用后的实际反馈；真实运动中本地语音抢占仍未验，按用户要求暂不继续音频工作。下一条精确操作：用户按平常需求使用后提供一次有帮助、打扰或不自然的具体感受，Agent 再核对对应链路并决定修复；用户也可在明确愿意补验时提供现场条件。软件测试和重复空闲轮询不能代替这项证据。

部署和低频开关保持当前状态，目标等待不会向设备发送停止或启用命令。保留已有制品、回退镜像及备份，不重复已通过的动作测试。任务实现、验收和本次等待交接仍合并在同一个中文任务提交中，仅推送当前分支；PR 和合并由用户执行。
