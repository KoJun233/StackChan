# R03：目标生命周期、阶段与反馈

- 文档状态：ACTIVE；需求状态：SPECIFIED；实现状态：NOT_STARTED。
- 版本：1.0；日期：2026-10-12；代码核对基准：`989b3d9`。
- 原计划映射：GOAL-01；首轮 LOOP-01 增强；建议阶段 W4–5。
- 需求负责人职责：服务端定义生命周期、前端呈现进度与反馈，设备复用同一状态查询。
- 入口：[需求索引](README.md)；共同硬边界：[公共契约](00-shared-contracts.md)。
- 前置：[R00 基线](R00-baseline-and-integration.md)、[R02 项目闭环](R02-project-loop.md)。
- 执行依赖：[R04 持久执行](R04-durable-execution.md)、[R05 权限预算](R05-permissions-and-budgets.md)。
- 消费接口：[R06 接续](R06-memory-continuity.md)、[R09 手机](R09-mobile-handoff.md)、[R10 能力证据](R10-capability-and-evidence.md)。

## 1. 要解决的用户问题与完成程度

用户交给伙伴的是一个有结果标准的事情，而不只是指定每天做什么。伙伴必须能回答“我们做到哪里、结果满足什么、还缺什么、下一步为什么做这个”，并根据纠正持续推进。目标完成由用户认可与标准检查共同决定，不能把工具执行成功、推送成功或收到一个正文当成目标完成。

本项完成后，同一真实目标可以被拆成有限阶段；每次交付后可以接受、修改、补条件、暂停或取消；第二天读取相同的进度和下一行动；已改预算或偏好不会让旧计划继续动作。无需新建提醒来模仿阶段推进。

MVP 的默认验收项目是“比较二至三个公开 HTTPS 桌面布置方案，符合尺寸和预算，解释推荐与取舍”。真实资料不足时允许受阻、等待用户或交付有明确限制的草稿，不能把未知价格补成事实。

## 2. 实际起点与代码定位

以下路径以仓库根为基准，实施前必须重新读取，不能把本节视作已经实现新增接口。

| 现有文件/结构 | 基准中实际已有 | 本项增量 |
| --- | --- | --- |
| `server/src/main/java/com/kj/stackchan/delegation/DelegationService.java` | 自然描述、安排、revision、暂停、真实 run 历史 | 增加目标/阶段业务层，关联旧任务而不替换 |
| `server/src/main/java/com/kj/stackchan/delegation/ModelDelegationTaskPlanner.java` | 理解目标和时间、缺条件单句追问、保存当前工具范围 | 提出有限阶段、每阶段成果和目标完成标准 |
| `server/src/main/java/com/kj/stackchan/delegation/AgentDelegationTaskExecutor.java` | 独立 ReactAgent、真实步骤/证据、缺能力 BLOCKED | 接收当前阶段输入与已批准下一行动 |
| `server/src/main/java/com/kj/stackchan/api/DelegationController.java` | `/api/v1/delegations` 的创建、修改、暂停、执行和历史 | 保持接口，目标拟用 `/api/v1/companion/goals` |
| `apps/stackchan-console/src/views/companion/delegations/` | 描述输入、理解回显、历史结果 | 新增目标进度与反馈操作，复用现有成果视图 |
| `server/src/main/resources/db/migration/V59__generic_delegation_tasks.sql` | 通用任务、steps、evidence、occurrence | 追加关联表，不修改旧 checksum |

目标状态、阶段状态、目标 API、阶段规划和最终完成确认在基准中尚不存在；不能在交接写成“已支持目标”。[ADR0074](../../project/decisions/0074-natural-language-delegation-tasks.md) 的工具证据、修订、范围与后台隔离规则继续适用。[计划书](../../project/companion-product-iteration-plan-2026-10-12.md) 的 W3 有用成果门槛先于目标扩张。

## 3. MVP 范围与排除

