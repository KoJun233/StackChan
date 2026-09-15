# 个人数据备份与隔离恢复验证

## 运行边界

- `postgres-backup` 使用与运行数据库相同的固定 PostgreSQL 镜像，只通过网络读取 `stackchan` 数据库。
- 备份保存到独立 `stackchan-postgres-backups` 卷；`server` 仅以只读方式挂载该卷。
- 日备份保留 7 份，周备份按 ISO 周保留 4 份。`.partial` 文件不是成功备份。
- 每次备份成功前必须完成 SHA-256 校验和一次性 PostgreSQL 恢复验证。
- 恢复验证只创建容器内临时数据目录和 Unix socket，不接受目标数据库地址，不会覆盖运行数据库。
- 网页只显示安全状态，没有恢复按钮。

## 日常运行

基础 Compose 会启动 `postgres-backup`。默认启动后执行一次，之后每 86400 秒执行一次。仅测试环境可通过未跟踪的 `.env` 调整：

```dotenv
POSTGRES_BACKUP_RUN_ON_START=true
POSTGRES_BACKUP_INTERVAL_SECONDS=86400
```

查看容器健康日志时只检查阶段和退出码，不打印 Compose 环境或数据库凭据：

```powershell
docker compose logs --tail 100 postgres-backup
```

## 手工重新验证最新备份

脚本不接收目标主机、数据库 URL 或凭据参数，只能恢复到它自己创建的一次性 PostgreSQL：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-latest-postgres-backup.ps1
```

若部署使用其他未跟踪环境文件：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-latest-postgres-backup.ps1 -EnvFile .env.production
```

成功时退出码为 0，并原子更新只包含时间、成功状态和安全失败码的 `status.json`。

## 状态与失败处理

管理端“AI 陪伴 → 对话与个人数据”显示：

- 最近备份尝试、成功和失败时间；
- 最近恢复验证时间与结果；
- 日/周成功备份数量及保留上限；
- 备份卷总占用。

状态接口不会返回卷内路径、dump、manifest、摘要、数据库版本清单、记录计数、密码或密钥。失败码只使用 `SOURCE_COUNT_FAILED`、`SCHEMA_READ_FAILED`、`VERSION_READ_FAILED`、`DUMP_FAILED` 和 `RESTORE_VERIFICATION_FAILED` 等固定值。

失败后先确认 PostgreSQL 和备份容器是否运行、独立卷是否可写、空间是否充足。不要把 dump 复制到仓库，不要在终端打印容器环境。需要灾难恢复到正式数据库时，先停止写入并由管理员制定单独恢复窗口；本任务脚本刻意不提供正式库覆盖能力。

## 发布验证

陪伴 V51 增加一次性的全栈发布快照与隔离演练，使用 Windows PowerShell 7：

```powershell
./scripts/verify-companion-full-restore.ps1
```

脚本从已存在的本机 server/backup 容器取配置与命名卷，制作新数据库备份，再保存到独立 `stackchan-release-时间戳` 卷：数据库 dump/manifest、Skill 归档与校验和、Windows DPAPI 加密的运行配置（包含原加密主密钥和设备凭据签名密钥）及旧镜像标识。终端只返回验证结果，不输出认证材料。运行配置不是明文 `.env`，也不提交到 Git。

演练只创建独立数据库和 Skill 卷，使用无外连、无公开端口的内部网络；按正常应用配置启动候选，验证 V51、记录计数、Skill 归档恢复、现有加密字段解密、原管理员记录保留、临时测试管理员登录及页面壳。测试账号只存在于隔离副本。完成后清理本次副本，保留发布快照；异常中断时先核对 `stackchan-restore-时间戳` 资源归属，再清理同一批临时资源，不能删除源卷。

普通运行中执行该脚本不保证数据库与 Skill 文件属于同一停写时点。正式发布使用 `scripts/deploy-companion-v51.ps1` 先停止 server，成功完成快照和演练后才替换镜像。此脚本仅适用于已有 LAN 部署，保持 PostgreSQL、Redis、备份服务和数据卷；凭据只在进程内复用。若备份门槛失败，会重启旧服务；开始替换后失败不自动回退旧应用或覆盖数据库。

边界：DPAPI 仅由原 Windows 用户/机器解密，这份本地快照不是跨机器灾备。迁移到新机器之前，必须通过独立安全保管渠道准备原加密主密钥、设备签名密钥及运行配置，并保存对应镜像；不能只复制 dump 后重新生成密钥。独立卷仍位于同一台 Docker 主机，不能抵御整机或磁盘丢失。恢复原管理员记录及测试登录成功，也不替代用户用自己的当前密码登录确认。设备应沿用原身份和服务地址重连，不清 NVS、不重新配对、不刷写固件。

```powershell
docker build -f ops/postgres-backup/Dockerfile -t stackchan-postgres-backup:verify .
docker compose --env-file .env -f compose.yaml -f compose.lan.yaml config --quiet
```

Docker 镜像构建阶段会运行轮转测试，覆盖月初、跨年、同周重复执行和失败/部分文件不进入成功集合。发布后再运行一次手工恢复验证，并从页面确认恢复结果和保留数量。
