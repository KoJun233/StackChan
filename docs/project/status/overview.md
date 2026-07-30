# 全局工作流总览

- 状态：ACTIVE
- 最后更新：2026-07-31
- 当前分支：`codex/int-009-continuous-conversation`
- 实现基准：`a51ac83`
- 最后验证提交：`62c727b`
- 当前验证范围：Qwen-TTS 24→16 kHz 合成重采样定向测试 20/20、INT-009 服务端全量 282/282、Testcontainers 空库 V1..V23；前端 24 个文件 65/65、类型检查和 production build；三种 ESP-IDF profile、两项栈预算、唤醒模型包、LAN/production Compose、文档与差异检查通过。当前配置的 TTS→下载→16 kHz 规范化→ASR 不落盘直连通过；用户确认页面语音测试成功且 `dd81a7e` 实体播放不再有电流音。
- 当前优先级：INT-009 已完成实现、发布与人工验收；取得用户授权后只推送任务分支，由用户创建 PR 并人工合并，之后开始 INT-010。
- 当前部署：LAN HTTP development mode。
- 生产边界：HTTPS-only。

## 工作流摘要

| 工作流 | 状态 | 状态文件 | 当前分支 | 下一步 |
| --- | --- | --- | --- | --- |
| 服务端 | COMPLETE | [server.md](server.md) | `codex/int-009-continuous-conversation` | INT-009 完成；等待分支推送授权。 |
| 前端 | COMPLETE | [frontend.md](frontend.md) | `codex/int-009-continuous-conversation` | INT-009 页面与人工验收完成。 |
| 固件 | STABLE | [firmware.md](firmware.md) | `codex/int-009-continuous-conversation` | 保持 `dd81a7e / motion_disabled` 和 NVS 不变。 |
| 部署 | COMPLETE | [deployment.md](deployment.md) | `codex/int-009-continuous-conversation` | LAN 发布与人工验收完成；保持当前运行态。 |

INT-009 已按 server-first 顺序发布：镜像 `sha256:6902bf3e287568bef13d0fa1247676e475f34357fe7d22923687be10d651d332` 只替换 LAN server，运行库从 V22 升至 V23；旧镜像保留为 `stackchan-foundation-server:rollback-a2f723e-pre-v23`。PostgreSQL、Redis、备份容器、卷、端口、凭据和生产 HTTPS-only 边界未改变；旧 CoreS3 `b05d60f / motion_disabled` 在退避后自动重连，并跨两个 25 秒周期持续刷新心跳。连续对话仍默认关闭，未连接 COM3、未刷写或 OTA。

用户随后确认旧 `b05d60f` 的普通单轮语音正常；唤醒、说话、完整播放与恢复 WakeNet 的 server-first 人工兼容验收通过。该确认不授权连接 COM3、刷写或 OTA。

用户明确授权 `af5bcbe`、CoreS3、`COM3` 和 LAN HTTP Quad profile 后，使用官方 ESP-IDF v5.3.3 从干净提交重建并完整写入 bootloader、分区表、应用、OTA data 和语音模型；五个区域即时哈希和独立 `verify_flash` 均匹配，NVS 未擦除。启动确认 `af5bcbe`、8 MB Quad PSRAM/80 MHz 及内存测试、加密 NVS、CoreS3 外设、WakeNet、LAN HTTP、WebSocket 和 `motion_disabled`；数据库跨心跳周期收到 `af5bcbe / motion_disabled`，server 保持健康 200、重启和错误为 0。连续对话设置仍为关闭/8 秒。

用户启用连续对话后确认免唤醒有效跟进和静音退出正常，同时报告音量 80% 的回复播放偶发卡顿/电流音；把音量降至 50% 连续对话 2–3 轮后杂音仍存在，基本排除高音量削波。固件检查确认回复在完整接收后才播放，并发现默认扬声器后台任务低于动态表情任务、DMA 余量约 43 ms；`dd81a7e` 将扬声器任务提升到 UI 之上并把 DMA 余量扩大到约 85 ms。经用户精确授权，该 LAN HTTP Quad 完整镜像已刷入 CoreS3 `COM3`，保留 NVS；五区回读、启动、网络和心跳验证通过。

用户随后修改语音设置并发现 `/api/v1/settings/speech/test` 返回语音提供方不可用。官方 Qwen-TTS 文档确认非实时调用应使用 `multimodal-generation/generation`，并在 `input` 内发送 `text` 与 `voice`，不发送 `format` / `sample_rate`；该候选发布后仍为 400。一次性内存直连进一步确认当前 `qwen-tts + Dylan` 被供应商以 `InvalidParameter` 拒绝，而 `qwen-tts-latest + Dylan` 返回 200 和音频 URL；代码新增只记录 request ID、错误码和受限 message 的安全诊断，不打印完整异常载荷。

最终 `1cd6f76` 使用正式 Dockerfile 构建并只替换 LAN server；首次构建遇到 Maven Central TLS 握手中断，重试成功。运行镜像为 `sha256:76d5a78c0504bc339cb1b37260c9e7e4acf351caef58daf98fbfe3e912277349`，健康/网页 200、Flyway V23、重启 0、LAN 端口正确，CoreS3 `dd81a7e / motion_disabled` 心跳新鲜；部署未改写当前语音设置。

用户保存 `qwen-tts-latest + Dylan` 后，请求不再返回 400，但旧结果白名单拒绝了百炼新返回的乌兰察布 OSS 签名 URL。安全直连只检查 scheme/host 并验证 HTTPS 下载为 200、RIFF/WAVE 内容正确；当前修复新增该精确主机并继续拒绝后缀伪造，不使用通配域名。

`2ec69e1` 已通过正式 Dockerfile 构建并只替换 LAN server，运行镜像为 `sha256:6a79deb2eded1541e22ae367b39d8c3d3d8ac70413a38394af1b4b01ad41f681`。健康 200、Flyway V23、重启 0、LAN 端口和当前 TTS 配置正确，CoreS3 `dd81a7e / motion_disabled` 心跳新鲜；PostgreSQL、Redis、备份容器、固件和 NVS 未变。