- 必须：目标定义、明确完成标准、最多五阶段、当前阶段、下一行动、阶段成果、反馈历史、修订与八状态生命周期。
- 必须：完整明确的只读/文本委托可用同一条用户话语作为接受依据，避免机械二次确认；模糊目标只问最关键缺失项。
- 必须：默认串行阶段；同一目标同一时刻最多一个执行阶段，阶段依赖形成有限有向无环顺序。
- 必须：所有旧持续委托继续工作；旧记录允许无 goal 关联，不迁移成“已经完成的目标”。
- 不做：自动子目标、无限自主分解、跨目标依赖引擎、团队协作、多管理员、自动购买或任意外部写。
- 不做：自动把每段闲聊变成目标、自动替用户设置截止时间、按定时次数判定目标完成。
- 目标最多五阶段；每阶段最多五个预先描述的 planned actions。超出时返回范围过大说明，请用户收窄，不能偷偷丢弃第六项。
- 目标阶段规划应优先一至三阶段；五是硬上限，不是每个目标必须填满的模板。

## 4. 目标定义与状态契约

一个有效目标至少包含：原用户描述、目标成果、完成标准、已知限制、资料范围、交付方式、执行允许范围和下一行动。截止时间可空；未要求定时执行时不得凭空设置 DAILY。完成标准一至五条，每条有可验证方法及是否需用户主观判断。

示例标准：输出三个方案的尺寸/价格来源；超预算方案明确标注；推荐理由至少关联两个已知条件；缺资料列出；最终建议由用户明确采用。对未知价格，只能把“列出未核实条件”视为该条件的满足，不能自动满足“预算内”。

| 目标状态 | 含义 | 执行资格 |
| --- | --- | --- |
| DRAFT | 草案未接受或理解尚待确认 | 不启动阶段 |
| ACTIVE | 已接受且有合法下一行动 | 依 R04/R05 可领取 |
| WAITING_USER | 缺关键输入、阶段审阅或具体批准 | 不能绕过等待原因 |
| BLOCKED | 缺能力、预算、恢复错误或未知副作用 | 需明确恢复与重查 |
| REVIEW_READY | 最终成果已检查，等待采用 | 不主动重跑最终成果 |
| PAUSED | 用户暂停目标 | 阻止新工具和交付 |
| COMPLETED | 当前修订标准通过且用户明确采用 | 终态，不启动 |
| CANCELLED | 用户终止目标 | 终态，不能 resume |

阶段状态固定 `PENDING / RUNNING / SUCCEEDED / FAILED / BLOCKED / CANCELLED`，没有 PAUSED 或 REVIEW_READY。阶段 SUCCEEDED 仅表示阶段交付检查通过；用户反馈另存。目标暂停会使运行阶段 CANCELLED 并保留原因及历史，恢复时为仍需工作部分产生新的阶段执行计划记录，不改写旧 run。

## 5. 编号功能要求

**R03-FR01 目标识别。** 只在明确交代一件要做的事情时创建目标；举例、讨论能力、查看结果和假设语句不能创建。两个活跃目标下的“继续那个”必须先问目标，不能按最近更新静默选。归属来自认证会话/设备/当前伙伴。

**R03-FR02 草案和理解。** 模型只提议结构，服务端校验数量、文本长度、工具名和依赖。完整明确指令保存用户接受证据，直接进入 ACTIVE 或可解释的 BLOCKED；输入不完整保存草案并进入 WAITING_USER。未持久成功不说“已接下”。

**R03-FR03 关键追问。** 每轮只问一个决定能否执行的关键问题。可空的截止时间、装饰性标题不得阻塞。问“桌面可用宽度是多少？”时用户答“80厘米”只补当前待问字段，不改资料范围、收件渠道或授权。

**R03-FR04 有效完成标准。** 禁止“帮用户满意”这种无检查方法的标准。每条保存 criterionId、文字、检查方式、检查来源和必需标志；所有必需条目通过才能 complete。纯文本结构可由程序检查，偏好与可用性必须依用户确认，模型自评不够。

