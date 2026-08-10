# 服务端工作流

- 状态：READY
- 最后更新：2026-08-10
- 当前分支：`codex/ops-002-ota-stack-fix`
- 基准提交：`ce8faaa`
- 最后验证提交：`7e7c55f`

## 当前目标

OPS-002 server/V27、健康中心、`cf26fd7 -> 7e7c55f` 实体应用 OTA 和普通交互人工烟雾测试均已通过，等待推送授权。

## 已完成

- V27 新增不可变固件制品和逐设备升级任务；ESP 应用头、项目名、内嵌版本、大小与 SHA-256 双重校验，活动任务唯一且必须复述当前设备版本。
- 新固件命令只携带固定元数据；artifact 仅允许任务所属设备在 `READY/INSTALLING` 期间经 Bearer token 下载。ACK、安装、失败、超时与回退均持久化且幂等。
- 健康接口只返回版本、Flyway、设备 RSSI/能力、安全错误码、供应商最近人工测试、备份和待处理计数，不返回秘密、地址、模型、音频、转写、回复或完整异常。
- 当前全量 322/322 通过，Testcontainers 从空 PostgreSQL 应用 V1..V27；新增控制器认证、CSRF、制品隔离、健康元数据、严格 WebSocket 和服务层回归。
- `2bd8cdb` 正式镜像已只替换 LAN server，运行镜像为 `sha256:c8224e760c85750abe5b4b048540e028ab8b403d4ecbf1d9f73eb4ee3579cbf3`；公开健康接口与网页为 200、Flyway `27|true`、server 重启和错误为 0。
- 旧 CoreS3 在发布后持续报告 `dd81a7e / motion_disabled / OTA=false`，RSSI 与心跳持续刷新；固件发布、升级任务和活动任务均为 0，server 日志没有 `install_firmware`，旧固件未收到 OTA 命令。
- 一次 USB 引导后，同一设备跨两个心跳周期持续报告 `3ebdb65 / motion_disabled / OTA=true`，RSSI 正常；固件发布和升级任务继续为 0，server 健康 200、重启和错误为 0。
- `3ebdb65 -> 91a8a28` 首次任务与一次诊断重试均进入 `INSTALLING`，但 ACK 为空且设备版本未改变；页面刷新不影响任务。串口证据确认根因是设备传输任务栈溢出，而不是服务端下载、调度或浏览器状态。
- 两条故障任务已分别以 `device_command_unacknowledged` 和 `device_transport_stack_overflow` 标记 `FAILED`，活动 OTA 任务为 0；未公开 artifact，未修改认证、Flyway 或服务端运行资源。
- `cf26fd7` USB 引导后，服务端跨两个心跳周期收到 `cf26fd7 / motion_disabled / OTA=true`，RSSI 为 -46..-48 dBm，近期没有新错误；活动 OTA 任务保持 0。
- `cf26fd7 -> 7e7c55f` 任务在约 10 秒内完成 `READY -> INSTALLING -> INSTALLED`，`command_accepted=true`、失败码为空；目标版本心跳从 `14:13:05Z` 刷新到 `14:13:55Z`，活动任务回到 0，没有回退或新 server 错误。

- 新增 ADR 0030 与 Flyway V26：交互设置增加默认关闭的个性化开关，主动提醒记录可空主题键和 `FIXED/GENERATED/FALLBACK` 状态，设备主题状态保存七天冷却与用户永久静音。
- 现有允许时段、免打扰、在线、语音忙碌、提醒忙碌、最小间隔和每日上限始终先于记忆查询与模型调用；成功占用配额后最多读取一条 `CONFIRMED + enabled + allowProactiveMention` 且通过敏感过滤的当前范围记忆。
- 个性化生成限制为八秒和 2..100 字单句纯文本；供应商失败、超时或 URL、Markdown、医疗/情绪诊断等违规输出均回退现有固定问候。模型调用不持有调度行锁，安全日志只记录失败阶段。
- 同设备同主题创建提醒后七天内不再候选；用户整句说“别再提这个”可永久静音最近主动主题，普通聊天中的引用不误触发，管理员可在页面恢复且不删除原记忆。
- 服务端全量 309/309 通过；Testcontainers 从空 PostgreSQL 应用 V1..V26，并在真实数据库覆盖主动授权筛选和敏感记忆排除。
- 正式镜像 `sha256:bfe34a0206969ccd7fecb073b00025bfe5c1f7f266c55f0aa2e829127ea97cd7` 已只替换 LAN server；健康与网页 200、V26 三个新增列存在、未登录主题 API 为 401、启动错误与重启为 0。
- 用户确认个性化主动问候测试无问题；安全元数据核对最新主动提醒为 `DELIVERED + GENERATED`、主题存在、尝试 1 次、失败码为空，未读取提醒或记忆正文。

