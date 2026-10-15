# R05：行动权限、具体批准与有限执行预算

- 文档状态：ACTIVE；需求状态：SPECIFIED；实现状态：NOT_STARTED
- 版本：1.0；日期：2026-10-12；源码基准：`989b3d9`
- 原计划：GOAL-01；优先级：P1，任何新goal-owned执行启用前的硬门禁。
- 入口：[需求索引](README.md)；共同字段与状态：[公共契约](00-shared-contracts.md)。
- 前置：[R00](R00-baseline-and-integration.md)、[R02](R02-project-loop.md)、[R03](R03-goal-lifecycle.md)。
- 共同启用：[R04](R04-durable-execution.md)；工具真实性：[R10](R10-capability-and-evidence.md)；手机审批界面：[R09](R09-mobile-handoff.md)。

## 1. 做到什么程度

用户可以看懂伙伴为了完成目标准备读什么、做什么、结果给谁；只读研究可以在已允许范围内持续推进，外发必须有合法依据。改变目标、停止、撤销和额度耗尽会在实际工具开始前阻断动作，服务重启和并发不能绕过这些条件。

本项必须把规则实施在服务端守卫、数据库预算与实际工具入口，不能只写模型提示或在前端藏按钮。验收包括陈旧批准、正文变化、并发额度、午夜、角色变化、进程中断和现有Bark兼容。不是建设通用企业授权平台。

## 2. 当前代码起点

| 实际位置 | 当前已有 | 本项增量 |
| --- | --- | --- |
| `server/src/main/java/com/kj/stackchan/agent/AgentToolAssemblyService.java` | 按已启用Tool/Skill/MCP与上下文装配 | 加目标范围/效果类别交集，未知效果不能进自主路径 |
| `AgentToolPolicyInterceptor.java`（同包） | 真实工具审计、结果限制、executionGuard、Bark前置读取 | 统一具体批准与预算守卫，执行前后重查 |
| `AgentOrchestrator.java`（同包） | 当前明确请求、缺正文补充、能力咨询只读 | 保留口语授权证据，不强制重复确认 |
| `server/src/main/java/com/kj/stackchan/delegation/BarkNotificationService.java` | 独立SENDING持久标记、按turn/run去重、固定收件配置 | 批准绑定真实内容；新请求新turn，不清旧槽 |
| `DelegationService.java`（同包） | 任务revision、run/attempt、锁内守卫、有限worker | 目标和设备日预算；旧入口也守新目标限制 |
| `server/src/main/java/com/kj/stackchan/config/AppProperties.java` | Agent默认4工具/20秒；最大工具8、timeout1–60秒；结果8192/24576B | 保持内部更严上限，不因外层120秒改内部配置 |
| `apps/stackchan-console/src/views/settings/agent/index.vue` | 现有Agent和工具设置 | 展示实际有效限额、缺配置，不显示凭据 |

现有目录/明示Bark请求不是新的120秒审批资源；现有定时BARK交付也已有保存授权。新增机制不能说这些现有代码从未具有权限保护。阶段/目标预算与以下批准表在基准中尚不存在。

## 3. 行动分类与兼容

| 类别 | MVP资格 | 所需依据 |
| --- | --- | --- |
| GENERATE_LOCAL | 允许 | 已接受目标、当前阶段、有效预算；不需要重复批准写本地成果 |
| READ_PUBLIC | 允许 | 目标保存资料范围、真实只读工具、HTTPS策略和预算 |
| READ_PERSONAL | 仅现有已授权查询 | 当前设备/角色/账户原范围；不能因公开研究目标读全部私人应用 |
| WRITE_PERSONAL_LOCAL | 沿原业务确认入口 | 原个人待办/记忆确认规则；目标许可不自动扩展 |
| BARK_SEND | 允许具体外发 | 当前明确请求/合法原交付政策，或新增绑定实际内容的单次批准 |
| WRITE_EXTERNAL_OTHER / PAYMENT / PURCHASE | 不开放 | 返回缺能力/不在范围，给草稿或手动执行路径 |
| UNKNOWN_EFFECT | 不进入goal自主执行 | 需管理员可信分类和另立设计，不能采信模型自称只读 |

