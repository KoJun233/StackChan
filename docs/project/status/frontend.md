# 前端工作流

- 状态：READY
- 最后更新：2026-08-04
- 当前分支：`codex/int-011-memory-2`
- 基准提交：`fbe5bde`
- 最后验证提交：`fbe5bde`

## 当前目标

INT-011 管理端静态资源已随 LAN server 发布；用户确认人工测试无问题并授权推送。建议和正文仍不进入 Pinia 或浏览器持久化。

## 已完成

- 长期记忆 API 和表单增加主题键、1..5 重要度和“允许主动提及”开关；手工新增留空主题键时使用标题，自动建议仍由服务端固定为待确认且未启用。
- 长期记忆列表展示可能重复数量、待替代/已替代关系和重要度；冲突建议使用明确的“确认替代”，确认后旧项保留来源审计但不再提供启用操作。
- 设备交互诊断按回合读取只含记忆 ID、标题、主题、范围和来源说明的投影，明确使用记录不复制记忆正文；API 失败只隐藏该可选解释，不影响既有阶段时间线。
- 新增 API 与页面回归覆盖使用来源 URL 编码、长期记忆 2.0 字段和诊断页来源说明；Node v24.15.0 下 Vitest 24 个文件 66/66、`vue-tsc -b` 和 production build 通过。

- INT-010 不新增页面、路由、浏览器持久化或写入入口；现有管理端在 Node 24.15.0 下通过 Vitest 24 个文件 65/65、`vue-tsc -b` 和 production build。Vitest 仍有既知的关闭超时提示，但命令成功退出。

- INT-009 在“交互与主动陪伴”页面增加连续对话开关和 3..8 秒跟进窗口，明确本地 VAD、最多三个成功跟进回合、从首次唤醒起最多两分钟，以及触摸/静音/离线/错误/“结束聊天”的退出规则。
- 交互设置 API 类型与测试覆盖新增字段；设备诊断时间线增加 `FOLLOW_UP_LISTENING`、`FOLLOW_UP_TIMEOUT`、`CONVERSATION_ENDED` 中文映射，并把既有 `LISTENING_RESUMED` 明确为“恢复聆听”。
- INT-009 在 Node 24.15.0 下通过 Vitest 24 个文件 65/65、`vue-tsc -b` 和 production build；连续对话设置不进入 Pinia 或浏览器持久化。
- 新增“AI 陪伴 → 对话与个人数据”路由和页面；列表、消息详情、筛选、范围导出、单条/整段删除均使用真实管理员 API，删除前明确提示立即失效和不可撤销。
- 页面只读展示最近成功备份、最近恢复验证、7 日/4 周保留数量和存储占用；明确说明历史备份保留期及网页不提供恢复操作。
- 新增 personal-data API、页面和路由测试；官方 Node 24.15.0 容器内前端全量 24 个文件 65/65、`vue-tsc -b` 和 production build 通过，验证镜像摘要为 `sha256:a5c838d459f4be05ea2716149e1ad471d1e98d6fb35d43ab1b26fee15aef987c`。

