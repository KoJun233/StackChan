# R04：有限目标的持久执行、中断恢复与防重复动作

- 文档状态：ACTIVE；需求状态：SPECIFIED；实现状态：NOT_STARTED。
- 版本：1.0；日期：2026-10-12；源码核对基准：`989b3d9`。
- 原计划映射：GOAL-01；建议阶段 W4–5；首个可恢复目标必须与权限守卫共同交付。
- 入口：[需求索引](README.md)；共同硬边界：[公共契约](00-shared-contracts.md)。
- 前置：[R00 基线](R00-baseline-and-integration.md)、[R02 成果](R02-project-loop.md)、[R03 目标](R03-goal-lifecycle.md)。
- 共同启用门禁：[R05 权限预算](R05-permissions-and-budgets.md)；证据规则：[R10 能力证据](R10-capability-and-evidence.md)。
- 本需求负责执行可靠性，不定义用户采用、已读或目标完成的新语义。

## 1. 完成到什么程度

一个目标在服务重启、断网、两次点击、计划修改或停止之后，仍能解释最后确认完成的行动、是否有未知副作用、下一步是否安全。只读失败可有限重试；已经或可能外发的通知不能为了“恢复成功”再发一次。自动化不能证明恰好一次外部投递，必须准确表达供应商回执边界。

MVP 是在已有任务、运行槽、修订、租约与两工作线程上增加目标绑定、行动记录和恢复分类。不能重新做一套并行 scheduler、队列、workflow engine 或长期 Agent 进程。

本项代码完成且 R05 守卫验收前，goal-owned 的自动执行入口保持关闭。R02 旧委托路径可以继续工作，不等待所有长期目标增强功能。

## 2. 基准中的真实能力

| 现有文件 | 真实行为 | 尚需增量 |
| --- | --- | --- |
| `server/src/main/java/com/kj/stackchan/delegation/DelegationService.java` | 2 worker、SynchronousQueue、无待执行队列；occurrence槽、attempt、revision；逐步guard | 目标/阶段守卫、行动持久检查点、120秒外层预算、恢复分类 |
| `server/src/main/java/com/kj/stackchan/delegation/DelegationScheduler.java` | 应用ready调用recover；每30秒tick | 同一个tick识别goal绑定，不增加第二个调度器 |
| `server/src/main/java/com/kj/stackchan/delegation/DelegationSchedule.java` | ONCE/DAILY/WEEKLY/INTERVAL和本地日期/时刻槽 | 沿用槽语义，增加stage唯一绑定 |
| `server/src/main/java/com/kj/stackchan/delegation/DelegationRepository.java` | PostgreSQL行锁、唯一occurrence、历史读取 | goal关联/行动记录/恢复判断查询 |
| `server/src/main/java/com/kj/stackchan/delegation/AgentDelegationTaskExecutor.java` | ReactAgent输入有界历史，禁止子任务，实际steps/evidence | 恢复输入只包含已验证检查点和当前修订 |
| `server/src/main/java/com/kj/stackchan/delegation/BarkNotificationService.java` | 外发前独立提交SENDING；相同run/turn去重；SENDING映射UNKNOWN | goal批准和永久副作用槽关联，保留现有发送实现 |
| `server/src/main/java/com/kj/stackchan/agent/AgentToolPolicyInterceptor.java` | 执行guard、结果字节上限、实际步骤及证据 | 工具前后持久行动标记与预算扣减 |

现有RUNNING租约为10分钟；应用启动把RUNNING记FAILED `DELEGATION_INTERRUPTED`，投递SENDING变UNKNOWN。现有普通成果自动交付的FAILED会被tick有限重试，最多3次；Bark Agent工具FAILED/UNKNOWN不自动重试。这两个路径必须区别，不能声称基准已经统一实现“不自动外发重试”。

新goal-owned任务必须被明确标记并排除旧FAILED自动写重试筛选；旧legacy非目标委托保留既有行为。必须有回归测试证明这个隔离，而不全局关闭或放宽legacy规则。[ADR0074](../../project/decisions/0074-natural-language-delegation-tasks.md)、[ADR0075](../../project/decisions/0075-bark-agent-notification-tool.md)继续适用。

