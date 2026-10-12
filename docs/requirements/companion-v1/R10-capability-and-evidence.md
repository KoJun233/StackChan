# R10：可信能力目录、真实成果与来源证据

- 文档状态：ACTIVE；需求状态：SPECIFIED；实现状态：NOT_STARTED
- 版本：1.0；日期：2026-10-12；源码事实基准：`989b3d9`
- 原计划：GOAL-01的工具/成果边界，贯穿LOOP-01；优先级：P1，真实证据自R02起必须有效。
- 入口：[需求索引](README.md)；统一规则：[公共契约](00-shared-contracts.md)。
- 现有证据前置：[R00](R00-baseline-and-integration.md)、[R02](R02-project-loop.md)；新增目标范围前置：[R03](R03-goal-lifecycle.md)、[R05](R05-permissions-and-budgets.md)。
- 证据保存/恢复：[R04](R04-durable-execution.md)；页面呈现：[R09](R09-mobile-handoff.md)；真实价值：[R11](R11-pilot-and-release.md)。

## 1. 做到什么程度

用户问“你能做什么”时得到当前启用、授权且配置相符的能力；交代事情后知道是否真的读取、生成、发出，哪些结论来自资料、哪些是建议、哪些信息没有取得。缺能力不是靠模型自信语气掩盖，也不是把一项研究退化为仅提醒用户去做。

本项必须交付可信目录、缺口说明、工具前验证、证据绑定、稳定成果标识、来源新鲜度和可检查页面。API/工具变更会使旧目录快照失效。当前受控公开页面读取继续工作；无需为了验收新增搜索服务或私人应用连接器。

## 2. 当前真实起点

| 当前源码 | 已有事实 | 本项增量 |
| --- | --- | --- |
| `server/src/main/java/com/kj/stackchan/agent/AgentToolAssemblyService.java` | 内置/Skill/MCP按配置装配、固定上下文 | 丰富可信效果/配置状态与版本，结合当前goal范围 |
| `CapabilityListTool.java`（同包） | tools/skills名称，不返回认证/连接地址 | 兼容增加有界能力说明和缺口，保留原字段 |
| `AgentToolPolicyInterceptor.java`（同包） | 捕获真实steps/evidence、绑定来源id、限制结果大小 | 持久实际来源/步骤关联与完整度，不采信模型假引用 |
| `PublicSourceReadTool.java`（同包） | read_public_source读取单URL/feed，结果最多6144B，不自动抓links | 明确链接发现与真正读取的区别，保存裁剪/读取时刻 |
| `server/src/main/java/com/kj/stackchan/delegation/PublicSourceReader.java` | HTML文本/来源、feed条目；6秒/1MiB、拒绝非2xx；不跟重定向 | 复用公网策略，范围受R02/R05约束，不增加认证抓取 |
| `PublicHttpsUrlPolicy.java`、`PinnedHttpsTransport.java`（同包） | 受控公共HTTPS与地址固定边界 | 保留DNS/私网/认证限制，不因目标需要绕过 |
| `AgentDelegationTaskExecutor.java`（同包） | 验evidenceIds来自真实返回，output URL须在证据中，required read无证据不能SUCCEEDED | 增量真实证据完整度、成果关联及缺失维度 |
| `DelegationService.java`（同包） | 现有run output/steps/evidence，attempt可更新 | R04归档不可变尝试，本项只建单一稳定artifact引用 |
| `apps/stackchan-console/src/views/companion/delegations/helpers.ts` | 来源链接显示/安全处理 | 事实与推断、读取时间、缺信息、陈旧状态可读 |

基准没有内置通用搜索引擎、购买/支付、任意登录网站/私人应用写操作或Dots/Muse集成API。已启用MCP工具按管理员实际配置核对，不能因为插件入口存在就宣称所有第三方能力可用。

## 3. 范围与排除

包括可信目录与状态、阶段工具交集、公开来源验证、有限成果来源、缺能力、历史兼容和测试。目录描述来自代码/管理员审核配置，能力状态来自当前配置及实际调用，不通过真实发送/付费探针自动检测。

