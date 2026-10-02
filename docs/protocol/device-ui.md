# 设备屏幕确认与日常设置协议

- 文档状态：REFERENCE
- 协议版本：1
- 任务：DEVICE-008
- 数据库迁移：V56

## 能力与兼容

设备完成中文字体、卡片显示、独立触摸归属及 HTTP 操作初始化后，在既有认证 WebSocket 上报严格的三个字段：

```json
{"type":"device_ui_capabilities","sequence":1,"version":1}
```

`sequence` 使用连接内既有递增序列。未知版本、字段、重复字段和旧连接事件被拒绝。每次新连接将能力和卡片可见回执清零；旧固件不发送此事件，继续使用语音复述/确认。

新连接仅在收到能力后使用七字段语音表情：

```json
{"type":"configure_expression","command_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","theme_color":"#FF4FA3","emotion":"HAPPY","intensity":"MEDIUM","duration_seconds":10,"turn_id":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"}
```

`turn_id` 为对应服务端语音回合的规范 UUID。未协商设备仍收到原六字段命令；主题同步、工作情绪等非语音表情也保持六字段。情绪枚举和 5–15 秒范围保持现有协议。

## 卡片通知与读取

下行通知只携带固定提案 ID，完整内容不进入 1024 字节 WebSocket 接收缓冲：

```json
{"type":"device_confirmation_available","proposal_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}
```

所有 HTTP 路径都以 `/api/v1/device/ui` 为前缀，用既有设备 Bearer JWT 认证，服务端从令牌确定设备。请求不接受设备、伙伴、会话、回合或替代内容字段。所有成功和错误响应均为 UTF-8 JSON、`Cache-Control: no-store`，完整响应最大 8192 字节；超限返回失败，不能截断后让用户确认。

`GET /confirmations/{proposal_id}` 返回：

```json
{"proposal_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","action_label":"新增待办","title":"新增待办","content":"整理会议材料","time_label":"2026-10-03 15:00 Asia/Shanghai","role_name":"StackChan","expires_at":"2026-10-02T12:00:00Z","expires_in_seconds":120,"status":"PENDING","result_message":""}
```

所有文字字段非 null；`title` 至多 200 字符、`content` 至多 2000 字符。2000 汉字的记忆卡片可完整读取并滚动；普通提醒仍遵守现有的 1000 字符内容限制。时间由固定时刻和时区格式化；相对操作显示分钟数。`expires_in_seconds` 由服务器时钟计算，取值 0–120，设备用本地单调时钟落实剩余时限；`expires_at` 保留 ISO UTC 原始截止时间。

卡片实际完成渲染后调用 `POST /confirmations/{proposal_id}/shown`，无请求体，返回：

```json
{"proposal_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","status":"SHOWN"}
```

回执必须属于当前认证连接上实际提供的同一个卡片且尚为 PENDING。服务端等待可见回执至多 1200 ms：只有成功才把完整复述替换为“请确认屏幕上的内容”；能力缺失、断线、拉取失败或回执超时均保留原复述。回执期间不持有数据库事务或连接锁。

`POST /confirmations/{proposal_id}` 的请求体严格为一个字段：

```json
{"action":"CONFIRM"}
```

或者 `{"action":"CANCEL"}`。确认必须已有渲染回执；取消不需要该回执。响应与原卡片字段一致，附带实际 `status` 和短 `result_message`。状态为 PENDING、EXECUTING、EXECUTED、CANCELLED、EXPIRED 或 FAILED；重复点击不重新执行，失败和过期不会变为取消。断线退休的旧卡片拒绝操作，重新连接必须重新提供卡片和获取回执。

所有动作绑定原提案 ID、设备、当前伙伴 ID、原设备语音会话和来源回合，TTL 两分钟。待办完成固定原标题、更新时刻、截止时间和时区，执行时锁对象并逐项核对；伙伴切换固定目标 ID 和名称，不按确认时最新同名伙伴选择；同名伙伴必须消歧。原伙伴名称作为显示快照持久化。V56 同时记录设备当前伙伴的 `consent_epoch` UUID；实际切换伙伴时更新，提案保存来源标识，切走再切回也不能恢复旧同意。原会话替换、伙伴切换、对象编辑/删除/已完成、目标伙伴改名/归档和过期均不能复用旧同意。

V56 安全退休旧的未固定目标待办完成/伙伴切换 pending 提案，其余旧语音提案继续兼容。确认执行保留其他语音回合及已派送提醒的 busy 门禁；只允许排除本次明确确认自身的语音回合，不能移除整体门禁。

