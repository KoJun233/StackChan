# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-11
- 当前分支：`codex/bark-voice-routing`
- 实现基准：`ec53bfe`
- 最后验证提交：`ec53bfe`

## 当前任务

[COMPANION-010 Bark Agent Tool](../bark-agent-tool.md) 的现场语音缺陷已修正并安装为 `bark-voice-a37d220` / V60。用户原话「嗯，给我的手机发一条消息」目标在动词之前，旧规则漏判并进入无工具低延迟聊天；「你没有那个 Send for notation 那个功能吗」也漏判。发现后先标记 STALE，现依据代码、回归与实际部署恢复完成状态。

明确手机发送请求进入 Agent；功能咨询读取当前配置授权目录，不开放写工具。角色、工具边界和能力目录说明作为模型 system prompt，以实际能力覆盖历史否认。缺正文且未实际操作时追问内容，紧接的正文回答延续此次授权；取消、咨询、换话题、续讲和已发送历史不会继承写权限。字面正文中的否定、能力词和带伞措辞不会误当指令或无关查询。真实失败及 UNKNOWN 仍不能声称发送成功。

新任务从新鲜 origin/master@ec53bfe 创建，包含未合入的持续任务/Bark 依赖，最终保留一个中文任务提交。实际安装冻结 `a37d220e7a2a0189b28412ffd420b2173fbbfc13` 从旧 dc2ba2f 集成增加此次修复，保留 V57–V60。原 `codex/companion-topic-continuity@8ad9822` 工作树及用户文件保持。最后验证提交字段为分支已存在基线；本轮源码与成绩以下列冻结来源和收据为准。

| 工作流 | 状态 | 交接 |
| --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | 口语请求、能力咨询、内容追问与模型系统提示修复 |
| [前端](frontend.md) | STABLE | 页面源码未改，生产类型、构建和全部资源指纹通过 |
| [固件](firmware.md) | READY_FOR_REVIEW | 保持原 r3 与既有实体验收范围 |
| [部署](deployment.md) | READY_FOR_REVIEW | V60 修复安装、恢复与完整产物/数据核验通过 |

## 验证结果

修复前最小基线 32/32。最终冻结集成相关 5 文件、103/103，失败/错误/跳过均零；包括文字/语音、实际 ReactAgent 调用、用户原话、能力目录优先于旧否认、正文补全、取消与旧发送不复用、运行时关闭、未配置、失败与 UNKNOWN。先前 Bark 完整集成的 840/840 为旧版本成绩，本轮不冒充新完整套件。中间新增测试断言误把 instruction 当作首消息，已修正为 system prompt 检查并重新通过。最终报告 `.tools/stackchan-companion-tests-f35b33f2c84c4f1683ab971ae3e91bcc` 位于 `D:\stackchan\.worktrees\bark-voice-integration`。

候选重新执行完整 vue-tsc/Vite 与服务端打包；任务分支相对 master 的全部 TS/Vue 执行等同提交钩子的 ESLint/stylelint，`git diff --check`、`pnpm docs:check` 通过。生产大 chunk 和既有弃用提示保留，最终退出 0。

用户授权推送后，在当前任务分支重跑相同 5 个相关测试类：99/99，失败/错误/跳过均零，报告 `.tools/stackchan-companion-tests-7a9100f3ea5c4dc89e53e0f4d658c7cb`。与安装集成的 103 项相差原 V57 的 4 项语音测试；本轮代码与已验证安装源码一致，测试后仅更新验收及交接文档。实现、验收与交接压缩在同一中文任务提交后仅推送该分支。

停服备份及内部隔离 V60 恢复先于替换，保留 `stackchan-release-20261011152920`，旧恢复点保持。五项加密设置、管理员、Skill、DPAPI、个人数量与全部旧 checksum 验证通过；原 1 个任务、3 条历史、Bark 设置及通知回执指纹不变。真实镜像、JAR 与全部 288 个资源匹配；健康 ok、控制台 200、匿名委托 API 401，原 r3 固件保持。自动化模拟模型/Bark，不发送真实测试通知。用户现场复测后确认「基本没问题」，并明确授权推送当前任务分支。

## 下一步与边界

用户自行在 GitHub 为 `codex/bark-voice-routing` 创建 PR、审核并完成最终合并。实际安装仍使用保留 V57–V60 的冻结集成；当前任务分支不包含尚未合入的 V57，禁止直接替换已安装来源。通知用法及真实回执边界见 runbook。

无实现或安装阻塞。LAN、身份、卷、凭据、DoH true 和固件保持。用户本轮明确授权外部推送，范围仅当前任务分支；PR 创建、审核和最终合并由用户执行。详见[使用说明](../runbooks/recurring-research-and-bark.md)。