不新增搜索服务商、云电脑、任意浏览器控制、自动购买、登录抓取、向量库、私人文件批量摄取、百科事实库或工具市场。新的adapter必须独立需求，说明正式API、费用、权限、错误和证据质量；本项只保留有限可扩接口，不做空壳SDK。

## 4. 可信能力分类与状态

类别沿R05的GENERATE_LOCAL/READ_PUBLIC/READ_PERSONAL/WRITE_PERSONAL_LOCAL/BARK_SEND/UNKNOWN_EFFECT等语义，保留现有AgentCapabilityType的BUILTIN_TOOL/SKILL/MCP_SERVER/MCP_TOOL来源类型，二者不是同一个枚举。

| capabilityStatus | 含义 | 可向用户怎样表达 |
| --- | --- | --- |
| READY | 必要配置和授权满足，且有当前可信成功依据 | 当前可以在此范围使用，实际结果以本次调用为准 |
| CONFIGURED_UNVERIFIED | 已配置/启用，但本版本未实测或不应自动探测 | 已配置，实际调用后确认；不能宣称本次执行过 |
| DISABLED | 管理员关闭/运行关闭 | 当前未启用，可查看设置 |
| UNCONFIGURED | 缺必需配置/凭据 | 尚未配置，不将密钥交给模型补齐 |
| TEMPORARILY_UNAVAILABLE | 最近实际调用失败，失败类别明确 | 暂不可用，给有限恢复选择 |
| UNSUPPORTED | 当前没有这类实现 | 不能执行，提供手动资料/草稿路径 |
| UNKNOWN | 无法可靠读取目录/状态 | 状态未知，不授予自主权限 |

READY不意味着供应商保证成功；临时失败展示至少60秒的有界错误快照，再次用户请求可依法尝试，不自动探测/重试副作用。目录中的“配置可用”不能取代R05批准或结果中的真实receipt。

## 5. 编号功能要求

**R10-FR01 目录事实源。** 以真实ToolCallback、当前启用记录、可信效果分类、必要配置和版本构造目录，禁用/归档/更新立即使快照无效。一次请求最多100项，名称/摘要有界；不缓存跨伙伴目录给另一范围。

**R10-FR02 咨询与权限分开。** “能发手机消息吗”只返回能力与所缺条件，不开放写工具或创建目标。历史模型否认与当前可信目录冲突时以目录说明当前能力。目录下发system范围不允许来源文档覆盖。

**R10-FR03 缺口。** 每个无法执行的阶段给固定reason（NO_TOOL/NOT_CONFIGURED/NOT_AUTHORIZED/UNKNOWN_EFFECT/SOURCE_UNAVAILABLE/EVIDENCE_MISSING等）、已完成部分、最少一个合法替代及需用户决定的事项。缺搜索时可请提供URL；不得说“我已搜遍市场”或要求为了所有委托必须RSS。

**R10-FR04 实际调用取证。** steps只能由服务器拦截真实工具生成；每步关联toolCallId、run/attempt、scope、tool版本、开始/结束、状态、有限结果/错误及裁剪标志。无实际调用不能保存SUCCEEDED工具步骤。生成本地草稿可无外部工具，标明GENERATED_DRAFT，不能作为外部事实已核实。

**R10-FR05 URL范围。** 读取前检查R02/R05保存范围和原公网HTTPS策略，拒绝credential URL、私网/loopback/link-local及非法协议。当前读取器不跟重定向，3xx标SOURCE_UNAVAILABLE并供用户选择明确合法新URL；不为“兼容”悄悄开自动跳转。网页links不自动扩大范围。

**R10-FR06 来源类型。** PUBLIC_PAGE指实际成功读取该页；FEED_ENTRY指feed中的条目/摘录，不能声称阅读全文；DISCOVERED_LINK只供导航，不能当链接正文已核实；USER_PROVIDED明确是用户输入；GENERATED_DRAFT是系统推断/创作，不是资料事实。一次成果最多20条实际证据，保留现有限制。

