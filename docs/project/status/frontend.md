# 前端工作流

- 状态：ACTIVE
- 最后更新：2026-09-29
- 当前分支：`codex/body-motion-five-actions`
- 基准提交：`2f75da3`
- 最后验证提交：`2f75da3`
- 最后验证范围：BODY-002 控制台 142 项测试及生产构建、现有 LAN 8080 发布为本任务分支历史证据；本次传输修复未修改前端。状态字段以主线基准提交作可解析锚点；本任务最终单提交自身的哈希无法写入同一提交

## BODY-002 当前进度

管理端保留五个手动模板入口，新增每条动作的 `SENT/ACCEPTED/COMPLETED/STOPPED/FAILED/UNCONFIRMED` 状态展示，以及独立且默认关闭的低频自动动作开关。陪伴设置页增加设备级“今天安静点”及提前恢复；页面明确说明普通提醒继续。`pnpm --filter @stackchan/console test --maxWorkers=1` 通过 39 文件、141 项；镜像中的 production build 通过类型检查和构建。测试期间开发代理曾报告 localhost:3000 连接拒绝及 Vite 退出延迟，但最终退出码为 0。原 BODY-002 页面已发布到现有 8080，首页返回 200。新候选 `7d8c55a` 为文字聊天增加机器人选择；唯一在线且支持身体动作的设备会默认选中，可手动改为不联动，并把设备 ID 随文字请求提交。定向 20 项测试和生产构建通过；新页面已随 `body002-think-7d8c55a` 发布，首页与 15 个资源 GET 均为 200。浏览器控制连接因插件服务文件缺失而失败，尚无真实页面点击证据。下一条精确操作：浏览器可用时核对设备选择与状态文案；用户在设备旁后逐个核对实体结果。

以下为 2026-09-21 控制台改造与发布的历史状态，不代表 BODY-002 已部署。

## 当前目标

按用户最新指示停止剩余浏览器验收，将已实现的新 UI 发布到现有 8080，交由用户使用后反馈。此交付已完成，不再等待浏览器工具恢复。

## 已完成

[实施记录](../console-experience-implementation.md)所列 B01–B08、U01–U10 的代码改造及回归；页面壳、浅深主题、菜单、伙伴首页、聊天、陪伴/工作、形象、扩展和文案收敛。实测另修默认伙伴初始化冲突及短屏发送按钮裁切。伙伴切换确认、保存等待、乱序/失败与操作范围回归通过。

2026-09-21 20:58:52 发布 console-ux-20260921；8080 首页及 15 个引用静态资源与 dist 哈希一致，服务健康正常。详见[发布与回退](../../runbooks/console-ux-20260921-release.md)。

## 正在进行

新版已在 8080 运行，等待用户试用反馈。代码与文档整理为当前任务分支的单个中文提交；用户已授权推送该分支，PR 和最终合并由用户执行。

## 下一步操作

用户刷新现有 8080 页面并试用；如发现问题，按页面、步骤、预期与实际结果定位修复。GitHub 上由用户创建 PR、审核与合并。需要恢复旧 UI 时按发布手册执行同 JAR 镜像回退。

## 阻塞项

本次发布无阻塞。Browser 缺文件及 Computer Use URL 识别失败仍存在，但用户已明确取消其作为此次交付前置条件。A08、A10–A16 等完整视觉、焦点及实体手机验收未通过也未预填通过，移交用户试用。

## 关键文件

[实施记录](../console-experience-implementation.md)、[原排查报告](../console-ux-audit-2026-09-19.md)、apps/stackchan-console/src/、packages/themes/index.ts、scripts/deploy-console-ui.ps1、compose.console-ux.yaml。

## 验证命令与最近结果

`pnpm --filter @stackchan/console test --maxWorkers=1`：39 文件 142/142；`pnpm --filter @stackchan/console run build`（含 vue-tsc）通过；定向 ESLint/Stylelint 通过。保留 Vitest 本地连接/退出等待与 Vite 大块体积提示。部署预检确认后端 JAR、所有环境键值（版本除外）及数据卷一致，HTTP 健康和静态资源哈希通过。文档门槛为 docs:check、docs:check:test 与 git diff --check。

## 相关设计、计划和决策

[完整排查报告](../console-ux-audit-2026-09-19.md)、[实施与移交](../console-experience-implementation.md)、[部署手册](../../runbooks/console-ux-20260921-release.md)。保留 ADR 0047 的 TDesign Chat 展示例外与 ADR 0069 的旧链接/授权/数据保留边界。

## 安全与兼容性约束

部署授权仅用于本次现有 8080 更新；保持 LAN 开发模式，生产仍需 HTTPS。没有固件刷写、凭据轮换、数据恢复或真实删除。未跟踪缓存与 JVM 日志保持原样。
