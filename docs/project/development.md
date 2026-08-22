# 开发环境与命令

## 环境

- 已安装 Java 21。
- 在当前 Windows 工作站上，Maven 位于 `E:\maven-3.9.16\bin\mvn.cmd`；其他机器可使用兼容的 Maven 3.9 可执行文件。
- Node 必须满足 `^22.22.2 || ^24.15.0 || >=26.0.0`；pnpm 遵循仓库的 `packageManager` 字段。
- Docker Compose 运行 PostgreSQL、Redis 和 server。固定的 PostgreSQL 镜像为 `postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4`。
- 固件构建固定使用 ESP-IDF 5.4.4；官方 CoreS3 BSP 4.0.0 要求 IDF 5.4 或更高，旧 5.3.x 环境不能构建当前依赖锁。刷写前必须明确确认设备和 commit。

密钥值存放在未跟踪的 `.env` 中；`.env.example` 只包含变量名称。不要在被跟踪文件、终端证据或文档中写入 `apiKey`、password、token 或 `Authorization` 的值。

## Node 与 pnpm 引导

开始文档检查前，先确认当前 shell 已能找到 Node 和 pnpm：

```powershell
node --version
pnpm --version
```

如果在此工作站的 Codex App 中缺少其中之一，使用以下临时 PowerShell 引导；它只影响当前 shell：

```powershell
$codexDeps = 'C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies'
$env:PATH = "$codexDeps\node\bin;$codexDeps\bin\fallback;$env:PATH"
node --version
pnpm docs:check
```

该 Codex 捆绑运行时当前为 v24.14.0，只可用于无依赖的文档恢复检查。前端测试、构建和开发必须使用满足仓库 `engines`（`^22.22.2 || ^24.15.0 || >=26.0.0`）的 Node 版本。其他 Agent 或机器应安装受支持的 Node，而不是削弱 `package.json` 中的版本约束。

## 命令

从仓库根目录运行服务端和控制台检查：

```powershell
& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml test
pnpm --filter @stackchan/console run test
pnpm --filter @stackchan/console run build
```

运行固件配网检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-firmware-provisioning-stack-budget.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-firmware-provisioning-stack-budget.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-firmware-voice-stack-budget.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-firmware-voice-stack-budget.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-firmware-transport-stack-budget.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-firmware-transport-stack-budget.ps1
```

运行唤醒模型包和 ESP-SR 内置目录回归检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-wake-word-model-package.ps1
& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml '-Dtest=EspSrWakeWordModelCatalogTest,WakeWordModelPackageValidatorTest' test
```

运行内置唤醒词选择与模型 OTA 的服务端针对测试：

```powershell
& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml '-Dtest=*WakeWord*,DeviceWebSocketHandlerTest' test
```

服务端只读取 `COMPANION_WAKE_MODEL_CATALOG_DIRECTORY` 指向的锁定 ESP-SR 2.4.6 模型目录；正式容器固定使用 `/app/wakenet-models`。下拉目录、三槽安装和回退验证见 [ESP-SR 内置唤醒词切换与安全 OTA](../runbooks/custom-wake-word-model.md)。

仅在受信任的 LAN 上使用 LAN 开发覆盖层：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-lan-compose.ps1
docker compose -f compose.yaml -f compose.lan.yaml up --build -d
```

在生产部署前验证生产 Compose 配置：

```powershell
docker compose -f compose.yaml -f compose.production.yaml config --quiet
```