**R10-FR07 证据关联。** 引用的evidenceId必须来自当前实际调用或经R04允许复用的真实检查点，绑定toolCallId/run/attempt/读取时间/规范URL。模型新造id、标题或URL不能进入verified集合；output现实来源URL必须有对应真实证据，不只做字符串以https开头校验。

**R10-FR08 结论性质。** 将来源陈述、用户条件、推断/推荐、未知分开。官方营销描述注明“来源宣称”，不冒充独立体验验证。资料只证明被读取，不自动证明其所有内容正确；冲突事实并列来源、日期和待核实项，不静默选择更好看的数字。

**R10-FR09 缺失和裁剪。** 未提供尺寸/价格填“来源未提供”；截取后的结果显示truncated=true及限制，不能推断遗漏信息不存在。内部工具6144B、单结果8192B/总24576B默认及更严配置保持。不能扩大缓冲/取消限制让“全文”标签成立。

**R10-FR10 新鲜度。** 新目标默认动态价格/库存/可买性有效期24小时，其他公开方案描述7天；用户可设更严期限或明确“按某日资料比较”，后者展示历史口径。页面实际读取时间与文章发布时间分开；feed发布日不作为网页fetch时刻。到期证据可读为历史，但不能直接满足“当前价/当前可买”的完成标准。

**R10-FR11 重新读取。** 新鲜度到期而标准要求当前信息时，目标显示缺条件，用户明确继续后按R04/R05读取并消耗预算；不得后台无限刷新。资料不可读保留历史/未知，不拿旧成功步骤当本次实际执行。用户接受历史限制须明确修订标准，不能靠点击“完成”跳过检查。

**R10-FR12 单一成果标识。** artifactId只在一个统一成果引用服务产生，为服务端UUID；唯一(runId,attempt,outputVersion)，outputVersion从1起且已发布内容不可变。metadata关联原run/尝试归档正文引用，禁止在目标/手机/记忆另存三份全文。修改产出是新版本/新artifact，纯继承/展示/通知重发不是新产出。

**R10-FR13 历史映射。** R02先沿用task/run及实际attempt；R10给已保存成果建立稳定artifact引用，旧taskId/runId深链保持。缺历史attempt/读取时间/证据id时标PARTIAL/UNKNOWN，不猜测补齐，不能生成虚构旧执行记录。当前可读取正文只映射一次，R04的真实旧尝试归档可追溯。

**R10-FR14 修订与继承。** 保存artifact原goalRevision和当前有效关联分开。R03纯pause/resume允许同artifact在内容未变且检查仍有效时继承，记录inheritedFromRevision/definitionDigest，不改原事实和读取日期、不计R11新交付、不创建新R07提示。偏好/标准/来源变化则旧成果历史化，不未经复核采用。

**R10-FR15 删除与来源撤销。** 删除run/源会话/goal遵循原隐私/关联删除，清理相应引用、待阅、普通候选与未消费审批；稳定成果ID不能成为访问已删正文的后门。外部动作保留不含正文的永久去重墓碑；不把资料结果自动写成confirmed个人记忆。

**R10-FR16 结果真实性。** 必需来源无有效证据时BLOCKED或WAITING_USER；部分资料可生成有限草稿但不能伪报全部任务完成。NO_UPDATES按公共C04结束运行，不产生新artifact/推送；SENT、已读、采用、COMPLETED独立，工具输出不替用户认可。

**R10-FR17 UI与诊断。** 首屏显示成果、读取时间、缺信息和建议依据；完整来源可展开标题/规范URL/类型/时刻。普通用户不必理解toolCallId，诊断区可查看匿名关联。加载/空/读取失败/陈旧/冲突有可读文字，不仅颜色。

**R10-FR18 受控扩展。** 新adapter有固定schema、效果类别、权限/预算和证据输出，不接受任意模型代码/HTTP请求。来源含工具指令只作数据；UNKNOWN_EFFECT或供应商价格/接口未核实，不启用持续目标。无需本项购买/安装新插件。

## 6. 拟新增数据、接口与兼容

artifact引用建议表 `companion_artifact_refs`：id、task/run/attempt/output_version、goal/stage_nullable、原revision、body_ref、content_sha256、created_at、provenance_completeness；唯一run/attempt/output版本。body_ref指原运行或R04不可变尝试档案，禁止复制明文到每张映射表。旧记录迁移按实际现有版本建引用，不计算虚构产出数。

