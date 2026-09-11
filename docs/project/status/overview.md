# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-09-13
- 当前分支：`codex/contextual-fact-followup`
- 实现基准：`3da34c1`
- 最后验证提交：`3da34c1`
- 最后验证范围：日期追问/语音定向 32/32，非 loopback 回归 497/497，V49 空库迁移、JAR/镜像、新备份隔离恢复、本机/LAN 健康和文档检查通过
- 当前部署：LAN HTTP development mode
- 生产边界：HTTPS-only

## 开发暂停与合并交接

2026-09-13 推送前采用较小 JVM 内存复验语音定向 32/32 通过（此前默认 JVM 启动曾受本机内存不足影响）；497/497 回归为 2026-09-12 的已通过结果。用户确认当前核心功能满足正常使用，要求暂停后续优化并推送已完成实现，等待人工合并。本次仅交付已完成的 COMPANION-004/005/006；“换个话题”尚未实现，不包含在交付中。后续优化与延期实体验收不作为继续开发的授权。

下一条操作：推送当前任务分支后由用户创建 PR、审核并合并；Agent 不创建或合并 PR，不再启动新功能开发。运行版本保持 `fact-followup-v49`。

## 已完成并发布的 COMPANION-006

- 日期省略追问按近期连续用户话题继承天气/日程必需工具；当前明确问题优先，游戏转题、其他工具和不完整历史会停止继承，普通闲聊快速路径保留。
- 定向 32/32、排除既有八个 Windows loopback 类后的回归 497/497、V1..V49 空库迁移、打包和文档检查通过。实际模型日期理解与措辞未冒充自动化验收。
- 新备份及隔离恢复成功后只替换 LAN server：容器 `d449921c54d6`，镜像 `sha256:38da048573e52aef1bbb1e6fa35e1e8ba782d1cbb857810c8f9740f40916448c`，版本 `fact-followup-v49`，本机/LAN 健康 ok 且无重启。
- 复用已部署页面资源，仅替换 JAR；数据库 V49、PostgreSQL/Redis/备份容器和设备 `4444860 / motion_disabled / DISABLED` 不变。回退可使用上一镜像标签 `proactive-context-v49`。
- 设计见 [ADR 0056](../decisions/0056-contextual-voice-fact-followups.md)。本轮无代码或部署阻塞；后续为实体日期追问复测和用户审核。保留前轮未推送实现并整理为一个中文任务提交，外部推送等待明确授权。

## 已完成并发布的 COMPANION-005

- 当前设备、当前角色最近三十分钟内成功播放的最新一条主动消息进入语音上下文，用户可以继续追问；未播放、失败、过期、未来记录及其他设备/角色均被排除。
- 只读取有长度上限的正文和已有来源元数据，明确区分标题信息与一般解释，不假装已读取文章全文；读取失败时继续普通语音。
- 定向 26/26、排除既有八个 Windows loopback 测试类后的服务端回归 492/492、V1..V49 空库迁移和 JAR 打包通过。
- 新备份和隔离恢复成功后仅更新 LAN server：容器 `398d9a946aa9`，镜像 `sha256:46b814c136d1092d82a7cc46d797a35381f04e3d2fa3a0244c77d7b04e52fd40`，版本 `proactive-context-v49`；本机/LAN 健康 ok、无重启，数据库 V49、依赖容器和设备 `4444860 / motion_disabled / DISABLED` 保持不变。
- 复用已发布页面资源，只替换已验证 JAR；保留上一版本 `bounded-history-v49` 供回退。无新增 TTS 调用、迁移、页面、固件或主动次数。
- 设计见 [ADR 0055](../decisions/0055-delivered-proactive-conversation-context.md)。代码和发布无阻塞；真实模型措辞尚待正常主动播报后的用户追问复测，Git 推送等待明确授权。

## 已完成并发布的历史读取优化

用户确认上一轮可用后继续开发。历史改为数据库过滤并仅返回最近二十条正文，按既有索引排序；查看与导出完整记录接口不变。定向 29/29、排除既有八个 Windows loopback 类后的回归 489/489、V49 空库迁移和打包通过。新备份及隔离恢复后只替换 server：容器 `5ff72ab79651`，镜像 `sha256:75260733ece0a01043b72b7320fb49cad347bbfc0478f123db4608bd5a44e67c`，版本 `bounded-history-v49`，本机/LAN 健康 ok、无重启，设备保持 `4444860 / motion_disabled / DISABLED`。实际端到端提速未测量；当前无代码阻塞，等待审核及推送授权。

## 已完成并发布的 COMPANION-004

- 2026-09-12 重新 fetch 确认最新 master 为 `3da34c1`，任务从该提交继续；旧状态中的 COMPANION-003 待审核描述已由 PR #39 合并事实取代。
- 语音从已有最近二十条历史中，按持久化回复关联选取三十分钟内至多四个完整轮次；同时间戳、缺失用户消息和失败回复不再按相邻位置误配。历史正文继续作为消息传递，不提升为系统指令。
- 省略表达优先承接最近一轮，缺少对象时提示模型简短澄清；长期记忆不得用于补造当前游戏或事件。措辞质量仍需实体对话复测。
- 最终非 loopback 服务端 487/487、V1..V49 空库迁移、JAR 打包和镜像构建通过。三项既有语音修复继续保留：短语音快速路径、一次 TTS/一个 WAV、无有效语音按 NO_SPEECH 结束。
- 新备份与隔离恢复成功，只替换 LAN server；容器 `18054bd71191`、镜像 `sha256:4ffc790589e3fa8e2cd155d94b709e768d9dd3a28e5d31b6eefbcdf6b5a32fff`、版本 `conversation-context-v49`，本机和 LAN 健康为 ok，无重启。
- 镜像以已部署的 `c923491` 镜像为基础，仅替换已测试 JAR；管理页面复用已发布版本。数据库 V49、依赖容器和 CoreS3 `4444860 / motion_disabled / DISABLED` 保持不变。
- 设计见 [ADR 0054](../decisions/0054-bounded-recent-voice-context.md)。未完成项为用户实体措辞复测与 Git 推送，推送等待明确授权。

## 当前结论

`COMPANION-003` 已完成并发布：新增默认关闭、与语音主动问候独立的无声陪伴开关；开启后复用当前允许时段，每 30–90 分钟随机显示 6–8 秒弱/中等动态表情，每个本地日期最多八次。离线、免打扰、活动语音和正在播放的提醒会抑制显示且不占次数；实现不调用模型、不播音、不读取摄像头、麦克风或电脑活动，也不执行身体动作。详见 [ADR 0053](../decisions/0053-bounded-silent-presence-expressions.md)。

