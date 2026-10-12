# 成人桌面伙伴 V1：R00 兼容基线

- 文档状态：COMPLETED（软件基线；现场与安装单独待验）
- 日期：2026-10-12；最终交付核验 2026-10-13（Asia/Shanghai）
- 工作流：[全局总览](../status/overview.md)、[服务端](../status/server.md)、[前端](../status/frontend.md)、[固件](../status/firmware.md)、[部署](../status/deployment.md)、[产品](../status/product.md)
- 需求：[R00](../../requirements/companion-v1/R00-baseline-and-integration.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)

## 来源与当前程度

本轮由用户要求实施第一项需求，软件兼容与验证可以继续。真实外发、安装、固件刷写和外部推送仍需其对应授权。

| 字段 | 来源与事实类型 |
| --- | --- |
| sourceMaster | `989b3d9f591a854d9ca26fe05b78fb4af53329b5`，2026-10-12 刷新；PR #48 已合入 |
| candidateCommit | 任务分支 `codex/companion-r00-baseline-20261012`；最终完整 SHA 与构建绑定收据封存在本工作树 `.tools/r00/candidate-receipt.json` |
| legacyTopicSource | `8ad9822f4c74c0440a52b73b23f8fe661017dc97`；其父提交 `ec53bfe1d25983d36a4eb0b43e3d1ce8ea32757f`，原分支仍保留 |
| historicalInstalledSource | `a37d220e7a2a0189b28412ffd420b2173fbbfc13`，2026-10-11 的历史安装，保留 V57–V60 |
| deviceProfile / firmwareDigest | 保持历史 LAN r3 `b872f2ff185474cac90b372d1b3419873196f1db`；二进制 SHA256 `96961C58D9407DA0A4D2256C98A785ADECF2453BBDA185F648EDD3E74E9F086F`，本轮不构建或安装新固件 |
| schemaVersionsAndChecksums | 候选 V1–V60；主线缺 V57；原 V57 及全部旧 SQL 内容保持，全部版本指纹见[源码与迁移清单](companion-v1-source-inventory.json)；本地扩展证据 `.tools/r00/evidence-facts.json` |

基准与最后验证提交元数据采用当前分支祖先 `989b3d9`，不拿它代表新增功能成绩。最终任务提交不能在自身正文自引用，完整候选 SHA/干净程度/制品由外置收据记录。

迁移清单中的 SHA256 对应 Git 原始 SQL 对象。Windows `git archive` 的 CRLF 换行转换会改变打包字节的 SHA256；外置收据分别保存 Git、archive 和 JAR 的逐项指纹，验证 archive 与 JAR 字节相同、归一化 LF 后与 Git 内容相同，并核对全部 60 项 Flyway checksum。换行转换不作为 SQL 内容冲突，不修改任何迁移或校验记录。

## 事实恢复与整合范围

旧主线状态仍等待用户创建 Bark PR，Git 已显示合入。开始实施先标 STALE，再依据最新 Git 与只读证据更新；没有重复开发 Bark。原工作树 `D:\stackchan` 的用户生成类型、缓存、JVM 日志和固件日志保持，初始路径/文件 SHA256 保存于 `.tools/r00/original-worktree-fingerprints.json`；正在使用的 pnpm 索引无法独占读取，明确标为未取指纹；最终其 WAL/SHM 两个运行期文件已不再存在。Agent没有删除或清理该缓存，不能声称这些动态文件路径/内容冻结。

仅从已提交 Git 对象应用 V57 的服务端、迁移、前端、测试、设计、ADR 与使用 runbook；没有从用户未提交工作树取业务代码，没有复制旧状态覆盖新状态。原 37 项增量的逐文件清单见[源码清单](companion-v1-source-inventory.json)。新主线和 V57 业务代码无文本冲突，历史冻结中的 V57 核心源码与原提交一致；保留主线口语路由、能力咨询、Bark 回执、持续委托修订与调度。

| 审阅面 | 整合要求与结果 |
| --- | --- |
| TopicContinuationService | 原许可、单条相关话题、日预算、暂停/停止/休眠、真实来源进展与删源锁保持 |
| VoiceTurnService | 回复完成后记录进展；控制优先级、转题、取消旧提案和语音失败处理保持 |
| CompanionPromptService | 当前事实优先，普通记忆不授予续问；新增明确 WEB 组装入口，禁止网页待续上下文 |
| VoiceActionProposalService / Coordinator | 固定候选与同意周期、当前角色/会话、两分钟 TTL、设备预算、拒绝和旧语音确认保持 |
| DeviceUiService | UI v1 固定确认卡片显示待续与进展许可，旧固件降级保持 |
| PersonalDataService | 删除与异步进展共用会话锁；删除源会话任一消息清除复制进展 |
| LongTermMemoryEntity / Service / Repository | 原范围、确认/替代/拒绝查询和 NONE 默认保持；未确认不召回 |
| V57 SQL | 保留原编号、内容与 checksum；不 repair、改名或自动处理运行库 |

