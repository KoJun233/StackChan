# R02 首个真实项目闭环

- 文档状态：`ACTIVE`；需求状态：`SPECIFIED`；实现状态：`READY_FOR_REVIEW`（软件）；真实场景与视觉验收待验。
- 版本：1.1；日期：2026-10-15；代码事实基准：`master@a961816`（PR #49 已合入 R00/R01）。
- 计划映射：十二周计划 `LOOP-01`，W2–W3 首轮闭环；为 W4–W5 目标推进提供真实样例。
- 使用者：一位成人拥有者；现有管理员网页与经过认证的设备语音；伙伴范围保持独立。
- 实现入口：[需求索引](README.md)、[共享契约](00-shared-contracts.md)。
- 硬前置：[R00 基线与兼容整合](R00-baseline-and-integration.md)；语音体验与 [R01](R01-natural-voice.md) 并行。
- 后续扩展：[R03 目标生命周期](R03-goal-lifecycle.md)、[R04 可恢复执行](R04-durable-execution.md)、[R05 权限预算](R05-permissions-and-budgets.md)、[R10 证据](R10-capability-and-evidence.md)。
- 首轮不要求 R03、R04、R05 的完整实现；已有授权、运行保护、真实来源与有界执行必须有效。
- 本文不是功能已完成、刷写、部署、外推或真实购买的授权记录。

2026-10-15：用户确认合并后授权继续 R02 开发，交付与 FR/AC 结果见[本轮交接](../../project/baselines/companion-r02-project-loop.md)、[ADR0078](../../project/decisions/0078-versioned-readonly-project-loop.md)。原“V57 未合入”与“未开始”状态已 STALE，按 Git 纠正。下文保留规格；实现仅给新 ONCE 意图开启最小项目模式，周期业务保持旧运行语义。

## 1. 交付到什么程度

用户直接说出一个真实目标，伙伴接下后读取明确允许的材料，交付可检查的比较与推荐，解释缺失信息；用户修改一条偏好后得到修订成果，第二天可继续查看原结果，再明确认可、暂停或取消。
首轮必须通过语音或聊天自然语言入口走通，不能仅提供后台表单、定时资讯抓取或一段演示文本。
本需求把“做过哪些操作”“产出了什么”“用户是否认可”分开显示；模型生成完成不等于用户目标完成。
最小产品形态是现有持续委托加一张待阅成果卡、成果反馈与跨天结果查询，不新建一套独立 Agent 运行器。
用户可将下面的固定验收示例换成自己近期需要的真实只读研究目标，开发实现不得把桌面布置硬编码为任务类型。

## 2. 当前已有与实际缺口

| 当前事实 | 核对位置 | 本需求的增量 |
| --- | --- | --- |
| 持续委托支持自然语言、ONCE/DAILY/WEEKLY/INTERVAL | `server/src/main/java/com/kj/stackchan/delegation/ModelDelegationTaskPlanner.java` | 明确“现在做一次”的无定时委托入口，不无谓询问每天几点 |
| 语音/聊天任务摄入当前要求定时词；待补充内容存内存、十分钟过期 | `server/src/main/java/com/kj/stackchan/delegation/DelegationConversationIntake.java` | 区分明确一次委托、能力咨询、示例、闲聊；过期不继承旧授权 |
| 原任务更新增加 revision，并使旧执行/未投递结果失效 | `server/src/main/java/com/kj/stackchan/delegation/DelegationService.java` | 偏好修改复用此保护；反馈操作新增版本校验 |
| 已有运行、历史成果、工具 steps/evidence 与受控 HTTPS 读取 | `server/src/main/java/com/kj/stackchan/delegation/AgentDelegationTaskExecutor.java`、`PublicSourceReader.java` | 约定比较成果结构、缺失项与可复查的反馈 |
| 现有 API 返回 task/run，但 TaskSnapshot 未暴露 revision，RunSnapshot 未暴露 attempt | `server/src/main/java/com/kj/stackchan/api/DelegationController.java`、`DelegationService.java` | 新增兼容字段/反馈资源版本；不能假定旧接口已有乐观锁 |
| 已有持续委托列表与详情、五秒有界轮询、来源链接防护 | `apps/stackchan-console/src/views/companion/delegations/list.vue`、`detail.vue`、`helpers.ts` | 增加最小待阅卡和反馈，不重建页面框架 |
| 当前网页 API 用管理员会话；设备身份与网页身份不同 | `server/src/main/java/com/kj/stackchan/security/SecurityConfiguration.java` | 网页参数中的 deviceId/roleId 只作范围，不能赋予设备权限 |
| 最新 a961816 已合入 R00/R01，包含原 V57 与新增 V61；历史安装事实仍为 a37d220/V60 | `docs/project/status/overview.md`、`deployment.md` | 本轮从合并后的主线创建任务分支；不以历史安装记录声称新候选已安装 |

