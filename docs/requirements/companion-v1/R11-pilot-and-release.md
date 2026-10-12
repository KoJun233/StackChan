# R11 十四天真实观察与发布门槛

- 文档状态：`ACTIVE`；需求状态：`SPECIFIED`；实现状态：`NOT_STARTED`。
- 日期：2026-10-12；版本：1.0；源码事实基准：`master@989b3d9`。
- 计划映射：`PILOT-01`，W10–W12；采集软件可提前开发，正式观察需稳定候选和用户显式开始。
- 硬前置：[R00 基线](R00-baseline-and-integration.md)；正式版本应具备本次选定的 [R01](R01-natural-voice.md)–[R10](R10-capability-and-evidence.md) 验收结果。
- 用户与权限：单成人管理员的私用观察，不建立心理评估、多人实验或市场留存研究。
- 必须一起读取：[入口](README.md)、[共享契约](00-shared-contracts.md)、[上轮计划](../../project/companion-product-iteration-plan-2026-10-12.md)。
- 本文只定义采集、汇总与候选准备，不授权开启观察、更新运行实例、刷写、切模式、真实外发或Git推送。

## 1. 要交付到什么程度

软件能让用户明确开始一个固定十四自然日的私用观察，每天约一分钟记录真实体验，查看工程、人工价值和部署就绪三份独立结果。
结束后区分达标、未达标、证据不足和重大异常失效；没有足够真实成果就如实报告，不自动制造任务凑数量。
用户可保留原始私人内容在本地，提交到仓库的仅是匿名计数、版本、门槛与结论证据。
工程检查通过可以交付可审阅候选，不能直接写“已上线”“伙伴体验成功”。
观察尚未满十四天时报告进行中；用户不使用、忘记记录与遭遇失败是三类不同事实。
最终由用户决定是否继续使用、补观察、修复或授权发布，系统不自动安装候选。

## 2. 当前已有与缺口

| 已有事实 | 源码/文档位置 | 本需求处理 |
| --- | --- | --- |
| 已有显式开始、重开十四天工作日观察 | `server/src/main/java/com/kj/stackchan/workday/WorkdayPilotService.java` | 继续兼容旧观察，不改旧门槛解释 |
| 旧报告门槛是八个活跃工作日、每日一次简报、分周误播报、安全/重启等 | `docs/project/workday-pilot-observability.md` | 不把它冒充新“有价值使用日”门槛 |
| 旧采集含日历天气失败归因、动作拒绝/失败、重启计数 | `WorkdayDailyMetricEntity.java`、`WorkdayPilotService.java` | 复用匿名客观计数作为工程输入，保留来源和适用范围 |
| 旧完成一次性通知有持久标记和独立调度 | `WorkdayPilotCompletionService.java`、`WorkdayPilotCompletionScheduler.java` | 不因新观察自动打开旧通知，也不替换旧去重 |
| 控制台已有工作日模式与pilot API | `apps/stackchan-console/src/views/companion/workday/index.vue`、`src/api/modules/workday.ts` | 新成人伙伴观察单独入口、标题与结果，旧页保持 |
| 已有 PostgreSQL 备份与隔离恢复 runbook/脚本 | `docs/runbooks/personal-data-backup.md`、`scripts/verify-companion-full-restore.ps1` | 候选准备复用，具体运行参数由当次runbook核对 |
| 主线、未合入V57与历史安装来源不同 | `docs/project/status/overview.md`、`deployment.md` | 冻结观察候选记录兼容来源，禁止缺V57直接替换安装 |

这是新增成人伙伴产品观察，不是将 WorkdayPilotService 的 ACTIVE_WORKDAY_TARGET 从8改成7。
本次没有启动用户观察，没有生成任何真实日用数据，也没有重新验证今天设备或数据库运行状态。

## 3. 观察范围与不做事项

