# 服务端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-02
- 当前分支：`codex/companion-interaction-delivery`
- 基准提交：`69a98cd`
- 最后验证提交：`69a98cd`
- 最后验证范围：上述提交为主线锚点；本轮工作树语音定向 47/47、陪伴单元 61/61；最新关心时间解析/确认/投递定向 21/21、完整 Linux 回归 638/638、零失败/错误/跳过、退出 0

## 当前目标

完成 COMPANION-007 的 V05/V06、C03/C04 和 D01，依据[交付文档](../companion-interaction-delivery.md)；[ADR 0070](../decisions/0070-hybrid-voice-and-confirmed-companion-care.md)记录输入与确认边界。

## 已完成

整合独立 ASR 工作树已调查的官方 HTTP 格式与额度安全码，同时保留旧返回结构兼容；已录 PCM 有序发送取消人为实时节奏。新增 ASR/模型/TTS 有界耗时，不记录认证载荷；手动就绪不冒充恢复唤醒监听。

明确记住产生未生效候选，语音确认绑定固定快照、伙伴、设备及来源。后台修改/拒绝/确认/删除、换伙伴、过期、重复与并发确认有数据库回归；敏感建议拒绝不回滚失败状态。明确时间与主题的一次关心需确认，30 天安排上限、四小时有效期，服从安静、伙伴/DND/主题和可靠投递门控。V54/V55 空库迁移已在真实 PostgreSQL 回归中执行。

## 正在进行

最新时间解析修复定向 21/21、完整 638/638、零失败/错误/跳过、退出 0、非 OOM。当前 companion007-d3459d2 / V55 为提醒页修复发布；server 源/JAR 与 1f4b185 完全相同，638 项 XML 复核，不冒充新增运行。真实控制台认证/只读 GET、注销后 401 通过，临时账号已清理。旧 MockMvc 偶发竞争及 fork JVM 退出等待警告保留，见[交付验证](../../runbooks/companion-interaction-20261001.md)。

## 下一步操作

当前发布健康/指纹及停写备份 stackchan-release-20261002111654 隔离 V55 恢复通过。2026-10-02 用户确认全部非动作清单含真实跨日均正常，功能验收收尾，并明确授权只推送当前任务分支；推送并核对远端 HEAD 后由用户创建 PR、审核/合并。没有待修的已报告问题，不重复已测路径或自主轮询；日后具体异常再按对应回合核对诊断，不用合成 UI 冒充现场。

## 阻塞项

当前没有实现、发布或用户功能验收阻塞；整体见[交付状态](overview.md)。既有完整 XML 638/638、退出 0、非 OOM 与候选代码等价性已核对。用户总体确认全部清单正常，未提供逐次计数/计时或门控明细；本次固定查询区间仍 0 回合、耗时未知，不把反馈转换为统计达标或候选发布后的新数据库证据。

## 关键文件

server/src/main/java/com/kj/stackchan/speech/、memory/LongTermMemoryService.java、voiceaction/、reminder/、V54/V55 迁移和 scripts/test-server-linux.ps1。

## 验证命令与最近结果

脚本使用固定 Java 21 Maven 镜像、只读代码/依赖种子，测试在临时目录，不连接生产数据或设备。运行 ./scripts/test-server-linux.ps1，定向可加 -Tests 'CompanionConsentPersistenceTest,CompanionFollowUpParserTest,FollowUpDeliveryTest'。最新定向 21/21、完整 638/638，报告目录 7e96c158ed79439abccec1560eeb48cc。./scripts/test-companion-voice-evidence.ps1 通过，分开模型计时/固定动作，缺失阶段仍未知；docs:check、检查器 7/7 和差异检查通过，交接后复跑。

## 相关设计、计划和决策

[交付文档](../companion-interaction-delivery.md)、[ADR 0070](../decisions/0070-hybrid-voice-and-confirmed-companion-care.md)、[本轮发布记录](../../runbooks/companion-interaction-20261001.md)。上一任务 611/611、V53 发布与原 ASR JAR 来源保留在[历史发布](../../runbooks/device-quiet-voice-20261001.md)，不作为本轮通过成绩。

## 安全与兼容性约束

用户已授权检查通过后的原 LAN 部署及保留 NVS OTA；最新决定后续实体动作免测，不再执行或请求确认，保持禁用；本次外部推送仅授权当前任务分支，不推送 master、不创建或合并 PR。伙伴/设备隔离、未经确认记忆不进入上下文、普通提醒不受陪伴暂停影响、单 WAV 和隐私删除保留。不改模型计费、凭据或部署模式，其他工作树不覆盖/暂存/清理。
