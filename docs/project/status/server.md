# 服务端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-31
- 当前分支：`codex/work-006-daily-task-progress`
- 基准提交：`221711f`
- 最后验证提交：`221711f`
- 最后验证范围：WORK-006 单元 20/20、PostgreSQL 5/5、非 loopback 服务端 456/456 与 LAN/V45 发布通过

## 当前目标

在用户主动询问时可靠返回当前设备与角色的今日完成项和剩余待办，同时保持 WORK-004/V45 数据结构、WORK-005 首次简报和 2026-08-30 至 2026-09-12 的十四天本地匿名聚合不变。

## 已完成的 WORK-006

- 既有 `current_personal_tasks` 保留 `tasks/count` 兼容字段，并追加设备时区自然日内最多十项已完成待办。
- 完成项查询严格绑定当前设备与角色，真实 JPQL 使用半开时间区间并按完成时间倒序。
- Tool 结果中的完成项只包含标题，不包含备注、精确完成时间、完成项 ID、设备或角色字段。
- 待办进度问题进入强制 Tool 路由；Tool 不可用或失败时拒绝猜测。
- 不新增迁移、页面、主动播报、观察指标、固件或身体动作。
- 单元定向 20/20、PostgreSQL/Flyway V1..V45 持久化 5/5、非 loopback 服务端 456/456 通过；LAN server 已发布为 `work006-v45-task-progress`。

## 已完成的 WORK-005

- 新查询只选择当前设备、当前角色的未完成待办，候选为逾期、今天到期或高优先级；到期事项优先并按时间排序，服务层再次限制最多两项。
- 首次简报只拼接最长四十个 Unicode 码点的标题和“已逾期、今天到期、高优先级”分类；返回类型不包含备注。
- 待办读取异常按可选本地增强跳过，不抑制天气、日历或可靠投递；简报成功状态和外部降级归因不增加新维度。
- 不新增 Flyway、API、页面、Agent Tool、固件或身体动作；运行库继续是 V45。
- 定向 18/18、PostgreSQL/Flyway V1..V45 查询与持久化以及排除既有 8 个 Windows loopback 类后的服务端 452/452 已通过。
- LAN server 已发布为 `work005-v45-task-brief`；健康、V45、观察窗口、待办数据和设备安全状态正常，等待下一次合法首次简报实体语音验收。

## 已完成的 WORK-004

- V45 新增设备与角色隔离的个人待办，支持标题、备注、优先级、完成状态和可选截止时间；设备与角色创建后不可改绑。
- 未来截止时间复用同角色可靠提醒；修改更新等待中的提醒，完成、删除或移除截止时间会取消关联提醒，过期时间不补播。
- `current_personal_tasks` 只返回当前设备与角色最多二十条未完成待办的最小字段，明确排除备注；待办问题缺少 Tool 时拒绝猜测。
- 语音新增和完成复用两分钟确认、范围校验和幂等执行；只读任务问题不会进入动作提案链。
- 管理 API 提供筛选、CRUD、完成和重新打开；未认证访问保持 401。
- 定向 40/40、非 loopback 全量 448/448、空库 V1..V45 通过；运行库为 V45，初始待办数为零。

## 已完成的 WORK-003

- V44 为观察记录保存完成通知入队时间，并为每设备、每窗口、每结论的固定主题建立唯一索引。
- 五分钟调度只处理完整结束的窗口；`COLLECTING` 不创建提醒，当前窗口首次调度后完成提醒仍为零。
- 提醒正文固定、绑定当前角色并复用免打扰、离线重试、忙碌门禁、TTS 和 ACK，不调用 LLM、不包含指标明细。
- 调度和重新开始观察争用同一观察行锁；创建提醒与写入标记在同一事务完成，重启和并发不会重复播报。
- 定向 13/13、非 loopback 全量 433/433、空库 V1..V44 及运行库 V44 通过。

## 已完成的 WORK-002

