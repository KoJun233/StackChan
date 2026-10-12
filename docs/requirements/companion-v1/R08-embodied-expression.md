# R08 具身表达、真实状态与固件资源门槛

- 文档状态：ACTIVE
- 需求状态：SPECIFIED
- 实现状态：NOT_STARTED
- 需求 ID：R08；优先级：P1，R02 首轮就复用最小状态表达。
- 计划映射：PRESENCE-01；W2–3 开始，贯穿各阶段，W10–12 做减法与校准。
- 结果：用户能读懂正在听、正在处理、正在说、受阻和待阅，且安静时不被动作打扰。
- 前置：[R00 资源与版本基线](R00-baseline-and-integration.md)、[R01 语音状态](R01-natural-voice.md)、[R02 真实项目状态](R02-project-loop.md)。
- 主动表达前置：[R07 注意力仲裁](R07-proactive-attention.md)；目标详细状态遵循 [R03](R03-goal-lifecycle.md)。
- 公共规则：[需求入口](README.md)、[公共契约](00-shared-contracts.md)。
- 本文件为增量规格；软件预览不代表设备已经呈现，历史FPS不转成新版本成绩。

## 1. 完成到什么程度

保留当前简洁双眼和主伙伴，在少量稳定眼神、停顿、音频与界面之间建立一致含义。
正在听就显示LISTENING，当前请求处理中显示PROCESSING，真正发声才显示SPEAKING。
后台目标运行不长期占据PROCESSING；用户仍能聊天和保持安静，详细进度在网页读取。
有成果待阅可在获准时给一次低强度提示；受阻有可操作原因；暂停不表现为受伤。
旧固件保持可用，新字段可选；不能为显示状态强迫新刷写、启相机或增加运动。
需要新固件时先证明容量、内部RAM、栈和语音并发安全，再产生候选；过不了门槛就收窄到服务端/控制台表达。

## 2. 当前基础与证据界限

主线基准989b3d9已有双眼、12种emotion、分层生命周期、菜单和UIv1确认。
历史现行固件device008-ui-r3-b872f2f来自b872f2f，二进制3141344B，3MiB槽只余4384B。
历史待机完成率约60.544FPS，来自285.28秒窗口；不是录音/讲话测量，也不是本需求新成绩。
相机已有两次内存失败和实体运动待验；本需求不把它们写为可用感知/表达能力。

| 真实路径 | 已有能力与应复用点 |
| --- | --- |
| firmware/main/expression_engine.h 与 .c | IDLE/EMOTION/INTERACTION/PHYSICAL/SYSTEM分层，12情绪、弱中强、turn生命周期 |
| firmware/main/interaction_state.h 与 .c | 八个真实状态：IDLE、LISTENING、PROCESSING、SPEAKING、SUCCESS、NO_SPEECH、OFFLINE、RECOVERABLE_ERROR |
| firmware/main/companion_hardware.cpp | 双眼渲染、播放生命周期与本地显示；避免另建一套动画引擎 |
| firmware/main/voice_control.c | 本地listen/process/play/cancel状态与turnId；前台真实来源 |
| firmware/main/device_ui.cpp 与 device_menu_view.inc | 两页菜单、quiet、stop、role等原动作；不占用已有停止入口 |
| firmware/main/device_ui_protocol.h 与 .c | UIv1有界响应8192B、角色最多32、固定确认卡片与UUID检查 |
| server/src/main/java/com/kj/stackchan/expression/DeviceExpressionService.java | 已认证设备emotion下发和预览、动态表达能力检查 |
| server/src/main/java/com/kj/stackchan/expression/ExpressionSuggestionParser.java | 隐藏emotion标记解析与白名单 |
| apps/stackchan-console/src/views/companion/expressions/index.vue | 软件预览与表情管理；保留其预览标识 |
| scripts/test-expression-lifecycle.py | turn开始、播放、取消及迟到表情宿主回归 |
| scripts/test-display-completion.py | 显示完成口径；提交帧数不等于SPI完成帧数 |
| scripts/verify-firmware-display-stack-budget.ps1 | 显示栈预算 |
| scripts/verify-firmware-device-ui-stack-budget.ps1 | UI任务栈预算 |
| docs/project/device-interaction-upgrade.md | 设备交互历史设计与安全边界 |

