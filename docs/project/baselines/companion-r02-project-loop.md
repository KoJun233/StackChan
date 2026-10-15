# R02 一次项目闭环交付与交接

- 文档状态：ACTIVE
- 更新：2026-10-15
- 任务分支：`codex/companion-r02-project-loop-20261015`
- 基准：`master@a96181673e0c401cf63b3fdc920dd03511ab65b1`
- 状态：READY_FOR_REVIEW（软件实现与自动验证完成）；真实场景、视觉与安装待验。

用户确认合并 PR #49 后要求继续开发下一项，本轮只实施 [R02](../../requirements/companion-v1/R02-project-loop.md)。历史 R00/R01 已合入，原工作树不更新、不暂存、不清理。原十二条文件清单中九项初始可读摘要保持、三项动态缓存初始不可读，原 short status 未变化，详见本任务 `.tools/r02/original-worktree-final-comparison.json`。

## 实际交付

明确一次委托从网页聊天或设备语音进入现有 intake，认证上下文固定 device/role，稳定 turnId 用作意图键。能力咨询、示例和否定不读取页面，不调用规划或执行；缺关键资料每次问一项，草稿十分钟过期。网页新建带 UUID 键；未带键的旧接口与原周期运行保持兼容。保存现在一次的 ONCE 同事务领取运行意图，提交后执行，重试不会创建第二份任务。未来 ONCE 的调度与手动继续同样共用修订运行槽；暂停后恢复也不能同修订双跑。明确把项目改为周期安排时返回原 occurrence 语义，旧成果和反馈保留且不可再由项目反馈入口修改。

输入 URL 经公开 HTTPS 校验、去重并保存，认证 query（包括常见预签名参数）拒绝。独立来源模式只从用户指令确定，模型不能增加联网权限。每次 read_public_source handler 前验证固定来源，网页内新链接不能扩大范围。旧读取器实际拒绝 3xx，本轮保持 fail-closed，未声称支持安全重定向。没有增加搜索引擎或私人连接器。

五区成果仍保存于 run.output；来源记录包含原始读取入口、实际 URL、E 编号与时间。没有读到的事实写未知；缺关键来源、重复方案和伪造事实转 BLOCKED，并保留有限信息。模型不能写认可状态或声称购买/实体目标已完成。新卡片从独立待阅集合取最多三条，跨任务分页；详情先显示成果，再显示全文和过程。

反馈入口绑定具体 runId、task.revision、review.resourceVersion 与幂等键。认可只认可该成果；偏好修订仍在原 task，保留旧 run、来源、反馈以及 supersedesRunId。可只保存或保存并重新比较；两种执行方式共用 revision intent，旧日期 occurrence 不会吞掉新修订。当前偏好明确撤销手机交付时收缩为 CONSOLE；修改偏好不会新增手机外发权限。暂停与取消阻止旧结果交付，取消有独立原因，并拒绝旧 :resume/:run/:update 等入口绕过。隔日查询只读取原结果、修改与下一项决定。

## FR 与验收证据

| 范围 | 实现与自动证据 | 人工边界 |
| --- | --- | --- |
| FR01–05；AC01–04、16 | Intake/Planner/Orchestrator 测试覆盖实际 WEB/VOICE 路由、ONCE/CONSOLE、咨询示例零读取、缺来源、过期草稿与固定 scope | 实体 ASR 与真实收单待验 |
| FR06–08；AC05–06 | ProjectSourcePolicy/PublicSourceScope/ProjectAgentBoundary/ProjectArtifactRenderer 覆盖逐步 allowlist、显式空工具、全部来源、失败局部、原文引用与重复方案 | 假页面是合成夹具；真实二至三个 HTTPS 来源待提供与验收 |
| FR09–10、13；AC08–09、12、18 | 控制台待阅/详情/表单/API 测试覆盖版本、明确认可、UNKNOWN 分离、切伙伴丢旧响应、离线保留成果 | 360/1280px 视觉截图未取得，见下方限制 |
| FR11、15；AC07、10–11、13、17 | ProjectLoopFeedbackTest：真实 PG 事务/并发、原 task 修订、旧成果保留、同 revision 唯一槽、晚到失效、容量失败与明确重试、取消守卫、收据回查 | 偏好确实改善真实推荐理由需日用验收 |
| FR12、14；AC14–15 | Intake 与真实服务查询测试：跨日摘要无新运行、多候选序号选择、不同对象拒绝误配，后台执行不占自然入口 | 隔日真人问答待验；没有声称实体首声达标 |
| FR16；AC19–20 | MVC 管理员/CSRF/角色权限、旧 ID 深链与 V61→V62 迁移、隔离 dump/restore 保持历史 | R03 尚未实现，其未来迁移为 R03_PENDING；本轮仅证明当前结构保留原 ID 与关联 |

完整服务端 1092/1092、失败/错误/跳过 0，报告 `.tools/stackchan-companion-tests-59f71ccde7e4454abf67f34596ff94ba`；项目事务 17/17、迁移恢复 6/6，定向兼容回归 63/63。控制台完整 46 文件 204/204、vue-tsc、十文件 ESLint/四文件 Stylelint 通过。docs:check、检查器 7/7 与 diff 检查通过。既有 Surefire/Vite/Vitest 退出警告保留。720 项服务端、866 项控制台源码指纹和最终提交、生产 image/JAR/62 SQL/全部静态资源由 `.tools/r02/candidate-receipt.json` 核验封存；缺失或失败收据不能视作候选通过。不把先前 R01 成绩冒充新增代码验证。

