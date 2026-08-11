# 全局工作流总览

- 状态：READY_FOR_REVIEW
- 最后更新：2026-08-11
- 当前分支：`codex/evt-001-external-notifications`
- 实现基准：`0e92d58`
- 最后验证提交：`0e92d58`
- 当前部署：LAN HTTP development mode
- 生产边界：HTTPS-only

## 当前结论

`BASE-009` 的路线刷新与状态交接已按用户要求并入当前 EVT-001 任务分支。`EVT-001` 的 V28、独立 Bearer 权限、REST/Streamable HTTP MCP、可靠提醒投递、管理页面和健康计数均已实现并部署到当前 LAN server；CoreS3 未连接、未刷写。

当前代码已可进入人工审核。运行数据库已从 V27 迁移到 V28，网页静态资源与鉴权边界已验收，用户确认基础外部通知测试正常；免打扰延期和离线重连仍待专项验收。外部通知菜单已并入“提醒管理”，集成及队列删除也已随 `50d6269` 镜像发布。后继任务仍为 `ROLE-001` 角色容器。

## 工作流摘要

| 工作流 | 状态 | 当前事实 | 下一步 |
| --- | --- | --- | --- |
| [服务端](server.md) | READY_FOR_REVIEW | LAN 已运行 `50d6269`/V28；346/346、删除定向 16/16、真实 MCP HTTP 入队通过 | 做删除与实体投递验收 |
| [前端](frontend.md) | READY_FOR_REVIEW | 菜单归组和删除已发布；25 个文件 69/69、类型检查和构建通过 | 做管理员浏览器复核 |
| [固件](firmware.md) | STABLE | CoreS3 `7e7c55f / motion_disabled / OTA=true` | 后续两任务不改固件 |
| [部署](deployment.md) | STABLE | LAN 已运行 EVT-001 server/V28，健康检查通过 | 保持 LAN/production 隔离并完成实体投递验收 |

## 当前能力地图

- 对话：流式文字聊天、本地唤醒语音、完整回复、连续对话、触摸取消和隐私安全诊断。
- 陪伴：结构化单例人设、确认记忆、建议过滤、相关检索、周期提醒、免打扰和有界主动关心。
- Agent：受控 ReactAgent、Skill ZIP、只读 Tool、页面管理的 Streamable HTTP MCP Client 和语音动作确认。
- 设备：配对/JWT/WebSocket、唤醒模型 OTA、八状态表情包和应用 A/B OTA。
- 数据与运维：个人数据搜索/导出/删除、7 日/4 周备份、隔离恢复和健康中心。
- 外部通知：固定设备集成、一次性令牌、幂等 REST/MCP 入队、离线保留、免打扰/忙碌延期、设备单飞、安全健康计数，以及受播放状态保护的通知/集成删除。

完整合并记录见[里程碑索引](../milestones.md)，长期架构约束见[ADR 索引](../decisions/README.md)。

## 版本与运行态

- Git：`master` 合并提交 `0e92d58`；任务提交 `e8f3035` 已在历史中。
- LAN server：EVT-001 `50d6269` 镜像，Flyway V28；运行镜像和回退信息保留在[部署状态](deployment.md)。
- CoreS3：实体验证候选 `7e7c55f`，应用 OTA 已启用，`motion_disabled`。
- `7e7c55f` 是压缩前实机候选，不是当前 `master` 祖先；只能作为运行版本，不能作为新任务分支基线。

## 最近验证

- `0e92d58`：服务端 322/322，通过空 PostgreSQL 的 Flyway V1..V27。
- `cf26fd7 -> 7e7c55f`：真实应用 OTA 为 `INSTALLED`，新镜像跨心跳稳定，NVS 保留。
- 用户确认 OTA 后普通唤醒对话、播放中触摸停止和后续再次对话正常。
- `EVT-001`：服务端 341/341，通过空 PostgreSQL 的 Flyway V1..V28；真实 Streamable HTTP MCP 客户端完成 Bearer 鉴权、工具发现和通知入队。
- Node v24.15.0：前端 Vitest 25 个文件 69/69、`vue-tsc -b` 和 production build 通过；路由专项 3/3 通过。
- 删除迭代：服务端 346/346、删除定向 16/16；前端继续为 69/69，并完成类型检查和 production build。
- 全量服务端首次运行遇到既有异步 MockMvc 用例的瞬时 `ConcurrentModificationException`；该用例单独重跑及随后完整 341/341 重跑均通过，未修改无关实现。
- 用户授权后已生成并恢复校验新 PostgreSQL 备份，仅替换 LAN server；运行库成功迁移到 V28，健康接口和外部通知静态资源为 200，未认证 REST/MCP 均为 401，启动日志无错误。

## 依赖与阻塞

- `ROLE-001` 必须等 `EVT-001` 由用户审核并合入，避免通知数据随后重复迁移角色归属。
- 用户已确认基础外部通知测试正常；免打扰延期和离线重连两个专项场景尚未执行，不阻塞代码审核或提交整理。
- 固件 OTA 回退演练、自定义表情实体素材和公网生产部署仍需要各自单独授权，但不阻塞当前软件路线。

## 下一步

刷新管理页面复核菜单与删除功能，并按[外部通知 runbook](../../runbooks/external-notifications.md)补验免打扰延期和离线重连；继续不推送分支、不连接 COM3、不刷写固件。
