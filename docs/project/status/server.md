# 服务端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-05
- 当前分支：`codex/device-interaction-upgrade`
- 基准提交：`effa511`
- 最后验证提交：`effa511`
- 最后验证范围：候选源码a840d0d完整服务端回归 663/663、状态通知/同意边界定向 74/74；最后追加 V56 升级退役 SQL 后真实 PostgreSQL/设置范围定向 33/33

Git基线与安装来源分开记录。V56服务器/控制台仍来自 `a840d0d17194c8aecedfd9b7d19b9e1ca6a7bb4b`；已安装显示固件来源 `1de3d7a7c86527e6f5613b5251e838a7b880b570`，由 `.tools/device-display-r2/manifest.json` 冻结，第二次修订应用OTA已获许可完成；首轮显示来源保留在 `.tools/device-display-candidate`。此前USB启动修复714c030与物理地址迁移的来源仍由 `.tools/device008-offline-repair/manifest.json` 保留；安装后补文档不改变制品来源。

## 当前目标

DEVICE-008 的 I02 跨端情绪修复、I04 固定快照确认卡片、I05 有回执的菜单设置。软件行为依据[本轮计划](../device-interaction-upgrade.md)，确切 HTTP/WebSocket 字段与兼容边界见[设备 UI 协议](../../protocol/device-ui.md)。

## 已完成

工作开始/返场表情统一为合法 `HAPPY` 和 5 秒；协商 UI v1 的语音表情携带规范 `turn_id`，旧固件继续收到原六字段命令。能力、卡片通知和可见回执按连接隔离，换连接清零；WebSocket/HTTP 拒绝重复字段和尾随 JSON。

设备认证 HTTP 提供完整卡片、确认/取消、实际显示回执和窄字段控制中心。卡片绑定原提案、设备、伙伴、会话、来源回合和两分钟 TTL，所有响应 `no-store` 且按 UTF-8 编码限制 8192 字节；2000 汉字的记忆内容可完整读取。只有实际显示回执才使用短语音提示，1200 ms 超时或旧设备保留原复述；可见待确认卡片拒绝新语音，停止声音不撤销提案。

完成待办固定原标题、更新时刻、截止时间与时区，后台编辑、删除或已完成会失败且保存终态审计。伙伴切换固定目标 ID/名称，同名消歧，保留其他回合和已派送提醒的 busy 门禁。设备伙伴 `consent_epoch` 只在实际换伙伴时改变，切走再切回也不能复活旧同意。V56 保存来源伙伴名和同意标识，并退休缺失固定目标或已发生伙伴变化的旧待确认提案。

菜单持久设置返回服务端重新读取的 `SAVED` 状态；音量/夜间显示写入使用设备及设置行锁合并。今日安静/恢复、伙伴切换/归档、交互设置及工作/休息状态变化仅在事务提交后发送 v1 状态刷新通知；回滚及普通工作日 tick 不通知。补齐工作模式返回时间至少 5 分钟的校验，使服务层与数据库约束一致。

Linux 回归脚本仅在隔离容器里声明现有 Maven 缓存的来源并离线运行，未改用户 Maven 设置。新增真实 PostgreSQL/认证控制器、并发与固件真实解析器宿主验证，均不使用生产数据；用户生成文件、缓存和既有改动未暂存。

## 正在进行

同源 `device008-a840d0d` 镜像运行、数据库V56、健康正常；原设备UI GET认证200/v1/no-store/8192字节边界、无认证401及控制台HTTP200证据保留。用户后来报告的离线已通过USB启动修复及物理origin迁移恢复，第二次显示修订已获许可安装，设备新鲜心跳为 `device008-ui-r2-1de3d7a`；音量UI路径和语音现场反馈的新固件修订见固件状态；服务器产品源码/镜像未变、没有重复迁移或恢复数据库，没有创建生产确认测试或执行用户待办。

## 下一步操作

在真实屏幕按[runbook](../../runbooks/device-interaction-20261002.md)第4–5步检查确认/取消、过期/断线和菜单回执；任何反馈按具体链路复现。安装来源保持 `a840d0d`，如有功能变更，迁移/同意边界最小复验为 `./scripts/test-server-linux.ps1 -Tests 'DeviceUiPersistenceTest,CompanionConsentPersistenceTest,WorkdaySettingsServiceTest'`。