## 3. 范围、标识与状态

- 每goal最多五阶段，每阶段最多五planned actions；同一目标同一时间一个RUNNING阶段。
- actionId是服务端规划行动标识；真实toolCallId由服务端绑定，不接受模型指定来制造新槽。
- stageId关联现有taskId；一次运行使用现有runId/attempt/occurrenceKey，并保存goalRevision快照。
- 不改变旧run枚举：`RUNNING / SUCCEEDED / NO_UPDATES / FAILED / CANCELLED / BLOCKED`。
- 不改变旧delivery枚举：`NONE / PENDING / SENDING / SENT / FAILED / UNKNOWN / SKIPPED`。
- stage枚举：`PENDING / RUNNING / SUCCEEDED / FAILED / BLOCKED / CANCELLED`。
- goal枚举：`DRAFT / ACTIVE / WAITING_USER / BLOCKED / REVIEW_READY / PAUSED / COMPLETED / CANCELLED`。
- SENT仅表示Bark接受；NONE/SKIPPED不是发送成功；UNKNOWN不能自动重发；投递状态不代替阶段成果状态。
- 最后阶段成功最多到REVIEW_READY；COMPLETED只依R03的明确采用与标准检查。
- 不做：任意工具快照序列化、恢复隐藏模型推理链、接管私人App、无限排队、自动对子目标分派。

## 4. 执行链路与编号功能要求

**R04-FR01 任务绑定。** 为每个当前stage创建/绑定一个受控委托任务，保留原Delegation创建/执行层。绑定task携带goalId、goalRevision、stageId和ownership标志；legacy无绑定不受新增目标状态过滤。旧用户任务不得静默转为goal-owned。

**R04-FR02 唯一领取。** 当前goal必须ACTIVE、revision匹配、stage前置通过、角色有效、范围和预算有效，才领取PENDING阶段与现有occurrence。stage领取、run槽、预算预约、执行快照同一事务；并发领取只能一个成功，其他返回同一run或明确忙碌。

**R04-FR03 工作容量。** 沿用2 worker/no queue。容量满时不另建内存队列；返回429 `EXECUTION_CAPACITY_BUSY`，保留下一行动且不假报运行。未真正进入worker的领取释放未消费预约，留审计原因；已有真实工具开始的尝试不得退还实际消费。

**R04-FR04 每步守卫。** 每个工具开始前检查goalRevision、task revision、run attempt、goal ACTIVE、stage RUNNING、角色、设备绑定、租约、预算和必要批准。任一失败停止该步骤，不能依赖prompt承诺。调用结果写入及交付前再次检查，旧快照不影响当前目标。

**R04-FR05 有界运行。** 新外层每stage最多8工具调用、每run120秒、每goal每日3次执行尝试、设备每日总10次；失败/重试计入相关额度。内部现有Agent默认4次/20秒、结果大小及更严设置优先，不把120秒改成现有Agent配置值。

**R04-FR06 行动检查点。** 工具开始前持久记录操作摘要指纹、类别、允许范围、授权与状态；完成后保存可验证回执和证据关联。仅程序实际收到结果才记录成功，模型说“已读/已发”无效。检查点不保存认证载荷或完整私人参数。

**R04-FR07 外部写前标记。** Bark仍复用既有独立持久外发标记；新增goal记录关联其receiptId。写请求开始后若进程退出，恢复将其判为UNKNOWN并goal/stage BLOCKED，不以数据库回滚推断“从未发送”。永久外部动作槽不能随24小时普通幂等键清理。

**R04-FR08 只读重试。** 可确认无副作用的暂时网络/依赖失败最多额外2次，总3次；重试前每次守卫、扣工具额度和检查时间。可用注入时钟的1秒/3秒退避，遇Retry-After仅在剩余预算内遵守，不延长run。身份、revision、SSRF、无能力或确定格式错误不自动重试。

