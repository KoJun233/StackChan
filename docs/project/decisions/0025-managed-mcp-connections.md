# 0025：管理员管理可移植的 MCP 连接与加密认证

- 状态：ACCEPTED
- 日期：2026-07-29
- 关联：[0023：受控 ReactAgent、Skill、Tool 与 MCP](0023-controlled-react-agent-skills-tools-mcp.md)

## 背景

ADR 0023 首版由 Spring 启动属性提供 MCP 连接，且需要认证头时依赖部署侧反向代理。该方式可以验证
Streamable HTTP Tool 调用，但连接不能由管理员在页面维护，也无法随 PostgreSQL 数据迁移到另一台
StackChan 主机，不符合自托管管理体验。

## 决策

1. “Agent 能力”页面提供 MCP 连接新增、编辑和删除。连接包含稳定名称、HTTPS URL、endpoint、
   `NONE/BEARER` 认证类型；只支持 Streamable HTTP。
2. 连接元数据存入 PostgreSQL。Bearer Token 使用现有 AES-256-GCM `SecretCipher` 加密，密文与随机 IV
   分列保存；API、页面、日志、审计和模型上下文都不返回 Token。编辑时 Token 留空表示保留原密文，
   显式切换为 `NONE` 才清除。
3. 服务端按数据库记录动态构建独立 `WebClientStreamableHttpTransport` 和 `McpSyncClient`。Bearer Header
   只绑定该连接的 WebClient，不注册为全局 Header；修改或删除连接会关闭旧客户端并清除发现缓存。
4. 新增或修改连接后总开关默认停用。连接发现成功仍不代表授权；管理员继续逐连接、逐 Tool 启用，
   并沿用连接身份摘要和输入 Schema SHA-256 变化自动撤权。
5. 生产连接必须使用 HTTPS。仅显式 LAN development 环境允许 localhost、RFC1918 或 `.local` HTTP；
   URL 禁止用户信息、query 和 fragment，endpoint 必须为绝对路径。
6. 单个 MCP 连接失败、超时或返回畸形协议只标记安全失败，不阻止应用启动、普通聊天或其他连接。
7. 旧的 Spring 启动属性连接继续兼容，但页面创建的数据库连接是推荐方式；不得使用机器专用代理作为
   用户连接的持久实现。

## 结果

- 备份并恢复 PostgreSQL 与相同的 `COMPANION_SECRETS_ENCRYPTION_KEY` 后，MCP 连接可迁移到其他机器。
- 轮换加密主密钥前必须按数据生命周期 runbook 迁移密文；丢失主密钥后不能恢复 Bearer Token。
- 本决策只管理连接和只读 MCP Tool，不开放 resources、prompts、sampling、roots、elicitation 或副作用。
