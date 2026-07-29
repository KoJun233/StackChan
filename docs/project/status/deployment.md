# 部署工作流

- 状态：COMPLETE
- 最后更新：2026-07-29
- 当前分支：`codex/data-001-personal-data-lifecycle`
- 基准提交：`2a3b712`
- 最后验证提交：`2a3b712`
- 当前运行态代码：INT-008 任务版本，包含 DeepSeek V4 Agent Tool 修复、自定义 Skill ZIP 生命周期与可移植 MCP 管理；CoreS3 `b05d60f`，Flyway V21。

## 当前目标

在不改变当前 V21 LAN 运行态的前提下准备 DATA-001：新增独立备份卷和 PostgreSQL 工具容器、V22 server 及管理页面；只有用户明确授权后才实际发布。

## 已完成

- 新增独立 `stackchan-postgres-backups` 卷、`postgres-backup` 服务和 server 只读挂载；备份镜像构建阶段执行 7 日/4 周轮转测试，覆盖跨月、跨年、同周重复与部分文件边界。
- 当前运行 PostgreSQL 已通过明确命名的临时卷完成一次只读 `pg_dump`、SHA-256、一次性空 PostgreSQL 恢复和四类关键记录计数比对；结果成功，临时卷已删除，运行数据库、server、Redis、端口和固件均未改变。
- 新增 `scripts/verify-latest-postgres-backup.ps1` 和备份 runbook；脚本不接收目标数据库地址，只允许恢复到备份容器创建的一次性实例。
- 最终备份镜像 `stackchan-postgres-backup:data-001-candidate` 构建成功，摘要为 `sha256:26607653cb1a13d380fcba5969d6b044a5f227384ed96cd7942f2c3a18519fc2`。
- 独立官方 Node 24.15.0 验证镜像完成前端 65/65 和 production build，摘要为 `sha256:a5c838d459f4be05ea2716149e1ad471d1e98d6fb35d43ab1b26fee15aef987c`；任务提交随后使用仓库正式多阶段 Dockerfile 完整构建 server 候选镜像 `sha256:2e1cb300420b97552d83931bf65b01326f2294d18adfe4ad350fced398ccc546`。构建没有替换或停止运行中的 server、PostgreSQL 或 Redis。
- 用户明确授权后，旧 V21 镜像保留为 `stackchan-foundation-server:rollback-data001-pre-v22`；正式 server 镜像 `sha256:2e1cb300420b97552d83931bf65b01326f2294d18adfe4ad350fced398ccc546` 只替换 server，独立备份容器使用镜像 `sha256:0c22e9c5404b7ebba7859c9f1d04786c788a3457794c966aa7f2233c240ebd12`。PostgreSQL、Redis、LAN overlay、固件和现有凭据未改变。
- 发布后健康与网页根地址为 200、Flyway `22|true` 且迁移数 22，新 API/页面未登录访问为 401，server 启动错误数为 0；CoreS3 恢复 `b05d60f / motion_disabled` 新鲜心跳，原有 1 条 MCP 连接和 1 个 Skill 包保留。
- 首次恢复演练发现命令替换子 shell 未执行父级退出清理，导致临时 PostgreSQL 在备份周期内残留；备份 runner 改为在成功和每个失败分支显式停止并清理临时实例。修复镜像重新发布后，启动备份和独立 `verify-latest` 均成功，日/周备份为 2/1，临时恢复进程和一次性容器残留均为 0，server 对备份卷保持只读挂载。

