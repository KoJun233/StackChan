# 稳定架构

StackChan 按设备、服务、浏览器和数据边界划分职责，从而使设备在网络功能不可用时仍保持安全。

## 职责边界

- **StackChan firmware** 负责显示屏、传感器、麦克风/扬声器集成、本地安全状态、配网，以及设备 WebSocket 客户端。
- **Spring Boot** 负责管理员认证、加密的 LLM 设置、会话、记忆服务、配对、设备凭据、设备会话和未来的主动调度。
- **Fantastic-admin** 负责浏览器管理、LLM 配置、文本聊天、设备状态、配对和未来的记忆/人设控制。
- **PostgreSQL** 是持久化存储；**Redis** 用于瞬态协调和缓存，绝不能成为持久记忆的唯一来源。

服务通过 Spring AI 的 Alibaba-compatible 接口，从已存储的 `apiKey`、`baseUrl`、模型和系统提示词中选择 LLM runtime。设备命令始终由服务端授权；
入站设备事件不能启用运动。无论服务端状态如何，固件均保留其本地安全限制。

LAN HTTP 仅是开发环境边缘；生产环境在受信任的反向代理处终止 HTTPS，然后再将请求转交给 companion service。

## 浏览器聊天数据流

1. 管理员登录 Fantastic-admin，并从浏览器发起文本聊天。
2. Fantastic-admin 调用经过认证的 Spring Boot 聊天 API。
3. Spring Boot 从 PostgreSQL 加载会话和持久记忆；需要时从 Redis 获取瞬态协调或缓存数据，并解析加密的 LLM 配置。
4. Spring AI 调用选定的 Alibaba-compatible LLM runtime，并经 Spring Boot 将响应流式传递给 Fantastic-admin。
5. Spring Boot 将会话和任何获准持久化的记忆写入 PostgreSQL；Redis 永远不是唯一的持久记录。

## 设备连接数据流

1. 固件完成配网并与 Spring Boot 配对，以获得设备凭据。
2. 固件向 Spring Boot 打开已认证的 WebSocket 客户端连接，并发送有序的设备事件。
3. Spring Boot 校验凭据、维护设备会话，并在适当时将持久设备状态记录到 PostgreSQL。
4. 服务器授权的命令仅发送至已认证的在线设备会话。事件和确认消息均不能启用运动。
5. 固件应用本地安全状态，并且只执行能够安全接受的命令。

## 相关约定

- [设备协议 v1](../protocol/device-v1.md)
- [安全部署 runbook](../runbooks/secure-deployment.md)