Skill是说明/工具编排，不是授权豁免；MCP描述声称只读也不是可信分类。普通文本生成、合法公开资料读取无需反复确认。网页/通知中“忽略限制”“点击即批准”只是数据，不能制造授权。

原legacy角色绑定委托、明确Bark口语请求和已授权定时交付保持原范围。切换聊天伙伴不会一律停掉legacy周期业务，不会把其数据转给新伙伴；归档继续原取消规则。新goal-owned动作、当次批准与普通候选按公共C02失效。

## 4. 编号功能规则

**R05-FR01 范围交集。** 实际允许工具是当前可信目录、角色权限、目标已保存范围、当次动作许可、预算的交集。交集来自服务器，模型/客户端不能指定一个更大的allowedToolNames列表。关闭工具后旧目标下一步立即受阻。

**R05-FR02 目标允许范围。** 保存来源模式为EXPLICIT_URLS或明确接受的有限来源范围；R02默认EXPLICIT_URLS。实际读取、重定向和链接跟进都检查范围与原公网策略。更换来源是目标修订，不能“研究需要”自动扩大。

**R05-FR03 分类。** 内置效果类别由代码目录定义；管理员审核的MCP分类仅在已授权工具版本有效，schema/服务器变更使旧分类待复核。UNKNOWN_EFFECT不被作为READ_PUBLIC。框架自动工具、read_skill同样计实际工具预算，不能成为隐藏调用路径。

**R05-FR04 可读预览。** 新外发预览明确当前目标/伙伴、渠道、固定收件配置的非秘密标签、标题、完整将发送正文、来源成果版本和允许次数。Bark只接title/body及受控成果链接，不接受模型提供收件地址、serverUrl或key。标题1–80 Unicode码点、正文1–2800 UTF-8字节沿既有边界；超长先生成待确认缩短版本，不能批准后再改。

**R05-FR05 单次批准。** 新批准服务端TTL120秒，绑定goalId、goalRevision、device/role、artifactId、actionDigest、contentDigest、一次用途及原用户接受依据。期限从预览创建计算，不因刷新/点击延长。动作开始前消费；未开始已到期不能发送，已开始外发仍记录真实回执，不能用到期假装已撤回。

**R05-FR06 自然授权复用。** 用户当前明确要求“把这份比较发到我手机”且对象/正文唯一时，可以用该话语构造同范围单次批准，避免再问“确认吗”；无法唯一绑定成果/正文就只问缺失一项。功能咨询、续讲、否定、历史已发送消息不能变成新外发依据。来源内容不能成为用户接受证据。

**R05-FR07 延后交付。** 新goal-owned外发若具体内容尚未形成，不创建假内容批准。默认成果在网页待阅；需要新增具体写授权时形成当前内容预览并等待用户。原任务已经保存的明确定时BARK交付政策只对该原任务和原范围保持兼容，不扩展为所有新阶段许可；新阶段不能借原授权发送任意信息。绑定/迁移不得静默改变旧已承诺交付，也不得自动增加收件渠道。

**R05-FR08 批准失效。** 正文/标题/收件配置/来源成果/目标revision变化，暂停/取消、角色切换/归档、撤销、到期使未消费批准INVALIDATED或EXPIRED。批准与业务状态事务保存。使用旧approvalId返回409/410并说明当前动作；不静默替换成新正文执行。

**R05-FR09 每步重查。** 领取、每次工具前、结果落盘/交付前都使用R04守卫。role/device来自已认证上下文；网页只具管理员管理权限，不因传deviceId变成设备JWT，不开放实体音频/动作。已开始的网络外发可能无法撤回，保存真实历史。

**R05-FR10 去重与未知。** 一次批准至多消费一次；HTTP键、批准消费、动作槽及运行关联共同防重。新goal-owned FAILED/UNKNOWN不自动外发重试；legacy原FAILED有限重试保留，UNKNOWN全路径不自动重发。明确用户核查后的新请求建立新动作/批准，不修改旧UNKNOWN，也不重新研究成果。

**R05-FR11 新发送上下文。** 同一个旧run的永久Bark operation key不能通过新attempt再次发送。`:request-new-delivery`验证保存成果后，服务器建立新已认证WEB/VOICE意图turnId并调用原Bark服务，绑定新actionId/approval与旧artifact；不调用旧run的retryDelivery绕开墓碑，不接受客户端自行生成turnId。