- “可信 Skill”区域改为“自定义 Skill 包”：使用 `FaFileUpload` 选择完整 ZIP，导入后展示名称、说明、版本、文件数、大小和包内相对文件清单。
- 页面明确导入后默认停用，且 Skill 不获得 Shell、Python、文件系统、新 Tool 或 MCP 权限；支持逐包启停和停用后删除确认。
- 内置 Tool 表只保留 `current_date_time` 与 `list_agent_capabilities`，不再展示三个没有实际用途的查询 Tool。
- 新增 MCP 连接表单和列表，支持连接名称、Base URL、endpoint、NONE/BEARER 认证、新增、编辑和删除；Token 只写不回显，新连接默认停用。
- 当前页面通过 Vitest 22 个文件 62/62、`vue-tsc -b` 和 production build，并已随 V21 LAN server 发布；Skill 和 MCP 生命周期人工验收已完成。
- INT-007 新增“陪伴交互 → 宠物表情包”路由和页面，使用单一 `ref model`、`FaForm/FaFormItem` 与八个 `FaFileUpload`，在浏览器预检 PNG MIME、384 KiB 上限和 `320×240` 尺寸。
- 页面可生成资源包、显示八状态服务端预览、查看设备 `READY/INSTALLING/ACTIVE/FAILED/DISABLED`、启用、恢复默认和删除，并在卸载/重置时释放 blob URL。
- 页面明确展示图片权利和隐私边界；生成动作不直接调用第三方 AI，允许按 runbook 使用任意受控工具生成输入图。
- `7fb9575` 完成语音设置表单、提醒列表/详情 CRUD、时间转换、路由和 API 测试。
- `731a68c` 上 Vitest 通过 37/37，`vue-tsc` 和 production build 通过。
- `e9f072a` 将“设备配对”升级为“设备配网”：使用 `FaForm`/`FaFormItem` 收集 Wi-Fi 与服务地址，通过 Web Serial 直接写入物理 USB 串口，并保留手动一次性配对码入口。
- 配网页面只通过既有 API 生成一次性配对码；Wi-Fi 密码不进入 HTTP 请求、Pinia 或浏览器持久化存储，配网结束后立即清空。
- 新增严格配网载荷、HTTPS/私有 LAN HTTP 地址、非秘密串口状态解析和串口读写测试。
- `121ed8e` 在语音配置页新增 OpenAI-compatible/阿里云百炼提供方选择、Workspace ID 条件字段、实时 Fun-ASR 与 Qwen-Audio-TTS 推荐默认值和提供方专属校验。
- 页面明确百炼首版仅支持华北 2，并将连接测试改名为“测试语音识别与合成”。
- `121ed8e` 上 Vitest 通过 43/43，`vue-tsc` 与 production build 通过。
- `f962d71` 修复语音配置页点击“保存配置”无请求的问题：所有字段显式绑定表单模型，保存按钮改为表单内原生 submit，并在校验失败时显示明确提示。
- 新增页面级回归测试，确认从旧 OpenAI-compatible 配置切换到百炼后会提交 `DASHSCOPE`、Workspace ID 和实时 ASR/TTS 默认值。
- `3e50d56` 进一步确认 `FaFixedBar` 会将按钮 Teleport 到表单外；语音表单增加稳定 ID，传送后的保存按钮通过原生 `form` 属性绑定该表单，回归测试同步模拟真实 Teleport DOM。
- `ac3cee4` 移除会触发 `t._def.defaultValue is not a function` 的 Zod `.default()`，同时保留未挂载提供方字段的表单值。
- `a1e4cd7` 在提交前将非当前语音提供方字段规范为空字符串，避免后端 DTO 对隐藏字段返回 `Invalid input`；前端 Vitest 45/45、`vue-tsc` 和 production build 通过。
- `e41a40f` 为 ASR 和 TTS 分别增加“实时（WebSocket）/非实时（HTTP）”选择，并保留自由模型名输入；切换服务商或模式不会再自动改写模型名。
- 页面明确说明模式严格决定协议且失败不回退；保留 `FaForm`/`FaFormItem`/`FaSelect`/`FaInput` 和 Teleport 固定栏按钮的原生 `form="speech-settings-form"` 绑定。
- `06a67ab` 在现有语音配置页增加“本地唤醒与录音”区域，使用 `FaSelect` 和 `FaNumberField` 配置普通/灵敏唤醒、开始说话阈值和静音阈值，不新增菜单或路由。
- 表单使用 Zod 校验允许范围以及“静音阈值必须小于开始阈值”，并继续保留 Teleport 保存按钮的原生表单绑定。
- 当前任务将任意短语输入和模型上传入口替换为服务端目录驱动的 `FaSelect`；页面只提交目标设备 ID 与白名单模型名。
- 下拉默认选择 `Hi, Stack Chan`，并包含“小峰小峰”等 13 个 ESP-SR 2.4.6 内置选项；页面明确提示实际切换会触发安全 OTA 和设备重启。
- API 层覆盖目录查询、内置模型任务创建和任务轮询；页面继续展示 `READY`、安装、成功、失败和自动回退状态。
- INT-001 在设备总览增加“交互诊断”入口，按设备展示最近回合状态、设备/服务端阶段、相对耗时和安全失败码。
- 时间线明确说明不保存音频、识别文本或机器人回复；空数据、加载失败和手动刷新均有独立反馈，不影响安全停止按钮。
- INT-003 扩展 `VoiceTurnStatus` 为 `CANCELLED`，时间线增加 `TOUCH_STARTED` 和 `CANCELLED` 中文标签，取消保持中性展示且无失败码。
- INT-005 在“AI 陪伴”下新增“人设设置”和“长期记忆”菜单，以及隐藏的记忆新增/编辑详情路由。
- 人设页使用 `FaForm`/`FaFormItem` 管理名字、语气、回复长度、主动程度、话题边界和禁忌，并说明其与底层系统规则的优先级。
- 长期记忆页使用 `FaSearchBar`、`FaTable`、`FaPagination` 和标准列表/详情模式，支持搜索、确认、拒绝、启停、编辑、删除、批量删除和清空。
- 页面显示全局/设备范围、用户档案/事件类别、来源说明和确认状态；待确认建议不会显示为已启用，手工新增明确提示视为用户确认。
- 新增真实 `/api/v1/persona` 与 `/api/v1/memories` 客户端和测试，不引入 fake mock 数据。
- INT-006 提醒表单增加单次/每日/每周、重复间隔和 DST 说明；列表增加重复来源、10 分钟后提醒和跳过下一次操作。
- 新增“交互与主动陪伴”设置路由和 `FaForm` 页面，支持设备选择、音量、夜间模式、跨午夜免打扰、离线策略、主动问候开关/时间窗/限频及立即停止播报。
- 首轮验收发现显式 `v-model` 控件仍被 `FaFormItem` 转发的原生 `input/change` 事件覆盖，数字框和下拉框因此把 Event 对象交给 Zod 并显示 `Invalid input`；公共表单组件现以 `update:modelValue` 为唯一字段值来源，同时保留无显式模型控件的自动绑定行为。