`COMPANION-002` 已完成并发布：服务端后台读取 Hacker News 官方只读 API 并保存不超过两小时的通用技术标题缓存，不发送用户兴趣；只有个性化开启且选出已确认兴趣后，模型才在最多六个标题中返回候选编号和短开场。最终标题与来源由程序确定性拼接，V48 保存原始标题、HTTPS 链接、Hacker News 条目收录时间和抓取时间，管理端提醒列表可追溯查看；无匹配、超时或输出不合规均退回普通人设问候。详见 [ADR 0052](../decisions/0052-source-backed-interest-briefs.md)。

`COMPANION-001` 已完成实现并发布：保留显式开关，在设备时区的指定窗口内保存随机候选，每天最多三次且两次至少间隔一小时；到点后仍经过在线、免打扰、语音忙碌和提醒单飞仲裁。开场措辞读取当前活动角色人设；自动建议只有被识别为用户明确兴趣时才预置允许主动提及，仍须用户确认后生效。当前 DeepSeek 官方接口没有可核验来源的联网搜索，本阶段从已确认兴趣切入但禁止生成“最近发布、最新模型”等无来源资讯。详见 [ADR 0051](../decisions/0051-persona-aware-random-proactive-conversation.md)。

`VOICE-001` 已完成代码、自动化与 LAN 发布。八十码点以内且没有外部查询意图的设备语音使用关闭推理的直接流式模型。用户纠正本轮问题是两段音频之间等待，而非整体调用慢；日志确认首段同步 TTS 会阻塞后续模型文本消费，余句随后才单独合成。当前实现让每个语音回合只进行一次 TTS、只下发一个 WAV，所有标点语气停顿由语音服务生成；正文优先摘要到 160 码点以内，模型超限时在自然句界安全收束。当前时间语音直接执行已授权的本地 Tool 并确定性生成单句，不经过 ReactAgent；其他事实能力仍保持受控 Tool。非实时 ASR 明确返回“无有效语音/无词”时按 `NO_SPEECH` 安静结束，不再显示语音服务错误。详见 [ADR 0050](../decisions/0050-low-latency-natural-voice-conversation.md)。

V46 后台信息架构、真实二级菜单、无黑块聊天输入区、窄屏消息滚动、天气图标、未来七天只读日程和归档七天后手动删除已经发布并在当前整合分支完整保留；边界见 [ADR 0047](../decisions/0047-isolated-tdesign-chat-ui.md) 与 [ADR 0048](../decisions/0048-guarded-archived-role-deletion.md)。语音整块上传阻塞已由 `a70fb9c` 实机修复；为把 286/436 ms 的完整 WAV 上传进一步压缩，用户确认采用 [ADR 0049](../decisions/0049-local-vad-gated-live-voice-upload.md) 的本地 VAD 门控边录边传。首个 `b2303e8` 固件两轮尾部达到 136/17 ms，但同步 HTTP 写入阻断 I2S；`628acd0` 已把采集与 24 KiB PSRAM 上传任务分离，用户确认两轮识别恢复正常。短首段和 MCP 预热发布后响应已变快，但最新两轮的设备播放阶段比 PCM 时长额外增加约 5.12/24.16 秒；服务端在设备播放期间已经发完后续段，确认是固件在 HTTP 回调内同步播放、暂停继续收帧，而不是机器人或服务端内存不足。当前候选把收帧与播放拆开，使用深度 1 的 PSRAM 预取队列和独立 24 KiB 播放任务。首次刷入 `4aa700a` 暴露 WebSocket 8 KiB 栈超出启动时 7680 字节最大内部连续块；最终纠正使用官方动态缓冲、7168 字节实测可容纳栈、WebSocket 优先启动和队列就绪门控，静态 DRAM 未增长，问题不是总内存或 PSRAM 不足。

`MEDIA-004` 任务提交 `8c28f0a` 已由 `fa093dc` 合入 `master`。V1 PNG 与 V2 EAF 共享互斥 A/B 槽，服务端、页面和固件严格支持 `boot_appear`、`wake`、`role_switch`，原生 LVGL 始终负责连续表情与安全回退。V36 与页面已部署；用户暂无 EAF 素材，因此 V2 实体激活验收延期，CoreS3 保持 `71868da`，本主线不 OTA、不把延期写成通过。

`WORK-001` 私用工作日桌面陪伴 V1 已由 `c62ccd0` 合入。V37–V42、确定性首次简报、在席 50/10、三种休息回应、返回表现，以及管理页/确认式语音/顶部长按三个显式入口已经闭环；实机 `424cb49` 顶部长按启停通过且全过程保持 `motion_disabled`。详细边界见[开发设计](../workday-companion-v1.md)和 [ADR 0041](../decisions/0041-private-first-deterministic-workday-companion.md)。

`WORK-002` 已由 `f0bce2d` 合入。V43 保存误播报、外部服务降级归因、动作安全结果、设备重启和显式观察窗口，管理页提供确定性 `COLLECTING/PASS/FAIL` 报告。管理员已于 2026-08-30 17:35（Asia/Shanghai）显式开始观察，固定窗口为 2026-08-30 至 2026-09-12，工作日掩码为 31（周一至周五）；第十四天完整结束后再生成最终结论。设计边界见[WORK-002 文档](../workday-pilot-observability.md)和 [ADR 0043](../decisions/0043-explicit-local-workday-pilot-observability.md)。

`WORK-003` 已在当前任务分支完成并发布为 V44。最终窗口结束后，服务端通过现有可靠提醒队列只向机器人入队一次固定 `PASS/FAIL` 结论；窗口日期、门槛、采集口径和当前聚合均不改变。入队标记、观察行锁和数据库唯一键共同阻止重启或并发重复，详细报告仍只在管理页查看。当前窗口首次调度后保持未通知且完成提醒数为零；详见[WORK-003 文档](../workday-pilot-completion-notification.md)。

`WORK-004` 已完成自动化并发布为 V45。管理端可按设备与角色管理本地个人待办；未来截止时间复用可靠提醒，机器人通过最小只读 Tool 回答当前待办，语音新增和完成继续要求确认并保持幂等。默认角色保留 ID 的前端误校验已修复并发布，普通提醒和通知集成同步使用同一合法边界；用户已确认默认角色新增正常。Agent 不接收备注，过期任务不补播，设备/角色不可改绑。其余人工验收继续进行；详见[WORK-004 文档](../personal-task-management.md)和 [ADR 0044](../decisions/0044-local-first-confirmed-personal-tasks.md)。

