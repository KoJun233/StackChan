# 0032：外部通知使用独立令牌、可靠提醒投递与 Streamable HTTP MCP

- 状态：ACCEPTED
- 日期：2026-08-10
- 工作流：服务端、前端、部署
- 关联：[0007：设备语音与持久提醒](0007-device-voice-and-durable-reminders.md)、[0023：受控 ReactAgent、Skill、Tool 与 MCP](0023-controlled-react-agent-skills-tools-mcp.md)

## 背景

Codex、Claude Code 等外部 Agent 需要在任务完成后让 StackChan 主动语音通知用户。现有管理员会话、设备 JWT 和提醒 CRUD 权限都过大；普通提醒的离线“跳过/稍后”策略也不满足外部任务完成消息的可靠排队要求。

## 决策

1. 管理员创建固定绑定单台设备的通知集成。集成令牌使用高熵随机值，只在签发响应中展示一次；数据库只保存 SHA-256 摘要、到期、撤销和最近使用时间。
2. 外部入口只接受独立 Bearer 令牌。该身份只能创建通知并查询本集成通知状态，不能访问管理员、设备、聊天、提醒管理或其他集成数据。
3. REST 与 Streamable HTTP MCP 共用一个 `ExternalNotificationService`。MCP 只公开 `push_notification` 和 `get_notification_status`，不公开 resources、prompts、roots、sampling 或任意回调能力。
4. 外部通知复用 `reminders` 的 TTS、同源音频下载和设备 ACK 链路，增加 `EXTERNAL` 来源、集成归属、幂等键、内容摘要、过期时间和 `EXPIRED` 终态。正文按原文确定性播报，不调用 LLM。
5. 外部通知默认 24 小时过期，离线时保持排队，尊重免打扰和活动语音。每台设备同时最多一条 `DISPATCHED` 播报；失败码只使用受限枚举。
6. 同一集成与幂等键重复提交相同正文时返回原通知，正文不一致返回 409。每个集成默认每分钟 30 次创建请求，最多保留 100 条未完成通知。
7. 管理页面可以创建/编辑/停用集成、签发/撤销令牌、查看通知队列并显式测试播报。健康中心只显示启用集成数、排队数和近期失败/过期数，不返回正文或令牌。

## 原因

独立最小权限令牌避免外部 Agent 获得管理权限；复用已验收的提醒链路避免固件协议分叉；持久幂等和过期时间使重试、重启与离线恢复可预测；单飞约束防止同一设备并发合成和播报。

## 影响

- Flyway V28 新增通知集成、令牌及提醒扩展字段。
- Spring Security 对外部 REST/MCP 使用无会话 Bearer 过滤器，并对这些入口忽略管理员 CSRF；管理员接口继续使用会话与 CSRF。
- 生产入口仍必须在可信反向代理后使用 HTTPS。LAN HTTP 只允许受信任开发网络。
- 本任务不修改固件、不接受任意回调 URL，也不允许紧急绕过免打扰。

## 替代关系

无。