普通新增批准只允许当前有效成果所属goal为ACTIVE/WAITING_USER/REVIEW_READY。PAUSED/CANCELLED不新建动作；BLOCKED仅在绑定的具体旧投递FAILED/UNKNOWN经用户核查并明确新发送请求时允许这一恢复动作，不绕过其他预算/权限阻塞。人工新发送走独立当前用户请求守卫，不能要求已结束旧stage重新RUNNING。它不自动恢复后台阶段或完成goal。

对旧UNKNOWN，用户处置另存 `USER_CHECKED / USER_ACCEPTED_POSSIBLE_DUPLICATE / KEEP_BLOCKED` 等受控resolution事件；原投递仍UNKNOWN，不宣称已查到确定渠道事实。解除受阻必须有明确处理选择、当前规则/标准重新检查；新请求成功不是默认采用成果，最终完成仍由R03确认。

**R05-FR12 硬限额。** 新默认每目标最多5阶段、每阶段5planned actions/8实际tool calls，每run外层120秒；每goal当地日3run、设备当地日10run。内部Agent4/20默认及结果8192/24576B更严限制优先；不自动放宽现有配置。所有实际工具、失败和只读重试计数，缓存纯读历史不计执行run。

**R05-FR13 日run扣减。** worker开始执行时一次扣goal/设备run，两个计数同事务；重复同run/attempt不重复扣。未进入worker的预约释放；已开始模型/工具的运行即使失败也计数。2worker容量满不假排队、不消耗已未执行run额度。tool额度按真实调用开始扣，超时调用同样计入。

**R05-FR14 并发与窗口。** 预算以数据库唯一(device/localDate、goal/localDate)和原子比较扣减，不能用进程内AtomicInteger替代日全局约束。设备时区分桶、重启/删除/角色切换/更换时区不清消费；采用公共C02。多个目标共用设备10run，不能各算10。

**R05-FR15 耗尽。** BUDGET_EXHAUSTED保留成果、固定原因与下一行动，goal BLOCKED；工具不启动，不能切更贵模型、延长timeout或连用新goalId规避设备上限。午夜回补仅额度可用，用户resume后再检查，不自动补跑阻塞期间每个旧时刻。

**R05-FR16 配置范围。** V1只允许把默认外层限额调低：stageTools1–8、runSeconds1–120、goalRunsPerDay1–3、deviceRunsPerDay1–10；停用执行用显式开关，0不代表无限。设置增大至默认以内仍是明确管理员选择，不重新开始旧阻塞目标。高于默认需独立需求；阶段/行动硬上限不由模型配置。

**R05-FR17 金额。** 默认金额未知，页面显示调用/时长和UNKNOWN，不编造费用或把未知当零。用户可以保存来自实际供应商计价的非负日金额上限及币种、来源/更新时间；估算须标估算。设置金额上限却无法可靠计量/估算上界时阻塞新付费调用，不自行买额度；没有金额配置仍执行调用/时长硬限额。

**R05-FR18 撤销与恢复。** 撤销持续只读范围使相关goal受阻，未消费批准失效；批准拒绝不等于取消整个目标。用户可以仅改成网页交付或补资料，重新接受后按新revision继续。恢复必须重查目录、权限和额度，不把曾经批准过当永久许可。

## 5. 拟新增数据与状态

以下为新设计；实现按最新Flyway版本递增，不预占V61，不修改旧迁移。

| 数据 | 必需内容 |
| --- | --- |
| goal permission scope | goal/revision、分类、tool IDs、source范围、允许交付、原授权依据和撤销时刻 |
| approval | id、goal/revision、scope、artifact、action/content摘要、状态、expiresAt、consumedAt、acceptedEvidence引用 |
| execution budget | device/goal/localDate、有效时区、上限、预约/消费计数、resourceVersion |
| budget reservation | run/attempt唯一、开始/释放/结算事件、工具调用唯一槽、无私人参数 |
| cost snapshot | 币种、计价来源、更新时间、已知/未知/估算、供应商用量引用；不保存凭据 |