**R03-FR05 阶段设计。** 每阶段明确目的、输入、预期成果、检查、前置阶段和一至五行动；行动文字可解释具体要读什么或生成什么。不得创建仅叫“思考/处理中”的空阶段。阶段 ID 稳定，同修订顺序由 position 与依赖决定。

**R03-FR06 下一行动。** 只选择第一个前置检查已通过且可授权的 PENDING 阶段；显示一句用户能理解的动作、必要工具、预计触发条件和受阻项。没有可执行阶段但存在未完成标准时应 WAITING_USER/BLOCKED，不能显示空白 ACTIVE。

**R03-FR07 阶段交付。** 关联真实 task/run/attempt 和不可变成果版本。SUCCEEDED 需成果结构、必需证据与阶段检查通过；无新内容 NO_UPDATES 不算新阶段交付。失败与受阻显示已完成部分及恢复选择，不能重播旧结果冒充更新。

**R03-FR08 反馈。** 支持接受本阶段、需要改进、修改目标条件、补充信息、暂停和取消。反馈保留发生时间、作者来源、目标修订和对应 artifactId；普通点赞或标已读不代表最终采用。改进反馈必须形成具体下一行动或一次澄清，不只存一句评论。

**R03-FR09 修订。** 目标、标准、来源、阶段、行动范围或交付方式变化时 revision+1。保存修订、失效旧计划/批准、取消旧执行与确定下一状态必须同一事务；历史成果保留旧 goalRevision，并明确为历史。已有成功外发如实保留。

**R03-FR10 反馈后的推进。** 接受一个非最终阶段后，如下一阶段为已获准只读且无需新条件，目标可继续 ACTIVE；若需用户决定则 WAITING_USER。最终阶段成功仅进入 REVIEW_READY；接受其成果后仍要执行完成标准检查与明确完成确认。

**R03-FR11 完成。** complete 必须指定当前 revision、artifactId 和明确认可证据；验证该成果当前关联有效、所有必需标准 PASS、无待决必需阶段及尚未处置的必需未知副作用。历史UNKNOWN只可经R05明确用户核查/处理选择登记resolution，原回执不得伪改SENT；已被明确处理的历史不永久阻断所有完成。失败返回 409/422 并保留 REVIEW_READY 或受阻状态。Bark SENT、页面打开、工具成功不替代用户认可。

**R03-FR12 暂停。** 暂停事务先持久状态/修订与失效事件，再尝试取消工作；操作成功后不再开始新工具或普通目标提示。用户文案说明“已暂停，已发出的请求可能无法撤回”。已保存成果仍可查看，不自动删除。

**R03-FR13 恢复。** resume 重查归属、角色、工具与预算，根据 pausedFromStatus 恢复 ACTIVE、WAITING_USER、BLOCKED 或 REVIEW_READY。纯暂停/恢复递增修订但目标/标准/来源/范围未变时，按R10建立同一artifactId的当前有效关联，记录inheritedFromRevision/definitionDigest并重查标准与新鲜度；原成果不改、不计新产出/普通提示。改变预算/偏好/来源不得借此采用旧成果；有效关联或标准已失效则WAITING_USER/BLOCKED，不能卡在不可complete的REVIEW_READY。恢复不隐式补全缺资料/新批准；BUDGET_EXHAUSTED 午夜额度回补也不能自动恢复。重复 resume 不创建重复 run。

**R03-FR14 取消与重开。** CANCELLED 不接受 resume；若用户要重新做，显式创建新目标并可引用旧目标历史。COMPLETED 的“再调整”须显式 reopen 创建新修订并 DRAFT/WAITING_USER，保留原完成事实，不把它后台自动改成 ACTIVE。

**R03-FR15 角色/设备保护。** 资源读写都服务端验证 deviceId/roleId；归属不可直接修改。角色归档或切换使旧批准与未开始动作失效；历史保留原绑定，设备在线状态不得替代认证。跨范围查询不泄露标题或正文。