用户随后复测进入 `dashscope_tts_audio_invalid`；安全 WAV 头探测确认 Qwen-TTS 输出为 PCM、单声道、24 kHz、16-bit，而设备链路要求 16 kHz。当前修复只在供应商合成结果上执行 24→16 kHz 重采样并重建规范 WAV，设备上传与 ASR 输入继续严格拒绝非 16 kHz。

`62c727b` 已通过正式 Dockerfile 构建并只替换 LAN server，运行镜像为 `sha256:090a3117fe970da14b53d2278df5967629abf5ae912c19e1e83bec9f08cd1d8b`，容器为 `a3c3eb6efeb4`。健康 200、Flyway V23、重启 0，CoreS3 保持 `dd81a7e / motion_disabled`；PostgreSQL、Redis、备份容器、固件和 NVS 未变。随后用当前加密配置执行不落盘端到端直连，TTS、音频下载和 ASR 均返回 200，24 kHz 音频经同算法规范化为 16 kHz 且转写非空；临时探测文件已删除，未输出或保存秘密、签名 URL、音频或转写。

用户随后确认页面语音测试成功，并且实体回复播放不再出现电流音；24→16 kHz 合成兼容与当前软硬件组合的人工验收通过。本轮未再次替换容器、刷写固件或改写 NVS。

唤醒词入口现改为“选择 ESP-SR 2.4.6 内置短语、服务端从锁定目录可信打包、设备双槽 OTA、重启健康确认、失败自动回退”。任意文本生成、第三方生成器和模型包上传均已从最终代码与页面移除；V12 只保留为已部署迁移历史，V13 清理临时字段。下拉包含“Hi, Stack Chan”“小峰小峰”等 13 项。CoreS3 曾使用 `0398073` 镜像完成 WakeNet9/WakeNet9l/WakeNet9s 三槽引导；管理员选择“小峰小峰”后，任务已完成 `READY -> INSTALLING -> INSTALLED`。INT-001 发布时设备升级为 `ecc40f3`，INT-002 升级为栈修复镜像 `216d383`，当前实机为 INT-009 `dd81a7e`。

内置目录版本此前部署到既有 `stackchan-foundation` LAN HTTP 服务时，健康和网页根地址均为 200、Flyway 为 V13、容器包含 13 组模型，CoreS3 心跳为 `0398073 / motion_disabled`。一次误用基础 Compose 导致端口暂时只监听 `127.0.0.1`，恢复正式 `compose.lan.yaml` 覆盖层后设备自动重连并完成“小峰小峰”安装。部署前保留了 `stackchan-foundation-server:rollback-upload-v12` 本地回退镜像；当前服务已由 INT-001 升级为 `ecc40f3` / V14。

INT-001 软件实现已完成：同一回合 ID 关联设备唤醒、录音、上传、服务端 ASR/LLM/TTS、播放和麦克风恢复；PostgreSQL 只保存 7 天结构化元数据，设备总览展示最近时间线。旧固件仍可省略回合 ID，SCV1 正文不变。

INT-001 已发布到既有 LAN server 和 CoreS3；机器证据包含 1 个 `NO_SPEECH` 与 3 个完整成功回合。用户随后确认实体扬声器、聆听/处理/待机表情和管理端四条时间线均符合预期，INT-001 人工验收完成。INT-002 只改本地固件显示语义，不新增协议或部署变更。

INT-003 已完成触摸事件队列、600 ms 按住说话、聆听/处理/播放取消、HTTP 与扬声器中断、晚到回复丢弃、幂等服务端取消、V15 `CANCELLED` 诊断以及管理端时间线映射。`ca2ec8a` 已只替换既有 LAN server 并执行 V15；`717a8b1` LAN HTTP Quad 完整镜像已刷入 CoreS3 `COM3`，五个区域独立校验通过且 NVS 未擦除。启动、WebSocket、WakeNet、PSRAM 和 `motion_disabled` 正常，数据库收到 `717a8b1` 心跳；用户确认屏保首触、长按说话、阶段取消和播放立即停止四项均通过，运行库记录真实 `TOUCH_STARTED` 与 `CANCELLED`。

INT-004 第三版 `e3a752f` 的 1500 ms、首句和 40 code point 限制已全部移除。用户授权后，完整输出版本 `219b90b` 已只替换现有 LAN server；健康与网页根地址为 200、Flyway V15、LAN `0.0.0.0:8080`、CoreS3 `717a8b1 / motion_disabled` 心跳正常，近期错误数为 0。用户随后确认长回复已经完整播放且没有问题，INT-004 人工验收完成。文字聊天、SCV1、取消、固件和数据库结构仍不变。

INT-005 当前工作树新增结构化人设、全局/设备长期记忆、来源与确认状态、记忆搜索/编辑/确认/拒绝/启停/删除/清空，以及文本聊天和设备语音共用的提示词组装器。只有 `CONFIRMED + enabled` 记忆会进入上下文；待确认模型建议不会作为事实使用，删除后下一轮通过新查询立即失效。当前版本未自动从对话提取建议，保留待确认接口供后续兼容性验证后接入。

用户授权后，`d4ad838` 已使用正式 Dockerfile 和 `compose.yaml + compose.lan.yaml` 只替换现有 server，并将运行库从 V15 升至 V16。新镜像为 `sha256:c6bc9795d11831f73c2ab7914f0bce611a9057b352bcb5569ae992e781972c9e`，旧镜像保留为 `stackchan-foundation-server:rollback-d4ad838-pre-v16`；健康与网页根地址均为 200，未登录人设/记忆 API 均为 401，CoreS3 `717a8b1 / motion_disabled` 已恢复心跳，近期启动错误数为 0。

用户随后完成人设与长期记忆功能验收并确认没有问题；INT-005 的页面操作、保存结果和对话行为人工验收完成，无需继续修改运行服务或固件。

INT-006 当前工作树新增每日/每周周期提醒、DST 本地墙钟锚点、稍后提醒、跳过下一次、免打扰、离线错过策略、音量、夜间模式和确定性主动问候。主动问候默认关闭，不由 LLM 决定内容或时机；语音回合、已派发提醒、离线和免打扰均参与仲裁。管理端新增“交互与主动陪伴”页面，设备协议新增严格 `configure_interaction` 与 `stop_audio`，且保持 `motion_disabled`。

