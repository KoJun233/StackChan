# 部署工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-05
- 当前分支：`codex/device-interaction-upgrade`
- 基准提交：`effa511`
- 最后验证提交：`effa511`

Git基线与安装来源分开。服务器/控制台a840d0d17194/V56保持；上版设备 `device008-ui-r2-1de3d7a` 来源1de3d7a7c86527e6f5613b5251e838a7b880b570，冻结 `.tools/device-display-r2`。旧b760f92/714c030和a840d0d恢复点保留，压缩任务不改变二进制来源。

现行设备 `device008-ui-r3-b872f2f`，冻结来源 `b872f2ff185474cac90b372d1b3419873196f1db`，独立安装/测量证据 `.tools/device-display-r3`。

## 当前目标

完成r3视觉修订的来源验证和授权应用OTA，详见[视觉计划](../device-visual-polish.md)。

用户于2026-10-05反馈当前r3“可以了基本没问题了”，定性接受菜单/动画和现行功能，并明确授权推送当前任务分支。没有新增录音/讲话FPS量化样本。固件持续安装许可保持；只推送 `codex/device-interaction-upgrade`，PR创建、人工审核和合并由用户执行，Agent不推送master。实体运动/相机及数据保留边界保持。

## 已完成

首轮b760f92于2026-10-04 23:47应用OTA INSTALLED，任务cb688c12；原现场导航/WAKE手势通过，仍有音量和语音问题。r2于2026-10-05 00:47安装，任务 `4f3ad6a3-05de-459f-9cb3-b108477bd79f` INSTALLED，无失败码；release 3139488B/SHA256 `F1E5EE401FE1E646C4CA4C7F58D4B31C298BB4776212A87A68D472BB48374406`，与冻结bin一致。新版本心跳/校准/DISABLED/health=ok/V56通过，临时安装管理员0。

r2采集600秒，仅预期OTA复位，无异常；活跃待机295.29秒同代次窗口60.761FPS。用户本轮定性确认功能可用。原采集未包含菜单构建/录音/讲话，因此仍缺量化结果。详细安装/指标限制见[runbook](../../runbooks/device-interaction-20261002.md)。

## 正在进行

本版应用OTA及在线健康验证完成，用户已定性接受并授权任务分支推送。没有新增部署或刷写操作；缺失录音/讲话阶段样本仍待补。

## 本轮安装

r3软件/预览/双构建、实际五栈、原3MiB槽容量/分区/ELF摘要核验及安装完成。r2冻结/receipt未覆盖；现行r3来源和实测见下方。旧待机数据不继承给新二进制。

## 下一步操作

用户已接受当前版并授权推送。完成本提交最小五类宿主、文档和差异检查后，只推送 `codex/device-interaction-upgrade`；由用户在GitHub创建PR、人工审核并合并。录音/讲话同代次FPS及相机/实体运动现场验证仍是明确剩余项，不将定性接受视为量化达标。

## 阻塞项

没有软件实施或固件许可阻塞。间歇性重启唯一根因未知，有限无异常窗口不等于长期验证。用户此前本地相机开启因资源不足失败，不记相机验收；Agent不开启相机或运动。

## 关键文件

`scripts/install-body-motion-ota.ps1`、`scripts/capture-body-ota-startup.py`；候选 `.tools/device-display-r3`，已安装冻结 `.tools/device-display-r2`，首次显示 `.tools/device-display-candidate` 和修复恢复点均保留。

## 验证命令与最近结果

来源绑定双构建和实际五栈/最小五类宿主；安装前版本/大小/SHA/新鲜心跳/安全状态/无活动任务，安装后公开 `/api/v1/health` 与read-only DB核验。串口不发命令、不主动复位、只保存脱敏白名单诊断。r3当前软件结果见[固件状态](firmware.md)，原服务端663/663和前端147/147属于历史部署成绩。

## 相关设计、计划和决策

[视觉计划](../device-visual-polish.md)、[显示设计](../device-display-refinement.md)、[实施计划](../device-interaction-upgrade.md)、[协议](../../protocol/device-ui.md)、[开发环境](../development.md)、[部署ADR0004](../decisions/0004-lan-http-development-only.md)。不重新迁移数据库、切换模式或轮换凭据。

## 安全与兼容性约束

后续固件已有持续安装许可，软件和来源验证通过才执行。保留分区、NVS、模型、身份、校准、Wi-Fi，生产HTTPS-only保持；协议制品不安装；仅按用户明确许可推送当前任务分支，不推送master、不创建或合并PR。相对master一个中文任务提交，用户文件不覆盖或暂存。

## 第三次显示修订实际交付

来源 `b872f2ff185474cac90b372d1b3419873196f1db`，LAN `device008-ui-r3-b872f2f`，3141344B，3MiB槽余4384B，SHA256 `96961C58D9407DA0A4D2256C98A785ADECF2453BBDA185F648EDD3E74E9F086F`。任务 `76d97a9e-3d3c-400f-a9dd-6c6cbdfad9ba` 于2026-10-05T01:47:04.647469+08:00 INSTALLED，无失败码；新心跳/校准/DISABLED/health=ok/V56、临时安装管理员0通过。来源/双制品/日志/预览53项摘要复核，独立receipt绑定300秒采集/分析/安装日志和冻结manifest。

LAN/协议双构建、原最小五类宿主、完整LVGL交互/endpoint/ASan/UBSan/泄漏、22菜单场景、31眼睛静态场景、240压力对照和640动画帧逐像素局部/全刷对照、唤醒/情绪生命周期、显示完成wrapper/语音流故障注入、实际选定五栈与分区/ELF/容量通过。栈仍是选定项目/LVGL路径估计，第三方/RTOS/ISR完整深度不在该证明内。

本版300秒被动采集完成，活跃待机57同代次窗口/285.28秒，最终SPI完成率60.544FPS，窗口60.0–61.677。最近128区间p95最高快照19.261ms/p99 23.597ms，不是全会话百分位或LCD扫描同步。0条异常日志、0条捕获复位；UI最小空闲栈2536B，内部free/largest最低采样22055/7680B，codec写入错误最高0。本次串口开始晚于OTA启动，未捕获启动ROM/复位/初始化空间；启动健康由独立OTA回执及新版本心跳证明。listening没有可计量同代次窗口；processing没有可计量同代次窗口；speaking没有可计量同代次窗口。本次没有新的菜单构建样本，端到端点击延迟未测。采集时尚无本版用户反馈；随后用户于2026-10-05反馈r3“可以了基本没问题了”，定性接受当前版。没有新增录音/讲话FPS实测，不将软件GIF当设备成绩。

现行设备来源/证据在 `.tools/device-display-r3`；旧r2/b760f92/714c030恢复点保留，服务器a840d0d/V56不重新部署。后续文档压缩不改变该安装来源。

## 2026-10-05 验收与推送交接

用户反馈当前版“可以了基本没问题了”，明确要求推送。远端master已刷新，仍为effa511；任务相对master一个中文提交，实施和交接共同压缩。只暂存本任务文档，用户components.d.ts、缓存/JVM日志保持。代码未改变，现行服务器与固件冻结来源保持；推送前重跑最小五类宿主、`git diff --check` 和 `pnpm docs:check`。GitHub PR/人工审核/合并由用户进行，没有新增实体测试或部署。
