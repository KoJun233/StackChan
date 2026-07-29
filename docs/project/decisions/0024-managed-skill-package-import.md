# 0024：管理员导入受控的完整 Skill 压缩包

- 状态：ACCEPTED
- 日期：2026-07-29
- 关联：[0023：受控 ReactAgent、Skill、Tool 与 MCP](0023-controlled-react-agent-skills-tools-mcp.md)

## 背景

ADR 0023 第一版把 Skill 固定在应用 classpath，适合验证 `SkillsAgentHook`，但无法让管理员独立维护
自己的 Skill。实际 Skill 往往不只有 `SKILL.md`，还可能包含 `references/`、`examples/`、模板或其他
说明资源，因此单文件粘贴和容器临时目录都不能满足包生命周期。

## 决策

1. 管理员从“Agent 能力”页面上传一个 ZIP。包根目录可以直接包含 `SKILL.md`，也可以只有一个顶层
   Skill 目录；每个 ZIP 必须且只能识别出一个 Skill。
2. 服务端严格校验 Agent Skills frontmatter 中的 `name` 和 `description`，可读取 `version`；名称必须
   符合小写字母、数字和单连字符规范。重名导入拒绝，不静默覆盖。
3. 服务端拒绝绝对路径、`..`、盘符、NUL、重复路径、过深目录、符号链接、加密条目和其他非普通
   文件，并限制压缩包大小、文件数、单文件大小和总解压大小。失败时清理 staging，不留下数据库记录。
4. 完整 Skill 目录原子移入 `COMPANION_AGENT_SKILLS_DIRECTORY`；生产容器使用独立持久卷。PostgreSQL
   只保存名称、说明、版本、摘要、文件数、解压大小、启用状态和时间，不保存包正文。
5. Skill 导入后默认停用。管理员检查元数据和文件清单后才能启用；启用、停用和删除在下一回合立即
   生效。已启用 Skill 必须先停用再删除。
6. Spring AI Alibaba `FileSystemSkillRegistry` 只扫描上述受控目录，`FilteringSkillRegistry` 只向
   `SkillsAgentHook` 暴露数据库中已启用的 Skill。应用不读取用户 home、远程 URL 或任意路径。
7. Skill 只提供 `SKILL.md` 中的文字工作流。导入包不会授予 Shell、Python、任意文件系统、新 Tool、
   MCP 或业务写入权限；包内附属文件在当前版本被完整保存和展示，但不自动执行或注入模型上下文。
8. 第一版不提供在线替换。升级同名 Skill 时先停用并删除旧包，再导入和检查新包，避免无审计覆盖。

## 结果

- ADR 0023 的 ReactAgent、Tool/MCP 授权和隐私审计继续有效；其中“Skill 只从 classpath 加载”的决定
  被本 ADR 替代。
- 随应用发布的 `reminder-query`、`memory-query`、`device-status` 演示 Skill 及其三个查询 Tool 被删除；
  保留已实际验证的 `current_date_time` 和 `list_agent_capabilities`。
- 后续若要让 Skill 读取附属资源或执行脚本，必须新增独立 ADR 和最小权限 Tool，不能从本决策推导授权。