当前有效成果关联保存(goalId,goalRevision,artifactId,validity,inheritedFromRevision,definitionDigest)，以当前目标定义检查；同artifact可以有历史与当前关联。COMPLETE/PARTIAL/UNKNOWN证据完整度是独立字段，UNKNOWN不满足必需已核实标准。

来源引用最少有sourceId/type、canonicalUrl_nullable、title、actualReadAt_nullable、publishedAt_nullable、toolCallId_nullable、run/attempt、contentDigest、boundedExcerpt、truncated、freshUntil、availability。excerpt复用已允许有限结果/现有detail，不整页归档和泄露私人正文到日志；缺值明确null而非0/当前时刻。

| 拟新增接口 | 响应与约束 |
| --- | --- |
| `GET /api/v1/companion/capabilities?deviceId=UUID&roleId=UUID&goalId=UUID` | 认证范围内最多100项、catalogVersion、效果、status、allowedForGoal、固定缺口；不返回连接地址/secret/schema全文 |
| `GET /api/v1/companion/artifacts/{id}` | 已授权成果正文引用、原版本/当前关联、类型、来源/完整度/限制、反馈引用；无写副作用 |
| `GET /api/v1/companion/artifacts/{id}/evidence` | 默认20上限20，真实有限来源与步骤引用；可查历史但明示陈旧 |
| `GET /api/v1/companion/goals/{id}/capability-gaps` | 当前revision的缺口与可选下一行动，不自动安装/探测/执行 |

保留CapabilityListTool的tools/skills字段，新目录metadata为兼容可选；旧委托API的output/steps/evidence与深链继续可用。新接口scope绑定服务端，不让任意goalId改变授权；401登录、403无许可、404跨范围/删除、503目录暂不可读，业务缺能力在200响应中按状态呈现，执行命令可按公共C10返回422。

本项查询不接任意测试URL，不新建“试一下Bark”的探针入口，不产生审批/已读事件。新增写/迁移关联沿公共幂等/版本规则和最新Flyway编号，R03–R09消费唯一artifact服务。

## 7. 用户可见文字

| 事实 | 展示文字 |
| --- | --- |
| 有公开资料工具、无搜索 | “我能读取你给的公开HTTPS页面，目前没有通用搜索能力。可以先比较你提供的链接。” |
| 手机能力已配置但未请求 | “已配置手机通知；只有你明确要求时才发送。” |
| 没读链接正文 | “这是来源中的链接，尚未读取链接页面。” |
| feed条目 | “依据订阅源条目摘要，未读取文章全文。” |
| 未提供维度 | “来源未提供尺寸/价格，这一项尚不能比较。” |
| 陈旧动态资料 | “价格依据〔读取日期〕，尚未确认当前价格。” |
| 局部成果 | “已比较读取成功的资料，另外一份暂不可读。可以补链接或接受有限比较。” |
| 必需证据缺失 | “缺少可核实来源，这一步尚未完成。” |
| 旧成果继承 | “恢复后沿用这份成果，内容未变；来源时间保留原记录。” |
| 目录读取失败 | “能力状态暂时未知，请稍后查看；没有开始新操作。” |

来源在手机360px折行，外链noopener noreferrer，危险协议拒绝；1280px保留相同信息顺序。缺资料不显示绿色“全部完成”，不把模型语气或任务数量用作成功证据。