上轮计划见[产品迭代计划](../../project/companion-product-iteration-plan-2026-10-12.md)。
这些是源码与历史记录事实，本需求编写未访问运行数据库或重新确认设备在线。

## 3. 固定首轮验收项目

验收委托为：“帮我比较这 2–3 个公开 HTTPS 页面里的桌面布置方案。桌面空间有限，优先容易整理、线缆少。先给我比较和推荐，缺什么信息直接说，不帮我购买。”
验收前由用户或测试执行者提供实际可读的 2–3 个页面 URL；不得写入认证链接、带密钥查询参数或购买会话。
离线自动测试使用明确标记的假页面响应；它们不能算真实成果或十四天日用证据。
人工验收要求至少两个不同方案，实际读取用户给定来源，比较“占地、整理难度、线缆、可调整性”；来源未给的维度写“来源未提供”。
若页面属于同一方案的重复链接，伙伴应指出只有一个有效方案并请补第二个；不能假装成两个方案。
第一次反馈固定为：“我更在意线缆少，预算信息先不用猜，按这个重新比较。”
修订必须引用原成果，调整推荐依据，保留原成果与来源；不能生成另一个无关联的新任务。
第二天用户问“昨天桌面布置那个比较到哪了”，应返回原结果摘要、该次修改和下一项待确认信息。
最终用户可说“这份比较可以了，先到这里”；只代表比较成果被认可，不能说已选择方案、已下单或已完成布置。

## 4. 范围与非目标

MVP 包含一次明确委托、已有后台执行、最小成果审阅、一次偏好修订、跨天查询、暂停/取消、只读权限与最小幂等。
默认交付 `CONSOLE`；用户明确要手机通知且现有配置有效时才采用 `BARK`。
允许用户主动提供更多公开来源；不要求 RSS、工具名称、模型参数或固定资讯模板。
固定验收项目的读取范围默认仅为用户给定2–3个URL；从描述提取并经PublicHttpsUrlPolicy校验后保存规范来源，实际工具调用前按保存范围检查，不只靠prompt限制。
实际PublicSourceReader拒绝全部3xx，本轮保持拒绝并标来源失败；不声称已支持安全重定向。成功读取记录输入/实际来源对应，feed条目可作为该输入的证据但不自动抓取其链接；页面中的新链接不能自动扩大本任务范围，用户明确补充后才修订范围。
用户改为允许公开资料探索的真实目标时，可保存明确的只读探索范围并使用现有工具；不得把这个模式硬编码成RSS表单或声称内置全网搜索。
不包含自动购物、支付、桌面实景相机扫描、空间三维建模、抓取登录网站、通用搜索引擎、新私人连接器。
不包含全生命周期多阶段 DAG、任意自动续跑、完整目标预算编辑器、独立移动 App；这些按对应后续需求落地。
不要求机器人朗读全文；机器人应说 1–2 句真实摘要，再让用户在网页看来源和比较。
不为成果数量自动制造每日任务，不为“持续”反复生成同一总结。

## 5. 功能要求

