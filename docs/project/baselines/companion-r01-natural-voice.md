# 成人桌面伙伴 V1：R01 自然语音交付

- 文档状态：COMPLETED（软件；实体与安装待验）
- 日期：2026-10-14；最终交付核验 2026-10-15（Asia/Shanghai）
- 分支：`codex/companion-r01-natural-voice-20261013`
- 基准：`origin/master@989b3d9f591a854d9ca26fe05b78fb4af53329b5`
- 依据：[R01](../../requirements/companion-v1/R01-natural-voice.md)、[公共契约](../../requirements/companion-v1/00-shared-contracts.md)、[R00](companion-v1-baseline.md)、[ADR0077](../decisions/0077-measured-private-natural-voice.md)、[实体手册](../../runbooks/natural-voice-quality.md)

## 来源、授权与交付程度

用户要求继续“001”，依据需求顺序实施 R01，并指定暂不推送，R00/R01 本地整理后再统一推送。2026-10-13 刷新 origin/master 仍为 989b3d9；新分支从其创建，带入已完成 R00，再合成相对 master 恰好一个中文任务提交。原 R00 分支及 a1a2e642a7651b00c2b04fc36f11a8ae2375e9a3 保留；用户 D:\stackchan 的原分支、未提交类型和运行期缓存/日志未使用或清理。2026-10-15 用户进一步明确要求“推送吧”，只授权本任务分支外推；远端 SHA 和结果见 `.tools/r01/push-receipt.json`。没有 PR 创建、安装、刷写、真实模型/Bark 调用或十四天观察。

完整最终 SHA、干净状态、测试源码指纹、image/JAR/61 项 SQL/全部控制台资源和双固件收据位于本工作树 `.tools/r01/candidate-receipt.json`。生产构建入口为 `scripts/build-companion-candidate.ps1`，固件最终构建记录在 `.tools/r01/firmware-candidate-receipt.json`。最终 SHA 外置避免提交正文自引用；工作流元数据中的最后验证提交 989b3d9 是存在的祖先，不冒充新增代码成绩。源码、状态、交接同一任务提交。

R00 历史证据仍在原 R00 工作树 `.tools/r00/candidate-receipt.json`，本记录不把它当新的 V61 候选。历史最近只读运行核查是 2026-10-12 的 a37d220/V60/r3/motion_disabled；本次没有再次连接运行环境，不能声称当前在线状态新鲜。

## 三项基线问题

| 基线 | 本轮复现/改变 | 剩余事实 |
| --- | --- | --- |
| F01 用户说记忆似乎都在一个 session | 保留 R00 的确认记忆/V57；近期语音只取成功配对，当前纠正/换题覆盖旧上下文 | 用户原始交互、跨天场景未取得；R06 的长期替代/跨天接续尚未实施，不能声称已修复全部记忆问题 |
| F02 用户说回答等待五六秒 | 端点立即进入本地 PROCESSING；上限澄清；普通短聊实际路由分组；实际 codec 写入观测与独立实体采集 | 未运行真实供应商或测量扬声器首声，不能承诺已降至 2.5 秒 |
| F03 匿名合成：模糊续讲与能力/通知请求 | 唯一近期对象才能续讲，能力查询/否定/引用不外发，缺工具返回真实限制 | 这不是第三条真实失败；第三真实场景仍待用户自然反馈 |

## 实现与 FR 对照