## 8. Given / When / Then 验收

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| R10-AC01 | read_public_source启用，无搜索工具 | 用户问能力/交代比较 | 如实限定公开URL，不声称全网搜索 |
| R10-AC02 | Bark已配置，用户只问能否发送 | 能力咨询 | 不外发、不创建批准，说明明示请求边界 |
| R10-AC03 | 工具在规划后被关闭 | 下一实际调用 | 最新目录拒绝，不使用旧快照 |
| R10-AC04 | 一个MCP工具schema改变/效果未知 | goal自主规划 | UNKNOWN_EFFECT受阻，不能继承旧只读分类 |
| R10-AC05 | 公共页含注入指令和攻击者收件人 | 读取/建议 | 指令不授予工具或外发，真实数据可有限使用 |
| R10-AC06 | URL是私网、带凭据或3xx | 读取 | 原策略拒绝，无自动跳转/认证抓取 |
| R10-AC07 | 返回页只发现一个产品链接 | 输出比较 | 不声称读取产品全文，链接只标发现 |
| R10-AC08 | feed实际读取成功 | 生成摘要 | 标FEED_ENTRY，发布时间不冒充文章fetch时刻 |
| R10-AC09 | 模型构造E99和虚假来源URL | 成果校验 | 拒绝该引用，不进入verified成功成果 |
| R10-AC10 | 必需读取工具实际失败 | 模型返回成功 | BLOCKED/缺证据，不保存成功交付 |
| R10-AC11 | 来源无价格/尺寸 | 生成比较 | 明示未知，推荐不使用虚构数字 |
| R10-AC12 | 结果超过6144B/现有预算 | 工具返回 | 受控裁剪并标限制，不扩结果额度 |
| R10-AC13 | 价格证据超过24小时 | 当前价标准检查 | 不直接PASS，需重新读取或明确历史标准 |
| R10-AC14 | 资料作者营销称最受欢迎 | 推荐 | 标来源宣称，不视为独立实测排行 |
| R10-AC15 | 相同run/attempt/output版本并发映射 | 建artifact引用 | 仅一个artifactId，正文不复制两份 |
| R10-AC16 | 旧run无完整读取时间/来源id | 迁移/页面查看 | PARTIAL/UNKNOWN，不补当前时刻假取证，旧深链可用 |
| R10-AC17 | REVIEW_READY纯暂停/恢复 | 继承有效成果 | 原artifact不变，当前合法关联、重查新鲜度，不计新交付/提示 |
| R10-AC18 | 恢复同时改了预算/来源 | 采用旧成果 | 不走纯继承认可路径，按新定义重新检查/工作 |
| R10-AC19 | NO_UPDATES且没有新正文 | 运行结束 | 不新建artifact，stage PENDING/goal WAITING_USER，无紧循环 |
| R10-AC20 | 来源/run被删除后旧artifact URL | 查询 | 404/清理引用，不读隐蔽副本，非正文副作用墓碑仍防重 |
| R10-AC21 | 成果已SENT但未采用 | 用户查完成 | 状态仍待阅/真实goal状态，不说已读或完成 |
| R10-AC22 | 两角色资料与目录 | 跨范围查询 | 不泄露标题/正文/配置，scope服务端验证 |

## 9. 验证与 AI 交付

自动验证使用mock公开响应/目录/供应商与隔离PostgreSQL，覆盖真实拦截器和执行器，不能只测试渲染固定数组。SSRF/3xx/credential与提示注入为负例，测试不抓真实私人网站或真实Bark。

```powershell
./scripts/test-server-linux.ps1 -Tests 'PublicSourceReaderTest,AgentToolPolicyInterceptorTest,AgentDelegationTaskExecutorTest,BarkNotificationToolTest'
./scripts/test-server-linux.ps1 -Tests 'CompanionCapabilityCatalogTest,CompanionArtifactPersistenceTest,CompanionEvidenceValidationTest'
pnpm --filter @stackchan/console exec vitest run src/views/companion/delegations/helpers.test.ts src/views/companion/delegations/detail.test.ts
pnpm --filter @stackchan/console run build
git diff --check
pnpm docs:check
```

第二条服务端命令的三类是本需求必须新增的验证，尚不存在。本项真实用户成果验收至少两份实际已许可公开资料，原始私人输入本地保留；fixture只证明工程，不计R11产出。

AI交付FR01–18/AC01–22对照、可信目录/版本/类别、守卫、真实来源绑定、唯一artifact迁移、有限页面和缺口文字，空库/旧数据升级与删除并发证据。说明当前可用能力和仍缺能力，保留旧API/深链及历史完整度。更新所属状态/全局并保存一个中文提交；不为了报告“可用”自动采购、安装、真实外发、部署或推送。