普通网页 `request.deviceId` 不再建立设备、实体动作或外发范围。只有服务器保存的会话绑定可提供设备范围；绑定 A 且请求 B 始终只作用于 A。既有可信绑定 WEB 的明确 Bark 请求继续原授权语义；普通网页角色/全局确认记忆与非设备读取保持。网页完成回合仍不保存设备待续进展。

## 隔离迁移、数据保持与回退

采用真实 PostgreSQL 的匿名合成数据，不读取生产正文、不复制用户备份、不把合成恢复称为线上恢复。新的 `CompanionBaselineUpgradeTest` 逐表比较管理员、设备及绑定、角色、个人任务、会话、普通记忆/使用记录、待续状态/进展/日预算、委托 taskId/runId、运行历史、通知设置和 SENT/UNKNOWN 回执；五项合成加密配置只比较解密摘要。

需同时验证：新库从零迁移全部 60 项；合法 V56→V57→V60；合法历史 V57–V60 启动验证且零迁移；合成数据 dump/恢复后再次启动且完整快照保持。已确认普通记忆保持 NONE，旧 checksum/ID/数量/字段保持，SENT/UNKNOWN 不回到待发送。

主线曾允许 V60 库缺 V57：候选默认 Flyway 校验应拒绝该路径，数据保持。该库不能直接套用“历史含 V57”安装流程，不自动打开 out-of-order 或 repair；必须另行明确兼容迁移方案。今天历史安装已含 V57 的事实与此风险分开。

复用 [个人数据备份与隔离恢复](../../runbooks/personal-data-backup.md)、`build-companion-candidate.ps1`、`verify-companion-full-restore.ps1` 和 `deploy-body-motion.ps1`。真实发布时先停写，一致数据/Skill/DPAPI/配置与旧镜像配套保存，在内部隔离实例核验候选及解密，再考虑运行替换。回退使用匹配旧产物与同一快照；没有自动 DROP、清库、重配身份或回滚未知发送。保留旧 a37d220 镜像和历史发布恢复点，不改设备 NVS。

## 真实反馈与匿名复现夹具

本轮收到两个真实主观报告：记忆似乎都在一个 session 中；回复卡顿、等待五六秒。尚无逐回合原始输入/录音和同条件配置，频次未知，不能推断唯一根因或报告首声分位数。

| 场景 | 受控输入与步骤 | 当前事实、期望与缺证据 |
| --- | --- | --- |
| F01 记忆跨会话 | 合成内容“木工底座已经完成”；在设备伙伴 A 创建并确认待续，结束/新开会话后问相关进度；再用伙伴 B 和普通网页复查 | 用户报告疑似只在一个会话；数据库记忆与短上下文分开，候选验证已确认待续可按设备/角色使用，网页不获待续。真实使用效果/频次待验 |
| F02 等待与长句 | 短问候与一条超过八秒的合成长句分别复现；记录话音结束、端点、上传、ASR、LLM、TTS、设备首声 | 用户自报等待约 5–6 秒；当前完整回答后单 WAV，8 秒采音与约 800ms VAD 边界保持。期望减少等待且不截断。设备首声 UNKNOWN，未测 p50/p95 |
| F03 指代/受阻 | 合成“比较两种桌面方案”后“第二个呢”；缺资料时查询已保存运行，不新增循环或推送 | 非真实用户失败基线；期望相关指代/资料缺失可解释、有证据才交成果。用受控路由/证据测试验证，第三项真实样例待验 |

合成输入公开且无个人内容；真实录音和内容如后续采集只留本地。F01/F02 是真实反馈加合成复现方案，不冒充已复现的真实失败；F03 明确为合成。R01 可按上述步骤采集真实基线，R00 不顺手实现时延或语义检索增强。

## 首个目标与能力边界

默认接受“比较二至三个公开 HTTPS 桌面方案，列来源事实/缺失条件，给推荐和下一行动”。只允许公开资料读取与文本生成，成果留控制台；没有购买、真实外发或新服务商权限。尺寸、预算、偏好必须明确提供或标缺失，不自行填写价格。无通用搜索时只读提供 URL，不声称全网搜索。具体公开来源与已配置状态由本轮只读能力摘要记录于 `.tools/r00/evidence-facts.json`，模型执行留给 R02。

工具继续现有内部默认 4 次/20 秒，最大 8 次/60 秒；结果大小、公网 HTTPS、超时、重定向与 TLS 限制保持。没有因后续公共契约 8/120 外层预算放大现有内部限制。未配置供应商显示未配置；Bark 仅可用目录/配置摘要，不发送测试通知或展示密钥。

