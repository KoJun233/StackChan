# StackChan 自定义 AI 陪伴机器人

StackChan 是一个可自托管的 AI 陪伴机器人项目，包含 M5Stack 设备固件、Java 服务端、Fantastic-admin 管理前端和本地服务器部署配置。模型供应商、`apiKey`、`baseUrl` 和模型名称由用户配置，长期目标包括语音对话、记忆、主动关心和身体表达。

## 当前能力

- 管理员登录与密码轮换。
- LLM 配置和基于 Spring AI Alibaba 的流式文字聊天。
- 对话持久化、失败恢复和重试幂等。
- 设备配对、JWT 凭据、WebSocket 心跳和停止动作命令。
- LAN HTTP 开发固件与 HTTPS-only 生产部署边界。

## 仓库结构

- `server/`：Java 21 / Spring Boot 服务端。
- `apps/stackchan-console/`：Vue 3 / Fantastic-admin 管理前端。
- `firmware/`：ESP-IDF 设备固件。
- `docs/`：项目文档、协议、运行手册、设计和计划。
- `scripts/`：验证、部署和固件辅助脚本。
- `compose*.yaml`：基础、LAN 开发和生产部署配置。

## 新会话入口

Agent 先读取 [`AGENTS.md`](AGENTS.md)，人类开发者和 Agent 再进入 [`docs/project/README.md`](docs/project/README.md)。当前进度以 `docs/project/status/` 为准，不以历史聊天或临时工作记录为准。

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

## 安全提示

不要把真实密码、API Key、Token、配对码或热点凭据提交到仓库。`compose.lan.yaml` 只用于受信任局域网开发，生产必须遵守 [`docs/runbooks/secure-deployment.md`](docs/runbooks/secure-deployment.md)。