`WORK-005` 已完成并发布。每日首次简报会确定性读取当前设备与活动角色最多两项逾期、今天到期或高优先级待办，只播报截断标题和分类，备注不进入正文；查询失败不抑制天气/日历简报。每天一次去重、`SUCCESS/PARTIAL` 和十四天观察口径均不改变；详见[WORK-005 文档](../workday-personal-task-brief.md)和 [ADR 0045](../decisions/0045-deterministic-private-task-daily-brief.md)。

`WORK-006` 已完成并发布。既有 `current_personal_tasks` 保持原字段兼容，并追加当前设备时区、当前活动角色的今日完成项；完成项只提供标题。该能力只响应用户主动查询，不新增日终播报、迁移、页面、固件或身体动作，也不改变十四天观察口径；详见[WORK-006 文档](../workday-daily-task-progress.md)和 [ADR 0046](../decisions/0046-private-read-only-daily-task-progress.md)。

`BODY-001` 已完成 K151 接近/环境光、顶部触摸、反馈舵机和本地安全状态实现，协议只允许校准、显式开关和五种固定模板，禁止任意角度/速度/循环。V41 和管理页已发布。经用户授权，当前任务固件已通过 COM3 保留 NVS 直刷；严格 USB 本地校准诊断连续两次完成两路反馈中位校准，设备全程保持 `motion_disabled`。根因为按需开启 VM 后 250 ms 不足以覆盖升压轨和两个舵机冷启动，等待延长至 1200 ms 后稳定恢复；INA226 同时确认底座电池源存在。实体动作启用和模板仍未授权、未执行。安全边界见 [ADR 0042](../decisions/0042-local-first-k151-body-safety.md)和[实体冒烟 runbook](../../runbooks/k151-body-motion-smoke-test.md)。

## 工作流摘要

| 工作流 | 状态 | 当前事实 | 下一步 |
| --- | --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | V49、短时话题及日期追问工具衔接已发布 | 用户审核任务提交 |
| [前端](frontend.md) | READY_FOR_REVIEW | 主动关心页提供默认关闭的无声陪伴开关和下一候选时间 | 用户审核任务提交 |
| [固件](firmware.md) | READY_FOR_REVIEW | `4444860` 已保留 NVS 安装且 WebSocket 稳定在线 | 用户唤醒两轮并监听段间日志 |
| [部署](deployment.md) | READY_FOR_REVIEW | LAN 运行 `fact-followup-v49`，数据库为 V49 | 保持默认关闭并等待用户审核 |

## 当前能力地图

- 对话：流式文字聊天、本地唤醒语音、完整回复、连续对话、触摸取消、隐私安全诊断和有序分段播放。
- 陪伴：角色容器、可选角色音色、确认记忆、建议过滤、相关检索、周期提醒、免打扰、有界主动关心和无声陪伴表情；人设与陪伴数据按角色隔离。
- Agent：受控 ReactAgent、Skill ZIP、只读 Tool、页面管理的 Streamable HTTP MCP Client 和语音动作确认。
- 待办：设备与角色隔离的本地 CRUD、可靠截止提醒、最小只读 Agent Tool，以及确认式语音新增与完成。
- 设备：配对/JWT/WebSocket、唤醒模型 OTA、动态球形表情、兼容八状态 PNG 包和应用 A/B OTA。
- 数据与运维：个人数据搜索/导出/删除、7 日/4 周备份、隔离恢复和健康中心。
- 外部通知：固定设备集成、一次性令牌、幂等 REST/MCP、可靠单飞、互动回执，以及可选的同集成确定性原文摘要。
- 日历：V39 提供 iCloud CalDAV 只读发现与 `REPORT` 查询、加密 App 专用密码、Apple Account 已验证邮箱、显式允许列表、私人事件脱敏、二十四小时缓存、每小时同步和设备绑定的未来七天 Agent Tool。区域回退仅在全球认证失败时尝试中国大陆入口，只允许 Apple 全球/中国大陆根入口和 `p数字-caldav` HTTPS 分片。
- 天气：V40 按设备固定位置保存 Open-Meteo 当前及今明两天的最小必要字段；缓存最长一小时，支持无保存连接测试、手动同步、启动补同步、到期前续期、确定性中文摘要和设备绑定只读 Agent Tool，不允许模型指定位置或猜测失效数据。

完整合并记录见[里程碑索引](../milestones.md)，长期架构约束见[ADR 索引](../decisions/README.md)。

## 版本与运行态

- Git：`COMPANION-002` 已由 `06ab0b3` 合入主线；当前任务分支 `codex/contextual-fact-followup` 基于 `master@3da34c1`。
- LAN server：`silent-presence-v49-final` 运行于 `http://192.168.1.4:8080/`；运行库为 V49，详情见[部署状态](deployment.md)。
- CoreS3：当前运行 `4444860` LAN HTTP Quad；经 COM3 保留 NVS 安装，Wi-Fi、设备身份、8192 字节主栈、WakeNet 和 `motion_disabled` 保留，WebSocket 已稳定在线并向服务端上报新版本。
- 实机固件提交只作为运行候选，不能替代 `master` 作为新任务分支基线。

## 最近验证

- 2026-09-11 COMPANION-003：服务端定向 25/25、排除 8 个既有 Windows loopback 类后的 482/482、控制台 32 文件 102/102、类型检查、production build 和定向 ESLint/Stylelint 通过；Testcontainers 从空库成功应用 V1..V49。发布前新备份与最新备份隔离恢复成功，只替换 server；当前容器 `a8c901a1156b`、镜像 `sha256:c9234911630d5ca42fb804c58aa734f29b9c8919e09008321cfae510979a320a`、构建版本 `silent-presence-v49-final`，健康为 `ok` 且无重启。本机与 `192.168.1.4` 首页为 200，运行静态资源包含“无声陪伴”；现有一条设置保持开关关闭且无候选。PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换，设备保持 `4444860 / motion_disabled`。

