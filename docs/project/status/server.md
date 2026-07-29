# 服务端工作流

- 状态：COMPLETE
- 最后更新：2026-07-29
- 当前分支：`codex/data-001-personal-data-lifecycle`
- 基准提交：`2a3b712`
- 最后验证提交：`2a3b712`

## 当前目标

完成 `DATA-001` 个人数据生命周期：管理员可分页搜索、查看、导出并物理删除对话正文；服务端只读展示独立 PostgreSQL 备份卷的安全状态，恢复演练只能使用一次性临时数据库。

## 已完成

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

DATA-001 已替换当前 LAN server 并迁移到 Flyway V22。健康与网页根地址为 200，新管理 API 未登录访问为 401，Agent、Skill 和 MCP 数据保留；用户已完成人工验收并确认功能正常。本任务没有固件或语音协议修改。

## 下一步操作

推送任务分支，由用户创建 PR 并人工合入 `master`；随后从最新 `master` 开始 INT-009。

## 阻塞项

服务端实现、运行发布和人工验收均无阻塞。备份卷含完整逻辑备份，必须按数据库同等级保护；不得记录或返回 Tool 参数/结果、认证载荷、配置秘密、音频、转写、回复正文或完整模型响应。

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
- `server/wakenet-models/`

## 验证命令与最近结果

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
- [个人数据备份与隔离恢复验证 runbook](../../runbooks/personal-data-backup.md)
- [Agent、Skill、Tool 与 MCP 运维 runbook](../../runbooks/agent-tools-mcp.md)

## 安全与兼容性约束

- 不在状态文档中记录 API Key、Token 或完整认证载荷。
- 任何生产部署仍须保持 HTTPS-only 边界。
