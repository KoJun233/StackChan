# R07 普通主动时机、统一注意力预算与退让

- 文档状态：ACTIVE
- 需求状态：SPECIFIED
- 实现状态：NOT_STARTED
- 需求 ID：R07；优先级：P1，首轮即遵守安静与取消边界。
- 计划映射：PRESENCE-01，W2–3 最小待阅，W6–7 统一主动策略。
- 用户结果：允许时在合适时机提示有用信息；忙碌、拒绝、静音时能退让。
- 前置：[R00 基线](R00-baseline-and-integration.md)、[R01 前台音频](R01-natural-voice.md)、[R02 待阅事件](R02-project-loop.md)。
- 关联：[R06 记忆许可](R06-memory-continuity.md)、[R08 状态表达](R08-embodied-expression.md)、[R09 手机交接](R09-mobile-handoff.md)。
- 公共规则：[需求入口](README.md)、[公共契约](00-shared-contracts.md)。
- 本文所有 attention 数据/接口为拟新增；不另建提醒、调度或通知系统。

## 1. 完成后的实际行为

新增统一普通主动开关默认关闭，即使旧配置开着也不能自动获得新机制许可。
用户显式启用后，成果提示、轻问候、兴趣关心和无声陪伴共同受一台设备的注意力预算约束。
默认只在设备本地 10:00–18:00，普通主动语音每天最多 1 次，无声提示每天最多 3 次。
当前正在听/说、专注、免打扰、当天安静、错误伙伴、任务暂停时，相关成果只保持待阅。
用户说“先不聊”直接简短结束；不以确认卡、哀伤表情或接连问题继续索取注意。
到期、关闭、预算耗尽或角色失效的提示不补播；用户主动查看仍能取得真实成果。
用户自己设置的到期提醒和业务渠道通知保留原授权与边界，不能借普通主动策略被悄悄取消或重复。

## 2. 当前代码与增量

代码基准 989b3d9；历史安装/未合 V57 按 R00 核对，不能沿历史统计声称新仲裁已实现。

| 已存在路径 | 当前事实 | R07 增量 |
| --- | --- | --- |
| server/src/main/java/com/kj/stackchan/interaction/ProactiveInteractionService.java | 旧主动问候、记忆个性化、忙/离线检查与独立次数 | 普通来源先交统一仲裁，不再各自直接发送 |
| server/src/main/java/com/kj/stackchan/interaction/SilentPresenceService.java | 旧无声表情调度、独立计数；检查语音和提醒 | 统一设备预算与实际呈现回执 |
| server/src/main/java/com/kj/stackchan/interaction/InteractionSettingsService.java | 声音、DND、时区、连续对话、旧普通主动开关 | 增量统一设置，旧DTO兼容 |
| server/src/main/java/com/kj/stackchan/interaction/ProactivePauseService.java | device/role 暂停与恢复 | 继续使用既有暂停范围，不另造全局隐形开关 |
| server/src/main/java/com/kj/stackchan/interaction/DeviceQuietTodayService.java | 设备当天安静 | 作为普通主动硬门禁 |
| server/src/main/java/com/kj/stackchan/interaction/ProactiveTopicCooldownService.java | 主题冷却 | 共用来源退让；不覆盖用户明确停止 |
| server/src/main/java/com/kj/stackchan/reminder/ReminderDeliveryService.java | 提醒投递及独立错过策略 | USER/FOLLOW_UP/业务提醒保留原分类语义 |
| server/src/main/java/com/kj/stackchan/expression/DeviceExpressionService.java | 既有 emotion 下发，返回命令提交情况 | 下发接受不能直接记为实体已展示 |
| server/src/main/java/com/kj/stackchan/voiceaction/VoiceActionCoordinator.java | 简短反馈、拒绝、当日安静 | 增量撤销对应普通候选，不强制加确认 |
| apps/stackchan-console/src/views/settings/interaction/index.vue | 现有互动设置 | 统一开关、次数与解释复用此入口 |
| docs/project/decisions/0021-limited-proactive-interaction.md | 旧主动与到期提醒边界 | 不重写成所有提醒受 1 次语音额度 |
| docs/project/decisions/0068-confirmed-short-responses-and-quiet-decline.md | 拒绝关闭当前话题，不等于全天关闭 | 保持明确区分 |

## 3. 分类与不在范围