| 编号 | 必须行为 | 不满足的判定 |
| --- | --- | --- |
| R02-FR01 | 明确“帮我比较/整理/写一份”且目标清楚时，支持现在一次的 ONCE 委托 | 必须补“每天/明天”等定时词才保存 |
| R02-FR02 | 自然入口由当前认证上下文绑定设备/伙伴；模型不能选择或替换范围 | 从模型 JSON 接受另一角色或任意设备 |
| R02-FR03 | 缺目标/关键资料时每次只问最关键的一项；说明“任务还未保存” | 模糊目标已自动执行、重复列长设置清单 |
| R02-FR04 | 询问“能不能比较”、假设/示例、拒绝与普通聊天不创建委托 | 一句能力咨询触发后台任务 |
| R02-FR05 | 保存后复述目标、资料范围、交付处及未获授权操作 | 用“我已经比较完了”代替接单 |
| R02-FR06 | 资料读取必须实际使用授权 read_public_source；保留实际 evidence | 靠模型记忆构造现实结论或引用链接 |
| R02-FR07 | 成果包含下面五个固定内容区，比较维度来源可追溯 | 只有一段泛泛建议或未标缺失项 |
| R02-FR08 | 资料不足时交付已获得的有限事实和阻塞解释，提供一个可执行选择 | 空白失败、编造规格或让用户自己做所有研究 |
| R02-FR09 | 首屏待阅卡显示任务、结果版本、摘要、范围、来源数量、待确认点 | 只有“成功”标签，看不出交付物 |
| R02-FR10 | 用户可“认可这份结果”“修改偏好”“暂停”“取消”；修改不是认可 | 任意页面访问或“谢谢”自动判目标完成 |
| R02-FR11 | 修改偏好作用于同一 taskId，旧运行失效、原成果保留、修订有因果关联 | 删除重建任务、静默覆盖原成果 |
| R02-FR12 | 跨天结果查询只读，无新任务/外发；明确多个候选时请用户选择 | 为回答进展重复执行或复用另一角色成果 |
| R02-FR13 | 运行、用户反馈、渠道投递三个状态分开；Bark SENT 只表示渠道接受 | 标注“手机已读”“用户认可”“已购买” |
| R02-FR14 | 语音长任务在现有后台执行，聊天仍能响应停止/进展查询 | 阻塞整条语音链直到研究结束 |
| R02-FR15 | 同一 Idempotency-Key 重复提交返回原任务/反馈；响应丢失时先查询再重试 | 新建两份委托、同成果外发两次 |
| R02-FR16 | 后续 R03 迁移保持 taskId/runId/来源/反馈历史可用 | 首轮体验依赖未来 R03 才能验收或迁移丢历史 |

明确“现在/先做一次”时，ONCE.onceAt取服务端保存时刻，并建立一次执行意图；不能只保存onceAt=now后等待原调度。
现有 `DelegationSchedule.next(ONCE, now)` 对非未来时刻返回null，因此创建即执行必须显式领取原运行槽；task、运行意图与receipt同事务，worker在提交后投递。
相同摄入键与ONCE occurrence只领取一次；容量拒绝/进程中断有明确FAILED与恢复选择，不能说已开始比较却没有run。
不要求完整R04重启自动续跑；中断后复用已有租约/失效保护，显示“执行中断，尚未完成”，由用户明确重试一次。

五个内容区为：`任务理解`、`方案比较`、`建议与理由`、`缺失信息与限制`、`下一步供你决定`。
方案比较按方案列出信息；事实引用真实来源，建议注明依据，用户偏好与推断分开。
推荐可给“在少线缆优先的前提下，方案 A 更合适”，不能写“已为你选定 A”。
成本、尺寸等易变化信息显示读取时间；未实际获得就留空并标明未知。
下一步最多三个选择，默认不执行购买、登录或新来源扩张。

## 6. 最小状态与兼容映射