- 新增 ADR 0029 与 Flyway V25：启用 `pg_trgm`，为长期记忆增加 `topicKey`、1..5 重要度、最后使用时间、来源回合、替代关系和允许主动提及开关；新增只保存 `turnId + memoryId + usedAt` 的使用记录表。
- 网页和设备语音普通成功回合结束后异步执行至多一次结构化建议提取；供应商失败、畸形 JSON、取消/失败/重放和语音动作回合均不产生建议。自动建议固定为 `ASSISTANT_SUGGESTED + PENDING + disabled`，范围和来源回合由应用绑定。
- 应用侧拒绝凭据、精确地址、身份号码、银行卡号及财务/医疗推断；拒绝内容、完整用户表达、机器人回答、模型响应和 Tool 参数/结果均不进入日志或审计。
- 同范围同 `topicKey` 建议只记录待替代旧项；管理员确认后才以行锁停用旧项并保留双向替代审计，旧项无法重新进入上下文。修改、拒绝、停用和删除均在下一轮检索中立即生效。
- 上下文检索综合 `pg_trgm` 相似度、重要度、最后使用和更新时间，每轮最多 8 条、渲染约 4,000 字符；扩展查询不可用时回退普通索引和确定性排序。成功回合幂等记录实际选择的记忆 ID。
- 管理 API 增加不含记忆正文的按回合使用来源投影，返回记忆 ID、标题、主题、范围、来源与来源说明；真实 PostgreSQL 已验证 V1..V25、相似度排序和使用记录写入。
- INT-011 服务端全量 299/299 通过；Testcontainers 从空 PostgreSQL 应用 V1..V25，覆盖原生 `pg_trgm` 检索、普通索引回退、建议安全过滤、冲突确认、使用记录幂等和成功/失败回合边界。
- 本地候选 `09323bb` 已只替换 LAN server，运行库从 V23 成功应用 V24/V25；健康与网页 200、迁移 `25|true`、记忆 API 未登录 401、启动后错误 0，旧 `dd81a7e / motion_disabled` 自动恢复新鲜心跳。
- 用户完成发布后的记忆与实体链路测试并确认无问题；验收时产生 1 条 `PENDING + disabled` 建议，符合“确认前不进入上下文”的边界。核对只读取状态与数量，未读取记忆或对话正文。

- 新增 ADR 0028 与 Flyway V24：动作提案绑定固定管理员、设备、会话和来源回合，保存两分钟有效期、白名单字段和独立安全审计；临时免打扰只增加服务端字段，SCV1 与固件协议不变。
- 明确动作先由确定性路由处理；复杂明确表达使用独立 ReactAgent，且只注册 `submit_voice_action_proposal`。该 Tool 只能保存经应用校验的提案，不能直接调用提醒、设置、记忆或设备服务；畸形输入、越权枚举、未调用 Tool 和提供商失败均不产生业务副作用。
- 提醒、稍后、跳过下一次、临时免打扰和音量必须确定性复述并由同设备同会话确认；确认使用提案行锁保持幂等，取消、过期、跨范围和重放均不能重复执行。“记住”仅创建 `ASSISTANT_SUGGESTED + PENDING + disabled` 建议。
- 普通 Agent 新增当前设备范围的下一条提醒和待确认记忆数量两个只读 Tool；模型不能提供设备 ID。动作审计不保存用户原话、模型回复、业务正文或 Tool 参数/结果。
- 服务端全量 290/290 通过，Testcontainers 从空 PostgreSQL 成功应用 V1..V24；新增定向测试覆盖普通聊天不误触发、取消、过期、重复确认、跨设备确认、临时免打扰和提案 Tool 不直接调用业务服务；既有 SSE 回放测试补齐异步完成等待，消除 MockMvc 响应头并发竞态。

- INT-009 新增 ADR 0027 与 Flyway V23：设备级交互设置保存连续对话开关和 3..8 秒跟进窗口，默认关闭；诊断阶段扩展 `FOLLOW_UP_LISTENING`、`FOLLOW_UP_TIMEOUT` 和 `CONVERSATION_ENDED`。
- `configure_interaction` 按开关兼容下发：关闭时维持旧固件可接受的四字段命令，启用时下发六字段命令；跟进静音超时和明确结束均按正常完成语义收口，不保存静音音频、转写或回复正文。
- INT-009 服务端全量 278/278 通过；Testcontainers 从空 PostgreSQL 成功应用 V1..V23，覆盖 V23 约束、四/六字段兼容、控制器映射和新增阶段白名单。
- 修正 DashScope Qwen-TTS 非实时 HTTP 协议：与非实时 ASR 共用 Workspace 的 `multimodal-generation/generation` 端点，`input` 同时承载 `text` 和 `voice`，不再发送该接口不接受的 `format` / `sample_rate`；保留模型名原样传递、结果 WAV 标准化和安全阶段日志。
- DashScope TTS 的 4xx 日志新增受限诊断：只记录 HTTP 状态、`request_id`、供应商错误码、最多 240 字符且移除控制字符的 message 和本地异常类型；不记录请求头、Workspace URL、完整响应、API Key、音频、转写或回复正文。
- DashScope 临时音频下载继续使用精确 SSRF 白名单：在既有北京 OSS 主机之外，新增当前 Qwen-TTS 实际返回的乌兰察布 OSS 主机；HTTP 签名 URL 仍升级为 HTTPS，拒绝任意子域、后缀伪造、用户信息、非默认端口和片段。
- 供应商合成结果允许把实测的 24 kHz、16-bit、单声道 PCM 线性重采样为设备要求的 16 kHz，并重建规范 WAV；设备上传和 ASR 输入仍严格拒绝非 16 kHz，其他供应商采样率也继续拒绝。
- 新增 ADR 0026 和 Flyway V22：单条消息物理删除时把回复引用置空而不连带删除，整段对话级联删除消息/设备映射；正在流式生成的消息或对话返回冲突，不与生成写入竞争。
- 新增 `/api/v1/personal-data` 管理 API，支持设备、更新时间和关键词过滤、分页消息查看、单条/整段删除及相同范围 JSON 导出；导出不包含认证、模型配置、诊断日志或 Tool 参数/结果。
- 新增 `BackupStatusService` 只读投影，只返回尝试/成功/失败时间、恢复验证结果、日/周数量、保留上限和存储占用，不返回路径、dump、manifest、摘要、凭据或密钥。
- 后端全量 274/274 通过；Testcontainers 从空 PostgreSQL 成功应用 V1..V22。当前 V21 运行库的临时备份卷完成真实逻辑备份、SHA-256、空库恢复和人设/确认记忆/提醒/表达资源包计数比对，临时卷已删除。