首轮实体验收确认立即停止、免打扰和稍后/跳过正常；交互设置控件出现 `Invalid input`，单次提醒每分钟顺延、每日提醒无法完成到期播报，主动问候三分钟内未触发。运行元数据确认停止后的语音回合残留在 `RESPONSE_READY`，使提醒和主动问候持续判定设备忙碌。当前工作树让显式 `v-model` 成为表单控件唯一值来源、停止成功后终止活动回合、只把最近 15 分钟更新的活动回合视为忙碌，并让固件远程停止复用既有取消上报路径。

用户授权后，`f0d99fa` 已使用正式 Dockerfile 和 LAN overlay 只替换 server，运行镜像为 `sha256:0f780fd7264c0137238e89783bc6e172cf61fe4b2ad23e5a3a329ea10b95d1cc`，回退标签为 `stackchan-foundation-server:rollback-f0d99fa-pre-fix`。首次提醒在 server/设备会话恢复窗口丢失 ACK，五分钟保护任务自动恢复后第二次派发成功；随后主动问候在下一分钟调度周期创建并一次送达。两条记录最终均为 `DELIVERED`，CoreS3 心跳持续新鲜。

用户随后确认 INT-006 六项页面与实体交互验收全部正常。语音连接测试暴露的供应商模型/音色兼容性由用户修复，已暴露凭据已撤销并替换，识别与合成恢复正常；仓库和状态文档未记录凭据或完整供应商响应。

INT-007 本地发布候选完成：默认表情升级为 M5Unified/M5GFX 独立实现的动态机械眼；V18、八状态 PNG 可信打包、管理端预览/切换、设备同源认证下载、A/B 原子启用、损坏回退和停用擦除已实现。实现只借鉴 RoboEyes 的公开视觉概念，未复制其 GPL-3.0 源码。`f91dbdb` 已只替换 LAN server 并将运行库升级至 V18。`8394cb3` 首次刷写暴露默认清理阻塞 ACK 的 60 秒重连；`b05d60f` 将默认清理改为幂等立即成功并把真实擦除移到传输任务队列，重新完整刷写后连接与连续心跳恢复稳定。

用户确认 `b05d60f` 默认机械眼没有问题，INT-007 的默认表情人工验收通过。当前没有多余自定义表情素材，用户明确选择暂缓宠物表情包页面、八图生成、启用和恢复默认的实体测试；该未执行项记录为后续按需验证边界，不阻塞本阶段完成。

## 架构决策

- [架构决策记录](../decisions/README.md)

## 验证与边界

