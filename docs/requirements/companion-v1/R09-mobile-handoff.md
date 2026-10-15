# R09 手机与机器人之间的成果接续

- 文档状态：`ACTIVE`；需求状态：`SPECIFIED`；实现状态：`NOT_STARTED`。
- 日期：2026-10-12；版本：1.0；代码事实基准：`master@989b3d9`。
- 计划映射：`HANDOFF-01`，W8–W9；首轮 [R02](R02-project-loop.md) 的最小待阅卡先交付。
- 硬前置：[R00](R00-baseline-and-integration.md)、R02 的任务/反馈接口；长期目标页接续需 [R03](R03-goal-lifecycle.md)。
- 接点：[R06 上下文](R06-memory-continuity.md)、[R07 注意力](R07-proactive-attention.md)、[R10 证据](R10-capability-and-evidence.md)。
- 共同规则：[需求索引](README.md)、[共享契约](00-shared-contracts.md)。
- 对象：同一成人拥有者在设备语音、桌面浏览器、手机浏览器之间查看和纠正同一成果。
- 本文不授权开放公网、改变部署模式、发送真实通知、刷写固件或外部推送。

## 1. 要交付的完整体验

机器人接下真实项目后，把完整成果保存到现有网页。
用户可以从现有菜单或一条不含认证载荷的成果链接在手机打开，登录后直接查看指定版本的成果。
用户在手机修改偏好、认可、暂停后，桌面页与后续机器人进展查询看到同一服务端状态。
“回到机器人继续”只保存一条有界接续意图，下一次用户主动唤醒时由当前设备/伙伴上下文读取；它不远程强制开口。
打开页面、看到通知、点击来源分别是不同动作，不自动等同于认可、购买或目标完成。
手机采用现有登录的响应式网页；不建立原生 iOS/Android App、新账号体系或手机双向聊天 SDK。
在 LAN 模式，手机离开受信任局域网后可能无法访问；应真实说明，不自动建设公网入口。

## 2. 当前能力与边界

| 已有事实 | 准确位置 | 需实现的增量 |
| --- | --- | --- |
| 已有委托列表、详情和历史 run 展示 | `apps/stackchan-console/src/views/companion/delegations/list.vue`、`detail.vue` | 手机布局、指定成果深链、高亮指定版本、反馈入口 |
| 已有详情路由 `/companion/delegations/detail/:id?` | `apps/stackchan-console/src/router/modules/companion.ts` | 保持旧路由，添加受控 `run` 查询参数 |
| apiJson 同源 credentials、X-XSRF-TOKEN、401 过期事件 | `apps/stackchan-console/src/api/client.ts` | 登录后恢复白名单目标、并发/陈旧与反馈冲突 |
| 任务 API 与投递 API 限 ADMIN | `server/src/main/java/com/kj/stackchan/security/SecurityConfiguration.java` | 新接续接口显式 ADMIN；设备端走已有认证上下文 |
| Bark send 当前参数为 serverUrl/deviceKey/title/body，payload 尚无成果 url 字段 | `server/src/main/java/com/kj/stackchan/delegation/BarkPushClient.java` | 可选可验证成果链接，不假定现有 payload 已支持 |
| Bark SENT 仅渠道接受，FAILED/UNKNOWN 分开 | `BarkPushClient.java`、`BarkNotificationService.java` | 页面文案保持，UNKNOWN 不自动重发 |
| 现有历史成果由任务/伙伴范围检索 | `server/src/main/java/com/kj/stackchan/agent/DelegationResultsTool.java` | 用户手机反馈后查询读取同一最新结果 |
| 当前详情有有界五秒轮询及请求编号保护 | `apps/stackchan-console/src/views/companion/delegations/detail.vue` | 复用有界刷新，页面隐藏/切换时停止，回来重新读取 |
| 当前生产 HTTPS 与 LAN HTTP 边界已有 runbook | `docs/runbooks/secure-deployment.md`、`docs/project/runbooks/recurring-research-and-bark.md` | 保持模式，深链来源白名单与失败说明 |

[上轮计划](../../project/companion-product-iteration-plan-2026-10-12.md)中的“Bark 单向推送不是双向伙伴”在本需求仍成立。
本需求编写没有检查当前手机接收、会话或运行实例；历史 V57–V60 来源按 R00 核对。