- 新增 ADR 0024 和 Flyway V20 `agent_skills`；Skill 以完整 ZIP 导入，元数据存 PostgreSQL，完整目录存 `COMPANION_AGENT_SKILLS_DIRECTORY`，Compose 使用独立持久卷。
- 新增 ADR 0025 和 Flyway V21 `agent_mcp_connections`；管理员可管理 Streamable HTTP 连接，Bearer Token 由既有 AES-256-GCM 密钥加密保存，API、页面和日志不返回明文。
- `ManagedMcpClientRegistry` 为每条连接创建独立客户端，修改或删除后关闭旧客户端并清缓存；连接无需重启即可重新发现，同时保留 HTTPS、LAN 私网 HTTP、连接授权和逐 Tool 摘要授权边界。
- ZIP 导入拒绝路径穿越、绝对路径、重复路径、链接、加密/特殊条目、非法单/多 Skill 结构和超限包；`references/`、`examples/` 等文件原样保留并在管理 API 列出，任何失败清理 staging。
- Skill 导入后默认停用，启停在下一回合通过 `FilteringSkillRegistry` 生效；已启用包必须先停用再删除，删除使用隔离移动和数据库事务补偿。
- 移除 `reminder-query`、`memory-query`、`device-status` 演示 Skill 及三个未实际开放的查询 Tool；当前保留 `current_date_time` 和 `list_agent_capabilities`。
- V21 后端 Maven 全量 266/266 通过，Testcontainers 从空 PostgreSQL 成功应用 V1..V21；正式 LAN 镜像已运行 V21，Skill 包与 MCP 端到端人工验收已完成。
- ReactAgent 直接 Tool 通过 `.tools(List<ToolCallback>)` 注册，测试同时断言最终模型请求的 `OpenAiChatOptions.toolCallbacks` 包含 `current_date_time`；已启用的文件系统 Skill 通过 `SkillsAgentHook` 注册 `read_skill`。
- 安全模型包装层保留提供商原生 `OpenAiChatOptions`；DeepSeek 官方 V4 Agent Tool 请求单独关闭思考模式，避免当前 OpenAI 适配器缺失 `reasoning_content` 回放或与强制 `tool_choice` 冲突，普通 ChatClient 对话不受影响。
- 此前未提交 Tool 修复的 Maven 全量测试通过 256/256；运行镜像已部署，用户已确认 `current_date_time` 与 `list_agent_capabilities` 可调用。
- 实机复测确认 Tool 审计仍为 0 后，修正 ReactAgent 创建链路：`SafeChatModel` 允许框架反射读取默认选项，Builder 显式传入 Agent 专用 `OpenAiChatOptions`，移除自定义强制 `tool_choice`，并使用 `agent.call(messages)` 执行完整 Tool 循环；同时只记录提供商失败 HTTP 状态码，不记录请求、回复或凭据。
- INT-007 新增 V18 `expression_packs`、`expression_pack_states` 和 `device_expression_packs`，保存版本化制品、八状态预览和设备安装状态。
- 服务端要求恰好八张可真实解码的 `320×240` PNG，限制单图 384 KiB/制品 1.5 MiB，生成逐图及整体 SHA-256，并只向已选择该包的认证设备提供同源下载。
- 安装调度使用 `READY -> INSTALLING -> ACTIVE/FAILED`，负载或连接失败可重试；设备重连会幂等补发当前包或清理命令，启用中的资源包禁止删除。
- `2384587` 完成加密语音提供方设置和 OpenAI-compatible ASR/TTS 客户端。
- `ac7c171` 完成设备鉴权语音回合和 `SCV1` 响应封装。
- `cbd5095` 完成持久提醒、每秒调度、离线保留、播放 ACK 和超时恢复。
- `731a68c` 上 Maven 全量测试通过 153/153。
- `121ed8e` 增加显式语音提供方适配层、`provider_type`/Workspace ID 迁移、Fun-ASR WebSocket 双工状态机、16 kHz WAV 到 PCM 校验与流式发送，以及 Qwen-Audio-TTS 非流式 HTTP 调用。
- 百炼 TTS 临时音频只允许从固定北京 OSS 结果主机下载，文档中的 HTTP URL 会升级为 HTTPS；下载限制为 8 MiB 并验证 RIFF/WAVE。
- “测试语音识别与合成”现在先合成短 WAV 再执行 ASR，能够同时验证 TTS 和 ASR，而不是只验证音频生成。
- `121ed8e` 上 Maven 全量测试通过 164/164。
- `d14eda0` 增加不记录凭据、Workspace ID、响应正文或音频的供应商阶段诊断。
- `dee2d7f` 仅在服务端合成后用于 ASR 自测的路径上兼容百炼 WAV 的超大 `data` 占位长度；设备上传仍保持严格 WAV 长度校验。
- `29aef87` 记录安全的语音连接测试成功事件，不输出认证信息或供应商正文。
- `f0dcecd` 将百炼输出的 16 kHz、16-bit、单声道 PCM 重建为标准 WAV 后再交给设备播放，同时保持设备上传路径的严格 WAV 校验不变。
- `6286c34` 将设备语音回合的 LLM 阶段从非流式 `.call()` 改为 30 秒有界流式接收并在服务端聚合完整文本，同时追加最多两句话、不使用 Markdown 的语音回答指令；网页聊天流式路径不变。
- `e41a40f` 增加独立 `asr_mode` / `tts_mode`，把模式作为唯一协议路由信号；百炼实时 ASR/TTS 只走 WebSocket，非实时 ASR/TTS 只走 HTTP，模型名原样传递且失败不切换协议。OpenAI-compatible 当前仅实现非实时 HTTP，选择实时会直接失败。
- `e41a40f` 增加百炼非实时 ASR 的同步多模态 HTTP 请求、实时 TTS WebSocket 生命周期和二进制 WAV 聚合，并继续执行 16 kHz 单声道 WAV 标准化。
- `06a67ab` 增加 Flyway v10，为语音设置保存唤醒灵敏度、开始说话阈值和静音阈值；保存后向在线设备广播严格的 `configure_voice_detection` 命令，设备重连时也会补发当前配置。
- `06a67ab` 对本地语音参数执行服务端范围和交叉校验，配置命令沿用既有 `command_ack`，但不会改变提醒交付状态。
- 当前任务保留 Flyway V11 持久任务和三槽 OTA，取消任意短语生成、第三方生成器与模型包上传接口。
- 增加固定内置模型目录 API；创建任务只接受白名单模型名，服务端从随镜像发布的 ESP-SR 2.4.6 目录读取三个官方模型文件并现场生成连续 `srmodels.bin`。
- 首批 13 个选项覆盖 WakeNet9/WakeNet9l，包括 `wn9_xiao3feng1xiao3feng1_tts3`；每个选项均通过真实文件打包、SHA-256、结构和 1 MiB 上限测试。
- 服务端通过固定同源 URL 下发安装命令，接收设备重启后的安装/回退状态，且 WebSocket 重连补报保持幂等；提醒 ACK 路由不受影响。
- 部署验证发现 READY 任务调度器在事务外执行悲观锁查询；当前任务树已将候选任务读取放入 `TransactionTemplate`，并补充事务次数回归断言，部署后未再出现 `Query requires transaction be in progress`。
- V12 仅作为已经部署过的迁移历史保留；V13 终止旧版活动任务并删除临时 `source` 字段，最终 API 和实体不再暴露生成或上传来源。
- INT-001 增加 Flyway V14、语音回合和阶段事件模型、7 天保留清理，以及管理员只读的最近回合接口；诊断表不含音频、识别文本、模型回复、供应商响应或认证载荷。
- 语音上传接受可选 `X-StackChan-Turn-Id` 并在成功响应中回显；旧固件省略时由服务端生成，SCV1 正文保持不变。
- 设备 WebSocket 增加严格 `voice_turn_stage` 事件，只接受规范 UUID、白名单阶段、0..300000 毫秒相对耗时和白名单失败码；自由文本和额外字段会被拒绝。
- ASR、LLM、TTS 和安全失败分类由服务端直接记录；诊断存储临时不可用时不会中断既有语音主流程。
- INT-005 新增 Flyway V16：单例结构化人设表和长期记忆表，长期记忆记录全局/设备范围、用户档案/事件类别、来源说明、确认状态、启用状态和时间戳。
- 新增管理员人设 API，以及记忆搜索、读取、手工创建、编辑、待确认建议、确认、拒绝、启停、删除和清空 API；手工创建直接确认，机器人建议默认待确认且停用。
- 新增统一提示词组装器；浏览器文本聊天只加载全局记忆，设备语音加载全局和当前设备记忆，且两条路径都只查询已确认并启用的记录。
- 提示词明确把记忆视为数据而非系统指令，并固定基础规则、人设、用户档案、事件记忆、渠道规则和当前会话的优先顺序。每轮重新查询 PostgreSQL，删除和停用无需等待缓存失效。
- INT-006 新增 Flyway V17、每日/每周周期提醒、本地墙钟锚点、稍后提醒、跳过下一次和最近完成结果；重复实例收到 ACK 后在同一事务中推进并清除旧命令 ID。
- 新增设备级交互设置、跨午夜免打扰、三种离线错过策略、音量、夜间模式、固定文本主动问候时间窗/间隔/每日上限和事务锁计数。
- 主动问候只由确定性配置、在线状态和忙碌仲裁创建 `PROACTIVE` 提醒，不调用 LLM 决定时机或内容；语音回合和已派发提醒期间会延期。
- 新增严格 `configure_interaction`、`stop_audio` 下发及重连补发路径；旧固件忽略未知命令时，原有提醒和语音路径仍可继续使用。
- 首轮验收定位到停止命令只中断设备播放、未终止服务端 `RESPONSE_READY` 回合，导致提醒每分钟顺延且主动问候持续被忙碌仲裁抑制；当前工作树在停止成功后批量终止该设备活动回合，并只将最近 15 分钟更新的活动回合视为忙碌，避免异常断线永久阻塞调度。

