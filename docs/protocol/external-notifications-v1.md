# 外部通知 API 与 MCP v1

## 认证与传输

- 管理接口使用现有管理员会话和 CSRF。
- 外部接口与 `/mcp/notifications` 使用 `Authorization: Bearer <一次性签发令牌>`。
- 令牌固定绑定一个通知集成和一台设备。响应、日志与健康接口不返回令牌摘要或认证载荷。
- LAN development 可使用受信任局域网 HTTP；公网 production 必须使用 HTTPS。

## 管理接口

- `GET /api/v1/notification-integrations`
- `POST /api/v1/notification-integrations`
- `GET /api/v1/notification-integrations/{id}`
- `PUT /api/v1/notification-integrations/{id}`
- `DELETE /api/v1/notification-integrations/{id}`
- `POST /api/v1/notification-integrations/{id}/tokens`
- `DELETE /api/v1/notification-integrations/{id}/tokens/{tokenId}`
- `GET /api/v1/notification-integrations/notifications`
- `DELETE /api/v1/notification-integrations/notifications/{notificationId}`
- `POST /api/v1/notification-integrations/{id}:test`

创建与更新集成正文为 `{name, deviceId, roleId?, enabled}`。签发令牌可提交 `{expiresAt?}`，响应中的 `token` 只出现一次。测试播报正文为 `{content, responseActions?}`，由管理员显式触发并进入同一可靠队列。

删除集成会永久删除其令牌和全部通知队列/历史。管理员也可单独删除外部通知记录。若目标集成包含 `DISPATCHED` 通知，或目标通知本身处于 `DISPATCHED`，服务端返回 409；必须等待设备 ACK、失败或过期恢复后再删除，不能中断正在播放的设备命令。

## 外部 REST

### 创建通知

`POST /api/v1/external/notifications`

- 必需请求头：`Idempotency-Key`，长度 1–128。
- JSON：`{content, expiresInSeconds?, responseActions?}`。
- `content` 去除首尾空白后必须为 1–500 字。
- `expiresInSeconds` 省略时为 86400，允许 60–86400。
- `responseActions` 可选，只允许 `ACKNOWLEDGE/SNOOZE/COMPLETE`；省略或空数组表示单向通知。
- 新建返回 201；幂等重放返回 200 并携带原通知；同键不同正文返回 409。

### 查询状态

`GET /api/v1/external/notifications/{id}`

只允许查询当前令牌所属集成，返回：

```json
{
  "id": "uuid",
  "status": "PENDING",
  "attemptCount": 0,
  "failureCode": null,
  "createdAt": "instant",
  "updatedAt": "instant",
  "expiresAt": "instant",
  "deliveredAt": null,
  "responseActions": [],
  "response": null
}
```

互动通知产生回执后，`response` 为 `{action, snoozeMinutes?, respondedAt}`。`SNOOZE` 会让通知重新进入 `PENDING`，后续再次送达；外部调用方只通过本接口或 MCP 轮询，不配置回调地址。

公开状态为 `PENDING/DISPATCHED/DELIVERED/FAILED/EXPIRED/CANCELLED`。不存在或不属于当前集成均返回 404，避免泄露其他集成标识。

## Streamable HTTP MCP

端点：`/mcp/notifications`。服务器只声明 tools capability：

- `push_notification(content, idempotencyKey, expiresInSeconds?, responseActions?)`
- `get_notification_status(notificationId)`

两个 Tool 与 REST 使用相同认证上下文、校验、限流、幂等、队列上限和状态对象。MCP 会话建立后的每个 HTTP 请求都必须携带同一 Bearer 令牌。

## 安全失败码

- `notification_authentication_failed`
- `notification_integration_disabled`
- `notification_invalid_request`
- `notification_idempotency_conflict`
- `notification_rate_limited`
- `notification_queue_full`
- `notification_not_found`
- `notification_delivery_in_progress`
- `notification_response_unavailable`
- `notification_response_expired`

投递失败只暴露 `speech_provider_unavailable`、`invalid_speech_settings`、`device_playback_failed` 和 `notification_expired` 等受限码，不返回供应商响应或异常正文。
