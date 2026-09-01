# 部署工作流

- 状态：STABLE
- 最后更新：2026-09-03
- 当前分支：`codex/console-information-architecture`（聊天滚动复修已部署，实机固件保持 `424cb49`）
- 基准提交：`227b369`
- 最后验证提交：`d7dc00d`
- 最后验证范围：聊天滚动复修的新备份、隔离恢复、server-only 发布、V46、健康、LAN 与最终静态资源通过
- 当前模式：LAN HTTP development

## 当前目标

保持已发布的 WORK-006/V46 业务能力稳定运行，并由用户在 LAN 环境验收真实二级菜单、无黑块输入区及窄屏消息滚动；天气、只读日程与角色删除冷静期继续保持。CoreS3 保持 `424cb49 / motion_disabled`。

## 已发布的聊天滚动复修

- 2026-09-03 发布前生成新备份并完成隔离恢复；旧镜像保留为 `pre-chat-scroll-v46`。
- 只替换 `stackchan-foundation-server-1`；当前容器为 `f12a4040770c`，镜像为 `sha256:0042f143a832aa8d5315dd902876db0dcb8c1f125e68f76ab3c4a6c1224cf165`，构建版本为 `console-feedback-v46-chat-scroll-final`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；运行库保持 V46。
- 健康、本机与 LAN 首页为 200；聊天 CSS/JS 为 200，运行 CSS 确认固定工作区高度、内部纵向滚动、滚轮边界与触摸滚动，启动日志无应用错误。

## 已发布的二级菜单与聊天复修

- 2026-09-03 发布前生成新备份并完成隔离恢复；旧镜像保留为 `pre-menu-chat-fix-v46`。
- 只替换 `stackchan-foundation-server-1`；当前容器为 `044b01b409d3`，镜像为 `sha256:6c9d7ff0a125abf8103a440cac94b64b902e65a19b0bd5b7a0cf9e437342b83d`，构建版本为 `console-feedback-v46-menu-chat-final`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；运行库保持 V46。
- 健康、本机与 LAN 首页为 200；新菜单、聊天 JS/CSS 均为 200，运行资源确认“今日陪伴/事务管理”、主动关心、Enter 发送、停止生成和 OKLCH 主题映射。

## 已发布的六项后台反馈

- 新备份和最新备份内置隔离恢复通过；旧 V45 镜像保留为 `pre-console-feedback-v45`。
- 只替换 `stackchan-foundation-server-1`；当前容器为 `afefeaf5884f`，镜像为 `sha256:a4066d42cb3eb5e8f5655c0648cac8009e559c577ac9d6767f7edcc919712328`，构建版本为 `console-feedback-v46-final`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；数据库由 V45 迁移至 V46。
- 健康、本机与 LAN 首页为 200；未认证角色和只读日程接口为 401，最终聊天、工作日和角色静态资源为 200。

## 已发布的后台信息架构与 UI 重整

- 11:53 UTC 生成新备份并完成内置隔离恢复，11:53 UTC 再次独立验证最新备份成功；旧 WORK-006/V45 镜像保留为 `pre-console-ia-3697be8`。
- 只替换 `stackchan-foundation-server-1`；当前容器为 `c580d0855c3c`，镜像为 `sha256:a3b015c36bc17db419b87993081badf3b744527b50627ac6f527f86c0649e85d`，构建版本为 `console-ia-3697be8`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；数据库继续为 V45。
- 本机与 LAN 首页为 200，未认证设备接口为 401；运行资源包含新的今日概览与隔离 TDesign Chat chunk。运行库保留 1 条未完成待办和 2026-08-30 至 2026-09-12 观察窗口；设备在线并保持 `424cb49 / motion_disabled / DISABLED`。

## 已发布的 WORK-006

- 12:30 UTC 生成新备份，12:31 UTC 完成独立恢复验证；WORK-005 镜像保留为 `pre-work006-v45`。
- 只替换 `stackchan-foundation-server-1`；当前容器为 `9617c52a48db`，镜像为 `sha256:6b120ef75ae32845b679d2ce976cf91260928a0dde3304b0ca63db61b19c79cb`，构建版本为 `work006-v45-task-progress`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；数据库继续为 V45。
- 健康与 LAN 首页为 200，运行库保留 1 条未完成待办和 2026-08-30 至 2026-09-12 观察窗口；设备在线并保持 `424cb49 / motion_disabled / DISABLED`。

## 已发布的 WORK-005