原 Java `DelegationService.RunStatus` 与前端 `DelegationRunStatus` 保留 `RUNNING/SUCCEEDED/NO_UPDATES/FAILED/BLOCKED/CANCELLED`，不得添加目标状态来替换运行状态。
原 delivery 保留 `NONE/PENDING/SENDING/SENT/FAILED/UNKNOWN/SKIPPED`。
MVP 的成果反馈单独保存 `UNREVIEWED/ACCEPTED/REVISION_REQUESTED/SUPERSEDED`，不能把 `ACCEPTED` 写成 run.status。
首轮任务“已暂停”由原 enabled=false 加反馈原因投影；“已取消”必须额外记录明确取消原因，不能仅依据 enabled=false 猜测。
标记CANCEL后的同一MVP任务不允许通过原 :resume/:run 绕过取消记录；需用户明确创建新意图，新任务可以引用旧成果。
在 R03 实现后，用户等待、阻塞、成果待阅等投影迁移到公共 goal 状态；`COMPLETED` 只来自明确认可且约定完成标准满足。
只认可一次阶段比较不能完成尚有其他约定阶段的完整目标；R02 ACCEPT_RESULT 先保存该run的成果认可，R03判断整体标准。
待阅卡不是新的执行器；运行成功但待阅时显示“比较已生成，等你看看”。
读取一个关键来源失败而完整比较不成立时保持 `BLOCKED`，显示已有局部事实；不能标整项成功。
用户明确接受“先按读到的两份做有限比较”后才允许按该缩小范围形成新的成功成果。
来源正常但缺少部分尺寸/价格时可形成成功比较，前提是缺失项显式展示且推荐不依赖虚构值。
暂停/取消后晚到的模型或渠道结果不得覆盖当前状态，不自动恢复执行。

## 7. 数据与拟议接口

以下是开发契约，不是现有已实现接口；最终字段须与共享契约一致，旧 API 保持兼容。
新增反馈旁表最少包含 `id/task_id/run_id/device_id/role_id/kind/comment/expected_revision/idempotency_key/created_at`。
`kind` 为 `ACCEPT_RESULT/REVISE_PREFERENCES/PAUSE/CANCEL`；`comment` 0–2000 Unicode 字符，修改偏好必须非空。
新增成果审阅投影保存 `run_id/review_state/resource_version/supersedes_run_id/acknowledged_at`；同一 run 单一当前投影，初始 resourceVersion=1。
TaskSnapshot 兼容增加只读 `revision`；RunSnapshot 兼容增加只读 `taskRevision/attempt`，旧字段及含义保持。
HTTP `Idempotency-Key` 为客户端生成 UUID；相同 scope + action + key + 相同正文重复返回原结果，不重新规划/执行；语音摄入使用稳定 turnId 推导同一意图的键。
服务端接受共享契约规定的16–128字符ASCII键，保存至少24小时；已发生外发的动作去重不随普通键清理而消失。
R02首次落地统一receipt服务：`GET /api/v1/companion/operations/{key}?action={受控操作}&resourceId={UUID}`；创建时resourceId可省，由已认证scope+action绑定。
状态为 `PENDING/COMMITTED/FAILED/NOT_FOUND`，响应仅key/action/status/resourceIds/revision/受控errorCode/createdAt/updatedAt；无私人正文。
本项注册的受控action为 `DELEGATION_CREATE/DELEGATION_FEEDBACK/DELEGATION_RUN_REVISION`，客户端提交时保留对应action与键用于回查；不接受任意工具名作action。
任务/反馈mutation与COMMITTED收据同事务；PENDING只表示可追踪意图已持久化，不证明业务任务已保存；FAILED同键返回原失败，不重复执行。
NOT_FOUND仅表示当前认证scope没有收据，不揭示其他scope资源；若需重新执行，先核对revision/永久副作用槽，再由新意图生成新键。
相同 Idempotency-Key 不同正文返回 `409 IDEMPOTENCY_CONFLICT`；不把完整正文写入工程日志。
反馈必须携带被审阅的 runId、`expectedRevision`（对应 task.revision）、`resourceVersion`（仅反馈投影版本）；不允许“认可最新结果”在后台猜版本。
`GET /api/v1/delegations/{taskId}/project-summary`：返回 task、latestRun、review、下一条需用户决定的事项；纯读。
`POST /api/v1/delegation-runs/{runId}/feedback`：正文包含 kind/comment/expectedRevision/resourceVersion/runMode，HTTP 含 Idempotency-Key；返回反馈、最新投影及可空newRunId。
`runMode` 仅 REVISE_PREFERENCES 使用，为 `SAVE_ONLY/SAVE_AND_RUN`，默认 SAVE_ONLY；其他kind传值时400，防止认可顺便触发执行。
`REVISE_PREFERENCES` 复用原任务更新保护；界面明确提供“保存并重新比较”与“只保存，稍后执行”。
选择重新比较时，服务端在同一受控操作中创建与原成果关联的新执行意图；不能通过重新启用周期安排制造重复运行。
同一天旧 occurrenceKey 已成功时，修订必须显式产生新的“修订 occurrence”，不能错误复用旧成功结果或清空旧 run。
修订 occurrenceKey 最少含 taskRevision 与 Idempotency-Key；其来源与新 runId/旧 runId 的关联持久化。
新增 `POST /api/v1/delegations/{taskId}:run-revision`，JSON 携带 expectedRevision、HTTP 含 Idempotency-Key；用于用户稍后明确执行已保存修订，不改变旧 :run 语义。
SAVE_AND_RUN 反馈事务与 :run-revision 共用同一运行意图服务；成功反馈不要求前端再调用第二个写接口。
同一偏好修订至多一条有效revision-run意图；不同标签页换新键也不能让同revision的比较并发重复，新意图需要新的明确修订或合法失败重试。
SAVE_ONLY后的手动“继续比较”既不重新生成旧反馈，也不复用已成功旧occurrence；服务端从当前任务revision构造该修订运行槽。
服务端负责范围校验、来源 URL 限制、长度限制与错误分类，模型只建议文本与结构。
所有新表迁移编号在 R00 兼容整合后以真实最高版本顺延；不得占用或改写 V57。
后续 goal 表通过关联字段引用原 taskId，反馈保留原 runId；旧深链和原 API 仍能找到成果。
goal-owned绑定出现后，原 :update/:pause/:resume/:run/:run-revision 与本反馈入口均转交R03/R04/R05同一守卫，不能直接修改阶段绑定任务绕过目标暂停、取消或批准。
legacy任务expectedRevision校验task.revision；goal-owned投影额外返回goalId/goalRevision，修改反馈须同时携带所见goalRevision并验证绑定仍有效，实际目标更新由R03统一增加版本。