## 3. MVP 与不做范围

必须交付手机成果阅读、来源查看、R02反馈、明确接续、桌面刷新、可解释状态、登录返回与无 token 深链。
支持既有单管理员、多个独立伙伴容器；不改变伙伴隔离。
先针对同一已绑定设备接续；跨设备转移任务/音频控制、远程唤醒与后台持续录音不纳入本期。
不从 Bark 的消息回复或点击确认审批，不新增 Bark 回调消费或“手机已读”伪状态。
不将个人结果公开分享，成果 URL 只作导航，访问仍需登录和服务端授权。
不把管理员网页中的 deviceId 参数当作设备登录或设备权限。
不自动切 HTTPS/LAN、配域名、开端口、增加隧道、安装通知 App 或轮换凭据。
不增加大型“今日”“目标”“成果”三套重复入口；首轮先沿用持续委托，R03 完成后再兼容目标入口。
不因实现网页接续自动合入/安装未审核固件。

## 4. 用户路径

路径 A：语音发起 → 服务端产生成果 → 用户在手机打开持续委托 → 查看指定 run → 纠正一项偏好 → 下次问机器人进展。
路径 B：已有明确 Bark 交付授权 → 渠道返回 SENT/UNKNOWN → 用户主动打开成果链接 → 登录 → 查看原版本；不自动认可。
路径 C：桌面查看成果 → 点击“复制成果链接” → 手机浏览器打开 → 登录恢复原位置；链接中没有正文或认证信息。
路径 D：手机点击“下次和机器人继续” → 服务端保存短时接续 → 下次当前伙伴语音唤醒 → 一句短提示并接续；用户可拒绝。
路径 E：手机正在反馈 → 桌面已改变目标 → 手机收到409 → 展示最新版本和未提交文字 → 用户重新确认；不强行覆盖。

## 5. 编号功能要求

| 编号 | 要求 | 验收含义 |
| --- | --- | --- |
| R09-FR01 | 已有成果 URL 在手机登录后打开原 task/run | 无需重新从列表找任务 |
| R09-FR02 | 已删除/不存在 run 有明确空态；不会退到另一份成果冒充原链接 | 显示“这份成果已不存在”，可返回原任务 |
| R09-FR03 | 被指定旧版本保持可读，标旧偏好且可跳最新 | 不把“最新”内容套在旧 runId 上 |
| R09-FR04 | 手机阅读完整 output/evidence，长比较可展开，来源链接安全打开 | 完整信息不能仅在桌面可见 |
| R09-FR05 | 手机反馈携带 expectedRevision/resourceVersion/Idempotency-Key | 与 R02 同一服务端状态和并发保护 |
| R09-FR06 | 登录返回只允许本站受控成果路径，不能接受外站重定向 | 原 UUID 导航可恢复，不把 token 放 URL |
| R09-FR07 | 每个视图显示当前伙伴与设备标签；上下文冲突先解释再切换 | 不静默以当前伙伴解释另一角色成果 |
| R09-FR08 | 新反馈后桌面页及下一语音查询读取最新状态 | 不依赖手机本地缓存作为事实来源 |
| R09-FR09 | 用户明确要求接续才创建 handoff；只提示下一主动唤醒 | 页面打开不自动远程说话 |
| R09-FR10 | handoff 绑定设备/伙伴/任务/成果版本，有过期、取消、消费防重 | 不永久保留旧授权或跨角色泄漏 |
| R09-FR11 | 没有配置可访问站点来源时仍可在菜单查看，Bark 不发虚假链接 | 不把当前局域网地址假称外网可用 |
| R09-FR12 | Bark 链接为导航，SENT 只代表渠道接受 | 不回传审批、已读或完成状态 |
| R09-FR13 | 360px 与 1280px 均可阅读和操作，软键盘不遮保存/取消 | 完整截图与键盘人工检查 |
| R09-FR14 | 断网、超时、401、403、409、陈旧结果分开表现 | 用户知道没保存、待查还是已被更新 |
| R09-FR15 | 页面隐藏/离开停止刷新；重新进入验证 scope 和版本 | 不后台无限轮询或呈现旧伙伴闪屏 |
| R09-FR16 | 支持明确撤销尚未消费的 handoff；取消目标同步失效 | 不取消了仍提示继续 |
| R09-FR17 | 新接口沿用管理员会话与CSRF；设备消费从受验证上下文取 scope | URL 中 UUID 不成为凭据 |
| R09-FR18 | “标记已看”产生独立阅读事件，不改变目标revision/认可/交付 | 打开、预取、通知点击不自动已读 |