- 11:14 UTC 生成新备份并完成内置恢复校验，11:15 UTC 再次独立验证最新备份成功；旧 V45 镜像保留为 `pre-work005-v45`。
- 只替换 `stackchan-foundation-server-1`；当前容器为 `e384ffebb23a`，镜像为 `sha256:f9e5db38787621ea85d26d302fc92a0d6b5aee6f4051c85b43c1d460d9675112`，构建版本为 `work005-v45-task-brief`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 未替换；数据库仍为 V45。
- 健康与 LAN 首页为 200，运行库保留 1 条个人待办和 2026-08-30 至 2026-09-12 观察窗口；设备在线并保持 `424cb49 / motion_disabled / DISABLED`。

## 已发布的 WORK-004

- 默认角色保留 ID 校验修复只替换 server/内置管理页面；当前容器为 `12ecb4a04de5`，镜像为 `sha256:4c8e3d5348aa2a5ef1381bb6538deff3994b4bb6088ace941b7ff05d44c0792c`，构建版本为 `work004-v45-role-fix`。
- 13:33 UTC 生成新备份并完成隔离恢复；修复前 V45 镜像保留为 `pre-role-id-fix-a13a30c`。本机/LAN 首页和健康为 200，未认证待办接口为 401，运行资源包含共享角色 ID 校验。
- 发布范围只有 server 镜像和内置管理页面；数据库由 V44 前进到 V45。
- 发布前在既有备份卷生成新备份并完成一次性 PostgreSQL 隔离恢复；V44 回滚镜像保留为 `pre-work004-v44`。
- 最终 server 容器为 `e8f8d03035c8`，镜像为 `sha256:a29d2895a6b9dcdaa1afbc785de6b935e59c335de288c4fe8bc86a9f24da7d54`，构建版本为 `work004-v45-final`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 均未替换；健康、本机/LAN 首页、V45 和鉴权边界通过。
- 发布后 `personal_tasks=0`；观察窗口仍为 2026-08-30 至 2026-09-12，完成通知标记为空；设备保持 `424cb49 / motion_disabled / DISABLED`。

## 已发布的 WORK-003

- 发布范围只有 server；数据库由 V43 前进到 V44，内置管理页面代码没有变化。
- 发布前在独立备份卷生成新备份并完成一次性 PostgreSQL 隔离恢复；V43 回滚镜像保留为 `pre-work003-v43`。
- 最终 server 容器为 `04816c320b97`，镜像为 `sha256:378005a8996e1d1e5d63a3324336981f81da9f924e2e86145fa1fd2cf25a901f`，构建版本为 `work003-v44-final`。
- PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和 CoreS3 均未替换；健康、本机/LAN 首页、V44、鉴权、观察窗口和安全状态通过。

## 已发布的 WORK-002

- 发布范围只有 server 镜像和内置管理页面，数据库已从 V42 前进到 V43。
- 发布前在既有备份容器内生成新备份并完成最新备份校验；旧 WORK-001 镜像保留为 `pre-work002-c62ccd0`。
- 最终 server 容器为 `e2dcfa2fbe86`，镜像为 `sha256:db8e8aca683db95ee1ace9273ae80aac8deb9368f2af15ecfae612e615dd1c72`，并保留标签 `work002-v43-final`。
- 健康、本机/LAN 首页、Flyway V43、观察 API 鉴权和设备持续在线均通过；PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 和固件均未替换。

## 已完成

- Docker Compose 运行 PostgreSQL、Redis、server 和独立备份容器；数据卷和备份卷分离。
- LAN development 绑定局域网地址；production 配置只接受可信代理后的 HTTPS/WSS。
- PostgreSQL 日/周轮转、原子备份、清单、只读状态和一次性临时库恢复验证。
- ROLE-001 server/V29 和角色管理前端已发布；CoreS3 保持原固件且 OTA 能力启用。
- Docker Desktop 数据位于 E 盘，现有卷、镜像和容器已保留。
- 完整合并历史见[里程碑索引](../milestones.md)。

## 正在进行

LAN server 已运行聊天滚动复修版本，当前宿主机局域网地址为 `http://192.168.1.4:8080/`，容器为 `f12a4040770c`，镜像摘要为 `sha256:0042f143a832aa8d5315dd902876db0dcb8c1f125e68f76ab3c4a6c1224cf165`。运行态继续保留 WORK-006/V46，并提供真实侧栏分组、Fantastic-admin 聊天输入区、独立可滚动消息区、天气图标、未来七天只读日程和受七天冷静期保护的角色永久删除。

CoreS3 当前上报 LAN HTTP Quad 固件 `424cb49`，保留 NVS、Wi-Fi、设备身份和 `motion_disabled`；启动、语音、无动作中位校准及 WORK-001 顶部长按启动/停止正常。服务地址仍指向当前宿主机。MEDIA-004 V2 实体激活继续等待 EAF 素材。

