# 语音后的身体动作许可修复

- 文档状态：ACTIVE
- 日期：2026-10-01
- 任务分支：`codex/body-motion-acceptance-20260929`
- 主线基准：`0914e43`，沿用当前 BODY-002 验收任务分支
- 当前实机：`1b46c28`；保留 NVS 应用 OTA 已安装，普通说话后的点头设备结果和用户实体反馈通过，低频自动动作已恢复
- 决策：[ADR 0042](../project/decisions/0042-local-first-k151-body-safety.md)

## 已确认的问题

2026-09-29 现场显式启用舵机并打开自动动作后，用户确认头部安稳。22:36:33 设备一次唤醒以 `NO_SPEECH` 结束，随后舵机回到 `DISABLED`。生产源码的 `begin_turn` 和 `SPEAK_REMINDER` 无条件调用永久停止，因此即使头部本来完全没动，也撤销了管理员的许可。服务端自动动作开关仍开，但要求设备处于 `motion_armed`，不能再下发动作。2026-10-01 复核当前实机在线、校准保留、动作禁用；近两天有五个完整语音回合和一条已投递主动开场，未读取对话正文。9 月 30 日主动开场最终投递时间为 12:02:39（Asia/Shanghai）。这些记录只能证明链路，不能代替体验反馈。

## 修复行为

音频开始时先在本地安全状态机获取阻止动作的计数，再建立语音回合或入队播报。头部空闲时保留当前许可；音频期间即使调用者携带过期的空闲 guard，也拒绝新动作和重新启用。用户语音和多个排队提醒可重叠，只有每个音频使用者结束后才解除临时阻止。提醒入队失败立即释放；播放完成、失败或取消均释放；连接销毁时清理未播放队列的计数。

若音频取得门禁时动作已入队/正在执行，设备调用原本地停止回调，报告 `VOICE_STOP` 并回到 `DISABLED`。音频结束只减少计数，绝不启用舵机或重放动作。断线、显式停止、重启、顶部触摸、反馈故障、超时和能力缺失继续禁用。五套轨迹、限位、速度、反馈检查、协议、数据库和服务端不改变；未修改音频服务、ASR/TTS 配置或模型计费。

## 验证与部署门槛

执行 `python scripts/test-body-audio-safety.py`：使用实际 `firmware/main/safety_state.c`，只将日志和 FreeRTOS 临界区替换为宿主 pthread。覆盖空闲语音保留许可、过期 guard 拒绝、重叠音频计数、实际中断永久禁用、故障/断线/显式停止后不复活，以及 500 次双线程音频与动作竞争。该测试不接触设备、不调用音频服务。

随后完成 ESP-IDF 5.5.5 的 LAN HTTP Quad 与 protocol 构建、栈预算和文档检查；候选必须记录版本、SHA-256 和分区余量后，才可通过保留 NVS 的应用 OTA 安装。安装后默认禁用，无实体转动；先检查在线、校准、队列与门禁日志，再在用户就位时只做有针对性的实体复测。软件测试不能代替真实运动和实际语音抢占观察。

宿主六组测试和 500 次竞争通过；两套 ESP-IDF 5.5.5 的提交绑定构建通过，LAN 镜像为 `0x195530`（47% 分区余量）、protocol 为 `0x3a150`（92% 余量）。初次工作树构建仅作为编译检查，安装使用下方绑定候选。三组栈预算脚本回归及实际制品栈预算检查全部通过；`pnpm docs:check`、`pnpm docs:check:test`（7/7）和 `git diff --check` 通过。用户表示未注意到这两天自然使用和主动开场，体验仍为未观察到。自然陪伴价值和十四天体验尚未完成；本轮目标继续保持 ACTIVE。

安装准备已运行 `scripts/configure-body-motion-natural-use.ps1 -Mode Stop`，自动开关返回 `false`，设备仍为 `DISABLED`；临时管理员按脚本清理。停止路径首次运行通过，解除启动记录中该路径未执行的限制。安装完成后先保持禁用，待用户重新确认在旁再启用；9 月 29 日的现场确认不能作为今天的机械空间证据。

## 已安装制品与现场复测