## 正在进行

最终前端全量 Vitest、`vue-tsc -b` 和 production build 回归。

## 下一步操作

人工验收与推送授权已完成；下一步由用户创建 PR、人工审核并合并。

## 阻塞项

当前无阻塞。页面不得把建议、使用记录或正文写入 Pinia/localStorage，不得读取或显示音频、转写、模型响应、凭据或 Tool 参数/结果。

## 关键文件

- `apps/stackchan-console/src/views/settings/agent/index.vue`
- `apps/stackchan-console/src/api/modules/agent.ts`
- `apps/stackchan-console/src/router/modules/settings.ts`
- `apps/stackchan-console/src/views/companion/expressions/index.vue`
- `apps/stackchan-console/src/api/modules/expressionPacks.ts`
- `apps/stackchan-console/src/router/modules/companion.ts`
- `apps/stackchan-console/`
- `apps/stackchan-console/src/views/devices/pairing/index.vue`
- `apps/stackchan-console/src/utils/serialProvisioning.ts`
- `apps/stackchan-console/src/views/settings/speech/index.vue`
- `apps/stackchan-console/src/api/modules/wakeWords.ts`
- `apps/stackchan-console/src/views/devices/overview/index.vue`
- `apps/stackchan-console/src/api/modules/devices.ts`
- `apps/stackchan-console/src/api/modules/personaMemory.ts`
- `apps/stackchan-console/src/views/companion/persona/index.vue`
- `apps/stackchan-console/src/views/companion/memories/`
- `apps/stackchan-console/src/views/devices/overview/index.vue`
- `apps/stackchan-console/src/router/modules/companion.ts`
- `apps/stackchan-console/src/views/settings/interaction/index.vue`
- `apps/stackchan-console/src/api/modules/interactions.ts`
- `apps/stackchan-console/src/views/reminders/`
- `apps/stackchan-console/src/router/modules/settings.ts`

## 验证命令与最近结果