## 正在进行

server/V27 和健康中心保持已验收运行态；`7e7c55f` 已通过真实 OTA 和普通交互人工验收，没有活动 OTA 任务。

## 下一步操作

压缩临时提交为单一中文任务提交；只有用户明确说“推送吧”才推送当前任务分支。

## 阻塞项

回退演练仍需新的逐次授权，本轮明确不执行。不得保存或记录认证载荷、下载 URL、音频、转写或回复正文。

## 关键文件

- `server/src/main/java/com/kj/stackchan/expression/`
- `server/src/main/java/com/kj/stackchan/api/ExpressionPackController.java`
- `server/src/main/resources/db/migration/V18__expression_resource_packs.sql`
- `server/`
- `server/src/main/java/com/kj/stackchan/wakeword/`
- `server/src/main/resources/db/migration/V11__wake_word_model_ota.sql`
- `server/src/main/resources/db/migration/V12__wake_word_model_job_sources.sql`
- `server/src/main/resources/db/migration/V13__retire_generated_wake_models.sql`
- `server/src/main/resources/db/migration/V14__voice_turn_diagnostics.sql`
- `server/src/main/resources/db/migration/V15__voice_turn_cancellation.sql`
- `server/src/main/resources/db/migration/V16__persona_and_long_term_memory.sql`
- `server/src/main/resources/db/migration/V25__long_term_memory_2.sql`
- `server/src/main/resources/db/migration/V17__proactive_interaction_and_recurring_reminders.sql`
- `server/src/main/java/com/kj/stackchan/interaction/`
- `server/src/main/java/com/kj/stackchan/reminder/`
- `server/src/main/java/com/kj/stackchan/persona/`
- `server/src/main/java/com/kj/stackchan/memory/`
- `server/src/main/java/com/kj/stackchan/wakeword/EspSrWakeWordModelCatalog.java`
- `server/src/main/java/com/kj/stackchan/speech/VoiceTurnDiagnosticsService.java`
- `server/src/main/java/com/kj/stackchan/speech/VoiceTurnCancellationService.java`
- `docs/project/decisions/0019-complete-voice-replies.md`
- `docs/project/decisions/0020-confirmed-scoped-long-term-memory.md`
- `docs/project/decisions/0021-limited-proactive-interaction.md`
- `docs/project/decisions/0027-bounded-continuous-conversation.md`
- `server/wakenet-models/`

