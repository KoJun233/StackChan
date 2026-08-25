# 服务端工作流

- 状态：ACTIVE
- 最后更新：2026-08-25
- 当前分支：`codex/workday-companion-v1`
- 基准提交：`fa093dc`
- 最后验证提交：`a2a8e29`

## 当前目标

复测已发布的 `CONN-001` V39 每小时日历同步与设备绑定近期日历 Tool；成功后实现 `WEATHER-001` 固定地点天气，同时维持 SCV1/SCV2、角色隔离和设备安全边界。

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
- 当前不启动定时调度、不访问 iCloud/Open-Meteo、不生成语音和动作；状态转换服务供后续语音、日历、天气和传感器编排复用。
- WORK-001A server 已发布到 LAN，运行库迁移至 V38；健康接口为 200，未认证工作日运行态接口保持 401。
- V39 新增加密 iCloud 连接、发现日历、显式允许列表与短期事件缓存；App 专用密码复用 AES-GCM 秘密边界，所有响应只返回账户掩码和安全状态。
- Apple Account 标识只接受账户“登录与安全性”中已登记并验证的电子邮件地址；真实 Shell 对照证明手机号格式会在密码校验前被 Apple CalDAV 固定拒绝，因此不再作为可用入口展示或接收。
- CalDAV 客户端只实现 `PROPFIND` 与 `REPORT calendar-query`；发现默认访问全球 `caldav.icloud.com`，仅在认证失败时尝试中国大陆 `caldav.icloud.com.cn`。生产重定向和查询严格限制在这两个根入口及 `p数字-caldav.icloud.com[.cn]` 编号 HTTPS 分片。单响应限制 2 MiB、最多 64 个日历和 1000 条聚合事件。
- 同步只读取允许列表中的未来七天事件，缓存最长二十四小时；不保存备注、参与人、附件或会议正文，私人事件在入库前替换为“私人日程”并删除地点。
- 每小时扫描已连接或可重试错误状态，只同步存在允许日历且距上次成功同步至少一小时的设备；认证失败停止自动重试，单设备失败不影响其他设备，手动同步继续保留。
- 内建 `upcoming_device_calendar_events` 只读 Tool 绑定当前认证设备，最多返回未来七天内八条未结束事件及缓存时间；日程问题必须调用该 Tool，提醒 Tool 和历史对话不能替代日历事实。
- 连接删除通过外键级联物理删除密码密文、日历白名单和事件缓存；除每小时只读日历同步外，当前不启用工作模式周期调度。真实凭据只在运行态加密保存，不写入日志或文档。
- CONN-001 server 及手机号兼容已发布到 LAN，运行库保持 V39；健康接口为 200，未认证日历接口保持 401，PostgreSQL、Redis、备份容器和 CoreS3 未替换。
- V35 把 `FIXED/ADAPTIVE` 的帧率约束从离散 `20/30/60` 扩展为连续 `1..60`；固定模式仍要求最小值等于最大值，修改离线可保存，设备重连后自动同步。
- 新增受管理员会话与 CSRF 保护的帧率查询/更新和临时表情预览接口。服务端只接受 12/9/6 枚举与 1–15 秒期限，不接受任意动画参数。
- V35 服务、控制器和 WebSocket 边界均已同步并增加 24–57、47 FPS、0/61 拒绝用例。服务端全量 391/391 通过，Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V35。
- LAN 运行库已应用 V35，健康接口和首页均为 200，启动日志无 `ERROR`/`Exception`。
- 实机反馈揭示重连同步把帧率错误地依赖在角色主题同步成功之后；工作树已为两者使用独立会话标记，主题同步失败不再阻止帧率恢复，定向 `DeviceWebSocketHandlerTest` 17/17、服务端全量 392/392 和空库 Flyway V1..V35 通过。
- `MEDIA-002D` 服务端实现、最终全量回归和 LAN 发布均已完成。

## 下一步操作

由用户再次询问机器人近期日程，确认已发布 Tool 的真实语音调用；成功后进入 `WEATHER-001` 固定地点 Open-Meteo。

## 阻塞项