- 2026-07-31 最终收尾重跑通过：服务端 282/282 与空库 Flyway V1..V23；Node v24.15.0 前端 24 个文件 65/65、类型检查和 production build；两项固件栈预算、LAN Compose、文档检查、文档测试和差异检查。运行 server 健康 200、重启 0、Flyway V23，CoreS3 `dd81a7e / motion_disabled` 心跳新鲜；未连接 COM3、刷写、改写 NVS 或推送远端。
- INT-009 `a2f723e` 发布前完成新 V22 逻辑备份和隔离恢复验证；正式 Dockerfile 构建镜像 `sha256:6902bf3e287568bef13d0fa1247676e475f34357fe7d22923687be10d651d332`，只替换 server 容器为 `f3a9651e468b`。健康与网页为 200、Flyway `23|true` 且迁移数 23、未登录交互设置为 401、管理端包含连续对话资源、启动错误数和重启数均为 0；设置记录为关闭/8 秒，旧 `b05d60f / motion_disabled` 心跳跨两个周期刷新。未连接 COM3 或刷写固件。
- 用户确认 V23 server 与旧 `b05d60f` 的普通单轮语音正常；server-first 兼容人工 smoke test 通过，未读取或记录音频、转写或回复正文。
- `af5bcbe` LAN HTTP Quad 重建应用为 `0x14b520`、分区余量 57%；CoreS3 `COM3` 五区独立回读匹配且 NVS 未擦除。45 秒启动观察确认 ESP-IDF 5.3.3、8 MB Quad PSRAM/80 MHz、内存测试、加密 NVS、CoreS3 外设、WakeNet、Wi-Fi、WebSocket 与 `motion_disabled`，致命错误为 0；设备跨两个心跳周期在线。
- 用户人工确认免唤醒跟进和静音退出正常，50% 音量对照仍有电流音。播放加固提交 `0646cf6` 的干净 LAN HTTP Quad 镜像完整构建通过，嵌入版本正确，应用 `0x14b5a0`、分区余量 57%；未连接 COM3、未刷写或 OTA。
- `dd81a7e` LAN HTTP Quad 完整镜像经用户授权刷入 CoreS3 `COM3`，五区独立 `verify_flash` 匹配且 NVS 未写入。启动确认 `speaker DMA=512x8 task_priority=4`、PSRAM、加密 NVS、CoreS3 外设、WakeNet、Wi-Fi、WebSocket 与 `motion_disabled`，45 秒致命错误为 0；server 健康 200、重启 0、Flyway V23，数据库收到新鲜 `dd81a7e` 心跳。
- V20 工作树后端 Maven 全量 264/264、Testcontainers 空库迁移 V1..V20、前端 Vitest 22 个文件 61/61、`vue-tsc -b`、production build、`git diff --check`、`pnpm docs:check` 和 LAN Compose 静态验证通过。正式 Dockerfile 只替换既有 LAN server 后，健康与网页根地址为 200、Flyway `20|true` 且迁移数 20、`agent_skills` 表和独立 Skill 卷存在、未登录 Agent API/页面为 401、启动错误数为 0；CoreS3 `b05d60f / motion_disabled` 恢复新鲜心跳。未连接 MCP、COM3，未刷写、OTA、修改凭据或生产 HTTPS-only 边界。
- V20 改造前的 INT-008 服务端 Maven 全量 255/255 通过；真实 ReactAgent Tool 回合、强制事实 Tool 路由、MCP 授权、预算与元数据审计测试通过，Testcontainers 从空 PostgreSQL 应用 V1..V19。当时的 3 个 classpath 演示 Skill 已在当前工作树删除，该历史结果不能替代 V20 自定义 Skill 包复核。
- BASE-008 文档刷新在 `8122959` 基线上完成：Maven 244/244、前端 Vitest 20 个文件 57/57、`vue-tsc -b`、production build、文档测试 7/7、LAN Compose 静态验证、`pnpm docs:check` 和 `git diff --check` 通过。固件源码未变；配网与语音危险预算夹具通过，配网真实报告通过，语音真实报告沿用 INT-007 已验证构建，当前干净工作树未重新生成忽略的 `.su` 分析产物。未部署、未连接 COM3、未刷写、未 OTA、未修改卷、端口、凭据或运行数据库。
- INT-007 服务端全量 238/238、前端 Vitest 20 个文件 57/57、`vue-tsc -b` 和 production build 通过；`f91dbdb` 正式镜像只替换 server 后，健康与网页均为 200、Flyway `18|true`、成功迁移数 18、三张表达资源表存在、未登录表达资源 API 为 401、近期错误数为 0。经用户授权，修复后的 `b05d60f` LAN HTTP Quad 镜像五区写入和独立回读均通过且 NVS 未擦除；启动确认 ESP-IDF 5.3.3、8 MB Quad PSRAM/80 MHz、内存测试、CoreS3 外设、WakeNet 和 `motion_disabled`。42 秒启动窗口只建立一次 WebSocket，后续 32 秒窗口无断线、退避或崩溃，数据库心跳从 `22:42:52` 刷新到 `22:43:42`；server 健康 200、错误数 0。
- INT-006 验收修复服务端全量 236/236、Testcontainers 空库 V1..V17、前端 19 个文件 55/55、`vue-tsc -b`、production build 均通过；ESP-IDF 5.3.3 协议与 LAN HTTP Quad profile 分别生成 `0x37880` / `0x143c40` 镜像，应用分区余量 93% / 58%。server 已发布，CoreS3 已刷入 `2465427`。
- INT-006 经用户明确确认 CoreS3、`COM3`、LAN HTTP Quad development profile 和 `2465427` 后，从干净提交完整刷写 bootloader、分区表、应用、OTA data 和语音模型；五个区域独立 `verify_flash` 全部匹配，NVS 未擦除。启动确认应用版本、8 MB Quad PSRAM / 80 MHz 及内存测试、加密 NVS、CoreS3 外设、WakeNet“小峰小峰”、LAN HTTP、WebSocket 和 `motion_disabled`；数据库收到 `2465427` 新鲜心跳，35 秒窗口未见 panic、栈溢出或看门狗，server 健康保持 200、Flyway 保持 `17|true`。
- INT-006 用户确认六项验收全部正常；供应商模型/音色兼容性修复与凭据轮换后，语音识别与合成测试恢复正常。诊断只保留安全状态码，未记录密钥、Workspace ID、音频或完整供应商响应。
- INT-006 经用户明确授权，从干净 `9495111` 构建 `sha256:ece87baaf655536a7578d4e2af87c67276b99ebc1ef37374728471aea21b879a` 并只替换现有 LAN server；健康与网页为 200，Flyway `17|true`，未登录交互设置 API 为 401，启动错误数为 0。PostgreSQL/Redis 容器未变，LAN 保持 `0.0.0.0:8080`，旧 CoreS3 `717a8b1 / motion_disabled` 恢复新鲜心跳。未连接 COM3、刷写、OTA、修改凭据或改变生产 HTTPS-only 边界。
- INT-006 经用户明确确认 CoreS3、`COM3`、LAN HTTP Quad development profile 和 `c1d7383` 后，从干净提交构建并完整刷写 bootloader、分区表、应用、OTA data 和语音模型；五个区域独立 `verify_flash` 全部匹配，NVS 未擦除。启动确认版本、8 MB Quad PSRAM、加密 NVS、CoreS3 外设、WakeNet、LAN HTTP、WebSocket 和 `motion_disabled`，数据库收到 `c1d7383` 新鲜心跳；未见 panic、栈溢出、看门狗或重启循环。
- INT-005 服务端全量 225/225 通过；Testcontainers PostgreSQL 从空库成功应用 16 个迁移至 V16。前端 Node v24.15.0 下 Vitest 18 个文件 53/53、`vue-tsc -b` 和 production build 通过；Vitest close-timeout advisory 仍为既有非阻塞警告。
- INT-005 部署后 `/api/v1/health` 与网页根地址均为 200，Flyway `16|true` 且成功迁移数为 16；人设单例初始记录为 1、长期记忆初始记录为 0，容器静态资源包含人设与记忆页面。server 容器由 `011ad10df7f9` 变为 `dc2a0ff8e75b`，PostgreSQL/Redis 保持 `6d8feaa18623` / `58e31a403637`，LAN 保持 `0.0.0.0:8080`。未连接 COM3、刷写固件、修改卷、凭据或生产 HTTPS-only 边界。
- INT-005 用户人工验收确认人设与长期记忆功能没有问题；不再读取或记录对话正文，当前运行资源保持不变。

