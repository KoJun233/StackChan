# 0023：对话使用受控 ReactAgent、受信任 Skill 与仅授权 MCP Tool

- 状态：ACCEPTED
- 日期：2026-07-28
- 关联：[0002：Java / Spring AI 后端](0002-java-spring-ai-alibaba-backend.md)、[0020：确认后进入上下文的长期记忆](0020-confirmed-scoped-long-term-memory.md)
- 部分替代：[0024：管理员导入受控的完整 Skill 压缩包](0024-managed-skill-package-import.md)替代 classpath-only Skill 来源；[0025：管理员管理可移植的 MCP 连接与加密认证](0025-managed-mcp-connections.md)替代部署侧静态 MCP 来源和“不保存 MCP secret”的临时限制；其余 Agent、Tool、MCP 与审计边界继续有效。

## 背景

当前文字与语音对话都直接使用 Spring AI `ChatClient`。该链路可以完成模型问答，但没有
Agent 循环、Tool 注册、Skill 按需加载或 MCP Tool 适配，因此后续提醒查询、设备状态解释
和经确认的语音操作会各自形成独立解析逻辑。

Spring AI Alibaba Agent Framework 1.1.2.2 提供 `ReactAgent`、`SkillsAgentHook`、
`ToolCallLimitHook` 和 Spring AI `ToolCallback` 兼容层。项目继续保留现有 OpenAI-compatible
模型配置与语音供应商适配，不绑定 DashScope ChatModel。

## 决策

1. 文字与设备语音共享一个 `AgentOrchestrator`。启用时由 Spring AI Alibaba
   `ReactAgent` 执行 ReAct 循环；关闭或 Agent 在产生答案前失败时回退原有 `ChatClient`
   普通聊天链路。
2. Agent 每次调用重新绑定本回合的会话和设备范围。模型不能提供或覆盖设备 ID、会话 ID、
   管理员身份、权限列表或 MCP 连接信息。
3. 第一版只注册只读 Tool。每回合最多调用 4 次 Tool，总预算 20 秒，单次 Tool 结果最多
   8 KiB，全部结果最多 24 KiB；Tool 输入、结果正文、对话正文和认证信息不得进入审计或日志。
4. 初版 Skill 从应用 classpath 加载；Skill 不执行脚本、不读取任意路径，也不继承运行 Codex 的
   Skill。Skill 来源与完整包生命周期现由 ADR 0024 替代。
5. 使用 Spring AI MCP Client 的 Streamable HTTP transport。STDIO 与旧 SSE transport 在应用中
   显式排除；MCP resources、prompts、sampling、roots 和 elicitation 不暴露给 Agent。
6. MCP 连接由启动配置提供，发现不代表授权。管理员必须按“连接名/原始 Tool 名”启用；服务端
   同时保存启用时的输入 schema SHA-256。连接身份或 schema 变化后，该 Tool 自动停止进入 Agent。
7. 全局紧急开关、内置 Tool、Skill 与 MCP Tool 的启用状态存入 PostgreSQL。MCP endpoint 和
   认证配置不会传给模型；本版本不在网页中接收或保存 MCP secret。
8. 每次 Tool 调用只审计回合 ID、会话/设备范围、Skill、Tool、来源、MCP 连接、结果分类、
   耗时、字节数与是否截断。审计失败不得泄漏内容，也不得改变只读 Tool 的结果。

## 结果

- 后续安全语音操作可以复用同一 Agent 与权限边界，但副作用仍必须经过独立的结构化提案、
  服务端复核和用户确认，不能把本 ADR 的只读授权扩大为直接写入。
- 使用不支持 Tool Calling 的兼容模型时仍可聊天，只是不能使用 Skill/Tool/MCP。
- MCP server 的新增、认证头安全存储和动态连接生命周期仍需独立迭代；在此之前由部署配置和
  生产 HTTPS 校验保护连接边界。