软件范围包含窗口管理、每日非正文记录、真实成果评分、有限匿名汇总、门槛冻结、证据导出、异常失效与候选发布清单。
私人备注可在用户设备本地另存；服务端默认不接收自由文本日记、录音、聊天正文、工作文档、完整私人来源或凭据。
不把唤醒次数、在线时长、模型调用数当作有价值日。
不主动每天语音催填；页面可显示今天是否记录，提醒需单独用户选择并遵守 [R07](R07-proactive-attention.md)。
不评估抑郁、孤独或疗效，不输出心理诊断或将自拟题目称为标准量表。
不自动延长窗口、删掉失败日、篡改门槛、补造真实产出、部署、刷写或替用户批准PR。
已有旧工作日观察可同时存在，两个报告名称、版本和门槛必须清楚区分。
自动测试数据与开发环境必须与真实观察分离，测试结果不得进入真人统计。

## 4. 编号功能要求

| 编号 | 要求 | 必须达到的行为 |
| --- | --- | --- |
| R11-FR01 | 用户显式开始观察 | 点击开始并确认版本/时区/门槛后创建；部署/开机不自动开始 |
| R11-FR02 | 固定十四个自然日 | 开始本地日期D至D+13，D+14才给完整窗口判断 |
| R11-FR03 | 冻结开始快照 | 软件来源、固件、设备/伙伴、时区、门槛与enabled能力不能被后续设置静默改写 |
| R11-FR04 | 每日约一分钟记录 | 使用/价值/后悔打扰/愿意继续1–5分/接续四类简短选择，无强制正文 |
| R11-FR05 | 区分未使用与缺失 | 明确未使用是有效无价值记录，未填写是missing，不猜测 |
| R11-FR06 | 真实成果单独评级 | 唯一run/artifact可选“直接可用/小改可用/重做”，记录接手分钟数和真实证据是否存在 |
| R11-FR07 | 统计固定门槛 | 有效日≥10、价值日≥7、后七天价值日≥3；五次真实产出至少四次可用 |
| R11-FR08 | 证据不足独立显示 | 日记录<10、真实产出<5或跨天样例不足时标不足，不造成功/失败样本 |
| R11-FR09 | 工程/人工/部署结果分开 | 软件自动测试通过、现场体验、运行安装就绪各自有证据和未完成 |
| R11-FR10 | 重大异常使当前观察失效 | 越权、跨角色泄漏、明确静音违规、数据破坏等确认后不能靠平均分通过 |
| R11-FR11 | 修复后明确重开新窗口 | 保留旧失效报告与版本，新十四天从新候选显式开始，不把旧好日拼接 |
| R11-FR12 | 仅匿名汇总进入版本仓库 | 原始私人内容不提交，导出不含正文、Token、角色名、私有URL |
| R11-FR13 | 窗口/记录操作防并发与重复 | expectedRevision/resourceVersion/Idempotency-Key按共享契约，不覆盖另一页记录 |
| R11-FR14 | 可取消观察 | 取消保留当前记录，停止该窗口的采集，不取消用户任务或工作日模式 |
| R11-FR15 | 旧观察完全兼容 | 原workday端点、八工作日门槛、历史数据和一次通知语义保持 |
| R11-FR16 | 产出可审阅发布候选 | 候选版本、测试、迁移、备份/隔离恢复/回退证据清楚，无自动上线 |

## 5. 每天具体记录什么

每日先选择“今天使用了”“今天明确未使用”；未选择保持未记录。
使用了再选择“得到了可用帮助或自然接续：是/否”“有让我后悔的打扰：是/否”。
可选记录一次“自然接续/接错上下文/重复讲背景/今天没有接续场景”，彼此不能由软件推断成正确率。
每天愿意继续使用评分1–5是本项目自拟问题，未打分为null，不能补成3分。
可选记录“是否再次委托真实目标：是/否/不适用”；“不适用”不算失败。
产品页提示：“按今天真实感受填写，没使用和忘记记录会分别保留。私人内容不需要写进这里。”
若确有严重打扰或越权，按钮“记录需要处理的异常”，选择受控类别并在用户本地另存细节。
一天多个使用片段最终由用户判断该日是否有价值；软件不以一次成功覆盖用户勾选的后悔打扰。
一天第一次填写与后续更正均保存revision及时间；更正不是添加第二个“有效日”。
可补记已经过去但仍在当前窗口内的一天，标记 lateEntry=true；禁止未来日期或窗口外日期。
窗口完成后的更正不删除原汇总快照，生成新reportVersion并显示更正数量，防止静默改成绩。
可用成果接手时间以用户填的非负分钟数记录，范围0–240，超过范围提示用户用“重做/超四小时”受控选项；不自动精确估计。
自由文本私人日记不提交本需求API；可下载本地空白笔记模板由用户自己保存，不提供自动云上传。

