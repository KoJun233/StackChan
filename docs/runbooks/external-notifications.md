# 外部通知集成运行手册

## 创建与交付令牌

1. 管理员在“外部通知”页面创建集成并确认固定目标设备。
2. 点击“签发令牌”，按需设置到期时间。
3. 只在当前结果中复制令牌并写入调用方的秘密存储；关闭结果后无法再次查看。
4. 不把令牌写入仓库、Codex Skill、MCP Tool 描述、Compose、URL、日志或支持工单。

轮换时先签发新令牌并更新调用方，验证创建/查询成功后撤销旧令牌。撤销立即阻止后续 REST/MCP 请求，但不会删除已经入队的通知。

## 删除与清理

- 删除单条通知只影响管理队列/历史，不向外部来源发送回调，也不允许外部 Bearer 权限调用。
- 删除集成会永久删除全部令牌以及该集成的通知队列/历史；操作前先确认调用方已经停止使用对应令牌。
- `DISPATCHED` 表示设备命令已发出且仍在等待 ACK。此时删除通知或所属集成会返回 409；等待状态进入 `DELIVERED/FAILED/CANCELLED`，或由超时恢复为 `PENDING` 后再操作。
- 删除不可恢复。若只是临时阻止新通知，应停用集成而不是删除。

## REST smoke test

以下示例中的值必须通过本地秘密变量注入，不在终端历史或证据中保存明文：

```powershell
$headers = @{
  Authorization = "Bearer $env:STACKCHAN_NOTIFICATION_TOKEN"
  'Idempotency-Key' = [guid]::NewGuid().ToString()
}
$body = @{ content = '任务已完成，可以查看结果。' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$env:STACKCHAN_BASE_URL/api/v1/external/notifications" -Headers $headers -ContentType 'application/json' -Body $body
```

随后使用返回 ID 查询状态，直到 `DELIVERED` 或安全终态。不要在验收记录中保存正文或 Authorization 头。

## MCP 配置

将 Streamable HTTP 地址设置为 `<baseUrl>/mcp/notifications`，认证使用同一 Bearer 令牌。发现结果必须只有 `push_notification` 与 `get_notification_status`。若出现其他 Tool，停止接入并检查服务端配置。

## 故障处理

- 401：令牌无效、过期或已撤销；不要重试旧令牌，联系管理员轮换。
- 403：集成已停用；确认目标设备和业务来源后由管理员重新启用。
- 409：幂等键已对应不同正文，或管理员尝试删除正在播报的通知/集成；前者生成新键，后者等待设备 ACK 或超时恢复。
- 429：超过每分钟 30 次或 100 条未完成上限；等待现有通知完成/过期后重试。
- 长时间 `PENDING`：检查免打扰、设备在线、活动语音和过期时间。外部通知离线时不会采用普通提醒的跳过策略。
- `FAILED/EXPIRED`：只依据安全失败码处理；不得采集供应商完整异常、音频或播报正文。

## 生产边界

公网必须通过可信 HTTPS 反向代理，代理访问日志需删除 Authorization。不得组合 `compose.lan.yaml` 与 `compose.production.yaml`，也不得为了外部 Agent 暴露管理员会话、设备 JWT 或数据库端口。
