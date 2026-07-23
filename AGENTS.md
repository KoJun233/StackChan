# StackChan Agent Development Contract

## 必读顺序

1. 读取 `README.md`。
2. 读取 `docs/project/README.md`。
3. 读取 `docs/project/status/overview.md`。
4. 读取任务所属的工作流状态文件。
5. 读取状态文件链接的设计、计划、决策和 runbook。

## 开始任务前

- 运行 `git status --short --branch` 和 `git log -5 --oneline`。
- 在运行 `pnpm docs:check` 前，如 `node` 或 `pnpm` 缺失，必须遵循[开发环境中的 Node 与 pnpm 引导](docs/project/development.md#node-与-pnpm-引导)；不得为此修改仓库的 Node engine 约束。
- 识别用户或其他工作流已有的未提交改动，不覆盖、不暂存、不清理。
- 确认状态文件中的基准提交和最后验证提交存在于当前分支。
- 运行 `git diff --check` 和 `pnpm docs:check`。
- 运行该工作流记录的最小验证命令。

## 工作流状态

- 全局总览：[docs/project/status/overview.md](docs/project/status/overview.md)
- 服务端：[docs/project/status/server.md](docs/project/status/server.md)
- 前端：[docs/project/status/frontend.md](docs/project/status/frontend.md)
- 固件：[docs/project/status/firmware.md](docs/project/status/firmware.md)
- 部署：[docs/project/status/deployment.md](docs/project/status/deployment.md)
- 交接模板：[docs/project/templates/handoff.md](docs/project/templates/handoff.md)
- ADR 模板：[docs/project/templates/decision.md](docs/project/templates/decision.md)

## 完成、暂停或阻塞时

- 先完成实现和验证。
- 更新对应工作流状态和全局总览。
- 写明完成内容、未完成内容、下一条精确操作、阻塞条件和验证结果。
- 实现、状态更新和交接记录必须在推送任务分支前压缩到同一个任务提交中，不保留独立交接提交。
- 运行 `git diff --check` 和 `pnpm docs:check`。

## 提交与合并约束

- 每个任务必须从最新 `master` 创建独立任务分支；除空仓库首次初始化外，禁止直接在 `master` 上提交或推送。
- 提交信息必须使用中文。Conventional Commit 的类型前缀（如 `feat:`、`fix:`、`docs:`）可以保留英文，但标题说明和正文必须使用中文。
- 开发过程中可以产生多个临时提交，但推送前必须将任务分支相对 `master` 的全部改动压缩为恰好一个中文任务提交，并重新完成该提交的验证。
- 经用户明确允许外部推送后，Agent 只推送当前任务分支；不创建 PR、不合并 PR，也不直接推送 `master`。
- PR 创建、人工审核和 GitHub 页面上的最终合并均由用户执行。
- `master` 禁止 force push。任务分支因压缩提交需要改写远端历史时，只允许使用 `git push --force-with-lease`。

## 安全边界

- 禁止在文档、日志和提交中写入秘密或完整认证载荷。
- 未经用户明确确认，不刷写固件、不切换部署模式、不轮换凭据、不执行外部推送。
- LAN HTTP 只用于局域网开发；生产部署必须保持 HTTPS 边界。

## 事实冲突

当文档与仓库冲突时，以 Git、代码和新鲜验证结果为准。先把状态标记为 `STALE` 并修正文档，再继续开发。