**R04-FR09 失败写不自动重试。** goal-owned的Bark FAILED和UNKNOWN都不自动再发；FAILED显示明确未获接受确认，UNKNOWN显示需先查手机。人工重发必须另一个当前内容/批准事件，复用已保存成果，不重跑研究或生成，且不得复用已消费的写动作槽。

**R04-FR10 再发边界。** UNKNOWN不提供一键普通重试。用户可明确确认手机未收到并提出新发送请求；服务端记录“用户已核查仍可能重复”的依据并建立新动作ID、当前内容摘要和120秒单次授权。它是新请求，不篡改旧UNKNOWN为FAILED或SENT。

历史UNKNOWN的用户处置按R05单独保存resolution，旧确定性与回执不修改。原受阻是否解除要重查当前标准/许可，不能仅用新通知SENT自动complete；人工新请求不要求旧已结束stage重回RUNNING，也不借新turn自动恢复后台计划。

**R04-FR11 重启恢复。** 启动先让现有recover处理run租约，再对goal绑定按行动日志分类。没有工具开始、仅纯生成中断、确认只读中断可保存FAILED及可恢复下一行动；有任何未确定写副作用必须BLOCKED。不能看到旧RUNNING就自动重新执行全部阶段。

**R04-FR12 读取检查点复用。** 当前修订、参数摘要一致且符合R10新鲜度要求的成功只读结果可作为恢复输入；历史引用明确读取时间。已确定成功写步骤不重放；未知写步骤不能跳过去继续其他相关外发。不可复用的只读资料须重新读取且计预算。

**R04-FR13 无隐藏状态恢复。** 不序列化ReactAgent内部推理。恢复根据当前阶段、已验证行动、有限成果及缺条件重新产生一次有界执行输入；tool范围来自当前交集。无法形成合法下一行动时BLOCKED并解释，不从聊天文本猜恢复点。

**R04-FR14 修改、暂停和取消。** 事务保存新revision/终止状态并失效旧绑定、批准、运行，随后尽力中断worker。暂停后不得启动新工具或成果交付；已开始外部调用不能承诺撤回。晚到真实回执只写旧不可变行动历史，不使新revision成功或触发主动提示。

**R04-FR15 角色归档/删除。** 归档使未开始执行失效；删除按R00已验证的级联和隐私清理规则处理。正在执行且不能确定的外发仍需保留不含正文的去重墓碑；不能因业务行删除就允许同一个操作再发送。

**R04-FR16 NO_UPDATES。** 只有实际查询成功并无新内容时接受；不是空模型响应的成功回退。终结run为NO_UPDATES、stage回PENDING、goal WAITING_USER(reason=NO_NEW_DATA)，保存读取记录和“补资料/稍后再查”选择，不产生artifact、不推送、不完成阶段。只等用户明确继续后守卫领取，不遗留RUNNING或ACTIVE紧循环；legacy周期委托仍按原规则。

**R04-FR17 自动触发。** 仅使用现有scheduler tick与supported安排。已过期时刻遵循旧跳过漏期规则，不补跑过去每一天；用户暂停/取消/预算受阻期间的积压不在恢复后连发。下一阶段只能在当前goal/stage事件验证后被启用。

**R04-FR18 恢复说明。** 返回最近已确认行动、未确认行动、停止原因、可执行下一步和是否需要用户核查。技术诊断可查看关联run/attempt，产品文案不要求用户理解租约。恢复成功需真实状态变化，不只显示Toast。

## 5. 持久化与事务边界

以下均为拟新增设计，基准中无这些goal数据表。迁移实施时递增最新版本，保持原V1–V60 checksum与旧记录兼容。

| 拟增量结构 | 必需字段和约束 |
| --- | --- |
| goal/task绑定 | goal_id、goal_revision、stage_id、task_id、ownership=`GOAL`；同当前stage一条有效绑定 |
| goal/run绑定 | goal_id、goal_revision、stage_id、task_id、run_id、delegation_attempt、occurrence_key、claim_token、expires_at |
| 行动日志 | action_id、绑定scope、run/attempt、tool_name、READ/GENERATE/BARK_SEND类别、参数摘要、action_digest、content_digest、开始/结束、确定性、receipt_id、错误码 |
| 检查点证据 | 真实工具返回的evidence ID和读取时间，已验证的小量摘录或成果引用、字节大小、截断标志 |
| 副作用去重墓碑 | 非秘密操作键、目标修订/动作ID指纹、内容摘要、真实或UNKNOWN结果；不能自动删除后重放 |
| 执行功能开关 | goal-owned路径初始关闭，只有R04/R05共同验收可启用；不改变legacy已启用任务 |