## 3. 范围、性格与不做的事

纳入：真实状态映射、安静优先、少量emotion参数、turn绑定、待阅/受阻的最小可读提示与软件预览。
纳入：设备能力分级、旧协议降级、资源门禁、实体判断与并发测量。
性格采用简短、友善、诚实、愿意退让；成功可以轻庆祝，未知就承认，暂停后平静等待。
排除：新增相机、视觉识别、情绪识别、舵机动作、随机舞蹈、唤醒后转头或运动脚本。
排除：新图像素材包、大字体包、分区扩大、刷新凭据、强制更换硬件或改变用户角色主题色。
排除：亲密度、忽略惩罚、假装难过索取回应、“只有我懂你”等关系操控。
既有安全固定运动模板维持原授权，但本需求不得调用它们作为新的状态反馈。

## 4. 状态与表达映射

以下为产品映射，不增加THINKING、GOAL_RUNNING等必填设备协议枚举。

| 真实事件 | 设备主状态/可选弱表达 | 网页精确状态 | 限制 |
| --- | --- | --- | --- |
| 本地采样已开始 | LISTENING | “正在听” | 不由服务器猜采音开始 |
| 当前语音端点已确定且请求未完成 | PROCESSING | “正在处理这次请求” | 不等于目标后台一直工作 |
| 实际播放开始 | SPEAKING +当前turn获准emotion | “正在说” | TTS生成/发送都不代表正在说 |
| 普通空闲 | IDLE | “待命” | 保留现有轻眨眼，不新增主动脉冲 |
| 目标ACTIVE、阶段RUNNING | 仍IDLE；不自动持续FOCUSED | “正在推进：{阶段名}” | 当前run与目标状态取真实服务端来源 |
| 目标BLOCKED | 仍真实前台状态；可选CONFUSED/WEAK 5秒 | “受阻：{固定原因对应文字}” | 不覆盖听/说，不伪装设备系统故障 |
| 目标WAITING_USER | 平静IDLE | “等你补充或确认” | 不每分钟催促或自动开确认卡 |
| 成果REVIEW_READY | 平静IDLE；获R07批准可选CONTENT/WEAK 5秒 | “有成果待查看” | 不庆祝最终完成，不据SENT判已读 |
| 用户明确采用且标准满足 | 本次互动可HAPPY/WEAK 5秒 | “按约定完成” | 必须真实COMPLETED，不以run成功代替 |
| 目标PAUSED/CANCELLED | IDLE/NEUTRAL | “已暂停”/“已取消” | 不表现愤怒、失落、恳求继续 |
| 当前quiet/专注/DND | 原平静显示，抑制普通脉冲 | “保持安静” | 不能隐藏离线、更新或真实故障 |
| 断网 | OFFLINE | “设备离线” | 清除旧turn与普通提示，重连不补播 |
| 当前回合可恢复错误 | RECOVERABLE_ERROR | “本次请求未完成，可重试” | 只表示设备/回合错误，不用于所有业务受阻 |
| 正在真实固件更新 | 现有UPDATING层 | “设备更新中” | 本需求不自动发起更新 |

阶段与目标状态不是设备音频状态，二者必须可以同时真实呈现：例如“正在听；项目已暂停”。
只有已认证资源绑定后的状态才能展示目标标题，错误伙伴不得看见另一伙伴的私有标题。

## 5. 功能规则

### 优先级与安静

- FR08-01：本地急停/触摸停止最高优先，先停止声音/原安全动作；所有表达不得延迟R01停音。
- FR08-02：保留现有UPDATING、OFFLINE、RECOVERABLE_ERROR系统优先级，普通emotion与目标提示不能覆盖。
- FR08-03：前台LISTENING/PROCESSING/SPEAKING高于普通主动、生命周期预览和目标提示；音频回合不被后台状态夺走。
- FR08-04：菜单/固定确认模态保持原交互语义，普通提示不覆盖、不打开另一个模态；原停止与取消按钮始终可用。
- FR08-05：quietToday、DND、专注抑制普通语音、无声提示和待阅动画；允许无打扰的真实静态数据，但不引入持续闪烁。
- FR08-06：普通idle微动保持既有实现与帧率策略，不新增独立随机触发器；新短emotion提示必须受R07仲裁。
- FR08-07：后台阶段RUNNING不得持续设置PROCESSING或SPEAKING，不阻塞Wake/PTT，也不以长“思考”动画假装有真实进展。
- FR08-08：目标受阻/待阅的静态标识和网页文字持续可查；脉冲一次后回到当前真实状态，不累计重复吸引注意。
- FR08-09：旧固件不支持最小目标标识时使用原双眼与控制台状态；不得因缺字段令设备无法连接。

