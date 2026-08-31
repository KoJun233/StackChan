# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-31
- 当前分支：`codex/work-006-daily-task-progress`
- 实现基准：`221711f`
- 最后验证提交：`221711f`
- 最后验证范围：WORK-006 单元 20/20、PostgreSQL 5/5、非 loopback 服务端 456/456、备份恢复、镜像构建与 LAN/V45 发布通过
- 当前部署：LAN HTTP development mode
- 生产边界：HTTPS-only

## 当前结论

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
| [服务端](server.md) | READY_FOR_REVIEW | WORK-006 今日待办进度已完成并运行于 LAN/V45 | 完成实体语音查询验收 |
| [前端](frontend.md) | STABLE | 个人待办 CRUD 与默认角色校验修复已发布且回归点人工通过 | 完成其余页面和机器人语音验收 |
| [固件](firmware.md) | STABLE | `424cb49` 已安装，顶部长按启停实机通过且动作保持禁用 | 当前硬件未上报环境光能力，按不支持路径安全降级 |
| [部署](deployment.md) | STABLE | LAN 运行 WORK-006/V45，基础容器和 CoreS3 未变化 | 保持运行并完成今日进度语音验收 |

## 当前能力地图

- 对话：流式文字聊天、本地唤醒语音、完整回复、连续对话、触摸取消、隐私安全诊断和有序分段播放。
- 陪伴：角色容器、可选角色音色、确认记忆、建议过滤、相关检索、周期提醒、免打扰和有界主动关心；人设与陪伴数据按角色隔离。
- Agent：受控 ReactAgent、Skill ZIP、只读 Tool、页面管理的 Streamable HTTP MCP Client 和语音动作确认。
- 待办：设备与角色隔离的本地 CRUD、可靠截止提醒、最小只读 Agent Tool，以及确认式语音新增与完成。
- 设备：配对/JWT/WebSocket、唤醒模型 OTA、动态球形表情、兼容八状态 PNG 包和应用 A/B OTA。
- 数据与运维：个人数据搜索/导出/删除、7 日/4 周备份、隔离恢复和健康中心。
- 外部通知：固定设备集成、一次性令牌、幂等 REST/MCP、可靠单飞、互动回执，以及可选的同集成确定性原文摘要。
- 日历：V39 提供 iCloud CalDAV 只读发现与 `REPORT` 查询、加密 App 专用密码、Apple Account 已验证邮箱、显式允许列表、私人事件脱敏、二十四小时缓存、每小时同步和设备绑定的未来七天 Agent Tool。区域回退仅在全球认证失败时尝试中国大陆入口，只允许 Apple 全球/中国大陆根入口和 `p数字-caldav` HTTPS 分片。
- 天气：V40 按设备固定位置保存 Open-Meteo 当前及今明两天的最小必要字段；缓存最长一小时，支持无保存连接测试、手动同步、启动补同步、到期前续期、确定性中文摘要和设备绑定只读 Agent Tool，不允许模型指定位置或猜测失效数据。

完整合并记录见[里程碑索引](../milestones.md)，长期架构约束见[ADR 索引](../decisions/README.md)。

## 版本与运行态

- Git：`master` 合并提交 `221711f`；WORK-006 从该提交创建独立分支，尚未外部推送。
- LAN server：WORK-006/V45 今日待办进度查询，当前宿主机地址 `http://192.168.1.4:8080/`；运行镜像和回滚镜像信息见[部署状态](deployment.md)。
- CoreS3：当前运行 `424cb49` LAN HTTP Quad 固件；Wi-Fi/NVS、语音链路、8192 字节主栈与 `motion_disabled` 已保留，中位校准已连续两次通过，WORK-001 顶部长按启停已实机通过。
- 实机固件提交只作为运行候选，不能替代 `master` 作为新任务分支基线。

## 最近验证

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

保持当前 LAN server/V41 和 CoreS3 `5e14d73` 的 `motion_disabled` 不变；把 SCS 写 ACK 兼容修复压回单一任务提交并重建提交绑定 LAN HTTP Quad 候选。Git 推送仍需用户明确授权；新的固件候选也需再次获批后才可保留 NVS 安装，本轮只复测无动作校准，动作继续按 runbook 分阶段授权。