- INT-003 服务端发布前保留 `stackchan-foundation-server:rollback-ca2ec8a-pre-v15`；只重建 server，PostgreSQL、Redis、卷和 LAN overlay 未改变。健康接口与网页根地址均为 200，Flyway `15|true`，启动后错误数为 0；旧固件 `216d383 / motion_disabled` 恢复心跳并完成完整成功回合。
- INT-003 经用户明确授权，从干净 `717a8b1` 重建并完整刷写 CoreS3 `COM3` 的 LAN HTTP Quad 镜像；bootloader、分区表、应用、OTA data 和语音模型五个区域均通过独立 `verify_flash`，NVS 未擦除。启动窗口确认 PSRAM、CoreS3 外设、LAN HTTP、WakeNet、WebSocket 与 `motion_disabled` 正常，未见 panic、栈溢出、看门狗或重启循环；数据库收到 `717a8b1` 心跳，Flyway 保持 V15，服务端近期错误数为 0。用户随后确认屏保首触只唤醒、600 ms 长按说话、聆听/处理/播放短触取消以及播放立即停止四项全部通过；最近诊断包含 5 次 `TOUCH_STARTED` 和 1 个 `CANCELLED` 终态。
- INT-004 基线只读取七天诊断表中的阶段与耗时，不读取音频、转写、回复或认证载荷。首版新增 ADR 0019、有界首句边界和早停测试；针对测试 8/8、服务端全量 221/221 通过，Testcontainers 从空库成功应用 15 个迁移至 V15。该验证阶段尚未部署、未连接 COM3、未刷写、未 OTA、未修改 Compose 或凭据。
- INT-004 按用户授权从干净 `5016324` 构建镜像并只重建 `stackchan-foundation-server-1`；旧镜像保留为 `stackchan-foundation-server:rollback-5016324-pre-int004`。PostgreSQL、Redis 容器 ID 未变，LAN 继续绑定 `0.0.0.0:8080`，健康与网页根地址为 200，Flyway `15|true` 且迁移总数仍为 15；CoreS3 `717a8b1 / motion_disabled` 在 30 秒内恢复心跳，近期 server 错误数为 0。未连接 COM3、未刷写、未 OTA、未修改卷、凭据或生产 HTTPS-only 边界。
- INT-004 首版部署后的 11 个成功回合中，录音结束到播放开始 P50/P95 为 `5978/7158 ms`，服务端 P50/P95 为 `5443/6021 ms`；相对基线中位数分别回退 `190/217 ms`，但服务端 P95 改善 `621 ms`。阶段分解确认 ASR/TTS 改善而 LLM 回退；第二版发布前针对测试 8/8、服务端全量 221/221、Flyway 空库至 V15、文档检查和 7/7 文档测试通过。
- INT-004 第二版经用户授权从干净 `3d8c1fb` 构建 `sha256:192ed2297336577bf96b3b1479f7c9c11336ceceba9fccb907a7f0e72a78e9a3` 并只替换 server；旧镜像保留为 `stackchan-foundation-server:rollback-3d8c1fb-pre-int004-v2`。server 容器由 `b17ee0d4b280` 变为 `c97e6e139830`，PostgreSQL/Redis 容器未变；健康与网页根地址为 200，LAN 为 `0.0.0.0:8080`，Flyway `15|true` 且迁移数 15，CoreS3 `717a8b1 / motion_disabled` 恢复新鲜心跳，近期错误数为 0。
- INT-004 第二版复测共有 11 个成功回合和 1 个 `NO_SPEECH`；录音结束到播放开始 P50/P95 为 `6139/7430 ms`，服务端总耗时为 `5543/6835 ms`，性能验收未通过。第三版首块后时间预算针对测试 9/9、服务端全量 222/222、Flyway 空库至 V15 通过。
- INT-004 第三版经用户授权从干净 `e3a752f` 构建 `sha256:44095eecafa334d0e5a7e033db920efa014689a6f26871cbc961983c23dd4a29` 并只替换 server；第二版镜像保留为 `stackchan-foundation-server:rollback-e3a752f-pre-int004-v3`。server 容器由 `c97e6e139830` 变为 `963bd58931bb`，PostgreSQL/Redis 容器未变；健康与网页根地址为 200，LAN 为 `0.0.0.0:8080`，Flyway `15|true` 且迁移数 15，CoreS3 `717a8b1 / motion_disabled` 恢复心跳，近期错误数为 0。
- INT-004 第三版复测共有 10 个成功回合和 1 个设备 `PLAYBACK_FAILED`。首音频 P50/P95 为 `6131/6327 ms`，服务端 P50/P95 为 `5535/5809 ms`；失败回合已完成服务端 TTS 并进入设备播放，server 健康为 200、同期错误数为 0。当前不把第三版视为中位性能验收通过，但确认尾延迟目标取得实质改善。
- INT-004 用户在得知 1500 ms 限制仍存在后撤回此前按现状完成的结论，并明确要求取消内容截断。当前实现完整聚合所有非空 LLM 流数据块后再执行 TTS 和成功历史持久化；30 秒供应商超时仍仅作为异常保护，不会把部分文本当作成功回复播放。
- INT-004 完整输出版本通过 `VoiceTurnServiceTest` 5/5、服务端全量 218/218、Flyway 空库至 V15、文档检查、文档测试 7/7、LAN Compose 静态验证和 `git diff --check`；未部署、未连接 COM3、未刷写固件或修改运行凭据。
- INT-004 经用户明确授权，从干净 `219b90b` 构建镜像 `sha256:ffc6534de0484c61c8a8776c3c004c54a731b720dbd8c99bc9a593a7e2c51e6e` 并只替换 server；旧镜像保留为 `stackchan-foundation-server:rollback-e3a752f-pre-219b90b`。server 容器由 `963bd58931bb` 变为 `011ad10df7f9`，PostgreSQL/Redis 保持 `6d8feaa18623` / `58e31a403637`；健康与网页为 200、LAN 为 `0.0.0.0:8080`、Flyway `15|true`、设备心跳为 `717a8b1 / motion_disabled`，近期 server 错误数为 0。
- INT-004 用户实体复测确认取消限制后的回复完整播放且没有问题；不再收集额外回合或读取对话内容，人工验收通过。
- 内置目录最终实现通过 Maven 205/205、Flyway 空库至 V13、真实 13 模型打包、前端 Vitest 17 文件 49/49、`vue-tsc -b`、production build、模型包回归、LAN Compose、`git diff --check` 和 `pnpm docs:check`。部署后健康/网页 200、Flyway `13|true`、容器模型 13 组、近期错误 0；恢复 LAN overlay 后 CoreS3 自动重连并确认“小峰小峰”任务为 `INSTALLED`，固件仍为 `0398073` 且 `motion_disabled` 不变。
- `6df88f9` 之后的调度事务修复已在当前任务树完成针对回归、全量回归和 LAN 部署验证。
- 当前任务工作树上服务端全量测试 198/198、前端 Vitest 17 个文件 48/48、`vue-tsc -b`、production build、Flyway 全新 PostgreSQL 至 V11、模型包回归、两项固件栈预算、`git diff --check` 和 `pnpm docs:check` 均通过。
- 本地上传增量在当前任务工作树通过 WakeWord 针对测试 20/20、服务端全量 205/205、前端 Vitest 17 个文件 50/50、`vue-tsc -b`、production build、全新 PostgreSQL 至 V12 和上传打包脚本回归；当前 LAN server 健康/网页均为 200，Flyway `12|true`，线上 `speech-CKv17cKU.js` 包含双模式界面，CoreS3 保持 `0398073` / `motion_disabled`。
- 2026-07-26 已使用仓库正式 Dockerfile 重建并替换同一 LAN server；网页根地址返回 200，Flyway 为 `11|true`，线上静态资源包含 `speech-DFhfjNNt.js`，调度器持续运行未再出现事务异常，CoreS3 恢复 `e33a0d4` / `motion_disabled` 心跳。生成器可执行文件仍未配置。
- 2026-07-26 经用户明确确认 CoreS3、`COM3`、LAN HTTP Quad 和 `0398073` 后，从独立目录构建应用镜像 `0x1421e0` 并完整写入 bootloader、分区表、应用、OTA data 和出厂模型；五个区域独立 `verify_flash` 全部匹配。启动确认三个模型槽、8 MB PSRAM / 80 MHz、加密 NVS、CoreS3 外设、出厂 WakeNet 和 `motion_disabled`，数据库收到 `0398073` 心跳。
- 当前任务工作树的协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四个 Quad profile 均构建通过，镜像分别为 `0x37880`、`0x153260`、`0x1421e0` 和 `0x1428d0`；公开测试证书已删除，未创建或保留私钥。
- ESP-IDF 在 Windows 构建中两次于工具链子进程内无诊断中止；相同目录降低并行度后增量构建完成，没有源码编译诊断。
- 当前自定义唤醒框架的模型包回归、真实双模型容量校验、`git diff --check`、`pnpm docs:check`、两项固件栈预算，以及协议测试、默认 HTTPS、LAN HTTP 三个 Quad profile 构建均通过；镜像分别为 `0x37880`、`0x1515b0`、`0x140460`。
- ESP-SR 实际双模型分区包含模拟外部首选模型和 `wn9l_histackchan_tts3`，大小 `585211 / 1048576` 字节，余量 `463365` 字节；未刷写设备、切换部署或提交短语到外部服务。
- `731a68c` 上 Maven 153/153、前端 Vitest 37/37、`vue-tsc`、production build、`pnpm docs:check`、两项 firmware provisioning stack budget check 和 LAN Compose verification 全部通过。
- `b6dbaf8` 修正 CoreS3 PSRAM 为 Quad 模式；协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四个 profile 均构建并确认嵌入 `b6dbaf8`，应用分区余量分别为 93%、56%、58% 和 58%。
- `e9f072a` 新增 Fantastic-admin Web Serial 配网页面并将屏保改为关闭背光；前端 Vitest 41/41、`vue-tsc`、production build、`pnpm docs:check`、两项固件栈预算和 LAN Compose verification 通过。
- `121ed8e` 增加百炼原生语音适配；Maven 164/164、前端 Vitest 43/43、`vue-tsc`、production build、`pnpm docs:check` 和 LAN Compose verification 通过。
- `29aef87` 完成安全语音成功日志并完整重建当前 LAN server；页面保存、刷新持久化和百炼双向语音测试通过，健康接口返回 200，Flyway 保持 v8。
- `b4876fb` 完成 ADR 0010 的低亮度动态瞳孔屏保；Maven 169/169、前端 Vitest 45/45、`vue-tsc`、production build、`pnpm docs:check`、两项固件栈预算和 LAN Compose verification 通过。
- `f0dcecd` 将百炼 TTS WAV 标准化后再交给设备播放；针对测试 16/16、Maven 全量 170/170 通过，同一 `stackchan-foundation` LAN server 完整重建后健康检查返回 200。
- `6286c34` 将设备语音的 LLM 调用从非流式等待改为 30 秒有界流式聚合，并要求最多两句话的语音回答；真实供应商流式探测 HTTP 200，首个数据块约 955 ms、完整约 12.1 秒，Maven 全量 170/170 通过。
- `e41a40f` 将语音协议路由改为显式模式；Maven 179/179、前端 Vitest 44/44、`vue-tsc`、production build、`git diff --check` 和 `pnpm docs:check` 通过。当前 LAN server 已部署该提交，Flyway v9、健康接口 200，线上静态资源包含新的模式表单。
- `06a67ab` 完成可配置本地语音检测和 WakeNet 生命周期修复；Maven 183/183、前端 Vitest 45/45、`vue-tsc`、production build、`git diff --check`、`pnpm docs:check`、两项固件栈预算、LAN Compose 静态验证和四种固件 profile 构建均通过。
- `06a67ab` 已完整重建到当前 LAN server；第一次构建被 Maven Central 临时 TLS 握手中断，重试成功后健康接口 200、Flyway v10、线上语音资源包含三个新字段，CoreS3 恢复 `b4876fb` / `motion_disabled` 心跳。
- `06a67ab` LAN HTTP 完整镜像已刷入 CoreS3 的 `COM3`，五个分区全部通过哈希校验；启动确认版本、8 MB Quad PSRAM、加密 NVS、CoreS3 外设、灵敏 WakeNet、`350/200` 配置补发和 `motion_disabled`。服务端心跳已切换为 `06a67ab`。
- `ce27ec9` 完成显式 `0.50` 灵敏唤醒阈值和麦克风短帧竞态修复；正确 Quad 镜像刷入后实际阈值为 `0.496`，连续捕获 3 次唤醒和录音。首次错误 Octal 构建引发的启动循环已由正确 Quad 镜像覆盖恢复，NVS 和硬件未损坏。
- `b2e8db2` 修复零 tick 轮询导致的 CPU0 任务看门狗；协议测试、默认 HTTPS、LAN HTTP、测试证书 HTTPS Quad 镜像分别为 `0x37880`、`0x150f10`、`0x13fdd0`、`0x1402e0`，两项固件栈预算通过。
- `b2e8db2` 的正确 Quad LAN HTTP 完整镜像已刷入 COM3，五个分区通过写入校验；启动确认 8 MB Quad PSRAM、Wi-Fi/NVS、灵敏 WakeNet 和 `motion_disabled`。数据库在 2026-07-21 22:29:38（Asia/Shanghai）收到该版本心跳；串口监听确认任务看门狗消失，但持续出现 `WakeNet microphone capture failed: ESP_ERR_TIMEOUT`。
- `e33a0d4` 接受在首个轮询 tick 内已经完成的麦克风短帧；协议测试、默认 HTTPS、LAN HTTP、测试证书 HTTPS Quad 镜像分别为 `0x37880`、`0x150f20`、`0x13fde0`、`0x1402f0`，两项固件栈预算通过。测试证书使用 ESP-IDF 公开样例，构建后已删除且未创建或保留私钥。
- `e33a0d4` 的正确 Quad LAN HTTP 完整镜像已刷入 COM3，bootloader、应用、分区表、OTA data 和 WakeNet 模型分区全部通过哈希校验；数据库在 2026-07-21 22:49:25（Asia/Shanghai）收到 `e33a0d4` / `motion_disabled` 心跳。
- `e33a0d4` 上板监听共 300 秒未出现 `ESP_ERR_TIMEOUT`、任务看门狗或崩溃；首次 180 秒窗口命中两次唤醒并两次恢复监听。一次因 Wi-Fi 尚未连接返回 `ESP_ERR_INVALID_STATE`，另一次峰值能量 `313` 低于开始阈值 `350` 并安全返回 `ESP_ERR_NOT_FOUND`；后续 120 秒窗口无新唤醒命中。
- INT-001 原始基线验证通过：Maven 189/189 且当时的 Flyway V11 成功应用；前端 Vitest 46/46、`vue-tsc` 和 production build 通过；ESP-IDF 5.3.3 协议测试 profile 构建为 `0x37880`、余量 93%；两项固件栈预算、`pnpm docs:check`、文档校验测试、`git diff --check` 和 LAN Compose 静态验证通过。重放到最新 `master` 后诊断迁移已顺延为 V14，合并回归结果见本任务后续记录。
- INT-001 最新 `master` 合并回归通过：服务端 `mvn clean test` 205/205、Testcontainers PostgreSQL 14 个迁移到 V14；前端 Node v24.15.0 下 Vitest 50/50、`vue-tsc -b` 和 production build；ESP-IDF 5.3.3 协议 profile `0x37880`、余量 93%；两项固件栈预算、唤醒模型包回归、文档检查与 7 项文档测试、LAN Compose 静态验证和 `git diff --check` 均通过。未部署、未连接设备、未刷写固件。
- INT-001 验证只生成临时构建产物并使用进程内占位值；未连接 COM3、未刷写固件、未重建服务、未更改数据库、凭据、部署模式或远程状态。
- INT-002 四个 Quad profile 完整编译通过：协议测试、默认 HTTPS、LAN HTTP、测试证书 HTTPS 镜像分别为 `0x37880`、`0x153d20`、`0x142cd0`、`0x143110`，余量分别为 93%、56%、58%、58%；两项栈预算、唤醒模型包、文档、LAN Compose 和差异检查通过。经用户明确授权后，从干净 `adbd75e` 重建的 LAN HTTP Quad 完整镜像已刷入 CoreS3 `COM3`；五个区域均通过独立摘要校验，NVS 未擦除。启动确认 8 MB PSRAM、CoreS3 外设、LAN HTTP、WakeNet 和 `motion_disabled`，数据库收到 `adbd75e` 最近心跳；未部署服务或改变凭据与模式。
- 首轮用户验收确认没听清与屏保通过，但正常回合和离线显示不可区分。修复后的协议测试与 LAN HTTP Quad profile 镜像分别为 `0x37880` 和 `0x142d90`，状态单测、两项栈预算、模型包、文档 7/7、LAN Compose 与差异检查通过。
- 经用户明确授权后，从干净提交 `abd6a22` 重建的 LAN HTTP Quad 完整镜像已刷入 CoreS3 `COM3`；bootloader、分区表、应用、OTA data 和语音模型五个区域均通过独立 `verify_flash`，NVS 未擦除。启动确认应用版本、ESP-IDF 5.3.3、8 MB PSRAM / 80 MHz 及内存测试、CoreS3 外设、WakeNet、LAN HTTP、WebSocket 和 `motion_disabled` 正常，45 秒窗口未见 panic、看门狗或重启循环；服务健康为 200、Flyway `14|true`，数据库收到 `abd6a22` 最近心跳。未替换 server、执行迁移、改变凭据或切换模式。
- 用户正常对话复测稳定复现“录音完成后处理点阵再回到灰色离线”。脱敏串口证据显示两次 `Speech captured` 后均报告 `A stack overflow in task voice_control has been detected` 并软件复位；服务端未收到 `REQUEST_RECEIVED`，因此问题位于设备同步 HTTP 上传路径而非 ASR/LLM/TTS 或显示仲裁。
- 修复将 `VOICE_TASK_STACK_SIZE` 从 12288 提高到 32768 字节，并新增语音栈预算验证与回归。真实 `-fstack-usage` 报告确认已知本地路径 10416 字节、外部 HTTP/TCP 余量 22352 字节；12288 字节夹具被拒绝、32768 字节夹具通过。经用户明确授权，从干净 `216d383` 重建的 LAN HTTP Quad 完整镜像已刷入 CoreS3 `COM3`；五个区域独立 `verify_flash` 全部匹配且 NVS 未擦除。启动确认版本、ESP-IDF 5.3.3、8 MB PSRAM / 80 MHz 及内存测试、CoreS3 外设、WakeNet、LAN HTTP、WebSocket 和 `motion_disabled`，40 秒窗口未见 panic、栈溢出、看门狗或重启循环；服务健康为 200、Flyway `14|true`，数据库收到 `216d383` 新鲜心跳。用户随后完成两轮正常对话，串口均记录录音完成和 WakeNet 恢复监听且未见栈溢出、panic 或复位；数据库两轮均为 `COMPLETED`，完整包含请求接收、ASR、LLM、TTS、播放开始/完成和恢复监听阶段。用户确认两轮实体语音回复、成功反馈和返回待机均正常。另一次独立 `NO_SPEECH` 按预期失败码结束。
- 用户按 runbook 临时制造语音模型调用失败并恢复原配置，确认橙色可恢复异常反馈和恢复后的正常回合均符合预期；用户明确表示无需继续读取串口或数据库证据，被动监听已停止并释放 `COM3`。
- `b4876fb` 的协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四个 Quad profile 均从干净工作树构建并嵌入 `b4876fb`，镜像大小分别为 `0x37880`、`0x1506a0`、`0x13f570` 和 `0x13f9f0`，应用分区余量分别为 93%、56%、58% 和 58%。测试证书使用 ESP-IDF 公开样例，构建后已删除且未创建或保留私钥。
- `b4876fb` 的 LAN HTTP 完整镜像已刷入 COM3，五个分区全部通过写入哈希校验；启动确认版本 `b4876fb`、8 MB Quad PSRAM、加密 NVS、CoreS3 外设、WakeNet 和 `motion_disabled`。2026-07-20 22:38（Asia/Shanghai）USB 配置写入后数据库恢复持续心跳，服务地址错配阻塞已解除。
- 2026-07-20 22:36（Asia/Shanghai）在线提醒在一次尝试后收到设备成功播放 ACK 并变为 `DELIVERED`；实体扬声器是否清晰出声仍待用户听觉确认。
- `f962d71` 修复语音配置表单提交；前端 Vitest 44/44、`vue-tsc` 和 production build 通过，并已重建同一 LAN server。健康检查返回 200，Flyway v8，设备心跳恢复。
- `3e50d56` 将 Teleport 后的固定栏保存按钮通过原生 `form` 属性绑定语音表单；模拟真实 Teleport 的回归测试、前端 Vitest 44/44、`vue-tsc` 和 production build 通过，LAN server 重建后健康检查返回 200。
- Docker Desktop 数据已迁移到 E 盘并通过 Junction 保持原路径兼容；现有 PostgreSQL 卷和运行服务保留。
- `e9f072a` 的协议测试、默认 HTTPS、LAN HTTP 和测试证书 HTTPS 四个 Quad profile 均构建并确认嵌入 `e9f072a`，应用分区余量分别为 93%、56%、58% 和 58%。测试证书 profile 临时使用 ESP-IDF 公开测试证书，验证后已删除且未创建私钥。
- `b6dbaf8` 的测试证书 profile 使用一次性未跟踪证书，验证后证书和私钥已删除；没有记录或提交秘密。
- COM3 已完整刷入旧版 `e9f072a` 的 LAN HTTP 镜像，各分区通过 SHA 校验；启动确认 8 MB Quad PSRAM、M5StackChan/CoreS3 硬件、加密 NVS、WakeNet 和 `motion_disabled`。数据库最近一次心跳为 `2026-07-20 00:24:55+08:00`；`COM3` 当前存在，但设备是否已上电待刷写前确认。
- COM3 静置约 301.7 秒后只记录一次背光关闭事件，期间无 panic 或重启，固件侧确认不再周期性整屏重绘；触摸恢复和屏幕视觉效果仍待用户确认。
- 部署的既有模式证据仍为 `c2e7502`；`731a68c` 只重跑 LAN Compose 静态验证，没有 Compose 模式、凭据或部署变更。
- 前端验证使用 `C:\Users\Administrator\.cache\codex-runtimes\manual-node\node-v24.15.0-win-x64`，满足仓库 engine；没有修改 engine 约束。
- 当前任务实现与状态文档将在同一个任务提交中交接；Git 跟踪的 `docs/project/status/` 是当前状态的正式事实来源。
- 仓库协作约束要求中文提交、单提交任务 PR、人工审核和 merge commit；PR 策略工作流检查提交数量、PR 标题和提交标题。