### 情绪与真实生命周期

- FR08-10：情绪只从现有12种白名单选择；新增状态反馈默认WEAK、5秒，未出现合法情绪则NEUTRAL。
- FR08-11：当前语音emotion继续按现有5–15秒合法范围与turnId绑定；隐藏标记不读出、不留在用户正文。
- FR08-12：待播放emotion不提前消耗寿命；以真实播放开始计时；重复事件不延长TTL。
- FR08-13：turn取消、断开、新turn开始清除旧绑定；迟到旧emotion/播放回执不能让新回合改表情。
- FR08-14：目标提示使用独立sourceId/revision，不伪造语音turnId；目标修订、暂停、取消、角色切换清除未开始旧提示。
- FR08-15：强情绪不用于普通成果提示、缺能力、暂停或被拒；用户内容需要情绪时保持既有白名单与本期性格约束。
- FR08-16：只有最终明确采用且满足R03标准才给完成庆祝；阶段成功、Bark SENT、用户已读只给对应真实状态。
- FR08-17：失败表达简短可恢复，不循环震荡悲伤/愤怒，不把系统错误归责用户。
- FR08-18：角色主题与TTS设置保留；角色切换后旧角色晚到事件不得改新主题或播旧角色提示。

### 预览、回执与旧设备

- FR08-19：软件预览醒目标“软件预览”；设备预览标“设备预览”，必须真实在线且支持；提交命令只表示已提交。
- FR08-20：软件preview不计普通主动次数、实体显示成功、状态辨识率或FPS；不能从canvas动画流畅度推断设备达标。
- FR08-21：新增普通提示呈现回执仅在设备确实开始对应可见表达时发送，绑定candidateId/nonce/sourceRevision；旧设备缺失为UNKNOWN或待阅降级。
- FR08-22：已有命令接受、WebSocket发送成功、configureExpression返回true不替代实际呈现回执；服务器与设备成绩分开。
- FR08-23：所有新设备字段可选、有界；UIv1确认卡保持真实确认用途，不将成果待阅伪造成需要确认的proposal。
- FR08-24：若新增静态目标摘要，最多一个当前角色对象，文本摘要最多48 Unicode码点；长内容、来源、权限在网页查看。
- FR08-25：未知枚举/超长/非法UUID/错误revision安全忽略新增字段，不覆盖原状态或破坏原协议解析。
- FR08-26：离线后清理当前瞬时提示；重连只同步当前有效静态状态，不重放动画、旧确认或已取消音频。

### 资源和实现门禁

- FR08-27：新固件开发前记录同条件B0二进制、应用槽、internal RAM最小余量、各任务stack high-water与PSRAM；未知就不能批准新固件候选。
- FR08-28：历史4384B余量作为最低保留门槛：候选不得超3MiB槽且不得减少该历史余量；若B0有更多余量，应保留B0余量。
- FR08-29：历史r3情况下候选应用二进制必须≤3141344B；届时若新槽/来源不同，R00冻结严格上限，不暗改分区或降低余量凑通过。
- FR08-30：内存判断分开internal RAM与PSRAM；不能用大量PSRAM剩余掩盖内部RAM不足；voice/UI/display/transport栈不得低于现有验证预算。
- FR08-31：在相同模式/帧率下候选最小内部空闲堆与stack余量不得低于B0；达不到先删减新分配/资源，不能直接降低语音任务栈。
- FR08-32：优先参数编排、现有emotion、服务器文字与控制台；不引入大位图、额外字体、常驻大缓冲或新增高频任务。
- FR08-33：连续切换菜单、录音、播放、取消后临时对象释放；原菜单/card释放机制不得被新状态持有引用阻止。
- FR08-34：任何资源门禁失败，本需求以服务端/网页状态和旧设备降级继续完成可逆工作；将新增设备能力标BLOCKED，不生成可刷写宣称。
- FR08-35：软件实现不自动刷写/部署/启相机/新运动；原有效授权按AGENTS核对，不用历史许可推断新的实体能力。