## 验证命令与最近结果

- 2026-08-10 用户确认 OTA 后普通唤醒对话、播放中触摸停止和后续再次对话均正常；服务端运行态无须调整。
- 2026-08-10 实体应用 OTA 任务完成为 `INSTALLED / command_accepted=true / failure_code=''`，设备版本从 `cf26fd7` 更新到 `7e7c55f` 并跨两个心跳周期保持 `motion_disabled / OTA=true`；活动任务为 0，没有回退或新 server 错误。
- 2026-08-10 `cf26fd7` USB 引导后，数据库心跳从 `13:57:59Z` 刷新到 `13:59:15Z`，均为 `motion_disabled / OTA=true`；RSSI 正常，活动 OTA 任务为 0，server/V27 无新错误。
- 2026-08-10 修复工作树服务端全量 322/322 通过；Testcontainers PostgreSQL 从空库应用 V1..V27。服务端源码、运行容器、数据库和认证边界未修改。
- 2026-08-10 两条 `INSTALLING` 任务均无 ACK，设备始终报告 `3ebdb65`；串口诊断确认设备侧传输任务栈溢出。任务已安全标记失败且活动数为 0，server/V27、Flyway、容器和认证边界未改。
- 2026-08-04 从本地干净候选 `09323bb` 使用正式 Dockerfile 构建并只替换 LAN server；V23→V25 迁移成功，`pg_trgm`、7 个新记忆字段和使用记录表存在，健康/网页 200、未登录记忆 API 401、server 错误 0。PostgreSQL、Redis、备份容器、固件和 NVS 未改变。
- 2026-08-02 INT-011 收尾运行 `mvn -f server/pom.xml test`：299/299 通过；Testcontainers PostgreSQL 从空库应用 V1..V25，原生 `pg_trgm` 检索和使用记录持久化通过。未连接运行数据库、未执行 V24/V25 运行迁移或替换容器。
- 2026-07-31 收尾重跑 `mvn -f server/pom.xml test`：282/282 通过，Testcontainers PostgreSQL 从空库应用 V1..V23；运行 server 健康 200、重启 0。
- `62c727b` 使用正式 Dockerfile 构建镜像 `sha256:090a3117fe970da14b53d2278df5967629abf5ae912c19e1e83bec9f08cd1d8b`，只替换 LAN server 为 `a3c3eb6efeb4`；健康 200、Flyway `23|true`、重启 0，CoreS3 保持 `dd81a7e / motion_disabled`。当前配置的不落盘端到端直连验证为 TTS 200、音频下载 200、规范化 16 kHz、ASR 200 且转写非空；未保存或输出秘密、签名 URL、音频或转写。
- 用户确认发布后的页面语音测试成功，实体播放不再出现电流音；24→16 kHz 合成兼容的人工验收通过。
- `2ec69e1` 正式 Dockerfile 构建镜像 `sha256:6a79deb2eded1541e22ae367b39d8c3d3d8ac70413a38394af1b4b01ad41f681` 并只替换 LAN server 为 `4ddd62c9ee5d`；健康 200、Flyway `23|true`、重启 0、LAN `0.0.0.0:8080`，当前 TTS 配置正确，数据库收到 4 秒内的 `dd81a7e / motion_disabled` 心跳。发布后 50 秒观察窗没有新的页面语音测试请求。
- `1cd6f76` 正式 Dockerfile 首次构建因 Maven Central TLS 握手中断失败，重试成功构建镜像 `sha256:76d5a78c0504bc339cb1b37260c9e7e4acf351caef58daf98fbfe3e912277349` 并只替换 LAN server。健康/网页 200、Flyway `23|true`、重启 0、LAN `0.0.0.0:8080`，数据库收到 12 秒内的 `dd81a7e / motion_disabled` 心跳。
- 一次性 Java 21 内存探测使用运行库的加密配置直连相同 Workspace：当前 `qwen-tts + Dylan` 返回 400 / `InvalidParameter`，供应商说明该版本只接受四个基础音色；`qwen-tts-latest + Dylan` 返回 200 且存在音频 URL。探测辅助文件已删除，未输出或写入 API Key、Workspace ID、请求正文、音频或转写。
- 官方 Qwen-TTS 文档核对确认非实时端点与 `input.text` / `input.voice` 结构；安全错误诊断定向通过 15/15，`mvn -f server/pom.xml test` 全量通过 279/279。新增回归覆盖 request ID、错误码和控制字符受限 message 提取。
- 当前配置直连返回 HTTP 200，音频 URL 的安全摘要为 `http` / `dashscope-result-wlcb.oss-cn-wulanchabu.aliyuncs.com`；升级 HTTPS 后下载 200、88,364 字节且 RIFF/WAVE 魔数匹配。辅助探测文件已删除，未输出签名 URL、API Key、Workspace ID、音频或正文。
- 乌兰察布精确结果主机与伪造后缀拒绝回归定向通过 16/16，服务端 Maven 全量通过 280/280。
- 当前配置的安全 WAV 头探测为 PCM format 1、单声道、24 kHz、16-bit、88,320 字节音频数据；未保存或输出音频正文。24→16 kHz 合成重采样、非 16 kHz 设备输入拒绝和适配器定向通过 20/20，服务端 Maven 全量通过 282/282。
- 播放加固 `dd81a7e / motion_disabled` 刷写启动后恢复 WebSocket，数据库在核对时收到 13 秒内的新鲜心跳；server 健康 200、运行中且重启 0，Flyway 保持 V23。未变更 server、数据库、凭据或模式。
- 用户确认新固件免唤醒跟进和静音退出正常；设备在完整接收服务端 WAV 后才开始播放，因此偶发播放杂音不归因于服务端网络分块。诊断未读取或记录音频、转写或回复正文。
- INT-009 `a2f723e` 正式镜像只替换 server，Flyway 从 V22 升至 V23；健康/网页为 200、未登录交互设置为 401、设置默认关闭/8 秒、启动错误数和重启数为 0。旧 `b05d60f / motion_disabled` 自动重连并跨两个 25 秒周期刷新心跳；PostgreSQL、Redis、备份容器与固件未变。
- 用户确认 V23 与旧 `b05d60f` 的普通单轮语音正常，完整播放并恢复 WakeNet；server-first 人工兼容 smoke test 通过。
- 新固件 `af5bcbe / motion_disabled` 刷写后跨两个心跳周期在线；Flyway 保持 V23、连续对话设置关闭/8 秒，server 健康 200、重启和错误为 0。
- INT-009 `mvn test` 全量 278/278 通过；Spring/Testcontainers 从空 PostgreSQL 应用 V1..V23。新增回归覆盖连续对话默认关闭、3..8 秒数据库约束、四/六字段配置兼容、控制器映射和三个隐私安全阶段。
- 自定义 Skill 工作树的主源码和测试源码已进入 `javac` 编译阶段且无 Java 诊断错误；当前 Codex 沙箱在编译器关闭依赖 JAR 时触发 `AccessDeniedException`，因此本轮 Maven 测试与 V20 Testcontainers 结果仍待正常构建环境复核。此前 DeepSeek V4 Tool 修复 Maven 全量 256/256 与 V1..V19 已通过并发布。
- BASE-008 文档刷新未修改服务端代码；在合并基线 `8122959` 上运行 Maven 全量测试 244/244 通过，Testcontainers 套件从空 PostgreSQL 应用 V1..V18。未连接运行数据库、未执行迁移或替换容器。
- INT-007 `mvn test` 全量 238/238 通过，包含真实 PNG 解码、八状态完整性、尺寸和截断拒绝测试；Spring/Testcontainers 空库上下文成功应用 V1..V18。测试 profile 已关闭表达资源调度器，避免容器结束后计划任务访问已关闭数据库。
- INT-007 `f91dbdb` 已从正式 Dockerfile 构建为 `sha256:b7a8ea8c36b212d1e25e1f98a8a933c78fcbc530d247520e1ec0a34e270da321` 并只替换 LAN server，容器为 `b2d7e1f6e44e`；健康与网页为 200、Flyway `18|true`、成功迁移数 18、三张表达资源表存在、未登录表达资源 API 为 401、近期错误数为 0。CoreS3 `2465427 / motion_disabled` 恢复新鲜心跳。
- `8394cb3` 固件首次连接时，服务端按默认资源状态下发 `clear_expression_pack`；服务端健康保持 200 且无错误，但固件同步擦除双槽后无法及时发送 ACK，设备进入重连。修复位于固件幂等清理和任务队列，server/V18 无需再次部署。
- `b05d60f` 修复固件完整刷写后，服务端继续保持健康 200、错误数 0；设备一次建立 WebSocket 后跨两个 25 秒心跳周期持续刷新 `b05d60f / motion_disabled`，无需更换 server 或迁移数据库。
- 用户确认默认机械眼人工验收通过，并选择暂缓无素材的八图生成、安装和恢复默认实体测试；服务端自动化覆盖与运行认证边界保持既有通过结果。
- INT-006 验收修复后服务端全量 236/236 通过；Testcontainers PostgreSQL 从空库应用 V1..V17。新增测试覆盖停止成功/失败的回合终止边界、活动回合批量取消，以及提醒和主动问候只检查最近 15 分钟的忙碌状态。
- INT-006 `9495111` 已只替换现有 LAN server，Flyway 为 `17|true`；健康与网页为 200，未登录交互设置 API 为 401，启动错误数为 0，旧 CoreS3 `717a8b1 / motion_disabled` 恢复新鲜心跳。
- INT-006 验收修复 `f0d99fa` 已只替换 server，镜像为 `sha256:0f780fd7264c0137238e89783bc6e172cf61fe4b2ad23e5a3a329ea10b95d1cc`、容器为 `4d9db38c136c`；健康与网页为 200、Flyway `17|true`、迁移数 17、启动错误数 0。首次派发在重连窗口丢失 ACK，五分钟恢复任务重试后单次提醒 `DELIVERED`；主动问候下一周期一次 `DELIVERED`。
- CoreS3 完整刷入 `2465427` 后恢复 WebSocket，数据库收到 `2465427 / motion_disabled` 新鲜心跳；server 健康保持 200、Flyway 保持 `17|true`，近 15 分钟未见 server 错误。
- 用户确认 INT-006 六项验收和语音识别/合成均正常；供应商兼容性与凭据轮换由用户完成，未将秘密或完整认证载荷写入仓库。
- CoreS3 完整刷入 `c1d7383` 后恢复 WebSocket，数据库收到 `c1d7383 / motion_disabled` 新鲜心跳；server 健康保持 200、Flyway 保持 `17|true`，近期启动错误数为 0。
- INT-005 定向测试覆盖人设保存、手工记忆确认、模型建议待确认、设备范围校验、提示词顺序、管理 API、文本 SSE 和设备语音；全部通过。
- INT-005 服务端全量 225/225 通过；Testcontainers PostgreSQL 从空库验证并应用 V1..V16。部署后运行库为 `16|true`，人设单例记录 1 条、长期记忆初始记录 0 条；健康与网页根地址为 200，未登录人设/记忆 API 均为 401，启动错误数为 0。CoreS3 `717a8b1 / motion_disabled` 恢复心跳，未改动固件。
- INT-005 用户完成人设与长期记忆行为验收并确认没有问题；没有为验收读取或记录对话正文。