行动日志的效果确定性字段建议 `NOT_STARTED / VERIFIED / UNKNOWN`，它不是新的goal/run/delivery状态。NOT_STARTED只在守卫拒绝且传输未调用时成立；传输调用后未确认不能再回NOT_STARTED。

锁顺序保持device→role→goal→task→run→预算/批准关联；涉及Bark现有锁序必须审查防死锁。不能在持有父行锁的事务内，用另一个REQUIRES_NEW事务插入需等待父行FK锁的记录，造成自锁；独立外发标记事务使用不依赖该长事务锁的已有写槽或在外层领取提交后写入，再执行最终守卫。

模型规划、等待退避和耗时网络不得占据无限长事务。MVP可继续使用现有有界guard网络调用锁来保证暂停顺序；每次调用必须严格超时，120秒外层到期会停止新动作。若重构成短事务fencing，必须证明与暂停/外发线性化关系，不能简单把旧锁去掉。

执行结果条件更新同时要求run RUNNING、attempt、task revision、goalRevision和claim_token匹配。过期worker不能提交新状态；执行容量释放、预算结算与终止事件必须可重复调用且幂等。

只读失败在同一run内重试消耗工具额度和运行时长；已终止run重新领取同槽的attempt递增时，消耗一次新的日执行额度。旧run快照的steps/output在重试前归档到不可变goal尝试历史，防止现有重试清空数组导致证据丢失。

## 6. 拟新增 API 与已有接口关系

现有`/api/v1/delegations/{id}:run`和历史读取保持。对goal-owned task，直接旧run入口也必须经过goal守卫，不能从旧页绕过预算/暂停/批准。所有新增写键为16–128 ASCII且至少保存24小时，同键同请求返回首次结果，同键异正文409。

| 拟新增接口 | 行为 |
| --- | --- |
| `POST /api/v1/companion/goals/{id}:run` | expectedRevision；领取合法下一阶段；202返回goal/stage/task/run/attempt和真实状态 |
| `GET /api/v1/companion/goals/{id}/executions` | 20条默认/100上限，有界历史、行动、来源、恢复说明 |
| `GET /api/v1/companion/goals/{id}/executions/{runId}` | 检查归属及绑定，返回运行/交付/行动确定性；跨范围不泄露 |
| `POST /api/v1/companion/goals/{id}:recover` | expectedRevision、用户选择；重查原因、只恢复合法只读或等待状态 |
| `POST /api/v1/companion/goals/{id}/executions/{runId}:request-new-delivery` | 明确的新外发请求及核查说明，关联保存成果；按R05创建新单次动作，不修改旧未知回执 |

不能提供接受客户端任意tool参数的“执行动作”API；客户端只操作服务器已保存的动作和scope。recover不是resume所有历史失败，也不是更改run status的管理员后门。

查询示例新增字段：`goalRevision`、`stageId`、`lastVerifiedAction`、`unknownActionIds`、`blockReason`、`nextAction`、`canRecoverReadOnly`。不得把UNKNOWN映射为SUCCEEDED或零错误码。

所有新增写复用公共C03统一operations收据；COMMITTED可只代表执行意图已保存，实际运行查上述executions。恢复FAILED/BLOCKED阶段须用户选择、原因解决且守卫通过，先PENDING再正常领取新attempt；CANCELLED阶段使用新修订快照，不修改旧尝试。artifactId沿R10唯一引用，不平行创建成果库。

## 7. 状态与恢复决定表