- V43 扩展每日匿名聚合、设备最后序号和每设备当前十四天窗口；观察冻结开始日期、设备时区和工作日掩码。
- 日历/天气简报降级记录受控失败码；心跳序号回退记录重启，固件累计失败数只记录相邻正差值。
- 安全拒绝、预期触摸/语音停止和实体动作失败分开归类；只有超时、反馈和硬件错误使动作门槛失败。
- 管理 API 支持读取、开始、重新开始、标记和撤销当天误播报；报告只在完整十四天后给出 `PASS/FAIL`。
- 最终定向服务端 45/45 通过，覆盖第十四天完整结束前保持 `COLLECTING`；排除 8 个已确认受 Windows Java loopback 限制的既有网络测试类后，其余 424/424 通过，空 PostgreSQL 从 V1 完整应用到 V43。
- 发布前备份和最新备份校验成功，只替换 server；运行库成功迁移到 V43，健康、LAN 首页、未认证观察接口和设备持续在线均通过。

## 已实现的 MEDIA-004

- V36 为资源包增加 `STATIC_PNG` / `LIFECYCLE_EAF` 类型、严格版本耦合、片段表和设备 `lifecycle_clip_supported` 能力位。
- multipart 接口接受最多三个受控事件，服务端编译器验证 EAF 签名、checksum、帧/块表、RLE4 解码长度、尺寸、时长、大小和逐片段摘要，再生成确定性 V2 制品。
- 旧客户端和旧固件继续使用 V1；V2 启用要求目标设备显式上报能力，缺失不能推断支持。角色切换成功后只向支持设备触发受限 `ROLE_SWITCH` 行为。
- 当前验证：395/395 全量通过，Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V36；重连门控专项 1/1 通过。

## 已完成

- 管理员认证、加密 LLM/语音配置、文字与设备语音共用的受控 ReactAgent 对话链路。
- 设备配对/JWT/WebSocket、本地唤醒后的 SCV1 语音闭环、连续对话、触摸取消和阶段诊断。
- 单例结构化人设、确认记忆、建议过滤、`pg_trgm` 检索、使用记录、周期提醒和有限主动关心。
- Skill ZIP、只读 Tool、页面管理且加密认证的 Streamable HTTP MCP Client，以及结构化语音动作提案。
- 对话搜索/导出/物理删除、备份状态、健康中心、唤醒模型/表情资源和应用固件 OTA 服务。
- Flyway V28 新增通知集成、仅哈希令牌和外部通知归属/幂等/过期字段，并在建立设备单飞唯一索引前安全回收历史重复派发。
- 独立 Bearer 过滤链只覆盖外部 REST/MCP；令牌固定集成与设备、只显示一次、支持到期/撤销/禁用，管理员写接口继续要求会话与 CSRF。
- REST 与 `push_notification`、`get_notification_status` 两个 Streamable HTTP MCP Tool 复用同一业务服务；正文确定性入队，不调用 LLM。
- 外部通知默认 24 小时过期，离线持续排队，尊重免打扰、活动语音和设备忙碌；每设备至多一个 `DISPATCHED`。
- 健康中心只提供启用集成、排队、最近失败/过期计数和安全失败码，不返回正文或秘密。
- 管理员可删除单条通知或整个集成；集成删除同步清理令牌和通知历史，`DISPATCHED` 播报期间统一返回 409，避免破坏设备 ACK 链路。

## 已完成的 ROLE-001

- V29 创建角色容器与设备活动角色映射，将既有人设和陪伴数据迁移到默认 `StackChan` 角色，并为会话、记忆、提醒和通知集成建立非空角色归属。
- 角色 CRUD、设备活动角色、归档/恢复和兼容 `/persona` 接口完成；默认角色不可归档，归档会切回默认角色、取消未来提醒并停用对应通知集成。
- 网页会话永久绑定角色，设备语音会话按 `(deviceId, roleId)` 隔离；Agent Tool、记忆建议、主动问候和语音动作均从认证上下文取得角色范围。
- `SWITCH_ROLE` 复用两分钟、同设备/同会话、确认后幂等执行的语音提案机制；活动语音回合或已派发提醒期间拒绝切换。
- 角色背景位于基础安全规则之后并作为受限数据段转义；设备凭据、模型、语音和固件配置继续共享。

