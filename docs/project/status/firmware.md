# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-05
- 当前分支：`codex/device-interaction-upgrade`
- 基准提交：`effa511`
- 最后验证提交：`effa511`

Git基线、源码和安装来源分开记录。上版设备 `device008-ui-r2-1de3d7a` 来源 `1de3d7a7c86527e6f5613b5251e838a7b880b570`，冻结 `.tools/device-display-r2`；服务器/控制台a840d0d/V56保持。原b760f92与714c030恢复点保留，任务压缩不改变已安装二进制来源。

现行设备 `device008-ui-r3-b872f2f`，冻结来源 `b872f2ff185474cac90b372d1b3419873196f1db`，独立安装/测量证据 `.tools/device-display-r3`。

## 当前目标

按[小屏菜单与动作计划](../device-visual-polish.md)完成第三次显示修订。先查官方StackChan、LVGL smartwatch、RoboEyes和Apple ELEGNT，再改原生菜单视觉与状态动作，实测当前版本。

用户于2026-10-05反馈当前r3“可以了基本没问题了”，定性接受菜单/动画和现行功能，并明确授权推送当前任务分支。没有新增录音/讲话FPS量化样本。固件持续安装许可保持；只推送 `codex/device-interaction-upgrade`，PR创建、人工审核和合并由用户执行，Agent不推送master。实体运动/相机及数据保留边界保持。

## 已完成

原六项软件见[实施计划](../device-interaction-upgrade.md)、[ADR0071](../decisions/0071-device-eyes-cards-and-local-tracking.md)和[现场记录](../../runbooks/device-interaction-20261002.md)。r2修复真实UI endpoint白名单（音量控件被禁用的根因），释放关闭菜单的隐藏控件，UI/LVGL栈移至PSRAM而容量不变；语音TCP_NODELAY及接收失败显式关闭/保留原因完成。

r2于2026-10-05 00:47应用OTA INSTALLED（任务4f3ad6a3），启动在线/校准/DISABLED/health=ok/V56，临时安装管理员0。600秒被动采集仅预期OTA复位，无异常；活跃待机59个同代次窗口/295.29秒，最终SPI完成率60.761FPS。UI最小空闲栈2616B，内部free/largest最低采样21911/7680B，codec写入错误0。这些仅属于r2；没有该次菜单构建或录音/讲话阶段样本，不能继承给r3。

## 正在进行

本版软件及安装验证完成，用户已定性接受。正在完成授权推送前的单提交和最小回归检查；录音/讲话同代次FPS无新样本，保留待验。

## 本轮实现与验证

r3保留两页四入口和独立配置行为；纯黑底、重画大符号、统一彩色圆图标、文字留白、主页双眼标记和配置返回箭头。录音增加专注起伏/状态条，思考视线停留/轮转点，讲话幅度提升；高兴/喜爱/困惑增加有节奏动作。WAKE被LISTENING遮住的问题用有界一次叠加修正，优先级/TTL/取消回合语义保持。

真实RGB565菜单22场景、完整LVGL ASan/UBSan/泄漏与pointer/worker/endpoint、生命周期和原最小五类宿主、语音流故障注入及显示完成wrapper通过。眼睛31静态场景、240脏区压力对照和8组/640动画帧逐像素对照通过，状态区随时间刷新无残影。双构建、实际五栈/固定容量与安装完成；25FPS GIF只是软件预览。

## 下一步操作

用户已接受当前版并授权推送。完成本提交最小五类宿主、文档和差异检查后，只推送 `codex/device-interaction-upgrade`；由用户在GitHub创建PR、人工审核并合并。录音/讲话同代次FPS及相机/实体运动现场验证仍是明确剩余项，不将定性接受视为量化达标。

## 阻塞项

没有软件实施或固件许可阻塞。间歇性重启唯一根因未知，有限无异常窗口不能替代长期验证。用户此前本地两次人脸跟随开启因资源不足失败，不记作相机验收。

## 关键文件

`firmware/main/device_menu_view.inc`、`device_ui.cpp`、`expression_engine.c`、`robot_eyes_renderer.c`；`firmware/test/host/{robot_eyes_render_test,expression_lifecycle_test}.c`；`scripts/test-robot-eyes-renderer.py`、`verify-firmware-display-stack-budget.ps1`。r3工作目录 `.tools/device-display-r3`，r2冻结目录保持。

## 验证命令与最近结果

