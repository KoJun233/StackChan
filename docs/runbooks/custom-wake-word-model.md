# ESP-SR 内置唤醒词切换与安全 OTA

本流程只允许管理员从项目锁定的 ESP-SR 2.4.6 内置模型目录选择唤醒词。页面不接受任意文本或模型文件，也不会调用第三方生成服务。

## 前置条件

- 设备已经运行包含永久 `model`、`model_a`、`model_b` 三个 1 MiB 模型槽的固件。
- 当前 CoreS3 的 `0398073` LAN HTTP Quad 镜像已满足此前置条件，并已链接 WakeNet9、WakeNet9l 和 WakeNet9s 运行接口。
- 服务端容器包含 `/app/wakenet-models`；正式 Dockerfile 从 `server/wakenet-models` 复制所需的 13 组原样官方文件。目录 README 记录 ESP-SR 版本、上游提交和组件哈希，LICENSE 随模型发布。
- 生产环境保持 HTTPS-only；LAN HTTP 只用于受信任局域网开发。

## 当前下拉目录

| 页面短语 | ESP-SR 模型名 |
| --- | --- |
| Hi, Stack Chan | `wn9l_histackchan_tts3` |
| 小峰小峰 | `wn9_xiao3feng1xiao3feng1_tts3` |
| 小爱同学 | `wn9l_xiaoaitongxue` |
| 你好小智 | `wn9l_nihaoxiaozhi_tts3` |
| 你好星宝 | `wn9l_ni3hao3xing1bao3_tts3` |
| Hi, 乐鑫 | `wn9_hilexin` |
| Hi, ESP | `wn9_hiesp` |
| Alexa | `wn9_alexa` |
| Jarvis | `wn9_jarvis_tts` |
| Computer | `wn9_computer_tts` |
| Hey Gigi | `wn9l_heygigi` |
| Bonjour ESP | `wn9l_fr_bonjouresp_tts3` |
| こんにちは ESP | `wn9l_ja_konnichihaesp_tts3` |

目录是服务端固定白名单，不根据请求中的路径或文件动态扩展。新增选项前必须确认模型存在于锁定组件、打包后不超过 1 MiB，并由当前固件的 WakeNet 接口成功创建。

## 页面操作

1. 打开“语音配置”中的“乐鑫内置唤醒词”。
2. 选择目标机器人和唤醒短语。
3. 点击“切换并安装”。该操作会让机器人下载模型并重启；仅打开页面不会触发切换。
4. 页面每两秒刷新最新任务。机器人离线时任务停留在 `READY`，重连后自动发送。
5. `INSTALLED` 表示新模型已创建 WakeNet 并通过启动健康确认；`ROLLED_BACK` 表示设备已恢复上一槽；`FAILED` 需要按失败码排查。

同一机器人同一时间只允许一个 `READY` 或 `INSTALLING` 任务。服务端现场打包所选官方模型和 `wn9l_histackchan_tts3` 出厂回退模型；选择出厂模型时只打包该模型。

## 设备安装与回退

设备只接受任务 ID、模型名、SHA-256 和大小组成的严格 `install_wake_model` 命令。下载地址由已配置的同源服务地址与任务 ID 本地拼接，命令不能指定第三方 URL。

设备把制品写入非活动 OTA 槽，校验长度、SHA-256、包结构、目标模型和出厂回退后才保存 pending 状态并重启。以下任一情况会恢复上一槽并再次重启：

- 目标模型缺失或只能加载包内回退模型；
- WakeNet 接口、实例、16 kHz 单声道格式或监听缓冲区无效；
- 启动后 20 秒没有完成健康确认；
- pending 状态在未确认前发生第二次启动。

安装或回退结果持久保存在 NVS，并在每次 WebSocket 重连补报一次。服务端按任务、设备、模型名和 SHA-256 幂等接收。

## 失败码

| 失败码 | 含义 |
| --- | --- |
| `wake_word_model_catalog_unavailable` | 服务端缺少锁定的内置模型目录或目录内容无法安全打包。 |
| `invalid_wake_word_model_job` | 设备、模型白名单项无效，或该设备已有活动任务。 |
| `device_install_rejected` | 设备下载、写入或校验失败。 |
| `device_install_timeout` | 设备接受命令后十分钟仍未补报结果。 |
| `device_rollback` | 新模型启动健康检查失败，设备已恢复上一槽。 |
| `feature_retired` | V13 迁移终止了旧版任意短语或上传任务。 |

## 验证

从仓库根目录运行：

```powershell
& 'E:\maven-3.9.16\bin\mvn.cmd' -f server\pom.xml '-Dtest=*WakeWord*,DeviceWebSocketHandlerTest' test
pnpm --filter @stackchan/console exec vitest run src/api/modules/wakeWords.test.ts src/views/settings/speech/index.test.ts
pnpm --filter @stackchan/console run typecheck
pnpm --filter @stackchan/console run build
```

`EspSrWakeWordModelCatalogTest` 会对固定目录中的每个选项执行真实文件打包、结构校验和 1 MiB 容量检查。真机验收需由管理员主动选择一个非当前模型，并确认 `READY -> INSTALLING -> INSTALLED`、目标短语可唤醒、回复后恢复监听且 `motion_disabled` 不变。自动回退测试应使用受控故障方法，不能上传任意模型文件。

本 runbook 不授权刷写、切换部署模式、推送代码或自动提交模型切换任务。
