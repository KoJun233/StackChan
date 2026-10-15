# 项目文档约定

当前任务：[成人桌面伙伴 V1 R02 一次项目闭环](baselines/companion-r02-project-loop.md)。用户确认 PR #49 合并后要求继续下一项，已从最新 master@a961816 创建独立分支；R00/R01 已合入，R03–R11 未实施。当前进展与软件/人工证据边界见[总览](status/overview.md)，需求入口见[十二项需求](../requirements/companion-v1/README.md)。2026-10-15 用户已明确授权推送当前 R02 分支；实际推送与远端核对结果见本任务 `.tools/r02/push-receipt.json`，PR、审核与合并由用户操作。

历史最近只读核查为 2026-10-12 的 a37d220/V60、原 r3/motion_disabled，本轮未再访问运行环境；R01 V61/双固件与 R02 V62 候选均不能按开发完成推断已安装。刷写、真实外发、安装待独立授权。历史 LAN 入口为 http://192.168.1.5:8080；恢复点与匹配旧镜像保留，发布须先停写一致备份与内部隔离恢复。

## 稳定文档与状态文档

稳定文档记录长期有效的架构、开发环境、路线图、协议、决策和 runbook；它们不记录频繁变化的当前任务。状态文档只记录当前目标、进度、阻塞、下一条精确操作和最近验证，不再累积每轮 Agent 的部署流水。已完成任务只在[里程碑索引](milestones.md)保留摘要，详细证据由 Git、ADR 和 runbook 保存。Git 跟踪的 `docs/project/status/` 是跨会话当前状态的正式事实来源，临时工作记录和历史聊天不是。

## 工作流状态

- `ACTIVE`：正在实现。
- `BLOCKED`：存在明确阻塞。
- `READY`：前置条件完成，可以开始下一任务。
- `READY_FOR_REVIEW`：实现与自动化验证完成，等待人工审核、合入或另行授权的实体/部署验收。
- `STABLE`：当前能力已经验证，没有正在进行的修改。
- `STALE`：文档与仓库事实不一致，必须先恢复状态。

## 文档生命周期

- `DRAFT`：尚未确认。
- `ACTIVE`：当前开发依据。
- `COMPLETED`：计划已经执行完成，保留作历史证据。
- `SUPERSEDED`：已被新文档替代，必须链接替代文档。
- `REFERENCE`：长期有效的协议、运行手册或架构说明。

## 必读顺序

1. [`../../AGENTS.md`](../../AGENTS.md)
2. [`../../README.md`](../../README.md)
3. `status/overview.md`
4. 任务所属的工作流状态文件。
5. 状态文件链接的当前设计稿和实施计划。
6. 相关决策、协议和 runbook。

准备开始新任务时，再读取[下一阶段可执行任务清单](todo.md)，选择执行看板中第一项 `READY` 任务。任务清单不替代状态文件；发生冲突时先按 Git 事实修正状态文件。

## 稳定项目文档

- [BODY-002 五种 K151 身体动作的完成与交接计划](body-motion-completion-plan.md)
- [2026-09-19 前端体验排查与改造建议（DRAFT）](console-ux-audit-2026-09-19.md)
- [架构说明](architecture.md)
- [开发环境与命令](development.md)
- [产品路线图](roadmap.md)
- [2026-09-13 桌面机器人功能评审报告](feature-review-2026-09-13.md)
- [工作日桌面陪伴 V1 开发设计](workday-companion-v1.md)
- [WORK-002 私用观察与门槛可观测性](workday-pilot-observability.md)
- [WORK-003 观察结果一次性通知](workday-pilot-completion-notification.md)
- [WORK-004 本地个人待办闭环](personal-task-management.md)
- [WORK-005 工作日首次简报纳入个人待办](workday-personal-task-brief.md)
- [WORK-006 今日个人待办进度查询](workday-daily-task-progress.md)
- [下一阶段可执行任务清单](todo.md)
- [已完成里程碑](milestones.md)
- [架构决策记录](decisions/README.md)