| FR01 | 实现与软件证据 |
| --- | --- |
| 01 | 现有 WAKE/PTT 及配置合同保持；voice interaction/控制台设置回归 |
| 02 | 128000 个单声道 PCM16 样本上限；等待开口不消耗讲话缓冲，宿主实际采音函数回归 |
| 03 | 本地 VAD 命中才上传，空采音测试无上传 |
| 04 | 100ms 窗口/8 静音窗保持；600ms 停顿继续、800ms 收尾；低音量/背景识别需实体补验 |
| 05 | 端点与 PROCESSING 状态接受在等待上传完成前测量，失败不补 0；200ms 实体门槛待验 |
| 06 | 限时原因可选字段；显式原因或已满 PCM 上限先澄清，不提取记忆或执行写动作 |
| 07 | PTT 松手提交一次、不追加；WAKE 提交/取消原路径保持 |
| 08 | 原连续默认关闭、3–8 秒开口、三次追加、120 秒新启动边界回归 |
| 09 | 已在 120 秒内合法开始的采音跨边界可完成，不重开绕限 |
| 10 | 真实麦克风采样后报告输入就绪；失败不报告恢复 |
| 11 | 保持短聊 1–2 句/尽量 40 汉字提示与实际快路径，模型自然性需现场确认 |
| 12 | 正文含最终标点 ≤160 Unicode 码点，emoji/长答边界回归 |
| 13 | 表情正文剥离；完整正文一次 TTS、单 WAV，SCV1/2 回归 |
| 14 | 无工具普通闲聊与事实/目录/业务读取分开；服务器实际 route 标记，不信模型声明 |
| 15 | Bark 明确意图、缺正文追问；能力、否定、引用、续讲、旧发送不赋新写授权 |
| 16 | 三十分钟、四轮完整成功配对，过滤失败/孤立/重复 assistant |
| 17 | 当前纠正清旧历史/主动/模型上下文，不把修改自动写入长期记忆 |
| 18 | 换题切断旧引用；续讲只接唯一最新对象链，无对象/同刻歧义追问 |
| 19 | 结束/忙碌/拒绝固定收尾且不调用模型或提取记忆；引用/否定保持正常语义 |
| 20 | 沿既有真实委托/提案状态与结果，不新增或假造 R02 目标状态 |
| 21 | 取消仍绑定当前回合，不隐式取消持久委托或撤销长期授权 |
| 22 | 本地停音在网络取消前，实际源码顺序与 codec 锁后取消回归；十次 ≤250ms 待实体 |
| 23 | 晚到生成/音频/情绪/确认与延后 UI 通知验证 turnId/取消，不复活旧回合 |
| 24 | 设备单前台回合与音频锁保持；codec 获取锁后再次检查取消 |
| 25 | 官方无词映射 NO_SPEECH，配置/网络错误保持固定故障分类 |
| 26 | 麦克风/断网/内存/非法响应与恢复仍走原固定错误码 |
| 27 | 保留主线 FAILED 有限投递重试，UNKNOWN 不重发；语音续讲不继承 Bark 写权限 |
| 28 | 服务端设备/会话绑定才有范围；保留 R00 网页请求 deviceId 不授予设备权限 |
| 29 | 旧字段缺失接受并显示未知；新字段严格类型/来源；旧 API/SCV1/2 保持 |
| 30 | 双 profile、五栈门槛、容量检查；不改分区、模型、相机或运动 |

主要源码：`VoiceTurnService`、`VoiceDialogueControl`、`VoiceConversationContextPolicy`、`AgentOrchestrator`、`VoiceCaptureLimitPolicy`、`DeviceVoiceController`、`DeviceWebSocketHandler`、`VoiceTurnDiagnosticsService`、`VoiceQualityService`、`PersonalDataService`、V61、设备概览/API/诊断辅助函数，以及固件 voice_control/voice_service、companion_hardware、device_transport/protocol。没有新增第二套诊断存储、调度或长期记忆系统。

## AC 与可复现场景

| AC01 | 软件证据/验收状态 |
| --- | --- |
| 01、02 | voice-interaction/input-timing：默认不开连续、PTT 松手不追加；软件通过，实体待验 |
| 03 | input-timing：600ms 停顿不截断、8 窗静音收尾；轻语音/真实噪声待验 |
| 04 | VoiceTurnService/Controller/CaptureLimitPolicy：上限澄清，零写动作/零记忆提取 |
| 05 | voice-interaction/input-timing：三次追加、120 秒新启动/已启动完成 |
| 06 | VoiceConversationContextPolicy：最多四轮完整成功、失败/孤立过滤 |
| 07 | 过期历史与无对象确定性追问，无模型造旧对象 |
| 08 | topicBoundary/bareTopicSwitch：旧主动与历史隔离，指定 B 保留 |
| 09 | currentCorrection：按当前表达路由，无自动长期替代 |
| 10 | 普通短聊快路径/提示通过，真实模型 1–2 句及自然性待验 |
| 11 | AgentOrchestrator/VoiceFactFollowUp：工具拒绝/失败不快路径猜天气 |
| 12 | BarkNotificationTool：能力、续讲和否定不得外发，不复用已完成发送 |
| 13、14 | cancellation/codec/input-timing/expression/LVGL：本地先停、晚到丢弃、下一轮恢复；实体停音时间待验 |
| 15、16 | DashScope/VoiceTurnService：无词与配置/供应商故障独立固定码 |
| 17 | 服务端与控制台：缺记录、旧提前 marker、倒序/重复/不足三十样本显示未知 |
| 18 | 取消服务及委托生命周期回归：当前声音停止不取消持久目标 |
| 19 | VoiceDialogueControl 的引用/否定/忙碌组保持，未撤销无关许可 |
| 20 | 软件统计、空 CSV 与分析脚本通过；真实三十短聊/十次停止均 UNKNOWN |