- 2026-08-04 INT-011 正式镜像已发布新的记忆列表/详情和设备诊断静态资源；网页 200、记忆 API 未登录 401，既有认证边界不变。
- 2026-08-02 INT-011 使用 Node v24.15.0：Vitest 24 个文件 66/66、`vue-tsc -b` 和 production build 通过；既有 Vitest close-timeout advisory 不影响退出码。
- 2026-07-31 使用 Node v24.15.0 收尾重跑：Vitest 24 个文件 65/65、`vue-tsc -b` 和 production build 通过；既有 Vitest close-timeout advisory 不影响退出码。
- 用户通过页面启用连续对话后确认免唤醒跟进和静音退出正常；同轮发现的播放杂音不涉及页面数据或浏览器持久化，未读取或显示音频、转写或回复正文。
- INT-009 正式 server 镜像已包含连续对话管理资源 `interaction-BKnLI4ot.js`；网页为 200、未登录交互设置 API 为 401，数据库设置保持关闭/8 秒。未在浏览器持久化配置，未连接或刷写设备。
- `af5bcbe` 固件刷写并上线后，管理页和 API 保持关闭/8 秒默认值；机器侧发布验证未改变浏览器持久化与隐私边界。
- INT-009 使用受支持的 Node v24.15.0：Vitest 24 个文件 65/65、`vue-tsc -b` 和 production build 通过。新增回归覆盖交互设置字段提交与三个连续对话诊断阶段；Vitest 既有 close-timeout advisory 不影响退出码。
- INT-008 在临时受支持的 Node v24.15.0 下完成控制台 Vitest 22 个文件 59/59、`vue-tsc -b` 和 production build；新增回归覆盖 API 请求、路由、隐私边界和紧急总停。Vitest 仍报告既有 close-timeout advisory，但退出码为 0。
- BASE-008 文档刷新未修改前端代码；受支持的 Node v24.15.0 下 Vitest 20 个文件 57/57、`vue-tsc -b` 和 production build 通过。既有 Vitest close-timeout advisory 不影响退出码。
- INT-007 Vitest 20 个文件 57/57、`vue-tsc -b` 和 production build 通过；新增测试覆盖八状态 multipart 生成、设备选择/启停/删除 API 和路由。Vitest close-timeout advisory 与 Node v24.14.0 engine 提示为既有非阻塞警告。
- INT-007 `f91dbdb` 正式镜像已发布表达资源页面静态包；网页根地址为 200，容器内存在 `assets/expressions-CKQoQtb2.js`，未登录表达资源 API 为 401。尚未上传或记录用户图片。
- `8394cb3` 固件首次刷写暴露默认清理导致的 WebSocket 重连，尚未开始上传图片或页面端到端验收；页面代码和已部署静态资源无需修改。
- `b05d60f` 修复固件已稳定在线；管理页面、静态资源和认证边界保持不变，下一步可开始八图上传、预览、启用、恢复默认和删除人工验收。
- 用户确认默认机械眼正常，并明确选择暂缓宠物表情包页面、八图生成、启用和恢复默认测试；尚未上传或记录任何自定义图片。
- INT-006 验收修复使用受支持的 Node v24.15.0：`vue-tsc -b`、Vitest 19 个文件 55/55 和 production build 通过。Vitest close-timeout advisory 与 pnpm 启动进程的 Node v24.14.0 engine 提示均为既有/工具链非阻塞警告，实际测试与构建子进程使用 v24.15.0。
- INT-006 正式镜像已发布交互设置和周期提醒资源；网页根地址为 200，未登录交互设置 API 为 401，既有认证边界保持不变。
- INT-006 `f0d99fa` 正式镜像已发布公共表单事件修复；网页根地址为 200，PostgreSQL/Redis、LAN 端口和认证边界未改变。
- 用户确认 INT-006 六项页面与实体交互验收全部正常；语音模型/音色兼容性修复后连接测试恢复正常。
- INT-005 使用 Node v24.15.0：人设/记忆 API 与路由定向测试 4/4、Vitest 全量 18 个文件 53/53、`vue-tsc -b` 和 production build 通过。Vitest close-timeout advisory 仍为既有非阻塞警告。
- INT-005 正式 Docker 镜像已发布人设页面资源和 `personaMemory` API 资源；网页根地址为 200，未登录 `/api/v1/persona` 与 `/api/v1/memories` 均为 401，确认管理路由受既有认证边界保护。
- INT-005 用户完成人设与长期记忆页面及对话行为验收并确认没有问题，人工验收通过。

