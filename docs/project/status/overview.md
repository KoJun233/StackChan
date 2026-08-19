# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-19
- 当前分支：`codex/int-013-streaming-tts`
- 实现基准：`13987b0`
- 最后验证提交：`29e8c36`
- 当前部署：LAN HTTP development mode
- 生产边界：HTTPS-only

## 当前结论

`EVT-003` 已通过 PR #21 合入 `master`。用户要求继续暂停连接器。`INT-013` 的 SCV2 有序分段、SCV1 双向兼容、触摸取消和晚到分片丢弃均已完成自动化与实机成功路径验收。

当前 LAN 运行 INT-013 `a04ae0b` server/V32 与 CoreS3 `29e8c36 / motion_disabled / OTA=true`；设备使用 SCV2，分段顺序、播放中停止和后续回合实机正常。

## 工作流摘要

| 工作流 | 状态 | 当前事实 | 下一步 |
| --- | --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | INT-013 server/V32 已部署，SCV1/SCV2 鉴权边界与实机链路正常 | 等待人工审核合并 |
| [前端](frontend.md) | STABLE | INT-013 不修改页面；EVT-003 已合入 | 保持现状 |
| [固件](firmware.md) | READY_FOR_REVIEW | CoreS3 `29e8c36` 已通过 INT-013 实机成功路径 | 等待人工审核合并 |
| [部署](deployment.md) | STABLE | LAN 运行 INT-013 `a04ae0b` server/V32 与 CoreS3 `29e8c36` | 保持当前运行态 |

## 当前能力地图

- 对话：流式文字聊天、本地唤醒语音、完整回复、连续对话、触摸取消和隐私安全诊断；INT-013 候选增加有序分段播放。
- 陪伴：角色容器、可选角色音色、确认记忆、建议过滤、相关检索、周期提醒、免打扰和有界主动关心；人设与陪伴数据按角色隔离。
- Agent：受控 ReactAgent、Skill ZIP、只读 Tool、页面管理的 Streamable HTTP MCP Client 和语音动作确认。
- 设备：配对/JWT/WebSocket、唤醒模型 OTA、八状态表情包和应用 A/B OTA。
- 数据与运维：个人数据搜索/导出/删除、7 日/4 周备份、隔离恢复和健康中心。
- 外部通知：固定设备集成、一次性令牌、幂等 REST/MCP、可靠单飞、互动回执，以及可选的同集成确定性原文摘要。

完整合并记录见[里程碑索引](../milestones.md)，长期架构约束见[ADR 索引](../decisions/README.md)。

## 版本与运行态

- Git：`master` 合并提交 `13987b0`；当前任务分支基于该提交。
- LAN server：INT-013 `a04ae0b` 镜像，Flyway V32；运行镜像和保留的旧镜像信息见[部署状态](deployment.md)。
- CoreS3：运行实机候选 `29e8c36`，应用 OTA 已启用，`motion_disabled`。
- 实机固件提交只作为运行候选，不能替代 `master` 作为新任务分支基线。

## 最近验证

- INT-013 工作树基于 `13987b0`；服务端全量 377/377、空库 Flyway V1..V32 通过。ESP32-S3 协议 profile 编译通过，固件大小 `0x37880`、最小应用分区余量 93%。
- Node v24.19.0、pnpm 11.19.0：主控制台 Vitest 25 个文件 70/70、类型检查和 production build 通过；旧控制台 5 个文件 23/23、类型检查和构建也通过。
- INT-013 LAN：新备份与隔离恢复通过，只替换 server；运行库迁移到 V32，健康页和首页为 200，SCV1/SCV2 未认证入口为 401，启动日志无错误。
- `bd818f0` 应用 OTA 为 `INSTALLED`，从 `7e7c55f` 更新后跨三个心跳稳定，NVS 设备身份、OTA 能力和 `motion_disabled` 保留；用户确认分段顺序和下一回合正常，触摸停止失败。
- `29e8c36` 修复取消顺序和按下判定；ESP32-S3 协议 profile、语音栈预算和 LAN HTTP Quad 构建通过，应用大小 `0x14f3a0`、分区余量 56%。应用 OTA 为 `INSTALLED`、无失败码，用户确认触摸立即停止、晚到分段不播放且下一回合正常。
- EVT-003 合并提交为 `13987b0`，任务提交 `8378e5f`；其服务端 367/367、空库 Flyway V1..V32 和前端 70/70/类型检查/构建证据保留在合并历史。
- `0e92d58`：服务端 322/322，通过空 PostgreSQL 的 Flyway V1..V27。
- `cf26fd7 -> 7e7c55f`：真实应用 OTA 为 `INSTALLED`，新镜像跨心跳稳定，NVS 保留。
- 用户确认 OTA 后普通唤醒对话、播放中触摸停止和后续再次对话正常。
- `EVT-001`：服务端 341/341，通过空 PostgreSQL 的 Flyway V1..V28；真实 Streamable HTTP MCP 客户端完成 Bearer 鉴权、工具发现和通知入队。
- Node v24.15.0：前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过；路由专项 3/3 通过。
- 删除迭代：服务端 346/346、删除定向 16/16；前端继续为 69/69，并完成类型检查和 production build。
- `ROLE-001`：服务端 348/348，通过空 PostgreSQL 的 Flyway V1..V29；角色/会话定向 13/13。Node v24.19.0、pnpm 11.19.0 下前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过。
- ROLE-001 LAN：部署前备份和隔离恢复通过，只替换 server；运行库迁移至 V29，首页 200，角色/设备 API 未认证均为 401，默认角色和全部陪伴数据角色归属完整。
- `ROLE-002`：服务端 351/351，空 PostgreSQL 成功应用 Flyway V1..V30；角色音色与回退定向 24/24。Node v24.15.0、pnpm 11.19.0 下前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过。
- ROLE-002 LAN：发布前新备份与隔离恢复通过，只替换 server；运行库迁移至 V30，健康接口和首页为 200，角色/设备 API 未认证为 401，角色音色前端资源已发布，启动日志无错误。
- 全量服务端首次运行遇到既有异步 MockMvc 用例的瞬时 `ConcurrentModificationException`；该用例单独重跑及随后完整 341/341 重跑均通过，未修改无关实现。
- 用户授权后已生成并恢复校验新 PostgreSQL 备份，仅替换 LAN server；运行库成功迁移到 V28，健康接口和外部通知静态资源为 200，未认证 REST/MCP 均为 401，启动日志无错误。

## 依赖与阻塞

- `EVT-001`、`ROLE-001`、`ROLE-002`、`EVT-002`、`EVT-003` 已合入；INT-013 无实现前置阻塞。
- 用户明确要求暂缓日历；整个 `CONN-*` 连接器组不在当前任务内。
- 用户已确认基础外部通知测试正常；免打扰延期和离线重连两个专项场景尚未执行，不阻塞代码审核或提交整理。
- 用户明确要求本轮不做固件 OTA 回退演练；自定义表情实体素材和公网生产部署仍需要各自单独授权。

## 下一步

INT-013 实现、LAN server 部署和 CoreS3 成功路径验收均已完成。下一步推送当前任务分支，由用户创建 PR、人工审核并合并；不连接 COM3、不再 OTA，本轮未做回退演练。