## 6. 深链与通知契约

稳定 MVP 路径：`/companion/delegations/detail/{taskId}?run={runId}`；taskId/runId 只接受 UUID。
同一 task 的 run 必须由服务端核对归属，run 属于其他任务时返回 404，不能仅靠前端筛选。
R03 完成后可另有目标路由，但必须保留上述旧路径，通过 task↔goal 映射到同一成果。
允许的 URL 数据只有本站路径、UUID、受控标签名；禁止 token、cookie、API key、Bark key、正文、私有偏好、授权码。
复制链接先生成规范 path，绝不复制整段当前任意 query/hash；链接不是公共分享权限。
登录返回参数若使用 `returnTo`，必须单次解码后校验以 `/companion/` 开头且命中允许成果路由。
拒绝 `//外站`、外部 scheme、反斜杠绕过、控制字符、重复编码、含认证参数或超过 512 字符的返回值。
返回路径可用 sessionStorage 暂存纯导航参数，登录成功后清除；不得将私人草稿或凭据作为返回参数。
通知站点来源配置拟新增 `consoleBaseUrl`，只保存 origin/允许基路径，不存用户正文。
生产只接受部署配置允许的 HTTPS origin；LAN 开发可接受管理员明确设置的受信任 LAN HTTP origin。
公网通知传 LAN 地址仍只说明“请连接家庭/办公局域网后查看”，不自动改变网络配置。
禁止从任意请求 Host/X-Forwarded-Host 或来源页面生成外发链接，避免攻击者控制通知地址。
未设置或无效 consoleBaseUrl 时禁用“通知中附成果链接”，不阻断现有任务网页交付。
BarkPushClient 需增加可选导航 URL 的受控入口，并纳入现有 payload/UTF-8 大小限制；外发只在已有明确权限范围内。
默认手机通知采用简短标题与无私密详情摘要，完整内容保存在网页；现有用户明确选择的正文交付策略按兼容要求保留。
禁止用手机点击次数补造已读率；R11 只统计用户显式价值反馈。

## 7. 接续数据与拟议 API

