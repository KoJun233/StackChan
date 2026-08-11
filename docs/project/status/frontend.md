# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-11
- 当前分支：`codex/evt-001-external-notifications`
- 基准提交：`0e92d58`
- 最后验证提交：`0e92d58`

## 当前目标

交付 Fantastic-admin 的“外部通知”管理模块，覆盖集成、固定目标设备、一次性令牌、测试播报、投递队列和健康摘要；随后 `ROLE-001` 再升级人设、聊天、记忆、提醒和个人数据页面。

## 已完成

- 登录/密码轮换、LLM 与语音配置、流式聊天、设备配对/总览和健康中心。
- 人设、长期记忆、提醒、交互设置、主动主题和个人数据管理。
- Skill、Tool、MCP、表情资源包、唤醒模型和应用 OTA 管理页面。
- 页面不把秘密、对话正文、记忆建议或 Tool 数据持久化到 Pinia/localStorage。
- 新增真实 REST API 模块、外部通知路由、集成新增/编辑表单、令牌签发/撤销和通知状态筛选；未生成 fake/mock 业务数据。
- 令牌明文只停留在签发结果弹窗，关闭后不提供再次查看入口。
- 外部通知保留 `/notifications` URL，但导航归属已并入“提醒管理”，避免破坏旧书签和详情页高亮。
- 集成列表和通知队列均增加二次确认删除；`DISPATCHED` 通知按钮禁用，删除集成明确提示令牌及队列/历史会被永久清理。
- 完整合并历史见[里程碑索引](../milestones.md)。

## 正在进行

实现和 Node v24.15.0 自动化验证已完成，等待人工审核；当前 LAN 已发布菜单归属、集成删除和通知记录删除。

## 下一步操作

刷新管理页面，确认“提醒管理”下同时显示提醒列表和外部通知，旧 `/notifications` URL 继续可用，并实测两类删除的二次确认与播放中保护。

## 阻塞项

- 代码审核和发布无阻塞；管理员浏览器复核尚未执行。
- 角色页面改造必须等 EVT-001 合入，避免对通知集成和提醒角色归属做两次迁移。

## 关键文件

- `apps/stackchan-console/src/api/modules/`
- `apps/stackchan-console/src/router/modules/`
- `apps/stackchan-console/src/views/settings/agent/`
- `apps/stackchan-console/src/views/reminders/`
- `apps/stackchan-console/src/views/companion/`
- `apps/stackchan-console/src/views/devices/health/`
- `apps/stackchan-console/src/views/notifications/`

## 验证命令与最近结果

- Node v24.15.0、pnpm 11.13.1：Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过；路由专项 3/3 通过。
- 新 API 单测断言集成/令牌管理、队列筛选和测试播报均调用真实管理端点。
- 删除 API、69/69 Vitest、`vue-tsc -b` 和 production build 已在 Node v24.15.0、pnpm 11.13.1 容器中通过。
- 未修改浏览器持久化策略；用户确认当前 LAN 外部通知基础测试正常，最新菜单和删除功能已发布、尚待管理员会话复核。

## 相关设计、计划和决策

- [当前任务清单](../todo.md)
- [0001：Fantastic-admin 前端](../decisions/0001-fantastic-admin-frontend.md)
- [0020：确认且有范围的长期记忆](../decisions/0020-confirmed-scoped-long-term-memory.md)
- [0025：页面管理 MCP 连接](../decisions/0025-managed-mcp-connections.md)
- [0026：个人数据生命周期](../decisions/0026-personal-data-lifecycle-and-isolated-backups.md)
- [0031：健康中心与应用 OTA](../decisions/0031-safe-application-firmware-ota-and-health-center.md)
- [0032：外部通知平台](../decisions/0032-external-notification-platform.md)

## 安全与兼容性约束

- 一次性通知令牌不得写入 Pinia、localStorage、URL、日志或错误报告。
- 测试播报是显式副作用，必须由管理员主动触发并清楚展示目标设备。
- 旧 API 缺少新字段时使用安全默认值；ROLE-001 前不得假装已有角色隔离。
- 后续发布或替换 server 仍需明确授权；本次已授权发布，未推送分支。
