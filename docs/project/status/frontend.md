# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r01-natural-voice-20261013`
- 基准提交：`989b3d9`
- 最后验证提交：`989b3d9`

## 当前目标

在现有设备详情展示质量汇总、来源、样本/失败/缺失和未知；保留 R00 记忆与委托/Bark控制台。

## 已完成

设备回合详情与质量汇总分别加载、分别显示错误；切设备的旧请求不覆盖新结果。实际 codec 开始与端点标记才计算参考耗时，旧 marker/缺阶段/倒序保持未知。显示实际短聊分组、排除数量、固定失败码和未结束数量；少于三十样本不给百分位。单调计时与实体首声分别说明，无正文、录音或认证值。

没有新路由、表单或配置，既有设备选择/权限合同保持。

## 正在进行

软件完成，等待审核；实体与安装待验。

最终完整 SHA、测试源码指纹、61 SQL/Flyway、生产 image/JAR/全部控制台资源及双固件六制品见本工作树 `.tools/r01/candidate-receipt.json`。989b3d9 为当前祖先元数据，新增代码成绩以工作树回归和最终候选收据为准。实现、状态、交接与 R00 合成一个中文任务提交；用户已于 2026-10-15 明确授权推送本任务分支；推送与远端 SHA 核对结果见 `.tools/r01/push-receipt.json`。PR、人工审核与合并由用户执行。

## 下一步操作

读取交付/收据审核详情与汇总；安装授权及一致恢复后再验证真实设备数据、失败/缺失标签，不以模拟样本作达标。

## 阻塞项

无软件阻塞。本轮分支推送已授权，刷写或安装尚未授权；实体样本不足保持 UNKNOWN。缺 V57 的既有 V60 库仍默认拒绝升级，需另行兼容方案。

## 关键文件

实现路径与 FR/AC 对照见下方交付记录，最终源码指纹见收据。

## 验证命令与最近结果

定向 20/20；完整 46 文件 181/181，零失败，`.tools/r01/frontend-full-tests.log`。vue-tsc -b、六文件 ESLint/Stylelint 通过，生产候选构建成功。已有 Vite/Vitest 退出与大 chunk 警告保留；三项自动生成 d.ts 在任务树恢复，未改用户原树。

本机 Node24.19.0/pnpm11.25.0 只执行已装脚本；完整 Vitest 直接由 Node 启动以避开 junction 运行状态限制。生产缓存 Node24.15.0/pnpm11.13.1，锁文件/engine 保持。

## 相关设计、计划和决策

[R01 交付与 FR/AC](../baselines/companion-r01-natural-voice.md)、[R00 历史基线](../baselines/companion-v1-baseline.md)、[R01 规格](../../requirements/companion-v1/R01-natural-voice.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[ADR0077](../decisions/0077-measured-private-natural-voice.md)、[实体测量](../../runbooks/natural-voice-quality.md)、[备份恢复](../../runbooks/personal-data-backup.md)。

## 安全与兼容性约束

不运行真实模型/Bark、不刷写、安装、改部署模式、轮换凭据或创建 PR；仅按本次授权推送当前任务分支。原用户工作树保持；实体录音/谈话/认证载荷不提交，LAN HTTP 仅局域网开发。