**R03-FR16 审计和可解释性。** 保存目标事件、修订、原因、标准检查与实际执行关联；UI 默认显示目的、成果与下一行动，诊断区才显示 run/attempt。日志不写目标正文、消息载荷、认证或密钥。

## 6. 允许的状态转移

以下是业务事件表，未列转移返回 `INVALID_STATE`。同状态幂等读取不生成事件，不允许客户端任意 PATCH status。

| 当前 | 事件和条件 | 下一状态 |
| --- | --- | --- |
| DRAFT | 明确接受，条件和只读范围齐备 | ACTIVE |
| DRAFT | 缺一个必需条件 | WAITING_USER |
| DRAFT | 明确接受但必需工具缺失 | BLOCKED |
| WAITING_USER | 有效补充/批准完成，尚有执行步骤 | ACTIVE |
| WAITING_USER | 补充后仍缺关键信息 | WAITING_USER |
| ACTIVE | 需要一个用户决定或新动作批准 | WAITING_USER |
| ACTIVE | 能力、预算或恢复问题 | BLOCKED |
| ACTIVE | 最终阶段成果检查通过 | REVIEW_READY |
| REVIEW_READY | 明确改进/修改标准 | DRAFT、WAITING_USER 或 ACTIVE，必须新修订 |
| REVIEW_READY | 当前成果明确采用且标准通过 | COMPLETED |
| BLOCKED | 用户恢复且原因解决 | ACTIVE 或 WAITING_USER |
| DRAFT/ACTIVE/WAITING_USER/BLOCKED/REVIEW_READY | 用户暂停 | PAUSED |
| PAUSED | 用户恢复且守卫通过 | 根据原等待原因决定，不一定 ACTIVE |
| 非 COMPLETED/CANCELLED | 用户取消 | CANCELLED |
| COMPLETED | 用户明确 reopen | 新 revision 的 DRAFT/WAITING_USER |

阶段从 PENDING 领取为 RUNNING；RUNNING 根据真实执行转 SUCCEEDED、FAILED、BLOCKED、CANCELLED；SUCCEEDED 同修订不可自动回 RUNNING。FAILED/BLOCKED仅在用户recover/resume且原因解决、守卫通过后转PENDING，再正常领取RUNNING并新建attempt，旧尝试历史不覆盖；CANCELLED不原地复活，需新修订阶段快照。NO_UPDATES结束旧run、stage回PENDING、goal WAITING_USER(reason=NO_NEW_DATA)，等待补资料/稍后再查选择，不遗留RUNNING或立即重跑。目标修订使未开始旧阶段 CANCELLED，新修订对应新阶段快照。

## 7. 拟新增数据与 API，均非基准已有

迁移版本由实施时最新 Flyway 决定，不预占 V61，不修改 V1–V60。表名以下为建议，最终需在设计/ADR固定并保持语义。

| 拟新增数据 | 必需字段/约束 |
| --- | --- |
| `companion_goals` | id、device_id、role_id、revision、status、original_description、outcome、criteria、constraints、zone_id、accepted_at/evidence、paused_from_status、block_reason、timestamps |
| `companion_goal_revisions` | goal_id+revision 唯一，目标/标准/范围/阶段快照不可变 |
| `companion_goal_stages` | id、goal_id、goal_revision、position、purpose、input、expected_output、checks、dependencies、planned_actions、status；上限服务端检查 |
| `companion_goal_feedback` | id、goal_id、goal_revision、artifact_id、type、内容、事件来源、resource_version、timestamps |
| `companion_goal_events` | 有界事件类型、from/to、revision、reason、关联资源，不记录认证载荷 |
| `companion_goal_task_bindings` | goal_id、goal_revision、stage_id、现有task_id；同阶段一条有效绑定 |

成果沿用 R02 结构与旧 run 保存；本项只加 goal/stage/revision 关联及标准检查结果，不再建一套不兼容成果正文。每个标准状态限定 `PENDING / PASS / FAIL / UNKNOWN`，UNKNOWN 不算通过。

