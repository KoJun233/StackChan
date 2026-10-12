# R00：版本基线、兼容整合与首轮验收准备

- 文档状态：ACTIVE；需求状态：SPECIFIED；实现状态：READY_FOR_REVIEW（软件基线通过；现场/部署待验）
- 需求 ID：R00；映射原计划：PLAN-00；优先级：P0，所有软件迭代的前置核对
- 核对日期：2026-10-12；源码基准：`989b3d9`
- 依赖：无新增业务需求；遵循[入口](README.md)、[公共契约](00-shared-contracts.md)、[仓库规则](../../../AGENTS.md)。
- 上游设计：[十二周计划第4、8、10节](../../project/companion-product-iteration-plan-2026-10-12.md)、[开发环境](../../project/development.md)、[部署状态](../../project/status/deployment.md)。

本项已由当前R00任务实施，软件结果、FR/AC和现场边界见[基线交接](../../project/baselines/companion-v1-baseline.md)。以下FR/AC编号与原规格保持；后续实施仍须核对最新Git及运行事实。

## 1. 用户问题与完成程度

项目当前有可用的语音、记忆、委托、通知和身体表达，用户四类体验均不满意，但尚无本轮逐回合测量。主线、原 V57 分支和历史安装来源又不同。如果 AI 直接从主线新增功能并安装，可能遗失已经在历史部署使用的待续许可和数据。

本需求必须交付一个**保留旧能力、可验证、可回退的软件候选基线**，以及三项真实失败样例、一个真实目标和可用工具范围。完成源码兼容与隔离验证才放行下游；没有实体授权时可以完成候选和证据准备，安装/真实语音测量标为待验收。写一份“先看版本”说明而不核对源码和迁移不算软件任务完成。

## 2. 已核实的三种版本事实

| 来源 | 2026-10-12事实 | 开发意义 |
| --- | --- | --- |
| `origin/master@989b3d9f591a854d9ca26fe05b78fb4af53329b5` | PR #48已合入，包含自然语言持续委托、历史成果、Bark Tool及口语/能力路由修复 | 后续从更新后的最新 master 建任务，不重复实施这些功能 |
| `codex/companion-topic-continuity@8ad9822` | V57待续话题和进展尚未合入该主线；原工作树另有用户未提交文件 | 保留分支，不修改原工作树；在新候选上整合必要增量 |
| 历史冻结 `a37d220e7a2a0189b28412ffd420b2173fbbfc13` | 部署状态记录安装 `bark-voice-a37d220`，保留V57–V60 | 作为兼容对照，不冒充当前在线检查；不要整棵覆盖最新主线 |
| 历史固件 `b872f2ff185474cac90b372d1b3419873196f1db` | `device008-ui-r3-b872f2f`；3,141,344B，3MiB槽余4,384B | 新固件必须重新测资源，不以旧空闲FPS当新并发成绩 |

以上 Git 对象本轮可解析。执行时必须刷新它们的祖先/合入关系，主线若已合V57则不再重复 cherry-pick。历史部署收据只能证明曾经安装；正在运行的版本、schema、设备身份需要授权范围内的只读证据。

## 3. 现有能力与起点

| 已有能力 | 真实代码/文档起点 | R00不可破坏 |
| --- | --- | --- |
| 语音前台 | `firmware/main/voice_control.c`、`voice_service.c`、`voice_capture_policy.c`；`server/.../speech/VoiceTurnService.java` | 唤醒、触摸停止、8秒边界、默认关闭连续回合、旧协议 |
| 有限上下文 | `VoiceConversationContextPolicy` | 4轮/30分钟范围和不同设备/角色隔离 |
| 确认记忆 | `server/.../memory/LongTermMemoryService.java`、`MemorySearchQuery.java` | 未确认不可召回、纠正替代、allowProactiveMention |
| V57待续 | 原提交的 `TopicContinuationService`、`ContinuationState`、`V57__confirmed_topic_continuity.sql` | 同意许可、设备日预算、暂停/停止/休眠、进展与删源锁 |
| 持续委托 | `DelegationService`、`ModelDelegationTaskPlanner`、`AgentDelegationTaskExecutor` | 调度、真实工具步骤/证据、取消守卫、旧 run/delivery枚举 |
| 现有通知 | `AgentOrchestrator`、`BarkNotificationTool`、`BarkNotificationService` | 明确外发请求、缺正文追问、能力咨询只读、UNKNOWN边界 |
| 管理网页 | `apps/stackchan-console/src/views/companion/`及`src/api/modules/` | 原记忆、任务、历史成果、身份校验、资源指纹 |
| 身体与UI | `expression_engine.c`、`robot_eyes_renderer.c`、`device_ui.cpp` | 音频优先、安全停止、旧UI v1兼容、默认动作许可 |

