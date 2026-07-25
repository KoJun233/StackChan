# 部署工作流

- 状态：STABLE
- 最后更新：2026-07-26
- 当前分支：`codex/int-001-voice-turn-diagnostics`
- 基准提交：`af65290`
- 最后验证提交：`af65290`
- 当前运行态代码：已部署的 ESP-SR 内置唤醒词目录版本（Flyway V13），不含 INT-001。

## 当前目标

保持现有 Compose 模式和生产 HTTPS-only 边界，把锁定的 ESP-SR 2.4.6 内置模型目录随服务端镜像发布。

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
- 当前任务移除生成器可执行文件、超时和上传配置；正式镜像复制锁定模型目录，Compose 固定设置 `COMPANION_WAKE_MODEL_CATALOG_DIRECTORY=/app/wakenet-models`，不新增凭据。
- 2026-07-26 已使用仓库正式多阶段 Dockerfile 构建并只替换同一 `stackchan-foundation` server；PostgreSQL、Redis、外部卷、LAN HTTP mode 和生产 HTTPS-only 边界均未改变。
- 部署前已从旧 server 容器制作本地回退镜像 `stackchan-foundation-server:rollback-06a67ab`；临时导出文件已删除，未输出或写入凭据。
- 首次新镜像运行暴露 READY 调度悲观锁查询缺少事务边界；修复、针对测试和重新构建后再次替换 server，持续日志观察未再出现事务异常。
- 本地上传增量已使用仓库正式多阶段 Dockerfile 重建并只替换同一 LAN server；部署前给原镜像添加本地回退标签 `stackchan-foundation-server:rollback-9b61339`，没有把容器运行凭据写入镜像或仓库。
- Flyway 已从 V11 升至 V12；PostgreSQL、Redis、外部卷、端口、LAN HTTP mode 和生产 HTTPS-only 边界均未改变。
- 最终镜像替换时曾误用基础 Compose，导致 server 暂时只发布 `127.0.0.1:8080`，网页本机可用但 CoreS3 离线；已改回 `compose.yaml + compose.lan.yaml`，恢复 `0.0.0.0:8080` 和既有 LAN development mode。

## 正在进行

当前 `stackchan-foundation` 已用正式 LAN overlay 运行内置目录版本，Flyway 为 V13，容器包含 13 组模型并发布 `0.0.0.0:8080`。CoreS3 运行 `0398073`、持续报告 `motion_disabled`，且“小峰小峰”任务已完成安装；没有凭据轮换或固件刷写，生产 HTTPS-only 边界不变。

## 下一步操作

用户实体呼叫“小峰小峰”，确认安装后的 WakeNet9 模型能够稳定唤醒；如未命中，再采集不含秘密的串口 WakeNet 初始化和检测日志。

## 阻塞项

没有模型文件、生成器、设备连接或部署模式阻塞。工作区仍没有 `.env`；后续 LAN 重建必须显式同时传入 `compose.yaml` 和 `compose.lan.yaml`，不得输出秘密、组合 LAN 与 production profile 或降低生产 HTTPS-only 边界。

## 关键文件

- `compose.yaml`
- `compose.lan.yaml`
- `compose.production.yaml`
- `scripts/verify-lan-compose.ps1`

## 验证命令与最近结果

- 当前任务的 LAN Compose 静态验证通过；仅使用当前进程内的非秘密占位值，代码实现未触碰运行中的容器、卷、端口或凭据。
- INT-001 最新 `master` 合并回归再次完成 LAN Compose 静态展开：基础模式只绑定 `127.0.0.1:8080`，LAN overlay 绑定 `0.0.0.0:8080`，PostgreSQL 与 Redis 均未发布主机端口。未重建或替换运行中服务。
- 2026-07-26 本任务 LAN 部署：`/api/v1/health` 和网页根地址均返回 200，Flyway 为 `11|true`，容器内包含 `speech-DFhfjNNt.js`；调度事务修复后日志中未再出现 `Query requires transaction be in progress`，CoreS3 心跳恢复为 `e33a0d4` / `motion_disabled`。
- 2026-07-26 本地上传增量部署：`/api/v1/health` 和网页根地址均返回 200，Flyway 为 `12|true`，容器内 `speech-CKv17cKU.js` 包含“在线生成/上传模型包/上传并安装/本地上传”；服务端近两分钟错误数为 0，CoreS3 心跳保持 `0398073` / `motion_disabled`。未触发模型 OTA、刷写或第三方提交。
- 2026-07-26 内置目录部署：部署前保留 `stackchan-foundation-server:rollback-upload-v12`；正式 Dockerfile 构建成功并只替换 server。健康和网页根地址均为 200，未登录目录 API 返回 401，Flyway `13|true`，容器模型目录为 13 组，近两分钟错误数为 0；CoreS3 心跳保持 `0398073` / `motion_disabled`。未触发模型 OTA 或刷写。
- 2026-07-26 首次内置词真机切换：基础 Compose 的 loopback 监听使任务暂留 `READY`；恢复 `compose.lan.yaml` 后 CoreS3 自动重连，任务 `d9fcd60f-51cf-4e5d-a12b-7cb7fbcfb5da` 完成 `INSTALLED`，目标为 `wn9_xiao3feng1xiao3feng1_tts3`，设备继续报告 `0398073` / `motion_disabled`。未重新刷写固件。
- 2026-07-26 CoreS3 完成 `0398073` LAN HTTP Quad 三槽引导，数据库收到 `0398073` / `motion_disabled` 心跳；Compose 模式、卷、端口和生产 HTTPS-only 边界未改变。
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
- [0015：运行时生成并安全 OTA 自定义唤醒模型](../decisions/0015-runtime-wake-model-generation-and-ota.md)
- [0016：唤醒词仅从 ESP-SR 内置模型目录选择并安全 OTA](../decisions/0016-built-in-esp-sr-wake-model-catalog.md)

## 安全与兼容性约束

- LAN HTTP 仅限局域网开发。
- 生产环境必须保持 HTTPS-only，且不得组合 `compose.lan.yaml` 与 `compose.production.yaml`。
- 不在状态文档中记录凭据或完整认证配置。