- 2026-09-11 COMPANION-002：发布前新 PostgreSQL 备份和最新备份隔离恢复成功，只替换 server；最终容器 `a145b96ea279`、镜像 `sha256:085ab90c9602e16789bdfd53f1f281d7695fc21d2454514dca607f21c6f92e7c`、构建版本 `companion-sourced-v48-final`。运行库为 V48，本机和 `192.168.1.4` 首页为 200，健康为 `ok`，未认证提醒接口为 401，最终容器后台成功缓存 12 条 Hacker News 候选。PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；主动开关和个性化保持关闭，最小间隔 90 分钟、每日上限 3 次，设备保持 `4444860 / motion_disabled / DISABLED`。

- 2026-09-10 COMPANION-001：随机调度、人设开场、兴趣权限和 API 定向 22/22；排除 8 个既有 Windows loopback 类后的服务端 470/470，完整 502 个用例中 31 个错误仍全部来自这些基础设施限制；控制台 32 文件 102/102、类型检查和 production build 通过。发布前生成新备份并完成最新备份隔离恢复；旧镜像保留为 `pre-companion-random-v47`，只替换 server。当前容器 `0a388ac7217c`、镜像 `sha256:691d99e0f6756df2cba153a5e73facde96f4f3e4cf94df9ee868a2fb7f6a15ca`、版本 `companion-random-v47`；本机与 LAN 健康为 `ok`，运行库 V47，MCP 预热完成。PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；主动开关保持关闭，设备保持 `4444860 / motion_disabled / DISABLED`。

- 2026-09-08 主线冲突修复：当前任务分支已变基到 `master@f32386a`，Git 合并模拟无冲突，相对主线恰好一个任务提交；已合入主线的 V46 前端文件不再重复出现在差异中。服务端语音专项 20/20、三组固件栈预算、`git diff --check` 和 `pnpm docs:check` 均通过。

- 2026-09-08 WebSocket 启动回归纠正与安装：最终 `4444860` 使用官方动态缓冲、7168 字节 WebSocket 栈、优先启动和队列就绪门控。LAN HTTP Quad 为 1,650,016 字节、SHA-256 `23E94ACCF3CC72CC2D95B5DE6408E372924D61A72675FC38AE2A1A8853826536`；COM3 保留 NVS 写后哈希通过。启动确认 8 MiB PSRAM、Wi-Fi/身份、WakeNet、LAN HTTP 与 `motion_disabled`，WebSocket 在 7680 字节最大连续块下成功启动，连接时栈余量 4000 字节并稳定在线超过一分钟；服务端健康为 `ok`、无 OOM/重启，数据库已收到 `4444860 / motion_disabled` 心跳。

- 2026-09-07 段间播放预取候选：两轮服务端音频在设备播放期间已全部 flush，但设备播放 17.123/76.379 秒相对 PCM 12.000/52.220 秒额外增加 5.123/24.159 秒。固件将完整 SCV2 音频帧所有权移交给深度 1 的 PSRAM 队列和独立 24 KiB 播放任务，网络解析不再被同步播放阻塞。ESP-IDF 5.5.5 protocol/LAN HTTP Quad 完整构建通过，LAN 应用 1,649,632 字节（`0x192be0`）、分区余量 48%；三组栈回归通过，真实语音/上传/播放外部调用余量为 28,000/22,320/24,416 字节。候选尚未连接或刷写。

- 2026-09-07 首播延迟优化发布：首段目标 16 字、硬上限 24 字，MCP 目录在应用就绪后后台预热，语音链路新增隐私安全阶段日志。专项 20/20、排除既有 8 个 Windows loopback 类后 462/462 通过；发布前 13:07 UTC 新备份和隔离恢复验证成功。只替换 server，容器 `d126dfa8432d`、镜像 `sha256:a72c6b97bf9fc8c9617dabe79bce5723787c56dc13b44ddbb51e836e4b76218b`、版本 `voice-latency-v46-final`；MCP 目录预热 895 ms，服务无 OOM/重启，本机与 `192.168.1.4` 健康为 200，运行库 V46，设备继续在线上报 `628acd0 / motion_disabled`。PostgreSQL、Redis、备份容器和 CoreS3 未替换。

- 2026-09-07 实机语音复核：用户确认 `628acd0` 两轮识别恢复正常。隐私安全事件显示 ASR 为 562/303 ms；第一轮 Agent 为 12,153 ms、第二轮为 2,586 ms；完整 TTS 后到设备首播仍为 3,371/5,290 ms。主机剩余 12.29 GiB，server 容器 681 MiB、无 OOM、无重启，排除内存不足。

- 2026-09-05 实机语音纠正：两轮主请求的 PCM 为 9,600/32,000 字节、尾部为 136/17 ms，ASR 分别为“嗯”/“No”；同步网络写入造成约 1–1.8 秒采样缺口，PSRAM 约 7.7–8.0 MiB 可用且无复位，排除内存不足。纠正候选使用独立 24 KiB PSRAM 上传任务和双 100 ms 起始确认；protocol/LAN HTTP Quad 为 `0x3a140`/`0x1927a0`，分区余量 92%/48%，语音/上传任务外部栈余量分别 28,016/22,320 字节。

- 2026-09-05 V46 边录边传整合：服务端语音定向 18/18，排除既有 8 个 Windows loopback 类后的完整回归 460/460；控制台 32 文件 102/102、类型检查和 production build；三组任务栈负例/正例与真实语音栈预算通过。08:20 UTC 新备份和隔离恢复成功，只替换 server，数据库保持 V46；匹配 LAN HTTP Quad 固件通过 COM3 保留 NVS 安装，等待用户两轮独立唤醒。
- 2026-09-03 聊天滚动复修发布：发布前新备份与隔离恢复成功，只替换 server；容器 `f12a4040770c`、镜像 `sha256:0042f143a832aa8d5315dd902876db0dcb8c1f125e68f76ab3c4a6c1224cf165`、版本 `console-feedback-v46-chat-scroll-final`。V46、本机/LAN 首页与健康均为 200，聊天 CSS/JS 为 200，运行 CSS 确认固定视口高度、内部纵向滚动、滚轮边界和触摸滚动；基础数据容器和 CoreS3 未替换。

- 2026-09-03 二级菜单与聊天复修发布：发布前新备份与隔离恢复成功，只替换 server；容器 `044b01b409d3`、镜像 `sha256:6c9d7ff0a125abf8103a440cac94b64b902e65a19b0bd5b7a0cf9e437342b83d`、版本 `console-feedback-v46-menu-chat-final`。V46、本机/LAN 首页与健康均为 200，运行资源确认“今日陪伴/事务管理”、主动关心、Enter 发送、停止生成及 OKLCH 主题映射；基础数据容器和 CoreS3 未替换。