artifactId统一由R10的单一成果引用服务生成，关联run/attempt/outputVersion，不复制正文。complete验证该artifact在当前revision的有效关联；纯暂停/恢复的合法继承可引用原不可变artifact，保存继承来源/定义摘要及当前检查，标准变化的旧artifact不能直接完成新修订。

实施本项时先落实上述最小成果引用/当前有效关联接口，采用R10已冻结契约；完整能力目录和新鲜度展示可由R10随后增强，不让R03和R10互相等待。当前标准依现有真实证据检查，无法证明的值UNKNOWN；不得为了独立验收伪造证据完整度。

新增 API 都使用现有认证和错误包装；所有写请求必须有 expectedRevision（创建无既有 revision 时除外）及 `Idempotency-Key`，键16–128 ASCII `[A-Za-z0-9._:-]`，至少保留24小时。同键不同正文409，资源归属始终服务端绑定。

| 拟新增接口 | 输入/输出要点 |
| --- | --- |
| `POST /api/v1/companion/goals` | description、已验证归属选择、可选 constraints；201返回goalId/revision/status/理解/首个追问或下一行动 |
| `GET /api/v1/companion/goals` | 按认证范围过滤，默认20上限100，稳定游标/分页；不返回别的伙伴 |
| `GET /api/v1/companion/goals/{id}` | 当前定义、阶段、最新成果、完成检查、blockReason、nextAction |
| `POST /api/v1/companion/goals/{id}:accept` | expectedRevision、明确接受依据；返回真实状态，不自动授予写权限 |
| `POST /api/v1/companion/goals/{id}:revise` | expectedRevision、具体变更、必要反馈关联；新修订与失效原子保存 |
| `POST /api/v1/companion/goals/{id}/feedback` | expectedRevision、artifactId、type、内容；反馈更新再带resourceVersion |
| `POST /api/v1/companion/goals/{id}:pause` | expectedRevision；返回保存后的暂停与在途说明 |
| `POST /api/v1/companion/goals/{id}:resume` | expectedRevision；重查后返回实际下一状态，不自动重发UNKNOWN |
| `POST /api/v1/companion/goals/{id}:cancel` | expectedRevision；返回CANCELLED，拒绝后续运行 |
| `POST /api/v1/companion/goals/{id}:complete` | expectedRevision、artifactId、明确认可；标准未过422 |
| `POST /api/v1/companion/goals/{id}:reopen` | 仅COMPLETED、expectedRevision、明确新要求；新修订草案 |
| `GET /api/v1/companion/goals/{id}/history` | 修订/反馈/执行/成果事件有界分页 |

正文上限沿用任务描述4000字符；标题不超过100个Unicode字符；outcome 1000字符；单条标准500字符；反馈4000字符；单阶段描述1000字符。服务端按code point验证用户文本，保存前去除非法控制字符，不能仅靠前端maxlength。

网络未知复用公共C03的operations收据回查；COMMITTED只表示命令业务变更/运行意图已保存，不代表成果、阶段或外发完成，不新造另一套反馈回查接口。

## 8. 事务、幂等与并发

- 锁顺序与旧委托保持兼容：device→role→goal→task→run；实现前核对实际事务调用链，禁止反向嵌套导致死锁。
- 修改用 expectedRevision 的比较更新；先验证再模型规划，最终保存时再检查，规划期间他人修改必须409，不能覆盖新版本。
- 同 revision 同 stage 只能一条有效领取；两端同时“继续”返回同一现有运行或忙碌状态，不产生两份执行。
- 反馈、修订事件、目标状态与下一行动事务一起保存。写失败不出现“反馈已保存但下一阶段仍用旧偏好”。
- 长模型调用不占用目标长事务；快照在事务外规划，工具开始与成果写入守卫按 R04执行。
- 完成与修改并发时只有一个 expectedRevision 成功；完成不能用已经被修改的旧成果。
- 暂停/取消先完成失效事务；晚到成果仅作为失效历史，不写当前阶段成功，不推送，不播报。
- 通用幂等键到期不删除外部动作永久槽；同 goal_revision/stage/occurrence 的旧副作用不能重新获得执行资格。

