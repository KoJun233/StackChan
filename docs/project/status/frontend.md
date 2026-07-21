# 前端工作流

- 状态：STABLE
- 最后更新：2026-07-21
- 当前分支：`master`
- 基准提交：`ROOT`
- 最后验证提交：`ROOT`

## 当前目标

维护 `apps/stackchan-console` 中的设备配网、双提供方语音配置和提醒 CRUD 页面。

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

## 正在进行

`06a67ab` 已部署到当前 LAN server；线上 `speech-DFdzgFtJ.js` 已确认包含 `wakeSensitivity`、`speechStartThreshold` 和 `speechSilenceThreshold`。服务重建后浏览器需要刷新，管理员会话如已失效则需重新登录。

## 下一步操作

用户刷新并重新登录语音配置页后确认三个新字段可见；保持默认“灵敏 / 350 / 200”或按环境调整并保存。刷入匹配固件后再根据实体日志中的非敏感峰值能量继续校准。

## 阻塞项

没有前端软件阻塞。实体语音验收仍依赖 CoreS3 刷入匹配固件。验证使用本地 Node v24.15.0，不修改仓库 engine；不得输出运行容器秘密。

## 关键文件

- `apps/stackchan-console/`
- `apps/stackchan-console/src/views/devices/pairing/index.vue`
- `apps/stackchan-console/src/utils/serialProvisioning.ts`

## 验证命令与最近结果

- `vitest run`：在 `b4876fb` 使用 Node v24.15.0 通过 45/45。
- `vue-tsc -b` 和 production build：在 `b4876fb` 使用 Node v24.15.0 通过。
- 管理页面实测：保存、刷新后持久化和百炼双向测试均在当前完整重建的 LAN server 上通过。
- Vitest close-timeout advisory 仍会在测试成功退出后出现，是已记录的非阻塞警告。
- `e41a40f` 提交后 Vitest 16 个文件、44 个测试全部通过，`vue-tsc -b` 和 production build 通过；页面针对测试确认模式和任意模型名按用户输入提交，切换提供方不会替换模型名。
- `git diff --check` 与 `pnpm docs:check` 通过。
- `06a67ab` 提交后 Vitest 16 个文件、45 个测试全部通过，`vue-tsc -b` 和 production build 通过；回归测试覆盖新字段加载、提交和阈值交叉校验。
- 2026-07-21 LAN 部署后的静态资源核对确认三个新字段均已进入线上语音页面包。

## 相关设计、计划和决策

- [0001：浏览器管理端采用 Vue 3 与 Fantastic-admin](../decisions/0001-fantastic-admin-frontend.md)
- [0003：LLM 提供方配置由管理员管理并保护秘密](../decisions/0003-configurable-llm-provider.md)
- [0006：聊天重试按 clientMessageId 对账并保持幂等](../decisions/0006-chat-retry-idempotency.md)
- [0007：设备本地唤醒、服务端语音适配与持久提醒](../decisions/0007-device-voice-and-durable-reminders.md)
- [0008：管理后台通过 USB 配网，空闲屏保关闭背光](../decisions/0008-browser-usb-provisioning-and-screen-off-idle.md)
- [0009：服务端增加阿里云百炼原生语音适配器](../decisions/0009-native-dashscope-speech-adapter.md)
- [0011：语音协议只由显式接入方式决定](../decisions/0011-explicit-speech-access-modes.md)
- [0012：机器人本地唤醒与录音判定参数由管理员配置](../decisions/0012-configurable-device-voice-detection.md)

## 安全与兼容性约束

- 不在状态文档中记录浏览器会话、Token 或其他认证秘密。
- Wi-Fi 密码只允许存在于当前表单内存和 USB 串口写入中，不得进入服务端 API、Pinia、localStorage、日志或测试证据。
- 前端应继续兼容既有 Fantastic-admin 边界。