- 2026-09-03 二级菜单与聊天复修验证：控制台 Vitest 32 文件 102/102、类型检查、production build 与定向格式检查通过；新增菜单 Store 用例直接验证最终侧栏层级。应用内浏览器仍被宿主插件缓存版本错配阻塞，因此视觉结果等待用户强制刷新确认。

- 2026-09-01 六项后台反馈发布：新备份及内置隔离恢复成功，只替换 server；当前容器 `afefeaf5884f`、镜像 `sha256:a4066d42cb3eb5e8f5655c0648cac8009e559c577ac9d6767f7edcc919712328`、版本 `console-feedback-v46-final`。运行库由 V45 迁移至 V46，健康、本机/LAN 首页为 200，角色与日程未认证接口为 401，最终聊天/工作日/角色资源为 200；PostgreSQL、Redis、备份容器与 CoreS3 未替换。

- 2026-09-01 六项后台反馈验证：控制台 Vitest 31 文件 101/101、类型检查、production build 和定向 ESLint/Stylelint 通过；服务端角色/日历/工作日定向 14/14，空 PostgreSQL 成功应用 V1..V46。完整服务端 490 个用例中 31 个错误均来自既有 Windows Java loopback 限制且业务断言失败为零。

- 2026-09-01 后台信息架构与 UI 重整发布：11:53 UTC 新备份、内置隔离恢复与最新备份独立恢复成功；旧镜像保留为 `pre-console-ia-3697be8`，只替换 server。当前容器 `c580d0855c3c`、镜像 `sha256:a3b015c36bc17db419b87993081badf3b744527b50627ac6f527f86c0649e85d`、版本 `console-ia-3697be8`。本机与 LAN 首页为 200、未认证设备接口为 401，V45、新 dashboard/chat 静态资源、1 条未完成待办、观察窗口与 `424cb49 / motion_disabled / DISABLED` 均通过；PostgreSQL、Redis、备份容器和 CoreS3 未替换。

- 2026-09-01 后台信息架构与 UI 重整：控制台 Vitest 30 文件 99/99、类型检查与 production build 通过，本任务文件定向 ESLint 和聊天页 Stylelint 通过，`git diff --check` 与文档检查通过。测试中既有 3000 端口拒绝连接与 Vite 关闭超时提示不影响成功退出；TDesign Chat 独立懒加载 chunk 的体积告警已记录在 ADR 0047，未扩散到普通管理页。

- 2026-09-04 边录边传候选：服务端语音定向 18/18，完整 490 个用例中 31 个错误全部来自既有 8 个 Windows loopback 类且失败断言为 0，排除后 458/458；ESP-IDF 5.5.5 protocol 与 LAN HTTP Quad 完整构建通过，应用分别为 237,888 和 1,647,712 字节、分区余量 92%/48%。三组任务栈回归通过；真实语音本地最坏路径 4,752 字节、32 KiB 栈外部调用余量 28,016 字节。文档检查通过；未发布服务端、未连接或刷写 CoreS3。

- 2026-09-04 运行态复核：LAN server 实际为 V46 `console-feedback-v46-chat-scroll-final`，容器 `f12a4040770c`、镜像 `sha256:0042f143a832aa8d5315dd902876db0dcb8c1f125e68f76ab3c4a6c1224cf165`，数据库为 V46。15:25 UTC 新备份和独立恢复验证成功；识别到当前边录边传分支缺少未合入的 V46 后停止发布，未替换任何容器、未操作 CoreS3。
- 2026-08-31 WORK-006：单元定向 20/20、真实 PostgreSQL 5/5、完整 488 个用例中 31 个错误全部来自 8 个既有 Windows loopback 类且业务断言失败为零，排除后其余 456/456 通过；镜像内服务端和管理端 production build 通过。12:30 UTC 新备份、12:31 UTC 独立恢复验证成功，只替换 server；容器 `9617c52a48db`、镜像 `sha256:6b120ef75ae32845b679d2ce976cf91260928a0dde3304b0ca63db61b19c79cb`、版本 `work006-v45-task-progress`。健康和 LAN 首页为 200，V45、1 条未完成待办、观察窗口均保留，设备在线并保持 `424cb49 / motion_disabled / DISABLED`。

- 2026-08-31 WORK-005：最终定向 18/18、真实 PostgreSQL/Flyway V1..V45 查询和持久化通过，排除 8 个既有 Windows loopback 类后服务端 452/452；镜像内服务端与管理端 production build 通过。11:14 UTC 新备份及独立恢复验证成功，只替换 server；容器 `e384ffebb23a`、镜像 `sha256:f9e5db38787621ea85d26d302fc92a0d6b5aee6f4051c85b43c1d460d9675112`、版本 `work005-v45-task-brief`。健康和 LAN 首页为 200，V45、1 条待办、2026-08-30 至 2026-09-12 观察窗口均保留，设备在线并保持 `424cb49 / motion_disabled / DISABLED`。

- 2026-08-30 WORK-004 默认角色校验修复：确认项目保留默认角色 UUID 被严格 RFC 版本位误拒绝，改为与 PostgreSQL UUID 文本边界一致的共享校验。专项 7/7、完整控制台 30 文件 98/98、类型检查、production build、ESLint 和 Stylelint 通过。13:33 UTC 新备份与隔离恢复成功，只替换 server；运行版本 `work004-v45-role-fix`，V45、健康/鉴权、观察窗口和 `424cb49 / motion_disabled / DISABLED` 均正常。
- 2026-08-30 用户确认默认角色新增不再出现“请选择角色”，该发布回归点人工验收通过。

- 2026-08-30 WORK-004/V45：待办、Agent Tool、确认式语音动作与 PostgreSQL 持久化定向 40/40；完整服务端 480 个用例中 31 个错误全部来自 8 个既有 Windows Java loopback 测试类，业务断言失败为零，排除后其余 448/448 通过；空 PostgreSQL 成功应用 V1..V45。控制台 Vitest 29 文件 93/93、类型检查和 production build 通过。21:06 新备份与隔离恢复成功，只替换 server；运行库为 V45，本机/LAN 首页和健康为 200，未认证待办接口为 401。待办表为空，观察窗口、完成通知标记和设备 `424cb49 / motion_disabled / DISABLED` 未变化。

