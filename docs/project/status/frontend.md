# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-09-03
- 当前分支：`codex/console-information-architecture`
- 基准提交：`227b369`
- 最后验证提交：`d7dc00d`
- 最后验证范围：二级菜单与聊天滚动复修；控制台 Vitest 32 文件 102/102、类型检查、production build、定向 ESLint/Stylelint 与 LAN 静态资源验证通过

## 当前目标

后台信息架构与六项页面反馈已完成并发布；针对用户复核发现的菜单仍平铺、聊天输入区黑块和窄屏消息区无法滚动，已完成结构性修正。Fantastic-admin 保持单侧栏，但“今日陪伴”和“事务管理”现在是实际可展开的中间菜单节点；TDesign 仅负责聊天消息展示，输入区回归 Fantastic-admin 组件，窄屏工作区使用受约束高度并由消息列表独立滚动。十四天观察窗口保持原边界，未重新开始观察。

## 已完成的六项后台反馈

- 菜单改为单侧栏二级结构；“今日陪伴”包含今日概览、陪伴聊天、工作陪伴和主动关心，“事务管理”包含提醒、个人待办和外部通知，其他角色、设备与能力分组保持可展开。
- 1024px 宽度下聊天使用 220px 会话栏与弹性消息区；TDesign 只渲染消息、Markdown 和复制动作，输入区改用 `FaTextarea`/`FaButton`，并把错误的 HSL 主题映射修正为项目实际使用的 OKLCH，消除黑块和表单 CSS 冲突。
- 小于 1024px 的上下布局为聊天工作区设置 `clamp(520px, 70dvh, 680px)` 高度；TDesign 根节点只占剩余空间，`.t-chat__list` 独立启用纵向、触摸和受控惯性滚动，输入区始终位于消息区下方。
- 天气缓存增加天气、状态、温度、体感和降水图标；iCloud 区增加未来七天只读日程列表、时间、忙碌状态和地点。
- 角色页解释归档语义并展示最早删除日期；归档满七天的非默认角色可在二次确认后手动永久删除。
- 控制台完整 Vitest 32 文件 102/102、类型检查、production build 和定向 ESLint/Stylelint 通过；新增菜单 Store 用例直接验证真实侧栏输出，不再只验证路由数组归属。

## 已完成的后台信息架构与 UI 重整

- 一级导航按“今日 / 陪伴 / 事务与通知 / 设备与运行 / 系统与能力”重新归类，保留既有业务 URL，并把 `/dashboard` 设为新的“今日概览”首页。
- 新增统一 `AppPageShell` 和 `AppMetricCard`，补齐设备、提醒、待办、记忆、角色、个人数据、AI 与语音配置的页面说明；今日概览聚合在线设备、开放待办、待投递提醒、风险和工作陪伴状态。
- 原“交互与主动陪伴”按入口拆成“主动关心”和“工作陪伴”；前者只显示设备交互/打扰策略，后者按当前概览、工作规则、日历天气和十四天观察分区。
- 外部通知、Agent 能力、语音配置和健康中心使用任务导向标签页，减少低频高级设置挤占首屏；通知回执复选改用框架 `FaCheckboxGroup`。
- 陪伴聊天改用 TDesign Chat 的列表、Markdown 内容、复制和发送器展示组件；现有 Pinia store 继续负责 SSE、取消、重试与会话持久化。依赖只在聊天路由懒加载，并由局部主题变量与 Fantastic-admin 明暗主题对齐。
- 新增 [ADR 0047](../decisions/0047-isolated-tdesign-chat-ui.md) 固化 TDesign Chat 的隔离边界；未引入附件、好评/差评、推理过程、分享或 TDesign Chatbot 传输层。

## 已完成的 WORK-004

- “提醒管理”增加“个人待办”入口，使用真实 REST API 和 Fantastic-admin 内建 `FaTable`、`FaSearchBar`、`FaPagination`、`FaForm` 与 `FaFormItem`，未生成 fake/mock 数据。
- 列表支持标题/备注、状态、优先级和角色筛选，以及完成、重新打开、单条/批量删除；备注只在管理端展示。
- 详情支持设备、角色、标题、备注、优先级、截止提醒和时区；创建后设备与角色不可编辑。
- 默认角色使用项目保留 UUID；表单不再用严格 RFC 版本位误判该合法角色，并让普通提醒和通知集成共用相同校验。
- 列表与详情按框架路由约定配置 active menu 和 keep-alive，保存后刷新原列表。
- 完整 Vitest 30 文件 98/98、`vue-tsc -b`、production build、ESLint 和 Stylelint 通过；修复页面已发布到 V45 运行态。

