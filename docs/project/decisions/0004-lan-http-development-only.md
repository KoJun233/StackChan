# 0004：LAN HTTP/WS 仅限显式编译的开发固件/profile

- 状态：ACCEPTED
- 日期：2026-07-19
- 工作流：固件、部署
- 相关提交：`5b66759`

## 背景

首次配对和受控局域网调试可能没有可信 HTTPS，但生产设备与部署必须保留 TLS 边界。

## 决策

`http://LAN_IP:8080` 与 `ws://` 仅允许在显式编译的 LAN development firmware/profile 中使用。生产固件绝不启用它们。

## 原因

独立 LAN 构建将明文协议与生产固件隔离，且不提供 USB、网页、NVS 或远程命令的运行时降级开关。

## 影响

LAN HTTP/WS 仅用于私有 IPv4 局域网开发与首次配对；普通和生产固件继续使用 HTTPS/WSS。未来 Agent 不得改变本 ADR 确定的传输边界或暴露存储的秘密；如需变更，必须有用户批准的新 superseding ADR。

## 来源

- [设备协议 v1](../../protocol/device-v1.md)

## 替代关系

无。