- INT-003 `mvn test` 全量通过 217/217；Testcontainers PostgreSQL 从空库成功应用 15 个迁移至 V15。覆盖取消早于请求、活动 LLM/TTS 中断、设备归属隔离、严格 ACK `result`、提醒取消和旧固件兼容。
- INT-003 LAN 发布只替换 `stackchan-foundation-server-1`；健康接口和网页根地址均为 200，Flyway `15|true`，近期错误数为 0。旧固件 `216d383 / motion_disabled` 恢复心跳，用户完成的最新回合为 `COMPLETED`，包含 `REQUEST_RECEIVED`、ASR、LLM、TTS、播放开始/完成和恢复监听。
- INT-003 `717a8b1` 实机验收后，最近 30 分钟隐私安全汇总包含 5 次 `TOUCH_STARTED`、1 个 `CANCELLED` 终态和正常完成回合；用户确认聆听、处理和播放阶段均可短触取消，播放可立即停止。未读取或记录音频、识别文本、机器人回复或认证载荷。
- INT-004 修改前 `VoiceTurnServiceTest` 4/4 通过；新增句子边界、Unicode 上限、上游流早停和取消回归后针对测试 8/8、服务端全量 221/221 通过。Testcontainers PostgreSQL 从空库应用 15 个迁移至 V15；没有新增数据库迁移、协议字段或内容诊断。
- INT-004 `5016324` 已只替换现有 server；运行健康与网页根地址为 200，Flyway 保持 `15|true`，PostgreSQL/Redis 未重建，CoreS3 `717a8b1 / motion_disabled` 恢复新鲜心跳，近期错误数为 0。部署后测量起点为 `2026-07-26 10:48:44+00`，当时语音回合总数为 51。
- INT-004 首版部署后共有 11 个成功回合且无失败或取消。录音结束到播放开始 P50/P95 为 `5978/7158 ms`，上传到播放 P50/P95 为 `5921/7105 ms`，ASR/LLM/TTS P50 为 `381/3583/1403 ms`，服务端总 P50/P95 为 `5443/6021 ms`；中位延迟没有改善。第二版针对测试 8/8、服务端全量 221/221、Flyway 空库至 V15、文档检查和 7/7 文档测试通过。
- INT-004 第二版 `3d8c1fb` 已只替换现有 server；运行镜像为 `sha256:192ed2297336577bf96b3b1479f7c9c11336ceceba9fccb907a7f0e72a78e9a3`，回退标签为 `stackchan-foundation-server:rollback-3d8c1fb-pre-int004-v2`。server 容器由 `b17ee0d4b280` 变为 `c97e6e139830`，PostgreSQL/Redis 未重建；健康与网页根地址为 200，Flyway `15|true`、迁移数 15，CoreS3 `717a8b1 / motion_disabled` 恢复心跳，近期错误数为 0。
- INT-004 第二版从 `2026-07-26 12:12:21.103106+00` 起共有 11 个成功回合和 1 个 `NO_SPEECH`。录音结束到播放开始 P50/P95 为 `6139/7430 ms`，上传到播放为 `6081/7381 ms`，ASR/LLM/TTS 为 `328/712`、`3635/4621`、`1564/1755 ms`，服务端总耗时为 `5543/6835 ms`；性能验收未通过。第三版针对测试 9/9、服务端全量 222/222、Flyway 空库至 V15 通过。
- INT-004 第三版 `e3a752f` 已只替换现有 server；运行镜像为 `sha256:44095eecafa334d0e5a7e033db920efa014689a6f26871cbc961983c23dd4a29`，回退标签为 `stackchan-foundation-server:rollback-e3a752f-pre-int004-v3`。server 容器由 `c97e6e139830` 变为 `963bd58931bb`，PostgreSQL/Redis 未重建；健康与网页根地址为 200，Flyway `15|true`、迁移数 15，CoreS3 `717a8b1 / motion_disabled` 恢复心跳，近期错误数为 0。
- INT-004 第三版从 `2026-07-26 12:40:15.810739+00` 起共有 10 个成功回合和 1 个 `PLAYBACK_FAILED`。录音结束到播放开始 P50/P95 为 `6131/6327 ms`，上传到播放为 `6075/6268 ms`，ASR/LLM/TTS 为 `348/736`、`3364/3647`、`1742/1977 ms`，服务端总耗时为 `5535/5809 ms`。失败回合已完成 ASR、LLM、TTS 和 `PLAYBACK_STARTED`，随后由设备上报播放失败；server 健康为 200 且同期错误日志数为 0。
- INT-004 完整输出版本删除 `VoiceReplyBoundary`、首块后 1500 ms 预算和语音提示中的单句要求；`VoiceTurnServiceTest` 5/5 通过，并确认多块、多句 LLM 内容会全部进入 TTS 与成功历史，取消行为保持不变。
- INT-004 完整输出版本服务端全量 218/218 通过；Testcontainers PostgreSQL 从空库成功应用 15 个迁移至 V15。文档检查、文档测试 7/7、LAN Compose 静态验证和 `git diff --check` 通过；没有新增迁移、协议字段或内容诊断。
- INT-004 `219b90b` 已只替换现有 server，运行镜像为 `sha256:ffc6534de0484c61c8a8776c3c004c54a731b720dbd8c99bc9a593a7e2c51e6e`，回退标签为 `stackchan-foundation-server:rollback-e3a752f-pre-219b90b`。server 容器为 `011ad10df7f9`，PostgreSQL/Redis 容器未变；健康与网页为 200、Flyway `15|true`、CoreS3 `717a8b1 / motion_disabled` 恢复新鲜心跳，近期错误数为 0。
- INT-004 用户实体复测确认长回复完整播放且没有问题；人工验收通过，不再读取回合内容或追加性能样本。
- 当前任务针对测试 20/20、Maven 全量 198/198 通过；全新 PostgreSQL 成功从空库应用 Flyway 至 V11。调度事务修复后针对测试再次通过 20/20。
- 本地上传增量针对测试 20/20、Maven 全量 205/205 通过；全新 PostgreSQL 成功从空库应用全部 12 个迁移。部署后 `/api/v1/health` 与网页根地址为 200、Flyway `12|true`、服务端近两分钟错误数为 0，设备心跳保持 `0398073` / `motion_disabled`。
- 内置目录专项测试通过；其中目录测试对全部 13 个真实 ESP-SR 模型执行打包和 1 MiB 容量校验。“小峰小峰”与出厂回退组合可被服务端和固件包校验器接受。
- 内置目录版本 Maven 全量 205/205 通过；全新 PostgreSQL 验证 13 个迁移并成功到 V13。部署后健康和网页根地址为 200、Flyway `13|true`、模型目录 13 组、近两分钟错误数为 0。
- 2026-07-26 恢复正式 LAN Compose overlay 后，设备自动重连并接受既有 `READY` 任务；命令 ACK 为成功，设备重启补报安装状态后任务进入 `INSTALLED`，目标模型为 `wn9_xiao3feng1xiao3feng1_tts3`。
- 2026-07-26 LAN 部署：网页根地址和 `/api/v1/health` 均返回 200、Flyway 为 `11|true`；调度器持续运行未出现事务异常，CoreS3 恢复 `e33a0d4` / `motion_disabled` 心跳。
- 2026-07-26 CoreS3 完成一次性三槽引导后，数据库收到 `0398073` / `motion_disabled` 心跳；模型 OTA 服务端仍等待真实生成器制品。
- `& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test`：在 `f0dcecd` 通过 170/170；WAV 针对测试 16/16 通过。
- 管理页面 `POST /api/v1/settings/speech/test`：2026-07-20 21:34（Asia/Shanghai）真实通过，服务端仅记录安全的 `DASHSCOPE` 成功事件。
- 在线提醒：2026-07-20 22:36（Asia/Shanghai）一次调度后收到成功播放 ACK，状态为 `DELIVERED`、`attempt_count=1`、无失败码。
- 设备语音观察：2026-07-20 22:40（Asia/Shanghai）WakeNet、录音上传和 ASR 通过；USER 消息完成，ASSISTANT 因外部 LLM 连续读取超时以 `provider_unavailable` 失败，未进入 TTS。
- LLM 恢复探测：使用数据库中既有加密配置执行单次最小 `/chat/completions` 请求，返回 HTTP 200、约 4.5 秒；未输出 API Key 或响应正文。
- 流式根因探测：同一配置的真实 `stream=true` 请求返回 HTTP 200，首个数据块约 955 ms、完整约 12.1 秒，共收到 185 个 SSE 数据行；未输出认证信息或响应正文。
- `& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test`：在 `6286c34` 通过 170/170；语音服务针对测试和 LLM 工厂测试合计 9/9 通过。
- `& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test`：在 `e41a40f` 提交后通过 179/179；Flyway 测试数据库成功应用 9 个迁移。
- 模式分流针对测试：33/33 通过，覆盖任意模型名原样传递、实时/非实时单路调用、百炼 HTTP ASR、WebSocket TTS 和 OpenAI-compatible 实时模式不回退。
- 既有聊天 `INTERRUPTED` SSE 重放测试增加等待异步结果；连续 3 次针对复跑和两次全量 Maven 验证均通过，生产聊天逻辑未修改。
- 2026-07-21 LAN 部署：健康接口 200、Flyway v9、1 台设备恢复心跳；线上语音静态资源包含新模式表单。
- `& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test`：在 `06a67ab` 通过 183/183；覆盖 v10 迁移、参数校验、保存广播、重连补发和配置 ACK 不影响提醒状态。
- 2026-07-21 `06a67ab` LAN 部署：首次 Docker 构建被 Maven Central 临时 TLS 握手中断，原服务持续健康；重试后完整镜像构建成功，健康接口 200、Flyway v10、CoreS3 心跳恢复。
- INT-001 原始基线验证：Maven 189/189 通过；迁移在重放前使用 V11。重放到最新 `master` 后已顺延为 V14，合并回归结果见后续记录；接口测试确认 JSON 不含 `audio`、`transcript` 或 `reply`。
- INT-001 最新 `master` 合并回归：`mvn clean test` 通过 205/205；Testcontainers PostgreSQL 从空库验证 14 个迁移并成功到 V14，WebSocket 合并测试同时覆盖 `wake_model_status` 与 `voice_turn_stage`。

