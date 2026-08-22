# StackChan 自定义 AI 陪伴机器人

StackChan 是一个可自托管的 AI 陪伴机器人项目，包含 M5Stack 设备固件、Java 服务端、Fantastic-admin 管理前端和本地服务器部署配置。对话能力基于 Spring AI 与 Spring AI Alibaba `ReactAgent`，模型供应商、`apiKey`、`baseUrl` 和模型名称仍由用户配置；阿里云百炼语音能力由项目内适配层接入。

## 当前能力

- 管理员登录与密码轮换。
- LLM 配置，以及文字/语音共用的受控 ReactAgent 对话链路。
- 管理员导入且默认停用的完整 Skill ZIP、两个已验证只读 Tool、页面管理且加密认证的 Streamable HTTP MCP、调用预算和隐私安全审计。
- 设备端本地唤醒、语音识别、完整回答合成与播放闭环。
- 默认关闭、仅在本地 VAD 命中后上传且受三回合/两分钟上限约束的连续对话。
- 对话持久化、失败恢复、重试幂等和隐私安全的阶段诊断。
- 管理员对话搜索、范围导出、单条/整段物理删除，以及 7 日/4 周 PostgreSQL 备份与隔离恢复验证。
- 设备配对、JWT 凭据、WebSocket 心跳、触摸取消和停止动作命令。
- 严格隔离的多角色容器、可选角色 TTS 音色、经敏感过滤且确认后生效的长期记忆建议、有界相关检索、单次/周期提醒和有限主动问候。
- 160×160 原生动态球形表情、12 种角色情绪、分层交互/生命周期动作，以及兼容的八状态 PNG 资源包。
- LAN HTTP 开发固件与 HTTPS-only 生产部署边界。

## 仓库结构

- `server/`：Java 21 / Spring Boot 服务端。
- `apps/stackchan-console/`：Vue 3 / Fantastic-admin 管理前端。
- `firmware/`：ESP-IDF 设备固件。
- `docs/`：项目文档、协议、运行手册、设计和计划。
- `scripts/`：验证、部署和固件辅助脚本。
- `compose*.yaml`：基础、LAN 开发和生产部署配置。

## 新会话入口

Agent 先读取 [`AGENTS.md`](AGENTS.md)，人类开发者和 Agent 再进入 [`docs/project/README.md`](docs/project/README.md)。当前进度以 `docs/project/status/` 为准；下一阶段的任务边界、依赖和验收条件见 [`docs/project/todo.md`](docs/project/todo.md)，不以历史聊天或临时工作记录为准。

## 参与开发

提交信息、任务分支、单提交 PR 和人工审核要求见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。除空仓库首次初始化外，`master` 不接受直接提交或推送。

## 快速验证

```powershell
pnpm docs:check
& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test
pnpm --filter @stackchan/console run test
pnpm --filter @stackchan/console run build
```

详细环境、固件和部署命令见 [`docs/project/development.md`](docs/project/development.md)。
个人数据备份布局、轮转和恢复演练见 [`docs/runbooks/personal-data-backup.md`](docs/runbooks/personal-data-backup.md)。

## 安全提示

不要把真实密码、API Key、Token、配对码或热点凭据提交到仓库。`compose.lan.yaml` 只用于受信任局域网开发，生产必须遵守 [`docs/runbooks/secure-deployment.md`](docs/runbooks/secure-deployment.md)。
