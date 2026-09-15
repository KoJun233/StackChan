# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-09-15
- 当前分支：`codex/companion-trust-improvements`
- 实现基准：`54e41f9`
- 最后验证提交：`54e41f9`
- 最后验证范围：本任务工作区与发布镜像；提交字段为基准，不冒充已提交结果
- 当前部署：LAN HTTP development，`companion-v51-20260914`，运行库 V51
- 生产边界：HTTPS-only

## 当前交付

已按[完整清单](../companion-completion-plan.md)实现 D01–D09 软件深化及 M01–M12 入口/职责收敛。主价值为自然聊天、准确记忆、多个独立伙伴及轻量主动开场；低价值扩张冻结，已有数据和保障功能保留。原评审见[功能评审报告](../feature-review-2026-09-13.md)，决策见 [ADR 0057–0069](../decisions/README.md)。

2026-09-15 18:36（Asia/Shanghai）停写备份并通过完整隔离恢复后，仅替换 server（内含新控制台）。运行镜像 `sha256:db5cd4afd52c61c1bbdc3ed1916f31ddccb0c7ba07d4ecc4b299bdcd0edf61af`，V51 全部迁移成功；健康、首页和入口脚本 HTTP 200，零重启；一台设备重新上报心跳，身体状态仍为 DISABLED。没有刷写固件、开启动作、轮换凭据、外部推送或创建 PR。

验证：服务端 554/554（排除八个既有 Windows loopback 受限测试类）、控制台 33 文件 107/107、类型/相关静态检查、固定工具链 Docker 全栈构建通过。隔离恢复验证了记录计数、Skill 归档、运行配置 DPAPI 往返、四项加密配置解密、原管理员记录保留、测试账号登录及页面壳。备份位置与恢复边界见[发布手册](../../runbooks/companion-v51-release.md)。

## 工作流摘要

| 工作流 | 状态 | 当前事实 |
| --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | V51 已发布，等待用户实际对话与记忆测试 |
| [前端](frontend.md) | READY_FOR_REVIEW | 新控制台已发布，路由/组件自动化通过，真实点击待用户 |
| [固件](firmware.md) | READY_FOR_REVIEW | 沿用现有固件；发布后心跳恢复，动作保持禁用 |
| [部署](deployment.md) | READY_FOR_REVIEW | 已有升级前停写备份、隔离恢复和发布健康证据 |

## 推送与合并交接

2026-09-15 用户确认可以推送，等待其合并。当前任务相对最新 origin/master 恰好一个中文提交；业务代码和已部署镜像不变，本次仅补齐交接。文档检查、校验器 7/7 和差异检查通过。只推送 codex/companion-trust-improvements；PR 创建、审核及最终合并由用户执行。没有新的开发或部署任务，真实体验未覆盖项保留如下。

## 下一步与未覆盖项

用户从 [50 项基线及追加专项](../companion-experience-acceptance.md)测试换题、记忆、轻量主动、简短回应及页面。十四日真实使用观察未开始，不能把工程 PASS 或自动化回归解释为陪伴价值。浏览器插件缺少运行文件，真实点击验收未执行；HTTP 页面检查不等价于交互通过。复杂口语/指代、真实模型听感和条件硬件能力仍需用户测试。

旧状态曾把 V49 当作当前版本，已按本次现场结果修正；历史合并能力查[里程碑](../milestones.md)，不再用旧任务流水覆盖当前状态。