## 已完成的 ROLE-002

- V30 为角色增加可空 TTS 音色覆盖，空值继续继承全局配置。
- 设备语音使用会话角色，提醒与外部通知使用持久提醒所属角色；模型不能提供或覆盖角色 ID。
- 角色音色失败后仅用全局音色重试一次；供应商、模型、访问模式与密钥仍为全局共享。
- 全局连接测试与设备播放/ACK 协议保持不变，日志不记录音色值、正文、音频或供应商载荷。

## 已完成的 EVT-002

- Flyway V31 为外部通知增加回执动作白名单和独立回执事件，并扩展受约束的语音动作类型。
- 外部 REST 与两个既有 MCP Tool 复用同一业务服务；旧调用省略动作时保持单向通知，新状态只增加 `responseActions` 和最新 `response`。
- `SNOOZE` 重新排入同一可靠队列并把过期时间延长到再次播报后 24 小时；已知晓和完成是幂等终态回执。
- 语音只选择同设备、同角色最近 24 小时内可回应的通知，目标通知 ID 由服务端固定写入提案，仍需两分钟内确认后执行。
- 不增加回调 URL，不扩大 MCP Tool 数量，不修改固件、设备播放协议或确定性通知正文。

## 已完成的 MEDIA-002C

- V33 为角色增加受约束主题色，为设备保存动态能力、目标/实际 FPS、耗时、丢帧、活动层、降帧原因、堆水位和传感器能力。
- 语音模型只可在正文末尾建议 12 种情绪、三级强度和 5–15 秒期限；服务端剥离标记、严格验证并发送结构化命令，无效标记回退中性且不会朗读。
- 角色切换和设备重连同步活动角色主色；提醒使用确定性 `CONTENT/WEAK/5`，系统状态、颜色与动画参数不受模型控制。
- 旧设备不报告能力时不发送情绪命令；旧协议、静态 PNG、SCV1/SCV2 和通知 ACK 保持兼容。

## 正在进行

