# 0003：LLM 提供方配置由管理员管理并保护秘密

- 状态：ACCEPTED
- 日期：2026-07-19
- 工作流：服务端、前端
- 相关提交：`14e71b1`

## 背景

陪伴聊天需要支持 OpenAI-compatible 服务，同时不能将 API Key 暴露给浏览器、设备、日志或响应载荷。

## 决策

模型名、`apiKey`、`baseUrl` 与系统提示词由管理员配置；秘密加密静态保存，且永不以明文返回。

## 原因

该配置方式支持管理员选择兼容模型服务，并保持密钥只在服务端解密使用，与陪伴设计和现有 LLM settings 实现一致。

## 影响

后续设置页面、API 与日志不得暴露存储的秘密。未来 Agent 不得改变本 ADR 确定的配置与秘密保护选择或暴露存储的秘密；如需变更，必须有用户批准的新 superseding ADR。

## 来源

- [LLM settings 服务](../../../server/src/main/java/com/kj/stackchan/llm/LlmSettingsService.java)
- [LLM 提供方设置实体](../../../server/src/main/java/com/kj/stackchan/llm/LlmProviderSettingsEntity.java)

## 替代关系

无。
