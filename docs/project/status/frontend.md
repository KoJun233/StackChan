# 前端工作流

- 状态：STABLE
- 最后更新：2026-07-26
- 当前分支：`codex/int-003-touch-controls`
- 基准提交：`37dcb49`
- 最后验证提交：`717a8b1`

## 当前目标

保持已发布的设备管理功能稳定，并让最近语音回合时间线正确显示触摸发起与用户取消，不把取消误标为失败。

## 已完成

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

## 正在进行

INT-003 管理端映射已随 `ca2ec8a` server 镜像发布，LAN 网页根地址返回 200；`717a8b1` 实机已产生真实 `TOUCH_STARTED` / `CANCELLED` 数据，前端类型、标签和中性取消展示与运行数据契约一致。

## 下一步操作

保持当前已发布资源；后续可按需在管理端人工查看真实取消回合，但不再阻塞 INT-003 完成。

## 阻塞项

没有前端软件或发布阻塞。不得输出浏览器会话或运行容器秘密。

## 关键文件

- `apps/stackchan-console/`
- `apps/stackchan-console/src/views/devices/pairing/index.vue`
- `apps/stackchan-console/src/utils/serialProvisioning.ts`
- `apps/stackchan-console/src/views/settings/speech/index.vue`
- `apps/stackchan-console/src/api/modules/wakeWords.ts`
- `apps/stackchan-console/src/views/devices/overview/index.vue`
- `apps/stackchan-console/src/api/modules/devices.ts`

## 验证命令与最近结果

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

## 安全与兼容性约束

- 不在状态文档中记录浏览器会话、Token 或其他认证秘密。
- Wi-Fi 密码只允许存在于当前表单内存和 USB 串口写入中，不得进入服务端 API、Pinia、localStorage、日志或测试证据。
- 前端应继续兼容既有 Fantastic-admin 边界。