以下为拟新增接口；R02反馈继续使用其原资源命名空间，不复制一套手机反馈服务。
新 `companion_handoffs` 最少包含 `id/device_id/role_id/task_id/run_id/goal_id_nullable/expected_revision/status/expires_at/created_at/consumed_at/resource_version`。
goal-owned接续额外保存服务器核定的goalRevision与taskRevision快照，不能只保存一个含糊的revision；旧无goal记录允许goalRevision为空。
`status` 为 `PENDING/CONSUMED/EXPIRED/CANCELLED/INVALIDATED`，只表达接续意图，不替代 goal/run 状态。
默认有效期为24小时，按服务端 Instant 计算；管理员/模型不能改成无限有效。
同一设备/伙伴最多一个 PENDING；新的明确接续原子使前一条 INVALIDATED，并保存因果关联。
接续只保存对象引用与用户选项 `REVIEW_RESULT/CONTINUE_DISCUSSION`，不新增自由文本私人记忆。
`POST /api/v1/companion/handoffs`：正文 deviceId/roleId/taskId/runId/expectedRevision/intent，HTTP Idempotency-Key；返回 handoff、expiresAt、resourceVersion。
legacy期expectedRevision对应task.revision；若task已绑定goal，则对应用户所见goal.revision，taskRevision由服务端当前绑定快照核对；响应明示revisionOwner=TASK或GOAL，不让客户端猜。
`GET /api/v1/companion/handoffs/{id}`：管理员纯读，不消费，不触发语音。
`POST /api/v1/companion/handoffs/{id}:cancel`：正文 resourceVersion，HTTP Idempotency-Key；成功 CANCELLED，重复同键返回原结果。
`POST /api/v1/delegation-runs/{runId}:mark-read`：JSON expectedRevision（当前任务版本）/resourceVersion（阅读资源版本），HTTP Idempotency-Key；写入仅该管理员对该run的readAt，原目标/运行/投递状态不变。
阅读记录以管理员与run唯一，首次尚无记录读取resourceVersion=0，创建后从1起；已标记重复同键返回原readAt，不新增采用反馈；旧版本也可标已看，仍不允许当作当前目标完成。
所有新增写键保存至少24小时，动作去重按共享契约持久记录；允许服务器规范的16–128字符键，前端默认随机UUID。
网页不得调用一个“消费设备接续”的免认证 API；真实消费在现有语音链内部由受验证上下文调用同一服务。
语音消费在新用户回合中校验当前 deviceId/roleId/taskRevision、目标未取消、run 仍存在、有足够上下文。
goal-owned消费同时校验goalRevision、绑定阶段与R03/R05允许范围；handoff只准讨论/审阅，不自动触发 :resume/:run/批准或消费目标执行预算。
消费事务把匹配 PENDING 改 CONSUMED，以当前 turnId 记录；重复网络回合不得再次播报接续提示。
消费失败不自动标成功；语音回合未建立、设备离线或用户没唤醒时保持 PENDING 至过期。
真实设备角色切换在 `server/src/main/java/com/kj/stackchan/role/CompanionRoleService.java` 同一状态变更事务中使旧角色PENDING接续INVALIDATED；切回不会复活，需重新明确接续。
仅在管理员网页切换只读筛选不改变设备角色；不消费其他角色条目；目标/任务修订也使旧条目 INVALIDATED，网页提示“目标已变化，请重新选择接续”。
目标 CANCELLED、任务物理删除、伙伴归档，必须同步使对应 PENDING 失效；成果仍可见按原管理员边界处理。
已有数据不生成 handoff；迁移保持 taskId/runId，不读出历史所有正文创建条目。
物理删除task/run按关联清理阅读/接续引用；必要去重墓碑只留非正文摘要，历史404仍不能跳到其他成果；匿名计数不保留私人标题。
接口操作记录 ID、scope、事件、耗时与失败码，不记录成果正文或认证载荷。
创建201返回 id/taskId/runId/deviceId/roleId/status/expiresAt/resourceVersion；取消/标记已看200返回当前资源与版本，不返回新的执行任务。
复用R02统一 `GET /api/v1/companion/operations/{key}?action=&resourceId=` 状态回查，不新增手机receipt服务；COMMITTED只说明相应业务写入完成，不等于渠道SENT。
本项注册action为 `HANDOFF_CREATE/HANDOFF_CANCEL/ARTIFACT_MARK_READ`；客户端保留随机键与action，不保存认证信息到回查URL。
400非法路径/字段、401会话过期、403不允许、404归属不符/不存在、409版本/键冲突、503暂不可用按公共错误包装显示，PENDING收据不说已保存。

## 8. 布局、交互和具体文案

