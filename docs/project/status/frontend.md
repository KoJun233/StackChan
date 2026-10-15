# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r02-project-loop-20261015`
- 基准提交：`a961816`
- 最后验证提交：`a961816`

## 当前目标

在既有持续委托列表、详情和表单完成 R02 待阅成果、明确认可、偏好修订与操作回查；保留设备质量汇总和原有权限、路由合同。原 R01 等待合并状态已 STALE；PR #49 已合入当前基准。

## 已完成

列表通过独立接口获取跨分页的最多三张待阅卡；详情先展示五区成果及输入/实际来源与读取时间，再展示原结果和历史。旧记录缺审阅字段时只读，旧版成果明确标记。

反馈明确绑定 run、任务修订与审阅版本；可认可、修改偏好、只保存或保存并重新比较、暂停与取消。写入结果未知保留原 UUID 键，先查收据再读取资源，不盲目换键重发。切换伙伴丢弃旧响应；离线保留已有成果并提示状态。

只扩展既有委托页面与 API 模块，没有新增路由或依赖。原用户工作树未改，自动生成类型文件仅在任务树恢复。

## 正在进行

组件、类型与 lint 验证已完成，等待代码审核；生产候选由同提交 archive 构建并封存收据。360/1280px 布局、键盘视觉焦点与截图保持 VISUAL_PENDING，不用组件测试充当视觉证据。

## 下一步操作

核对候选收据中的全部控制台资源、866 项已测源码指纹及最终任务提交，再本地审核页面差异；视觉验收按 [R02 交接](../baselines/companion-r02-project-loop.md)执行。

## 阻塞项

无已知软件阻塞。Browser 没有可用实例；Computer Use 因 Windows 无法可靠识别当前浏览器 URL 而停止，未取得截图。本轮任务分支推送已授权，安装或真实模型/Bark 仍未授权，真实来源和日用效果仍待验。

## 关键文件

委托 API 模块、`views/companion/delegations` 下的 list、detail、helpers、`DetailForm` 与新 `ProjectCard`；完整路径与 FR/AC 对照见 [R02 交付](../baselines/companion-r02-project-loop.md)。

## 验证命令与最近结果

完整 Vitest 46 文件 204/204、零失败，报告 `.tools/r02/frontend-full-tests.log`；`vue-tsc -b` 通过，报告 `.tools/r02/frontend-typecheck.log`。十文件 ESLint、四文件 Stylelint 通过，报告 `.tools/r02/frontend-lint.log`；生产构建与全部静态文件核验结果见候选收据。既有 Vite/Vitest 退出警告保留。

本机 Node24.19.0/pnpm11.25.0 仅执行已安装脚本，Vitest 直接用 Node 启动以避开 junction 状态限制；生产候选使用缓存 Node24.15.0/pnpm11.13.1，锁文件与 engine 保持。最终执行 `git diff --check`、`pnpm docs:check`。

## 相关设计、计划和决策

[R02 交付与 FR/AC](../baselines/companion-r02-project-loop.md)、[R02 规格](../../requirements/companion-v1/R02-project-loop.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[ADR0078](../decisions/0078-versioned-readonly-project-loop.md)、[委托与 Bark 手册](../runbooks/recurring-research-and-bark.md)、[R01 历史交付](../baselines/companion-r01-natural-voice.md)。

## 安全与兼容性约束

2026-10-15 用户已明确授权只推当前 R02 任务分支；推送及远端 SHA 核对结果见 `.tools/r02/push-receipt.json`。不创建 PR、不安装或刷写，不调用真实模型/Bark，不切换部署模式或轮换凭据。UUID 不授予设备或管理员身份；维持现有会话、CSRF 与范围检查。保留原用户改动，页面与报告不泄露秘密或完整认证载荷。