`server/...`是便于说明的目录缩写，不是可执行路径；Java根为 `server/src/main/java/com/kj/stackchan/`。实施前用 `rg --files` 确认类名与实际 package，不凭文件名建议新建第二个业务服务。

## 4. 本项范围与排除

范围：Git/文档事实恢复、已有V57兼容整合、迁移验证、回归候选、旧数据保持、真实失败基线和执行边界、版本证据、回退准备。允许按任务授权在隔离分支修兼容冲突；不重做V57产品逻辑。

不在范围：R01–R11的新业务能力、扩大录音/角色权限、采购模型/连接器、刷写、真实通知、安装运行库、改变LAN/生产模式、自动推送/创建PR。获明确安装授权后按独立部署交接执行，不把“基线通过”解释为部署许可。

## 5. 功能要求

### R00-FR01：建立可追溯起点

执行 `git status --short --branch`、`git log -5 --oneline`、读取规定状态/设计，记录用户及其他工作流改动的路径和指纹。先刷新origin/master，再从最新master建独立`codex/`分支和隔离工作树。保留原分支、原文件、日志、缓存和未跟踪数据；不暂存它们，不自动clean/reset。更新状态中的基准/最后验证提交必须可解析且在当前分支祖先中。

### R00-FR02：现有事实冲突必须修复

若状态称“等待Bark PR”但Git显示已合入，先记录STALE及事实证据，再修正等待条件。每项事实分别标源码、历史验证、历史安装、本轮只读运行核对；不能用一个版本字段囊括四者。发现今天设备不同版本就更新相关状态，不隐瞒也不刷回历史版本。

### R00-FR03：V57增量清单

列出8ad9822相对其基准的源码、迁移、前端、测试、ADR与runbook。至少审阅 TopicContinuationService、VoiceTurnService、CompanionPromptService、VoiceActionProposalService、DeviceUiService、PersonalDataService、LongTermMemoryEntity/Service/Repository及V57 SQL的冲突面。使用Git对象读取，不以原工作树未提交文件充依赖源码。

主线缺原V57文档时可只读：`git show 8ad9822:docs/project/topic-continuity.md` 和 `git show 8ad9822:docs/project/decisions/0072-confirmed-conversational-topic-continuity.md`。整合时将其必要设计/测试带入新候选，并以当前状态为准重写交接；不得把旧状态整段覆盖最新主线。

### R00-FR04：迁移兼容

先列出候选、最新主线、历史冻结源全部Flyway版本/SQL指纹。V57须保留原迁移内容及checksum；不得重命名V57来绕重复、删除flyway_history、执行repair掩盖差异或自动改线上库。若master同版本已有不同SQL，停止这一路径，形成明确冲突记录和新的兼容迁移方案，不能猜测数据已一致。

在隔离PostgreSQL验证两条路径：全新数据库从零应用候选全部迁移；由合法一致旧基线/V57–V60数据快照升级候选。真实备份需要授权且秘密本地保护，测试可以用等价合成夹具，但要标明不是线上恢复。验证confirmed普通记忆不会自动获得待续许可。

### R00-FR05：保留已有数据和身份

升级前后比较：管理员/设备/角色/个人任务/记忆/待续状态与进展/委托/运行/通知设置及回执数量；比对必要字段的本地摘要。密钥只比较是否可解密及摘要，不输出明文；DPAPI和加密材料按现有恢复runbook处理。旧 taskId/runId不变，已有UNKNOWN/SENT不重置为待发送。

### R00-FR06：并发与取消回归

整合后原V57来源删除锁、待续同意、角色切换、日预算以及委托revision守卫同时有效。VoiceTurnService兼容修复不得令webtext获得设备动作/存进展权限，能力咨询不得获得Bark写工具，历史已发送正文不得再次成为授权。

### R00-FR07：冻结候选证据

记录候选Git SHA、是否干净、构建工具版本、迁移指纹、服务端JAR/镜像和控制台文件清单摘要。仅源码/测试候选可写“代码就绪”，产物未构建不能写“可安装”。安装对照必须是此候选，不能拿旧840项或V57历史699项充新整合结果。

### R00-FR08：语音失败基线