## 已完成的 WORK-002

- 工作日 API 增加十四天观察报告和五个显式操作；九十天指标补充误播报、外部降级、动作安全和设备重启字段。
- 页面只展示本地聚合，不展示或缓存日程、天气响应、播报正文和传感器原始值。
- 重新开始不会删除历史聚合；开始确认明确提示同日已有聚合仍会计入。
- 完整控制台 Vitest 28 文件 91/91、`vue-tsc --noEmit` 和 production build 通过；最终工作日 API 6/6、类型检查和 build 复跑通过。
- 页面已随 WORK-002/V43 发布到 LAN，并成功显式开始 2026-08-30 至 2026-09-12 的当前窗口；不产生浏览器持久化或隐式启用行为。

## 已实现的服务地址快捷更新

- 设备配网页保留完整写入流程，并新增独立的“仅更新服务地址”动作；两种操作共用用户明确选择的物理 USB 串口和服务地址校验。
- 快捷操作只生成 `update_server`、服务地址和一次性配对码三个字段，单测明确证明串口载荷不含 Wi-Fi 名称或密码。
- 页面自动申请新的一次性配对码，区分旧固件不支持、Wi-Fi 未连接、目标服务配对失败和身份保存失败；成功时说明机器人将重启重连。
- 页面明确提示该动作需要匹配的新固件；在固件安装前不会把旧固件拒绝快捷命令误报为成功。

## 已实现的 MEDIA-004

- 创建区新增“生命周期动画”页签，可为开机、唤醒和角色切换分别选择 EAF，并以 1 ms 步长设置 16..100 ms 帧间隔。
- 浏览器先做签名和单片段大小快速检查；服务端继续作为 EAF 结构与资源预算的最终权威。
- 管理卡区分 V1 PNG 和 V2 EAF，展示片段帧数、间隔、大小与摘要并支持受认证下载。
- 未上报 `lifecycleClipSupported` 的设备不能启用 V2；V2 活动时仍可预览原生表情，V1 静态 PNG 兼容边界不变。
- 当前验证：Vitest 27 文件 82/82、`vue-tsc -b` 和 production build 通过。

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

- 新增 `workday` API 模块，并在既有“交互与主动陪伴”页面加入默认关闭的工作日桌面陪伴卡片，不新增路由或浏览器持久化。
- 页面可配置周一至周日、工作时段、50/10 节奏、10/45 离开阈值、IANA 时区及成对经纬度；前端与服务端使用一致的范围和组合验证。
- WORK-001 阶段 A 页面已随 LAN server/V37 发布，运行静态资源包含“工作日桌面陪伴”。
- V38 已在同一卡片发布当前七态、工作日、在场专注目标、首次简报状态和近九十天活跃日/专注/休息汇总；不新增路由或浏览器持久化。
- CONN-001 在同一卡片增加 iCloud 连接测试、连接/重新发现、日历白名单、手动同步和断开清理；账号与 App 专用密码只存在于临时输入状态，密码保存后立即清空且服务端永不回显。
- 页面明确显示账户掩码、安全失败原因、缓存数量和最近同步时间；新发现日历默认不允许读取，用户必须显式保存白名单。
- Apple Account 输入最终只接受已在账户“登录与安全性”中登记验证的邮箱；真实 CalDAV 对照已证明手机号不可用。
- CONN-001 页面已随 LAN server/V39 发布，并已完成真实账号连接与机器人日程问答复测。
- WEATHER-001 在同一卡片复用地点名称、时区和经纬度，新增 Open-Meteo 来源说明、缓存状态、安全失败、摘要、无保存测试和已保存位置同步；保存位置变更后立即刷新缓存状态。
- 经纬度控件使用保留字符串的十进制文本输入，避免原生数字输入把值转换为 number 后与表单字符串模型冲突并显示英文 `Invalid input`；有效范围仍由统一中文校验约束。
- V35 页面已部署；固定帧率为单滑块、自适应范围为双端滑块，均可按 1 FPS 步长选择 1–60 的任意整数。
- 前端 API 类型和诊断目标帧率同步改为连续数值，滑块只负责归一化、取整和端点限制，不再把 40/50 等值映射回三档。
- 原 12/8/6 静态标签改为 12/9/6 真机预览按钮，显示单按钮加载态，并在设备离线、旧固件或静态 PNG 模式下给出明确禁用原因。
- 连续滑块映射新增独立单测；控制台 27 文件 81/81、类型检查和 production build 通过，生产静态资源已随 LAN server 发布。
- 健康中心的“绘制/传输”已改为“场景更新/LVGL 刷新”，与固件拆分后的计时语义一致，并随最终 LAN server 发布。
- `MEDIA-002D` 页面实现、全量回归和 LAN 发布已完成。
- WORK-001 工作树在同一卡片新增开始/结束工作按钮；`REST_PROMPTED` 时展示开始休息、稍后十分钟和今天跳过三个确定性动作，并在操作后刷新运行态与指标。
- API 模块已覆盖启停和休息回应端点；同组操作增加进行中互斥，避免双击或同时提交启停/休息回应。完整控制台 Vitest 28 文件 90/90、`vue-tsc -b` 和 production build 均通过。

