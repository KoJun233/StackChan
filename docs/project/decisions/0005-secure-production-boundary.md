# 0005：生产环境保持可信代理后的 HTTPS-only 边界

- 状态：ACCEPTED
- 日期：2026-07-19
- 工作流：部署
- 相关提交：`5302acd`

## 背景

生产服务需要通过可信代理终止 TLS，不能将 Companion 直接以公共 HTTP 暴露；LAN HTTP 仅是独立的开发辅助模式。

## 决策

生产环境仅通过可信代理使用 HTTPS。`compose.lan.yaml` 与 `compose.production.yaml` 绝不可组合。

## 原因

这保留管理员会话、设备令牌和管理流量的生产 TLS 边界，并防止 LAN 明文覆盖层误用于生产入口。

## 影响

部署必须保持受保护的代理边界与 HTTPS-only 行为。未来 Agent 不得改变本 ADR 确定的生产边界或暴露存储的秘密；如需变更，必须有用户批准的新 superseding ADR。

## 来源

- [安全部署 runbook](../../runbooks/secure-deployment.md)

## 替代关系

无。