- Compose 已将 `/app/data/agent-skills` 独立持久卷和 4 MiB 文件/5 MiB multipart 请求边界应用到既有 LAN 环境，运行数据库为 V21。
- V21 正式镜像 `sha256:b4380b4c4dac9e9ed6702c83b6cc27ee7e55c2939dcb8d3737f8161c8541d4a9` 已只替换现有 server；回退标签为 `stackchan-foundation-server:rollback-int008-pre-v21-managed-mcp`，PostgreSQL、Redis、LAN overlay 和固件保持不变。
- 真实 HTTPS MCP 连接已使用加密 Bearer 认证写入 V21，标准初始化与 `tools/list` 返回 200 并发现 8 个 Tool；本机 Nginx 代理和 `.mcp-local` 临时配置已删除，Tool 授权状态由管理员页面控制。
- V20 使用正式 Dockerfile 与 `compose.yaml + compose.lan.yaml` 构建，只替换 `stackchan-foundation-server-1`；旧镜像保留为 `stackchan-foundation-server:rollback-int008-pre-v20-skill-import`。新容器 `e3123e318054` 使用镜像 `sha256:a5cdbca8d04c8ecd2ed27dded18d32c440459ff7ac19f78fd74710a1e94f1037`，PostgreSQL/Redis 容器保持不变。
- 发布后健康与网页根地址为 200，Flyway `20|true` 且成功迁移数 20，`agent_skills` 表存在，`stackchan-foundation_stackchan-agent-skills` 挂载到 `/app/data/agent-skills`；未登录 Agent API 与 `/settings/agent` 均为 401，启动错误数为 0，CoreS3 `b05d60f / motion_disabled` 恢复新鲜心跳。
- 用户明确授权后，部署前从 Flyway V18 运行库生成并校验仓库外 PostgreSQL 自定义格式快照，并将旧镜像保留为 `stackchan-foundation-server:rollback-8bb6332-pre-v19`。
- 正式多阶段 Dockerfile 连续两次因 Maven Central TLS 握手失败而未替换服务；随后使用受支持 Node v24.15.0 与本机 Maven 缓存重新生成前后端产物，并使用相同固定 `eclipse-temurin:21-jre` 摘要组装等价镜像 `sha256:da0e576d8565c30a5156e2bfb475b61f8d829614150422b5c32713e687bc9ad7`。临时 Dockerfile 已删除且未提交，仓库正式 Dockerfile 未修改。
- 只替换 `stackchan-foundation-server-1`，容器由 `b2d7e1f6e44e` 变为 `743d0f70f758`；Flyway 从 V18 升至 V19，健康与网页根地址为 200，未登录 `/settings/agent` 和 Agent API 均为 401，三张 Agent 表与管理页静态资源存在，近五分钟错误数为 0。
- 首次 Agent 人工验收暴露 Tool Calling 选项被安全包装层丢失；修复镜像 `sha256:9b66191cc9e9d70cefa61e7cba6511a35fb68a162b4c7bf853fcb83afcef1212` 只替换 server，容器由 `743d0f70f758` 变为 `df681d889349`，上一镜像保留为 `stackchan-foundation-server:rollback-8bb6332-pre-tool-routing-fix`。发布后健康与网页为 200、Flyway `19|true`、近期错误和 ChatOptions 降级警告均为 0；PostgreSQL/Redis 容器未变。
- 继续排查确认 ReactAgent 已通过 `.tools(List<ToolCallback>)` 注入直接 Tool，且测试会断言模型请求携带 `current_date_time` 回调；DeepSeek V4 Agent 请求现单独发送 `thinking.type=disabled`，普通聊天配置不变。修复镜像 `sha256:fc2940c183f87db25bc14ac8b786169bd001a8dfa5d6603eba99391a13d4ae35` 只替换 server，容器变为 `95904af388d5`，上一镜像保留为 `stackchan-foundation-server:rollback-int008-pre-deepseek-agent-fix`。发布后健康为 200、Flyway `19|true`、启动错误为 0；等待用户端到端复测，尚未提交或推送。
- 用户复测仍得到安全固定回复且 Tool 审计总数为 0；进一步确认 Spring AI Alibaba 通过反射读取 ChatModel 默认选项，而私有 `SafeChatModel` 包装类导致访问失败并静默丢失原生选项。包装类现为公开可反射类型，ReactAgent Builder 同时显式接收同一份 `chatOptions`，并改用标准 `agent.call(messages)` 完成模型/Tool 循环。镜像 `sha256:a58041aff8efb79fecc109a9a9011ed9aaabff5ab2b663c6056b8a81d3ba9fcd` 只替换 server，容器变为 `0ec1fb91053b`；健康为 200、Flyway `19|true`、启动错误为 0，等待再次复测。
- PostgreSQL/Redis 容器保持 `6d8feaa18623` / `58e31a403637`，LAN 继续绑定 `0.0.0.0:8080`；未连接 MCP 服务、COM3，未刷写、OTA、修改卷、端口、凭据或生产 HTTPS-only 边界。
- INT-007 用户授权后，从干净 `f91dbdb` 使用正式 Dockerfile 构建镜像并通过 `compose.yaml + compose.lan.yaml` 只替换现有 server；旧镜像保留为 `stackchan-foundation-server:rollback-f91dbdb-pre-v18`。
- 新镜像为 `sha256:b7a8ea8c36b212d1e25e1f98a8a933c78fcbc530d247520e1ec0a34e270da321`，server 容器由 `4d9db38c136c` 变为 `b2d7e1f6e44e`；Flyway 从 V17 升至 V18，健康与网页均为 200，未登录表达资源 API 为 401，三张表达资源表存在，近期错误数为 0。
- PostgreSQL/Redis 容器保持 `6d8feaa18623` / `58e31a403637`，LAN 继续绑定 `0.0.0.0:8080`；CoreS3 `2465427 / motion_disabled` 自动恢复新鲜心跳。未连接 COM3、刷写、OTA、修改卷、端口、凭据或生产 HTTPS-only 边界。
- 用户确认 `8394cb3`、CoreS3、`COM3` 和 LAN HTTP Quad profile 后，bootloader、分区表、应用、OTA data 和语音模型已完整写入并逐区回读匹配，NVS 未擦除；启动硬件与安全状态正常。随后确认固件在默认资源清理时进入 60 秒 WebSocket 重连，当前不视为稳定发布。
- 用户确认 `b05d60f`、CoreS3、`COM3` 和 LAN HTTP Quad profile 后，修复镜像已重新完整写入并逐区回读匹配，NVS 未擦除；启动、单次 WebSocket 建连、跨两个心跳周期的持续刷新和 server 健康均通过，重连缺陷关闭。
- 当前本地模式为 LAN HTTP development mode。
- `verify-lan-compose.ps1` 已在 `731a68c` 通过。
- Docker Desktop 数据目录已迁移到 `E:\DockerDesktop`；原 C 盘 WSL 路径是指向 `E:\DockerDesktop\wsl` 的 Junction，当前 VHDX 位于 E 盘且现有 PostgreSQL 卷、镜像和容器均保留。
- PostgreSQL、Redis 和 server 当前正常运行；迁移后 C 盘约有 23 GiB 空闲，E 盘约有 1.48 TiB 空闲。
- `f962d71` 已按既有 `stackchan-foundation` LAN Compose 项目重建并仅重建 server；没有切换 Compose 模式或输出运行凭据。
- 重建后 `/api/v1/health` 返回 200，Flyway 保持 v8，设备心跳恢复，容器静态资源包含语音配置提交修复。
- `3e50d56` 已再次只重建同一 LAN server；线上语音页面资源确认包含传送按钮的 `form` 绑定，健康检查返回 200。
- 当前同一 `stackchan-foundation` LAN server 已完整重建到 `29aef87`，不再使用临时热复制的前端资源；PostgreSQL 卷保留 1 台设备和 1 条既有提醒记录。
- 完整重建后管理页面保存和百炼双向语音测试均真实通过，服务端健康接口保持 200。
- `f0dcecd` 已按既有 `stackchan-foundation` LAN Compose 项目完整重建；健康接口返回 200，Flyway 保持 v8，设备恢复 `b4876fb` / `motion_disabled` 心跳，22:36 在线提醒收到成功播放 ACK。
- `6286c34` 已按同一 `stackchan-foundation` LAN Compose 项目只重建 server；PostgreSQL、Redis、卷和 Compose 模式未改变，健康接口返回 200，设备恢复 `b4876fb` / `motion_disabled` 心跳。
- `e41a40f` 已部署到同一 `stackchan-foundation` LAN server；PostgreSQL、Redis、外部卷、LAN HTTP mode 和生产 HTTPS-only 边界均未改变。健康接口返回 200，Flyway 升至 v9，1 台设备恢复心跳。
- 正式多阶段 Docker 构建首次被镜像站获取 Node/Maven 基础镜像元数据超时阻塞；部署改用本机已验证的 Node/Maven 产物和相同固定 `eclipse-temurin:21-jre` 运行时构建等价镜像。临时 Dockerfile 已删除且未提交，仓库正式 Dockerfile 未修改。
- `06a67ab` 已使用仓库正式多阶段 Dockerfile 完整重建到同一 `stackchan-foundation` LAN server；PostgreSQL、Redis、外部卷、LAN HTTP mode 和生产 HTTPS-only 边界均未改变。
- 第一次重建因 Maven Central 临时 TLS 握手中断而失败，旧服务期间持续健康；重试后镜像构建和容器替换成功，Flyway 升至 v10，健康接口返回 200，CoreS3 恢复 `b4876fb` / `motion_disabled` 心跳。
- 当前任务移除生成器可执行文件、超时和上传配置；正式镜像复制锁定模型目录，Compose 固定设置 `COMPANION_WAKE_MODEL_CATALOG_DIRECTORY=/app/wakenet-models`，不新增凭据。
- 2026-07-26 已使用仓库正式多阶段 Dockerfile 构建并只替换同一 `stackchan-foundation` server；PostgreSQL、Redis、外部卷、LAN HTTP mode 和生产 HTTPS-only 边界均未改变。
- 部署前已从旧 server 容器制作本地回退镜像 `stackchan-foundation-server:rollback-06a67ab`；临时导出文件已删除，未输出或写入凭据。
- 首次新镜像运行暴露 READY 调度悲观锁查询缺少事务边界；修复、针对测试和重新构建后再次替换 server，持续日志观察未再出现事务异常。
- 本地上传增量已使用仓库正式多阶段 Dockerfile 重建并只替换同一 LAN server；部署前给原镜像添加本地回退标签 `stackchan-foundation-server:rollback-9b61339`，没有把容器运行凭据写入镜像或仓库。
- Flyway 已从 V11 升至 V12；PostgreSQL、Redis、外部卷、端口、LAN HTTP mode 和生产 HTTPS-only 边界均未改变。
- 最终镜像替换时曾误用基础 Compose，导致 server 暂时只发布 `127.0.0.1:8080`，网页本机可用但 CoreS3 离线；已改回 `compose.yaml + compose.lan.yaml`，恢复 `0.0.0.0:8080` 和既有 LAN development mode。
- INT-003 部署前给原 server 镜像添加本地回退标签 `stackchan-foundation-server:rollback-ca2ec8a-pre-v15`；使用正式 Dockerfile 构建 `ca2ec8a` 并只重建 `stackchan-foundation-server-1`，未输出或写入运行凭据。
- 新 server 启动后 Flyway 从 V14 升至 V15；PostgreSQL、Redis、外部卷、`0.0.0.0:8080` LAN overlay 和生产 HTTPS-only 边界均未改变。
- INT-005 部署前将 `219b90b` 镜像保留为 `stackchan-foundation-server:rollback-d4ad838-pre-v16`；使用正式 Dockerfile 构建 `d4ad838` 并通过 `compose.yaml + compose.lan.yaml` 只替换 `stackchan-foundation-server-1`。
- 新 server 启动后 Flyway 从 V15 升至 V16；PostgreSQL、Redis、外部卷、`0.0.0.0:8080` LAN overlay、运行凭据和生产 HTTPS-only 边界均未改变，未连接 COM3 或刷写固件。
- INT-006 部署前将旧镜像保留为 `stackchan-foundation-server:rollback-9495111-pre-v17`；从干净 `9495111` 构建正式镜像并只替换现有 server，容器由 `dc2a0ff8e75b` 变为 `c6a2b12a8152`。
- 新 server 镜像为 `sha256:ece87baaf655536a7578d4e2af87c67276b99ebc1ef37374728471aea21b879a`；Flyway 从 V16 升至 V17，健康与网页为 200，未登录交互设置 API 为 401，启动错误数为 0。PostgreSQL/Redis 容器保持 `6d8feaa18623` / `58e31a403637`，LAN 继续绑定 `0.0.0.0:8080`，旧 CoreS3 `717a8b1 / motion_disabled` 恢复新鲜心跳。
- 用户明确授权后，CoreS3 `COM3` 已完整刷入从干净 `c1d7383` 构建的 LAN HTTP Quad 镜像；五个区域独立校验通过且 NVS 未擦除。启动确认新版本、8 MB PSRAM、加密 NVS、CoreS3 外设、WakeNet、WebSocket 与 `motion_disabled`，数据库收到新鲜心跳；server、PostgreSQL、Redis、Flyway V17、卷、端口、凭据和生产 HTTPS-only 边界未改变。
- 用户明确授权后，从干净 `f0d99fa` 使用正式 Dockerfile 构建 `sha256:0f780fd7264c0137238e89783bc6e172cf61fe4b2ad23e5a3a329ea10b95d1cc` 并通过正式 LAN overlay 只替换 server；旧镜像保留为 `stackchan-foundation-server:rollback-f0d99fa-pre-fix`。server 容器变为 `4d9db38c136c`，PostgreSQL/Redis 保持 `6d8feaa18623` / `58e31a403637`。
- 用户明确授权后，从干净 `2465427` 构建并完整刷写 CoreS3 `COM3` 的 LAN HTTP Quad 镜像；bootloader、分区表、应用、OTA data 和语音模型五个区域独立校验通过，NVS 未擦除。启动、WebSocket 和 `2465427 / motion_disabled` 心跳通过，server、数据库、LAN overlay 与生产 HTTPS-only 边界未改变。