- 当前无代码阻塞；iCloud Calendar、天气和实体动作按开发设计分阶段进入，凭据和设备操作仍需独立边界。
- 公网生产入口必须保持 HTTPS-only；不得复用管理员会话或设备 JWT 作为集成令牌。

## 关键文件

- `server/src/main/java/com/kj/stackchan/reminder/`
- `server/src/main/java/com/kj/stackchan/security/SecurityConfiguration.java`
- `server/src/main/java/com/kj/stackchan/agent/`
- `server/src/main/java/com/kj/stackchan/api/`
- `server/src/main/resources/db/migration/`
- `server/src/main/java/com/kj/stackchan/calendar/`
- `server/src/main/resources/db/migration/V39__icloud_calendar_read_only.sql`
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

- 2026-08-25 CONN-001 Agent 日历闭环：运行库无正文统计确认一条缓存事件有效，Agent 审计确认原失败回合只调用时间和提醒 Tool。新增每小时同步、允许列表门控、单连接失败隔离、设备绑定 Tool、缓存新鲜度区分、重叠事件查询和强制路由；专项 18/18、完整服务端 418/418、空库 Flyway V1..V39 通过。发布前备份和隔离恢复成功，只替换 server；运行健康、LAN 200、未认证日历 401、V39 和一条有效缓存事件均通过，无迁移、前端或固件改动。
- 2026-08-25 CONN-001 Apple 分片修正：真实账号 Shell 依次获得 principal 207、calendar-home-set 207 和编号中国大陆分片日历发现 207，共发现四个日历；确认现网失败由白名单漏接 `p数字-caldav` 命名导致。手机号与故意错误密码均固定返回 403，因此服务端收紧为邮箱。日历定向 7/7、完整服务端 412/412、空库 Flyway V1..V39 通过；修正已发布，运行库保持 V39，无固件改动。
- 2026-08-25 CONN-001 中国大陆区域修正：Apple 官方资料确认 `*.icloud.com.cn` 用于中国大陆 iCloud 服务；本地双端点测试覆盖全球认证失败后回退、中国大陆发现与事件查询，以及非认证失败不跨区重试。日历定向 6/6、服务端完整 411/411 和空库 Flyway V1..V39 通过。发布前备份和隔离恢复成功，只替换 LAN server；本机/LAN/健康接口为 200，运行库保持 V39，无数据库迁移、前端或固件改动。
- 2026-08-25 CONN-001 手机号兼容：日历与控制器定向 7/7、服务端完整 409/409 通过；Testcontainers 从空 PostgreSQL 应用 Flyway V1..V39，无新增迁移。自动化覆盖中国大陆号码规范化、手机号脱敏和不支持格式拒绝；随后只替换 LAN server，运行库保持 V39，本机/LAN/健康接口为 200，未认证日历接口为 401。未连接真实 Apple 账号，未操作 CoreS3。
- 2026-08-25 CONN-001/V39 发布：新备份与隔离恢复成功，只替换 LAN server；运行库由 V38 迁移至 V39，健康和本机/LAN 首页为 200，未认证日历接口为 401，基础容器与 CoreS3 未变化。
- 2026-08-25 CONN-001/V39 候选：加密服务、CalDAV 与控制器定向 5/5、完整服务端复跑 406/406 通过；Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V39。首次完整运行命中既有 `ConversationControllerTest` 流式响应发送竞态，该用例单独 1/1 及随后全量复跑均通过。
- 2026-08-25 WORK-001A/V38：服务端定向 8/8、PostgreSQL 原子简报占位专项 1/1、完整 404/404 通过；Testcontainers 从空 PostgreSQL 应用 Flyway V1..V38。首次持久化专项的测试上下文缺少既有设备依赖替身，补齐测试夹具后重跑通过，生产代码未因该夹具失败调整。

- 2026-08-24 WORK-001A 第一切片：服务端定向 4/4 通过；全量首次因既有 `ConversationControllerTest` 瞬时 `ConcurrentModificationException` 失败，该用例单独 1/1 通过后完整重跑 399/399 通过；Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V37。
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