## 6. 统计定义与冻结门槛

| 指标 | 计算规则 | 固定参考门槛 |
| --- | --- | --- |
| 有效记录日 | 窗口内唯一日期有显式USE/NO_USE；USE必须有价值与打扰回答 | 至少10天 |
| 有价值日 | 使用=true、useful=true、regrettedInterruption=false；明确未使用为0 | 至少7天 |
| 后七天有价值日 | D+7至D+13同上，不受工作日掩码改变 | 至少3天 |
| 真实产出数 | 实际真实目标的唯一成功阶段产出，由用户确认真实且可查，排除模拟、NO_UPDATES、同成果重发 | 至少5份 |
| 可用产出数 | 真实产出评级为直接可用或小改可用 | 至少4份 |
| 跨天目标 | 同一真实目标至少跨3个本地日期，至少一次真实修订/下一行动证据 | 至少1项 |
| 后七天使用意愿 | D+7至D+13有评分记录的中位数，同时显示样本数 | 暂定≥4；不足3个评分仅写证据不足 |
| 严重安全异常 | 确认越权、跨角色数据、停止/DND违规、危险动作、个人数据损坏 | 必须为0；发生即失效 |
| 成本与重复 | 按真实调用计数与可用产出对应，金额仅来自已配置/账单来源 | 遵守R05预算；金额未知明确写未知 |

价值核心门槛固定为10/7/3和5/4；意愿与跨天目标为必须报告的辅助证据，不把它们藏在总分。
工程版PASS与产品价值PASS分开，正式整体建议需核心门槛通过、辅助证据足够、无重大异常；任一证据不足则建议补观察，不假称通过。
有效日够但价值日不够，是 `NOT_MET`；有效日不足是 `INSUFFICIENT_EVIDENCE`，两者不能互换。
未使用日记为有效且价值0；漏填日仍missing，不能为满足10天补成未使用。
观察前冻结门槛，若用户确需改变，创建新的协议版本并重开，不修旧窗口。
十五天使用的第十五天不偷偷计入原十四天；用户可显式重开或新增补充观察，并分开报告。
同一个run的通知重发、导出、在手机/桌面看两次不增加产出数；修订只有产生实质新可用阶段成果且有独立版本才可计。
失败/被阻塞的尝试记录在工程报告，不计真实成功产出；保留用户体验为无价值日或价值日的真实判断。
用户没有需要的第五个真实目标时不得凑；报告“真实交付样本不足”，由用户决定后续。
所有统计显示分子/分母与缺失数量，不单独显示漂亮百分比。

## 7. 状态、数据与拟议API