## 正在进行

当前 LAN 已运行 DATA-001 / Flyway V22 和独立 `postgres-backup` 服务，用户页面人工验收通过。真实 HTTPS MCP 和授权状态保持不变，CoreS3 `b05d60f` 保持不变，未连接设备调试端口。

当前 `stackchan-foundation` 使用正式 LAN overlay 运行 `f91dbdb` server，Flyway 为 V18，并发布 `0.0.0.0:8080`。CoreS3 运行 `b05d60f` LAN HTTP Quad 镜像并持续报告 `motion_disabled`；PostgreSQL、Redis、卷、端口、凭据和生产 HTTPS-only 边界未改变。

首轮验收修复的 server/前端已发布。首次提醒在重连窗口丢失 ACK，既有五分钟恢复任务重试后送达；主动问候下一调度周期一次送达。固件取消上报修复已随 `2465427` 完整刷写，等待实体复测。

此前正常对话失败已定位为设备侧 `voice_control` 栈溢出；修复镜像 `216d383` 已部署到设备，正常回合、可恢复异常和恢复后的正常回合均完成人工验收。server 运行态、V14 数据库和 LAN overlay 无需修改。

INT-003 server 发布后正常回合为 `COMPLETED`；随后 `717a8b1` 固件完整刷入 CoreS3，五区域校验、启动、WebSocket、心跳和四项实体触摸验收全部通过。最近运行数据包含真实 `TOUCH_STARTED` 与 `CANCELLED`，Flyway 保持 V15，近期服务端错误数为 0。

