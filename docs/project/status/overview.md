# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-13
- 当前分支：`codex/evt-002-interactive-notifications`
- 实现基准：`82fdde3`
- 最后验证提交：`82fdde3`
- 当前部署：LAN HTTP development mode
- 生产边界：HTTPS-only

## 当前结论

`EVT-001`、`ROLE-001` 与 `ROLE-002` 已通过 PR #17、#18、#19 合入 `master`。用户要求暂缓日历连接器，当前转入 `EVT-002` 互动通知，实现显式动作白名单、可靠回执和确认式语音执行。

当前 LAN 仍运行 ROLE-002 `b6cad0b` server/V30；EVT-002 尚未部署、提交或推送，固件未修改。

## 工作流摘要

| 工作流 | 状态 | 当前事实 | 下一步 |
| --- | --- | --- | --- |
| [服务端](server.md) | IN_PROGRESS | V31 互动通知回执、REST/MCP 状态和确认式语音动作已实现 | 完成提交级全量验证 |
| [前端](frontend.md) | IN_PROGRESS | 通知测试动作、回执展示和管理员手动回应已实现 | 完成提交级全量验证 |
| [固件](firmware.md) | STABLE | CoreS3 `7e7c55f / motion_disabled / OTA=true` | 后续两任务不改固件 |
| [部署](deployment.md) | STABLE | LAN 仍运行 ROLE-002 server/V30 | 获明确授权后再部署 EVT-002 |

## 当前能力地图

- 对话：流式文字聊天、本地唤醒语音、完整回复、连续对话、触摸取消和隐私安全诊断。
- 陪伴：角色容器、可选角色音色、确认记忆、建议过滤、相关检索、周期提醒、免打扰和有界主动关心；人设与陪伴数据按角色隔离。
- Agent：受控 ReactAgent、Skill ZIP、只读 Tool、页面管理的 Streamable HTTP MCP Client 和语音动作确认。
- 设备：配对/JWT/WebSocket、唤醒模型 OTA、八状态表情包和应用 A/B OTA。
- 数据与运维：个人数据搜索/导出/删除、7 日/4 周备份、隔离恢复和健康中心。
- 外部通知：固定设备集成、一次性令牌、幂等 REST/MCP 入队、离线保留、免打扰/忙碌延期、设备单飞、安全健康计数、受保护删除，以及可选已知晓/稍后提醒/完成回执。

完整合并记录见[里程碑索引](../milestones.md)，长期架构约束见[ADR 索引](../decisions/README.md)。

## 版本与运行态

- Git：`master` 合并提交 `82fdde3`；当前任务分支基于该提交。
- LAN server：ROLE-001 `9e526f8` 镜像，Flyway V29；运行镜像和回退信息保留在[部署状态](deployment.md)。
- CoreS3：实体验证候选 `7e7c55f`，应用 OTA 已启用，`motion_disabled`。
- `7e7c55f` 是压缩前实机候选，不是当前 `master` 祖先；只能作为运行版本，不能作为新任务分支基线。

## 最近验证

- EVT-002 当前工作树基于 `82fdde3`，提交后将重新执行提交级静态检查。
- `EVT-002` 工作树验证：服务端 360/360 及空 PostgreSQL Flyway V1..V31 通过；前端 Node v24.19.0、pnpm 11.19.0 下 25 个文件 70/70、`vue-tsc -b` 和 production build 通过。
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

- `EVT-001`、`ROLE-001`、`ROLE-002` 已合入；EVT-002 无前置阻塞。
- 用户明确要求暂缓日历；整个 `CONN-*` 连接器组不在当前任务内。通知摘要聚合也不与回执核心耦合，本轮保留为后续独立设计，避免改变 EVT-001 的确定性原文播报契约。
- 用户已确认基础外部通知测试正常；免打扰延期和离线重连两个专项场景尚未执行，不阻塞代码审核或提交整理。
- 固件 OTA 回退演练、自定义表情实体素材和公网生产部署仍需要各自单独授权，但不阻塞当前软件路线。

## 下一步

整理 EVT-002 为单一中文任务提交；随后等待用户决定是否部署页面验收。未经明确授权不部署、不推送、不连接 COM3、不刷写固件。
