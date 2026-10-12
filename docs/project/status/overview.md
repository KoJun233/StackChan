# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r01-natural-voice-20261013`
- 实现基准：`989b3d9`
- 最后验证提交：`989b3d9`

## 当前交付

用户要求 R01 开发，R00/R01 合成相对最新 master@989b3d9 一个中文任务提交；2026-10-15 明确授权只推当前任务分支。软件 READY_FOR_REVIEW，实体/安装待后续指令；没有自动开展 R02–R11。

R00 保留原 V57 与主线委托/Bark，网页设备范围仅服务端绑定。R01 完成限时澄清、当前表达优先、有界上下文、确定性拒绝、实际短聊路由、安全端点/codec 计时、质量汇总和 V61 隐私删除。原用户分支与未提交文件未覆盖或清理。

| 工作流 | 状态 | 新鲜结果 |
| --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | 完整 1009/1009；PG 15/15；新增 V61、旧六十迁移不变 |
| [前端](frontend.md) | READY_FOR_REVIEW | 完整 46 文件 181/181；定向 20/20、类型/lint/生产构建 |
| [固件](firmware.md) | READY_FOR_REVIEW | 双 profile/五栈 gate/宿主通过；LAN 余量 4432 B，未刷写 |
| [部署](deployment.md) | READY_FOR_REVIEW | 同提交 image/JAR/61 SQL/全部控制台/双固件收据，未安装 |
| [产品](product.md) | READY_FOR_REVIEW | 30 受控场景通过；匿名分析 23/23，现场样本 0、UNKNOWN |

## 验证与剩余边界

最终完整 SHA、测试源码指纹、61 SQL/Flyway、生产 image/JAR/全部控制台资源及双固件六制品见本工作树 `.tools/r01/candidate-receipt.json`。989b3d9 为当前祖先元数据，新增代码成绩以工作树回归和最终候选收据为准。实现、状态、交接与 R00 合成一个中文任务提交；用户已于 2026-10-15 明确授权推送本任务分支；推送与远端 SHA 核对结果见 `.tools/r01/push-receipt.json`。PR、人工审核与合并由用户执行。

完整服务端失败/错误/跳过 0，控制台失败 0。保留旧 Surefire/Vite/Vitest 退出及大 chunk 警告。固件总静态增量 248 B，3 MiB 槽余量 4432 B 高于 4384 门槛仅 48 B，后续改动必须重新验证容量。诊断的 codec 写入开始与逻辑状态接受是设备参考证据，不能冒充声学首声或 LCD 帧完成；旧设备缺字段仍未知。

FIELD_PENDING：用户 F01 记忆与 F02 五六秒反馈尚无逐回合实体记录；第三真实失败未取得。空模板与手册已交付，三十真实短聊/十次触摸、背景/低音量/短答自然性待验；R06 跨天记忆未实施。DEPLOYMENT_PENDING/PUSH_AUTHORIZED：用户已授权分支推送，结果见外置推送收据；没有本轮安装、刷写、真实模型/Bark 或十四天观察。

## 当前运行与回退

本轮未再读取线上环境；最后只读事实为 2026-10-12 的 a37d220/V60/r3/motion_disabled，不能当当前新鲜状态。原恢复点与匹配镜像保留。后续授权安装须先停写备份、内部隔离恢复；V61 回退使用匹配数据库/镜像，不能直接换旧应用或修改 migration history。

## 下一条精确操作

核对[本轮交付](../baselines/companion-r01-natural-voice.md)、候选收据和推送收据后，由用户在 GitHub 创建 PR、审核并合并；Agent 不创建或合并 PR。安装/实体采集按[测量手册](../../runbooks/natural-voice-quality.md)另行授权后执行。后续 R02/R06 需独立任务指令。
