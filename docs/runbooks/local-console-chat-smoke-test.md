# 本地控制台与聊天验收

## 准备环境变量

从 `.env.example` 创建仅供本机使用的 `.env`，为下列变量填写高强度随机值：

- `POSTGRES_PASSWORD`
- `COMPANION_DEVICE_TOKEN_SECRET`
- `COMPANION_SECRETS_ENCRYPTION_KEY`
- `COMPANION_ADMIN_INITIAL_PASSWORD`（仅首次创建管理员时必填；数据库中已存在 `admin` 后可留空）

生产环境必须额外设置 `COMPANION_PRODUCTION=true`，且不得使用任何示例值。

## 安全门槛

`docker compose config` 只校验 Compose 变量展开；无论是否填写管理员初始密码都应成功。管理员初始密码由应用启动阶段校验：非 `development` 环境的全新数据库如果没有 `admin` 且未设置 `COMPANION_ADMIN_INITIAL_PASSWORD`，服务必须拒绝启动；已有管理员的数据库则应在该变量留空时正常重启。不要为了验证此门槛删除或重建现有数据卷。

```powershell
docker compose config
```

## 启动与验收

```powershell
docker compose config
docker compose up --build -d
docker compose ps
```

浏览器访问 `http://localhost:8080`，使用管理员账号登录后依次确认：

1. 在“AI 配置”保存并测试 `qwen3.7-plus`。
2. 在“陪伴聊天”发送一条短消息，确认流式回复完成。
3. 刷新页面，确认该会话和消息仍存在。
4. 断开 StackChan 或等待超过 90 秒，确认“设备总览”显示离线。

## 聊天失败与恢复

### 同步初始化失败

在一个独立的 PowerShell 窗口中运行以下流程。它只在当前进程内保存原环境覆盖并临时设置随机错误密钥，不修改 `.env` 或数据库，也不会打印任何密钥。不要关闭这个窗口，不要中断或跳过 `finally` 恢复；错误密钥生效期间禁止保存 AI 配置。

```powershell
$variableName = 'COMPANION_SECRETS_ENCRYPTION_KEY'
$hadProcessOverride = Test-Path "Env:$variableName"
$previousProcessOverride = if ($hadProcessOverride) {
  [Environment]::GetEnvironmentVariable($variableName, 'Process')
} else {
  $null
}

try {
  $temporaryBytes = New-Object byte[] 32
  [Security.Cryptography.RandomNumberGenerator]::Fill($temporaryBytes)
  [Environment]::SetEnvironmentVariable(
    $variableName,
    [Convert]::ToBase64String($temporaryBytes),
    'Process'
  )
  [Array]::Clear($temporaryBytes, 0, $temporaryBytes.Length)
  Clear-Variable temporaryBytes

  docker compose up -d --force-recreate server
  docker compose ps server
  $null = Read-Host '完成浏览器同步失败检查后按 Enter，立即恢复原环境并重建 server'
}
finally {
  if ($hadProcessOverride) {
    [Environment]::SetEnvironmentVariable($variableName, $previousProcessOverride, 'Process')
  } else {
    Remove-Item "Env:$variableName" -ErrorAction SilentlyContinue
  }
  Clear-Variable previousProcessOverride
  docker compose up -d --force-recreate server
  docker compose ps server
}
```

错误密钥生效后，发送一条不含隐私的测试消息，确认 `resolveForInvocation` 解密在首次 delta 前同步失败，界面仅显示安全错误，助手消息进入 `FAILED`，刷新页面后不恢复为 `STREAMING`。按 Enter 触发 `finally` 后，确认 Compose 重新读取原进程覆盖或原 `.env`，再发送新的无隐私测试消息验证恢复。模型服务网络不可达属于异步 provider failure，不得作为同步初始化失败证据。

### 服务重启恢复

1. 发起另一条测试消息，在回复仍为 `STREAMING` 时执行 `docker compose restart server`。服务恢复后刷新页面，确认遗留助手消息变为 `INTERRUPTED`，可以重新发送新消息，且没有残留的“正在思考”状态。
2. 使用 `docker compose logs server` 核对启动日志中的恢复计数；证据仅记录时间、恢复计数和终态，不记录用户消息、模型回复、系统提示词或完整请求/响应内容。

验收记录中不得包含聊天内容、API 密钥、浏览器会话凭据或完整 HTTP 头/请求体。同步初始化失败与服务重启恢复均为必测项。

停止本地环境：

```powershell
docker compose down
```
