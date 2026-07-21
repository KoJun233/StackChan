# 部署工作流

- 状态：STABLE
- 最后更新：2026-07-21
- 当前分支：`master`
- 基准提交：`ROOT`
- 最后验证提交：`ROOT`
- 当前运行态提交：`06a67ab`

## 当前目标

部署基线已恢复并重验；本任务不改变 Compose 模式。LAN HTTP development mode 与生产 HTTPS-only 边界保持不变。

## 已完成

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

## 正在进行

没有进行中的部署模式切换、生产部署或凭据轮换。当前运行的是包含 `06a67ab` 的 `stackchan-foundation` LAN HTTP development mode，server 绑定 `0.0.0.0:8080`，`/api/v1/health` 返回 200，Flyway 为 v10；CoreS3 当前报告 `e33a0d4` / `motion_disabled` 心跳，生产 HTTPS-only 边界未改变。

## 下一步操作

保持当前 `06a67ab` LAN server 运行并继续实体语音、屏保和离线提醒补发 smoke test。设备固件已经更新为 `e33a0d4`，生产继续保持 HTTPS-only。

## 阻塞项

当前运行状态没有部署阻塞。Maven Central 网络握手可能偶发失败，但重试已成功且现有服务健康。工作区仍没有 `.env`；未来重建必须创建未跟踪的本地配置或仅在当前进程内复用既有环境值，且不得输出秘密。不得组合 `compose.lan.yaml` 与 `compose.production.yaml`，生产部署必须保持 HTTPS-only。

## 关键文件

- `compose.yaml`
- `compose.lan.yaml`
- `compose.production.yaml`
- `scripts/verify-lan-compose.ps1`

## 验证命令与最近结果

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

- [安全部署 runbook](../../runbooks/secure-deployment.md)
- [0004：LAN HTTP/WS 仅限显式编译的开发固件/profile](../decisions/0004-lan-http-development-only.md)
- [0005：生产环境保持可信代理后的 HTTPS-only 边界](../decisions/0005-secure-production-boundary.md)

## 安全与兼容性约束

- LAN HTTP 仅限局域网开发。
- 生产环境必须保持 HTTPS-only，且不得组合 `compose.lan.yaml` 与 `compose.production.yaml`。
- 不在状态文档中记录凭据或完整认证配置。
