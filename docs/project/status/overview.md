# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r02-project-loop-20261015`
- 实现基准：`a961816`
- 最后验证提交：`a961816`

## 当前交付

PR #49 已将 R00/R01 合入 master@a961816。用户要求继续后续开发，本轮实施 [R02 一次项目闭环](../baselines/companion-r02-project-loop.md)；原“R01 等待合并、R02 未授权”状态已 STALE 并在本轮修正。原用户工作树与未提交文件保持。

R02 复用既有委托、运行与两工作者：自然入口收单、公开来源范围、五区成果、独立待阅卡、成果认可、同任务偏好修订、取消与隔日只读查询。V62 增加审阅、反馈、修订意图和无正文幂等收据；周期委托及旧接口保持兼容。R03–R11 尚未实施。

| 工作流 | 状态 | 本轮证据与边界 |
| --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | 完整 1092/1092；项目事务 17/17、迁移恢复 6/6，V1–V61 不变 |
| [前端](frontend.md) | READY_FOR_REVIEW | 46 文件 204/204、vue-tsc 与十文件 ESLint/四文件 Stylelint 通过 |
| [固件](firmware.md) | STABLE | 本轮未改、未重建；R01 历史 LAN 槽余量 4432 B，未刷写 |
| [部署](deployment.md) | READY_FOR_REVIEW | 同提交 image/JAR/62 SQL/全部控制台资源；核验结果见候选收据，未安装 |
| [产品](product.md) | READY_FOR_REVIEW | 软件闭环与自动验证完成；真实来源、隔日日用与视觉验收独立待验 |

## 验证与剩余边界

当前基准及最后验证祖先元数据均为 a961816；新增代码以本轮回归的源码指纹和最终候选收据绑定。完整服务端 1092/1092、控制台 204/204。实施、状态和交接合成恰好一个中文任务提交；随后从该提交 archive 构建，生产 image/JAR/62 SQL/Flyway/全部控制台的实际结果及完整 SHA 由 `.tools/r02/candidate-receipt.json` 封存。收据缺失或门禁未通过时，不得推断候选核验成功。2026-10-15 用户明确要求“那是你推送吧”，已授权只推当前 R02 任务分支；推送及远端 SHA 核对结果见 `.tools/r02/push-receipt.json`。本次授权不包含安装、刷写或真实外发。

FIELD_PENDING：尚未取得本轮真实二至三个可读 HTTPS 页面及隔日验收。R01 的实体语音计时、第三真实失败与 R06 长期记忆仍待验证，不能据合成测试承诺这些效果。

VISUAL_PENDING：Browser 无可用实例，Computer Use 因无法可靠识别 Windows 浏览器当前 URL 而停止。尚无 360/1280px 截图；组件与类型检查不替代视觉验收。

## 当前运行与回退

本轮未读取线上环境。最后历史只读事实为 2026-10-12 的 a37d220/V60/r3/motion_disabled，不能当作当前状态。未安装、刷写、调用真实模型/Bark 或切换部署模式。

后续安装需独立明确授权，并先停写备份与内部隔离恢复；V62 回退使用匹配的数据库备份和旧镜像，不能直接把旧程序接入新库或改写 migration history。固件沿用 R01 历史证据，容量余量不是本轮新鲜测量。

## 下一条精确操作

核对 [R02 交接](../baselines/companion-r02-project-loop.md)、本地单一中文提交与 `.tools/r02/candidate-receipt.json` 的完整 SHA、测试和制品摘要，审核差异。按本次明确授权，Agent 仅推当前任务分支并核对远端 SHA；PR 创建、审核与最终合并由用户执行。真实项目与视觉验收按 [R02 交接](../baselines/companion-r02-project-loop.md)继续。
