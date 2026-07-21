# 0002：后端保持 Java 21 / Spring Boot 与 Spring AI Alibaba-compatible 集成

- 状态：ACCEPTED
- 日期：2026-07-19
- 工作流：服务端
- 相关提交：`14e71b1`

## 背景

现有服务端承担后台 API、设备 WebSocket、会话持久化与模型调用，需要保持已验证的单服务边界，同时支持 OpenAI-compatible LLM 提供方。

## 决策

后端保持 Java 21 / Spring Boot，并使用 Spring AI Alibaba-compatible integration 进行 LLM 调用。

## 原因

该选择符合用户已批准的架构和当前服务端实现，可通过 Spring AI 的 OpenAI-compatible 接入方式适配配置中的模型与服务地址，而无需绑定单一供应方。

## 影响

后续服务端 LLM 调用必须继续遵守此集成边界。未来 Agent 不得替换所选后端栈或暴露存储的秘密；如需变更，必须有用户批准的新 superseding ADR。

## 来源

- [服务端 Maven 配置](../../../server/pom.xml)
- [LLM 运行时客户端工厂](../../../server/src/main/java/com/kj/stackchan/llm/LlmRuntimeClientFactory.java)

## 替代关系

无。