| 类别 | 是否普通主动 | 规则归属 |
| --- | --- | --- |
| 问候、兴趣话题、获许可的普通关心 | 是 | R07 统一仲裁与额度 |
| 成果完成后的可选桌面提示 | 是 | R07；成果本体保存/待阅语义由R02/R03 |
| 主动无声短表情/待阅脉冲 | 是 | R07，R08负责显示 |
| 用户正在查询成果时的正常回答/界面数据刷新 | 否 | 当前用户请求；不额外重复主动提示 |
| 用户明确设置的到期 USER 提醒 | 否 | 现有提醒/DND/错过策略与授权 |
| 明确确认的具体时间 FOLLOW_UP 关心 | 否 | 原一次性关心和四小时有效期边界，不自动变普通关心 |
| 已授权工作休息、业务/外部通知 | 否 | 原业务规则与渠道授权 |
| Bark 目标成果交付 | 否 | R05/R09 外发批准与投递状态，不计桌面普通语音次数 |
| 后台目标实时状态同步、不带动画/声音 | 否 | R08真实状态，不得用持续动画绕过主动额度 |

分类由服务端真实来源决定，模型不得把普通问候标为紧急提醒避开额度。
排除：看摄像头判定开会、心理推断、嗓音判断忙碌、多用户感知、主动购买或外写。
排除：新 push 服务、通用任务执行器、自动增加互动频率、忽略惩罚或关系等级。
不把通知数量作为陪伴价值指标；实际打扰和采用由 R11 观察。

## 4. 统一功能规则

### 开关、默认与时间

- FR07-01：新增 ordinaryAttentionEnabled 默认 false；迁移不从旧 proactiveEnabled/silentPresenceEnabled 自动推断启用。
- FR07-02：旧配置值保存并显示历史说明；关闭新机制时所有普通来源不得绕过仲裁直接播语音/发无声动作。
- FR07-03：新机制关闭只抑制普通主动；成果保留待阅，用户查询仍回答，明确到期提醒/业务通知按原规则运行。
- FR07-04：启用必须由管理员显式保存或当前明确的合法设置指令；模型、自动迁移、轮询、首次打开页面不能代替用户选择。
- FR07-05：启用后的默认设备当地窗口 10:00–18:00，起点包含、终点不含；首版配置仅允许同日 start<end，不支持跨午夜窗口。
- FR07-06：设备 IANA 时区默认 Asia/Shanghai；服务器 UTC保存时刻，浏览器时区无权改变分桶。
- FR07-07：普通语音默认每天1次、无声默认每天3次；首版允许调低或关闭，分别限0–1与0–3，不提供一键加频。
- FR07-08：跨伙伴共享设备额度；重启、取消、删除、切换伙伴不重置；修改时区保守继承当前消费，按公共C02执行。
- FR07-09：任意两次普通主动呈现至少间隔60分钟，语音和无声共用间隔；窗口结束不延迟补播。

### 候选、时机、退让

- FR07-10：普通来源只创建有来源ID、role/device、revision、有效期、渠道建议和价值类型的候选，不直接操作设备。
- FR07-11：候选创建不会消费呈现额度；同一源对象/实际内容版本的多模块提示合并，不能各自占一条。成果以稳定artifactId去重，goal纯暂停/恢复的修订和inheritedFromRevision不生成新候选、不重置已呈现或拒绝记录。
- FR07-12：所有普通候选 TTL 为创建后2小时，且不晚于创建当天主动窗口结束；未到窗口的候选只待阅，不跨日等下一窗口。
- FR07-13：如果目标成果在窗口外产生，成果照常保存；该次主动候选终止而不自动留到明日播报。
- FR07-14：候选最多保留20条未终结记录/设备；优先合并同源，满额时最旧低优先级候选终止为SUPPRESSED，成果本体不删除。
- FR07-15：优先级按可用阶段成果、明确获许可普通关心、兴趣消息、问候/无声陪伴；同类以创建时间排序，不做随机抢占。
- FR07-16：音频忙、录音、播放、屏幕确认/菜单、专注、DND、quietToday、当前角色不符时，不抢占前台；将可看内容转待阅。
- FR07-17：因这些门禁转为待阅的同一候选不自动排队补播；用户主动查询、查看或另设具体时间提醒才重新交互。
- FR07-18：状态只来自已认证设备、现有工作模式与设置；未知忙碌状态按保守不抢占，不能从聊天内容或摄像头猜测忙闲。
- FR07-19：阶段目标PAUSED/CANCELLED、源内容删除/替代、目标revision变化、伙伴切换，使未开始旧候选INVALIDATED；不会自动切到新角色重播。
- FR07-20：普通语音内容最多160 Unicode码点且仅说真实可用摘要；无声提示最多5–8秒、弱强度，不新增声音、震动或运动。
- FR07-21：普通主动不能自动创建连续对话；语音播完回到安静，用户主动接话按R01现有入口处理。
- FR07-22：识别到确定性“先不聊”“不聊这个”“现在忙”时直接“好的，先不聊。”，关闭本次话题/确认，停止对应候选，不再问是否确定。
- FR07-23：同一被拒来源/主题24小时内不产生新的普通候选；若用户明确主动提起，可正常回应但不能视为重新授权主动。
- FR07-24：“今天安静点”沿既有设备/伙伴当日暂停语义；普通拒绝不暗中改成全天停用；明确“别再提X”按R06持续主题停止。
- FR07-25：忽略不等于同意；不因无回应增加强度、次数、音量或换渠道催促，不显示受伤/责备措辞。
- FR07-26：关闭统一开关立即使未开始候选终止，并尽力停止正在呈现的普通候选；不撤销已经发生的外部通知，不补播库存。