框架复用 `FaPageHeader/FaPageMain/FaCard/FaButton/FaTag/FaAlert/FaCollapsible/FaModal/FaForm/FaFormItem/FaTextarea/FaFixedBar`，组件名以实际组件目录核验。
需要路由或表单实现时按仓库 Fantastic-admin 相关 skill，沿用 existing route meta/保活；不编写自制弹窗和底栏。
360px：左右内边距16px，单列；标题、伙伴/设备、状态、摘要、全文、来源、反馈按顺序排列。
正文建议14–16px、行高至少1.6；任务标题/来源/长URL断行；比较表转为逐方案卡片或受控局部滚动，不使整个页面横向溢出。
1280px：正文最大宽度约1024px，可采用成果主体与状态/反馈辅助列；阅读顺序与手机保持一致。
主操作触摸区域至少44px；卡片里的链接不与反馈按钮混为同一点击区。
底部操作区域考虑 safe-area；使用软键盘时不挡输入、保存和取消，内容可滚动到焦点。
“修改偏好”使用一个有标签的文本域，不把已有用户输入在重新获取结果时清空。
未提交内容只保留当前标签页内存；离开时提示是否放弃，默认不写 localStorage 存私人文字。
Enter 不自动提交多行偏好，提交必须明确按钮或可见快捷键；Esc 可关闭框架弹窗并先保护未保存输入。
所有 loading/错误/最新版本变化有文本提示与 aria-live，状态颜色不是唯一信息。
来源链接 `_blank` 加 `noopener noreferrer`；链接文字为来源标题及“查看原文”，不以裸长URL主导首屏。
成果第一屏文案：“这份成果由〔伙伴名〕为〔设备名〕生成”；时间同时显示日期与设备任务时区。
已登录上下文不匹配：“这份成果属于〔伙伴〕，要切换查看吗？”只读切换经明确选择，不改变设备当前角色。
旧版：“你打开的是旧版本，偏好后来已更新”；按钮“查看最新成果”；旧版认可按钮禁用并说明原因。
未登录：“登录后查看这份成果”；不在登录页显示任务标题、私人摘要或来源。
已删除：“这份成果已不存在”；按钮“返回持续委托”，不自动展示其他任务内容。
离线：“当前无法连接，显示的是上次读取的结果”；写操作禁用；提供“重新连接”。
保存状态不明：“还不能确认是否保存，正在核对”；核对后给确定结果，不能一律提示失败诱导再次点击。
并发冲突：“这项任务已经更新，请看最新版本后再提交”；保留原草稿并显示版本差异摘要。
接续成功：“已记下，下次你唤醒这个伙伴时继续；不会主动开口”；可见“取消接续”。
接续过期：“这条接续已过期，成果仍保留”；按钮“重新接续”需重新确认当前版本。
成果末尾独立提供“标记已看”；标记后显示“你已看过这份成果”，旁边保留“认可成果”作为独立业务操作。
Bark 状态显示“渠道已接受”“投递失败”“投递结果不明”，不得显示“手机已读”或“审批已通过”。

## 9. 网络、权限与竞态

初次加载失败不显示旧角色数据；已有快照遇失败保留并显示 fetchedAt 和陈旧标识，反馈按钮需重新验证版本。
持续执行/投递期间最多沿用五秒刷新；无运行/投递时停止计时器，页面 hidden/deactivated/unmount 停止并取消请求。
每次结果检查 route taskId、runId、device/role 与请求序号；过期请求返回不能替换当前页面。
401 清理当前页面私人展示，进入原登录流程；登录后重新校验，而不是直接信任旧缓存。
403 显示无权限，不提供设备Token输入框绕过；404不揭示其他scope对象存在；409显示可操作冲突。
429/服务容量错误尊重服务端重试信息，禁用重复提交短时间；不自动无限重试。
手机和桌面编辑同一目标时以服务端 expectedRevision/resourceVersion 为准；成功提交后刷新，不靠 optimistic UI 宣告完成。
相同 Idempotency-Key 不同正文409；同键重试只核验/返回原操作，不建立第二handoff或再次通知。
深链导航不触发 :run 或 :resume；prefetch、浏览器恢复、分享预览均只能读取。
来源材料、通知正文中的“点击即批准”不产生权限；Bark不作为审批回传渠道。
已取消目标不能新建 handoff；暂停目标可以保存纯讨论接续，但不得自动恢复执行，提示状态为暂停。
明确停止或DND下不主动播报handoff；下一次用户主动询问可只读查询，保持 [R07](R07-proactive-attention.md) 的打扰规则。
真实发送失败与接续失败分开；网页可用不会把 UNKNOWN 改成 SENT。

