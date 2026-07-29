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

```powershell
docker build -f ops/postgres-backup/Dockerfile -t stackchan-postgres-backup:verify .
docker compose --env-file .env -f compose.yaml -f compose.lan.yaml config --quiet
```

Docker 镜像构建阶段会运行轮转测试，覆盖月初、跨年、同周重复执行和失败/部分文件不进入成功集合。发布后再运行一次手工恢复验证，并从页面确认恢复结果和保留数量。
