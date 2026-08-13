# 部署工作流

- 状态：STABLE
- 最后更新：2026-08-13
- 当前分支：`codex/evt-003-notification-digests`
- 基准提交：`6ed3b5d`
- 最后验证提交：`6ed3b5d`
- 当前模式：LAN HTTP development

## 当前目标

保持 ROLE-002 LAN 运行态；EVT-003 未获本轮授权前不替换容器。

## 已完成

- Docker Compose 运行 PostgreSQL、Redis、server 和独立备份容器；数据卷和备份卷分离。
- LAN development 绑定局域网地址；production 配置只接受可信代理后的 HTTPS/WSS。
- PostgreSQL 日/周轮转、原子备份、清单、只读状态和一次性临时库恢复验证。
- ROLE-001 server/V29 和角色管理前端已发布；CoreS3 保持原固件且 OTA 能力启用。
- Docker Desktop 数据位于 E 盘，现有卷、镜像和容器已保留。
- 完整合并历史见[里程碑索引](../milestones.md)。

## 正在进行

当前运行态为 ROLE-002 `b6cad0b` server/V30 与 CoreS3 `7e7c55f / motion_disabled / OTA=true`。EVT-002/EVT-003 分别包含 V31/V32，均未发布到当前 LAN。

## 下一步操作

如用户授权部署 EVT-003，先做新备份与隔离恢复，再只替换 server，并验证 V31/V32、摘要页面、REST/MCP 和未认证边界。

## 阻塞项

- 当前没有运行环境阻塞。
- EVT-003 无运行环境阻塞，但部署尚未授权。凭据轮换和 OTA 回退仍需各自显式授权。

## 关键文件

- `compose.yaml`
- `compose.lan.yaml`
- `compose.production.yaml`
- `server/Dockerfile`
- `ops/postgres-backup/`
- `scripts/verify-lan-compose.ps1`

## 验证命令与最近结果

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
- 后续再次替换容器、修改卷/端口、轮换凭据或推送分支仍需明确授权；既有部署授权不自动延伸到 EVT-003。
