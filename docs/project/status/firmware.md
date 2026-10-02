# 固件工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-02
- 当前分支：`codex/companion-interaction-delivery`
- 基准提交：`69a98cd`
- 最后验证提交：`69a98cd`
- 最后验证范围：本轮工作树交互宿主五组、安全六组及 500 次竞争、双固件构建与三组实际栈预算/回归通过
- 最近记录的安装版本：`d8b6444`，保留 NVS 应用 OTA 已 INSTALLED；新鲜管理 API 确认校准保留、舵机 DISABLED、自动动作关闭

## 当前目标

完成 COMPANION-007 的 V01–V04、C01/C02，见[交付文档](../companion-interaction-delivery.md)。用户接受安静 WAKE、视频 PTT，已授权检查通过后的保留 NVS 应用 OTA。最新用户决定后续动作免测，不再执行动作；保持自动关闭、舵机禁用，不改角度/校准。

## 已完成

PTT 松手提交且不自动续听；普通监听右下角提交，拖出/其他区域取消；空闲角落短触切 WAKE/PTT，独立 NVS 保存，PTT 空闲不采音不检测。预卷和等待/讲话预算分离，短 PTT 弱语音与静音有不同门控；八秒讲话、三回合/两分钟新采音边界保留。

默认稳健不覆盖保存设置，失败可见并重试初始化。现场 clean 空地址崩溃后，模型只在正常监听期间复用，手动空闲/回合结束销毁，下次 WAKE 按需创建。真正读取后上报 LISTENING_RESUMED，手动用 MANUAL_INPUT_READY；SCV2 明确结束时消费音频也推进序号。头顶空闲短触五秒喜爱、不驱动舵机；长按和急停优先。五模板低优先级情绪不能遮住语音/系统表现，停止不恢复许可。

## 正在进行

d8b6444 LAN 应用 0x195e60（47%）已安装，protocol 0x3a150（92%）。原重启操作及三个短句无异常。完整回答读取后才恢复原省电，成功/失败均清理；组件级 STACKCHAN_STACK_USAGE 生成实际预算。2026-10-02 用户确认全部非动作清单均正常，功能验收收尾，当前仅等待 Git 人工审核；没有新增固件修改或 OTA。

## 下一步操作

保留已安装且复测稳定的 d8b6444；当前 d3459d2 服务端/控制台补丁不改固件，组件树与候选相同，不重复 OTA。空闲轻触五秒恢复且不运动分步通过；用户确认其余非动作清单正常，不再要求重复。NOD_SMALL/LOOK_USER 用户观察正常，THINK 仅设备完成；DROWSY/WAKE 及运动中真实 PTT 抢占用户免测，不执行或记通过。用户已明确授权只推送当前任务分支，核对远端 HEAD 后由用户创建 PR、审核/合并，不主动复位或重新采串口。

## 阻塞项

没有待修的已报告问题；用户完整范围确认解除此前等待反馈阻塞。验收为用户总体定性反馈，未提供逐次计数/计时；三个短句中位数 3622 ms/P95 4582 ms 仍不证明完整目标，本次固定查询区间无新回合，不声明准确率或时延指标独立达标。动作免测不作为阻塞，不重复 OTA、开动作、轮询或基本路径，不把宿主回归补作现场通过。

## 关键文件

firmware/main/voice_control.c、voice_capture_policy.c、touch_interaction.c、continuous_conversation.c、device_protocol.c、voice_protocol.c、body_touch_policy.c、body_hardware.cpp、companion_hardware.cpp、expression_engine.c、main/CMakeLists.txt。

## 验证命令与最近结果

python scripts/test-voice-interaction.py 五组通过；python scripts/test-body-audio-safety.py 六组与 500 次竞争通过。ESP-IDF 5.5.5 build-companion-lan-http-quad / build-companion-protocol 双构建通过，预算配置加 -D STACKCHAN_STACK_USAGE=ON。三组 test-firmware-*-stack-budget.ps1 与三组 verify-firmware-*-stack-budget.ps1 -BuildDirectory build-companion-lan-http-quad 通过；voice/upload/playback 已知路径 4816/2256/160，transport 8976，provisioning 7680 字节。静态预算不证明运行时最坏占用。文档检查器 7/7 与 docs/diff 通过，交接后复跑。

## 相关设计、计划和决策

[交付文档](../companion-interaction-delivery.md)、[ADR 0070](../decisions/0070-hybrid-voice-and-confirmed-companion-care.md)、[本轮证据](../../runbooks/companion-interaction-20261001.md)。历史 1b46c28 点头/语音后许可证据见[原修复](../../runbooks/body-motion-audio-permit-20261001.md)，五模板证据见[历史现场](../../runbooks/body-motion-20260929-field-acceptance.md)，不代表本轮通过。

## 安全与兼容性约束

应用 OTA 保留 NVS/Wi-Fi/凭据/校准，不写分区表/唤醒模型，不装 dirty 制品。沿用 LAN HTTP Quad，生产 HTTPS。最新用户决定停止后续实体测试；不得沿用旧逐步授权启用或驱动舵机。模板、限位和机械参数不变，其他工作树保持。