批准状态 `PENDING / APPROVED / CONSUMED / REJECTED / EXPIRED / INVALIDATED`；CONSUMED说明单次机会已用，不表示渠道SENT。未消费的PENDING/APPROVED才可拒绝/失效；消费后的真实回执由R04和原Bark记录，不能把它改成“未发生”。

预算预约→worker开始消费→工具前扣调用→结束结算必须幂等。批准消费与副作用去重准备先持久，再实际传输；处理R04指出的独立事务/FK锁顺序，不在长事务中形成自锁。每个状态变更保存固定原因，秘密/完整请求载荷不进日志。

## 6. 拟议 API 与响应

所有新增写有HTTP Idempotency-Key，键规范、收据回查与错误包装遵循公共C03/C10。网络未知先查统一operations，COMMITTED不是发送成功。

| 接口 | 输入与结果 |
| --- | --- |
| `GET /api/v1/companion/goals/{id}/permissions` | 当前范围、目录差集、有效限额、已消费数；不返回密钥 |
| `PUT /api/v1/companion/goals/{id}/permissions` | expectedRevision、完整允许范围、resourceVersion；合法变更产生新goalRevision并失效旧动作 |
| `POST /api/v1/companion/goals/{id}/approvals` | expectedRevision、服务器已保存actionId/artifactId；201返回完整预览、摘要、expiresAt/status |
| `POST /api/v1/companion/approvals/{id}:approve` | expectedRevision、resourceVersion、预览摘要；200返回APPROVED或当前真实消费结果，不接受任意正文替换 |
| `POST /api/v1/companion/approvals/{id}:reject` | expectedRevision、resourceVersion；200拒绝且停此动作，目标仍可只读继续 |
| `GET /api/v1/companion/goals/{id}/budget` | 当前当地日/时区、配置/内部更严实际值、预约/消费/余额、费用状态 |
| `PUT /api/v1/companion/budgets/settings?deviceId=UUID` | expectedRevision、上述下调字段/可选金额；200返回新设置/有效值，不启用暂停目标 |

401登录后先读预览；403动作无许可；404不属scope；409版本/内容/键冲突；410批准过期；422能力/计量不足；429预算/容量耗尽；503依赖暂不可用。审批完成并需发送时，服务端在合法守卫下创建单一执行意图，返回action/receipt查询引用，前端不另发第二次自由Bark调用。

## 7. 界面与设备文案

复用目标/成果页与FaModal/FaForm/FaFormItem/FaButton，不建立第二个通知设置。手机360px上下显示正文与主操作，完整预览不能隐藏在省略号；有可见焦点/标签，屏幕阅读错误关联字段。

| 场景 | 文案/操作 |
| --- | --- |
| 范围 | “允许读取：你提供的3个公开链接；结果保存在网页。没有购买或其他外部写权限。” |
| 具体发送 | “发送到已配置的手机”；完整标题/正文；“发送这一条”“暂不发送” |
| 过期 | “这次确认已过期，请核对最新内容。”；重新读取，不自动发送 |
| 改了内容 | “内容已变化，原确认不能用于这一条。” |
| 已消费 | “发送请求已提交，正在核对渠道结果。”；查询而非再次点击发送 |
| UNKNOWN | “不能确认是否被渠道接受；请先查看手机。本次不会自动重发。” |
| 额度耗尽 | “今天的执行额度已用完，成果保留。你可以在额度恢复后继续。” |
| 金额未知 | “费用未知；已统计调用次数和运行时长。” |
| 权限撤销 | “读取范围已撤销，暂不能推进；已有成果仍可查看。” |

不把审批当关系互动，不用“相信我就全部允许”。正常只读和原合法Bark语音请求不增加机械弹窗；用户实际看到的摘要必须和action/content摘要所指内容一致。