## 下一步操作

由用户强制刷新 LAN 页面，在当前窄屏宽度下复核聊天消息区的鼠标滚轮和触摸滚动；另完成一项当前角色待办后询问机器人“我今天完成了什么”，确认回答包含今日完成项和剩余待办且不包含备注或精确完成时间。Git 外部推送仍需用户授权。

## 阻塞项

- 当前运行态无部署阻塞。用户于 2026-08-25 明确长期授权服务端及其内置管理页面可直接部署；Git 外部推送、固件刷写/OTA、凭据轮换、部署模式切换以及卷或端口变更仍需分别显式授权。

## 关键文件

- `compose.yaml`
- `compose.lan.yaml`
- `compose.production.yaml`
- `server/Dockerfile`
- `ops/postgres-backup/`
- `scripts/verify-lan-compose.ps1`

## 验证命令与最近结果

- 2026-09-03 聊天滚动复修发布：新备份与隔离恢复成功，旧镜像保留为 `pre-chat-scroll-v46`，只替换 server。当前容器 `f12a4040770c`、镜像 `sha256:0042f143a832aa8d5315dd902876db0dcb8c1f125e68f76ab3c4a6c1224cf165`、版本 `console-feedback-v46-chat-scroll-final`；V46、本机/LAN 首页和健康 200，聊天 CSS/JS 200，运行 CSS 包含最终高度与滚动边界。PostgreSQL、Redis、备份容器和 CoreS3 未替换。

- 2026-09-03 二级菜单与聊天复修发布：新备份与隔离恢复成功，旧镜像保留为 `pre-menu-chat-fix-v46`，只替换 server。当前容器 `044b01b409d3`、镜像 `sha256:6c9d7ff0a125abf8103a440cac94b64b902e65a19b0bd5b7a0cf9e437342b83d`、版本 `console-feedback-v46-menu-chat-final`；V46、本机/LAN 首页和健康 200，新菜单与聊天资源 200，运行资源包含最终分组、发送/停止动作和 OKLCH 主题映射。PostgreSQL、Redis、备份容器和 CoreS3 未替换。

- 2026-09-01 六项后台反馈发布：新备份与最新备份内置隔离恢复成功，旧镜像保留为 `pre-console-feedback-v45`，只替换 server。当前容器 `afefeaf5884f`、镜像 `sha256:a4066d42cb3eb5e8f5655c0648cac8009e559c577ac9d6767f7edcc919712328`、版本 `console-feedback-v46-final`；V46、本机/LAN 首页 200、健康 200、角色与日程未认证接口 401、最终聊天/工作日/角色静态资源 200。PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 及 CoreS3 未替换。离线组装镜像第一次继承临时启动命令导致健康检查无响应，未触及数据库；修正入口后立即重建，并在最终资源格式化后再次精确替换为上述镜像。

- 2026-09-01 后台信息架构与 UI 重整发布：11:53 UTC 新备份及内置隔离恢复成功，随后最新备份独立恢复成功；旧 WORK-006/V45 镜像保留为 `pre-console-ia-3697be8`，只替换 server。当前容器 `c580d0855c3c`、镜像 `sha256:a3b015c36bc17db419b87993081badf3b744527b50627ac6f527f86c0649e85d`、构建版本 `console-ia-3697be8`；V45、本机/LAN 首页 200、未认证设备接口 401、新 dashboard/chat 静态资源、1 条未完成待办、观察窗口和设备在线安全状态通过。PostgreSQL、Redis、备份容器及 CoreS3 未替换。

- 2026-08-31 WORK-006/V45 发布：12:30 UTC 新备份、12:31 UTC 独立恢复验证成功；旧镜像保留为 `pre-work006-v45`，只替换 server。当前容器 `9617c52a48db`、镜像 `sha256:6b120ef75ae32845b679d2ce976cf91260928a0dde3304b0ca63db61b19c79cb`、构建版本 `work006-v45-task-progress`；V45、健康、LAN 首页、1 条未完成待办、观察窗口和设备在线安全状态通过。

- 2026-08-31 WORK-005/V45 发布：11:14 UTC 新备份及内置恢复成功，11:15 UTC 最新备份独立恢复成功；旧镜像保留为 `pre-work005-v45`，只替换 server。当前容器 `e384ffebb23a`、镜像 `sha256:f9e5db38787621ea85d26d302fc92a0d6b5aee6f4051c85b43c1d460d9675112`、构建版本 `work005-v45-task-brief`；V45、健康、LAN 首页、1 条待办、观察窗口和设备在线安全状态通过。

