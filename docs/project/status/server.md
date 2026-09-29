# 服务端工作流

- 状态：BLOCKED
- 最后更新：2026-09-29
- 当前分支：`codex/body-motion-five-actions`
- 基准提交：`2f75da3`
- 最后验证提交：`2f75da3`
- 最后验证范围：本任务最终单提交源码完成 BODY-002 定向服务端 22 项回归；V52/V53 空库迁移与全量测试的 Windows loopback 错误见历史记录。状态字段以主线基准提交作可解析锚点；本任务提交自身的哈希无法写入同一提交

## BODY-002 当前进度

心跳在场转发、返场时间与自动动作申请的定向测试均通过；设备到服务端的真实短时状态变化和自动命令结果仍需用户现场参与，当前无法继续实机验收。动作与自动开关保持关闭。

已新增五动作命令结果持久化、设备/命令/模板绑定、严格 `body_motion_result` 事件、管理端查询接口；`SENT/ACCEPTED` 不推定实体完成，手动结果查询超过 15 秒无结果为 `UNCONFIRMED`，自动命令由五秒定时巡检在超过 15 秒时标记，晚到结果可纠正，终态不可降级。自动动作独立开关默认关闭，只在在线且已 armed、设备能力与校准有效、非 DND/今日安静时接受确定性事件；持久事件键去重，完成后冷却 10 分钟、每设备本地日最多 8 次。丢失自动执行结果后由后台定时巡检关闭自动开关，避免静默重试。网页文字聊天请求可指定设备；回复持续超过两秒才申请一次 `THINK`，完成或取消后撤销定时任务。新候选已通过定向测试并部署到原 8080。设备级“今天安静点”覆盖后续主动语音、随机无声表情和自动动作，普通提醒继续。

验证：BODY-002 定向服务端测试通过；V52/V53 在 PostgreSQL 空库和真实数据隔离恢复中迁移通过。全量 `mvn test` 执行 594 项，563 项无错误，31 项因这台 Windows 的 loopback `SocketException: Invalid argument: connect` 失败；这些失败集中于 iCloud 测试及依赖其应用上下文的既有测试，不能宣称全套通过。候选已部署在原 8080，运行库 V53，健康通过，旧固件设备重连；实体结果路径还没有设备端验证。

下一条精确操作：用户今晚回到设备旁后，先核验新版固件的服务端在场 true→false；若通过，再验证工作日返场自动 `LOOK_USER`。五模板、远程停动、顶部触摸急停与自动 `WAKE` 已有实机结果，不重复。2026-09-29 `DeviceEventServicePresenceTest`、`WorkdayRuntimeServiceTest`、`WorkdayCompanionServiceTest`、`BodyMotionAutoServiceTest` 定向 22 项通过，分别覆盖心跳在场布尔转发、返场时间条件与已 armed 的 `LOOK_USER` 申请；不能代替实际设备到服务端的端到端证据。Spring Boot/Testcontainers 的 `DeviceEventServiceTest` 另行尝试后在应用上下文启动时遇到此 Windows 已知的 Unix domain socket loopback `Invalid argument: connect`，4 项未运行到断言，不记为产品失败或通过。以下为 V51 历史交接，不能当作 BODY-002 当前状态。

## 当前目标

按评审深化或收缩功能，部署后交给用户测试。软件交付与部署已完成。

## 已完成

完成对话控制、单次拒绝、相关记忆召回、伙伴暂停与主题静默、可信资讯/简报、真实待办计数、统一播报视图、固定对象确认和跨来源简短回应。V50/V51 已迁移并发布。

## 正在进行

用户已于 2026-09-15 授权推送当前任务分支，等待其创建 PR、审核和合并。不自动启动新开发或使用观察。

## 下一步操作

推送唯一中文任务提交后由用户创建 PR、审核和合并；用户也可按[验收表](../companion-experience-acceptance.md)测试两个独立伙伴、记忆、主动暂停和简短回应，记录实际识别与听感问题。

## 阻塞项

无发布阻塞。浏览器插件缺少运行文件，因此真实点击未验证；复杂口语、真实模型、使用价值和实体动作不以自动化替代。曾被自动审批拒绝读取隔离异常日志，已通过正常应用配置的隔离演练解决启动问题，没有读取该日志。

## 关键文件

server/src/main/java/com/kj/stackchan/、server/src/main/resources/db/migration/V50__voice_topic_boundary.sql、V51__role_proactive_pauses.sql。

## 验证命令与最近结果

服务端 554/554、V1..V51 空库和 V49→V51 升级、JAR 打包通过，日志 server/target/short-response-regression.log。八个既有 Windows loopback 类未纳入，不宣称全测试套件通过。实际发布 V51，健康正常。

文档检查使用 `pnpm docs:check`、`pnpm docs:check:test` 和 `git diff --check`。前端回归使用 `pnpm --filter @stackchan/console test --maxWorkers=1`；构建使用 `docker build -f server/Dockerfile -t stackchan-foundation-server:companion-v51-20260914 .`。不重复宣称八个受限服务端测试类通过。

## 相关设计、计划和决策

[完整实施清单](../companion-completion-plan.md)、[ADR 索引](../decisions/README.md)、[发布与回退](../../runbooks/companion-v51-release.md)、[备份与恢复](../../runbooks/personal-data-backup.md)。历史合并能力查[里程碑](../milestones.md)。

## 安全与兼容性约束

保持 LAN 开发模式；生产必须 HTTPS。伙伴记忆独立，操作确认不跨角色、不替换失效目标。旧 API/深链接与普通、工作、外部通知语义保留。本次只授权推送当前任务分支；不创建或合并 PR、不推送 master，不刷写固件或开启动作。DPAPI 备份依赖原 Windows 用户/机器，本地卷不是跨机器灾备；旧镜像与 V51 的直接回退不作兼容承诺。