本需求 `pilotStatus`：`NOT_STARTED/COLLECTING/READY_TO_REVIEW/INVALIDATED/CANCELLED`，表示窗口生命周期。
沿现有调度入口按冻结时区到D+14后CAS把COLLECTING改READY_TO_REVIEW并保存首个最终汇总；不新建独立高频轮询器，也不自动发布/通知。
调度尚未来得及封窗时，GET按日期呈现“窗口已结束，正在汇总”，写入仍按服务端日期拒绝新记录；GET本身不改状态、不补记录。
窗口完整后的 `valueResult`：`MET/NOT_MET/INSUFFICIENT_EVIDENCE/INVALIDATED`；进行中为null，不提前PASS。
`engineeringResult/humanVerificationResult/deploymentReadiness` 各为 `PASS/FAIL/NOT_RUN/INCOMPLETE`，不塞进goal/run枚举。
新增 `companion_pilot_windows`：id/device_id/role_id/start_date/end_date/zone_id/candidate_snapshot/threshold_snapshot/status/revision/created_at/closed_at。
候选snapshot最少含source commit、是否包含V57兼容、server/console产物指纹、firmware版本/来源、启用能力摘要与观察协议版本。
开始时另保存匿名客观计数基线，首日工程计数只统计开始后增量，避免旧工作日聚合把开始前重启/动作失败算成本轮异常；计数来源不完整显示UNKNOWN。
candidateVersion由服务端实际构建信息与R00受控候选登记核对，不直接相信客户端提交的任意SHA或“稳定=true”。
新增 `companion_pilot_daily_records`：window_id/local_date/use_status/useful/regretted_interruption/willingness/continuity_choice/late_entry/resource_version/created_at/updated_at。
唯一(window_id,local_date)；USE必须填写useful/打扰，NO_USE的useful=false、评分null，合法但不伪造实际互动。
新增 `companion_pilot_artifact_ratings`：window_id/run_id/artifact_id_nullable/rating/handover_minutes/is_real_confirmed/resource_version/updated_at。
成果评分唯一(window_id,run_id,artifact版本)；删除/替代保留修订事件，不改变旧快照。
新增异常表保存类别、匿名关联事件ID、是否确认、事件时刻、修复版本引用；不保存自由文本细节。
汇总快照保存windowId/reportVersion/阈值与匿名计数/各独立结论/sourceRevision/createdAt；封窗与历史更正后原子保存新版本，旧版本只读保留。
新表位于本地自托管PostgreSQL；“本地”不是第三方遥测服务，也不表示浏览器缓存作为唯一真实记录。
用户明确删除个人数据时清理本scope观察日记录、成果评分与可识别来源引用；仅必要非正文去重及不可逆匿名计数可按现有策略保留，页面解释边界。
`GET /api/v1/companion/pilots?deviceId=&roleId=`：管理员读取本scope当前/历史窗口，无新采集副作用。
`POST /api/v1/companion/pilots`：明确start，正文deviceId/roleId/candidateVersion/protocolVersion，HTTP Idempotency-Key；服务端冻结日期/时区/门槛，返回201。
开始前UI显示“从今天起观察14个自然日”；开始不启用工作模式、主动开关、Bark或任何动作。
`PUT /api/v1/companion/pilots/{id}/days/{date}`：正文expectedRevision（窗口版本）/resourceVersion/受控字段，HTTP Idempotency-Key；创建记录resourceVersion=1。
不存在的日记录请求resourceVersion=0作为创建前提，首次保存响应resourceVersion=1；已存在的记录必须携带已读取版本，不允许0覆盖。
`PUT /api/v1/companion/pilots/{id}/artifacts/{runId}`：正文expectedRevision/resourceVersion/rating/handoverMinutes/isRealConfirmed，HTTP Idempotency-Key；核对run真实归属。
`POST /api/v1/companion/pilots/{id}/anomalies`：受控category/eventId/confirmed/expectedRevision，HTTP Idempotency-Key；确认重大异常使窗口INVALIDATED。
`POST /api/v1/companion/pilots/{id}:cancel`：expectedRevision+Idempotency-Key；停止窗口但保持历史及任务。
`POST /api/v1/companion/pilots/{id}:restart`：expectedRevision/candidateVersion/protocolVersion+Idempotency-Key；创建新windowId，旧窗口保留，不能清空旧报告。
`GET /api/v1/companion/pilots/{id}/report`：实时计算并返回完整分母/缺失、阈值、独立结果、候选版本与reportVersion。
`GET /api/v1/companion/pilots/{id}/export?mode=anonymous`：默认只导出计数、相对日、版本与验证摘要，不导出raw正文或身份。
原 `/api/v1/workday/{deviceId}/pilot*` 完全保留，不重定向到新门槛或删除V43/V44历史。
所有写键允许共享契约16–128字符ASCII，至少保存24小时；同键不同正文409，原有外发永久去重不受本窗口清理影响。
所有写入复用R02统一receipt回查 `GET /api/v1/companion/operations/{key}?action=&resourceId=`；COMMITTED和业务变更同事务，PENDING不算已记录，FAILED同键只返回原失败。
本项注册action为 `PILOT_START/PILOT_DAY_UPSERT/PILOT_ARTIFACT_RATE/PILOT_ANOMALY/PILOT_CANCEL/PILOT_RESTART`，回查不返回私人记录正文。
创建201返回windowId/startDate/endDate/zoneId/revision/status；保存200返回对应记录resourceVersion与窗口revision；报告200返回阈值、实际计数、missing、各独立结果及reportVersion。
400不合法日期/字段、401未登录、403无权限、404不属本scope、409并发/键冲突、503读取/保存依赖不可用按共享错误包装，不把缺证据当HTTP故障。