- 2026-08-30 WORK-004 默认角色校验修复发布：13:33 UTC 生成新备份并完成隔离恢复，修复前 V45 镜像保留为 `pre-role-id-fix-a13a30c`，只替换 server。最终容器 `12ecb4a04de5`、镜像 `sha256:4c8e3d5348aa2a5ef1381bb6538deff3994b4bb6088ace941b7ff05d44c0792c`、构建版本 `work004-v45-role-fix`；V45、本机/LAN 首页、健康、401 鉴权和新静态资源通过。待办表仍为空，观察窗口保持 2026-08-30 至 2026-09-12，设备保持 `424cb49 / motion_disabled / DISABLED`。
- 2026-08-30 用户确认刷新页面后默认角色新增正常，发布修复人工验收通过；无需再次替换服务或操作固件。

- 2026-08-30 WORK-004/V45 发布：21:06 生成新备份并完成隔离恢复，旧 V44 镜像保留为 `pre-work004-v44`，只替换 server。最终容器 `e8f8d03035c8`、镜像 `sha256:a29d2895a6b9dcdaa1afbc785de6b935e59c335de288c4fe8bc86a9f24da7d54`；运行库为 V45，本机/LAN 首页和健康为 200，未认证待办接口为 401。待办表为空，原观察窗口和完成通知标记未变化，设备保持 `424cb49 / motion_disabled / DISABLED`。

- 2026-08-30 WORK-003/V44 发布：新备份和隔离恢复验证成功，旧 V43 镜像保留为 `pre-work003-v43`，只替换 server。最终容器 `04816c320b97`、镜像 `sha256:378005a8996e1d1e5d63a3324336981f81da9f924e2e86145fa1fd2cf25a901f`；运行库为 V44，本机/LAN 首页和健康正常，未认证观察接口为 401。当前观察窗口仍为 2026-08-30 至 2026-09-12，首次调度后入队标记为空且完成提醒数为零；设备保持 `424cb49 / motion_disabled / DISABLED`。

- 2026-08-30 WORK-002/V43 发布：发布前及第十四天边界修正后均生成并校验最新备份，旧 WORK-001 镜像保留为 `pre-work002-c62ccd0`，两次均只替换 `stackchan-foundation-server-1`。最终容器为 `e2dcfa2fbe86`，镜像为 `sha256:db8e8aca683db95ee1ace9273ae80aac8deb9368f2af15ecfae612e615dd1c72`，构建版本为 `work002-v43-final`；PostgreSQL `6d8feaa18623`、Redis `58e31a403637` 和备份容器 `c94b190f0428` 未变化。运行库由 V42 迁移到 V43，本机与 LAN 健康/首页为 200，未认证观察接口为 401，运行资源包含最终观察说明。CoreS3 在线上报 `424cb49 / motion_disabled / DISABLED`，未连接串口、未刷写固件、未执行身体动作。

- 2026-08-30 WORK-001 实机收口：用户自行安装 `424cb49`；数据库确认设备在线、固件版本匹配并持续为 `motion_disabled / DISABLED`。顶部长按先切换到 `ACTIVE_PRESENT`，再次长按切换为 `OFF`，跨调度周期保持停止。COM3 仅做授权范围内的只读监听，未下发命令或动作。

