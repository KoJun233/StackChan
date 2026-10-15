# 服务端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r01-natural-voice-20261013`
- 基准提交：`989b3d9`
- 最后验证提交：`989b3d9`

## 当前目标

R01 自然语音：截断先澄清、当前表达优先、有界续讲、真实短聊路由和安全诊断；保留 R00 V57/网页绑定及主线委托/Bark。

## 已完成

截断录音先确定性澄清，不调用模型、工具或记忆提取；正文含标点 ≤160 码点，一次 TTS/单 WAV。最多四轮成功配对/三十分钟，换题/纠正切旧上下文，无唯一对象追问；拒绝不提取记忆。Bark 明确意图/缺正文/只读能力与 UNKNOWN 不重发保持。

管理员质量接口默认 24 小时/最多七天，只给固定计数/阶段时长，旧固件可选字段接受、缺证据未知。普通短聊来自实际 route；三十有效样本才给 nearest-rank，DEVICE_MONOTONIC 是 codec 开始参考，实体首声独立。

仅新增 V61 会话关联/七日匿名墓碑；删除与阶段共享事务咨询锁，晚到不重建，旧未关联诊断不回填。V1–V60/checksum 不变，直接 60→61 保留十二表及五项加密摘要。

## 正在进行

软件完成，等待审核；实体与安装待验。

最终完整 SHA、测试源码指纹、61 SQL/Flyway、生产 image/JAR/全部控制台资源及双固件六制品见本工作树 `.tools/r01/candidate-receipt.json`。989b3d9 为当前祖先元数据，新增代码成绩以工作树回归和最终候选收据为准。实现、状态、交接与 R00 合成一个中文任务提交；用户已于 2026-10-15 明确授权推送本任务分支；推送与远端 SHA 核对结果见 `.tools/r01/push-receipt.json`。PR、人工审核与合并由用户执行。

## 下一步操作

读取本交付和收据进行代码审核；用户明确安装后才按测量手册采集。按本次授权推送并核对远端，随后由用户创建 PR，后续 R02/R06 不自动实施。

## 阻塞项

无软件阻塞。本轮分支推送已授权，刷写或安装尚未授权；实体样本不足保持 UNKNOWN。缺 V57 的既有 V60 库仍默认拒绝升级，需另行兼容方案。

## 关键文件

实现路径与 FR/AC 对照见下方交付记录，最终源码指纹见收据。

## 验证命令与最近结果

`./scripts/test-server-linux.ps1` 1009/1009，失败/错误/跳过 0，BUILD SUCCESS，报告 `.tools/stackchan-companion-tests-6d25eb52a1dd497b82e98b20bcd2268c`。诊断/API 67/67、PG 十项 R01 + 五项 R00 15/15；完整回归覆盖最终源码。已有 Surefire 退出三十秒警告保留。

`git diff --check`、`pnpm docs:check` 与检查器 7/7；最终源码/JAR/61 条迁移及真实 Flyway 11.7.2 核验封存收据。

## 相关设计、计划和决策

[R01 交付与 FR/AC](../baselines/companion-r01-natural-voice.md)、[R00 历史基线](../baselines/companion-v1-baseline.md)、[R01 规格](../../requirements/companion-v1/R01-natural-voice.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[ADR0077](../decisions/0077-measured-private-natural-voice.md)、[实体测量](../../runbooks/natural-voice-quality.md)、[备份恢复](../../runbooks/personal-data-backup.md)。

## 安全与兼容性约束

不运行真实模型/Bark、不刷写、安装、改部署模式、轮换凭据或创建 PR；仅按本次授权推送当前任务分支。原用户工作树保持；实体录音/谈话/认证载荷不提交，LAN HTTP 仅局域网开发。