## 下一步操作

由用户强制刷新本地页面，复核“今日陪伴/事务管理”展开结构与聊天输入区；如需外部推送，只推送当前任务分支并由用户自行创建 PR。十四天观察继续按冻结窗口运行。

## 阻塞项

- 自动化实现无阻塞；用户补充权限后再次连接应用内浏览器，仍因插件缓存引用不存在的宿主版本而无法执行视觉冒烟。production build、菜单 Store 测试与 LAN 静态资源检查已通过；WORK-004 其余机器人语音人工验收和固件操作边界不变。

## 关键文件

- `apps/stackchan-console/src/api/modules/`
- `apps/stackchan-console/src/components/AppPageShell/`
- `apps/stackchan-console/src/components/AppMetricCard/`
- `apps/stackchan-console/src/router/modules/`
- `apps/stackchan-console/src/views/dashboard/`
- `apps/stackchan-console/src/views/settings/agent/`
- `apps/stackchan-console/src/views/reminders/`
- `apps/stackchan-console/src/views/personal_tasks/`
- `apps/stackchan-console/src/views/companion/`
- `apps/stackchan-console/src/views/companion/expressions/expressionPackStaging.ts`
- `apps/stackchan-console/src/views/devices/health/`
- `apps/stackchan-console/src/views/notifications/`

## 验证命令与最近结果

- 2026-09-03 聊天滚动复修：控制台 Vitest 32 文件 102/102、`vue-tsc -b`、production build、定向 ESLint/Stylelint 通过；LAN 聊天 CSS/JS 为 200，运行 CSS 同时包含固定工作区高度、`overflow-y:auto`、`overscroll-behavior:contain` 和 `touch-action:pan-y`，本机/LAN 首页及健康均为 200。

- 2026-09-03 二级菜单与聊天输入区复修：控制台 Vitest 32 文件 102/102、`vue-tsc -b`、production build、定向 ESLint/Stylelint 通过；菜单 Store 用例确认“今日陪伴/事务管理”是真实一级分组，主动关心是“今日陪伴”二级项。LAN 运行资源包含新分组、Enter 发送、停止生成和 OKLCH 主题映射，本机/LAN 首页及健康均为 200。

- 2026-09-01 后台信息架构与 UI 重整：控制台 Vitest 30 文件 99/99 通过；`vue-tsc -b && vite build` production build 通过；本任务文件定向 ESLint 与聊天页 Stylelint 通过；`git diff --check` 和 `pnpm docs:check` 通过。测试保留既有 3000 端口拒绝连接和 Vite 关闭超时提示，但 Vitest 成功退出。TDesign Chat 为独立懒加载资源，构建提示该聊天 chunk 超过 500 kB，已由 ADR 0047 限制到单一路由。
- 2026-08-30 WORK-004 默认角色修复：确认默认角色 `00000000-0000-0000-0000-000000000001` 被严格 RFC UUID 版本位误拒绝；共享校验改为与 PostgreSQL UUID 文本边界一致。专项 7/7、完整 Vitest 30 文件 98/98、`vue-tsc -b`、production build、ESLint 和 Stylelint 通过；修复页面已发布，本机/LAN 首页为 200，未认证待办接口为 401。
- 2026-08-30 用户刷新已发布页面后确认默认角色新增正常，不再出现“请选择角色”，该回归点人工验收通过。

- 2026-08-30 WORK-001 管理入口：管理页保存临时周日规则后，固件顶部长按启动/停止均由运行态确认；工作控制请求互斥与动作禁用边界保持不变。

- 2026-08-29 WORK-001/V42 页面发布：本机与 `192.168.1.4:8080` 首页均为 200，运行静态资源包含“工作陪伴由你显式开始”；未认证工作接口为 401。
- 2026-08-29 WORK-001 整体页面：控制台 Vitest 28 文件 90/90、`vue-tsc -b` 和 production build 通过；测试结束的既有 3000 端口拒绝连接与 Vite 关闭超时提示不影响成功退出。工作操作进行中会禁用同组按钮，不新增路由或浏览器持久化。
- 2026-08-29 BODY-001：设备 API 单测纳入校准、显式启用和五种动作；完整控制台 Vitest 28 文件 89/89、`vue-tsc -b` 和 production build 通过。页面按 FaForm/Fa* 组件体系复用既有路由，未新增浏览器持久化；运行静态资源已包含 K151 卡片。