- V37 新增设备级工作日设置，默认关闭；保存工作日掩码、可跨午夜时段、50/10 节奏、10/45 离开阈值、固定地点坐标和 IANA 时区。
- `/api/v1/workday/{deviceId}/settings` 提供受既有管理员安全链保护的读取/更新；数据库、服务和请求验证共同拒绝空工作日、同起止时间、半组坐标、越界坐标和不安全阈值。
- `WorkdaySettingsService` 可按设备时区判定普通或跨午夜工作窗口；未创建设置时只产生安全的默认关闭记录。
- V38 新增设备持久运行态、七态转换、明确在场专注累计、离开阈值、休息/稍后/今日跳过和跨工作日安全停止；运行事实可在服务重启后恢复。
- 首次简报以 `(device_id, work_date)` 原子占位，`PENDING` 也阻止重放；成功、部分成功、失败和取消只记计数，不保存正文。
- 日指标只保存启动/结束、专注秒数、简报结果和休息选择，查询与写入时清理九十天以前记录；新增受管理员安全链保护的 runtime/metrics 只读接口。
- 阶段 A 的 V38 server 已发布到 LAN，健康接口为 200，未认证工作日运行态接口保持 401。
- V39 新增加密 iCloud 连接、发现日历、显式允许列表与短期事件缓存；App 专用密码复用 AES-GCM 秘密边界，所有响应只返回账户掩码和安全状态。
- Apple Account 标识只接受账户“登录与安全性”中已登记并验证的电子邮件地址；真实 Shell 对照证明手机号格式会在密码校验前被 Apple CalDAV 固定拒绝，因此不再作为可用入口展示或接收。
- CalDAV 客户端只实现 `PROPFIND` 与 `REPORT calendar-query`；发现默认访问全球 `caldav.icloud.com`，仅在认证失败时尝试中国大陆 `caldav.icloud.com.cn`。生产重定向和查询严格限制在这两个根入口及 `p数字-caldav.icloud.com[.cn]` 编号 HTTPS 分片。单响应限制 2 MiB、最多 64 个日历和 1000 条聚合事件。
- 同步只读取允许列表中的未来七天事件，缓存最长二十四小时；不保存备注、参与人、附件或会议正文，私人事件在入库前替换为“私人日程”并删除地点。
- 每小时扫描已连接或可重试错误状态，只同步存在允许日历且距上次成功同步至少一小时的设备；认证失败停止自动重试，单设备失败不影响其他设备，手动同步继续保留。
- 内建 `upcoming_device_calendar_events` 只读 Tool 绑定当前认证设备，最多返回未来七天内八条未结束事件及缓存时间；日程问题必须调用该 Tool，提醒 Tool 和历史对话不能替代日历事实。
- 连接删除通过外键级联物理删除密码密文、日历白名单和事件缓存；除每小时只读日历同步外，当前不启用工作模式周期调度。真实凭据只在运行态加密保存，不写入日志或文档。
- CONN-001 server 及手机号兼容已发布到 LAN，运行库保持 V39；健康接口为 200，未认证日历接口保持 401，PostgreSQL、Redis、备份容器和 CoreS3 未替换。
- V40 保存按设备固定位置绑定的当前天气及今明两天最小预报字段，不保存供应商完整响应或位置历史；缓存一小时，位置变更会使旧缓存失效。
- Open-Meteo 客户端固定访问官方 HTTPS Forecast API，只请求摄氏温度、体感、降水和 WMO 天气代码；禁止重定向，限制连接/请求时间和 256 KiB 响应，严格验证时区、数组长度、数值范围及天气代码。
- 管理 API 支持读取状态、使用当前提交位置做无保存测试，以及使用已保存位置手动同步；安全失败只返回 `REQUEST_FAILED`、`RESPONSE_TOO_LARGE` 或 `INVALID_RESPONSE`。
- 天气调度启动十秒后首次检查、之后每五分钟扫描；READY 缓存提前十分钟续期，失败设备十分钟退避重试，位置变化立即同步，单设备失败隔离。内建 `current_device_weather` Tool 绑定当前认证设备，只读取未过期缓存；天气问题必须成功调用，缓存不可用时禁止模型猜测。
- V35 把 `FIXED/ADAPTIVE` 的帧率约束从离散 `20/30/60` 扩展为连续 `1..60`；固定模式仍要求最小值等于最大值，修改离线可保存，设备重连后自动同步。
- 新增受管理员会话与 CSRF 保护的帧率查询/更新和临时表情预览接口。服务端只接受 12/9/6 枚举与 1–15 秒期限，不接受任意动画参数。
- V35 服务、控制器和 WebSocket 边界均已同步并增加 24–57、47 FPS、0/61 拒绝用例。服务端全量 391/391 通过，Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V35。
- LAN 运行库已应用 V35，健康接口和首页均为 200，启动日志无 `ERROR`/`Exception`。
- 实机反馈揭示重连同步把帧率错误地依赖在角色主题同步成功之后；工作树已为两者使用独立会话标记，主题同步失败不再阻止帧率恢复，定向 `DeviceWebSocketHandlerTest` 17/17、服务端全量 392/392 和空库 Flyway V1..V35 通过。
- `MEDIA-002D` 服务端实现、最终全量回归和 LAN 发布均已完成。
- WORK-001 工作树新增十五秒周期编排；首次简报复用可靠提醒队列并最多读取两项允许日程，私密标题脱敏，天气/日历各自安全降级，投递结果回写当日去重状态。
- 管理 API、确认式语音动作和严格设备 `workday_toggle` 事件提供显式启停；心跳在场状态只维持、暂停和恢复，不能自行启动。
- 休息只在在场专注达到阈值且设备在线、非免打扰、无活动语音/播报、十五分钟内无日程时入队；开始休息、稍后十分钟和今天跳过均使用确定性状态迁移。
- 返回表现只在连续离开达到返场阈值后触发表情；身体动作还必须已处于 `motion_armed`，不会由工作模式自行启用。
- V42 扩展受约束语音动作类型，并为每日简报和每轮休息主题建立数据库唯一索引。定向服务端 50/50 已通过；排除 8 个已确认受 Windows Java 回环限制的既有网络测试类后，其余 417/417 通过，真实 PostgreSQL 空库完整应用 Flyway V1..V42 并验证工作状态持久化。