- 2026-08-29 WORK-001/V42 发布：现有备份容器内完成新 PostgreSQL 备份和最新备份隔离恢复；旧镜像保留为 `pre-work001-v42-6c750ff`，只替换 `stackchan-foundation-server-1`。新容器为 `51fb6601e52a`，镜像为 `sha256:0e2bc6760001bb4d0304a738e019e5d8d42fc65cf4d6cef3a0bff496df0d4920`；PostgreSQL `6d8feaa18623`、Redis `58e31a403637` 和备份容器 `c94b190f0428` 未变化。运行库由 V41 迁移到 V42，健康为 `ok`，本机与 `192.168.1.4:8080` 首页为 200，未认证工作接口为 401，运行资源包含新工作陪伴控制且启动后无应用级错误。CoreS3 已恢复心跳，上报 `6c750ff / DISABLED`；未连接串口或刷写固件。
- 2026-08-29 BODY-001 写 ACK 诊断：用户安装 `5e14d73` 后，经逐次授权和刷新管理页，在 COM3 实时捕获一次到达设备的无动作校准；三次均为 `stage=yaw_torque_off`，时间约 840467/840597/840727 ms，130 ms 间隔证明真实时钟等待已生效。M5Stack 官方上层不依赖 `EnableTorque()` 返回值而继续读取反馈；修复改为写指令只确认 UART 发送，再读回 yaw/pitch 扭矩寄存器，确认关闭后才读取位置。双 profile 工作树构建及三组任务栈回归/静态预算通过；未启用动作。
- 2026-08-29 BODY-001 回包等待诊断：用户安装 `839e146` 后，经逐次授权在机器人无播报时实时捕获一次无动作校准；三次均为 `stage=yaw_torque_off`，时间间隔仅约 70 ms。确认 `pdMS_TO_TICKS(5)` 在 100 Hz 下为 0，固定扫描在回包前结束。修复改为 50 ms 单调时钟截止与最少 1 tick 阻塞，双 profile 工作树构建通过；未启用动作。
- 2026-08-29 BODY-001 校准反馈诊断：用户已通过网页 OTA 安装 `2108f78`。数据库确认设备在线、`body_motion_supported=true`、`servo_feedback_supported=false`、`body_calibrated=false`、`FEEDBACK_FAULT`。经用户明确授权只读连接 COM3；端口连接触发设备重启，随后正常回到 `2108f78`、网络与语音恢复、`motion_disabled` 保持。用户按提示只执行一次无动作校准，失败计数从 0 增至 1；未启用身体动作。加固代码已完成双 profile 工作树构建，但未安装。
- 2026-08-29 BODY-001 OTA 回退诊断：经用户逐次批准，对 `d1abe9d` 进行第二次 OTA 并只读监听 COM3。下载、SHA-256 和镜像装载成功；新镜像约 1.8 秒时在 BMI270 初始化错误路径报告 `A stack overflow in task main`，随即由 bootloader 回到 factory `fe95767`。旧固件重新连接 LAN server，NVS、Wi-Fi、身份、语音和 `motion_disabled` 保留；未执行校准、运动或舵机供电测试。
- 2026-08-29 BODY-001 发布：新 PostgreSQL 备份和最新备份隔离恢复验证成功，旧 server 镜像保留为 `pre-body001-3596c80`；只替换 `stackchan-foundation-server-1`，新容器为 `5f10d0a2037b`，镜像为 `sha256:a1f3c3cdad658579695d9ed54190b33f317fb3d3ffe09843897bafbda2b630cd`。PostgreSQL `6d8feaa18623`、Redis `58e31a403637` 和备份容器 `c94b190f0428` 未变化；运行库由 V40 迁移到 V41，健康为 `ok`，本机与 `192.168.1.4:8080` 首页为 200，未认证设备接口为 401，静态资源包含 K151 管理卡。CoreS3 未连接、未刷写，旧固件 `fe95767` 已恢复心跳并保持 `motion_disabled`。