## 下一步操作

推送任务分支，由用户创建 PR 并人工合入 `master`。如需回退，只回退 server 到 `stackchan-foundation-server:rollback-data001-pre-v22`，并在操作前单独评估 V22 数据库兼容性和备份服务状态。

## 阻塞项

实现、镜像、运行发布和人工验收均无阻塞。工作区仍没有 `.env`；后续重建必须继续从现有容器或受控环境文件保留相同数据库密码、设备密钥、加密密钥和 MCP 密文解密能力，不得输出秘密、组合 LAN 与 production profile 或降低生产 HTTPS-only 边界。

## 关键文件

- `compose.yaml`
- `compose.lan.yaml`
- `compose.production.yaml`
- `scripts/verify-lan-compose.ps1`

## 验证命令与最近结果

- V21 工作树后端 Maven 全量 266/266、Testcontainers 空库 V1..V21、前端 Vitest 22 个文件 62/62、`vue-tsc -b`、production build、`git diff --check`、`pnpm docs:check` 和 LAN Compose 静态验证通过。发布后健康为 200、Flyway `21|true`、迁移数 21、MCP 连接表存在、启动错误数为 0；真实 HTTPS MCP 初始化和 `tools/list` 为 200，并发现 8 个 Tool，授权状态由管理员页面控制。
- V20 工作树后端 Maven 全量 264/264、Testcontainers 空库 V1..V20、前端 Vitest 22 个文件 61/61、`vue-tsc -b`、production build、`git diff --check`、`pnpm docs:check` 和 LAN Compose 静态验证通过。发布后健康与网页为 200、Flyway `20|true`、迁移数 20、Skill 表与卷存在、未登录 Agent API/页面为 401、启动错误数为 0，CoreS3 心跳新鲜；未创建 `.env`、连接 MCP/COM3 或刷写固件。
- INT-008 通过 LAN Compose 静态验证：基础模式仍只绑定 `127.0.0.1:8080`，LAN overlay 为 `0.0.0.0:8080`，PostgreSQL/Redis 不发布主机端口。发布后健康与网页根地址为 200、Flyway `19|true`、三张 Agent 表和三个 Agent 静态压缩资源存在、未登录路由/API 为 401、近五分钟错误数为 0；未创建 `.env` 或连接 MCP 服务。
- BASE-008 使用仅限当前进程的非秘密占位值通过文档测试 7/7 和 LAN Compose 静态验证；基础模式仍只绑定 `127.0.0.1:8080`，LAN overlay 绑定 `0.0.0.0:8080`，PostgreSQL/Redis 均不发布主机端口。未创建 `.env`、未重建容器或修改运行资源。
- INT-007 `f91dbdb` 只替换现有 LAN server 后，健康与网页均为 200、Flyway `18|true`、迁移数 18、三张表达资源表存在、未登录表达资源 API 为 401、容器包含表达资源页面静态包、近期错误数为 0；PostgreSQL/Redis 未重建，LAN 保持 `0.0.0.0:8080`，CoreS3 `2465427 / motion_disabled` 自动重连。新增固件分区表仍未刷写，不能把 server 发布误记为实体验收。
- `8394cb3` 完整镜像五区写入与独立回读均通过，NVS 未擦除；启动确认版本、PSRAM、外设、WakeNet 和 `motion_disabled`。WebSocket 约两秒后停止并最终退避 60 秒，数据库固件版本更新为 `8394cb3` 但心跳不连续；server 健康 200、同期错误数 0。当前本地修复已通过协议/LAN 构建和固件安全回归，尚未重刷。
- `b05d60f` 修复镜像五区重新写入和独立回读均通过，NVS 未擦除；42 秒启动窗口只建立一次 WebSocket，后续 32 秒无连接或故障事件，数据库心跳从 `22:42:52` 刷新到 `22:43:42`。server 健康 200、错误数 0，PostgreSQL/Redis、Flyway V18、LAN 端口和生产 HTTPS-only 边界未改变。
- 用户确认默认机械眼没有问题；宠物表情包页面、八图生成、启用和恢复默认因当前没有多余素材由用户选择暂缓，当前运行资源保持不变。
- INT-006 验收修复服务端 236/236、前端 55/55、`vue-tsc -b`、production build、Flyway 空库 V1..V17，以及 ESP-IDF 协议/LAN HTTP Quad profile 构建通过。`f0d99fa` 部署后健康与网页为 200、Flyway `17|true`、迁移数 17、LAN `0.0.0.0:8080`、启动错误数 0；单次提醒恢复重试和主动问候均为 `DELIVERED`。
- INT-006 经用户明确授权，将 `2465427` LAN HTTP Quad 完整镜像刷入 CoreS3 `COM3`；bootloader、分区表、应用、OTA data 和语音模型五区独立 `verify_flash` 全部匹配且 NVS 未擦除。启动确认版本、8 MB Quad PSRAM / 80 MHz、加密 NVS、CoreS3 外设、WakeNet、LAN HTTP、WebSocket 与 `motion_disabled`，数据库收到新鲜心跳；35 秒窗口未见 panic、栈溢出或看门狗。server 健康保持 200、Flyway `17|true`，近 15 分钟 server 错误数为 0。
- 用户确认 INT-006 六项页面/实体验收和修复后的语音识别与合成全部正常；已暴露凭据由用户撤销并替换，运行边界和仓库均未保存秘密。
- INT-006 CoreS3 `c1d7383` 完整镜像五区写入和独立校验通过，NVS 未擦除；启动、PSRAM、CoreS3 外设、WakeNet、LAN WebSocket 与 `motion_disabled` 心跳通过。server 健康保持 200、Flyway 保持 `17|true`、近期 server 错误数为 0。
- INT-005 Testcontainers 已从空库成功执行 V1..V16，服务端全量 225/225、前端 53/53 和 production build 通过。经用户明确授权，从干净 `d4ad838` 构建镜像 `sha256:c6bc9795d11831f73c2ab7914f0bce611a9057b352bcb5569ae992e781972c9e` 并只替换 server；旧镜像保留为 `stackchan-foundation-server:rollback-d4ad838-pre-v16`。server 容器由 `011ad10df7f9` 变为 `dc2a0ff8e75b`，PostgreSQL/Redis 保持 `6d8feaa18623` / `58e31a403637`；健康接口与网页根地址为 200，LAN 为 `0.0.0.0:8080`，Flyway `16|true` 且成功迁移数 16，未登录人设/记忆 API 均为 401，容器包含人设与记忆静态资源，启动错误数为 0。CoreS3 `717a8b1 / motion_disabled` 恢复心跳；未连接 COM3、刷写固件或改变生产 HTTPS-only 边界。
- INT-005 用户完成管理端与对话行为人工验收并确认没有问题；当前运行资源继续保持不变。