## 接口与受控错误

Task 兼容增加 revision/cancelledAt/cancellationReason；Run 增加 taskRevision/attempt/review。旧枚举与字段不变。新增：

- `GET /api/v1/delegations/review-ready`：沿管理员现有过滤范围，最多三张待阅卡。
- `GET /api/v1/delegations/{taskId}/project-summary`：原结果、反馈历史、范围、来源数与下一项决定，纯读。
- `POST /api/v1/delegation-runs/{runId}/feedback`：四种反馈，修改偏好支持 SAVE_ONLY/SAVE_AND_RUN。
- `POST /api/v1/delegations/{taskId}:run-revision`：明确执行已保存的当前修订。
- `GET /api/v1/companion/operations/{key}`：action 限三种委托操作，必须给 deviceId/roleId，可给 resourceId；只返回 UUID/版本/状态/固定错误码。

| HTTP | 固定错误 | 行为 |
| --- | --- | --- |
| 400 | INVALID_REQUEST / 原 DELEGATION 字段错误 | 不保存无效业务；不回显正文 |
| 401/403/404 | 既有会话错误 / ACTION_NOT_ALLOWED / DELEGATION_NOT_FOUND | UUID 不授予身份，归属与已删除资源不泄露正文 |
| 409 | REVISION_CONFLICT / IDEMPOTENCY_CONFLICT / INVALID_STATE | 刷新所见版本后由用户决定，不静默覆盖 |
| 409 | 既有 DELEGATION_CLARIFICATION_REQUIRED | 保持旧追问合同；明确任务未保存 |
| 422 | EVIDENCE_REQUIRED / CAPABILITY_UNAVAILABLE | 缺证据/能力不得伪造成功 |
| 429 | EXECUTION_CAPACITY_BUSY / DELEGATION_CAPACITY | 不增加工作者；已保存的意图可能以 FAILED 呈现并供明确重试 |
| 503 | DEPENDENCY_UNAVAILABLE / OPERATION_PENDING | 写入响应未知先查原 key，不换键盲发 |

COMMITTED 只证明业务意图保存，不证明比较、渠道或实体行为完成。写入超时文案保持“状态暂未确认”，不把未知误说成未保存。前端保留原 key，回查后读取资源；PENDING 保持处理中，失败不隐式重做。

## 修订关联示例

以下标识及页面均为合成示意，不是真实项目记录：task `11111111-1111-4111-8111-111111111111` 的首版 run `22222222-2222-4222-8222-222222222222`、taskRevision=1、reviewVersion=1。用户修改“线缆少优先”后，原 run 保留；同 task revision=2，新 run `33333333-3333-4333-8333-333333333333` 的 supersedesRunId 指回首版。反馈只关联首版与该修订，不新建 task。最终认可只给所见的新 run，不将运行状态改成 ACCEPTED。

## 迁移、隐私与验证操作

仅新增 V62。隔离 PG 覆盖空库全部迁移、V61 含 V57–V61 合成数据升级、真实 pg_dump/pg_restore 后升级，旧十八表全列、六十一条 Flyway、五项加密摘要、UUID 与 SENT/UNKNOWN 保持。删除任务/设备/角色清除私人反馈与审阅，收据保留无正文去重墓碑。没有访问真实运行库。

```powershell
./scripts/test-server-linux.ps1
# 依赖引导见 docs/project/development.md；junction 本机直接用 Node 启动已安装 Vitest。
pnpm --filter @stackchan/console exec vitest run
pnpm --filter @stackchan/console run build
git diff --check
pnpm docs:check
```

生产候选使用 `scripts/build-companion-candidate.ps1`，仅打包本任务提交；最终 image/JAR/62 SQL/全部控制台文件与来源指纹保存在 `.tools/r02`。固件源码未改，沿用 R01 候选与其历史 4432 B 余量，不声称本轮重刷或重新实测。

## 未完成与下一条精确操作

软件完成后下一步先审核任务差异及收据。2026-10-15 用户明确要求“那是你推送吧”，授权只推当前 R02 分支；推送与远端 SHA 核对结果见 `.tools/r02/push-receipt.json`。推送前授权记录与本交接并入同一任务提交，候选重新绑定该提交。PR 创建、审核、合并由用户操作。安装、刷写与真实模型/Bark 调用仍待独立明确授权，不能直接以开发指令替代。

FIELD_PENDING：在选定候选安装后，用户或测试者给二至三个真实可读 HTTPS 页面，按 R02 固定场景完成收单、事实/未知项、偏好修订、隔日查询与认可/暂停；不购买。R01 实体计时、第三真实失败和 R06 长期记忆仍未验证。

VISUAL_PENDING：Browser 无可用实例；Computer Use 因 Windows 无法可靠识别浏览器当前 URL 而终止，已停止该工具输入。未伪造截图。360/1280px 布局、键盘视觉焦点与截图仍待人工验收；组件行为和类型检查不能替代视觉证据。故不宣称 R02 全部现场验收完成。