## 下一步操作

按 WORK-004 文档验证机器人待办查询、确认式新增与确认式完成；验收后保持现有服务运行。真实发生误播报时由管理员当日显式标记，2026-09-13 接收一次性结果通知并复核 `PASS/FAIL`。

## 阻塞项

- 当前无服务端代码阻塞；固件安装与实体动作仍需独立授权。
- 公网生产入口必须保持 HTTPS-only；不得复用管理员会话或设备 JWT 作为集成令牌。

## 关键文件

- `server/src/main/java/com/kj/stackchan/reminder/`
- `server/src/main/java/com/kj/stackchan/security/SecurityConfiguration.java`
- `server/src/main/java/com/kj/stackchan/agent/`
- `server/src/main/java/com/kj/stackchan/task/`
- `server/src/main/java/com/kj/stackchan/api/PersonalTaskController.java`
- `server/src/main/resources/db/migration/V45__personal_tasks.sql`
- `server/src/main/java/com/kj/stackchan/api/`
- `server/src/main/resources/db/migration/`
- `server/src/main/java/com/kj/stackchan/calendar/`
- `server/src/main/resources/db/migration/V39__icloud_calendar_read_only.sql`
- `server/src/main/java/com/kj/stackchan/weather/`
- `server/src/main/java/com/kj/stackchan/agent/CurrentDeviceWeatherTool.java`
- `server/src/main/resources/db/migration/V40__workday_weather_cache.sql`
- `server/src/main/java/com/kj/stackchan/notification/`
- `server/src/main/java/com/kj/stackchan/role/`
- `server/src/main/java/com/kj/stackchan/speech/VoiceReplySegmenter.java`
- `server/src/main/java/com/kj/stackchan/speech/VoiceTurnStreamEnvelope.java`
- `server/src/main/java/com/kj/stackchan/speech/VoiceTurnService.java`
- `server/src/main/java/com/kj/stackchan/api/DeviceVoiceController.java`
- `server/src/main/resources/db/migration/V29__companion_role_containers.sql`
- `server/src/main/resources/db/migration/V30__role_tts_voice_override.sql`
- `server/src/main/resources/db/migration/V31__interactive_notification_responses.sql`
- `server/src/main/resources/db/migration/V32__deterministic_notification_digests.sql`
- `docs/protocol/external-notifications-v1.md`
- `docs/runbooks/external-notifications.md`

## 验证命令与最近结果

- 2026-08-31 WORK-005：待办查询、时区分类、两项上限、备注隔离、标题截断、失败开放和简报拼接定向 18/18；真实 PostgreSQL 查询与 V1..V45 迁移通过，排除既有 8 个 Windows loopback 类后 452/452 通过。新备份和独立恢复验证后只替换 server；运行版本 `work005-v45-task-brief`，V45、1 条待办、观察窗口和 `424cb49 / motion_disabled / DISABLED` 均正常。

- 2026-08-30 WORK-004：待办、Agent Tool、确认式语音动作和 PostgreSQL 持久化定向 40/40；完整服务端 480 个用例中 31 个错误全部来自 8 个既有 Windows Java loopback 类，业务断言失败为零，排除后 448/448 通过；空库 V1..V45 和运行库 V45 通过。发布后待办数为零，鉴权、观察窗口和设备安全状态未变化。

- 2026-08-30 WORK-003：完成通知与 V44 持久化定向 13/13；完整服务端运行 465 个用例，31 个错误全部来自 8 个既有 Windows Java loopback 测试类且业务断言失败为零，排除后 433/433 通过。运行库迁移到 V44，当前观察记录未重置、完成通知未提前入队，设备保持 `424cb49 / motion_disabled / DISABLED`。

- 2026-08-30 WORK-002 观察启动：运行库存在唯一当前窗口，日期为 2026-08-30 至 2026-09-12，冻结时区 `Asia/Shanghai`、工作日掩码 31；设备启动后继续在线且动作状态保持禁用。