- 2026-08-27 USB 服务地址快捷更新页面已随 LAN server/V40 发布；本机和 `192.168.1.4:8080` 首页为 200，运行配网页资源包含“仅更新服务地址”。CoreS3 未连接或操作，真实快捷更新尚未验收。
- 2026-08-27 USB 服务地址快捷更新：控制台 Vitest 28 个文件 88/88、`vue-tsc -b` 和 production build 通过；既有 3000 端口拒绝连接输出和 Vite 关闭超时提示不影响成功退出。未发布管理页，未连接或操作 CoreS3。
- 2026-08-26 用户确认经纬度输入、固定位置同步和机器人天气问答均正常，前端人工验收通过。
- 2026-08-26 WEATHER-001 经纬度输入修正：控制台 Vitest 28 个文件 86/86、`vue-tsc -b` 和 production build 通过；修正页面已发布，当前 LAN 首页为 200，运行静态资源包含纬度示例 `31.2304`。
- 2026-08-26 WEATHER-001：控制台 Vitest 28 个文件 86/86、`vue-tsc -b` 和 production build 通过；天气 API 专项 4/4。既有 3000 端口拒绝连接输出和 Vite 关闭超时提示不影响成功退出。
- 2026-08-26 WEATHER-001 页面已随 LAN server/V40 发布；本机和当前 `192.168.1.4:8080` 首页为 200，运行静态资源包含 `Open-Meteo`，未认证天气接口为 401，未操作 CoreS3。
- 2026-08-25 CONN-001 Apple 分片修正：根据真实 Shell 结果移除误导性的手机号说明，账号输入改为邮箱类型并提示必须已在 Apple Account 登记验证；控制台 Vitest 28 个文件 85/85、类型检查和 production build 通过。新静态资源已随 LAN server 发布，并确认包含“Apple 账号邮箱”。
- 2026-08-25 CONN-001 手机号兼容：控制台 Vitest 28 个文件 85/85、`vue-tsc -b` 和 production build 通过；API 测试确认手机号沿既有兼容字段提交，不把账号或密码写入浏览器持久化。新页面已发布，本机和 `192.168.1.3:8080` 首页为 200，运行静态资源包含“Apple 账号邮箱或手机号”。
- 2026-08-25 CONN-001 页面随 LAN server/V39 发布；本机和 `192.168.1.3:8080` 首页均为 200，未认证日历接口为 401，未操作 CoreS3。
- 2026-08-25 CONN-001：控制台 Vitest 28 个文件 85/85、`vue-tsc -b` 和 production build 通过；既有 3000 端口拒绝连接输出与 Vite 关闭超时提示不影响成功退出。
- 2026-08-25 WORK-001 阶段 A/V38：控制台 Vitest 28 个文件 84/84、`vue-tsc -b` 和 production build 通过；既有 3000 端口拒绝连接输出与 Vite 关闭超时提示不影响成功退出。
- 2026-08-24 WORK-001 阶段 A 第一切片：控制台 Vitest 28 个文件 83/83、`vue-tsc -b` 和 production build 通过；测试结束保留既有 3000 端口拒绝连接输出与 Vite 关闭超时提示，但进程以 0 退出。
- 2026-08-23 MEDIA-004 使用 Node v24.19.0：主控制台 Vitest 27 个文件 82/82、`vue-tsc -b` 和 production build 通过；新增用例覆盖 EAF multipart 字段和逐片段帧间隔。测试结束仍有既有 Vite 关闭超时提示，但进程以 0 退出。
- 2026-08-23 生命周期动画页面已随 LAN server/V36 发布；本机和 `192.168.1.3:8080` 首页为 200，未认证管理接口保持 401。
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
- [工作日桌面陪伴 V1 开发设计](../workday-companion-v1.md)
- [0041：私用优先的确定性工作日陪伴闭环](../decisions/0041-private-first-deterministic-workday-companion.md)
- [0001：Fantastic-admin 前端](../decisions/0001-fantastic-admin-frontend.md)
- [0047：TDesign Chat 隔离展示层](../decisions/0047-isolated-tdesign-chat-ui.md)
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
- 服务端及其内置管理页面可按用户长期授权直接发布；Git 外部推送和固件操作仍需明确授权。
- 第三方表情项目只作调研参考，不复制代码或素材；页面不允许任意远程动画 JSON，也不把模型标记、诊断或主题色写入浏览器持久化。
