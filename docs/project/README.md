# 项目文档约定

当前任务：[设备交互与本地人脸跟随迭代](device-interaction-upgrade.md)。用户已确认r2功能可用，本轮查阅开源资料后完成[小屏菜单与动作修订](device-visual-polish.md)，`device008-ui-r3-b872f2f` 已按持续许可应用OTA安装，新版心跳/校准/DISABLED及服务健康通过。真实菜单和动画回归完成，300秒采集活跃待机完成率60.544FPS；录音/讲话无新实测窗口；用户已反馈当前版基本没问题并授权推送当前任务分支。实际指标与限制以[全局总览](status/overview.md)为准。旧r2及原恢复点保持，不用旧帧率代表新二进制。

上一任务COMPANION-007已合入 `origin/master@effa511`；历史见[记录](../runbooks/companion-interaction-20261001.md)。现行服务器/控制台a840d0d/V56，设备device008-ui-r3-b872f2f。用户已明确允许本次候选，并表示“后面你都可以直接安装，不用问我”，对本项目后续固件安装给予持续许可。后续软件及来源检查通过后可直接安装，不重复询问；仍保持现有数据保留、安全及外部推送边界。Agent没有相机/实体动作开启许可；本次已获仅推送当前任务分支的明确许可，PR创建/审核/合并仍由用户执行。

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