- 2026-08-30 WORK-003/V44：完成通知、重启清理、单设备失败隔离和 PostgreSQL 持久化定向 13/13；完整服务端 465 个用例中 31 个错误全部来自 8 个既有 Windows Java loopback 测试类，业务断言失败为零，排除后其余 433/433 通过；空 PostgreSQL 成功应用 V1..V44。发布前新备份和隔离恢复成功，只替换 server；运行库为 V44，本机/LAN 首页和健康正常，观察窗口与设备安全状态未变化，首次调度后完成提醒数为零。

- 2026-08-30 WORK-002 观察启动：数据库确认当前设备窗口为 2026-08-30 至 2026-09-12，时区 `Asia/Shanghai`，工作日掩码 31；启动时间为 17:35:33。设备随后持续在线，上报 `424cb49 / motion_disabled / DISABLED`，未启用或执行身体动作。第十四天完整结束前状态保持 `COLLECTING`，最早于 2026-09-13 读取最终 `PASS/FAIL`。

- 2026-08-30 WORK-002：服务端最终定向 45/45 通过；排除 8 个已确认受 Windows Java loopback 限制的既有网络测试类后，其余 424/424 通过，空 PostgreSQL 从 V1 应用到 V43。控制台完整 Vitest 28 文件 91/91、类型检查和 production build 通过，最终工作日 API 6/6、类型检查和 build 复跑通过。发布前及边界修正后备份校验成功，只替换 server；最终容器 `e2dcfa2fbe86`、镜像 `sha256:db8e8aca683db95ee1ace9273ae80aac8deb9368f2af15ecfae612e615dd1c72`，运行库迁移至 V43，本机/LAN 健康与首页为 200，未认证观察接口为 401。PostgreSQL、Redis、备份容器及 CoreS3 未替换；设备在线并保持 `424cb49 / motion_disabled / DISABLED`。

- 2026-08-30 WORK-001 实机收口：用户自行安装 `424cb49` LAN HTTP Quad 固件；设备在线上报同版本且保持 `motion_disabled / DISABLED`。临时启用周日后，1500 ms 顶部长按成功创建 `ACTIVE_PRESENT` 运行态；第二次长按切换为 `OFF`，跨十五秒调度周期未自动重启。`VOICE_STOP` 确认为语音交互开始时的安全停动记录，不是身体硬件故障。当前设备未上报接近、环境光和舵机反馈能力，因此这些路径继续按能力缺失安全降级，未宣称实体环境光验收通过。

- BODY-001 无动作校准闭环：`9ae97ba` LAN HTTP Quad 镜像校验有效并通过 COM3 仅写应用分区，NVS 保留。自动诊断避开语音占用后连续两次返回 `body_calibration=complete`；两次均确认 INA226 电池源存在、两路位置反馈有效并记录“motion remains disabled”。根因为安全设计按需开启 VM 后只等待 250 ms，短于真实冷启动；延长至 1200 ms 后稳定恢复。全程未启用或执行动作。