## 固件资源

保持原 r3，无固件源码增量。历史 3,141,344B、3MiB 槽余 4,384B；历史 UI 最小空闲栈 2,536B，内部 free/largest 最低采样 22,055/7,680B。空闲约 60.544FPS 属于旧 r3 待机，录音/讲话、端到端点击与相机仍未量化，不能当作本轮成绩。

后续新固件须双 profile、栈/内部 heap/PSRAM、语音并发及容量门禁；余量至少保留 4,384B 或同条件 B0 更大余量。资源不过时继续服务端/网页与旧设备降级，不削安全栈或改分区。详细资源证据见[固件状态](../status/firmware.md)及本地安全摘要。

## 验证与下游门禁

本轮整合初始相关服务端175/175、网页/Bark/语音100/100；最终服务端完整894/894，失败/错误/跳过均零，BUILD SUCCESS。五项真实PostgreSQL升级/合成dump恢复5/5；控制台定向18/18、完整46文件172/172，固定Linux工具链限定ESLint/Stylelint与完整vue-tsc/Vite、服务端生产打包通过。五类固件宿主和文档检查/检查器7/7通过。最终提交相关服务端200/200再验通过，后续仅修正文档，代码与全量验证来源相同；再验文档/差异/祖先，并从干净Git archive冻结匹配制品，收据记录完整来源，不以历史699/840充本轮。

服务端最终报告 `.tools/stackchan-companion-tests-9e7d249f1dc24bb792ced6c21a81cf79`；新增基线专项 `.tools/stackchan-companion-tests-c988928f39fa432ca0152a4407b8bc11`；全部日志在 `.tools/r00/`。先前新测试多schema的pg_trgm前置造成3个错误，固定公共扩展后5/5及完整894/894通过；失败报告保留。既有Surefire退出30秒、Vite退出/代理和大chunk警告不记作已修复。构建启动一次缓存来源完整SHA误录被门禁拒绝，改用Git解析值；Windows静态工具异常退出改用固定容器。没有削engine、修改锁文件或略过测试。

本机Node24.19.0、文档启动器pnpm11.25.0，仅执行已安装脚本；生产/静态容器Node24.15.0、锁定pnpm11.13.1、Maven3.9.16/Java21，PostgreSQL与Maven测试镜像仍用仓库固定digest。独立工作树依赖复用原本机模块，生产缓存来源 `9a90db5392d979b1bd91b04ffbdf75d1a27d1af1` 与候选依赖树/四项摘要一致。等同钩子的lint已独立执行，提交仅本任务文件，单次SKIP_SIMPLE_GIT_HOOKS避免本机缺失命令包装器影响；不修改钩子。

- SOFTWARE_BASELINE_READY：兼容、迁移、数据保持与生产候选通过，可开始 R01/R02 软件任务。
- FIELD_BASELINE_PENDING：真实第三项失败、跨会话自然体验和设备首声/停止计时。
- DEPLOYMENT_PENDING：本轮候选未安装，不沿用旧任务的外推或安装许可。

下一条精确操作：完成 R00 自动验收与冻结收据后，从新鲜 master 建 R01 独立任务并保留本候选；按 F01/F02 采集真实输入、配置摘要和端点→设备首声。R02 可在兼容候选与实际公开 URL/工具范围满足时实施，不等待完整目标系统。

## 2026-10-12 运行与能力只读证据

21:50（Asia/Shanghai）当前运行镜像/标签与历史a37d220相符，库全部60条successful/checksum匹配候选；r3/motion_disabled、新鲜心跳15秒。只读SQL强制read_only且不选择私人正文/密钥。当前委托1、运行4、Bark回执1、记忆6、个人任务0；运行历史相对旧收据自然增长，不冒充数据静态不变。历史版本、今天读证据、新候选三者独立。

LLM/语音/Agent/public reader/Bark配置与启用为true；可信内置目录10项、无停用项。MCP连接1/有认证1/启用工具设置8、Skill1/启用1，仅核对本地配置，未联系供应商，不宣称可达或费用已测。默认4工具/20秒，单结果8192B、总24576B；公开读取6秒/1MiB响应/6144B JSON；Bark标题80码点/正文2800B；委托2 worker/无队列/10分钟租约。实际工具/角色/当次授权仍重查。

