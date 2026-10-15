# 0076：网页设备范围由服务端会话绑定

- 状态：ACCEPTED
- 日期：2026-10-12
- 工作流：服务端
- 相关提交：`989b3d9`（R00 实现基准；候选收据另存）
- 关联：[R00](../../requirements/companion-v1/R00-baseline-and-integration.md)、[兼容基线](../baselines/companion-v1-baseline.md)

## 背景

整合原 V57 与最新主线时，发现网页消息请求的可选 deviceId 直接用于 Agent 设备范围和自动 THINK。该选择字段不能证明会话属于设备；普通网页因此可能获得外发工具或实体表现，与 R00-AC10 边界冲突。可信设备会话的既有明确 Bark 行为仍需兼容。

## 决策

ConversationController 只从 DeviceVoiceConversationService 的持久会话映射取得设备范围，请求字段保留兼容但不授予权限。无绑定时设备范围为空，保留角色及全局已确认记忆、非设备读取。绑定 A 与请求 B 不一致时仅使用 A，不创建或改变绑定。Bark 继续原设备/角色/开关和明确请求守卫，绑定 WEB 与 VOICE 行为保留。

CompanionPromptService 提供明确 WEB 组装入口，读取普通确认记忆但不调用待续上下文服务；原语音入口保持既有 V57 语义。网页完成回合继续不传设备至长期记忆提取，不记录设备待续进展。没有新增 API、绑定写入口、数据库版本或固件协议。

## 验证与影响

MockMvc 验证伪 deviceId 无范围/THINK、可信 A 不操作 B、可信绑定 WEB 保留明确手机请求；提示测试验证绑定网页保留普通记忆但无待续服务调用。原 Bark 的能力咨询只读、内容追问、旧发送不复用，以及 V57 语音路径均回归。

普通新建网页会话不能只靠 deviceId 获得设备专属工具；需要设备范围时使用已有可信设备会话。后续手机接续若增加独立绑定入口，应另做服务器授权与身份验证，不能恢复客户端自行指定写范围。

## 替代关系

补充 [ADR0072](0072-confirmed-conversational-topic-continuity.md) 与 [ADR0075](0075-bark-agent-notification-tool.md)。不扩展普通网页的设备、待续或外发权限。
