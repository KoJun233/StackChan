# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-13
- 当前分支：`codex/role-001-role-containers`
- 实现基准：`5d18623`
- 最后验证提交：`9e526f8`
- 当前部署：LAN HTTP development mode
- 生产边界：HTTPS-only

## 当前结论

`EVT-001` 已通过 PR #17 合入 `master`。`ROLE-001` 已在分支完成：V29、默认角色迁移、角色 CRUD、设备活动角色，以及网页/语音会话、记忆、提醒、通知集成、主动主题、Agent Tool 和个人数据的角色边界均已落地并通过自动化验证。

当前 LAN 已运行 ROLE-001/V29，等待用户执行双角色实体隔离验收；本分支未推送，未刷写固件。

## 工作流摘要

| 工作流 | 状态 | 当前事实 | 下一步 |
| --- | --- | --- | --- |
| [服务端](server.md) | VALIDATING | V29 与角色隔离主链路已部署，348/348 和运行迁移通过 | 执行双角色实体隔离验收 |
| [前端](frontend.md) | VALIDATING | 角色 CRUD、设备绑定和角色筛选已部署，69/69、类型检查和构建通过 | 执行管理员页面人工复核 |
| [固件](firmware.md) | STABLE | CoreS3 `7e7c55f / motion_disabled / OTA=true` | 后续两任务不改固件 |
| [部署](deployment.md) | VALIDATING | LAN 已运行 ROLE-001 server/V29，健康和鉴权边界通过 | 完成双角色用户验收 |

## 当前能力地图

- 对话：流式文字聊天、本地唤醒语音、完整回复、连续对话、触摸取消和隐私安全诊断。
- 陪伴：角色容器、确认记忆、建议过滤、相关检索、周期提醒、免打扰和有界主动关心；人设与陪伴数据按角色隔离。
- Agent：受控 ReactAgent、Skill ZIP、只读 Tool、页面管理的 Streamable HTTP MCP Client 和语音动作确认。
- 设备：配对/JWT/WebSocket、唤醒模型 OTA、八状态表情包和应用 A/B OTA。
- 数据与运维：个人数据搜索/导出/删除、7 日/4 周备份、隔离恢复和健康中心。
- 外部通知：固定设备集成、一次性令牌、幂等 REST/MCP 入队、离线保留、免打扰/忙碌延期、设备单飞、安全健康计数，以及受播放状态保护的通知/集成删除。

完整合并记录见[里程碑索引](../milestones.md)，长期架构约束见[ADR 索引](../decisions/README.md)。

## 版本与运行态

- Git：`master` 合并提交 `5d18623`；当前任务分支基于该提交。
- LAN server：ROLE-001 `9e526f8` 镜像，Flyway V29；运行镜像和回退信息保留在[部署状态](deployment.md)。
- CoreS3：实体验证候选 `7e7c55f`，应用 OTA 已启用，`motion_disabled`。
- `7e7c55f` 是压缩前实机候选，不是当前 `master` 祖先；只能作为运行版本，不能作为新任务分支基线。

## 最近验证

- 以下 ROLE-001 结果针对基于 `5d18623` 的当前任务工作树；提交后将重新执行提交级静态检查。
- `0e92d58`：服务端 322/322，通过空 PostgreSQL 的 Flyway V1..V27。
- `cf26fd7 -> 7e7c55f`：真实应用 OTA 为 `INSTALLED`，新镜像跨心跳稳定，NVS 保留。
- 用户确认 OTA 后普通唤醒对话、播放中触摸停止和后续再次对话正常。
- `EVT-001`：服务端 341/341，通过空 PostgreSQL 的 Flyway V1..V28；真实 Streamable HTTP MCP 客户端完成 Bearer 鉴权、工具发现和通知入队。
- Node v24.15.0：前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过；路由专项 3/3 通过。
- 删除迭代：服务端 346/346、删除定向 16/16；前端继续为 69/69，并完成类型检查和 production build。
- `ROLE-001`：服务端 348/348，通过空 PostgreSQL 的 Flyway V1..V29；角色/会话定向 13/13。Node v24.19.0、pnpm 11.19.0 下前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过。
- ROLE-001 LAN：部署前备份和隔离恢复通过，只替换 server；运行库迁移至 V29，首页 200，角色/设备 API 未认证均为 401，默认角色和全部陪伴数据角色归属完整。
- 全量服务端首次运行遇到既有异步 MockMvc 用例的瞬时 `ConcurrentModificationException`；该用例单独重跑及随后完整 341/341 重跑均通过，未修改无关实现。
- 用户授权后已生成并恢复校验新 PostgreSQL 备份，仅替换 LAN server；运行库成功迁移到 V28，健康接口和外部通知静态资源为 200，未认证 REST/MCP 均为 401，启动日志无错误。

## 依赖与阻塞

- `EVT-001` 已合入，`ROLE-001` 无前置阻塞。
- 用户已确认基础外部通知测试正常；免打扰延期和离线重连两个专项场景尚未执行，不阻塞代码审核或提交整理。
- 固件 OTA 回退演练、自定义表情实体素材和公网生产部署仍需要各自单独授权，但不阻塞当前软件路线。

## 下一步

完成双角色实体隔离和角色切换用户验收；验收通过并获得授权后再推送任务分支。未经明确授权不连接 COM3、不刷写固件。