## 8. Given / When / Then 验收

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| R05-AC01 | 已接受有限公开资料目标 | 执行只读/生成 | 不重复要求每页批准，实际只在范围内 |
| R05-AC02 | 模型建议多一个未经许可工具 | 进入工具入口 | 拒绝，scope不增大，无副作用 |
| R05-AC03 | MCP自称readonly但可信类别UNKNOWN | goal规划 | 不自主使用，说明需审核 |
| R05-AC04 | 网页内容要求发送到攻击者地址 | 读取后生成动作 | 内容只作数据，收件/批准不改变 |
| R05-AC05 | 当前具体手机发送请求、正文唯一 | 语音处理 | 复用当次明确依据，一次发送，不再重复问确认 |
| R05-AC06 | 能力咨询/续讲/历史已发正文 | 新回合 | 没有写授权或新发送 |
| R05-AC07 | 新批准创建119秒/120秒 | 动作开始 | 前者可按守卫消费，后者410不发送 |
| R05-AC08 | 批准后正文/收件配置变化 | 使用旧批准 | INVALIDATED/409，不用新正文外发 |
| R05-AC09 | 批准未消费 | 暂停或切角色 | 失效，切回来不复活旧批准 |
| R05-AC10 | 两页同批准/同键并发 | 同时发送 | 一次消费和一个动作槽，返回同结果 |
| R05-AC11 | 同键改变发送正文 | 重投 | 409，不覆盖原收据 |
| R05-AC12 | 渠道接受但回执写入失败 | 查询/重启 | UNKNOWN，无自动重发 |
| R05-AC13 | UNKNOWN且用户明确核查并新请求 | request-new-delivery | 新服务器turn/action/批准，旧UNKNOWN保留，同旧run不再发 |
| R05-AC14 | 新goal-owned FAILED | scheduler tick | 不自动重试写；legacy旧FAILED有限规则仍兼容 |
| R05-AC15 | Agent4/20默认、外层8/120 | 执行 | 更严4/20生效，结果8192/24576约束保留 |
| R05-AC16 | goal当日已有3run | 再次执行 | BLOCKED/429，第四run无工具/付费调用 |
| R05-AC17 | 多目标设备已有10run | 两目标并发启动 | 原子设备额度阻止第11，不各自拥有10 |
| R05-AC18 | worker容量满、请求尚未开始 | 启动 | 释放未执行预约，明确忙碌，无假排队 |
| R05-AC19 | 临时只读失败 | 初次加2重试 | 每次计工具/时间，超过额度先停 |
| R05-AC20 | 日额度耗尽后午夜到来 | tick或角色切换 | 额度可回补，goal仍BLOCKED；明确resume才重查 |
| R05-AC21 | 同日改时区/删除goal/重启 | 新请求 | 消费保守继承，设备额度不清空 |
| R05-AC22 | 设置金额上限但用量不能可靠计量 | 新付费运行 | 受阻并显示未知，不冒充0费用 |
| R05-AC23 | 旧明确定时BARK任务 | 切聊天伙伴/升级 | 仅原角色/原范围交付，未借许可开放新阶段 |
| R05-AC24 | 网页仅传deviceId无会话 | 审批或预算写请求 | 401；无设备身份、语音或实体权限 |

## 9. 验证命令与 AI 交付

基线用已有工具/委托测试；新增批准/预算需真实隔离PostgreSQL、mock供应商、可控时钟和并发屏障验证，不能只mock service称原子扣减成功。

```powershell
./scripts/test-server-linux.ps1 -Tests 'AgentToolPolicyInterceptorTest,BarkNotificationToolTest,DelegationPersistenceTest,DelegationUpgradePersistenceTest'
./scripts/test-server-linux.ps1 -Tests 'CompanionApprovalPersistenceTest,CompanionBudgetPersistenceTest,CompanionPermissionControllerTest'
pnpm --filter @stackchan/console exec vitest run src/views/companion/goals/permissions.test.ts src/views/companion/goals/budget.test.ts
pnpm --filter @stackchan/console run build
git diff --check
pnpm docs:check
```

第二个服务端命令和goals前端测试是本项**必须新增**，不是已有测试/成绩。实测不使用真实Bark/付费模型；迁移覆盖空库和含V57–V60/旧回执的隔离升级。R04/R05共同验证后才开启新goal-owned运行。

AI交付FR01–18/AC01–24对照、真实权限守卫/批准/预算事务、迁移、上下文与永久槽兼容、界面/错误、测试报告和候选回退。逐条说明普通生成、已授权定时交付、具体新发送三者的授权依据；更新所属状态和总览，保存在一个中文任务提交。不自动外发、安装、刷写、切模式、推送或创建PR。