- 天气续期修复：专项 5/5 通过；完整服务端共运行 430 个用例，30 个因受限环境无法建立 Java loopback 而出现基础设施错误，无业务断言失败。发布前备份与校验成功，只替换 server；健康为 `ok`，真实同步状态为 `READY`，缓存包含 2026-08-28/29 两条预报且未再出现唯一键错误。8 月 29 日固定地点预报为毛毛雨、最高降水概率 78%、预计降水 1.2 mm。
- USB 服务地址快捷更新实机：经授权通过 COM3 安装 `fe95767` LAN HTTP Quad，未擦除或写入 NVS；设备保留 Wi-Fi、身份和 `motion_disabled` 并连接 `192.168.1.4:8080`。
- USB 服务地址快捷更新：前端 Vitest 28 文件 88/88、`vue-tsc -b` 和 production build 通过；配网、语音与传输三组任务栈回归和静态预算全部通过。ESP-IDF 5.5.5 protocol profile 为 244,496 字节、余量 92%，LAN HTTP Quad 为 1,618,560 字节、余量 49%；工作树制品只作构建证据，不作为安装候选。部署前新备份与隔离恢复成功，只替换 server；健康为 `ok`、运行库保持 V40、本机与 LAN 首页为 200，运行资源包含新按钮。未连接或操作 CoreS3。
- WEATHER-001 用户验收：用户确认经纬度输入修正、真实固定位置同步和机器人天气问答均正常，任务功能闭环通过。
- WEATHER-001 经纬度输入修正：确认 `type=number` 的模型数值转换与 Zod 字符串模型冲突，改用保留字符串的十进制文本输入。控制台 86/86、类型检查和 production build 通过；新备份与隔离恢复成功，只替换 server，V40 无待迁移项，健康和当前 LAN 首页为 200，基础容器及 CoreS3 未变化。
- WEATHER-001：Open-Meteo、天气缓存/调度、管理 API、设备绑定 Tool 和强制路由定向 25/25；服务端完整 429/429，通过空 PostgreSQL 应用 Flyway V1..V40。控制台 Vitest 28 文件 86/86、`vue-tsc -b` 和 production build 通过；既有 3000 端口拒绝连接与 Vite 关闭超时提示不影响成功退出。发布前备份和隔离恢复成功，只替换 server；运行库迁移到 V40，本机和当前 LAN 首页为 200，未认证天气接口为 401，运行静态资源包含 `Open-Meteo`。未修改或操作固件。
- CONN-001 Agent 日历闭环：真实运行库存在一条未过期缓存事件；对应原语音回合审计只记录 `current_date_time` 和 `next_device_reminder`，确认错误不在 iCloud 同步而在 Agent 缺少日历数据源。新增每小时同步、允许列表门控、单连接失败隔离、设备绑定只读 Tool、未来七天重叠事件查询和日程问题强制调用。专项 18/18、服务端完整 418/418、空库 Flyway V1..V39 通过；发布前新备份和隔离恢复成功，只替换 server。运行镜像为 `sha256:fafc4e24a7d0191f20ec3300a2100517ed43c5c82d34088e0858c0edaaa688ed`，健康状态为 `ok`，本机与 LAN 首页为 200，未认证日历接口为 401，运行库保持 V39 和一条未过期缓存事件，无迁移、前端或固件改动。
- CONN-001 Apple 分片修正：真实账号 Shell 逐步验证 principal 207、calendar-home-set 207、编号中国大陆分片日历发现 207，共发现四个日历；手机号与故意错误密码均固定返回 403，因此撤销手机号兼容入口。日历定向 7/7、服务端完整 412/412、空库 Flyway V1..V39、控制台 85/85、类型检查和 production build 通过。发布前备份和隔离恢复成功，只替换 server；本机/LAN 首页和健康接口正常，未认证日历接口为 401，运行库保持 V39，无固件改动。
- CONN-001 中国大陆区域修正：Apple 官方资料确认中国大陆 iCloud 使用 `*.icloud.com.cn` 服务域；新增全球认证失败后回退、中国大陆完整发现/查询和非认证失败不重试测试。日历定向 6/6、服务端完整 411/411、空库 Flyway V1..V39 通过。发布前备份和隔离恢复成功，只替换 server；本机/LAN/健康接口均为 200，运行库保持 V39，无迁移、前端或固件改动。
- CONN-001 手机号兼容：服务端日历定向 7/7、完整 409/409 和空库 Flyway V1..V39 通过；控制台 Vitest 28 文件 85/85、类型检查和 production build 通过。中国大陆 11 位手机号自动补 `+86`，国际号码要求 E.164 国家码，响应仅返回脱敏标识。发布前备份及隔离恢复成功，只替换 server；本机/LAN/健康接口均为 200，未认证日历接口为 401，运行静态资源包含新账号标签。尚未使用真实 Apple 凭据，未操作固件。
- CONN-001/V39：加密服务、CalDAV 与控制器定向 5/5、完整服务端复跑 406/406 通过，Testcontainers 从空 PostgreSQL 应用 Flyway V1..V39；完整控制台 Vitest 28 文件 85/85、`vue-tsc -b` 和 production build 通过。发布前备份及隔离恢复成功，只替换 server，运行库迁移到 V39，本机/LAN/健康接口均为 200，未认证日历接口为 401；未连接真实 Apple 账号、未操作固件。
- WORK-001 阶段 A/V38：服务端定向 8/8、真实 PostgreSQL 去重专项 1/1、完整 404/404 通过，空库成功应用 Flyway V1..V38；控制台 Vitest 28 文件 84/84、类型检查和 production build 通过。发布前备份与隔离恢复通过，只替换 server，运行库迁移至 V38，本机/LAN/健康接口均为 200；未访问 iCloud、未连接或刷写设备。
- WORK-001 阶段 A 第一切片：服务端定向 4/4、完整重跑 399/399 通过，Testcontainers 从空 PostgreSQL 应用 Flyway V1..V37；控制台 Vitest 28 文件 83/83、`vue-tsc -b` 和 production build 通过。完整服务端第一次运行命中既有异步重复请求用例的瞬时 `ConcurrentModificationException`，单独 1/1 通过后全量复跑成功。发布前备份与隔离恢复通过，只替换 server，运行库迁移至 V37，本机/LAN/健康接口均为 200；未连接 iCloud、未操作设备。
- MEDIA-004：服务端 395/395、空库 Flyway V1..V36 通过；新增重连能力门控明确阻止回退到旧固件后继续安装 V2。控制台 Vitest 27 文件 82/82、类型检查和 production build 通过；ESP-IDF 5.5.5 protocol profile 与独立 sdkconfig 的 LAN HTTP Quad profile 均编译通过。LAN 候选为 1,618,336 字节、最小应用分区余量 49%，SHA-256 `BBF266A5FB77A47198FDC1EC4D22D9962DE1F32C054F69EC7FE4908AC84263F2`；该未提交工作树候选仅作为构建证据，不用于 OTA。
- MEDIA-004 LAN：发布前备份和隔离恢复成功，只替换 server；运行库迁移至 V36，健康接口和本机/LAN 首页为 200，未认证表情包与设备接口为 401，PostgreSQL、Redis 和备份容器未替换。CoreS3 未刷写。

- MEDIA-003 项目自有 EAF 生成器 3/3 通过；53,854 字节制品 SHA-256 为 `ABD64A59F59CAFF9CEE7A65921781BF6FE28B150AD876ADDF8CF52505690FE81`，内嵌制品与生成结果逐字节一致。
- 默认 ESP-IDF 5.4.4 LAN HTTP Quad 完整固件仍为 1,581,488 字节，与 MEDIA-002 最终稳定制品同尺寸。ESP-IDF 5.5.5 同口径 native、EAF RLE-only 和 Emote lifecycle 分别为 1,605,088、1,669,504 和 1,615,776 字节；三者均编译通过。
- `71868da` 制品为 1,669,504 字节，SHA-256 `7E794FDECA4A4CC005C87389A691C3B86FBD783B931E5086607F479394880206`；应用 OTA 为 `INSTALLED`，NVS 保留，未主动演练回退。
- 用户确认 EAF 只在开机窗口显示并恢复 native、三次唤醒对话与声音正常、TTS 触摸停止及下一回合正常，无黑屏、卡住或自动重启。稳定诊断约 55/60 FPS、绘制 1097 μs、传输 22627 μs、锁等待 10 μs、音频 underrun 0、最低空闲堆 7,735,712 字节。
- `41b8827-perf9` 已通过用户实机验收：固定 60 FPS 待机约 `55/60 FPS`，场景更新约 1041 μs、LVGL 刷新约 11077 μs、锁等待约 462 μs，音频 underrun 0、最低空闲堆约 7.56 MiB；用户确认画面和音量正常。
- `41b8827-perf9` LAN HTTP Quad 制品为 1,581,488 字节，SHA-256 `D5911FE9CAF747799DFE7C1D3D5745611D7B23AA0114D210EAD4973931761699`；保留 NVS、未做回退演练。
- 2026-08-23 经授权完成 MEDIA-002 最终 LAN 发布：部署前新备份和隔离恢复成功，只替换 server；运行镜像为 `sha256:bfd2019b4e8a84a0132794096d751f3bce872555165a11219081b820f9445810`，健康接口与 `192.168.1.3:8080` 首页为 200，启动日志无错误。设备重连后上报 `ADAPTIVE 45–60`、目标 60、实际 55，证明帧率同步不再依赖角色主题同步。
- 2026-08-23 最终软件回归：服务端全量 392/392、Testcontainers 空库 Flyway V1..V35、控制台 Vitest 27 文件 81/81、`vue-tsc` 与 production build、文档 7/7 和三组固件栈预算均通过。
- 最终 protocol profile 为 `0x397b0`（235,440 字节）、最小应用分区余量 93%，SHA-256 `78D18EE2A50815270BE3A9AA2DBB50C7B454632FA10AF920027C1F763578E0F0`；LAN HTTP Quad `perf9` 为 `0x1821b0`（1,581,488 字节）、余量 50%，SHA-256 `D5911FE9CAF747799DFE7C1D3D5745611D7B23AA0114D210EAD4973931761699`。

