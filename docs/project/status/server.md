# 服务端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-23
- 当前分支：`codex/media-002-expression-experience`
- 基准提交：`19bd459`
- 最后验证提交：`41b8827`

## 当前目标

在保持 SCV1/SCV2 和现有安全边界的同时，交付 `MEDIA-002D` 连续帧率策略和设备重连同步。

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

- V35 把 `FIXED/ADAPTIVE` 的帧率约束从离散 `20/30/60` 扩展为连续 `1..60`；固定模式仍要求最小值等于最大值，修改离线可保存，设备重连后自动同步。
- 新增受管理员会话与 CSRF 保护的帧率查询/更新和临时表情预览接口。服务端只接受 12/9/6 枚举与 1–15 秒期限，不接受任意动画参数。
- V35 服务、控制器和 WebSocket 边界均已同步并增加 24–57、47 FPS、0/61 拒绝用例。服务端全量 391/391 通过，Testcontainers 从空 PostgreSQL 成功应用 Flyway V1..V35。
- LAN 运行库已应用 V35，健康接口和首页均为 200，启动日志无 `ERROR`/`Exception`。
- 实机反馈揭示重连同步把帧率错误地依赖在角色主题同步成功之后；工作树已为两者使用独立会话标记，主题同步失败不再阻止帧率恢复，定向 `DeviceWebSocketHandlerTest` 17/17、服务端全量 392/392 和空库 Flyway V1..V35 通过。
- `MEDIA-002D` 服务端实现、最终全量回归和 LAN 发布均已完成。

## 下一步操作

等待用户页面验收；通过后整理相对 `master` 的单一中文任务提交。

## 阻塞项

- 当前无代码或部署阻塞；连接器仍按用户要求暂停。
- 公网生产入口必须保持 HTTPS-only；不得复用管理员会话或设备 JWT 作为集成令牌。

## 关键文件

- `server/src/main/java/com/kj/stackchan/reminder/`
- `server/src/main/java/com/kj/stackchan/security/SecurityConfiguration.java`
- `server/src/main/java/com/kj/stackchan/agent/`
- `server/src/main/java/com/kj/stackchan/api/`
- `server/src/main/resources/db/migration/`
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
- INT-013 server 已部署到 LAN；外部推送、固件刷写或凭据变更仍需分别明确授权。
