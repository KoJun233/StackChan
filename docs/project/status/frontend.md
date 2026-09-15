# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-09-15
- 当前分支：`codex/companion-trust-improvements`
- 基准提交：`54e41f9`
- 最后验证提交：`54e41f9`
- 最后验证范围：本任务工作区与实际发布；提交字段仍标识基准

## 当前目标

按评审深化或收缩功能，部署后交给用户测试。软件交付与部署已完成。

## 已完成

今日概览按设备与伙伴展示真实待办数量、未来与近期播报；主动陪伴提供角色独立暂停/恢复；两个可选角色草稿；个人事务与高级扩展收敛，旧链接兼容；记忆管理说明确认替代及遗忘范围。新控制台已随 V51 发布。

## 正在进行

用户已于 2026-09-15 授权推送当前任务分支，等待其创建 PR、审核和合并。不自动启动新开发或使用观察。

## 下一步操作

推送唯一中文任务提交后由用户创建 PR、审核和合并；用户也可按[验收表](../companion-experience-acceptance.md)测试两个独立伙伴、记忆、主动暂停和简短回应，记录实际识别与听感问题。

## 阻塞项

无发布阻塞。浏览器插件缺少运行文件，因此真实点击未验证；复杂口语、真实模型、使用价值和实体动作不以自动化替代。曾被自动审批拒绝读取隔离异常日志，已通过正常应用配置的隔离演练解决启动问题，没有读取该日志。

## 关键文件

apps/stackchan-console/src/views/dashboard/、views/settings/interaction/、views/settings/agent/、views/companion/、router/。

## 验证命令与最近结果

33 文件 107/107、vue-tsc 类型检查、相关 ESLint/Stylelint 通过；固定 Node 24.15.0 与 pnpm 11.13.1 的 Docker 页面构建通过。发布首页及 /assets/index-DS8dGmCA.js 返回 200。浏览器插件缺少运行文件，真实页面点击没有执行。

文档检查使用 `pnpm docs:check`、`pnpm docs:check:test` 和 `git diff --check`。前端回归使用 `pnpm --filter @stackchan/console test --maxWorkers=1`；构建使用 `docker build -f server/Dockerfile -t stackchan-foundation-server:companion-v51-20260914 .`。不重复宣称八个受限服务端测试类通过。

## 相关设计、计划和决策

[完整实施清单](../companion-completion-plan.md)、[ADR 索引](../decisions/README.md)、[发布与回退](../../runbooks/companion-v51-release.md)、[备份与恢复](../../runbooks/personal-data-backup.md)。历史合并能力查[里程碑](../milestones.md)。

## 安全与兼容性约束

保持 LAN 开发模式；生产必须 HTTPS。伙伴记忆独立，操作确认不跨角色、不替换失效目标。旧 API/深链接与普通、工作、外部通知语义保留。本次只授权推送当前任务分支；不创建或合并 PR、不推送 master，不刷写固件或开启动作。DPAPI 备份依赖原 Windows 用户/机器，本地卷不是跨机器灾备；旧镜像与 V51 的直接回退不作兼容承诺。
