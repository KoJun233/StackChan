# R01 自然语音的匿名实体测量

- 文档状态：REFERENCE
- 日期：2026-10-14；Asia/Shanghai
- 依据：[R01](../requirements/companion-v1/R01-natural-voice.md)、[ADR0077](../project/decisions/0077-measured-private-natural-voice.md)、[软件交付](../project/baselines/companion-r01-natural-voice.md)

## 开始条件

本次仅开发，不安装、不刷写、不调用真实模型或 Bark。后续用户明确授权安装时，先核对最终收据的源码 SHA、JAR、V1–V61、双固件六制品、LAN 余量和栈门槛；执行[个人数据停写备份与隔离恢复](personal-data-backup.md)及对应部署/应用 OTA 手册。不能把历史 a37d220/r3 的测量归为本候选。生产保持 HTTPS-only，LAN HTTP 只在局域网开发使用，不因测量修改模式、凭据、相机或运动开关。

## 采集口径

保留匿名计时表，录音与真实话语如需辅助定位只由用户在本地保存，不提交仓库。建议用同一条高帧率视频/音轨同时覆盖用户停说、设备可见状态、首声和触摸静音，记录帧率、时钟分辨率及方法；不从两台不同设备的墙钟相减。若观察误差接近门槛，结论保持 UNKNOWN 并重新采集。操作记录中的配置、网络条件和方法只能写非秘密信息。

| 时间点 | 观测方式 | 能证明什么 |
| --- | --- | --- |
| 实际停说 | 同一物理音轨中用户最后有效声音 | 与端点之差包含 VAD 尾静音 |
| 端点确定 | 新固件本地 SPEECH_CAPTURED，或可核对的同源端点标记 | 已结束采音，不等同服务器收到请求 |
| 本地反馈 | DEVICE_MONOTONIC 接受 PROCESSING；实体画面另测可见变化 | 接口接受不能当 LCD 帧完成 |
| 播放开始 | 新固件首次成功 codec 写入 | 参考播放时间，不能当扬声器首声 |
| 实际首声 | 同一物理音轨中设备第一声 | 实体首声性能门槛 |
| 实际触摸与静音 | 同一视频/音轨的触摸动作与扬声器静音 | 停音逐样本门槛 |

短聊成功样本至少三十条，覆盖安静 WAKE、安静 PTT、低音量、词中/句间停顿及背景条件。先验证 600ms 停顿继续采音、约 800ms 端点、不说话不上传、8 秒截断澄清；没有参考真值的背景噪声不能声称识别正确。另留断网、配置错误、响应非法、内存不足及恢复尝试，不删失败、超时和慢样本。复杂天气/日历/目标/手机工具任务归 TOOL，不混入普通短聊时延。

触摸停止至少十次，包括网络阻塞和晚到音频；逐次记录触摸至静音及最大值，每次要求不超过 250ms。验证安静后旧回合音频、表情和确认不复活，下一次合法输入能继续；停止声音不默认暂停已创建目标。连续开启时复验三次追加、两分钟禁止新启动但不截断已合法开始的讲话；PTT 不续听。达到上限时先澄清，不用真实写动作证明安全。

## 空表与离线分析

复制[空模板](../project/baselines/voice-quality-samples-template.csv)至仓库外的私有目录；每行只填固定列，不加谈话、设备 ID、用户名、URL、配置载荷或供应商自由错误串。`sample_id` 使用 S001 至 S999999，唯一且不能反向映射到真实谈话。`evidence_type` 为 REAL 或 SYNTHETIC，`kind` 为 SHORT_CHAT、TOOL 或 CANCEL，`measurement_source` 为 DEVICE_MONOTONIC 或 PHYSICAL_OBSERVATION，`result` 为 SUCCESS、FAILED、CANCELLED、TIMEOUT 或 MISSING。`condition` 仅允许：QUIET_WAKE、QUIET_PTT、BACKGROUND_WAKE、BACKGROUND_PTT、LOW_VOLUME、WORD_PAUSE、SENTENCE_PAUSE、NETWORK_DELAY、NETWORK_FAILURE、MEMORY_PRESSURE、RECOVERY、OTHER_CONTROLLED。失败码只填脚本允许的固定枚举；无证据留空，不写 0。

DEVICE_MONOTONIC 行只填本地反馈、codec 开始；PHYSICAL_OBSERVATION 行填实际首声、停说至端点/首声、触摸至静音等实体值。一行不混来源，同一次尝试的不同来源用不同匿名 ID，分别计数，不能合并为总体尝试数。所有 `_ms` 值为 0–300000 的整数；SYNTHETIC 永远不满足现场验收。

```powershell
python -B scripts/analyze-voice-quality.py D:\private\voice-samples.csv --output D:\private\voice-report.json
python -B scripts/test-voice-quality-analysis.py
```

默认空表正常输出 UNKNOWN。非法列、自由文本、重复 ID、混来源或非法数值以退出码 2 拒绝，错误消息不回显单元格。JSON 按证据、来源及业务类型分组，保留总尝试、成功/失败/取消/超时/缺失、固定失败码、有效/缺失数量、所有慢样本和最大值。成功记录缺值仍 UNKNOWN。

实体普通短聊成功且有效至少三十条才计算 nearest-rank：升序位置 ceil(p×N)，p50 ≤2500ms、p95 ≤5000ms。不能剔除慢成功样本或事后调整门槛。本地反馈每次 ≤200ms；失败后仍有反馈观测的样本保留。取消至少十次且每次 ≤250ms，工具不为十条取消数据计算稳定 p95。停说至端点及停说至首声单列，不能隐去 VAD 尾静音。

## 控制台和交接

设备详情的语音诊断独立加载回合与质量汇总；失败会显示读取失败，切设备时旧请求不得覆盖新设备。管理员接口为 `GET /api/v1/companion/voice-quality?deviceId=...&from=...&to=...`，ISO8601、from < to、默认 24 小时、上限七天，未知设备 404、无权限 401/403、非法范围 400，响应 no-store。返回固定计数/阶段耗时，不含正文或录音。普通短聊分组来自实际无工具路由，取消/失败/工具分开；少于三十条不给百分位。DEVICE_MONOTONIC 百分位只能作参考，实体门槛以以上 CSV 为准。

新诊断保留七日；单消息或整会话删除清除关联记录，并保存七日匿名墓碑拒绝晚到事件。升级前无关联旧诊断不能推定已清除，仍按原七日到期清理；匿名导出文件由用户独立删除。提交交接时写明候选版本、采集方法、样本数量、错误/缺失/取消值、未通过项与下一条操作，不以自动化测试代替自然使用或十四天观察。
