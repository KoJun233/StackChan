# StackChan 表情资源包 V2

V2 只承载有限生命周期 EAF 动画，不替代 V1 的八状态 PNG 包。两种包复用设备上的 `expression_a` / `expression_b` 分区，同一设备只能激活一种包；未安装、校验失败或播放结束时继续使用内置 LVGL 动态球体。

## 管理接口

管理员使用会话与 CSRF 调用：

- `POST /api/v1/expression-packs/lifecycle`：multipart 创建 V2 包。
- `GET /api/v1/expression-packs/{packId}/clips/{clipName}`：下载单个已验证 EAF，响应 `Cache-Control: no-store`。
- 既有列表、启用、停用和删除接口同时管理 V1/V2。

创建请求包含 `name`、可选 `description`，以及至少一个、最多三个文件字段：`boot_appear`、`wake`、`role_switch`。每个文件可带 `<clip>_frame_delay_ms`，缺省为 33 ms。

## 包布局

所有整数均为 little-endian：

| 偏移 | 内容 |
| --- | --- |
| `0..7` | ASCII `SCEPKG2\0` |
| `8..11` | `uint32` 格式版本，固定为 `2` |
| `12..15` | `uint32` UTF-8 JSON manifest 长度，1..16384 |
| `16..` | manifest，随后按 `boot_appear`、`wake`、`role_switch` 顺序连续存放 EAF 数据 |

manifest 固定包含 `version=2`、`kind=lifecycle_eaf`、`width=160`、`height=160` 和 `clips`。每个 clip 记录事件名、`format=eaf-rle4`、尺寸、帧数、帧间隔、相对 payload 偏移、长度和 SHA-256。服务端编译后再计算整个包 SHA-256；设备下载后先校验总长度和总摘要，再解析 manifest 和逐片段摘要。

## 有界 EAF 子集

- 固定 160×160、RLE4；不接受 JPEG、软件 JPEG 或其他帧格式。
- 每片段 1..120 帧、16..100 ms/帧、总时长不超过 5 秒、大小不超过 384 KiB。
- 整包不超过 1.5 MiB；偏移必须连续且完整覆盖 payload。
- 校验 EAF 签名、payload 长度、累加 checksum、帧表、块表、调色板和每块解码像素数。
- 服务端和固件均拒绝未知字段、未知事件、重复事件、越界整数、摘要不一致和尾随数据。

## 播放与回退

- `boot_appear`：启动后的开机行为窗口播放一次。
- `wake`：本地唤醒行为播放一次。
- `role_switch`：角色切换成功后由受认证服务端触发一次预览行为。
- 监听、处理、说话、离线、升级等高优先级状态立即停止片段并恢复原生表情。
- 片段缺失、LVGL EAF player 拒绝或播放完成时均回到原生渲染，不循环、不黑屏、不影响音频和运动安全。

新固件在严格 `expression` 诊断对象中上报 `lifecycle_clips_supported=true`。旧固件省略该字段并视为不支持；服务端不会向不支持的设备启用 V2 包。