## 相关设计、计划和决策

- [下一阶段可执行任务清单](../todo.md)
- [0002：后端保持 Java 21 / Spring Boot 与 Spring AI Alibaba-compatible 集成](../decisions/0002-java-spring-ai-alibaba-backend.md)
- [0003：LLM 提供方配置由管理员管理并保护秘密](../decisions/0003-configurable-llm-provider.md)
- [0006：聊天重试按 clientMessageId 对账并保持幂等](../decisions/0006-chat-retry-idempotency.md)
- [0007：设备本地唤醒、服务端语音适配与持久提醒](../decisions/0007-device-voice-and-durable-reminders.md)
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
- [0028：语音副作用采用结构化提案、确定性确认与幂等执行](../decisions/0028-confirmed-idempotent-voice-actions.md)
- [0029：长期记忆建议经过敏感过滤、冲突确认与有界检索](../decisions/0029-reviewed-memory-suggestions-and-bounded-retrieval.md)
- [0030：个性化主动关心必须经过规则门控与主题冷却](../decisions/0030-rule-gated-personalized-proactive-care.md)
- [个人数据备份与隔离恢复验证 runbook](../../runbooks/personal-data-backup.md)
- [Agent、Skill、Tool 与 MCP 运维 runbook](../../runbooks/agent-tools-mcp.md)

## 安全与兼容性约束

- 不在状态文档中记录 API Key、Token 或完整认证载荷。
- 任何生产部署仍须保持 HTTPS-only 边界。
