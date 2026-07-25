# 服务端工作流

- 状态：ACTIVE
- 最后更新：2026-07-26
- 当前分支：`codex/int-001-voice-turn-diagnostics`
- 基准提交：`af65290`
- 最后验证提交：`af65290`

## 当前目标

完成隐私安全的语音回合阶段诊断，同时保持 OpenAI-compatible、阿里云百炼、持久提醒、既有 SCV1 语音响应和 ESP-SR 内置唤醒模型 OTA 兼容。

## 已完成

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

## 正在进行

内置唤醒模型目录已部署到既有 `stackchan-foundation` LAN server，Flyway 当前为 V13；“小峰小峰”任务已进入 `INSTALLED`。INT-001 已重放到最新 `master` 并完成合并回归，尚未推送或部署。

## 下一步操作

用户创建并人工审核本任务 PR；合并后先只重建既有 LAN server，确认健康接口 200 和 Flyway V14，再发布管理端和固件；不得在服务端升级前刷入会发送新阶段事件的固件。

## 阻塞项

没有服务端软件阻塞。“小峰小峰”仍待实体声学验收；INT-001 部署和固件刷写均未获授权。不得在证据中记录配置秘密、供应商响应正文、音频或完整异常载荷。

## 关键文件

- `server/`
- `server/src/main/java/com/kj/stackchan/wakeword/`
- `server/src/main/resources/db/migration/V11__wake_word_model_ota.sql`
- `server/src/main/resources/db/migration/V12__wake_word_model_job_sources.sql`
- `server/src/main/resources/db/migration/V13__retire_generated_wake_models.sql`
- `server/src/main/resources/db/migration/V14__voice_turn_diagnostics.sql`
- `server/src/main/java/com/kj/stackchan/wakeword/EspSrWakeWordModelCatalog.java`
- `server/src/main/java/com/kj/stackchan/speech/VoiceTurnDiagnosticsService.java`
- `server/wakenet-models/`

## 验证命令与最近结果

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

## 安全与兼容性约束

- 不在状态文档中记录 API Key、Token 或完整认证载荷。
- 任何生产部署仍须保持 HTTPS-only 边界。