### 并发、计数、未知和兼容

- FR07-27：取候选与额度槽位必须数据库事务/唯一约束原子预留；两个进程或两个角色不能同时超过1/3上限。
- FR07-28：实际呈现成功才转PRESENTED并消费展示数；仅创建、生成TTS、提交命令、Bark SENT均不算已展示。
- FR07-29：发出前再次检查开关、role、源修订、目标状态、时间、忙碌与预算；预留期间条件改变则终止，不下发旧内容。
- FR07-30：已下发但缺实际呈现回执转UNKNOWN；不自动重发，保留额度预留防止再次占用同一槽位，统计未知与成功分别显示。
- FR07-31：只有确定下发前失败/未呈现才释放预留；已经可能呈现时不能用网络超时释放额度再发下一条。
- FR07-32：回执必须匹配候选、设备、角色、源修订和单次nonce；重复回执不重复消费，错误角色/旧候选回执不使新候选成功。
- FR07-33：旧固件没有新增呈现回执时保留原协议；自动普通提示降级为控制台待阅，或按现有实际播放回执完成，不能用命令接受伪造已展示。
- FR07-34：预算耗尽、TTL到期、停用、取消或失效均为本候选终态；次日、恢复、网络重连不自动重开或补播。
- FR07-35：读取待阅列表、用户手动查看与反馈不消费普通主动次数，不将用户操作算作系统主动成功。
- FR07-36：整个仲裁复用现有调度入口与提醒/表达下发服务；禁止另启常驻轮询风暴或独立提醒库。
- FR07-37：日志只记sourceId、candidateId、原因、状态、时间和数字；不记录个人正文或自由模型输出。
- FR07-38：发出后30秒仍缺对应呈现回执即转UNKNOWN；若原下发协议已有更短确定失败边界，使用更严格边界。晚到合法回执仅用于核查，不补播或释放同日未知预留后另发。

## 5. 拟新增数据和 API

以下对象名称是建议合同；实现者选择与现有实体的最小增量，不宣称现在已经存在。
候选状态：PENDING、PENDING_REVIEW、RESERVED、PRESENTED、DECLINED、EXPIRED、SUPPRESSED、INVALIDATED、UNKNOWN。
PENDING_REVIEW用于忙/角色/专注转待阅，不能再自动回PENDING；用户主动交互不通过这个候选补播。
终态PRESENTED/DECLINED/EXPIRED/SUPPRESSED/INVALIDATED/UNKNOWN不自动重开；UNKNOWN只允许诊断核查。
窗口外创建不产生可调度PENDING候选；若需审计记录，用SUPPRESSED、reason=OUTSIDE_WINDOW、expiresAt=createdAt，不能保存早于createdAt的过期时间。
PENDING_REVIEW到TTL终止时仅取消主动资格，源成果的待阅状态仍由R02/R03保留。

| 拟新增字段 | 限制 |
| --- | --- |
| deviceId、roleId、sourceType、sourceId、sourceRevision | 全部由真实服务器资源绑定；sourceType白名单 |
| presentationKey | 同源实际内容版本跨普通渠道唯一；成果绑定稳定artifactId，纯修订继承不换key；源模块不能任意生成不同key绕过去重 |
| proposedChannel | VOICE或SILENT；仲裁可降级待阅，不自动提升为VOICE |
| createdAt、expiresAt、status、reason | 使用UTC；reason固定枚举，不存私人解释 |
| reservedSlot、reservationNonce、presentedAt | 预留与实际呈现分开；未知不会假记presentedAt |
| localDate、effectiveZone、spokenCount、silentCount、unknownReservations | 设备日原子预算；时区变更保守继承 |