- 当前工作树：服务端全量 391/391、Testcontainers 空库 Flyway V1..V35、控制台 Vitest 27 文件 81/81、类型检查和 production build 均通过；ESP32-S3 protocol profile `0x37880`/93% 余量、LAN HTTP Quad `0x1523e0`/56% 余量均编译通过。
- MEDIA-002D LAN：部署前备份和隔离恢复成功，只替换 server；运行库迁移到 V35，健康接口与首页为 200，PostgreSQL、Redis 和备份容器未替换，启动日志无错误。CoreS3 未刷写。

- MEDIA-002 新视觉候选：ESP32-S3 protocol profile 编译通过，应用大小 `0x37880`、最小应用分区余量 93%；LAN HTTP Quad profile 编译通过，应用大小 `0x1513f0`、最小应用分区余量 56%。独立双眼胶囊几何、奶油色默认球体、稀疏语义色和按帧开始时间调度均已编译，尚未安装。
- `759a91f`：修复首个候选 UI 任务看门狗问题后通过应用 OTA 安装，NVS 与 `motion_disabled` 保留；设备稳定在线并上报动态诊断。用户确认基本功能正常，实测目标 30、实际约 19 FPS，绘制约 16.5 ms、传输约 11.5 ms，降帧原因为 `DRAW_BUDGET`，由旧调度把绘制耗时叠加进帧周期所致。
- INT-013 工作树基于 `13987b0`；服务端全量 377/377、空库 Flyway V1..V32 通过。ESP32-S3 协议 profile 编译通过，固件大小 `0x37880`、最小应用分区余量 93%。
- Node v24.19.0、pnpm 11.19.0：主控制台 Vitest 25 个文件 70/70、类型检查和 production build 通过；旧控制台 5 个文件 23/23、类型检查和构建也通过。
- INT-013 LAN：新备份与隔离恢复通过，只替换 server；运行库迁移到 V32，健康页和首页为 200，SCV1/SCV2 未认证入口为 401，启动日志无错误。
- `bd818f0` 应用 OTA 为 `INSTALLED`，从 `7e7c55f` 更新后跨三个心跳稳定，NVS 设备身份、OTA 能力和 `motion_disabled` 保留；用户确认分段顺序和下一回合正常，触摸停止失败。
- `29e8c36` 修复取消顺序和按下判定；ESP32-S3 协议 profile、语音栈预算和 LAN HTTP Quad 构建通过，应用大小 `0x14f3a0`、分区余量 56%。应用 OTA 为 `INSTALLED`、无失败码，用户确认触摸立即停止、晚到分段不播放且下一回合正常。
- EVT-003 合并提交为 `13987b0`，任务提交 `8378e5f`；其服务端 367/367、空库 Flyway V1..V32 和前端 70/70/类型检查/构建证据保留在合并历史。
- `0e92d58`：服务端 322/322，通过空 PostgreSQL 的 Flyway V1..V27。
- `cf26fd7 -> 7e7c55f`：真实应用 OTA 为 `INSTALLED`，新镜像跨心跳稳定，NVS 保留。
- 用户确认 OTA 后普通唤醒对话、播放中触摸停止和后续再次对话正常。
- `EVT-001`：服务端 341/341，通过空 PostgreSQL 的 Flyway V1..V28；真实 Streamable HTTP MCP 客户端完成 Bearer 鉴权、工具发现和通知入队。
- Node v24.15.0：前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过；路由专项 3/3 通过。
- 删除迭代：服务端 346/346、删除定向 16/16；前端继续为 69/69，并完成类型检查和 production build。
- `ROLE-001`：服务端 348/348，通过空 PostgreSQL 的 Flyway V1..V29；角色/会话定向 13/13。Node v24.19.0、pnpm 11.19.0 下前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过。
- ROLE-001 LAN：部署前备份和隔离恢复通过，只替换 server；运行库迁移至 V29，首页 200，角色/设备 API 未认证均为 401，默认角色和全部陪伴数据角色归属完整。
- `ROLE-002`：服务端 351/351，空 PostgreSQL 成功应用 Flyway V1..V30；角色音色与回退定向 24/24。Node v24.15.0、pnpm 11.19.0 下前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过。
- ROLE-002 LAN：发布前新备份与隔离恢复通过，只替换 server；运行库迁移至 V30，健康接口和首页为 200，角色/设备 API 未认证为 401，角色音色前端资源已发布，启动日志无错误。
- 全量服务端首次运行遇到既有异步 MockMvc 用例的瞬时 `ConcurrentModificationException`；该用例单独重跑及随后完整 341/341 重跑均通过，未修改无关实现。
- 用户授权后已生成并恢复校验新 PostgreSQL 备份，仅替换 LAN server；运行库成功迁移到 V28，健康接口和外部通知静态资源为 200，未认证 REST/MCP 均为 401，启动日志无错误。

## 依赖与阻塞

- `EVT-001`、`ROLE-001`、`ROLE-002`、`EVT-002`、`EVT-003` 和 `INT-013` 均已合入。
- 用户已明确覆盖原分支拆分方案，要求 `MEDIA-002A/B/C` 在当前分支一次性交付；该例外已写入 ADR 0038。
- 用户已恢复日历和天气连接器，但仅允许私用 V1 的只读 iCloud Calendar 与固定地点 Open-Meteo；日历语音闭环已由用户确认，其他连接器继续冻结。
- 用户已确认基础外部通知测试正常；免打扰延期和离线重连两个专项场景尚未执行，不阻塞代码审核或提交整理。
- 用户明确要求本轮不做固件 OTA 回退演练；自定义表情实体素材和公网生产部署仍需要各自单独授权。
- MEDIA-002 已合入，固件成功路径、显示性能和音量回归已通过；固定 60 FPS 仍是调度目标而不是硬件承诺。
- MEDIA-003/004 已合入且无代码阻塞。`esp_emote_gfx` 仍不进入正式显示链；MEDIA-004 V2 实体激活因暂无素材延期，后续只有在提供素材并单独授权时才执行。


## 下一步

开发按用户要求暂停；本轮已获任务分支推送授权。推送后等待用户创建 PR、审核与合并，不继续新增功能。