- INT-003 使用 Node v24.15.0：针对页面 3/3，Vitest 全量 17 个文件 51/51，`vue-tsc -b` 和 production build 通过。新测试确认触摸发起的取消回合显示中性状态；Vitest close-timeout advisory 仍为已记录的非阻塞警告。
- INT-003 正式 Dockerfile 已将新管理端资源打入 `ca2ec8a` server 镜像并发布；网页根地址返回 200，PostgreSQL、Redis、卷和 LAN overlay 未改变。
- INT-003 `717a8b1` 实机运行后，数据库出现真实 `TOUCH_STARTED` 和 `CANCELLED` 阶段；页面级测试已覆盖相同映射，用户完成的实体取消验收不依赖读取对话正文。
- 当前任务针对测试 2 个文件 4/4、Vitest 全量 17 个文件 48/48、`vue-tsc -b` 和 production build 均通过。
- 本地上传增量使 Vitest 全量达到 17 个文件 50/50；`vue-tsc -b` 和 production build 通过。API 测试确认上传表单数据完整且不覆盖 multipart boundary，页面测试确认上传模式不会调用在线生成接口。
- 内置目录专项前端测试 3 个文件 8/8 通过，覆盖目录获取、模型名提交和页面选择“小峰小峰”；Vitest 仍有已记录的 close-timeout advisory，但进程成功退出。
- 内置目录全量前端验证通过：Vitest 17 个文件 49/49、`vue-tsc -b` 和 production build；正式 Docker 镜像生成并部署 `speech-Bv0-YOqL.js`。
- 2026-07-26 LAN 部署核对：网页根地址返回 200，容器内 `speech-CKv17cKU.js` 包含双模式标签、任务来源和上传按钮；未提交短语、文件或创建模型任务。
- 2026-07-26 LAN 部署核对：网页根地址返回 200，容器内和线上构建均包含自定义唤醒词页面资源 `speech-DFhfjNNt.js`；浏览器访问后进入新版后台登录页。
- `vitest run`：在 `b4876fb` 使用 Node v24.15.0 通过 45/45。
- `vue-tsc -b` 和 production build：在 `b4876fb` 使用 Node v24.15.0 通过。
- 管理页面实测：保存、刷新后持久化和百炼双向测试均在当前完整重建的 LAN server 上通过。
- Vitest close-timeout advisory 仍会在测试成功退出后出现，是已记录的非阻塞警告。
- `e41a40f` 提交后 Vitest 16 个文件、44 个测试全部通过，`vue-tsc -b` 和 production build 通过；页面针对测试确认模式和任意模型名按用户输入提交，切换提供方不会替换模型名。
- `git diff --check` 与 `pnpm docs:check` 通过。
- `06a67ab` 提交后 Vitest 16 个文件、45 个测试全部通过，`vue-tsc -b` 和 production build 通过；回归测试覆盖新字段加载、提交和阈值交叉校验。
- 2026-07-21 LAN 部署后的静态资源核对确认三个新字段均已进入线上语音页面包。
- INT-001 最终验证：Vitest 16 个文件、46 个测试通过，`vue-tsc -b` 和 production build 通过；页面测试覆盖回合时间线和隐私说明。
- INT-001 最新 `master` 合并回归：使用满足 engine 的 Node v24.15.0，Vitest 17 个文件 50/50、`vue-tsc -b` 和 production build 全部通过；保留已记录的 Vitest close-timeout advisory。

## 相关设计、计划和决策

- [下一阶段可执行任务清单](../todo.md)
- [0001：浏览器管理端采用 Vue 3 与 Fantastic-admin](../decisions/0001-fantastic-admin-frontend.md)
- [0003：LLM 提供方配置由管理员管理并保护秘密](../decisions/0003-configurable-llm-provider.md)
- [0006：聊天重试按 clientMessageId 对账并保持幂等](../decisions/0006-chat-retry-idempotency.md)
- [0007：设备本地唤醒、服务端语音适配与持久提醒](../decisions/0007-device-voice-and-durable-reminders.md)
- [0008：管理后台通过 USB 配网，空闲屏保关闭背光](../decisions/0008-browser-usb-provisioning-and-screen-off-idle.md)
- [0009：服务端增加阿里云百炼原生语音适配器](../decisions/0009-native-dashscope-speech-adapter.md)
- [0011：语音协议只由显式接入方式决定](../decisions/0011-explicit-speech-access-modes.md)
- [0012：机器人本地唤醒与录音判定参数由管理员配置](../decisions/0012-configurable-device-voice-detection.md)
- [0013：语音回合使用隐私安全的阶段诊断](../decisions/0013-privacy-safe-voice-turn-diagnostics.md)
- [0015：运行时生成并安全 OTA 自定义唤醒模型](../decisions/0015-runtime-wake-model-generation-and-ota.md)
- [0016：唤醒词仅从 ESP-SR 内置模型目录选择并安全 OTA](../decisions/0016-built-in-esp-sr-wake-model-catalog.md)
- [0018：触摸控制采用本地事件队列与幂等语音回合取消](../decisions/0018-touch-control-and-voice-turn-cancellation.md)
- [0020：长期记忆必须经过确认并按会话范围组装](../decisions/0020-confirmed-scoped-long-term-memory.md)
- [0023：文字与语音共享受控 ReactAgent、Skill、Tool 与 MCP](../decisions/0023-controlled-react-agent-skills-tools-mcp.md)
- [0026：个人数据物理删除、范围导出与隔离备份恢复](../decisions/0026-personal-data-lifecycle-and-isolated-backups.md)
- [0027：连续对话采用设备本地有界跟进窗口](../decisions/0027-bounded-continuous-conversation.md)
- [0029：长期记忆建议经过敏感过滤、冲突确认与有界检索](../decisions/0029-reviewed-memory-suggestions-and-bounded-retrieval.md)

## 安全与兼容性约束

- 不在状态文档中记录浏览器会话、Token 或其他认证秘密。
- Wi-Fi 密码只允许存在于当前表单内存和 USB 串口写入中，不得进入服务端 API、Pinia、localStorage、日志或测试证据。
- 前端应继续兼容既有 Fantastic-admin 边界。
