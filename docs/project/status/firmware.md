# 固件工作流

- 状态：STABLE
- 最后更新：2026-10-15
- 当前分支：`codex/companion-r02-project-loop-20261015`
- 基准提交：`a961816`
- 最后验证提交：`7c22d00`

## 当前目标

保持已合入 R01 的固件行为，支持 R02 沿用现有语音入口。本轮没有固件修改、重新构建或刷写；下述容量、栈与宿主证据均为 R01@7c22d00 历史结果。

## 已完成

R01 已通过 PR #49 合入 master@a961816；旧等待 R01 合并状态已 STALE 并修正。历史实现包括采音端点接受 PROCESSING、可选 DEVICE_MONOTONIC 与反馈原因、首次成功 codec 写入后报告播放开始，以及取消前后复查。codec 证据不能证明 LCD 完成或声学首声。

R01 历史 LAN 大小 3,141,296 B，3 MiB 槽余量 4,432 B，门槛 4,384 B，仅余 48 B；protocol 214,048 B。新增固定内存 248 B，分区、srmodels、相机/运动开关保持。这些不是 R02 新鲜测量。

## 正在进行

R02 服务端与控制台软件已验证。固件保持 R01 原树和候选证据，不为未修改固件重新声称构建或容量成绩；实体安装、heap/PSRAM、高水位、LCD 反馈及触摸静音仍待验。

## 下一步操作

核对 R02 固件目录相对基准未变，并在本轮候选收据中明确引用 R01 历史来源。后续若修改固件，先重新验证容量 4,384 B 门槛和栈；若用户另行授权刷写，再核对候选、profile、设备及 NVS 策略，采集停止与短聊实体记录。

## 阻塞项

无本轮固件开发阻塞；本轮没有刷写授权。历史槽余量紧张，任何后续固件变更必须重新验证。实体语音、显示与硬件资源效果尚无本轮新鲜记录。

## 关键文件

[R01 交付](../baselines/companion-r01-natural-voice.md)与其原任务工作树 `.tools/r01/firmware-candidate-receipt.json` 保留历史制品来源；[R02 交付](../baselines/companion-r02-project-loop.md)说明本轮固件未改和现场边界。

## 验证命令与最近结果

R01@7c22d00 历史结果：input-timing/codec/协议回归、十二类宿主、七类受影响复验、LVGL ASan/UBSan、双 profile 与五栈 gate 通过。历史栈用量 voice 4,832、upload 2,256、playback 160、transport 9,024、provisioning 11,488 B。

本轮只核对 `git diff a961816 -- firmware` 无修改，不运行新固件构建、不刷写；完整源码绑定与历史引用将在 R02 候选收据记录。仓库结束检查仍执行 `git diff --check`、`pnpm docs:check`。

## 相关设计、计划和决策

[R02 交付](../baselines/companion-r02-project-loop.md)、[R02 规格](../../requirements/companion-v1/R02-project-loop.md)、[R01 历史交付](../baselines/companion-r01-natural-voice.md)、[ADR0077](../decisions/0077-measured-private-natural-voice.md)、[ADR0078](../decisions/0078-versioned-readonly-project-loop.md)、[实体测量](../../runbooks/natural-voice-quality.md)。

## 安全与兼容性约束

未经另行明确授权不刷写、不改 NVS、凭据或部署模式。2026-10-15 用户已明确授权只推当前 R02 任务分支；推送及远端 SHA 核对结果见 `.tools/r02/push-receipt.json`。不创建 PR、不调用真实模型/Bark；原用户树与历史固件制品保持。LAN HTTP 仅局域网开发，生产 HTTPS 边界保持。