## 最近恢复演练

- 日期：2026-07-19
- 状态：PASS
- 使用的已提交状态：`dc79b5a`；演练前交接提交为 `7d80b8c`，首次失败后由 `dc79b5a` 补充 Node 恢复引导。
- 耗时：约 2 分 45 秒（开始 `2026-07-19 16:47:08+08:00`，结束 `2026-07-19 16:49:53+08:00`）。
- 新 Agent 只读取 `AGENTS.md`、`README.md`、项目状态文档和 `development.md`，未依赖临时工作记录或聊天记录，也没有修改文件。
- 正确恢复四个均为 READY 的工作流、受保护的用户改动、LAN 开发/生产 HTTPS 边界、`0cba824` 代码验证、`5f3c486` / COM3 物理证据、`c2e7502` 部署证据，以及用户优先级选择依赖。
- 使用已记录的 Codex Node 临时引导，成功运行 `pnpm docs:check` 和 LAN Compose verification。
- 未发生文件、设备、部署、凭据或远程状态变更。

## 最近最终审查

- 日期：2026-07-19
- 状态：PASS
- 审查提交：`4d6bb95`
- 结果：最终只读全分支审查无 Critical、Important 或 Minor 发现，已准备好完成最终交接。
- 审查未引入文件、设备、固件、Compose 模式、部署、凭据或远程状态变更。