## 阻塞项

无服务端软件实施阻塞。实体屏幕可读性、触摸区域、实际显示回执时序、摄像头并发内存及运动自然度尚需候选现场验证；本任务服务部署与应用OTA已另获明确授权并完成；相机与实体运动仍未获开启许可。

## 关键文件

`server/src/main/java/com/kj/stackchan/device/DeviceUiService.java`、`DeviceConnectionRegistry.java`、`DeviceUiStateNotifier.java`；`api/DeviceUiController.java`；`voiceaction/VoiceActionProposalService.java`；`task/PersonalTaskService.java`；`role/CompanionRoleService.java`；`interaction/`、`speech/VoiceTurnService.java`、`workday/`；`server/src/main/resources/db/migration/V56__device_confirmation_snapshots.sql`。

## 验证命令与最近结果

`./scripts/test-server-linux.ps1`：最新完整回归 **663/663**，失败/错误/跳过均为 0，BUILD SUCCESS。证据目录 `.tools/stackchan-companion-tests-7b81046f0963495eb42dda49b75648e3`；覆盖同意标识、事务通知、旧语音兼容及工作设置下限。此前完整回归 659/659 和旧定向 50/50 保留为历史过程证据，不能替代新增改动验证。

`./scripts/test-server-linux.ps1 -Tests 'DeviceUiPersistenceTest,VoiceActionProposalServiceTest,CompanionRoleServiceTest,WorkdayRuntimeServiceTest,DeviceWebSocketHandlerTest,WorkdayCompanionServiceTest'`：**74/74**，失败/错误/跳过均为 0，证据目录 `.tools/stackchan-companion-tests-962a3d180d234c4eb2925144eb31e731`。最新 UI 持久化测试 19 项覆盖 2000 汉字/6000 字节、设备隔离、TTL、并发幂等、对象变化、伙伴切回、连接重置、事务回滚及工作日 tick 不滥发通知。

完整回归之后只追加 V56 的升级前旧伙伴周期退役 SQL；`./scripts/test-server-linux.ps1 -Tests 'DeviceUiPersistenceTest,CompanionConsentPersistenceTest,WorkdaySettingsServiceTest'` 对最终代码复验 **33/33**，失败/错误/跳过均为 0，BUILD SUCCESS。证据目录 `.tools/stackchan-companion-tests-50da1d5e17f9498f91e5ea7cfff29d36`；不把此前完整回归误记为该追加 SQL 已在同次验证。

完整及部分定向回归保留既有 Surefire fork 在 `System.exit(0)` 后 30 秒被结束的警告；测试断言通过且 BUILD SUCCESS，未把该警告记作已解决。测试运行在临时 Maven/PostgreSQL 容器中，离线缓存缺失会明确失败。

`python scripts/test-device-ui-protocol.py`：断网 GCC 容器编译真实解析器/cJSON，ASan/UBSan **6 组、170 项检查通过**；覆盖固定卡片 ID、长中文、SAVED/SHOWN 回执和严格单字段状态通知，详情见[设备 UI 协议](../../protocol/device-ui.md#软件验证)。`git diff --check` 与 `pnpm docs:check` 在本轮最终交接前通过。

## 相关设计、计划和决策

[本轮计划](../device-interaction-upgrade.md)、[设备 UI 协议](../../protocol/device-ui.md)、[开发环境](../development.md)、[身体安全](../decisions/0042-local-first-k151-body-safety.md)、[混合输入](../decisions/0070-hybrid-voice-and-confirmed-companion-care.md)、[历史证据](../../runbooks/companion-interaction-20261001.md)。

## 安全与兼容性约束

只授权软件实施；不继承旧任务 OTA/动作/外推许可，不打开相机或驱动实体。图像仅本地缓冲、不识别身份、不保存/上传；默认关闭，由菜单开启。录音/播放、触摸、断线、错误/更新和反馈门禁优先，重启仍禁用。保留旧固件语音确认与控制台 URL、用户工作树和 HTTPS-only 边界，不改凭据/计费/部署模式，不创建或合并 PR。