- 2026-08-28 天气续期修复发布：发布前新 PostgreSQL 备份及最新备份校验成功，旧镜像保留为 `pre-weather-refresh-fix-fe95767`，只替换 `stackchan-foundation-server-1`。新容器为 `e61d46f8494a`，镜像为 `sha256:e23e5f0a5cf2a1c1b65e0de67f77cb7bd7c15e69bbc3c862f71c42836f8aa20d`；健康状态为 `ok`，运行库保持 V40。启动十秒后的真实 Open-Meteo 同步为 `READY`，缓存有效至 2026-08-28 19:41（Asia/Shanghai），数据库包含 8 月 28/29 两条预报。首次 Compose 调用误用 `stackchan` 项目名，只创建未启动容器并因 8080 占用退出；原服务未中断，误建的空容器、网络和空卷已精确删除，随后以正确项目名完成切换。
- 2026-08-27 USB 服务地址快捷更新页面发布：部署前新 PostgreSQL 备份与隔离恢复成功，旧镜像保留为 `pre-usb-server-update-902bb95`，只替换 `stackchan-foundation-server-1`。新容器为 `944ed2ceedc3`，镜像为 `sha256:9ff0dce7ae5f17dc048fe341595b9f6dfcc688d576a476fdc3539c56199b3b9f`；PostgreSQL `6d8feaa18623`、Redis `58e31a403637` 和备份容器 `c94b190f0428` 未变化。健康状态为 `ok`，40 条迁移验证成功且运行库保持 V40，本机与 `192.168.1.4:8080` 首页为 200，未认证设备接口为 401，运行配网页资源包含“仅更新服务地址”。未连接或操作 CoreS3。
- 2026-08-26 用户确认经纬度输入修正、真实固定位置同步和机器人天气问答均正常，WEATHER-001 人工验收通过。
- 2026-08-26 修正经纬度输入被 `type=number` 转成数字后与字符串校验模型冲突导致的英文 `Invalid input`；改用保留字符串的十进制文本输入并提供示例。控制台 86/86、类型检查和 production build 通过；发布前新备份和隔离恢复成功，旧镜像保留为 `pre-v40-coordinate-fe5cece`，只替换 server。新容器为 `22a64c2dfe07`，镜像为 `sha256:9f3f06e5cf3528d467cebc74e534b7d5750fb7f505175244da8b6b9a78330b0a`；PostgreSQL、Redis 和备份容器 ID 未变化，V40 无待迁移项，健康和当前 LAN 首页为 200，运行资源包含经纬度示例，CoreS3 未操作。
- 2026-08-26 WEATHER-001/V40 发布前新 PostgreSQL 备份和隔离恢复验证成功；旧镜像保留为 `pre-v40-weather-ecad118`，只替换 `stackchan-foundation-server-1`。新容器为 `80fff6f9ff74`；PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 及数据卷均未变化。
- 运行源快照为 `ecad118`，镜像为 `sha256:db1f48cb1730351ce501d78e02b11635074c87a4fc6f31301233b1a6ead74c74`；Flyway 从 V39 成功迁移到 V40。本机与当前 LAN `192.168.1.4:8080` 首页为 200，健康状态为 `ok`，未认证天气接口为 401，运行静态资源包含 `Open-Meteo`，启动日志无应用级 `ERROR`；CoreS3 未连接、未刷写。
- 2026-08-25 CONN-001 Agent 日历闭环发布前新 PostgreSQL 备份和隔离恢复验证成功；旧镜像保留为 `pre-v39-calendar-agent-a2a8e29`，只替换 `stackchan-foundation-server-1`。新容器为 `7a6b6821a206`；PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 及数据卷均未变化。
- 运行源快照为 `a2a8e29`，镜像为 `sha256:fafc4e24a7d0191f20ec3300a2100517ed43c5c82d34088e0858c0edaaa688ed`；运行库保持 V39 且一条缓存事件仍有效。本机和 `192.168.1.3:8080` 首页为 200，健康状态为 `ok`，未认证日历接口为 401，启动后无应用级 `ERROR`；CoreS3 未刷写。
- 2026-08-25 CONN-001 Apple 编号分片修正发布前新 PostgreSQL 备份和隔离恢复验证成功；旧镜像保留为 `pre-v39-caldav-shard-1af1c03`，只替换 `stackchan-foundation-server-1`。PostgreSQL `6d8feaa18623`、Redis `58e31a403637`、备份容器 `c94b190f0428` 及数据卷均未变化。
- 运行源快照为 `1af1c03`，镜像为 `sha256:c1ee04a036376a1a36e3416ec54aa62a72da213a52d29f854a5c6e03f2612b68`；运行库保持 V39。本机和 `192.168.1.3:8080` 首页为 200，健康接口状态为 `ok`，未认证日历接口为 401，静态资源包含“Apple 账号邮箱”。CoreS3 未刷写。
- 2026-08-25 CONN-001 中国大陆区域回退发布前新 PostgreSQL 备份和隔离恢复验证成功；旧镜像保留为 `pre-v39-cn-caldav-3a547e6`，只替换 `stackchan-foundation-server-1`，PostgreSQL、Redis、备份容器 ID 和数据卷均未变化。
- 运行库保持 V39，39 条迁移验证成功；本机首页、`192.168.1.3:8080` 首页和健康接口均为 200，未认证日历接口为 401。运行镜像为 `sha256:24aee105c5c94d06fd7367fea56670850e5535d7bb1abc7f6d6186c3cadda3a2`，CoreS3 未刷写。
- 2026-08-25 CONN-001 手机号兼容发布前新 PostgreSQL 备份和隔离恢复验证成功；旧 V39 镜像保留为 `pre-v39-phone-842ac4b`，只替换 `stackchan-foundation-server-1`，PostgreSQL、Redis、备份容器 ID 和数据卷均未变化。
- 运行库保持 V39；本机首页、`192.168.1.3:8080` 首页和健康接口均为 200，未认证日历接口为 401，运行静态资源包含“Apple 账号邮箱或手机号”。运行镜像为 `sha256:7a9b2768820206878fe4f3e240736e9d69cf13e39ceb96578898f1b546ccf7a9`，未录入真实 Apple 凭据，CoreS3 未刷写。
- 2026-08-25 CONN-001/V39 发布前新 PostgreSQL 备份和隔离恢复验证成功；旧 V38 镜像保留为 `pre-v39-9f3b427`，只替换 `stackchan-foundation-server-1`，PostgreSQL、Redis、备份容器 ID 和数据卷均未变化。
- 运行库由 V38 迁移至 V39，共 39 条迁移验证成功；本机首页、`192.168.1.3:8080` 首页和健康接口均为 200，未认证日历接口为 401。运行镜像为 `sha256:47d77048d39d7cfc3f4a4a31de9c93d68ebd4dd997decbdb6abdc75554ef23d7`，未录入真实 Apple 凭据，CoreS3 未刷写。
- 2026-08-25 WORK-001 阶段 A/V38 发布前新 PostgreSQL 备份和隔离恢复验证成功；旧 server 镜像保留为 `pre-work001a-v38-e9c3edd`，只替换 `stackchan-foundation-server-1`，PostgreSQL、Redis、备份容器 ID 和数据卷均未变化。
- 运行库由 V37 迁移至 V38；本机首页、LAN 首页和健康接口均为 200，未认证工作日运行态接口为 401。运行镜像为 `sha256:17b863a5dc4fad4ce5d8df7d1869a0c94bbcaf2de3d39f85b6a4f5e462521258`，CoreS3 未刷写。
- 2026-08-24 WORK-001 阶段 A 发布前新 PostgreSQL 备份和最新备份隔离恢复验证成功；旧 server 镜像保留为 `pre-work001a-9a9fee0`，只替换 `stackchan-foundation-server-1`，PostgreSQL、Redis、备份容器 ID 和数据卷均未变化。
- 运行库由 V36 迁移至 V37；本机首页、LAN 首页和健康接口均为 200，未认证工作日设置接口为 401，运行静态资源包含“工作日桌面陪伴”，启动日志无 `ERROR`/`Exception`。本次未连接、测试或刷写 CoreS3。
- 2026-08-23 部署前工作树服务端 392/392、空库 Flyway V1..V35、前端 81/81/类型检查/生产构建、双固件 profile、三组任务栈预算和文档检查通过；自动化验证阶段未替换运行容器。
- 2026-08-23 MEDIA-004 发布前新 PostgreSQL 备份及最新备份隔离恢复验证成功；只替换 `stackchan-foundation-server-1`，运行库由 V35 迁移到 V36，PostgreSQL、Redis、备份容器 ID 和数据卷均未变化。
- 新 server 健康接口、本机首页和 `192.168.1.3:8080` 首页为 200；未认证表情包与设备接口均为 401。运行镜像为 `sha256:50d3f5bc86ce34441cbf16332a58ef99ea21afe8ff18028036539b9cf7ae0dbc`，旧镜像保留为 `pre-media004-0b70f33`，本次未 OTA CoreS3。
- 2026-08-23 经用户授权，部署前新 PostgreSQL 备份和最新备份隔离恢复验证成功；只重建 `stackchan-foundation-server-1`，PostgreSQL、Redis 和备份容器 ID 均未变化。
- 新 server 健康接口、本机首页和 `192.168.1.3:8080` 首页为 200；V35 无待迁移项，启动日志无 `ERROR`/`Exception`，运行静态资源包含新诊断标签。CoreS3 在容器重建后产生新心跳并恢复 `ADAPTIVE 45–60`、目标 60、实际 55。
- 2026-08-22 经用户授权重启 Docker Desktop 后，既有 PostgreSQL、Redis、server 和备份容器全部恢复；运行 server 镜像仍为预期摘要 `sha256:b819e63378db6250bdbd8fd66939f15960d6c72097fce28b3558114afcf4ae4c`，本轮固件迁移未修改服务端或前端，因此未无意义替换容器。
- 恢复后 `/api/v1/health` 和首页均为 200，Flyway 确认运行库保持 V35 且无待迁移项，启动日志无应用错误；服务端全量 391/391 和空库 V1..V35 通过。
- 2026-08-22 部署前新备份和最新备份隔离恢复均成功；只重建 `stackchan-foundation-server-1`，PostgreSQL、Redis 与备份容器 ID 未变化。
- 运行库成功迁移到 V35；`/api/v1/health` 和首页均为 200，运行镜像与预期新镜像摘要一致，启动日志无 `ERROR`/`Exception`。
- 首次 Compose 调用因遗漏既有项目名，只创建了一个未启动容器并在 8080 端口检查处退出；原服务未中断，所创建的空容器、空卷与空网络随后被精确清理，再以 `stackchan-foundation` 项目名完成切换。
- MEDIA-002 server/V34 与前端已部署，CoreS3 已运行 `41b8827`；用户确认平滑边缘和真机预览正常，并反馈固定 60 FPS 与语音并发回归。
- `d65811d` 首次应用 OTA 因 UI 任务看门狗自动回退；修正候选 `759a91f` 随后安装为 `INSTALLED`，NVS、设备身份、网络、WakeNet、OTA 和 `motion_disabled` 均保留，用户确认基础功能正常。
- 2026-08-19 INT-013 发布前新备份及最新备份隔离恢复成功；正式数据库未被覆盖。
- 旧 server 镜像保留为 `pre-int013-a04ae0b`，新镜像保留为 `int013-a04ae0b`；只重建 `stackchan-foundation-server-1`，PostgreSQL、Redis、备份容器和卷保持不变。
- 运行库成功从 V30 迁移到 V32，共 32 条迁移成功；`/api/v1/health` 和首页为 200，SCV1/SCV2 未认证语音入口均为 401，启动日志无错误。
- 本次未连接或刷写 CoreS3；现有固件继续通过 SCV1 与新 server 兼容。
- 用户随后以应用 OTA 将 CoreS3 从 `7e7c55f` 更新到 `bd818f0`；任务为 `INSTALLED`，设备连续心跳、NVS 设备身份、OTA 能力和 `motion_disabled` 保留。
- SCV2 分段顺序和后续回合在 `bd818f0` 已正常；`29e8c36` 修复镜像随后从 `bd818f0` 应用 OTA 安装，任务为 `INSTALLED`、无失败码，用户确认播放中触摸停止和后续回合正常。
- 2026-08-13 ROLE-002 发布前新备份及最新备份隔离恢复验证成功；未覆盖正式数据库。
- `stackchan-foundation-server-1` 已替换为 ROLE-002 `b6cad0b` server/V30；旧 ROLE-001 镜像保留为 `pre-role002-b6cad0b`，新镜像保留为 `role002-b6cad0b`。
- 运行库成功从 V29 迁移到 V30；`/api/v1/health` 和首页为 200，未认证角色/设备 API 为 401，前端资源包含角色音色配置，启动日志无错误。
- PostgreSQL、Redis、备份容器、原数据卷和端口保持不变；本次未修改或刷写 CoreS3。
- 2026-08-13 部署前新备份及最新备份隔离恢复验证成功；未覆盖正式数据库。
- `stackchan-foundation-server-1` 已替换为 ROLE-001 镜像，构建版本 `9e526f8`；旧 EVT-001 镜像保留为 `pre-role001-e1a0a12`，新镜像保留为 `role001-9e526f8`。
- 运行库成功从 V28 迁移到 V29；默认角色恰好一条，会话、记忆、提醒和通知集成均无空角色归属，设备活动角色映射已生成。
- 首页返回 200，未认证 `/api/v1/roles` 与 `/api/v1/devices` 均返回 401，启动日志无应用错误。
- 运行 server 健康与首页为 200，Flyway V28；未认证集成删除、队列删除、外部 REST/MCP 均为 401，启动日志无 `ERROR`/`Exception`。
- 用户确认基础外部通知测试正常；菜单归属及集成/队列删除已随 `50d6269` 镜像发布，等待管理员页面复核。
- 2026-08-11T13:54:04Z 新备份已完成 SHA-256 校验并恢复到一次性 PostgreSQL，关键数据计数一致，临时资源已清理。
- 当前 V27 镜像保留 `pre-evt001-f569ff9` 回退标签；本次未修改或刷写 CoreS3。
- 删除迭代发布前镜像额外保留为 `pre-evt001-delete-f8e9c1d`，新镜像保留为 `evt001-50d6269`。
- LAN 和 production Compose 静态边界在 OPS-002 基线通过。
- `git diff --check`、`pnpm docs:check` 和文档测试通过；本状态整合未访问 `.env` 或运行凭据。

