# 部署工作流

- 状态：STABLE
- 最后更新：2026-09-23
- 当前分支：`codex/console-experience-redesign`
- 基准提交：`13ad66c`
- 最后验证提交：`13ad66c`
- 最后验证范围：本任务分支新版控制台镜像与现有 8080 实际发布；提交字段标识基准

## 当前目标

用户授权跳过剩余浏览器验收，直接发布新版 UI 到现有 8080。已完成。

## 已完成

仅替换 server 的静态页面镜像为 console-ux-20260921，保持相同服务端 JAR、配置、数据卷和 LAN 模式；数据库/Redis/备份容器未重建。服务健康、首页和 15 个引用资源的 HTTP/哈希验证通过。详细摘要、哈希和回退见[发布手册](../../runbooks/console-ux-20260921-release.md)。

## 正在进行

无待执行发布操作；用户试用新版并反馈界面问题。

## 下一步操作

访问原 8080 地址，旧标签页 Ctrl+F5 刷新。若需回退，运行 scripts/deploy-console-ui.ps1 -Rollback，先验证当前 JAR 与原 V51 完全一致；不恢复或覆盖数据库。

## 阻塞项

无发布阻塞。完整视觉、键盘及实体手机验收依用户指示移交试用，不声称全部通过。

## 关键文件

compose.console-ux.yaml、server/Dockerfile.console-update、scripts/deploy-console-ui.ps1、[发布与回退](../../runbooks/console-ux-20260921-release.md)。

## 验证命令与最近结果

部署脚本实际执行通过：JAR 相同、环境仅版本变化、挂载一致；仅 server 重建；健康 ok、检查时 restart=0。首页 SHA-256 与本地 dist 相同，15 个引用资源内容相同。前端 141 项回归及构建已通过。文档检查为 pnpm docs:check、pnpm docs:check:test、git diff --check。

## 相关设计、计划和决策

[实施记录](../console-experience-implementation.md)、[本次发布](../../runbooks/console-ux-20260921-release.md)、[V51 历史发布](../../runbooks/companion-v51-release.md)、[备份手册](../../runbooks/personal-data-backup.md)。原 V51 备份证据保留，本次前端更新未改变后端或数据库结构。

## 安全与兼容性约束

保留现有 0.0.0.0:8080 LAN HTTP 开发模式，生产 HTTPS 边界不变。秘密只在进程内复用，不打印或落盘；不轮换凭据，不刷写固件，不开启动作。2026-09-23 用户另行授权推送当前任务分支；本次不推送 master。此同 JAR UI 回退不等于 V49 数据库回退。