- 2026-08-30 WORK-002：最终定向 45/45 通过；非 loopback 全量 424/424 通过，`DeviceConnectionRegistryConcurrencyTest` 与 V43 空库迁移均包含在通过范围。运行库由 V42 迁移到 V43，健康和鉴权边界正常；可观测性未抑制心跳，设备继续上报 `424cb49 / motion_disabled / DISABLED`。

- 2026-08-30 WORK-001 实机入口：设备长按事件成功创建 `ACTIVE_PRESENT`，第二次长按切换为 `OFF`，跨调度周期保持停止；服务端未执行身体动作，设备持续上报 `motion_disabled / DISABLED`。

- 2026-08-29 WORK-001/V42 发布：新 PostgreSQL 备份和隔离恢复验证成功，只替换 `stackchan-foundation-server-1`，运行库由 V41 迁移到 V42。健康为 `ok`，本机与 `192.168.1.4:8080` 首页为 200，未认证工作接口为 401，运行资源包含工作陪伴显式控制；启动后无应用级 `ERROR`/`Exception`。
- 2026-08-29 WORK-001 整体服务端：工作陪伴定向 50/50 通过；完整套件共运行 449 个用例且无业务断言失败，其中 32 个错误来自 9 个依赖 Windows Java 回环/selector 的测试上下文。将 WORK-001 持久化测试收敛为 JPA slice 后单独 1/1 通过；排除其余 8 个已确认的回环网络测试类重跑 417/417 通过。Testcontainers PostgreSQL 18.4 成功验证并应用 Flyway V1..V42，工作状态和每日简报占位持久化通过。
- 2026-08-29 BODY-001：控制器、命令注册、WebSocket 与事件服务定向 41/41 通过；严格拒绝原始传感器字段、根/身体运动状态冲突、无反馈启用和任意动作参数。完整套件运行 440 个用例，32 个因当前 Windows Java 无法建立 loopback/selector 而出现基础设施错误，失败断言为 0；定向 BODY 用例不依赖该环境并全部通过。发布前新 PostgreSQL 备份及隔离恢复成功；运行库由 V40 迁移至 V41，健康与本机/LAN 首页正常，旧固件继续以全部身体能力为 false 上报。

