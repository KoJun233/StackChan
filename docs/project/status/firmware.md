# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r01-natural-voice-20261013`
- 基准提交：`989b3d9`
- 最后验证提交：`989b3d9`

## 当前目标

R01 端点、本地状态与首次成功 codec 写入证据；取消优先、不改变采音/连续限额，交付未刷写双 profile。

## 已完成

实际采音端点先接受 PROCESSING 再等上传完成；新增可选 DEVICE_MONOTONIC、反馈与限时原因，空采音不上传。等待开口不消耗八秒讲话，120 秒只限制新启动，合法已启动讲话可完成；PTT 不续听。

首次成功写入才报播放开始，codec 锁后/每次写入前复查取消。回调只保存固定通知并报阶段；UI 任务核对当前 turn/取消后应用 SPEAKING，不在音频循环等 UI 锁。来源/时间不证明 LCD 完成或声学首声。

LAN 3,141,296 B，3 MiB 槽余量 4,432 B（门槛 4,384，余 48）；protocol 214,048 B。新增队列 192 B + 固定通知 56 B = 248 B 静态内存；原分区、srmodels、相机/运动开关保持。

## 正在进行

软件完成，等待审核；实体与安装待验。

最终完整 SHA、测试源码指纹、61 SQL/Flyway、生产 image/JAR/全部控制台资源及双固件六制品见本工作树 `.tools/r01/candidate-receipt.json`。989b3d9 为当前祖先元数据，新增代码成绩以工作树回归和最终候选收据为准。实现、状态、交接与 R00 合成一个中文任务提交；用户已于 2026-10-15 明确授权推送本任务分支；推送与远端 SHA 核对结果见 `.tools/r01/push-receipt.json`。PR、人工审核与合并由用户执行。

## 下一步操作

后续任何固件改动先复验 4,384 B 容量门槛和栈；用户另行授权刷写时仅使用匹配收据、profile、设备与 NVS 策略，再采集十次停止和三十短聊。

## 阻塞项

无软件阻塞。本轮分支推送已授权，刷写或安装尚未授权；实体样本不足保持 UNKNOWN。缺 V57 的既有 V60 库仍默认拒绝升级，需另行兼容方案。

## 关键文件

实现路径与 FR/AC 对照见下方交付记录，最终源码指纹见收据。

## 验证命令与最近结果

实际源码 input-timing/codec/协议回归、原十二类宿主、七类受影响复验及完整 LVGL ASan/UBSan 通过。双 profile 与 voice/transport/provisioning/device-ui/display 五栈 gate 通过；voice 4,832、upload 2,256、playback 160、transport 9,024、provisioning 11,488 B。

最终版本绑定 HEAD 的双构建/十二制品见 `.tools/r01/firmware-candidate-receipt.json`；旧 precommit 4,528 B 余量已被当前 4,432 B 替代。真实 heap/PSRAM/高水位、LCD 可见反馈与触摸静音待验。

## 相关设计、计划和决策

[R01 交付与 FR/AC](../baselines/companion-r01-natural-voice.md)、[R00 历史基线](../baselines/companion-v1-baseline.md)、[R01 规格](../../requirements/companion-v1/R01-natural-voice.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[ADR0077](../decisions/0077-measured-private-natural-voice.md)、[实体测量](../../runbooks/natural-voice-quality.md)、[备份恢复](../../runbooks/personal-data-backup.md)。

## 安全与兼容性约束

不运行真实模型/Bark、不刷写、安装、改部署模式、轮换凭据或创建 PR；仅按本次授权推送当前任务分支。原用户工作树保持；实体录音/谈话/认证载荷不提交，LAN HTTP 仅局域网开发。
