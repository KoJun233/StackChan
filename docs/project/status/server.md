# 服务端工作流

- 状态：VALIDATING
- 最后更新：2026-08-13
- 当前分支：`codex/role-001-role-containers`
- 基准提交：`5d18623`
- 最后验证提交：`9e526f8`

## 当前目标

交付 `ROLE-001` 角色容器：在认证上下文内解析角色，严格隔离会话、记忆、提醒、主动状态和通知归属，并保持设备、模型、语音与固件配置共享。

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

## 正在进行

无代码实现项；ROLE-001 已部署到 LAN，等待用户验收。

## 下一步操作

执行两个角色分别保存事实、页面/语音切换和历史不串用的实体验收。

## 阻塞项

- 当前无实现或部署阻塞；等待用户执行实体角色切换验收。
- 公网生产入口必须保持 HTTPS-only；不得复用管理员会话或设备 JWT 作为集成令牌。

## 关键文件

- `server/src/main/java/com/kj/stackchan/reminder/`
- `server/src/main/java/com/kj/stackchan/security/SecurityConfiguration.java`
- `server/src/main/java/com/kj/stackchan/agent/`
- `server/src/main/java/com/kj/stackchan/api/`
- `server/src/main/resources/db/migration/`
- `server/src/main/java/com/kj/stackchan/notification/`
- `server/src/main/java/com/kj/stackchan/role/`
- `server/src/main/resources/db/migration/V29__companion_role_containers.sql`
- `docs/protocol/external-notifications-v1.md`
- `docs/runbooks/external-notifications.md`

## 验证命令与最近结果

- 以下 ROLE-001 结果针对基于 `5d18623` 的当前任务工作树；提交后将重新执行提交级静态检查。
- `& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test`：348/348 通过。
- Testcontainers 从空 PostgreSQL 成功应用 V1..V29。
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
- [Agent/Skill/Tool/MCP runbook](../../runbooks/agent-tools-mcp.md)

## 安全与兼容性约束

- 不记录通知令牌、API Key、JWT、音频、转写、回复正文、Tool 参数/结果或完整供应商响应。
- 新外部权限只能创建和查询自身通知，不能访问管理员、设备、聊天或提醒管理 API。
- 现有提醒、旧固件、Agent MCP Client 和管理员 CSRF 行为必须兼容。
- 后续部署、运行迁移和外部推送仍需明确授权；本次已授权部署未推送分支。
