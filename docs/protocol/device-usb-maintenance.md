# 物理USB原位服务地址维修

- 文档状态：ACTIVE
- 最后更新：2026-10-03

仅机器人物理USB接受 `{ "type": "relocate_server", "serverBaseUrl": "http://192.168.1.6:8080" }`，必须恰好两字段且符合编译模式的规范origin；不接受配对码、Wi-Fi、密钥或任意附加字段。默认HTTPS生产约束保持，HTTP仅LAN开发固件。

固件先取消声音/停止动作并等传输静默，最长20秒；旧在途续期不能保存、60秒退避可立即中断。用原设备ID与refresh在拟定origin正常认证续期，成功后原子保存新origin/短期access并重启。设备ID、refresh、Wi-Fi与校准不变；不读取/输出认证材料，不新配对或轮换。失败返回固定码并恢复传输，不保存拟定origin。

回执仅type=provisioning/status：started、complete、identity_unavailable、transport_busy、server_verification_failed、identity_save_failed、invalid_request。无完整认证载荷。`scripts/relocate-device-server.py`仅在显式--apply时发送请求，打印白名单码，等待75秒；无回执不代表成功，须检查心跳后再重试。

此维修不向远程WS、语音工具或模型开放；实际发送前遵循当前任务许可。[离线修复安装/恢复](../runbooks/device-interaction-20261002.md#2026-10-03-离线诊断与修复候选)另行明确USB应用槽许可，不能使用全Flash/清NVS命令。
