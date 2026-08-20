# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-23
- 当前分支：`codex/media-002-expression-experience`
- 基准提交：`19bd459`
- 最后验证提交：`41b8827`

## 当前目标

收口 `MEDIA-002A/B/C/D` 管理体验：保留静态资源包兼容，同时提供角色主题色、连续帧率配置、真机预览和可解释的动态表情性能诊断。

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

## 已完成的 MEDIA-002A

- 将“创建资源包”和“管理与启用”拆成两个页签，创建资源包不再隐式绑定或改变设备。
- 支持选择文件夹并按中英文标准文件名把图片归入八个 v1 状态；无法识别、非 PNG、超过 384 KiB 或不是 320×240 的文件明确失败。
- 显示 8/8 完成度、缺失状态、逐项上传、状态说明和 320×240 预览；删除批量导入项会同步清除仍由它提供的状态。
- 设备区明确展示在线状态、当前资源包与安装状态；只有显式点击启用才下发安装，失败继续使用原表情。
- 页面明确区分当前静态 PNG 兼容范围和后续动态球形引擎，不新增浏览器持久化或隐式 OTA。

## 已完成的 MEDIA-002B/C

- 表情管理页增加动态球体/静态 PNG 边界和 12/8/6 语义说明；角色表单增加主题色；健康中心增加渲染模式、FPS、耗时、丢帧、堆与传感器诊断。

## 正在进行

- V35 页面已部署；固定帧率为单滑块、自适应范围为双端滑块，均可按 1 FPS 步长选择 1–60 的任意整数。
- 前端 API 类型和诊断目标帧率同步改为连续数值，滑块只负责归一化、取整和端点限制，不再把 40/50 等值映射回三档。
- 原 12/8/6 静态标签改为 12/9/6 真机预览按钮，显示单按钮加载态，并在设备离线、旧固件或静态 PNG 模式下给出明确禁用原因。
- 连续滑块映射新增独立单测；控制台 27 文件 81/81、类型检查和 production build 通过，生产静态资源已随 LAN server 发布。
- 健康中心的“绘制/传输”已改为“场景更新/LVGL 刷新”，与固件拆分后的计时语义一致，并随最终 LAN server 发布。
- `MEDIA-002D` 页面实现、全量回归和 LAN 发布已完成。

## 下一步操作

用户从 `http://192.168.1.3:8080/` 复核连续滑块、真机预览和健康中心标签。固定 47 FPS、自适应 24–57 FPS 可作为非阻塞的管理员补充验收。

## 阻塞项

- 当前无实现阻塞；后续固件工作仍需独立授权，连接器继续暂停。

## 关键文件

- `apps/stackchan-console/src/api/modules/`
- `apps/stackchan-console/src/router/modules/`
- `apps/stackchan-console/src/views/settings/agent/`
- `apps/stackchan-console/src/views/reminders/`
- `apps/stackchan-console/src/views/companion/`
- `apps/stackchan-console/src/views/companion/expressions/expressionPackStaging.ts`
- `apps/stackchan-console/src/views/devices/health/`
- `apps/stackchan-console/src/views/notifications/`

## 验证命令与最近结果

- 2026-08-23 使用 Node v24.19.0、pnpm 11.19.0：主控制台 Vitest 27 个文件 81/81、`vue-tsc -b` 和 production build 通过；测试结束仍有既有 Vite 关闭超时提示，但进程以 0 退出。
- 2026-08-23 最终前端随 LAN server 发布，运行静态资源同时包含“场景更新”和“LVGL 刷新”，本机与当前 LAN 首页均为 200。
- MEDIA-002A/B/C 使用 Node v24.19.0、pnpm 11.19.0：主控制台 Vitest 26 个文件 78/78、`vue-tsc -b` 和 production build 通过；测试结束有既有 Vite 关闭超时提示，但所有用例成功且进程以 0 退出。
- EVT-003 已通过 `13987b0` 合入；其前端 25 个文件 70/70、类型检查和 production build 证据保留在合并历史。
- 提交级回归发现初始化时期的 `DeviceRow` 测试夹具缺少既有必填 `lastSeenAt`；已仅补齐 `null`，不改变运行页面或 API。
- Node v24.19.0、pnpm 11.19.0：Vitest 25 个文件 70/70、`vue-tsc -b` 和 production build 已通过。
- 同一运行时下，旧控制台 Vitest 5 个文件 23/23、`vue-tsc -b` 和 production build 通过；仅测试夹具补齐既有字段。
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
- [0038：分层动态球形表情与兼容资源包](../decisions/0038-layered-expression-rendering-and-resident-appearance-catalog.md)

## 安全与兼容性约束

- 一次性通知令牌不得写入 Pinia、localStorage、URL、日志或错误报告。
- 测试播报是显式副作用，必须由管理员主动触发并清楚展示目标设备。
- 旧客户端省略角色时解析为默认角色；既有会话和陪伴数据不可通过编辑接口改绑角色。
- 发布或替换 server 仍需明确授权；当前 INT-013 不包含前端改动。
- 第三方表情项目只作调研参考，不复制代码或素材；页面不允许任意远程动画 JSON，也不把模型标记、诊断或主题色写入浏览器持久化。