## 8. 权限、异常与并发

网页读取/反馈沿用管理员会话，写入有 CSRF；401 进入现有会话过期流程，不能切换到 deviceId 免认证入口。
设备语音操作从已验证设备 JWT/会话与当前 role 解析 scope，不能借网页传来的 UUID 获得设备身份。
角色切换后不显示上个伙伴卡片；请求晚到必须按 requestId、taskId、当前 role 检查后丢弃。
角色归档禁止新任务/修订执行，历史成果可按已有管理员管理边界查看，不能自动跨角色接续。
任务设备/角色不可变；需换设备/伙伴时明确新建并重新确认，不能原地挪动私人任务。
反馈事务锁定对应任务与当前审阅版本；两个页面并发操作仅首个合法版本成功，另一个返回 `409 REVISION_CONFLICT`。
反馈200响应包含 feedbackId/taskId/runId/revision/resourceVersion/reviewState/newRunId_nullable；修订执行意图已建立但仍运行中不写“已比较”。
400字段错误、401会话失效、403无动作授权、404资源/归属不符、409版本/键冲突、422缺能力/证据、429容量不足、503依赖不可用使用共享错误包装。
不同用户意图不能仅因相同 URL 去重；幂等只对同一 Idempotency-Key/认证范围成立。
现有后台两工作者容量与十分钟旧执行过期是代码边界；本需求不直接加大容量、超时或绕过预算。
容量不足显示“当前有任务在执行，稍后再试”，保留委托；不隐藏任务或自动无限排队。
来源读取失败/越过 HTTPS 策略显示受控失败码及哪份资料未读，拒绝提示注入、跳转私网与认证载荷。
断网或 5xx 保留最后已加载成果并标“当前连接中断”；写操作状态不明时查回反馈 Idempotency-Key，不直接重发。
尚未保存的补充问答十分钟过期后提示“上一项补充已过期，请重新交代目标”；不继续旧发送授权。
用户在草稿补充过程中明确取消该草稿只取消草稿；明确“取消这个比较/取消当前任务”且对象唯一时才取消任务，多项可能时先明确对象。
单独“取消/停止”与触摸停止优先按R01取消当前采音/生成/播放，不推断为长期任务取消；没有前台回合或明确任务对象时问一次范围。
取消成功立即禁用后续执行，并使未投递成果失效；外发 UNKNOWN 不自动补偿重发。
任务物理删除时反馈正文、审阅投影和普通候选按同scope关联清理；只保留不含私人正文的必要操作/外发去重墓碑，不能借删除原任务重放旧键。
错误反馈不改变原成果内容；失败原因与修订意见分开保存，禁止模型伪造用户认可。

