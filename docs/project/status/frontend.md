# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-02
- 当前分支：`codex/companion-interaction-delivery`
- 基准提交：`69a98cd`
- 最后验证提交：`69a98cd`
- 最后验证范围：本轮工作树 41 文件 147/147、类型检查、production build、定向 ESLint/Stylelint 通过

## 当前目标

完成 COMPANION-007 语音诊断、稳健默认和一次关心来源/状态展示，不重做全站 UI，见[交付文档](../companion-interaction-delivery.md)。

## 已完成

ASR/模型/TTS 独立耗时、安全码和未知缺省；首声延迟用同设备采音结束/播放开始。区分恢复聆听与手动就绪，新默认稳健（推荐）保留保存值。提醒/首页区分 FOLLOW_UP 已确认的一次关心和 EXPIRED；可以取消/删除，不允许普通提醒式编辑/稍后播报绕过确认。旧 API 与伙伴隔离保持，诊断/来源回归通过。

## 正在进行

companion007-d3459d2 已发布原 LAN 8080，首页及 15 项资源指纹一致。获许可仅修该应用提醒页，沿用 FaPagination 换行/有界页码滚动；147/147、类型/build、定向 ESLint/Stylelint 通过。分页断言在旧版手机失败，新版桌面/手机通过；真实登录、诊断/语音配置 GET、唤醒下拉选项及合成关心/过期/缺失/错误/2500 条分页流程和 16 张截图通过，没有业务写请求或页面错误。

## 下一步操作

前端实现及实际无头验收完成；2026-10-02 用户确认整体非动作清单正常，并明确授权只推送当前任务分支；推送并核对远端 HEAD 后由用户创建 PR、审核/合并，不继续 UI 扩张。需要回归时在用户许可范围运行 ./scripts/verify-companion-console-headless.ps1 -AllowTemporaryAdministrator，固定隔离镜像、LAN 守卫、业务只读；诊断/提醒稀有状态仅合成 GET，不写真实数据。结束注销、验证 401 并删除临时账号，不重新开启 Windows 控制。

## 阻塞项

无实现或视觉阻塞；Windows 历史故障不再重试。镜像和临时管理员两项审核拒绝均在单独新许可后解除；已验证临时账号/容器零残留、每个测试会话注销后受保护 GET 为 401。中断遗留账号已清理，旧内存会话随正式替换消除。用户完整清单确认单独记录，不把 UI 合成状态当作真实音频/跨日证明，见总览。

## 关键文件

apps/stackchan-console/src/api/modules/devices.ts、reminders.ts、views/devices/overview/、views/settings/speech/index.vue、views/reminders/list.vue、views/dashboard/DeliveryTimeline.vue。

## 验证命令与最近结果

Node 24.19.0 满足 engines，未改锁文件。console 内 node ../../node_modules/vitest/vitest.mjs run --maxWorkers=1：41 文件 147/147；类型/build 和提醒页定向 ESLint/Stylelint 通过。隔离 Playwright 1.63.0 / Chromium 153.0.8010.12，1440x900、390x844、分页边界/首尾可达、合成阶段/缺省/错误及跟进菜单通过，16 张截图人工检查。缺临时账号许可参数的负向守卫通过。保留原代理拒绝、退出等待及大块提示，退出码 0；docs/diff 和检查器 7/7 收尾复跑。

## 相关设计、计划和决策

[交付文档](../companion-interaction-delivery.md)、[ADR 0070](../decisions/0070-hybrid-voice-and-confirmed-companion-care.md)、[本轮发布记录](../../runbooks/companion-interaction-20261001.md)。侧栏修复已合入，旧证据见[历史发布](../../runbooks/sidebar-route-layout-20260929-release.md)。

## 安全与兼容性约束

保留 Fantastic-admin/Fa、旧 URL 和授权，未扩主题/UI 范围；本次外部推送仅授权当前任务分支，不推送 master、不创建或合并 PR。用户缓存/日志与 components.d.ts 空内容差异不作为业务修改。