最小五类：`python scripts/test-{firmware-startup,face-tracking,device-server-relocation,device-transport-maintenance,voice-interaction}.py`。本轮增加 `test-device-ui-interaction.py`、`test-device-ui-renderer.py`、`test-expression-lifecycle.py`、`test-robot-eyes-renderer.py`、`test-voice-stream-receive.py`、`test-display-completion.py`。构建LAN/协议对应defaults、STACKCHAN_STACK_USAGE=ON和来源绑定版本；栈检查 `verify-firmware-{display,device-ui,voice,transport,provisioning}-stack-budget.ps1 -BuildDirectory build-device008-lan`。完成前 `git diff --check` 和 `pnpm docs:check`。

## 相关设计、计划和决策

[视觉计划](../device-visual-polish.md)、[显示设计](../device-display-refinement.md)、[实施计划](../device-interaction-upgrade.md)、[协议](../../protocol/device-ui.md)、[安全ADR0042](../decisions/0042-local-first-k151-body-safety.md)、[输入ADR0070](../decisions/0070-hybrid-voice-and-confirmed-companion-care.md)、[开发环境](../development.md)。

## 安全与兼容性约束

显示PSRAM栈不执行Flash/NVS操作，持久写入仍走内部栈worker，LCD双DMA缓冲保留内部RAM。完整中文字库/原3MiB应用槽/40MHz SPI保持。当前LAN开发和生产HTTPS-only边界不变。只暂存本任务文件，保留用户生成文件、缓存和JVM日志；相对master一个中文任务提交；本次仅推送用户授权的任务分支，不推送master、不创建或合并PR。

## 第三次显示修订实际交付

来源 `b872f2ff185474cac90b372d1b3419873196f1db`，LAN `device008-ui-r3-b872f2f`，3141344B，3MiB槽余4384B，SHA256 `96961C58D9407DA0A4D2256C98A785ADECF2453BBDA185F648EDD3E74E9F086F`。任务 `76d97a9e-3d3c-400f-a9dd-6c6cbdfad9ba` 于2026-10-05T01:47:04.647469+08:00 INSTALLED，无失败码；新心跳/校准/DISABLED/health=ok/V56、临时安装管理员0通过。来源/双制品/日志/预览53项摘要复核，独立receipt绑定300秒采集/分析/安装日志和冻结manifest。

LAN/协议双构建、原最小五类宿主、完整LVGL交互/endpoint/ASan/UBSan/泄漏、22菜单场景、31眼睛静态场景、240压力对照和640动画帧逐像素局部/全刷对照、唤醒/情绪生命周期、显示完成wrapper/语音流故障注入、实际选定五栈与分区/ELF/容量通过。栈仍是选定项目/LVGL路径估计，第三方/RTOS/ISR完整深度不在该证明内。

本版300秒被动采集完成，活跃待机57同代次窗口/285.28秒，最终SPI完成率60.544FPS，窗口60.0–61.677。最近128区间p95最高快照19.261ms/p99 23.597ms，不是全会话百分位或LCD扫描同步。0条异常日志、0条捕获复位；UI最小空闲栈2536B，内部free/largest最低采样22055/7680B，codec写入错误最高0。本次串口开始晚于OTA启动，未捕获启动ROM/复位/初始化空间；启动健康由独立OTA回执及新版本心跳证明。listening没有可计量同代次窗口；processing没有可计量同代次窗口；speaking没有可计量同代次窗口。本次没有新的菜单构建样本，端到端点击延迟未测。采集时尚无本版用户反馈；随后用户于2026-10-05反馈r3“可以了基本没问题了”，定性接受当前版。没有新增录音/讲话FPS实测，不将软件GIF当设备成绩。

现行设备来源/证据在 `.tools/device-display-r3`；旧r2/b760f92/714c030恢复点保留，服务器a840d0d/V56不重新部署。后续文档压缩不改变该安装来源。

## 2026-10-05 验收与推送交接

用户反馈当前版“可以了基本没问题了”，明确要求推送。远端master已刷新，仍为effa511；任务相对master一个中文提交，实施和交接共同压缩。只暂存本任务文档，用户components.d.ts、缓存/JVM日志保持。代码未改变，现行服务器与固件冻结来源保持；推送前重跑最小五类宿主、`git diff --check` 和 `pnpm docs:check`。GitHub PR/人工审核/合并由用户进行，没有新增实体测试或部署。