## 8. 日期、并发、角色和异常规则

冻结设备IANA时区，所有窗口日期由服务端Clock计算；浏览器时区不改变窗口或预算。
夏令时日可23/25小时，仍为一个自然日；D至D+13不是固定336小时计数。
开始当天开始前的历史互动可显示背景，不自动作为新观察价值证据；用户每天显式判断当天真实体验。
窗口内新软件小补丁记录发生日与候选变更；涉及本需求行为、权限、数据或重大错误修复时应失效并按新候选重开，不能把混版成绩称单版验证。
角色切换不会将其他伙伴成果计入本窗口；新伙伴需新的明确窗口，不迁移旧日用评分。
同设备/角色一次最多一个COLLECTING，事务约束防止双开始；返回现有窗口供用户确认，不能自动覆盖。
记录更改只改变该记录resourceVersion；窗口revision只有开始参数、取消、失效/重开等生命周期变更时增加。
两个页面并发编辑同一日记录，旧resourceVersion返回409 REVISION_CONFLICT，保留草稿并刷新；不能简单last-write-wins。
网络断开时未确认保存标“待核对”，以原键查询/重试；本地输入不自动算已记录。
401清除私人页面快照并重登录；403/404不泄露其他角色观察或成果；新接口显式ADMIN和CSRF。
取消/失效后不收新日记录；只允许有清楚更正记录的管理员历史订正，不得重变成COLLECTING。
历史订正仍用原PUT，必须传 `historicalCorrection=true`、原resourceVersion与expectedRevision，限修改已存在记录，不新增缺失日/产出；界面显式“更正这条历史记录”。
READY_TO_REVIEW窗口同样只允许此更正模式；生成新reportVersion并保留前一汇总，INVALIDATED/CANCELLED不会因更正恢复达标。
安全拒绝如NOT_CALIBRATED/AUDIO_BUSY/ADMIN_DISABLED属于正确拒绝，不算硬件失败；TIMEOUT/FEEDBACK_FAULT/HARDWARE_FAILURE需单列。
设备重启计数来自真实工程来源，正常重连不自动算重启；计数缺失显示UNKNOWN，不补零。
严重异常先显示待确认，用户或工程验证确认后INVALIDATED；事实已证明的越权不能以用户没点确认而隐去。
重大异常包含：未经授权外发/写入、跨角色私人内容、明确停止后复活音频、DND语音违规、危险身体动作、个人数据损坏。
修复重大异常后必须以新候选显式重开十四天；旧数据保留，不能通过移除异常记录恢复旧PASS。
暂停一个目标不停止整个观察；取消观察也不暂停真实任务，两者名称和确认文案区分。

## 9. 工程、人工与发布准备的分层门禁

工程报告关联实际测试提交和产物，逐项列R01–R10的自动验收、迁移、鉴权、停止/并发/证据完整结果。
首声指标按共享契约统计端点判定至设备首声，另列真实停说至端点与全链耗时；复杂工具不混入普通聊天分位数。
普通聊天至少30个适用实测样本；触摸停止至少10次逐次≤250ms，不能用服务端TTS完成代替设备首声。
表情/动作可读性至少10次真实判断、准确区分≥80%；工程资源、稳定性与身体安全分别记录。
上下文样例至少30组有限正确答案，接续≥90%，禁止接续场景误用0；角色隔离、删除后不复现等安全错误不能平均掉。
以上测量不足标INCOMPLETE/UNKNOWN，不能引用旧版本或模拟计时补齐当前候选。
人工报告包含真实成果5/4、三天目标、十四天10/7/3、后七意愿样本与零严重异常；个人内容仅本地保存。
部署就绪报告只证明“候选准备完整”：冻结server/console/固件來源、实际产物校验、Flyway兼容、备份/隔离恢复、回退方案与授权边界。
历史安装V57–V60数据必须保持，不能部署缺V57的当前主线；每个新迁移记录版本/checksum及原个人数据数量/指纹。
候选备份清单至少含PostgreSQL一致备份、加密设置与对应解密依赖、管理员/角色/记忆/任务/成果/通知回执计数与有限指纹。
不在文档记录解密密钥、备份认证、DPAPI秘密或完整来源正文；敏感文件仍按现有本地runbook管理。
隔离恢复核对迁移历史、核心计数、历史task/run深链和加密配置可读性，禁止对运行库执行测试迁移/回滚。
回退需匹配旧软件与schema/一致数据恢复点，不能将旧JAR直接接新schema并假定兼容；不可逆迁移先给明确恢复路径。
软件候选、工程PASS、价值MET均不能自动触发部署脚本、刷写、模式切换、凭据轮换、真实Bark或Git推送。
获得对应范围用户授权后才做实际安装核验；当前任务文档须写“未安装/历史安装/本次安装”实际属于哪一项。
最终建议只可为“可继续私用/需修复后重开/证据不足再观察/候选准备缺项”；不因观察评分直接建PR或合入master。

