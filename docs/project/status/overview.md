# 全局工作流总览

- 状态：ACTIVE
- 最后更新：2026-07-21
- 当前分支：`master`
- 实现基准：`ROOT`
- 最后验证提交：`ROOT`
- 当前验证范围：当前根初始化提交的完整变更树。
- 当前优先级：P1 完成实体唤醒、语音回复、动态瞳孔屏保和离线提醒补发验收
- 当前部署：LAN HTTP development mode。
- 生产边界：HTTPS-only。

## 工作流摘要

| 工作流 | 状态 | 状态文件 | 当前分支 | 下一步 |
| --- | --- | --- | --- | --- |
| 服务端 | STABLE | [server.md](server.md) | `master` | 保持 v10 LAN server 在线并在新固件重连时补发本地语音配置。 |
| 前端 | STABLE | [frontend.md](frontend.md) | `master` | 刷新页面并确认唤醒灵敏度和两项录音阈值可配置。 |
| 固件 | ACTIVE | [firmware.md](firmware.md) | `master` | `e33a0d4` 已刷入；继续实体语音回复、屏保和提醒验收。 |
| 部署 | STABLE | [deployment.md](deployment.md) | `master` | 保持 `06a67ab` LAN server 在线以配合固件刷写和实体 smoke test。 |

`e33a0d4` 已以正确 Quad LAN HTTP 完整镜像刷入 CoreS3，五个分区通过哈希校验；启动确认版本、8 MB PSRAM / 80 MHz、内存测试、CoreS3 外设、`threshold_milli=496`、Wi-Fi/WebSocket 和 `motion_disabled`。首个 180 秒窗口与后续 120 秒窗口均无 `ESP_ERR_TIMEOUT`、任务看门狗或崩溃，证明短帧超时回归已消失。首个窗口命中两次唤醒并两次恢复 WakeNet，但一次发生在 Wi-Fi 尚未连接，另一次峰值能量 `313` 低于开始阈值 `350`；完整录音和语音回复仍待用户实体复测。

## 架构决策

- [架构决策记录](../decisions/README.md)

## 验证与边界

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
- 当前 worktree 在实现提交验证后保持干净；Git 跟踪的 `docs/project/status/` 是当前状态的正式事实来源。
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

- 配网、录音上传、在线提醒 ACK、语音协议分流、低阈值唤醒和 `e33a0d4` 的短帧超时/任务看门狗修复已验证。剩余实体语音回复依赖用户在唤醒后说话峰值达到当前开始阈值 `350`，屏保视觉和离线提醒补发仍需继续验收。
- 部署工作依赖维持 LAN HTTP development mode 与 HTTPS-only 生产边界，且不得组合 `compose.lan.yaml` 和 `compose.production.yaml`。

## 下一步

让用户再次说“Hi StackChan”，变蓝后靠近设备并稍大声说一句短问题；确认出现 `Speech captured`、播放回复并恢复 WakeNet。若峰值仍低于 `350`，再由用户决定是否在管理后台把开始说话阈值调低。之后继续屏保视觉和离线提醒补发验收。后续变更只通过单提交 PR 进入 `master`，生产继续保持 HTTPS-only。
