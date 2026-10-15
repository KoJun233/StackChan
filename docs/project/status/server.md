# 服务端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r02-project-loop-20261015`
- 基准提交：`a961816`
- 最后验证提交：`a961816`

## 当前目标

完成 R02 一次只读项目的收单、执行、成果审阅与原任务修订，保留已合入 R00/R01、周期委托和旧接口的行为。原等待 R01 合并的记录已 STALE；PR #49 合并事实已纳入本轮基准。

## 已完成

明确 ONCE 项目使用认证 device/role 和稳定意图键；咨询、示例与拒绝不触发读取。来源权限由用户指令独立保存，给定 URL 每次工具调用前校验，纯文本显式空工具集不继承默认联网能力。认证 query 拒绝，原读取器保持拒绝所有 3xx。

五区成果与来源保存于原 run，未读事实为未知，缺关键证据转 BLOCKED。审阅与执行状态分开；认可绑定所见 run，偏好增加原 task revision 并保留旧成果。修订执行唯一槽、版本冲突、容量失败明确重试及取消后旧入口守卫已实现。

V62 新增审阅、反馈、修订意图及操作收据。COMMITTED 与业务意图同事务，UNKNOWN 先回查旧键；删除私人任务清正文与审阅，收据只留无正文墓碑。API 使用管理员会话、CSRF 与归属检查；旧周期任务仍用原调度和 occurrence。

## 正在进行

软件实现和完整回归已完成，等待代码审核；真实项目与视觉验收分别待验。a961816 为已验证祖先元数据；新增实现的源码指纹、最终提交与测试报告将由本轮候选收据绑定，不沿用 R01 测试数字证明新代码。

## 下一步操作

核对本地单一中文任务提交、R02 交付及候选收据，确认 image/JAR/62 SQL 与已测源码对应同一提交后审核。按 2026-10-15 新授权仅推当前任务分支；PR 创建与合并由用户执行。

## 阻塞项

无软件阻塞。本轮任务分支推送已授权；安装、刷写及真实模型/Bark 仍未授权。真实页面、实体语音与隔日验收不由合成或 PG 测试替代；缺 V57 的既有 V60 库仍默认拒绝升级。

## 关键文件

`DelegationService`、`OperationReceiptService`、`DelegationConversationIntake`、`AgentDelegationTaskExecutor`、`PublicSourceScope`、`ProjectArtifactRenderer`、管理员委托 API 与 V62；完整路径和 FR/AC 对照见 [R02 交付](../baselines/companion-r02-project-loop.md)。

## 验证命令与最近结果

`./scripts/test-server-linux.ps1` 完整 1092/1092，失败/错误/跳过 0，BUILD SUCCESS；报告 `.tools/stackchan-companion-tests-59f71ccde7e4454abf67f34596ff94ba`。其中项目事务 17/17、迁移 6/6，覆盖调度与手动同修订去重、暂停恢复、周期转换、撤销手机，以及 V61→V62 和隔离 pg_dump/pg_restore。定向兼容回归 63/63。已有 Surefire 三十秒退出警告保留。

`git diff --check`、`pnpm docs:check` 和检查器 7/7 通过。测试源码 720 项指纹与候选最终源码一致性由收据再次核验；候选还核对 V1–V61 原 SQL、62 条打包 SQL 与真实 Flyway checksum。

## 相关设计、计划和决策

[R02 交付与 FR/AC](../baselines/companion-r02-project-loop.md)、[R02 规格](../../requirements/companion-v1/R02-project-loop.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[ADR0078](../decisions/0078-versioned-readonly-project-loop.md)、[委托与 Bark 手册](../runbooks/recurring-research-and-bark.md)、[备份恢复](../../runbooks/personal-data-backup.md)、[R01 历史交付](../baselines/companion-r01-natural-voice.md)。

## 安全与兼容性约束

2026-10-15 用户已明确授权只推当前 R02 任务分支；推送及远端 SHA 核对结果见 `.tools/r02/push-receipt.json`。不创建 PR、不安装或刷写，不调用真实模型/Bark，不切换部署模式或轮换凭据。保留用户原工作树；文档与收据不保存秘密、完整认证载荷或私人项目正文。LAN HTTP 仅用于局域网开发，生产保持 HTTPS。
