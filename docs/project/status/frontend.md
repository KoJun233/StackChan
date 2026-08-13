# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-13
- 当前分支：`codex/evt-003-notification-digests`
- 基准提交：`6ed3b5d`
- 最后验证提交：`6ed3b5d`

## 当前目标

在现有通知集成 CRUD 中增加摘要窗口配置与列表状态，不新增页面或秘密持久化。

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

## 已完成的 ROLE-001

- “人设设置”升级为角色列表和详情表单，支持创建、编辑、归档、恢复、数据统计和设备活动角色绑定。
- 新会话可选择角色并永久绑定；聊天列表按角色过滤，切换角色不会把旧会话历史带入新上下文。
- 记忆、提醒、通知集成和个人数据页面增加角色选择或筛选；已有数据不可在编辑时改绑角色。
- 角色详情使用隐藏路由并保持角色管理菜单高亮，旧角色管理入口继续兼容。

## 已完成的 ROLE-002

- 角色详情表单增加最多 160 字的可选音色输入；留空时提交 `null` 并继承全局音色。
- 角色列表展示具体覆盖音色或“继承全局音色”，不新增重复页面、路由或浏览器持久化。

## 已完成的 EVT-002

- 测试播报可显式勾选已知晓、稍后提醒和标记完成；未勾选时仍发送兼容的旧请求正文。
- 通知队列展示允许动作和最新回执，已送达互动通知可由管理员手动回应以完成无实体设备验收。
- 管理页稍后提醒固定为 10 分钟，语音可明确选择 1–1440 分钟；页面继续保留既有状态筛选、删除保护和“提醒管理”菜单归属。

## 正在进行

- 集成表单可填写 0 或 5–300 秒摘要窗口，并明确只有单向通知参与、互动通知继续逐条播报。
- 集成列表展示摘要关闭或具体秒数；旧客户端省略配置时由服务端保留已有值。

## 下一步操作

完成状态交接并整理单一中文任务提交，提交后复核静态检查并等待部署授权。

## 阻塞项

- 当前无实现阻塞；EVT-003 尚未部署。

## 关键文件

- `apps/stackchan-console/src/api/modules/`
- `apps/stackchan-console/src/router/modules/`
- `apps/stackchan-console/src/views/settings/agent/`
- `apps/stackchan-console/src/views/reminders/`
- `apps/stackchan-console/src/views/companion/`
- `apps/stackchan-console/src/views/devices/health/`
- `apps/stackchan-console/src/views/notifications/`

## 验证命令与最近结果

- EVT-003 当前工作树基于 `6ed3b5d`；实现、交接与提交前静态检查已完成。
- Node v24.19.0、pnpm 11.19.0：Vitest 25 个文件 70/70、`vue-tsc -b` 和 production build 已通过。
- Node v24.15.0、pnpm 11.19.0：Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过。
- LAN 首页为 200，运行容器内角色详情静态资源包含“角色音色”和“继承全局音色”。
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
- [0033：角色容器](../decisions/0033-companion-role-containers.md)
- [0034：角色 TTS 音色覆盖](../decisions/0034-role-tts-voice-overrides.md)
- [0035：互动通知回执](../decisions/0035-interactive-notification-responses.md)
- [0036：确定性通知摘要](../decisions/0036-deterministic-notification-digests.md)

## 安全与兼容性约束

- 一次性通知令牌不得写入 Pinia、localStorage、URL、日志或错误报告。
- 测试播报是显式副作用，必须由管理员主动触发并清楚展示目标设备。
- 旧客户端省略角色时解析为默认角色；既有会话和陪伴数据不可通过编辑接口改绑角色。
- 发布或替换 server 仍需明确授权；当前 EVT-003 尚未部署或推送。