默认受控桌面比较来源为 [IKEA TROTTEN](https://www.ikea.com/us/en/p/trotten-desk-ash-effect-white-s19621919/) 与 [FlexiSpot E7](https://www.flexispot.com/flexispot-pro-standing-desk-e7)，仅核实官方公开页面存在；不是已执行的项目成果或购买建议。用户尺寸/预算/偏好未提供，项目reader是否能在当前限额内读取需R02实际受控验证，不伪造已全网搜索。

固件source/index与历史r3完整tree一致，工作树CRLF归一化一致；六个现存冻结固件制品原SHA256吻合。协议历史214,048B，LAN3,141,344B；512KiB SRAM/8MiB PSRAM为硬件容量，今天报告的minimumFreeHeap包含PSRAM不能充内部heap成绩。各voice/UI/transport/provisioning栈预算、分区与PSRAM证据在`.tools/r00/evidence-facts.json`，本轮无新固件。

## FR 与 AC 交接对照

| FR | 本轮结果与证据 |
| --- | --- |
| FR01 | 独立分支从新鲜989b3d9创建；原可读用户文件路径/指纹保持，动态缓存WAL/SHM变化和锁定限制单列；祖先与最终干净收据 |
| FR02 | 先标STALE再修正PR #48已合；历史安装/今天只读/新候选分别记录 |
| FR03 | 原37文件增量和重点冲突面审阅；[源码清单](companion-v1-source-inventory.json)、原设计/ADR/测试带入 |
| FR04 | 全部60迁移原内容/checksum；新库、合法旧基线和历史启动/缺57拒绝5/5 |
| FR05 | 20表旧字段快照、身份/ID/数量、预算进展、五项解密摘要、SENT/UNKNOWN保持 |
| FR06 | 全套894/894：V57删除/预算/确认、主线委托revision、Bark权限和WEB新增边界 |
| FR07 | 最终Git archive匹配镜像/JAR/全部页面资源与60项SQL；本地candidate-receipt.json |
| FR08 | 两项真实主观反馈+三条匿名合成复现夹具已备；第三真实失败、输入与计时待验 |
| FR09 | 默认二个官方HTTPS桌面URL与只读/文本范围已选；真实用户条件/实际成果留R02 |
| FR10 | 可信目录/源守卫/配置布尔与限额核验；无通用搜索，不调用模型/Bark |
| FR11 | r3源码及六制品/原大小分区/栈/内存摘要保持；五类宿主通过；无新固件实测 |
| FR12 | 本轮真实合成dump/restore通过；旧镜像/恢复卷保留，真实停写/DPAPI发布演练需安装授权 |
| FR13 | SOFTWARE_BASELINE_READY / FIELD_BASELINE_PENDING / DEPLOYMENT_PENDING分别记录 |
| FR14 | 六工作流与总览、此交接、实现压在同一中文任务提交；未外推或建PR |

| AC | 结果与验证范围 |
| --- | --- |
| AC01 | 通过：独立worktree，原用户文件未暂存/清理、可读指纹保持；动态缓存两路径已消失、内容未取指纹，未声称冻结 |
| AC02 | 通过：新鲜Git证明PR #48合入并修正文档，不重复实现Bark |
| AC03 | 通过：主线缺V57，原业务增量与测试整合，主线路由保持 |
| AC04 | 条件不适用：本轮主线未含V57，已明确检测，未重复cherry-pick |
| AC05 | 通过：区分历史收据；本轮另有只读运行版本/schema，非历史冒充 |
| AC06 | 本轮无同编号内容冲突：Git原始SQL指纹相等，打包CRLF差异单列且内容/checksum一致；不改checksum/repair/运行库，缺57风险路径默认拒绝 |
| AC07 | 通过：新库全部60迁移，普通confirmed记忆NONE，不获许可 |
| AC08 | 通过：合法V56→V60/历史V57–V60与真实合成恢复保持20表/ID/回执 |
| AC09 | 通过：原V57来源删除并发、实际成功消息进展和确认状态完整回归 |
| AC10 | 通过：普通WEB仅请求deviceId无设备/外发范围，不THINK/待续；可信绑定作用自身 |
| AC11 | 通过：能力咨询不开放Bark写；缺正文追问、取消与旧发送不复用 |
| AC12 | 通过证据边界：匿名合成明确标识，真实首声UNKNOWN；无旧成绩充新测量 |
| AC13 | 通过准备：给定HTTPS范围/缺资料边界，无内置搜索，无伪完成成果 |
| AC14 | 条件不适用：固件不改；原LAN余4384B且六产物摘要一致，未绕资源门禁 |
| AC15 | 通过：候选完成，没有部署/刷写/真实通知；现场门禁待验 |
| AC16 | 通过：从最终干净Git archive构建，image revision/JAR/全部SQL/控制台清单匹配收据 |
| AC17 | 通过合成恢复；真实数据候选发布/回退演练未执行，使用一致旧产物/快照禁止schema混用 |
| AC18 | 通过记录：前次错误保存、修正后全894/894；零skip，现场未验明确标注 |
