# 0008：管理后台通过 USB 配网，空闲屏保关闭背光

- 状态：SUPERSEDED
- 日期：2026-07-19
- 工作流：前端、固件
- 相关提交：`e9f072a`

## 背景

用户需要在 StackChan 管理后台自行修改机器人 Wi-Fi，同时发现原低亮度动态屏保会每秒整屏重绘并产生快速闪烁。Wi-Fi 密码不应经过服务端或浏览器持久化，空闲保护也不应依赖持续动画。

## 决策

Fantastic-admin 的设备配网页面使用 Chromium Web Serial，经物理 USB 将一次性配对码、Wi-Fi 和服务地址直接发送给机器人。页面只向既有服务端接口申请一次性配对码；Wi-Fi 密码不进入 HTTP 请求、Pinia、localStorage 或其他持久化存储，并在每次配网结束后从表单内存清除。

CoreS3 连续空闲默认 300 秒后只关闭显示背光，不清屏、不移动瞳孔、不周期性重绘。触摸、唤醒词、状态切换或提醒播报恢复正常亮度并重画当前表情。

## 原因

Web Serial 保留了物理 USB 和一次性配对码边界，管理员不需要手工拼接包含密码的 JSON，也不需要把秘密交给服务端。关闭背光消除了快速闪烁和无意义的显示写入，同时比低亮度动画更省电。

## 影响

- 浏览器配网要求安全上下文以及支持 Web Serial 的最新版 Edge 或 Chrome；`http://localhost` 属于可用的本地安全上下文。
- 串口不能同时被 ESP-IDF monitor 或其他串口工具占用。
- LAN HTTP 地址仍仅被显式 LAN 开发固件接受；生产固件继续只接受 HTTPS。
- 固件刷写仍要求用户明确确认设备、端口、profile 和精确提交。
- 后续不得恢复周期性整屏屏保动画，或让 Wi-Fi 密码经过服务端/浏览器持久化，除非有用户批准的替代 ADR。

## 来源

- [物理设备 smoke test](../../runbooks/physical-device-smoke-test.md)

## 替代关系

本 ADR 曾替代 ADR 0007 来源设计中“降低亮度并每秒移动瞳孔”的屏保细节；现已由 [0010](0010-low-brightness-local-pupil-screensaver.md) 完整替代。0010 保留本 ADR 的 Web Serial USB 配网和 Wi-Fi 秘密边界，并以低亮度、小区域、低频移动瞳孔替代关闭背光。