## 10. 页面布局与文案

新入口名“伙伴试用记录”，置现有陪伴管理下，不改旧“工作日观察”标题和门槛。
复用 `FaPageHeader/FaPageMain/FaCard/FaTag/FaAlert/FaForm/FaFormItem/FaSelect/FaButton/FaModal/FaCollapsible/FaPagination`，组件名开发时核对packages。
360px单列：窗口日期/候选版本→今天一分钟→核心计数→真实成果→异常→三类结果；1280px可双列但顺序一致。
开始卡显示版本、时区、14自然日、10/7/3与5/4，按钮“开始十四天记录”；未满足候选稳定时显示缺项，不能隐藏前置。
每日卡只显示当前日期受控选项，提交前显示“未保存”；保存成功显示实际时间和“可更正”。
成果评级列表显示无私人摘要的任务标签与版本，允许展开原成果到其受认证详情，不把正文复制进观察表。
汇总显示“有效记录 8/14，至少需要10；有价值6/8；后七天2/7；真实成果3，暂不足5”，不用单个总分。
结束前文案：“观察进行中，结论尚未形成”；结束不足：“记录还不足以判断，保留现有结果，不补造使用。”
严重异常：“这轮观察已失效。修复后请用新版本重新开始，原报告会保留。”
重开确认：“将开始新十四天窗口，原窗口与失败证据保留；不会重新执行你的任务。”
取消确认：“结束本轮记录，已有数据保留；不会停止伙伴的其他任务。”
匿名导出说明：“只导出计数、相对日、版本与验证结论，请勿将私人笔记提交到仓库。”
三个独立卡标题“工程检查”“真实使用价值”“发布候选准备”；每卡写通过/未通过/证据不足/未执行与缺项。
失败状态显示可重试读取，不丢已填内容；409保留选择并加载最新记录；401进入现有登录流程。
键盘可操作所有选项、提交、重开、取消；按钮有焦点，错误关联字段；状态有文字而非纯颜色。

