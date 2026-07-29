# ReactAgent、Skill、Tool 与 MCP 运行手册

## 固定边界

- 文字聊天和设备语音共用 Spring AI Alibaba `ReactAgent`；当前模型仍由原有 LLM 设置提供。
- 应用只加载 `COMPANION_AGENT_SKILLS_DIRECTORY` 中由管理员上传并通过校验的 Skill 包，不读取用户 home、远程 URL 或任意文件路径。
- 上传单位是完整 ZIP：根目录直接包含 `SKILL.md`，或只有一个含 `SKILL.md` 的顶层目录。导入后默认停用。
- 包内附属文件会保留并在管理页列出，但当前只向模型提供 `read_skill` 读取 `SKILL.md`；不会执行脚本或授予文件系统权限。
- 第一版内置 Tool 和 MCP Tool 都必须是只读能力。提醒、设置、记忆写入和设备控制不在本版本授权范围内。
- 每回合最多 4 次 Tool 调用、Agent 最多 20 秒；单次结果 8 KiB、全部结果 24 KiB。
- STDIO 与旧 SSE MCP transport 已排除，只接受 Streamable HTTP。Agent 不使用 MCP resources、prompts、sampling、roots 或 elicitation。

## 总开关与能力管理

- 部署级总开关：`COMPANION_AGENT_ENABLED`，默认 `true`。设为 `false` 后数据库开关无法重新启用 Agent。
- 用户时区：`COMPANION_AGENT_USER_ZONE_ID`，默认 `Asia/Shanghai`。
- 管理 API：
  - `GET /api/v1/agent/capabilities`：框架、预算、Skill、内置 Tool、MCP 健康和发现结果。
  - `PUT /api/v1/agent/settings`：更新 PostgreSQL 紧急开关。
  - `PUT /api/v1/agent/capabilities`：启停一个内置 Tool、MCP 连接或 MCP Tool。
  - `POST /api/v1/agent/skills`：multipart 上传一个完整 Skill ZIP。
  - `PUT /api/v1/agent/skills/{id}`：启用或停用一个已导入 Skill。
  - `DELETE /api/v1/agent/skills/{id}`：删除一个已停用 Skill 及其完整目录。
  - `POST /api/v1/agent/mcp-connections`：新增一个默认停用的 MCP 连接。
  - `PUT /api/v1/agent/mcp-connections/{id}`：修改连接；Bearer Token 留空时保留现有密文。
  - `DELETE /api/v1/agent/mcp-connections/{id}`：删除连接和加密认证信息。
  - `GET /api/v1/agent/tool-invocations`：查看不含参数和结果正文的调用审计。
- MCP Tool 首次发现后仍是禁用状态。启用时服务端保存连接身份摘要和输入 schema SHA-256；任一摘要变化都会自动撤销运行时授权，直到管理员重新确认。

## 配置 Streamable HTTP MCP

推荐在“Agent 能力”页面填写连接名称、HTTPS URL、endpoint 和认证类型。Bearer Token 通过同源管理 API
提交后使用 AES-256-GCM 加密保存，页面只显示“已配置”，不会再次返回明文。新增和修改连接后授权重置，
先刷新发现并检查服务身份和 Tool 清单，再分别启用连接与 Tool。

旧的 Spring AI MCP Client 标准属性继续作为无秘密兼容入口：

```properties
spring.ai.mcp.client.type=SYNC
spring.ai.mcp.client.request-timeout=10s
spring.ai.mcp.client.streamable-http.connections.home.url=https://mcp.example.invalid
spring.ai.mcp.client.streamable-http.connections.home.endpoint=/mcp
```

生产连接必须使用 HTTPS。只有同时启用 `COMPANION_LAN_DEVELOPMENT=true` 时，才允许
`localhost`、RFC1918 私有 IPv4 或 `.local` 主机使用 HTTP。URL 禁止内嵌用户名、密码、query
或 fragment；endpoint 必须是以 `/` 开头的路径。

除 Bearer Token 外的自定义 Header/OAuth 流程当前仍需可信反向代理或部署环境处理。不得把 secret 写入
仓库、Compose 示例、Tool 描述、日志或诊断证据。跨机器恢复必须同时保留 PostgreSQL 和相同的
`COMPANION_SECRETS_ENCRYPTION_KEY`，否则密文无法解密。

## 验收

1. 未登录访问能力 API 应返回 401；写接口没有 CSRF 应返回 403。
2. 关闭紧急开关后，文字和语音继续普通聊天，但不再获得任何 Tool。
3. 上传测试 Skill ZIP，确认导入后为停用；检查名称、版本、摘要、文件数和文件清单后手动启用。
4. 让模型处理与该 Skill 说明匹配的问题，确认先调用 `read_skill`，且不会因导入包新增 Shell、Python、文件系统或业务 Tool。
5. 停用后下一回合不再列出该 Skill；停用后删除，确认目录和管理页记录同时消失。
6. MCP 连接成功只表示已发现；明确启用 Tool 后才可进入 Agent。修改其输入 schema 后应立即显示为未授权。
7. 审计记录只能包含 ID、名称、来源、结果分类、耗时、字节数和截断标记；不得包含 Tool 参数、结果正文、对话正文、API Key、JWT 或完整模型响应。

## Skill ZIP 安全回归

- 正常覆盖根目录 `SKILL.md`、单顶层目录以及包含 `references/` 的完整包。
- 拒绝缺少或包含多个 `SKILL.md`、非法或超长 frontmatter、重名 Skill。
- 拒绝 `../`、绝对路径、Windows 盘符、重复路径、符号链接、加密条目和特殊文件。
- 拒绝超过文件数、单文件、总解压量、目录深度和上传大小限制的包；失败后不得残留 staging、最终目录或数据库元数据。
- 容器重启后已导入包仍存在，启用状态保持；Registry 不扫描持久目录之外的位置。
