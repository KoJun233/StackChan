# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-09
- 当前分支：`codex/recurring-research-delivery`
- 基准提交：`ec53bfe`
- 最后验证提交：`ee606b5`

## 当前目标

持续委托只改服务端和控制台，复用既有语音读取路径。本轮无固件修改或实体操作，原现场待验保持。

## 已完成

历史现行固件为 `device008-ui-r3-b872f2f`，冻结来源 `b872f2ff185474cac90b372d1b3419873196f1db`，证据在原工作树 `.tools/device-display-r3`。设备二进制 3141344B、3MiB 槽余4384B，SHA256 96961C58D9407DA0A4D2256C98A785ADECF2453BBDA185F648EDD3E74E9F086F。用户此前定性接受菜单与动画，不作为本轮日报手机成绩。

上一任务已通过现有 USB 服务地址迁移修正离线，2026-10-08 地址为 192.168.1.5；身份、校准、Wi-Fi 与刷新凭据保留，无刷写。本轮五类最小宿主验证已通过。

2026-10-09 服务升级 V58 后设备已重新连接：仍为 r3、motion_disabled，验收心跳距当前 7 秒；没有刷写、轮换凭据或新增设备命令。

## 正在进行

无进行中的固件工作。此前真实语音复验、待续卡片体验、录音/讲话同代次 FPS、相机与实体运动待验未被本轮替代。

## 下一步操作

持续委托软件安装后，用当前设备说「今天的 AI 日报好了没有」核对真实保存的成果与日期。既有待续卡片与语音现场验收继续由用户自然使用反馈；无待执行刷写。

## 阻塞项

没有本轮固件阻塞。既有人脸跟随两次因资源不足失败，不能记作相机验收；间歇性重启根因及长期稳定性未由有限观察窗口证明。

## 关键文件

既有 `firmware/main/device_ui.cpp` 与语音链路保持；本轮新增服务端 `DelegationResultsTool`，不新增设备协议。原 r3、r2 与旧恢复点保留。

## 验证命令与最近结果

本轮最小宿主：`python scripts/test-firmware-startup.py`、`test-face-tracking.py`、`test-device-server-relocation.py`、`test-device-transport-maintenance.py`、`test-voice-interaction.py` 均通过。没有新设备刷写、帧率或长期运行测量；原 r3 被动待机样本约60.544FPS，不继承为录音/讲话或本轮现场成绩。

## 相关设计、计划和决策

[持续委托设计](../recurring-research-delivery.md)、[ADR0073](../decisions/0073-recurring-research-and-bark-delivery.md)、[使用说明](../runbooks/recurring-research-and-bark.md)。 [设备交互设计](../device-interaction-upgrade.md)、[安全ADR0042](../decisions/0042-local-first-k151-body-safety.md)。

## 安全与兼容性约束

不刷写、不启相机或实体运动，不改分区、身份、校准和认证。原用户文件保持，LAN/生产边界保持。
