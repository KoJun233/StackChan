# 部署工作流

- 状态：STABLE
- 最后更新：2026-08-23
- 当前分支：`codex/media-002-expression-experience`
- 基准提交：`19bd459`
- 最后验证提交：`41b8827`
- 当前模式：LAN HTTP development

## 当前目标

维持已部署的 MEDIA-002D 最终 LAN server/V35；CoreS3 已由用户自行安装并验收 `41b8827-perf9`，不自动 OTA。

## 已完成

- Docker Compose 运行 PostgreSQL、Redis、server 和独立备份容器；数据卷和备份卷分离。
- LAN development 绑定局域网地址；production 配置只接受可信代理后的 HTTPS/WSS。
- PostgreSQL 日/周轮转、原子备份、清单、只读状态和一次性临时库恢复验证。
- ROLE-001 server/V29 和角色管理前端已发布；CoreS3 保持原固件且 OTA 能力启用。
- Docker Desktop 数据位于 E 盘，现有卷、镜像和容器已保留。
- 完整合并历史见[里程碑索引](../milestones.md)。

## 正在进行

LAN server 运行 `media002-final-local-41b8827`/V35，连续 1–60 FPS、帧率重连同步和诊断标签均已发布；当前地址为 `http://192.168.1.3:8080/`。CoreS3 运行 `41b8827-perf9`，用户确认画面流畅度和音量正常。部署前镜像保留为 `stackchan-server:pre-media002-final-41b8827`，当前镜像保留为 `stackchan-server:media002-final-41b8827`，摘要 `sha256:bfd2019b4e8a84a0132794096d751f3bce872555165a11219081b820f9445810`。

## 下一步操作

等待用户在当前 LAN 页面完成最终体验复核；通过后整理单一任务提交。本轮不替用户 OTA、不清除 NVS、不做回退演练。

## 阻塞项

- 当前运行态无部署阻塞。设备操作仍需显式授权；继续保留 NVS，不做 OTA 回退演练。

## 关键文件

- `compose.yaml`
- `compose.lan.yaml`
- `compose.production.yaml`
- `server/Dockerfile`
- `ops/postgres-backup/`
- `scripts/verify-lan-compose.ps1`

## 验证命令与最近结果

- 2026-08-23 部署前工作树服务端 392/392、空库 Flyway V1..V35、前端 81/81/类型检查/生产构建、双固件 profile、三组任务栈预算和文档检查通过；自动化验证阶段未替换运行容器。
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
- 后续再次替换容器、修改卷/端口、轮换凭据、刷写固件或推送分支仍需明确授权；既有部署授权不自动延伸到 INT-013。
