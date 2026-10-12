# 部署工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r01-natural-voice-20261013`
- 基准提交：`989b3d9`
- 最后验证提交：`989b3d9`

## 当前目标

交付绑定 R00/R01 合并提交的生产候选与双固件；按 2026-10-15 授权只推任务分支，安装待验。

## 已完成

`build-companion-candidate.ps1` 从已提交 archive 构建，固定兼容依赖缓存并校验源码依赖树；image OCI revision、JAR、V1–V61 和全部静态资源严格关联。原六十条 SQL/checksum 保持，只有 V61 新增；Windows archive CRLF 与 canonical Git LF 的差异仅允许换行归一化，archive/JAR 原字节相同，实际 Flyway 11.7.2 六十一条核验。

双固件版本/十二制品绑定同 HEAD，未安装。原 R00 候选保留于其原工作树；本候选收据位于 `.tools/r01`。

## 正在进行

软件完成，等待审核；实体与安装待验。

最终完整 SHA、测试源码指纹、61 SQL/Flyway、生产 image/JAR/全部控制台资源及双固件六制品见本工作树 `.tools/r01/candidate-receipt.json`。989b3d9 为当前祖先元数据，新增代码成绩以工作树回归和最终候选收据为准。实现、状态、交接与 R00 合成一个中文任务提交；用户已于 2026-10-15 明确授权推送本任务分支；推送与远端 SHA 核对结果见 `.tools/r01/push-receipt.json`。PR、人工审核与合并由用户执行。

## 下一步操作

按用户 2026-10-15 明确授权仅推当前任务分支；PR/人工审核/合并由用户完成。若授权安装，先停写备份并内部隔离恢复，使用匹配 V61 候选；回退使用匹配备份与旧镜像，不 repair/out-of-order 或直接把旧程序接新库。

## 阻塞项

无软件阻塞。本轮分支推送已授权，刷写或安装尚未授权；实体样本不足保持 UNKNOWN。缺 V57 的既有 V60 库仍默认拒绝升级，需另行兼容方案。

## 关键文件

实现路径与 FR/AC 对照见下方交付记录，最终源码指纹见收据。

## 验证命令与最近结果

服务端 1009/1009、控制台 181/181、PG 15/15、生产构建/制品核验、双固件/五栈门槛、docs:check/7 项检查器/diff 通过，完整 SHA 与摘要以外置收据为准。

本轮没有读取线上健康状态。最后历史只读核查为 2026-10-12 的 a37d220/V60/r3/motion_disabled，不能当今天新鲜状态；恢复点 20261011152920 与匹配旧镜像未操作。

## 相关设计、计划和决策

[R01 交付与 FR/AC](../baselines/companion-r01-natural-voice.md)、[R00 历史基线](../baselines/companion-v1-baseline.md)、[R01 规格](../../requirements/companion-v1/R01-natural-voice.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[ADR0077](../decisions/0077-measured-private-natural-voice.md)、[实体测量](../../runbooks/natural-voice-quality.md)、[备份恢复](../../runbooks/personal-data-backup.md)。

## 安全与兼容性约束

不运行真实模型/Bark、不刷写、安装、改部署模式、轮换凭据或创建 PR；仅按本次授权推送当前任务分支。原用户工作树保持；实体录音/谈话/认证载荷不提交，LAN HTTP 仅局域网开发。