| 事件 | Run/Stage | Goal和下一行动 |
| --- | --- | --- |
| 所需只读/生成完成且证据检查通过 | run SUCCEEDED；stage SUCCEEDED | 有下一阶段则ACTIVE/WAITING_USER；最终REVIEW_READY |
| 暂时只读失败且还有额度/时间 | 当前run保持RUNNING，行动最多3次 | 不宣称成功，按有限退避继续 |
| 只读重试耗尽 | run FAILED；stage FAILED | BLOCKED并显示可恢复原因 |
| 缺工具/权限 | run BLOCKED；stage BLOCKED | BLOCKED或具体批准WAITING_USER |
| 运行/日额度耗尽 | run BLOCKED；stage BLOCKED | BLOCKED BUDGET_EXHAUSTED；不自动午夜恢复 |
| 已实际查询但无新资料 | run NO_UPDATES；stage PENDING | WAITING_USER NO_NEW_DATA，保存补资料/稍后再查选择，不自动紧循环 |
| 写回执UNKNOWN/重启发现写未确定 | run BLOCKED；stage BLOCKED | BLOCKED，先核查外部历史 |
| 写明确FAILED | run BLOCKED；stage BLOCKED | BLOCKED，只有明确新请求可新动作 |
| pause/cancel/revise | 未结束run/stage CANCELLED | 按R03真实状态，不交付旧成果 |
| 已确认Bark SENT后暂停 | 原回执保留SENT | 停后续，不宣称撤回/已读 |
| 只读中断后用户恢复 | 新attempt，原失败历史保留 | 守卫通过才ACTIVE，预算不足保持BLOCKED |

## 8. 界面与错误文字

- 正常运行：“正在核对你给的方案。已读取2个来源，还需要核对第3个。”数字只能来自真实记录。
- 读取失败：“方案二的页面暂时无法读取。已保留其他资料，可以稍后继续或换一个链接。”
- 进程重启只读中断：“上次工作中断，已确认的资料保留。你可以继续核对剩余部分。”
- 未知外发：“这条通知是否被Bark接受还不能确定。请先查看手机历史，本次不会自动重发。”
- 容量满：“伙伴正在处理其他任务，这次还没有开始。稍后可以继续。”不能假报排队。
- 修改中断：“你修改了条件，旧计划已停止。已开始的外部请求可能仍会返回。”
- 页中执行、成果、投递和用户采用分开显示，失败源单列；可查看历史但不给普通用户任意编辑状态。
- 409保留输入并读取当前revision；401登录后先查询；429不自动每秒重复点击；503只读遵守有限重试，写不重放。