- INT-003 本地验证确认 Flyway 空库可到 V15、旧固件四字段 ACK 仍被接受、新固件 LAN HTTP Quad profile 可构建。随后按授权只替换 server：健康接口与网页根地址为 200、Flyway `15|true`、V15 约束生效、近期错误数为 0；旧固件 `216d383 / motion_disabled` 恢复心跳并完成完整成功回合。
- INT-003 经用户明确授权，将从干净 `717a8b1` 构建的 LAN HTTP Quad 完整镜像刷入 CoreS3 `COM3`；五个区域独立 `verify_flash` 全部匹配且 NVS 未擦除。启动确认 CoreS3 外设、PSRAM、LAN HTTP、WakeNet、WebSocket 与 `motion_disabled`，数据库收到 `717a8b1` 心跳；server、PostgreSQL、Redis 均保持运行，Flyway `15|true`，近期错误数为 0。用户确认屏保首触、长按说话、阶段取消和播放立即停止四项全部通过，最近诊断出现 5 次 `TOUCH_STARTED` 和 1 个 `CANCELLED`。
- INT-004 部署前基线来自最近 11 个完成回合：录音结束到播放开始 P50/P95 为 `5788/7210 ms`，ASR/LLM/TTS P50 为 `400/2960/1567 ms`，服务端总 P50 为 `5226 ms`。统计只读取诊断阶段与耗时。首版部署前工作树服务端全量 221/221 通过，当时尚未重建或替换运行中的 server，Flyway、CoreS3、Compose、卷、端口和凭据均未改变。
- INT-004 经用户明确授权，从干净 `5016324` 构建 `sha256:c6f6113fffb7d070f6a808bdfacb9b76a872aa2aa5557c67b39494747f9c6d9a` 并只替换 server；旧镜像 `sha256:315eaf7a24cb63a28f8d44abdab1a85309496ae862b8e7fa9ef79a88d35e71b2` 保留为 `stackchan-foundation-server:rollback-5016324-pre-int004`。server 容器由 `e1bf2c007d69` 变为 `b17ee0d4b280`，PostgreSQL/Redis 容器未变；网页根地址和健康接口为 200，LAN 为 `0.0.0.0:8080`，Flyway `15|true` 且成功迁移总数 15，设备 `717a8b1 / motion_disabled` 在 30 秒内恢复心跳，近期错误数为 0。
- INT-004 从 `2026-07-26 10:48:44+00` 起收集到 11 个成功回合且无失败或取消。录音结束到播放开始 P50/P95 为 `5978/7158 ms`，上传到播放 P50/P95 为 `5921/7105 ms`，ASR/LLM/TTS P50/P95 分别为 `381/819`、`3583/4156`、`1403/1771 ms`，服务端总 P50/P95 为 `5443/6021 ms`。相对部署前同样 11 个成功回合，中位首音频延迟回退 190 ms，首版不视为性能验收通过，因此继续实施并发布第二版。
- INT-004 第二版经用户明确授权，从干净 `3d8c1fb` 构建 `sha256:192ed2297336577bf96b3b1479f7c9c11336ceceba9fccb907a7f0e72a78e9a3` 并只替换 server；旧镜像 `sha256:c6f6113fffb7d070f6a808bdfacb9b76a872aa2aa5557c67b39494747f9c6d9a` 保留为 `stackchan-foundation-server:rollback-3d8c1fb-pre-int004-v2`。server 容器由 `b17ee0d4b280` 变为 `c97e6e139830`，PostgreSQL/Redis 容器保持 `6d8feaa18623` / `58e31a403637`；网页根地址和健康接口为 200，LAN 为 `0.0.0.0:8080`，Flyway `15|true` 且成功迁移数 15，CoreS3 `717a8b1 / motion_disabled` 恢复心跳，近期错误数为 0。新测量起点为 `2026-07-26 12:12:21.103106+00`，当时语音回合总数为 62。
- INT-004 第二版测量起点后共有 11 个成功回合和 1 个 `NO_SPEECH`。录音结束到播放开始 P50/P95 为 `6139/7430 ms`，上传到播放为 `6081/7381 ms`，ASR/LLM/TTS 为 `328/712`、`3635/4621`、`1564/1755 ms`，服务端总耗时为 `5543/6835 ms`；中位与尾部均未优于原始基线，第二版不视为性能验收通过。当前运行资源保持不变。
- INT-004 第三版经用户明确授权，从干净 `e3a752f` 构建 `sha256:44095eecafa334d0e5a7e033db920efa014689a6f26871cbc961983c23dd4a29` 并只替换 server；第二版镜像 `sha256:192ed2297336577bf96b3b1479f7c9c11336ceceba9fccb907a7f0e72a78e9a3` 保留为 `stackchan-foundation-server:rollback-e3a752f-pre-int004-v3`。server 容器由 `c97e6e139830` 变为 `963bd58931bb`，PostgreSQL/Redis 容器保持 `6d8feaa18623` / `58e31a403637`；网页根地址和健康接口为 200，LAN 为 `0.0.0.0:8080`，Flyway `15|true` 且迁移数 15，CoreS3 `717a8b1 / motion_disabled` 恢复心跳，近期错误数为 0。新测量起点为 `2026-07-26 12:40:15.810739+00`，当时语音回合总数为 75。
- INT-004 第三版测量起点后共有 10 个成功回合和 1 个设备 `PLAYBACK_FAILED`。录音结束到播放开始 P50/P95 为 `6131/6327 ms`，上传到播放为 `6075/6268 ms`，ASR/LLM/TTS 为 `348/736`、`3364/3647`、`1742/1977 ms`，服务端总耗时为 `5535/5809 ms`。P95 相对原始基线显著改善，P50 仍未达标；失败回合已完成服务端 TTS 并进入设备播放，健康接口为 200、同期 server 错误数为 0，当前运行资源保持不变。
- 用户在得知约 20 字效果来自仍启用的 1500 ms/首句/40 code point 限制后，明确要求全部取消；当前运行态仍是 `e3a752f`，完整输出版本尚未部署。孤立 `PLAYBACK_FAILED` 仍保留为仅在重复出现时调查的观察项。
- 完整输出版本使用仅限当前进程的非秘密占位值通过 LAN Compose 静态验证；基础模式保持 `127.0.0.1:8080`，LAN overlay 保持 `0.0.0.0:8080`，PostgreSQL/Redis 不发布主机端口。未创建 `.env`、未重建或替换运行容器。
- INT-004 经用户授权，从干净 `219b90b` 使用正式 Dockerfile 构建 `sha256:ffc6534de0484c61c8a8776c3c004c54a731b720dbd8c99bc9a593a7e2c51e6e` 并只替换 server；旧镜像保留为 `stackchan-foundation-server:rollback-e3a752f-pre-219b90b`。server 容器由 `963bd58931bb` 变为 `011ad10df7f9`，PostgreSQL/Redis 保持 `6d8feaa18623` / `58e31a403637`。健康与网页为 200、LAN `0.0.0.0:8080`、Flyway `15|true`、CoreS3 心跳 `717a8b1 / motion_disabled`，近期错误数为 0；未刷写固件或改变生产 HTTPS-only 边界。
- 用户确认部署后的长回复完整播放且没有问题；部署人工验收完成，当前运行资源保持不变。
- 当前任务的 LAN Compose 静态验证通过；仅使用当前进程内的非秘密占位值，代码实现未触碰运行中的容器、卷、端口或凭据。
- INT-001 最新 `master` 合并回归再次完成 LAN Compose 静态展开：基础模式只绑定 `127.0.0.1:8080`，LAN overlay 绑定 `0.0.0.0:8080`，PostgreSQL 与 Redis 均未发布主机端口。未重建或替换运行中服务。
- 2026-07-26 本任务 LAN 部署：`/api/v1/health` 和网页根地址均返回 200，Flyway 为 `11|true`，容器内包含 `speech-DFhfjNNt.js`；调度事务修复后日志中未再出现 `Query requires transaction be in progress`，CoreS3 心跳恢复为 `e33a0d4` / `motion_disabled`。
- 2026-07-26 本地上传增量部署：`/api/v1/health` 和网页根地址均返回 200，Flyway 为 `12|true`，容器内 `speech-CKv17cKU.js` 包含“在线生成/上传模型包/上传并安装/本地上传”；服务端近两分钟错误数为 0，CoreS3 心跳保持 `0398073` / `motion_disabled`。未触发模型 OTA、刷写或第三方提交。
- 2026-07-26 内置目录部署：部署前保留 `stackchan-foundation-server:rollback-upload-v12`；正式 Dockerfile 构建成功并只替换 server。健康和网页根地址均为 200，未登录目录 API 返回 401，Flyway `13|true`，容器模型目录为 13 组，近两分钟错误数为 0；CoreS3 心跳保持 `0398073` / `motion_disabled`。未触发模型 OTA 或刷写。
- 2026-07-26 首次内置词真机切换：基础 Compose 的 loopback 监听使任务暂留 `READY`；恢复 `compose.lan.yaml` 后 CoreS3 自动重连，任务 `d9fcd60f-51cf-4e5d-a12b-7cb7fbcfb5da` 完成 `INSTALLED`，目标为 `wn9_xiao3feng1xiao3feng1_tts3`，设备继续报告 `0398073` / `motion_disabled`。未重新刷写固件。
- 2026-07-26 CoreS3 完成 `0398073` LAN HTTP Quad 三槽引导，数据库收到 `0398073` / `motion_disabled` 心跳；Compose 模式、卷、端口和生产 HTTPS-only 边界未改变。
- 2026-07-26 INT-002 实机验证：CoreS3 `COM3` 已刷入 `adbd75e` LAN HTTP Quad 完整镜像，五个区域独立摘要校验通过且 NVS 未擦除；`/api/v1/health` 返回 200，Flyway 保持 `14|true`，数据库收到 `adbd75e` / `motion_disabled` 最近心跳。server、PostgreSQL、Redis、卷、端口、Compose 模式和生产 HTTPS-only 边界均未改变。
- 2026-07-26 INT-002 修复镜像验证：经用户明确授权，CoreS3 `COM3` 已刷入从干净 `abd6a22` 重建的 LAN HTTP Quad 完整镜像，五个区域独立 `verify_flash` 全部匹配且 NVS 未擦除；`/api/v1/health` 返回 200，Flyway 保持 `14|true`，数据库收到 `abd6a22` / `motion_disabled` 新鲜心跳。server、PostgreSQL、Redis、卷、端口、Compose 模式和生产 HTTPS-only 边界均未改变。
- 2026-07-26 正常回合失败诊断：设备脱敏串口在两次 `Speech captured` 后均确认 `voice_control` 任务栈溢出并软件复位，数据库没有 `REQUEST_RECEIVED`；server 健康、Flyway V14 和设备重连心跳正常。修复只涉及本地固件语音任务栈预算，不需要替换 server 或执行迁移。
- 2026-07-26 INT-002 栈修复镜像验证：经用户明确授权，CoreS3 `COM3` 已刷入从干净 `216d383` 重建的 LAN HTTP Quad 完整镜像，五个区域独立 `verify_flash` 全部匹配且 NVS 未擦除；启动确认版本、8 MB PSRAM、CoreS3 外设、LAN HTTP、WakeNet、WebSocket 与 `motion_disabled`，未见 panic、栈溢出、看门狗或重启循环。`/api/v1/health` 返回 200，Flyway 保持 `14|true`，数据库收到 `216d383` / `motion_disabled` 新鲜心跳。server、PostgreSQL、Redis、卷、端口、Compose 模式和生产 HTTPS-only 边界均未改变；正常对话仍待用户实测。
- 2026-07-26 INT-002 正常回合复验：用户完成两轮对话，数据库两轮均为 `COMPLETED`，完整包含请求接收、ASR、LLM、TTS、播放开始/完成与恢复监听；串口记录两次录音完成和恢复监听，未出现 `voice_control` 栈溢出、panic、看门狗或复位。另一次独立 `NO_SPEECH` 按预期结束。server、Flyway V14、LAN overlay、凭据、端口和生产 HTTPS-only 边界均未改变。
- 2026-07-26 INT-002 正常回合人工验收：用户确认两轮语音回复、成功反馈和返回待机均正常。无需修改或重建 server，下一项仅按 runbook 临时制造并恢复供应商模型失败以验收橙色可恢复错误。
- 2026-07-26 INT-002 异常反馈人工验收：用户确认临时模型失败的可恢复异常反馈以及恢复配置后的正常回合均正常，并要求不再读取串口或数据库证据。被动监听已停止；未重建或替换 server，Flyway、Compose、端口、凭据和生产 HTTPS-only 边界均未改变。
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lan-compose.ps1`：在 `b4876fb` 使用仅限当前进程的验证占位值和 `stackchan-foundation` 项目名通过；没有 Compose 模式或凭据变更。
- `f962d71`：同一 LAN Compose 项目重建 server 成功；健康检查 200，Flyway v8，设备心跳在 `2026-07-20 00:12:49+08:00` 后恢复。
- `3e50d56`：同一 LAN Compose 项目再次重建 server 成功；健康检查 200，线上资源包含 `speech-settings-form` 绑定。
- `29aef87`：同一 LAN server 完整重建成功；页面保存、刷新持久化和百炼双向测试通过，健康接口为 200，Flyway 保持 v8。
- `f0dcecd`：同一 LAN server 完整重建成功；健康接口为 200，Flyway v8，设备心跳和在线提醒成功 ACK 均已恢复。
- `6286c34`：同一 LAN server 只重建 server 成功；健康接口为 200，启动恢复 0 条流式生成，设备在重建后自动恢复心跳。
- `e41a40f`：提交后 Maven 179/179、前端 44/44、`vue-tsc`、production build、`git diff --check` 和 `pnpm docs:check` 通过；等价运行时镜像部署后健康接口 200、Flyway v9，线上静态资源包含 `speech-2wJFeG8R.js`。
- 2026-07-21 部署核对：当前数据库语音模式为 `REALTIME/NON_REALTIME`，这是 V9 对旧协议行为的直接迁移，不根据模型名改写；管理员保存前保持不变。
- 2026-07-20 运行核对：`stackchan-foundation-server-1` 正常运行并只发布 LAN 开发端口 `0.0.0.0:8080`；PostgreSQL 和 Redis 未发布宿主机端口。
- Docker 数据检查：`settings-store.json` 指向 `E:\DockerDesktop`，C 盘 Junction 指向 E 盘 WSL 数据目录，VHDX 与现有服务均存在。
- 2026-07-21 `06a67ab` 重建：仓库正式 Dockerfile 构建成功，健康接口 200，Flyway `10|true`，线上 `speech-DFdzgFtJ.js` 包含三项本地语音配置字段，CoreS3 心跳在重建后 23 秒内恢复。

## 相关设计、计划和决策

- [下一阶段可执行任务清单](../todo.md)
- [安全部署 runbook](../../runbooks/secure-deployment.md)
- [0004：LAN HTTP/WS 仅限显式编译的开发固件/profile](../decisions/0004-lan-http-development-only.md)
- [0005：生产环境保持可信代理后的 HTTPS-only 边界](../decisions/0005-secure-production-boundary.md)
- [0015：运行时生成并安全 OTA 自定义唤醒模型](../decisions/0015-runtime-wake-model-generation-and-ota.md)
- [0016：唤醒词仅从 ESP-SR 内置模型目录选择并安全 OTA](../decisions/0016-built-in-esp-sr-wake-model-catalog.md)
- [0018：触摸控制采用本地事件队列与幂等语音回合取消](../decisions/0018-touch-control-and-voice-turn-cancellation.md)
- [0023：文字与语音共享受控 ReactAgent、Skill、Tool 与 MCP](../decisions/0023-controlled-react-agent-skills-tools-mcp.md)
- [0026：个人数据物理删除、范围导出与隔离备份恢复](../decisions/0026-personal-data-lifecycle-and-isolated-backups.md)
- [个人数据备份与隔离恢复验证 runbook](../../runbooks/personal-data-backup.md)
- [Agent、Skill、Tool 与 MCP 运维 runbook](../../runbooks/agent-tools-mcp.md)

## 安全与兼容性约束

- LAN HTTP 仅限局域网开发。
- 生产环境必须保持 HTTPS-only，且不得组合 `compose.lan.yaml` 与 `compose.production.yaml`。
- 不在状态文档中记录凭据或完整认证配置。