以下三十个受控功能场景分别有断言，全部通过（30/30，误触发写操作 0）；这是实际源码/模拟模型的行为回归，不是实体 ASR 或自然对话正确率：

| 编号 | 场景 | 证据 |
| --- | --- | --- |
| S01 | 等待开口三秒，无声不上传 | input-timing 实际采音函数 |
| S02 | 等待开口后完整八秒，不扣等待 | input-timing |
| S03 | 600ms 句内停顿后继续 | input-timing |
| S04 | 八静音窗约 800ms 端点 | input-timing |
| S05 | PTT 松手提交一次 | input-timing |
| S06 | 120 秒内启动、跨界讲话完成 | input-timing |
| S07 | 120 秒后不启动 | input-timing |
| S08 | 麦克风失败不报告恢复 | input-timing |
| S09 | 已取消输入不采样 | input-timing |
| S10 | 断网拒绝新追加 | input-timing |
| S11 | codec 正常写入仅报告首次 | input-timing |
| S12 | codec 打开失败不造播放开始 | input-timing |
| S13 | 首次写入失败不造播放开始 | input-timing |
| S14 | 等音频锁时取消不打开/播放 | input-timing |
| S15 | 打开或写入时取消不复活 | input-timing |
| S16 | 四轮/三十分钟成功历史 | VoiceConversationContextPolicyTest |
| S17 | 失败或孤立 assistant 不成事实 | VoiceConversationContextPolicyTest |
| S18 | 重复 assistant 不成两轮 | VoiceConversationContextPolicyTest |
| S19 | 同刻多个对象必须澄清 | VoiceConversationContextPolicyTest |
| S20 | 过期/无对象续讲不调用模型 | VoiceTurnServiceTest |
| S21 | 裸换题只问一次并清旧上下文 | VoiceTurnServiceTest |
| S22 | 当前纠正优先、不记录待续进展 | VoiceTurnServiceTest |
| S23 | 截断流式/普通输入均先澄清 | VoiceTurnServiceTest |
| S24 | 结束/拒绝/忙碌不提取记忆 | VoiceTurnServiceTest |
| S25 | 引用/否定控制词不结束 | VoiceDialogueControlTest |
| S26 | 长答含标点 ≤160、emoji 不断码点 | VoiceTurnServiceTest |
| S27 | 完整多句仍一次 TTS/单 WAV | VoiceTurnServiceTest |
| S28 | 事实查询必须取真实工具源 | AgentOrchestratorTest/VoiceFactFollowUpTest |
| S29 | Bark 能力/否定/续讲无写授权 | BarkNotificationToolTest |
| S30 | 配置/网络/TTS 错误与 NO_SPEECH 分开 | VoiceTurnServiceTest/DashScope 测试 |

## 数据、接口与隐私

V1–V60 原始 Git SQL/checksum 保持；V61 增可空会话外键与匿名墓碑。先绑定服务端已认证会话，失败在创建私人消息前停止。单消息/整段删除清对应会话诊断，其他会话与旧未关联行隔离；阶段和删除共享咨询锁，删除后晚到设备/服务器事件和重新绑定拒绝。墓碑七日不延长，不含私人内容；原诊断七日保留不变。升级后的旧记录没有伪造关联。

汇总 GET `/api/v1/companion/voice-quality` 仅管理员，默认 24 小时、最大七天、ISO 时刻且 from < to；不存在设备 404、非法 400、未授权 401/403，no-store。无录音/转写/正文/密钥/供应商自由错误串。可选 `measurementSource`、`endpointToLocalFeedbackMs`、`captureLimitReached` 复用既有设备事件；未知值 null。`diagnosticCompleteness` 完整需唯一且顺序一致的设备 capture/start/completed 及服务器各阶段；重复不提高完整度。

DEVICE_MONOTONIC 首声命名兼容字段实际观测 codec 首次写入，仅作参考；200ms 本地反馈是状态接口接受。实体声学首声、LCD 完成及触摸静音不伪造，匿名表为空。普通短聊 cohort 必须有实际 `voice_casual` 路由与成功播放，失败、取消、工具及缺失数量单列；至少三十个适用成功样本才给 ceil(p×N) 百分位，慢样本保留。分析工具三十真实实体短聊与十次真实取消分别判定，合成样本不能通过实体门槛。

## 本轮验证