- 2026-08-28 天气续期修复：真实日志确认旧实现的 `deleteAll` 与同事务插入发生日期唯一键冲突；改为删除后显式 flush，并把调度改为启动十秒检查、五分钟轮询、到期前十分钟续期和失败十分钟退避。专项 5/5 通过；完整服务端运行 430 个用例，其中 30 个因受限执行环境无法建立 Java loopback 而发生基础设施错误，无业务断言失败。发布后真实 PostgreSQL/Open-Meteo 同步成功，状态 `READY`、缓存新鲜，2026-08-28/29 两条预报写入且日志无重复键错误。
- 2026-08-26 用户确认真实固定位置同步和机器人天气回答正常，WEATHER-001 服务端人工验收通过。
- 2026-08-26 WEATHER-001：Open-Meteo 客户端、缓存服务、小时调度、设备绑定 Tool、管理 API 和强制路由定向 25/25；完整服务端 429/429 通过，Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V40。首次全量运行发现多构造器组件缺少明确注入构造器，修正后完整复跑通过。
- 2026-08-26 WEATHER-001 LAN：发布前备份和隔离恢复成功，只替换 server；运行库由 V39 迁移至 V40，健康状态为 `ok`，本机和当前 LAN 首页为 200，未认证天气接口为 401，CoreS3 未操作。
- 2026-08-25 CONN-001 Agent 日历闭环：运行库无正文统计确认一条缓存事件有效，Agent 审计确认原失败回合只调用时间和提醒 Tool。新增每小时同步、允许列表门控、单连接失败隔离、设备绑定 Tool、缓存新鲜度区分、重叠事件查询和强制路由；专项 18/18、完整服务端 418/418、空库 Flyway V1..V39 通过。发布前备份和隔离恢复成功，只替换 server；运行健康、LAN 200、未认证日历 401、V39 和一条有效缓存事件均通过，无迁移、前端或固件改动。
- 2026-08-25 CONN-001 Apple 分片修正：真实账号 Shell 依次获得 principal 207、calendar-home-set 207 和编号中国大陆分片日历发现 207，共发现四个日历；确认现网失败由白名单漏接 `p数字-caldav` 命名导致。手机号与故意错误密码均固定返回 403，因此服务端收紧为邮箱。日历定向 7/7、完整服务端 412/412、空库 Flyway V1..V39 通过；修正已发布，运行库保持 V39，无固件改动。
- 2026-08-25 CONN-001 中国大陆区域修正：Apple 官方资料确认 `*.icloud.com.cn` 用于中国大陆 iCloud 服务；本地双端点测试覆盖全球认证失败后回退、中国大陆发现与事件查询，以及非认证失败不跨区重试。日历定向 6/6、服务端完整 411/411 和空库 Flyway V1..V39 通过。发布前备份和隔离恢复成功，只替换 LAN server；本机/LAN/健康接口为 200，运行库保持 V39，无数据库迁移、前端或固件改动。
- 2026-08-25 CONN-001 手机号兼容：日历与控制器定向 7/7、服务端完整 409/409 通过；Testcontainers 从空 PostgreSQL 应用 Flyway V1..V39，无新增迁移。自动化覆盖中国大陆号码规范化、手机号脱敏和不支持格式拒绝；随后只替换 LAN server，运行库保持 V39，本机/LAN/健康接口为 200，未认证日历接口为 401。未连接真实 Apple 账号，未操作 CoreS3。
- 2026-08-25 CONN-001/V39 发布：新备份与隔离恢复成功，只替换 LAN server；运行库由 V38 迁移至 V39，健康和本机/LAN 首页为 200，未认证日历接口为 401，基础容器与 CoreS3 未变化。
- 2026-08-25 CONN-001/V39 候选：加密服务、CalDAV 与控制器定向 5/5、完整服务端复跑 406/406 通过；Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V39。首次完整运行命中既有 `ConversationControllerTest` 流式响应发送竞态，该用例单独 1/1 及随后全量复跑均通过。
- 2026-08-25 WORK-001 阶段 A/V38：服务端定向 8/8、PostgreSQL 原子简报占位专项 1/1、完整 404/404 通过；Testcontainers 从空 PostgreSQL 应用 Flyway V1..V38。首次持久化专项的测试上下文缺少既有设备依赖替身，补齐测试夹具后重跑通过，生产代码未因该夹具失败调整。

- 2026-08-24 WORK-001 阶段 A 第一切片：服务端定向 4/4 通过；全量首次因既有 `ConversationControllerTest` 瞬时 `ConcurrentModificationException` 失败，该用例单独 1/1 通过后完整重跑 399/399 通过；Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V37。
- 2026-08-23 MEDIA-004 服务端全量 395/395 通过；Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V36。生命周期编译/WebSocket 定向 19/19、旧固件重连门控 1/1 通过。
- 2026-08-23 MEDIA-004 发布前备份及隔离恢复成功，只替换 LAN server；运行库迁移至 V36，健康接口与首页为 200，未认证表情包和设备接口为 401，基础容器未替换且 CoreS3 未 OTA。
- 2026-08-23 服务端全量 392/392 通过；Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V35。
- 2026-08-23 经授权只替换 LAN server，运行镜像 `sha256:bfd2019b4e8a84a0132794096d751f3bce872555165a11219081b820f9445810`；健康接口为 200、Flyway V35 无待迁移项、启动日志无错误。CoreS3 重连后恢复 `ADAPTIVE 45–60` 并上报目标 60/实际 55。
- 2026-08-22 Docker Engine 恢复后完成最新全量回归：391/391 通过；Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V35。