## 10. GWT 验收矩阵

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| R09-AC01 | 360px 手机、未登录、有效task/run链接 | 打开并登录 | 登录前无私人内容，登录后原run可读，无横向页面溢出 |
| R09-AC02 | 1280px 桌面同一成果 | 打开页面 | 标题/状态/来源与手机一致，阅读布局可用 |
| R09-AC03 | 有效旧run深链 | 当前已有新版 | 旧成果保留、标旧版，可显式跳最新，无自动认可 |
| R09-AC04 | runId 属于另一个 task | 构造深链 | 服务端404，不显示另一个任务内容 |
| R09-AC05 | run已被物理删除 | 打开链接 | 显示已不存在，可返回，不能用最新成果冒充 |
| R09-AC06 | returnTo 为外站或编码绕过 | 登录成功 | 忽略非法返回值，仅进入本站合法页 |
| R09-AC07 | 网页带任意deviceId且未登录 | GET/POST新接口 | 401，参数不产生设备权限 |
| R09-AC08 | 手机编辑期间桌面更新任务 | 手机保存 | 409、保留草稿、加载最新版，无静默覆盖 |
| R09-AC09 | 保存响应丢失 | 用户再点提交 | 同键核验原结果，无重复反馈或运行 |
| R09-AC10 | 已配置LAN站点地址、手机在外网 | 打开通知链接 | 明确需要局域网，不开放公网、不变部署 |
| R09-AC11 | 未配置consoleBaseUrl | 任务要Bark交付 | 可沿原策略发送，无伪造成果URL，网页菜单可读 |
| R09-AC12 | Bark返回SENT | 手机未打开 | 只显示渠道接受，无已读/认可/完成 |
| R09-AC13 | Bark返回UNKNOWN | 页面正常可读 | 不自动重发、不改变UNKNOWN含义 |
| R09-AC14 | 用户仅打开成果页面 | 有在线设备 | 无handoff、无远程语音、无目标恢复 |
| R09-AC15 | 用户明确点击接续 | 下次同设备同伙伴主动唤醒 | 消费一次提示并接续，重复turn不再次提示 |
| R09-AC16 | 接续已超过24小时 | 下次唤醒 | 过期不消费，成果仍可查看 |
| R09-AC17 | 接续绑定角色A | 设备角色切到B再切回A唤醒 | A旧接续已INVALIDATED不复活，无泄漏；需重新明确创建接续 |
| R09-AC18 | pending接续后目标已取消 | 下次唤醒 | 接续失效，无新执行或播报 |
| R09-AC19 | 任务运行期间页面隐藏 | 等待一分钟 | 页面无持续轮询；返回时重新读取scope与版本 |
| R09-AC20 | 360px页面打开软键盘 | 修改偏好并保存/取消 | 输入、按钮、焦点均可见可操作，无被底栏遮挡 |
| R09-AC21 | 只用键盘和屏幕阅读状态 | 查看/展开/反馈 | 逻辑焦点、可见名称、错误提示可读，非颜色依赖 |
| R09-AC22 | 登录会话刚过期 | 页面刷新 | 清除私人快照并登录，重新读取后才允许反馈 |
| R09-AC23 | 成果已可见但无阅读事件 | 仅打开后再明确点“标记已看” | 前一步不产生已读，后一步只写readAt，不认可、不完成、不改投递状态 |

## 11. 验证与AI交付定义

必须产出 360px 和 1280px 的真实运行截图，分别覆盖加载、待阅、来源展开、反馈、冲突、过期与离线。
手机浏览器验证软键盘、safe-area、返回导航；桌面验证键盘；模拟尺寸只证明布局，不代替实体手机体验。
服务端覆盖深链run归属、handoff一次消费、scope、过期、取消、Idempotency-Key与CSRF；不连接真实Bark外发。
新增handoff迁移在含旧个人数据的隔离PostgreSQL验证，确保原task/run IDs与反馈保持。
拟新增测试类必须由实现任务实际建立；以下命令从仓库根运行：

```powershell
./scripts/test-server-linux.ps1 -Tests 'DelegationControllerTest,BarkPushClientTest,CompanionHandoffServiceTest,CompanionHandoffControllerTest'
pnpm --filter @stackchan/console exec vitest run src/api/modules/delegations.test.ts src/views/companion/delegations/detail.test.ts src/views/companion/delegations/handoff.test.ts
pnpm --filter @stackchan/console run build
git diff --check
pnpm docs:check
```

`CompanionHandoffServiceTest/CompanionHandoffControllerTest/handoff.test.ts` 是拟新增验证，不是当前仓库已有成绩。
另运行实际改动 TS/Vue 的 ESLint/stylelint；不因布局测试缺少真实手机而伪报实体验收。
AI交付包含响应式页面、同源认证/安全返回路径、新接口/迁移、无token链接、handoff事务测试、错误文案、截图和跨端事实核验。
交接须写明哪些端实测、哪些仅模拟、哪些设备未在线、是否发送真实通知；不记录私人正文或会话凭据。
软件完成条件为 R09-AC01–23 逐项具备证据且旧入口兼容；无实体手机时可以标“软件通过，手机现场待验”。
更新所属前端/服务端及全局状态，中文单任务提交；未经另行明确许可不部署、刷写或外推。