## 6. 拟新增数据与接口

现有emotion配置、preview、UIv1和设备传输继续兼容；以下只是增量合同。
拟新增服务端状态映射对象：

| 字段 | 合同 |
| --- | --- |
| deviceId、roleId | 来自真实认证绑定；不接受模型随意指定 |
| foregroundVoiceState、turnId | 沿现有状态；没有前台turn时为空，不填后台goalId |
| goalId、goalRevision、stageStatus、artifactId | 只读真实业务状态，可为空；不得创建第二套目标状态 |
| presentationSource、candidateId、nonce | USER_REQUEST或R07许可候选；普通候选必须已获槽位 |
| capability | 原dynamic-expression/UIv1信息与可选attention-presentation-receipt能力 |
| expiresAt、quietReason、renderingStatus | 固定有界字段；提交、呈现、未知分别表达 |

拟新增管理员读取：GET /api/v1/companion/devices/{deviceId}/presence。
返回实际前台状态、授权角色目标摘要、安静原因和设备能力；无权限404/403，设备离线也保留业务只读状态。
该接口不下发动作，不开麦、不将读取当已读、不默认生成普通候选。
设备可选字段通过原已认证传输扩展；是否新增回执由资源门禁决定，不另开匿名HTTP上报。
存在数据库状态时用原源对象+revision查询，不存影子目标生命周期；只持久需要去重/回执的信息。
新增preview入口如果确有必要统一/api/v1/companion，原preview API继续可用；不能绕开设备能力校验。

## 7. 精确体验文案

| 条件 | 文案 | 注意 |
| --- | --- | --- |
| 软件预览 | “软件预览，效果以设备为准” | 不能显示“设备已验证” |
| 设备命令已提交 | “已提交设备显示请求” | 有真实呈现回执后才“设备已呈现” |
| 呈现回执缺失 | “设备呈现结果未知” | 不自动重发 |
| 当前目标运行 | “正在推进：{阶段名}” | 不显示“正在说”或长期“正在思考” |
| 缺工具受阻 | “缺少可用能力，暂不能继续” | 来源来自R10真实目录 |
| 预算受阻 | “执行额度已用完，等待你选择下一步” | 不偷偷换模型或扩额 |
| 成果待阅 | “有成果待查看” | 不能显示“你已经看过” |
| 暂停 | “已暂停，成果仍保留” | 中性，无失望措辞 |
| 安静 | “保持安静” | 普通提示不发声、不闪烁 |
| 旧设备降级 | “当前设备使用基础状态显示，详细进度在网页查看” | 不要求刷写才能查看成果 |
| 资源门禁失败 | “设备扩展暂缓：资源余量不足” | 技术数字放诊断区，不塞用户语音 |

主流程只给用户有意义的状态与操作；二进制、栈、lease、nonce在诊断/开发记录中查看。
颜色只是辅助，听/处理/说/受阻/待阅均有文字或稳定形态差异；不得只依颜色区分。

## 8. 验收场景