## 9. 页面、文案与具身接点

复用持续委托列表/详情及 `FaPageHeader/FaPageMain/FaCard/FaTag/FaAlert/FaButton/FaCollapsible/FaForm/FaFormItem/FaFixedBar`。
列表首屏置“等你查看”的最多三张卡，再显示原任务列表；不新增第二个顶级菜单。
详情第一块为任务标题、当前伙伴、最后成果时间与“比较已生成，等你看看”；第二块为摘要和来源数量；第三块为全文/过程。
反馈位于成果下方：主按钮“这份结果可以了”，次按钮“修改偏好”，更多菜单“暂停任务/取消任务”。
“修改偏好”打开框架对话框，显示正在修改的版本与单一文本输入，说明“只改变这项任务，不自动记成长期偏好”。
取消确认写“取消后不再执行，已有成果仍可查看”；物理删除沿用原管理动作，不能与取消混为一谈。
加载显示框架 skeleton/loading，空态“还没有成果，接下任务后会出现在这里”，失败态带受控说明与重试。
陈旧成果卡显示“来自旧偏好”；允许查看，不允许未经重新确认将它作为当前推荐。
键盘可到达卡片、来源、反馈按钮；按钮有可见焦点，状态不只用颜色；新结果用 polite live region 提示。
在 360px 宽度用单列卡片、比较维度按方案展开；在 1280px 可用宽表或双栏，不能造成页面横向溢出。
现有表情/语音先复用采音、真实协议 PROCESSING、播放与错误状态，不增加必填 THINKING 枚举；不要求 [R08](R08-embodied-expression.md) 完整完成才验收。
待阅只做网页卡片及既有可用状态表达，不新增长时间舵机运动、焦虑动画或反复语音催看。
机器人收单：“我先比较这些公开资料，结果放在持续委托里；没有资料的地方会明确写出来。”
机器人交付：“比较做好了，按少线缆优先给了建议。还有一项尺寸信息没提供，你可以在网页看依据。”
机器人查询：“昨天的比较保留着；你后来把少线缆放在第一位，最新一版已经按这个调整。”