拟新增 API：

| 路径 | 行为 |
| --- | --- |
| GET /api/v1/companion/attention/settings?deviceId=... | 当前配置、历史旧配置说明、有效默认、实际呈现/未知次数 |
| PUT /api/v1/companion/attention/settings?deviceId=... | expectedRevision与幂等键；验证0–1/0–3、start<end、有效时区 |
| GET /api/v1/companion/attention/pending?deviceId=...&roleId=... | 默认20条、上限20；仅当前授权范围的待阅摘要/源引用 |
| POST /api/v1/companion/attention/candidates/{id}/dismiss | 版本+幂等键；拒绝并停止同源提示，不自动删除成果 |

设备可选呈现回执若需新增，使用已认证现有传输并绑定nonce；不开放匿名管理员替设备确认的接口。
settings拟新增请求字段固定为enabled、windowStart、windowEnd、zoneId、voiceDailyLimit、silentDailyLimit、expectedRevision；时间为HH:mm，分钟整数，minIntervalMinutes首版固定60不由模型修改。
settings响应加revision、effectiveLocalDate、presentedVoiceCount、presentedSilentCount、unknownReservations；启用为false时仍返回历史选择与待阅可读信息。
更新配置只改变未来候选资格，不重置当日计数、不重新呈现历史候选；关闭后重新打开仍遵循现有日计数与源去重。
错误遵循公共契约：409版本冲突保留配置，403无权限不暴露其他角色标题，503依赖不可用不假记展示。
业务到期提醒和 Bark 继续使用原API/枚举，不能加入普通候选状态来改写其历史。
UNKNOWN晚到核查可单独记录resolution=CONFIRMED_PRESENTED/CONFIRMED_NOT_PRESENTED与resolvedAt；不重开原候选。确认已呈现时把同一预留转实际计数一次，不能双扣；确认未呈现仍不自动补发同日提示。
日预算与去重记录持久保存；清理策略不得使同日槽位或永久源去重丢失，至少覆盖有效期和当日。

## 6. 设置与待阅文案

| 位置/条件 | 精确文字 | 用户可做什么 |
| --- | --- | --- |
| 统一开关 | “允许普通主动提示” | 默认关闭，显式保存后启用 |
| 默认说明 | “默认10:00–18:00，每天最多1次语音、3次无声提示。” | 时间使用设备时区，限额可调低 |
| 与业务提醒区别 | “你设置的到期提醒和已授权业务通知按原规则运行。” | 进入原设置，不混改业务 |
| 旧配置保留 | “已保留旧设置；请重新选择是否启用统一主动提示。” | 不自动勾选 |
| 忙/专注/DND | “有成果待查看，当前保持安静。” | 用户可主动打开成果 |
| 预算耗尽 | “今天的普通主动提示已用完，成果仍可查看。” | 不出现“马上再提醒”自动补播 |
| 未知回执 | “是否已呈现未知，本次不会自动重试。” | 展示实际次数和预留未知分开 |
| 用户拒绝 | “好的，先不聊。” | 不弹确认，不触发悲伤惩罚 |
| 关闭成功 | “普通主动提示已关闭。” | 原提醒/业务通知范围保持可见 |
| 无待阅项 | “暂时没有待查看的成果。” | 不生成虚假闲聊占位 |
| 未认证 | “登录后查看” | 登录后重新读取，不自动恢复旧写入 |
| 源已经更新 | “内容已更新，请查看最新成果。” | 旧摘要不能继续播放 |

成果已读、采用、目标完成均由R03/R09独立保存；待阅列表清空不能自动说明目标完成。

## 7. 验收场景