## 9. 界面内容与错误恢复

目标页顶层依次显示“要得到什么”“做到什么程度”“现在做到哪里”“下一步/等你什么”。阶段只显示可理解的目的与真实成果；不以进度百分比猜测模型完成度。

| 场景 | 必需文案/操作 |
| --- | --- |
| 正常接下 | “已接下：比较这三个方案。先核对尺寸和资料，完成后给你建议。” |
| 缺关键条件 | “桌面可用宽度是多少？这个条件会影响推荐。” |
| 阶段完成 | “资料比较已完成。预算仍有一项未核实，你可以补充或接受带限制的建议。” |
| 最终待阅 | “建议已准备好，等你查看和决定是否采用。”；按钮查看/改进/采用并完成 |
| 标准未过 | “还不能标为完成：方案二价格未核实。可以补资料或修改完成标准。” |
| 修订冲突 | “目标刚刚有更新，已保留你的输入。刷新后再决定如何修改。” |
| 暂停 | “已暂停。成果保留，恢复前会重新检查条件。” |
| 预算受阻 | “今天的执行额度已用完，成果已保存。额度恢复后你可以继续。” |
| 取消后继续 | “这个目标已取消。可以依据历史另建一个目标。” |

错误查询不清空用户输入；网络断开后的创建/反馈重试使用原幂等键；401先登录再读最新状态，不自动重播完成/取消等写请求；旧 revision 返回409并提示差异。接口受阻可返回保存后的业务状态，不能友好解释后将运行标成功。

## 10. Given / When / Then 验收

以下全部属于 MVP；fixture 与 mock 不能替代真实用户采用体验。FR编号和AC编号在交接中逐条映射。

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| R03-AC01 | 有明确完整只读委托与正确归属 | 创建目标 | 保存接受证据并ACTIVE，不机械要求重复确认 |
| R03-AC02 | 缺必需桌面尺寸 | 创建 | WAITING_USER只问尺寸，不猜数值、不执行 |
| R03-AC03 | 用户讨论“如果每天研究会怎样” | 聊天 | 不创建目标/任务 |
| R03-AC04 | 六个必需阶段 | 校验计划 | 拒绝并解释需收窄，不截掉阶段 |
| R03-AC05 | 一个阶段六个行动 | 保存 | 服务端拒绝超限，前端限制不是唯一保障 |
| R03-AC06 | 阶段依赖有环 | 保存 | 400并指出依赖问题，无任务启动 |
| R03-AC07 | 前阶段SUCCEEDED，下阶段只读已授权 | 接受阶段成果 | 下一阶段ACTIVE，展示具体下一行动 |
| R03-AC08 | 最终阶段真实成果和检查通过 | 执行结束 | REVIEW_READY，不能自动COMPLETED |
| R03-AC09 | REVIEW_READY且手机通知SENT | 查看推送回执 | 仍REVIEW_READY，不标已读/采用 |
| R03-AC10 | 必需价格标准UNKNOWN | 用户要求完成 | 422解释缺条件，目标不完成 |
| R03-AC11 | 当前成果标准PASS | 用户明确采用并完成 | COMPLETED，保存认可与检查证据 |
| R03-AC12 | 旧revision成果刚完成 | 同时修改预算 | 只有当前revision操作成功，旧成果不完成新目标 |
| R03-AC13 | 正在执行阶段 | pause事务成功后旧结果返回 | 无后续工具/交付，旧结果为失效历史 |
| R03-AC14 | PAUSED原因为WAITING_USER | resume | 仍等待必要信息，不自动ACTIVE |
| R03-AC15 | CANCELLED | resume或定时触发 | 拒绝INVALID_STATE，不恢复 |
| R03-AC16 | COMPLETED | 用户明确reopen | 新修订草案，原完成和成果仍可查 |
| R03-AC17 | 两个活跃目标 | 用户说“继续那个” | 一次目标澄清，不静默绑定 |
| R03-AC18 | 两端同幂等键同反馈 | 并发提交 | 同一反馈ID和状态，仅一次事件 |
| R03-AC19 | 同幂等键改变反馈正文 | 提交 | 409 IDEMPOTENCY_CONFLICT，不覆盖 |
| R03-AC20 | 伙伴A资源 | 伙伴B读取/修改 | 404或403且不泄露正文与来源 |
| R03-AC21 | BLOCKED BUDGET_EXHAUSTED | 时钟跨午夜 | 不自动启动；用户resume才重查 |
| R03-AC22 | 修订模型规划期间另一端已更新 | 规划完成提交 | 409，保留输入，不覆盖当前revision |
| R03-AC23 | REVIEW_READY成果，纯暂停/恢复未改定义 | 恢复后明确采用 | 当前有效关联和标准/新鲜度通过则可complete；原artifact不重写，不计新交付 |
| R03-AC24 | 暂停后改预算/来源或证据到期 | 恢复/采用旧成果 | 不直接继承为完成，等待新检查或工作，不能停在不可操作的待阅 |
| R03-AC25 | 阶段FAILED/BLOCKED或run NO_UPDATES | 明确recover或查询 | 前者守卫通过才PENDING再新attempt；后者stage PENDING/goal WAITING_USER且不紧循环 |