## 10. GWT 验收场景

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| R02-AC01 | 两个给定公开 URL，清楚比较目标，已有读取授权 | 说“现在帮我比较” | 不追问周期，保存 ONCE，只读执行并交付五区成果 |
| R02-AC02 | 当前没有委托 | 问“你能比较桌面方案吗” | 只回答实际能力，不创建任务或读取页面 |
| R02-AC03 | 当前没有委托 | 说“例如帮我比较这两个方案” | 识别示例，不作为真实委托 |
| R02-AC04 | 目标清楚但只有一个方案 | 发起比较 | 明确补第二方案，不能输出虚假两方案比较 |
| R02-AC05 | 三个来源之一读取失败 | 执行 | 显示局部事实和失败来源，完整任务 BLOCKED，不伪造引用 |
| R02-AC06 | 两个来源可读但都无尺寸 | 生成比较 | 标尺寸未提供，推荐依据只用已知信息 |
| R02-AC07 | 首版已经保存 | 用户提高少线缆优先级并重新比较 | 同 taskId，新 revision 与新 run，旧版可查看，理由确实变化 |
| R02-AC08 | 首版已认可 | 模型自行生成一条“用户很满意” | 不增加认可记录、不把新版本自动认可 |
| R02-AC09 | 最新结果待阅 | 用户明确“这份比较可以了” | 保存明确反馈，不宣称购买或完成实体布置 |
| R02-AC10 | 两个网页持同 resourceVersion | 同时认可和修改 | 只首个合法提交生效，另一方409并刷新，无静默覆盖 |
| R02-AC11 | 保存响应丢失 | 以同 Idempotency-Key 重试 | 返回原 task/feedback，不新增任务或再次外发 |
| R02-AC12 | 页面切到另一伙伴后旧请求返回 | 请求完成 | 丢弃旧伙伴数据，不闪现旧成果 |
| R02-AC13 | 执行期间修改偏好 | 旧模型结果晚到 | 旧结果不得作为当前成果或触发外发 |
| R02-AC14 | 昨天两版成果均保留 | 今日问“昨天那个到哪了” | 只读回最新版本、修改和待确认点，不重复执行 |
| R02-AC15 | 两个同名任务均可能指代 | 查询“那个比较” | 请选择任务，不猜错并执行 |
| R02-AC16 | 草稿补充超过十分钟 | 只回复“发手机” | 不继承过期目标或发送授权，提示重新交代 |
| R02-AC17 | 当前执行已取消 | 工具/模型稍后返回 | 不恢复执行、无新外发，历史显示取消原因 |
| R02-AC18 | Bark 返回 UNKNOWN | 查看成果 | 结果与投递分开，不显示已读、不自动重发 |
| R02-AC19 | 网页未登录，URL 带 deviceId | 读取或反馈 | 401；UUID 不赋予设备权限 |
| R02-AC20 | R03 迁移已执行 | 打开首轮 taskId/runId 深链 | 原成果、来源和反馈仍可查，ID 未变化 |

## 11. 验证与 AI 交付定义

实现前先完成 R00 的主线/未合入/已安装来源核验，记录软件基线；本轮文档不声称功能测试已通过。
服务端至少覆盖真实入口判定、run修订去重、反馈事务、晚到结果失效、角色隔离、URL证据和渠道 UNKNOWN。
新增反馈迁移必须在空 PostgreSQL 及含原 V57–V60 数据的隔离恢复库验证；不接触真实运行库做开发测试。
控制台覆盖待阅卡、陈旧版本、反馈冲突、会话过期、角色切换和键盘操作，避免只测试按钮存在。
以下命令从任务仓库根执行；新增测试类需由实现者实际创建，报告中写真实通过数：

```powershell
./scripts/test-server-linux.ps1 -Tests 'DelegationConversationIntakeTest,DelegationControllerTest,AgentDelegationTaskExecutorTest,DelegationSpringPersistenceTest,ProjectLoopFeedbackTest'
pnpm --filter @stackchan/console exec vitest run src/views/companion/delegations/list.test.ts src/views/companion/delegations/detail.test.ts src/api/modules/delegations.test.ts
pnpm --filter @stackchan/console run build
git diff --check
pnpm docs:check
```

`ProjectLoopFeedbackTest` 是本需求拟新增测试，不属于当前基线；如名称调整，文档同步列实际命令。
按实际改动执行框架 ESLint/stylelint 与类型检查；环境缺 Node/pnpm 时遵循[开发环境引导](../../project/development.md#node-与-pnpm-引导)，不改 engine。
人工证据至少记录一项真实委托的收单、真实来源、可用成果、偏好修订、隔日查询与暂停/认可；私人成果留本地。
自动模拟结果、网页截图、实体体验与部署验证分别报告，不能互相代替。
AI 交付必须含实现、兼容迁移、相关测试、手机/桌面截图、受控错误表、run关联示例和状态交接；实现与交接合成一个中文任务提交。
完成判定为 R02-AC01–20 全部有结果且无越权、伪造、重复外发、历史丢失；真实场景未试用时标“软件完成，人工待验”。
部署、刷写与外部推送需单独既有明确授权；本需求文档不能作为这些动作的授权。
