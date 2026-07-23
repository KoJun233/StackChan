# 全局工作流总览

- 状态：ACTIVE
- 最后更新：2026-07-26
- 当前分支：`codex/custom-wake-word`
- 实现基准：`dae6015`
- 最后验证提交：`dae6015`
- 当前验证范围：ESP-SR 内置唤醒词目录、可信服务端打包、安全模型 OTA、健康确认、自动回退，以及当前 LAN server 部署。
- 当前优先级：P1 内置唤醒词下拉、LAN server 部署和“小峰小峰”首次真机 OTA 闭环已完成；下一步由用户进行实体唤醒验收。
- 当前部署：LAN HTTP development mode。
- 生产边界：HTTPS-only。

## 工作流摘要

| 工作流 | 状态 | 状态文件 | 当前分支 | 下一步 |
| --- | --- | --- | --- | --- |
| 服务端 | STABLE | [server.md](server.md) | `codex/custom-wake-word` | 已部署 V13；“小峰小峰”任务已进入 `INSTALLED`。 |
| 前端 | STABLE | [frontend.md](frontend.md) | `codex/custom-wake-word` | 已部署下拉界面；重新登录后可选择“小峰小峰”等词。 |
| 固件 | STABLE | [firmware.md](firmware.md) | `codex/custom-wake-word` | `0398073` 已安装“小峰小峰”；等待实体唤醒验收。 |
| 部署 | STABLE | [deployment.md](deployment.md) | `codex/custom-wake-word` | LAN overlay 与设备连接已恢复；保留本地 V12 回退镜像。 |

唤醒词入口现改为“选择 ESP-SR 2.4.6 内置短语、服务端从锁定目录可信打包、设备双槽 OTA、重启健康确认、失败自动回退”。任意文本生成、第三方生成器和模型包上传均已从最终代码与页面移除；V12 只保留为已部署迁移历史，V13 清理临时字段。下拉包含“Hi, Stack Chan”“小峰小峰”等 13 项。CoreS3 的 `0398073` 镜像已链接 WakeNet9/WakeNet9l/WakeNet9s 并完成三槽引导，因此无需重刷固件。管理员选择“小峰小峰”后，任务已完成 `READY -> INSTALLING -> INSTALLED`。

当前内置目录版本已部署到既有 `stackchan-foundation` LAN HTTP 服务：健康和网页根地址均为 200，Flyway `13|true`，容器包含 13 组模型，CoreS3 心跳保持 `0398073` / `motion_disabled`。一次误用基础 Compose 导致端口暂时只监听 `127.0.0.1`，恢复正式 `compose.lan.yaml` 覆盖层后设备自动重连并完成“小峰小峰”安装。部署前保留了 `stackchan-foundation-server:rollback-upload-v12` 本地回退镜像。

## 架构决策

- [架构决策记录](../decisions/README.md)

## 验证与边界

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

- 软件链路和设备三槽引导已验证；内置模型文件已随锁定组件存在，不再依赖用户上传或外部生成器。首次实际模型切换会重启设备，必须由管理员主动选择后触发。
- CoreS3 已具备 `model_a` / `model_b` 分区；后续更换兼容唤醒模型无需再刷整套固件。
- 既有语音回复、屏保视觉和离线提醒补发验收仍保留；本任务不改变后台检测参数、服务端协议或现有部署。
- 部署工作依赖维持 LAN HTTP development mode 与 HTTPS-only 生产边界，且不得组合 `compose.lan.yaml` 和 `compose.production.yaml`。

## 下一步

完成全量测试、Flyway V13 和 LAN server 部署核对；随后由管理员从网页选择一个非当前内置词，确认 `READY -> INSTALLING -> INSTALLED`。后续变更只通过单提交 PR 进入 `master`，生产继续保持 HTTPS-only。