## 跨工作流阻塞项与依赖

- INT-007 已由 PR #8 合入 `master`；默认机械眼通过实体验收。自定义八状态资源包的实体测试因没有合适素材暂缓，但不阻塞 `INT-008`。
- 用户将受控 Agent 调整为当前最高优先级；`codex/int-008-agent-tools-mcp` 已从最新 `master` 合并提交 `8122959` 创建。
- 后续顺序为 `INT-008 Agent 基础 -> DATA-001 -> INT-009 连续对话 -> INT-010 安全语音操作 -> INT-011 记忆 2.0 -> INT-012 主动关心`；任务范围和验收条件见[下一阶段可执行任务清单](../todo.md)。
- 软件链路和设备三槽引导已验证；内置模型文件已随锁定组件存在，不再依赖用户上传或外部生成器。首次实际模型切换会重启设备，必须由管理员主动选择后触发。
- CoreS3 已具备 `model_a` / `model_b` 分区；后续更换兼容唤醒模型无需再刷整套固件。
- INT-001 已完成服务端 V14、管理端、固件发布和用户人工验收；现有 1 个 `NO_SPEECH` 与 3 个成功回合作为 INT-002 前的基线。
- 屏保视觉和离线提醒补发仍是既有待验收项，不在 INT-001 中扩展。
- “小峰小峰”OTA 和实体声学命中已由三个成功语音回合确认。
- INT-002 的显示修复与 INT-003 的触摸控制均已完成实机验收；当前没有设备侧交互阻塞。
- INT-004 完整输出版本已完成软件、部署、人工验收和任务合并。
- 部署工作依赖维持 LAN HTTP development mode 与 HTTPS-only 生产边界，且不得组合 `compose.lan.yaml` 和 `compose.production.yaml`。

## 下一步

取得用户外部推送授权后，只推送 `codex/int-009-continuous-conversation`；由用户创建 PR 并人工合并。合入最新 `master` 后，按 `docs/project/todo.md` 从独立分支开始 INT-010。