| 项目 | 记录 |
| --- | --- |
| 构建版本 | `1b46c28`，干净提交绑定候选；后续今日安静服务端修复、辅助脚本及文档不改变固件源文件，最终任务提交的固件必须与该候选完全一致 |
| 配置 | ESP-IDF 5.5.5、LAN HTTP Quad；沿用现有 LAN 开发部署，不切换模式 |
| LAN 应用大小 | 1,660,208 字节，最小应用分区余量 47% |
| LAN SHA-256 | `B278FC935A20DB7E425E921B5753398016E52B4605FD3756279F5610BB3D8CBE` |
| protocol 应用大小 | 237,904 字节，最小应用分区余量 92% |
| protocol SHA-256 | `DA58A7516275F9762D2A6B18E0CB48E855E02F48ADD1F6184207811AE26440B5` |
| 应用 OTA | `476710d6-5e50-4254-b7d9-54df59e2c6f2`，`INSTALLED`，2026-10-01 00:37:26（Asia/Shanghai） |
| 持久数据 | 未擦除 NVS；Wi-Fi、凭据和原校准保留 |
| 安装后只读 | 在线、命令可用、已校准、`DISABLED`、自动开关关闭；冷启动反馈能力暂未启用为正常现象 |
| 现场显式启用 | 用户重新确认在旁及空间条件后，反馈探测通过，`motion_armed / ARMED`、`NONE`、故障计数 0 |

实际栈预算：voice 32,768 字节中已知 4,768／外部余量 28,000；upload 24,576 中已知 2,256／余量 22,320；playback 24,576 中已知 160／余量 24,416；transport 32,768 中已知 8,976／余量 23,792；provisioning 16,384 中已知 7,680／余量 8,704。静态预算不推定运行时最坏栈占用。

用户按普通方式说完一句话，语音回合 `846fc35e-0e82-4d2a-bf46-65bd246cdb2d` 为 `COMPLETED`，设备先后上报 `PLAYBACK_COMPLETED`、`LISTENING_RESUMED`。随后续聊回合 `dccdf32a-07f5-4a61-8634-4baca9bf10b0` 为 `ASR_UNAVAILABLE`；本轮未改识别配置，也不将该失败算作语音全链路通过。两个回合后设备仍为 `ARMED / NONE`、故障计数 0。

只执行一次 `NOD_SMALL`，命令 `1ad5055f-afdc-4d23-a594-a421bb9c2d22` 为 `COMPLETED / NONE`，结束后仍报告 `ARMED`、反馈有效，用户答复“正常”。随后按先前自然使用授权执行 `configure-body-motion-natural-use.ps1 -Mode Start -FirmwareVersion 1b46c28`，自动开关恢复 `true`；只读复核 `ARMED / NONE`、故障计数 0、校准和反馈有效、临时管理员 0、服务健康 `ok`，未额外发送动作。

下一条精确操作：将本任务实现、状态和交接压缩为一个中文任务提交，验证固件源码与构建候选完全相同后仅推送任务分支；用户创建 PR、审核并合并。后续按平常需求使用，记录实际注意到的主动回应与自然动作；不重复五模板已通过的现场观察。本轮不验证运行中真正语音中断，也不把 `stop_audio` 历史结果代替该证据。

随后已完成[今日安静语音范围修复与发布](device-quiet-voice-20261001.md)，服务器更新到 `device-quiet-voice-20261001`，固件源码及安装制品保持不变。服务器断线使舵机按规则禁用，用户新确认在旁后只恢复反馈探测和低频自动开关；收尾 `ARMED / NONE`、故障计数 0，无新增转动测试。上方普通语音后点头结果仍为本固件的现场证据。

## 操作入口

在仓库根目录使用 PowerShell；这些脚本含临时管理员清理，不输出认证载荷。版本参数必须匹配心跳，不沿用脚本的旧默认值：

```powershell
# 只读检查当前安装版本和动作状态
& ./scripts/verify-body-motion-device.ps1 -Step Status -FirmwareVersion 1b46c28
& ./scripts/verify-body-motion-device.ps1 -Step ApiStatus -FirmwareVersion 1b46c28

# 立即关闭自动开关并显式禁用舵机；不改变主动开场
& ./scripts/configure-body-motion-natural-use.ps1 -Mode Stop -FirmwareVersion 1b46c28

# 此修复的宿主自动验证，不操作实机
python scripts/test-body-audio-safety.py
```

启用舵机仍需现场确认空间，重启、断线或实际抢占后不自动恢复；先用 `verify-body-motion-device.ps1 -Step Enable -FirmwareVersion 1b46c28` 检查反馈并等待 `ARMED`，再用 `configure-body-motion-natural-use.ps1 -Mode Start -FirmwareVersion 1b46c28` 恢复低频开关。不要重复发送动作或自动重试失败命令。立即停止动作也可用管理页“停止动作”或轻触头顶；管理员显式 `stop_audio` 会禁用动作。

本次安装由 `install-body-motion-ota.ps1` 显式传入 `CurrentVersion=d18b3cd`、`TargetVersion=1b46c28`、`ExpectedSize=1660208`、上表 SHA-256 和 `firmware/build-audio-permit-lan-http-quad/stackchan_firmware.bin` 完成严格只读预检后，加 `-Install` 执行。现在设备已是目标版本，不应重复安装此历史来源预检；后续发布须重新核对当前版本、绑定制品与授权。最终文档压缩后，用 `git diff --exit-code 1b46c28 -- firmware` 确认固件源文件一致。
