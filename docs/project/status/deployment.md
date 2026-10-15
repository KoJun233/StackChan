# 部署工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r02-project-loop-20261015`
- 基准提交：`a961816`
- 最后验证提交：`a961816`

## 当前目标

准备绑定 R02 单一任务提交的服务端与控制台生产候选，核验 V62 迁移和完整资源来源。本轮完成本地构建与隔离验证；2026-10-15 用户已授权只推当前任务分支，安装和刷写仍未授权。

## 已完成

R00/R01 已通过 PR #49 合入当前基准，旧等待推送/合并记录已 STALE。R02 仅新增 V62；设计要求 V1–V61 原样保留，迁移测试覆盖 V61 数据升级与隔离 pg_dump/pg_restore。没有访问真实运行库或修改恢复点。

`scripts/build-companion-candidate.ps1` 从已提交 archive 构建，候选须校验 image OCI revision、JAR、62 SQL 与全部控制台静态资源；本轮固件未变，不把 R01 历史固件制品冒充本轮重新构建。

## 正在进行

软件完整回归已通过。已提交的单一任务提交作为候选来源；随后执行生产构建、62 条实际 Flyway checksum 与资源核验，其实际结果封存在 `.tools/r02/candidate-receipt.json`。收据缺失或任一门禁失败时，不得安装或宣称候选验证通过。a961816 为祖先元数据，最终任务 SHA 与测试源码指纹由候选收据绑定。

## 下一步操作

核对本地单一中文任务提交与候选收据，确认 `./scripts/build-companion-candidate.ps1` 及 image/JAR/62 SQL/全部控制台文件门禁通过，再审核。按用户本次明确授权仅推当前任务分支并核对远端 SHA，PR 创建与合并由用户执行。

若后续授权安装，先停写备份与内部隔离恢复，再使用匹配 V62 候选。回退使用匹配数据库备份与旧镜像，不能 repair/out-of-order、改写历史迁移或直接让旧应用连接新库。

## 阻塞项

无软件阻塞；候选是否通过严格以完整收据为准。本轮任务分支推送已授权；安装、刷写、真实模型/Bark 与部署模式切换仍未授权。缺 V57 的既有 V60 库仍默认拒绝升级，不能隐式补迁移。

## 关键文件

`scripts/build-companion-candidate.ps1`、V62、[R02 交付](../baselines/companion-r02-project-loop.md)及本任务 `.tools/r02` 的测试/构建/候选核验记录；固件历史证据在 R01 原任务工作树保留。

## 验证命令与最近结果

控制台 46 文件 204/204 与 `vue-tsc -b` 已通过；完整服务端 1092/1092、项目事务 17/17、迁移恢复 6/6、类型及 lint 通过。候选构建和核验完成后，实际日志、摘要、62 条 Flyway 结果与源码指纹记录到收据；不复用 R01 的候选 SHA 或成绩替代本轮结果。

本轮未读取线上健康状态。最后历史只读核查为 2026-10-12 的 a37d220/V60/r3/motion_disabled，不能当作今天的新鲜状态；恢复点 20261011152920 与匹配旧镜像未操作。

## 相关设计、计划和决策

[R02 交付与 FR/AC](../baselines/companion-r02-project-loop.md)、[R02 规格](../../requirements/companion-v1/R02-project-loop.md)、[ADR0078](../decisions/0078-versioned-readonly-project-loop.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[备份恢复](../../runbooks/personal-data-backup.md)、[R01 历史交付](../baselines/companion-r01-natural-voice.md)。

## 安全与兼容性约束

2026-10-15 用户已明确授权只推当前 R02 任务分支；推送及远端 SHA 核对结果见 `.tools/r02/push-receipt.json`。不创建 PR、不安装或刷写，不调用真实模型/Bark，不改部署模式或轮换凭据。原用户工作树及历史恢复点保持；候选清单不记录秘密或完整认证载荷。生产 HTTPS 边界保持，LAN HTTP 仅局域网开发。
