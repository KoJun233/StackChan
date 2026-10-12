# 服务端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-11
- 当前分支：`codex/bark-voice-routing`
- 基准提交：`ec53bfe`
- 最后验证提交：`ec53bfe`

## 当前目标

修正 [COMPANION-010](../bark-agent-tool.md) 用户原话的语音路由及能力否认，继续复用已有 Bark Tool 与配置。

## 已完成

「给我的手机发一条消息」支持目标/动词两种顺序，明确请求不再进入无工具语音聊天。具体工具的「有没有功能」、通知能力和设置咨询走真实能力目录读取，保留写工具隔离。角色及真实能力说明通过模型 system prompt 传入，优先于旧历史否认。

无正文且没有实际操作时返回内容追问，不虚报投递失败；紧接回答可以发送，字面正文不会误作天气查询。取消、否定、咨询、换话题、续讲、普通聊天和已发送历史不能继续写权限。通知正文内的否定和能力词不会误关闭工具。已尝试 FAILED/UNKNOWN 仍保留真实失败，不变成内容追问或聊天成功。

原通知服务、持久去重、权限重查、任务快照、自动交付去重和 V60 保持。已安装 `bark-voice-a37d220`，冻结源码 `a37d220e7a2a0189b28412ffd420b2173fbbfc13` 保留 V57–V60；最终任务提交的三个修复文件与冻结源码逐文件核验。

## 正在进行

软件与安装完成；用户现场复测确认基本正常，已授权推送当前任务分支。模拟自动化不证明手机接收。

## 下一步操作

用户在 GitHub 为 `codex/bark-voice-routing` 创建 PR，审核后完成最终合并；后续通知行为按 runbook 检查实际工具审计与回执。

## 阻塞项

无。UNKNOWN 先检查手机，不自动再发。

## 关键文件

`AgentOrchestrator.java`、`CapabilityListTool.java`、`BarkNotificationToolTest.java`；原 `BarkNotificationService` 与 V60 未改。

## 验证命令与最近结果

修复前 `./scripts/test-server-linux.ps1 -Tests 'BarkNotificationToolTest,AgentOrchestratorTest,AgentToolPolicyInterceptorTest'`：32/32。

最终安装集成 `./scripts/test-server-linux.ps1 -Tests 'BarkNotificationToolTest,AgentOrchestratorTest,AgentToolPolicyInterceptorTest,VoiceTurnServiceTest,VoiceDialogueControlTest'`：5 文件、103/103，错误/失败/跳过零；报告 `D:\stackchan\.worktrees\bark-voice-integration\.tools\stackchan-companion-tests-f35b33f2c84c4f1683ab971ae3e91bcc`，冻结 a37d220。新请求与能力咨询均验证 WEB/VOICE，真实 ReactAgent 读取目录/调用通知，历史否认不作为事实；模拟 Bark/模型不外发。

最终候选服务端编译/打包、`git diff --check`、`pnpm docs:check` 通过；ec53bfe 是分支基线，实际修复成绩以上述冻结来源为准。先前完整 840/840 为旧 Bark 版本成绩。

本轮用户复测并授权推送后，在当前任务分支再次运行相同 5 类最小回归：99/99，失败/错误/跳过零；报告 `.tools/stackchan-companion-tests-7a9100f3ea5c4dc89e53e0f4d658c7cb`。安装集成额外包含 V57 的 4 项语音测试，故此前为 103 项。测试后仅更新文档，最终代码仍与已测试源码一致。

## 相关设计、计划和决策

[设计](../bark-agent-tool.md)、[ADR0075](../decisions/0075-bark-agent-notification-tool.md)、[原任务](../recurring-research-delivery.md)、[使用说明](../runbooks/recurring-research-and-bark.md)。

## 安全与兼容性约束

没有新迁移；Key、消息正文和认证载荷不进入审计。原工作树、V57、模式、卷、凭据和 r3 固件保持。用户已授权仅推送当前任务分支；PR 创建、审核与合并由用户执行。