- MEDIA-002 情绪解析、设备表达、WebSocket、角色、设备 API 与 LLM 异常映射专项 39/39 通过。
- Docker 恢复后服务端全量 385/385 通过，运行数据库迁移至 V33；LAN 首页和健康检查正常，用户已完成页面与基础动态诊断复核。
- 排除 `ConversationServiceTest`、`DeviceEventServiceTest`、`PairingServiceTest`、`AdminPasswordPersistenceTest`、`NotificationMcpTransportTest` 和 `LongTermMemoryPersistenceTest` 六个 Testcontainers 类后，服务端回归 361/361 通过；Docker Engine 无响应，未声称完成全量或空库 V1..V33 验证。
- INT-013 工作树基于 `13987b0`；SCV2 严格帧、确定性分段、服务编排、内容协商和取消定向测试通过。
- 最终全量 Maven 377/377 通过，并由 Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V32；INT-013 不增加数据库迁移。
- `& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test`：351/351 通过。
- Testcontainers 从空 PostgreSQL 成功应用 V1..V30。
- 角色服务、音色运行时、语音回合和提醒投递定向 24/24 通过，覆盖角色音色生效、失败后单次全局回退和角色 ID 路由。
- LAN 运行库已应用 V30，`/api/v1/health` 为 200，首页为 200，未认证角色/设备 API 为 401，构建版本为 `b6cad0b`，启动日志无错误。
- `CompanionRoleServiceTest,ConversationServiceTest`：角色生命周期、设备绑定与会话归属定向 13/13 通过。
- 真实 Java MCP Streamable HTTP 客户端通过 Bearer 鉴权，只发现两个通知 Tool，并成功创建 `EXTERNAL` 队列项。
- 针对测试覆盖撤销/过期/禁用令牌、越权、限流、队列上限、幂等冲突、离线、过期、设备单飞、TTS 失败和 ACK 重放。
- LAN 运行库已应用 V28；健康接口为 200，未认证外部 REST 与 `/mcp/notifications` 均返回 401，启动日志无错误。
- 用户确认当前 LAN 的基础外部通知测试正常；免打扰延期和离线重连仍待专项验收。
- 删除功能新增 CSRF、集成级联清理、单条通知删除和播放中 409 覆盖；已重新部署到 LAN。
- 全量首次运行遇到既有异步 MockMvc 用例的瞬时 `ConcurrentModificationException`；该用例单独复跑及随后完整 346/346 重跑均通过，未修改无关实现。

## 相关设计、计划和决策

- [当前任务清单](../todo.md)
- [工作日桌面陪伴 V1 开发设计](../workday-companion-v1.md)
- [0041：私用优先的确定性工作日陪伴闭环](../decisions/0041-private-first-deterministic-workday-companion.md)
- [稳定架构](../architecture.md)
- [0007：设备语音与持久提醒](../decisions/0007-device-voice-and-durable-reminders.md)
- [0023：受控 ReactAgent、Skill、Tool 与 MCP](../decisions/0023-controlled-react-agent-skills-tools-mcp.md)
- [0031：安全应用固件 OTA 与健康中心](../decisions/0031-safe-application-firmware-ota-and-health-center.md)
- [0032：外部通知平台](../decisions/0032-external-notification-platform.md)
- [0033：角色容器](../decisions/0033-companion-role-containers.md)
- [0034：角色 TTS 音色覆盖](../decisions/0034-role-tts-voice-overrides.md)
- [0035：互动通知回执](../decisions/0035-interactive-notification-responses.md)
- [0036：确定性通知摘要](../decisions/0036-deterministic-notification-digests.md)
- [0037：有序分段语音播放](../decisions/0037-ordered-streaming-voice-playback.md)
- [Agent/Skill/Tool/MCP runbook](../../runbooks/agent-tools-mcp.md)

## 安全与兼容性约束

- 不记录通知令牌、API Key、JWT、音频、转写、回复正文、Tool 参数/结果或完整供应商响应。
- 新外部权限只能创建和查询自身通知，不能访问管理员、设备、聊天或提醒管理 API。
- 现有提醒、旧固件、Agent MCP Client 和管理员 CSRF 行为必须兼容；SCV1 不得移除。
- 服务端及内置管理页面可按用户长期授权直接发布；Git 外部推送、固件刷写/OTA 或凭据变更仍需分别明确授权。
