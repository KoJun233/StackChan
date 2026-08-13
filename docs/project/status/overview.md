# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-13
- 当前分支：`codex/role-002-role-voice`
- 实现基准：`8de06be`
- 最后验证提交：`8de06be`
- 当前部署：LAN HTTP development mode
- 生产边界：HTTPS-only

## 当前结论

`EVT-001` 与 `ROLE-001` 已分别通过 PR #17、#18 合入 `master`，用户已确认角色容器测试正常。当前 `ROLE-002` 已实现角色可选 TTS 音色、语音/提醒角色路由和全局音色单次安全回退。

当前 LAN 已运行 ROLE-002 `b6cad0b` server/V30，等待用户复核角色管理页面与实体音色；分支尚未推送，固件未修改。

## 工作流摘要

| 工作流 | 状态 | 当前事实 | 下一步 |
| --- | --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | V30 角色音色、角色路由和回退完成，351/351 与空库迁移通过 | 按需部署人工验收 |
| [前端](frontend.md) | READY_FOR_REVIEW | 角色表单和列表已增加音色配置，69/69、类型检查和构建通过 | 按需部署人工验收 |
| [固件](firmware.md) | STABLE | CoreS3 `7e7c55f / motion_disabled / OTA=true` | 后续两任务不改固件 |
| [部署](deployment.md) | VALIDATING | LAN 已运行 ROLE-002 server/V30，健康、迁移和静态资源通过 | 用户复核页面与角色音色 |

## 当前能力地图

- 对话：流式文字聊天、本地唤醒语音、完整回复、连续对话、触摸取消和隐私安全诊断。
- 陪伴：角色容器、可选角色音色、确认记忆、建议过滤、相关检索、周期提醒、免打扰和有界主动关心；人设与陪伴数据按角色隔离。
- Agent：受控 ReactAgent、Skill ZIP、只读 Tool、页面管理的 Streamable HTTP MCP Client 和语音动作确认。
- 设备：配对/JWT/WebSocket、唤醒模型 OTA、八状态表情包和应用 A/B OTA。
- 数据与运维：个人数据搜索/导出/删除、7 日/4 周备份、隔离恢复和健康中心。
- 外部通知：固定设备集成、一次性令牌、幂等 REST/MCP 入队、离线保留、免打扰/忙碌延期、设备单飞、安全健康计数，以及受播放状态保护的通知/集成删除。

完整合并记录见[里程碑索引](../milestones.md)，长期架构约束见[ADR 索引](../decisions/README.md)。

## 版本与运行态

- Git：`master` 合并提交 `8de06be`；当前任务分支基于该提交。
- LAN server：ROLE-001 `9e526f8` 镜像，Flyway V29；运行镜像和回退信息保留在[部署状态](deployment.md)。
- CoreS3：实体验证候选 `7e7c55f`，应用 OTA 已启用，`motion_disabled`。
- `7e7c55f` 是压缩前实机候选，不是当前 `master` 祖先；只能作为运行版本，不能作为新任务分支基线。

## 最近验证

- ROLE-002 当前工作树基于 `8de06be`，提交后将重新执行提交级静态检查。
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

- `EVT-001`、`ROLE-001` 已合入并验收，`ROLE-002` 无前置阻塞。
- 用户已确认基础外部通知测试正常；免打扰延期和离线重连两个专项场景尚未执行，不阻塞代码审核或提交整理。
- 固件 OTA 回退演练、自定义表情实体素材和公网生产部署仍需要各自单独授权，但不阻塞当前软件路线。

## 下一步

用户刷新角色管理页面，验证角色音色编辑、继承提示和两个角色的实体声音；确认后再获取推送授权。未经明确授权不再次部署、不推送、不连接 COM3、不刷写固件。