卡片显示期间暂停唤醒和采音；服务端也拒绝对仍可见的 pending 卡片提交新语音。停止提示音只停止当前声音；卡片的 CANCEL 才撤销操作。

## 控制中心

2026-10-04显示迭代不改服务端协议：上滑进入两页2×2图标，左右翻页、主页下滑退出，独立配置页大返回按钮；滑块松手才发送一次设置请求。设置请求与页面导航独立，保存/失败回执归属对应配置，固定伙伴ID不随角色排序变化；断线使未完成请求进入显式重试。

`GET /state` 返回服务端当前事实：

```json
{"version":1,"volume_percent":50,"night_mode":false,"quiet_today":false,"workday_state":"OFF","rest_pending":false,"role":{"id":"00000000-0000-0000-0000-000000000001","name":"StackChan"},"roles":[{"id":"00000000-0000-0000-0000-000000000001","name":"StackChan"}],"roles_truncated":false,"pending_confirmation_id":null}
```

工作状态枚举为 OFF、STARTING、ACTIVE_PRESENT、ACTIVE_ABSENT、REST_PROMPTED、RESTING、SKIPPED_FOR_DAY。可选伙伴只包含未归档对象，最多 32 项且受实际 JSON 字节预算限制；`roles_truncated=true` 明确说明其余伙伴须在管理控制台选择，不能把局部列表称为全部伙伴。

`POST /settings` 每次仅接受一个下列字段，拒绝未知、重复、混合字段及非规范 UUID：

| 字段 | 值 |
| --- | --- |
| `volume_percent` | 整数 0–100 |
| `night_mode` | boolean |
| `quiet_today` | boolean；true 今日安静，false 恢复 |
| `workday_action` | START / STOP |
| `rest_action` | START_REST / SNOOZE / SKIP_FOR_DAY |
| `role_id` | 已存在且未归档伙伴 UUID；busy 时拒绝 |

例如 `{"volume_percent":65}`。成功返回 `{"result":"SAVED","state":{...}}`；设置修改后重新读取服务端值，音量和夜间模式使用设备锁和设置行锁合并，不覆盖同一设备其他设置。显示 pending 卡片期间设置操作返回冲突。

输入模式、亮度、摄像头跟随开关等本地设置由固件单独管理。接口不提供舵机许可/校准、凭据、OTA、任意运动、图像或人脸坐标。

当前设备的服务端状态变化后，仅对已协商版本 1 的连接发送一个字段的通知：

```json
{"type":"device_ui_state_changed"}
```

通知不携带可直接执行或覆盖缓存的状态值。设备先停止依赖旧缓存的本地动作，再拉取 `GET /state`。服务端在事务提交后通知今日安静/恢复、音量/夜间显示、伙伴切换/归档、工作和休息状态变化；回滚不通知，工作日定时器普通 tick 不通知。固件另以低频刷新和缓存时限处理遗漏通知及自然到期，过期缓存不能作为跟随许可。

## 错误与恢复

错误响应为 `{"code":"...","message":"操作未保存或已失效，请刷新后重试。"}`，不包含异常、秘密或认证载荷。401 表示 JWT 无效；400 表示字段/类型/范围无效；404 表示卡片或目标不属于当前设备/伙伴/会话或已经退休；409 表示未显示、busy、对象变化或超出预算；503 表示当前不能安全提供结果。动作执行中的领域校验失败以卡片 FAILED 返回并保存审计；数据库/服务不可用则返回安全错误，设备不能显示“已保存”。

## 软件验证

`python scripts/test-device-ui-protocol.py` 在只读、断网 GCC 容器内编译真实 `device_ui_protocol.c`、`strict_json.c` 与托管 cJSON 源码，并执行 ASan/UBSan；2026-10-02 最新结果为 6 组、170 项检查通过。覆盖 SAVED 设置回执、32 个伙伴及重复 ID、固定卡片 ID、2000 汉字/6000 字节、状态与单调截止数据、8192 字节上限、重复字段/解码 NUL/深层 JSON/尾随内容，以及严格两字段卡片通知/SHOWN 回执和单字段状态通知。

服务端真实 PostgreSQL 和认证 HTTP 验证命令、最新完整回归证据见[服务端工作流](../project/status/server.md)。这些结果仅验证软件；V56 尚未部署，字体可读性、触摸区域、物理屏幕回执与实体体验仍需候选现场验证。