收集至少三项用户目前不满的可复现失败，每项记录匿名场景ID、原意、受控输入、当前表现、期望、版本/配置、频次和可复現步骤。推荐覆盖截断/等待、短指代/纠正、任务接下却没成果。输入与录音留本地、不提交原始个人内容。没有用户样例时可准备合成复现夹具并标“非真实用户基线”，真实基线继续待验，不能以等待为由不完成可执行兼容工作。

### R00-FR09：首个真实目标和范围

默认验收使用二至三个公开HTTPS桌面方案URL，要求比较事实/缺失信息、给推荐和下一行动；用户另选目标则记录其可用工具、成果、验收、时间和交付方式。目标明确且工具范围足够的输入无需再次问是否允许开发测试。需要真实外发/购买的部分不继承只读目标权限。

### R00-FR10：供应商和工具边界

实际读取可信能力目录和已配置状态：公开URL/feed、个人查询、文本生成、Bark等。没有通用搜索就记录缺能力；未配置供应商不得用占位凭据伪造可用。仅记录版本/配置摘要、内部限额、可用/未配置，不输出Token；不进行付费测试、Bark发送或供应商切换。

### R00-FR11：硬件资源基线

读取当前候选app大小、分区、内部RAM/PSRAM、voice/UI/transport/provisioning栈预算和已验证profile。固件不改时可注明保持历史r3，不要求为了文档重刷；固件有改时必须两套profile构建及相关宿主验证，产物不得超过现槽，不降现有安全预算来塞功能。若4,384B不足，先减功能/放服务端，资源阻塞不得绕过检查。

完整新固件放行同时满足[R08资源门禁](R08-embodied-expression.md)：至少保留4,384B历史余量，若同条件B0余量更大则保留B0；内部heap/stack不退化。仅“未超过3MiB”不等于全部资源验收通过。资源未过时仍可交付服务端/网页能力与旧设备降级。

### R00-FR12：可回退准备

复用[部署状态](../../project/status/deployment.md)和现有冻结构建/恢复脚本，不另造未经验证的“复制目录即可回退”。候选软件、schema与一致数据配成一套；保留已知旧产物/加密恢复材料/设备NVS策略和回退步骤。恢复先在隔离实例核验，再考虑任何运行安装；回退不会自动执行降级DROP、清空个人数据或凭据轮换。

### R00-FR13：下游门禁

语音模块R01可在版本/语音边界明确后开始；R02使用确认过的兼容候选；完整新固件/目标候选进入现场之前必须R00迁移与数据保持通过。状态分开写 SOFTWARE_BASELINE_READY、FIELD_BASELINE_PENDING、DEPLOYMENT_PENDING（这里是验收记录字段，非替代全局状态枚举），不因实体门禁而让已完成源码永久显示未开始。

### R00-FR14：可执行交接

更新服务端/前端/固件/部署相关状态和全局总览，写基准、验证提交、候选、已完成/未完成、精确下一条操作、阻塞条件和证据位置。本任务仅改文档时只更新产品工作流和全局，不伪造其他工作流实现。软件整合任务相对最新master压成一个中文提交；未获许可不推送或开PR。

## 6. 本地基线记录的数据契约

拟新增**证据记录文件**，不是业务API/表。建议在任务分支新增 `docs/project/baselines/companion-v1-baseline.md`，匿名内容可进Git；私人计时/录音/备份仅本地。路径是实施任务将创建，本轮需求包不声称该记录存在。

| 字段 | 必填与约束 |
| --- | --- |
| sourceMaster / candidateCommit | 完整SHA，master核对时刻与祖先关系 |
| legacyTopicSource / historicalInstalledSource | SHA、事实类型和最近可信收据时刻 |
| schemaVersionsAndChecksums | 候选/旧基线列表与指纹，不含密码/库转储 |
| deviceProfile / firmwareDigest | 已知profile/摘要或UNKNOWN及原因 |
| capabilitySnapshot | 工具名、可用状态、权限类别、已核实限额 |
| failures | 至少三条匿名受控样例或明确真实样例待验 |
| referenceGoal | 真实/合成标记、资料来源范围、预期成果、权限 |
| verification | 命令、工具版本、数量、通过/失败/待验和证据路径 |
| recovery | 候选与旧产物、隔离恢复状态、禁止混用事项 |

本项无需新增REST接口、前端设置页或用户强制确认卡。诊断记录只供开发/审阅；用户可见说明采用“先保留你已有的数据和待续能力，再做体验改进”，不用Flyway/lease术语。