## 11. 实施与验证命令

开始按 AGENTS 和 [开发环境](../../project/development.md)核对 Node/pnpm 与所属状态；本轮写需求不代表下述新测试已经存在或通过。Docker脚本启动隔离测试实例，不部署服务、不调用真实Bark/模型。

现有回归命令：

```powershell
git diff --check
pnpm docs:check
./scripts/test-server-linux.ps1 -Tests 'ModelDelegationTaskPlannerTest,DelegationConversationIntakeTest,AgentDelegationTaskExecutorTest,DelegationPersistenceTest,DelegationControllerTest'
pnpm --filter @stackchan/console run test
pnpm --filter @stackchan/console run lint
pnpm --filter @stackchan/console run build
```

新增实现必须增加可执行 `CompanionGoalLifecycleTest`、`CompanionGoalPersistenceTest`、`CompanionGoalControllerTest`，分别验证状态/并发、真实PostgreSQL约束和身份/API；完成后执行：

```powershell
./scripts/test-server-linux.ps1 -Tests 'CompanionGoalLifecycleTest,CompanionGoalPersistenceTest,CompanionGoalControllerTest'
```

测试注入时钟、mock planner/transport和并发屏障；至少覆盖AC01–25，不读取运行库。当前前端 `lint` 脚本为 `vue-tsc -b`，build同时做类型检查与生产构建；ESLint/stylelint按工作流对实际改动文件执行，不把lint脚本名称误当成全部静态检查。

## 12. AI 开发完成清单

- [ ] 已核对新鲜主线、V57集成与R02成果接口，明确现有与增量。
- [ ] FR01–16全实现，目标/阶段枚举和转移保持公共契约。
- [ ] 五阶段/五行动与一至五标准由服务端硬校验。
- [ ] 旧delegations API、旧无goal记录、旧迁移checksum和历史保留。
- [ ] 完成与用户采用分开，标准UNKNOWN/FAIL不能complete。
- [ ] 修改/暂停/取消、跨角色、幂等、并发和晚到结果有真实持久验证。
- [ ] UI包含草案、缺条件、受阻、待阅、修订冲突、暂停与历史。
- [ ] 新数据/API均落实，R04/R05尚未共同验收前不启用goal-owned执行。
- [ ] AC01–25有实现与证据映射；真实采用待验范围明示，不伪造体验通过。
- [ ] 更新所属工作流和全局总览，交接精确下一操作及回退；遵守单中文任务提交和外推授权。