| 检查 | 新鲜结果/证据 |
| --- | --- |
| 开始最小七类服务端 | 117/117；报告 722bfe22aca4496d9ea35e7c07d22ad6 |
| 对话/路由相关 | 184/184；报告 f2497f528247481687c87d4fb32e2c20 |
| 诊断/API/旧固件相关 | 最终 67/67；报告 552b3f1042e743d0b479b4f6661aa404 |
| 隔离 PostgreSQL | 15/15；报告 55ab7b86a1e84d80951851ca2e463a49；R01 十项 + R00 五项 |
| 完整服务端 | 1009/1009，失败/错误/跳过 0，BUILD SUCCESS；报告 `.tools/stackchan-companion-tests-6d25eb52a1dd497b82e98b20bcd2268c` |
| 控制台 | 定向 20/20；完整 46 文件 181/181；类型、六文件 ESLint/Stylelint 通过 |
| 离线分析 | 23/23；空表 0 真实/0 合成，UNKNOWN；`.tools/r01/analysis-tests.log` |
| 固件 | 新实际源码采音/codec/协议回归、原十二类宿主及 LVGL ASan/UBSan、相关七类复验、双 profile/五栈门槛通过 |
| 最终制品与文档 | 精确 HEAD、61 SQL/Flyway、全部控制台文件、双固件、docs:check/检查器 7/7/diff 见最终外置收据 |

隔离 PG 的 V60→V61 直接核验十二个已填表、五项加密配置/解密摘要、六十条历史 checksum 和 SENT/UNKNOWN 不变；真实事务并发证明阶段写入期间删除被同 turn 锁阻塞，完成删除后晚到事件不重建。R00 五项仍包含含 V57 升级、缺 V57 拒绝及合成 pg_dump/pg_restore。全部使用合成数据库、模型、工具；未访问运行库。

曾出现的回归均保留失败报告并修复后重验：V61 被 R00 历史 V60 夹具误复制，固定历史版本边界；设备可选字段误按必填字段数量校验，改为严格允许集合并逐字段验证；旧 nullable Boolean 构造发生拆箱，改明确解析且旧来源未知；倒序播放不能进入完整度/统计；隐私删除与晚到事件增加同事务锁；codec 回调 UI 锁移至 UI 任务。离线分析补齐失败/超时取消的逐值和全缺指标计数，23/23 重验通过。最终服务端和控制台仍有已有 Surefire 退出三十秒、Vitest/Vite 退出与构建大 chunk 警告，不将其说成修复或测试失败。

工具链：Node 24.19.0、本机 pnpm 11.25.0 仅作已安装脚本启动器；完整 Vitest 直接由同 Node 执行以避开任务依赖 junction 的 pnpm 运行状态限制。生产 Node 24.15.0/pnpm 11.13.1 固定依赖缓存；Maven 3.9.16/Java 21；ESP-IDF 5.5.5。engine、锁文件、用户依赖和供应商配置未修改。

## 固件资源、兼容与回退

LAN 应用 3,141,296 字节，3 MiB 槽余量 4,432 字节，最低 4,384；protocol 应用 214,048 字节。新增静态量共 248 字节，无新任务/队列分配；voice 栈 4,832、upload 2,256、playback 160、transport 9,024、provisioning 11,488 字节，五类 gate 通过。应用版本绑定最终 SHA，十二个制品和构建/栈日志在固件收据。分区与 srmodels 保持原 SHA；真实 heap/PSRAM 最小值、动态高水位和声学停音待现场，没有使用模拟值代替。

本候选尚未安装，不需要线上回退。日后授权安装前先停止写入、完整备份并内部隔离恢复，保持原匹配镜像与恢复点；V61 后回退必须使用匹配数据库备份与镜像，不直接将旧应用接到新库、删除迁移记录、repair 或 out-of-order。SCV1/2、旧取消接口及旧设备可选字段兼容。

## 未完成与下一条精确操作

SOFTWARE_READY_FOR_REVIEW；FIELD_PENDING / DEPLOYMENT_PENDING / PUSH_AUTHORIZED。三十真实短聊、十次停止、本地反馈每次 200ms、真实停说/VAD/首声、背景/低音量/短答自然性以及用户第三真实失败均未完成；R06 跨天记忆和 R02 项目闭环未实施。本任务不自动启动这些后续需求。

本次推送前重新刷新 master 仍为 989b3d9，任务分支相对主线恰好一个中文提交；授权、状态与交接并入同一提交，源码与既有回归输入逐项保持，重新核验文档及绑定候选。推送结果与远端 SHA 见 `.tools/r01/push-receipt.json`；下一步由用户在 GitHub 创建 PR、审核并合并，Agent 不创建 PR 或合并。若用户要求安装，则执行备份/恢复/容量门禁后按手册采集空模板；只有真实记录足够才判断实体性能，不用软件成绩代替。