## 7. 验收用例

| 编号 | Given / When / Then |
| --- | --- |
| R00-AC01 | 原工作树存在用户components改动和未跟踪日志；建立基线；原路径/指纹不变且未暂存，任务在独立分支 |
| R00-AC02 | 状态写旧Bark PR未合入而Git已合；核对；记录STALE后修正，不再次实施Bark |
| R00-AC03 | master未含8ad9822；整合；候选含待续源码/迁移/测试且不覆盖master口语路由 |
| R00-AC04 | 届时master已含V57；执行；检测已有增量并跳过重复cherry-pick，仍验兼容 |
| R00-AC05 | 原部署只有历史收据；输出；明确历史来源，今天运行版本标未重新检查 |
| R00-AC06 | 同V57编号内容冲突；预检；阻塞迁移方案，不repair/改checksum/碰线上库 |
| R00-AC07 | 全新隔离库；迁移；全部候选迁移成功且旧普通记忆不获待续许可 |
| R00-AC08 | 带V57–V60与旧个人数据的隔离库；升级；ID/数量/关键摘要保持，回执不重发 |
| R00-AC09 | 删除来源与异步进展并发；整合回归；进展不复现且已确认原事实按原管理保持 |
| R00-AC10 | webtext提供deviceId；请求；不会取得实体动作/待续进展/外发权限 |
| R00-AC11 | 能力咨询或旧发送历史；新回合；模型没有Bark写工具，缺正文追问兼容 |
| R00-AC12 | 无真实失败录音；报告；合成样例清楚标注，真实首声UNKNOWN，旧成绩不充本轮 |
| R00-AC13 | 给出比较目标但无搜索工具；规划；用提供URL或缺资料阻塞，不编造已全网搜索 |
| R00-AC14 | 固件候选超过3MiB槽；验证；阻塞该候选，不削engine/预算或自动改分区 |
| R00-AC15 | 未授权部署/刷写；候选完成；仅交付可审阅版本/回退证据，不执行实体操作 |
| R00-AC16 | 源码与产物不一致；对照；候选不可安装，重建匹配产物，不复用旧摘要 |
| R00-AC17 | 旧产物和新schema不兼容；回退演练；使用一致旧数据/产物组合，隔离核验先于运行回退 |
| R00-AC18 | 相关测试失败或被skip；交接；记录失败/skip和阻塞，不标全量通过 |

## 8. 实施验证命令与证据

先按开发环境引导满足engine的Node/pnpm。产品文档任务的最小命令为：

```powershell
git diff --check
pnpm docs:check
pnpm docs:check:test
```

软件整合任务先跑当前已有相关服务端类，V57带入后补待续类，不把未存在类的失败当通过：

```powershell
& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml '-Dtest=VoiceTurnServiceTest,VoiceConversationContextPolicyTest,AgentOrchestratorTest,BarkNotificationToolTest,DelegationPersistenceTest,DelegationUpgradePersistenceTest,LongTermMemoryServiceTest,MemoryRecallPersistenceTest' test
& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml '-Dtest=TopicContinuationPersistenceTest,TopicContinuationUpgradeTest,PersonaMemoryControllerTest' test
pnpm --filter @stackchan/console exec vitest run src/api/modules/personaMemory.test.ts src/views/companion/memories/list.test.ts src/api/modules/delegations.test.ts src/views/companion/delegations/list.test.ts src/views/companion/delegations/detail.test.ts
pnpm --filter @stackchan/console run build
```

修改服务端/迁移后跑完整server测试与打包，迁移升级用独立PostgreSQL；涉及固件时按状态/现有候选脚本构建两profile并做栈预算。宿主最小回归：

```powershell
python scripts/test-firmware-startup.py
python scripts/test-face-tracking.py
python scripts/test-device-server-relocation.py
python scripts/test-device-transport-maintenance.py
python scripts/test-voice-interaction.py
```

不要为只写需求的当前任务运行整套设备/软件测量；以上命令是将来R00软件整合的验收要求。命令路径发生变更时先查实际脚本与状态再执行，不能创建空同名测试来让命令成功。

## 9. 开发 AI 完成检查

逐项交付R00-FR01–14、AC01–18的证据；明确是否仅文档、候选整合、隔离迁移、现场基线或实际安装。保留历史来源与最新事实的区别。候选兼容通过后，下一个精确动作是选择R01三个失败样例或R02首个可用目标，按对应文件建独立实施任务；没有业务增量的R00不新增“目标已完成”假数据。