## 9. Given / When / Then 验收

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| R04-AC01 | 同goal/revision/stage两端同时启动 | 并发claim | 一个有效run/领取，另一端同结果或忙碌 |
| R04-AC02 | 所有2 workers忙碌 | 新启动 | 无新队列、429、未执行额度预约释放 |
| R04-AC03 | ACTIVE阶段等待第二工具 | pause事务已提交 | 第二工具未开始，旧晚到成果不交付 |
| R04-AC04 | 工具中修改目标 | 旧response返回 | 只保留失效历史，不写新阶段/主动候选 |
| R04-AC05 | 已确认成功读取A，读取B中断 | 用户recover | 可用A检查点，仅合法有界重读B |
| R04-AC06 | 读取连续暂时失败 | 自动重试 | 初次+2次最多3次，每次计工具/时间预算 |
| R04-AC07 | URL违反公网策略 | 工具失败 | 无自动重试，不通过换地址绕过规则 |
| R04-AC08 | 内部Agent默认4/20，外层8/120 | 执行 | 4/20优先，不延长现有Agent配置 |
| R04-AC09 | 120秒预算已到且仍有计划行动 | 守卫 | 停新工具并保存BLOCKED，不等10分钟租约再做 |
| R04-AC10 | 外发前标记已提交，进程被终止 | 应用recover | UNKNOWN+BLOCKED，零自动重发 |
| R04-AC11 | Bark实际接受但回执落库失败 | 恢复/用户查询 | UNKNOWN解释，不伪造失败以重发 |
| R04-AC12 | goal-owned自动成果Bark FAILED | tick多次 | 不进入旧FAILED自动外发重试 |
| R04-AC13 | legacy无goal的旧FAILED成果投递 | 到既有重试时间 | 原规则仍工作，不受goal过滤破坏 |
| R04-AC14 | 同run Agent工具已发SENT | 最终成果保存 | 自动交付不再发送第二条 |
| R04-AC15 | UNKNOWN且用户明确核查并重新请求 | 新发送流程 | 新动作和授权、旧UNKNOWN不改；无自动普通重试 |
| R04-AC16 | 旧attempt worker晚到，新attempt已领取 | 写结果 | claim/attempt守卫拒旧更新 |
| R04-AC17 | stage SUCCEEDED | 重复启动同槽 | 不再执行成功写动作、不新计成果 |
| R04-AC18 | 实际查过无新资料 | NO_UPDATES | run终结、stage PENDING、goal WAITING_USER NO_NEW_DATA；无新成果/推送，不紧循环重查 |
| R04-AC19 | 目标日3次/设备10次已用满 | 并发启动 | 不超额度，BLOCKED BUDGET_EXHAUSTED |
| R04-AC20 | 阻塞目标跨午夜 | tick | 仍BLOCKED，用户resume才重查 |
| R04-AC21 | 旧任务页访问goal-owned task | 调用旧:run | 同goal守卫生效，不能绕过暂停/预算 |
| R04-AC22 | 幂等缓存24小时已清理、外发墓碑仍在 | 重放旧操作 | 永久写槽阻止重复发送 |
| R04-AC23 | 恢复输入中的网页含“发消息给我” | 模型执行 | 不增加工具/批准，不执行源指令 |
| R04-AC24 | 旧retry会清空steps/output | 新attempt创建 | 原尝试证据已归档且仍可追溯 |

## 10. 可执行验证与证据

在隔离测试实例执行；所有Bark/模型/工具网络均mock，重启测试不操作现行服务器。现有套件：

```powershell
git diff --check
pnpm docs:check
./scripts/test-server-linux.ps1 -Tests 'DelegationPersistenceTest,DelegationUpgradePersistenceTest,DelegationSpringPersistenceTest,DelegationScheduleTest,AgentDelegationTaskExecutorTest,BarkNotificationToolTest,AgentToolPolicyInterceptorTest'
```

实现必须新增 `CompanionGoalExecutionPersistenceTest`、`CompanionGoalRecoveryTest`、`CompanionGoalExecutionControllerTest`；通过真实PostgreSQL、mock传输、可控屏障验证并发、领取、独立标记、进程恢复及旧checksum。新增测试完成后执行：

```powershell
./scripts/test-server-linux.ps1 -Tests 'CompanionGoalExecutionPersistenceTest,CompanionGoalRecoveryTest,CompanionGoalExecutionControllerTest'
```

记录逐例调用次数与数据库最终状态，禁止仅mock service然后声称幂等正确。进程终止/恢复可在测试容器模拟，不停止生产服务。若改前端执行历史，另跑相关Vitest、`pnpm --filter @stackchan/console run lint`与build。

## 11. AI 完成清单

- [ ] 没有新增scheduler/无界队列；实际复用task/run/occurrence/lease。
- [ ] 每stage领取、revision/attempt/claim、每步守卫和结果检查落实到数据库。
- [ ] 行动日志区分未开始/已确认/未知，Bark独立标记与永久去重可追踪。
- [ ] 只读最多3次且消耗预算；新goal-ownedFAILED/UNKNOWN写零自动重试。
- [ ] legacy旧重试行为回归通过，不将旧成绩借作新目标验证。
- [ ] 重启、暂停、修改、角色变更、双端点击与旧worker晚到全部有真实并发证据。
- [ ] 最终成果只到REVIEW_READY，SENT不表示已读或完成。
- [ ] 新测试、旧最小回归、差异/文档检查通过，AC01–24逐条证据可查。
- [ ] R05共同验收前功能关闭，候选和回退步骤完整，未获外部授权不实际发送/部署。
- [ ] 更新工作流/总览/交接，用一个中文任务提交保存，不创建PR或擅自推送。