| ID | Given | When | Then |
| --- | --- | --- | --- |
| AC07-01 | 旧主动/无声开关打开，新开关未选择 | 升级 | 普通提示不发，旧值保留，不自动迁移启用 |
| AC07-02 | 新开关关闭且有成果 | 候选产生 | 成果待阅可查询；无普通语音/无声 |
| AC07-03 | 新开关关闭且USER提醒到期 | 原提醒流程触发 | 按原授权/DND规则处理，非普通1次额度 |
| AC07-04 | 明确启用默认配置 | 设备本地09:59/10:00/18:00 | 仅10:00在窗口；18:00不呈现 |
| AC07-05 | 两个角色多进程并发争语音槽 | 同日请求普通提示 | 最多实际语音1次；无声独立最多3次 |
| AC07-06 | 今天已用满额度 | 重启、删除候选、改时区 | 不新增当天额度，不补播终止候选 |
| AC07-07 | 语音正在录音/播放 | 成果候选到达 | 不抢占、不打断；转待阅不自动补播 |
| AC07-08 | 专注、DND、quietToday任一开启 | 普通候选到达 | 保持安静，真实成果待阅 |
| AC07-09 | 候选属于旧角色 | 切换伙伴或旧回执到达 | 未开始候选失效，不对新角色展示旧内容 |
| AC07-10 | 一个成果被两模块同时提示 | 构建候选 | 同presentationKey合并，最多一次普通呈现 |
| AC07-11 | 候选等待两小时或过窗口末尾 | 仲裁/重连 | EXPIRED或终止，不明日补播 |
| AC07-12 | 普通提示被“现在忙”拒绝 | 识别控制 | 固定短收尾；当前源24h不重提，不强制全天关闭 |
| AC07-13 | 用户明确“今天安静点” | 原暂停逻辑执行 | 当天普通暂停，原业务提醒按自身规则继续 |
| AC07-14 | 目标PAUSED或revision已变 | 预留下发前重查 | 不发送旧候选，预留按确定未呈现释放 |
| AC07-15 | 下发成功但无呈现回执 | 超时处理 | UNKNOWN保留预留、不重发、不算已呈现 |
| AC07-16 | 实际呈现回执重复到达 | 处理多次 | 计数只加一次，旧nonce无效 |
| AC07-17 | 旧固件无呈现能力/回执 | 开启普通提示 | 降级待阅或现有真实播放回执，不伪造无声已展示 |
| AC07-18 | 新开关被关闭，已有候选待发送 | 调度继续运行 | 候选终止，恢复开关不补播 |
| AC07-19 | 候选超过20条 | 新来源加入 | 合并或终止低优先级最旧项；成果本体不丢 |
| AC07-20 | 用户忽略无声提示 | 等待后续 | 不升语音、不加频率、不换渠道催促 |
| AC07-21 | Bark供应商返回SENT | 查看目标状态与注意统计 | 仅渠道接受，不算设备展示、已读或采用 |
| AC07-22 | 网页待阅列表自动轮询 | 读取数据 | 不扣主动次数、不标已读、不发送新提示 |
| AC07-23 | 已提示artifact，仅goal纯暂停/恢复增加revision | 继承原成果 | presentationKey不变，无新候选/再次呈现/重置拒绝记录 |

## 8. 最低验证与实体边界

    git diff --check
    pnpm docs:check
    ./scripts/test-server-linux.ps1 -Tests 'ProactiveInteractionServiceTest,ProactiveSchedulePlannerTest,ProactivePausePersistenceTest,ProactiveTopicCooldownServiceTest,DeviceQuietVoicePersistenceTest,VoiceDialogueControlTest'
    pnpm --filter @stackchan/console exec vitest run src/views/settings/interaction/useCompanionSettings.test.ts
    pnpm --filter @stackchan/console run build
    python scripts/test-voice-interaction.py
    python scripts/test-expression-lifecycle.py

新增仲裁回归至少覆盖23项AC；真实PostgreSQL并发槽位、两个实例、重启和时区变化不可只用内存mock证明。
使用注入时钟覆盖午夜、10:00、18:00、TTL边界和DST；未知回执通过故障注入验证不重发。
普通来源列表必须逐个列明是否已接仲裁，不允许遗漏旧SilentPresence后台继续绕限。
如果修改设备可选回执，遵循R08尺寸/栈与双profile门槛；自动测试不证明实际5–8秒表情已显示。
真实设备分别测试听/说/菜单/专注/DND的到达时机；没有实体授权时标待验，不自动发送真实Bark。
日用打扰与愿意继续的反馈由R11自然观察取得，不能把低通知数量直接宣称体验良好。

## 9. AI 交付清单

1. 普通来源分类表、仲裁唯一入口、旧开关保留和显式启用的迁移说明。
2. 设备日额度、预留/实际/未知、去重和终态的数据库与并发证据。
3. 安静/忙/角色/暂停门禁、拒绝退让、TTL不补播和旧固件降级实现。
4. 设置页与最小待阅展示；实际计数和UNKNOWN分开，不宣称已读/采用。
5. 逐FR/AC验收结果、故障注入、匿名实体观察模板，说明所有未现场验证项。
6. 更新对应工作流及全局总览；一个中文任务提交，不推送、不部署、不真实外发。
7. 不从旧许可默默启用，不删除原提醒语义，不引入相机忙闲推断，不制造情绪惩罚，不用后台动画绕预算。