## 相关设计、计划和决策

- [当前任务清单](../todo.md)
- [工作日桌面陪伴 V1 开发设计](../workday-companion-v1.md)
- [0041：私用优先的确定性工作日陪伴闭环](../decisions/0041-private-first-deterministic-workday-companion.md)
- [开发环境与命令](../development.md)
- [0004：LAN HTTP 仅限开发](../decisions/0004-lan-http-development-only.md)
- [0005：生产 HTTPS-only](../decisions/0005-secure-production-boundary.md)
- [0026：备份与隔离恢复](../decisions/0026-personal-data-lifecycle-and-isolated-backups.md)
- [0031：应用 OTA 与健康中心](../decisions/0031-safe-application-firmware-ota-and-health-center.md)
- [安全部署 runbook](../../runbooks/secure-deployment.md)
- [个人数据备份 runbook](../../runbooks/personal-data-backup.md)

## 安全与兼容性约束

- 不组合 LAN 与 production Compose，不允许公网明文 HTTP/WS。
- 不把管理员密码、通知令牌、API Key、JWT、Wi-Fi 凭据或加密主密钥写入仓库、镜像或日志。
- 服务端及其内置管理页面可按用户的长期授权直接发布；Git 外部推送、固件刷写/OTA、修改卷/端口、切换部署模式或轮换凭据仍需明确授权。