| ID | Given | When | Then |
| --- | --- | --- | --- |
| AC08-01 | 本地采样未开始 | 服务端已接请求 | 不伪造LISTENING；真实采样后才显示 |
| AC08-02 | 当前语音端点确定 | 等待模型与TTS | PROCESSING本地及时显示，遵循R01门槛 |
| AC08-03 | TTS生成但未播放 | 状态同步 | 不显示SPEAKING；实际播放后才切换 |
| AC08-04 | 后台阶段RUNNING | 用户开始WAKE/PTT | 能正常听；后台状态不持续占PROCESSING |
| AC08-05 | 目标BLOCKED但当前在说 | 普通受阻提示产生 | 不覆盖讲话；网页真实说明受阻原因 |
| AC08-06 | quiet/DND/专注开启 | 成果待阅 | 保持平静，不自动脉冲、声音或运动 |
| AC08-07 | 菜单或确认卡打开 | 普通候选获调度 | 不覆盖模态；按R07转待阅/终止 |
| AC08-08 | turnA情绪等待播放 | A取消且B开始 | A迟到情绪/音频不能影响B |
| AC08-09 | 同一合法emotion事件重复 | 设备重复处理 | 不延长TTL、不重复提示 |
| AC08-10 | 目标修订或角色切换 | 旧候选晚到 | 不展示旧标题、不改新角色主题 |
| AC08-11 | run成功、Bark SENT、成果已读 | 判断最终表达 | 不庆祝COMPLETED；仅明确采用且标准满足才完成 |
| AC08-12 | 用户暂停/拒绝 | 回到空闲 | 中性平静，不悲伤惩罚或催促继续 |
| AC08-13 | 旧固件不支持新增字段 | 接收状态同步 | 基础双眼/协议保持；网页提供详细状态 |
| AC08-14 | configureExpression返回true但无呈现回执 | 查看统计 | 仅已提交/未知，不标实体成功、不扣成功展示数 |
| AC08-15 | 断网后重连 | 恢复当前状态 | 不重放旧动画、音频或确认 |
| AC08-16 | 软件preview流畅 | 保存验收报告 | 只记软件预览，不记实体FPS或辨识率 |
| AC08-17 | 候选二进制超过严格上限 | 准备候选 | 固件门禁失败，先资源减法，不能改分区通过 |
| AC08-18 | PSRAM充足但internal RAM/stack不足 | 运行语音并发 | 判资源不通过，不以PSRAM替代 |
| AC08-19 | 50次菜单打开关闭、10次真实语音与取消 | 检查B0/candidate堆栈 | 无新增泄漏/卡住/重启，满足冻结余量 |
| AC08-20 | 至少10次实体混合状态 | 用户判断听/处理/说/受阻/待阅 | 正确至少80%，记录未知/歧义；没有实测不报达标 |
| AC08-21 | 非法枚举/超长字段/错UUID | 解析可选增量 | 安全忽略，原状态/停止入口继续工作 |
| AC08-22 | 普通候选已过期或额度用尽 | 请求表现待阅 | 只静态可查，不用持续表情绕预算 |

## 9. 最低验证与量化方法

    git diff --check
    pnpm docs:check
    python scripts/test-expression-lifecycle.py
    python scripts/test-voice-interaction.py
    python scripts/test-device-ui-protocol.py
    python scripts/test-device-ui-interaction.py
    python scripts/test-device-ui-renderer.py
    python scripts/test-display-completion.py
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-firmware-display-stack-budget.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-firmware-device-ui-stack-budget.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-firmware-voice-stack-budget.ps1
    pnpm --filter @stackchan/console exec vitest run src/views/companion/expressions/selectionLabel.test.ts
    pnpm --filter @stackchan/console run build

服务端修改追加DeviceExpressionService相关测试与新增presence映射/能力/角色隔离测试，类名以实际仓库为准。
固件改动追加既有五类最小宿主、LAN/HTTPS两种profile构建、候选尺寸和transport/voice/UI栈预算。
实体资源测量先B0后候选，同帧率、同模式、同采集窗；至少300秒待机、10次语音含取消、50次菜单开关。
FPS以同代次有效窗口SPI完成数量/时间计算，提交帧/目标帧率/软件渲染速率不可代替。
录音、处理、讲话分别报告样本和完成率；若窗口缺失或新版本无采集，写UNKNOWN，不继承历史60.544FPS。
至少10次混合状态可读性判断，正确≥80%；单用户小样本只说明本设备理解情况。
所有真实场景需记录固件提交/二进制摘要、服务器版本、帧率、网络、heap/stack和失败，不保存真实私人语音。
资源不足时必须交付可验的服务端/控制台降级与未完设备边界，不能把BLOCKED写为实体完成。

## 10. AI 交付清单

1. 前台音频与后台目标独立映射、优先级、quiet门禁和有限emotion表。
2. turn/source/revision绑定、迟到清理、角色隔离与旧固件降级实现。
3. 软件预览与设备提交/实际呈现/UNKNOWN的明确区别。
4. B0与候选容量、内部RAM、栈、菜单/语音并发报告；门禁未过给减法方案。
5. 软件宿主/前端/服务端验证与实体样本分别记录；FR/AC逐项证据。
6. 更新相关工作流与全局总览；一个中文任务提交，不外推、不自行刷写/部署。
7. 不新增相机/运动/大资源包、不改分区、不把goal RUNNING当语音PROCESSING、不以情感惩罚维持活跃。
