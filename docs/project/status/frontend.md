# 前端工作流

- 状态：STABLE
- 最后更新：2026-10-11
- 当前分支：`codex/bark-voice-routing`
- 基准提交：`ec53bfe`
- 最后验证提交：`ec53bfe`

## 当前目标

[COMPANION-010](../bark-agent-tool.md) 语音修复不修改前端源码，保持原 Agent 设置、开关与审计入口。

## 已完成

工具目录由服务端动态提供，原通知条目、开关、审计及设置页说明保持。已安装 `bark-voice-a37d220`，与上一版本 index 保持，全部 288 个资源匹配候选。

## 正在进行

实现、类型和生产构建完成。真实聊天工具体验及 iPhone 新通知需用户试用。

## 下一步操作

刷新 `http://192.168.1.5:8080`，在「设置 → Agent 能力」查看 `send_phone_notification`；聊天中明确要求手机推送后查看调用审计与 Bark 历史。

## 阻塞项

无软件或安装阻塞；本轮没有发送真实测试通知。

## 关键文件

`apps/stackchan-console/src/views/settings/agent/index.vue`；动态目录来源 `AgentManagementController`。

## 验证命令与最近结果

本轮候选冻结 `a37d220e7a2a0189b28412ffd420b2173fbbfc13` 重新完成完整 vue-tsc/Vite 构建，日志 `D:\stackchan\.worktrees\bark-voice-integration\.tools\bark-voice-build.log`。最终任务分支相对 master 的全部 13 个 TS/Vue 执行等同提交钩子的 ESLint/stylelint 通过。下列组件测试与独立类型检查为前次 Bark 引入的成绩，页面源码此次未改。

控制台 `pnpm exec vitest run src/views/settings/agent/index.test.ts --reporter=dot`：1/1。`pnpm exec vue-tsc --noEmit -p tsconfig.app.json --incremental false` 与 node 配置均通过；该 Vue 文件 ESLint/stylelint 及等同提交钩子的检查通过。候选完整 vue-tsc/生产 Vite 构建通过，日志位于集成工作树 `.tools/bark-release-build.log`，源 `dc2ba2fb5dd437774d5d4ea6fa23894eeb186ce9`。实际全部 288 个资源 SHA256 与候选一致。

复用已验证的 Git 依赖树、锁文件和 Node/pnpm 缓存镜像，重新编译源码。旧测试 select stub/Vite 关闭与大 chunk 提示保留，退出 0。没有为单行说明另写实现镜像测试。`git diff --check`、`pnpm docs:check` 通过；原用户 components.d.ts 指纹保持。

## 相关设计、计划和决策

[设计](../bark-agent-tool.md)、[ADR0075](../decisions/0075-bark-agent-notification-tool.md)、[使用说明](../runbooks/recurring-research-and-bark.md)。

## 安全与兼容性约束

原入口和生成文件保持，不在页面显示 Key 或消息审计正文。没有新路由、表单或权限体系。
