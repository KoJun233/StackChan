# 陪伴体验 V51 发布与回退

- 文档状态：REFERENCE
- 已发布：2026-09-15 18:36（Asia/Shanghai）
- 范围：服务端、新控制台和 V50/V51；不含固件刷写或身体动作启用

## 已发布内容

见[实施完成清单](../project/companion-completion-plan.md)及 [ADR 0057–0069](../project/decisions/README.md)。保留多个独立伙伴；完善对话控制、相关记忆、主动拒绝/暂停、资讯与简报可信度、具体对象确认及简短回应；今日概览统一播报视图，高级扩展折叠。已有数据、授权边界和旧页面链接保留。

V50 增加可空话题边界；V51 增加设备/伙伴主动暂停表。升级不自动暂停伙伴或重写旧记忆。固件保持既有版本。

## 发布证据

- 镜像：`stackchan-foundation-server:companion-v51-20260914`。
- 镜像摘要：`sha256:db5cd4afd52c61c1bbdc3ed1916f31ddccb0c7ba07d4ecc4b299bdcd0edf61af`。
- 原镜像：`sha256:38da048573e52aef1bbb1e6fa35e1e8ba782d1cbb857810c8f9740f40916448c`（fact-followup-v49，保留）。
- 停写备份：`stackchan-release-20260915183606`；数据库备份时刻 `2026-09-15T10:36:06Z`。
- dump SHA-256：`ea0a0df213bf10acc20e0c8d1dcad498c93c6e2312738264fb25a2c75d1cc297`。
- 备份门槛：记录计数一致、Skill 归档恢复、DPAPI 配置往返、四项加密字段解密、原管理员记录保留、隔离测试账号登录及页面壳通过。
- 容器启动：`2026-09-15T10:36:35Z`。运行库 V51、全部迁移成功；健康正常，首页和 `/assets/index-DS8dGmCA.js` 返回 200，检查时零重启。
- 一台设备在发布后两分钟内上报心跳，身体状态仍为 DISABLED；不把心跳当成真实对话验收。

## 发布操作

正式镜像由 `server/Dockerfile` 同时构建服务端和页面，Node 24.15.0、pnpm 11.13.1；不复用旧页面。发布命令：

```powershell
./scripts/deploy-companion-v51.ps1
```

脚本固定本机既有 LAN 项目和候选镜像，只替换 server，不重建数据库、Redis 或备份服务。现场 `.env` 不存在，因此从原容器取所需配置，仅存于进程内并在退出时恢复进程环境；没有写明文秘密或轮换凭据。先停写，再执行完整备份与隔离恢复；门槛失败会重启原服务，开始替换后不会自动覆盖数据库或启动旧应用。

全栈演练使用 `scripts/verify-companion-full-restore.ps1`，仅新建独立资源且无外连、无公开端口。正常完成后清理副本，保留发布快照；中断时按时间戳核对归属再清理。配置采用 Windows DPAPI，依赖原用户和机器；跨机器迁移需另行安全保管原密钥与配置。本地 Docker 卷不等于异机灾备，详见[备份手册](personal-data-backup.md)。

## 回退边界

不能推断“旧 JAR + V51 数据库”已兼容；旧应用不执行新暂停约束。完整回退应停止写入，保留故障现场，先把上面的 V49 快照恢复到隔离库，确认旧数据、文件、密钥和旧镜像后，在明确的恢复窗口切换。正式库覆盖须单独核对目标并授权，不自动执行。回退会丢失备份时刻之后的新记录；不要删除迁移历史、DROP 新表或 Flyway repair 伪装回退。

## 验证范围

服务端 554/554、V1..V51 空库及 V49→V51 升级、JAR；控制台 33 文件 107/107、类型与相关静态检查；Docker 全栈构建；发布恢复和 HTTP 健康检查均通过。八个既有 Windows loopback 类未纳入服务端回归。日志分别为 `server/target/short-response-regression.log`、`advanced-console-test.log`、`companion-v51-image.log`、`companion-release-backup.log`。

浏览器插件缺少运行文件，真实点击验收未执行。用户按[50 项基线与追加专项](../project/companion-experience-acceptance.md)测试页面、实际口语、ASR/TTS 和陪伴听感，再开始十四日使用记录；没有预填这些项目通过。工程观察原门槛与窗口保持不变。