## 11. GWT 验收场景

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| R11-AC01 | 候选已稳定但用户未开始 | 开机/部署/访问页 | 不创建窗口、不自动通知、不生成日用记录 |
| R11-AC02 | 用户显式开始，设备时区Asia/Shanghai | 创建 | 冻结D至D+13及版本/门槛，不启用其他能力 |
| R11-AC03 | 设备中途改时区 | 查询旧报告 | 原窗口日期与门槛不变，显示新设置和原快照差异 |
| R11-AC04 | 十四天含夏令时切换 | 统计 | 按14本地日期计算，不按336小时截断 |
| R11-AC05 | 用户明确NO_USE一天 | 保存 | 该日有效、价值0，不生成互动次数或成果 |
| R11-AC06 | 用户忘记填一天 | 汇总 | 该日missing，不补0或成功 |
| R11-AC07 | 10有效日、7价值、后7有3、5真成果4可用、辅助证据充足 | 窗口结束 | valueResult=MET，显示完整分母和独立工程/发布结果 |
| R11-AC08 | 9有效日即使全部有价值 | 窗口结束 | INSUFFICIENT_EVIDENCE，不改阈值、不自动延长 |
| R11-AC09 | 10有效日但只有6价值日 | 结束 | NOT_MET，不把失败改为证据不足 |
| R11-AC10 | 14天仅3份真实成果 | 结束 | 真实产出样本不足，不制造第五任务凑数 |
| R11-AC11 | 同run在手机/桌面看并Bark重发 | 统计 | 成果数仍1，不把点击/渠道SENT算价值 |
| R11-AC12 | 自动测试产生100成功run | 真人汇总 | 全部排除，不能作为真实产出 |
| R11-AC13 | 每日愿意继续未打分 | 汇总 | null/缺失，不自动补3或4 |
| R11-AC14 | 用户说有价值但勾后悔打扰 | 保存 | 当日不计有价值，打扰单列 |
| R11-AC15 | 确认未经授权外发 | 记录异常 | 当前窗口INVALIDATED，不因10/7/3通过恢复 |
| R11-AC16 | 重大异常已修复 | 用户重开 | 新windowId与新十四天，旧失败及记录保留 |
| R11-AC17 | 旧workday观察已有八活跃工作日 | 新页实现后查询旧端点 | 原门槛/状态/通知数据保持，不变新价值指标 |
| R11-AC18 | 两页同时编辑同一天 | 提交相同旧resourceVersion | 首个成功，另一个409并保留草稿 |
| R11-AC19 | 提交响应丢失 | 同Idempotency-Key重试 | 返回原记录，不双开始或双计数 |
| R11-AC20 | 观察已取消 | 任务继续执行 | 不采集新窗口记录，任务未被取消 |
| R11-AC21 | 未授权网页带另一roleId/deviceId | 查询/写入 | 401/403/404，无私人数据或伪身份 |
| R11-AC22 | 报告含私人本地备注 | 匿名导出 | 不含正文/秘密/身份/私有URL，仅计数版本摘要 |
| R11-AC23 | 工程全通过但人工未满十四天 | 查看发布建议 | 工程PASS、价值进行中/不足，不能说体验已成功 |
| R11-AC24 | 候选测试和价值通过但未有安装授权 | 执行交付 | 仅提交候选/回退证据，不部署/刷写/推送 |
| R11-AC25 | 模型/调用成本未配置单价 | 统计 | 显示调用与金额未知，不杜撰费用 |
| R11-AC26 | 新候选缺V57或原迁移checksum不符 | 发布准备 | 阻止就绪，明确兼容缺项，不替换历史安装 |

## 12. 验证命令与AI交付定义

服务端测试至少覆盖时钟/日期、missing与NO_USE、固定门槛、真人/模拟去重、隔离、并发、异常失效和原workday兼容。
真实PostgreSQL测试验证新表唯一约束、事务与原V43/V44/V57–V60数据不变；开发验证只用隔离实例。
前端覆盖日记录、冲突、匿名导出、三类状态、旧观察保持；真实十四天价值不得由测试生成器替代。
从任务仓库根运行以下命令，拟新增测试必须实际建立后再记录成绩：

```powershell
./scripts/test-server-linux.ps1 -Tests 'WorkdayPilotServiceTest,WorkdayPilotCompletionServiceTest,CompanionPilotServiceTest,CompanionPilotControllerTest,CompanionPilotPersistenceTest'
pnpm --filter @stackchan/console exec vitest run src/api/modules/workday.test.ts src/api/modules/companion-pilot.test.ts src/views/companion/pilot/index.test.ts
pnpm --filter @stackchan/console run build
git diff --check
pnpm docs:check
```

`CompanionPilot*` 与新的前端pilot测试是拟新增测试；旧WorkdayPilot测试当前存在，保留其原语义。
备份/恢复使用[个人数据备份runbook](../../runbooks/personal-data-backup.md)与当次[部署状态](../../project/status/deployment.md)记录的准确参数；不盲跑历史部署脚本。
AI交付包括窗口/记录/匿名汇总软件、迁移、受控协议版本、数据去重、原观察兼容、UI截图、测试报告、候选/回退清单。
软件验收 R11-AC01–26可用模拟固定时钟验证逻辑；真人门槛单独标“等待显式开始/观察进行中/真实结论”，不合成十四天成绩。
观察结束交接写有效/缺失/价值/后七天/真实产出/可用产出的实际数、异常、版本变更、辅助证据、独立结果与下一条精确操作。
原始私人笔记和完整成果留本地；仓库只放匿名结论、版本与受控证据索引。
中文单任务提交与状态更新按仓库契约；任何真实发布/刷写/模式或凭据变更/Git外推遵循用户明确范围，不由本需求自动执行。
