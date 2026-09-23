# 控制台 UI 2026-09-21 发布与回退

- 文档状态：REFERENCE
- 发布时刻：2026-09-21 20:58:52（Asia/Shanghai）
- 发布来源：`codex/console-experience-redesign` 未提交工作区，基准 `13ad66c`
- 用户明确要求：停止浏览器验收，将新版发布到现有 8080，后续体验问题由用户反馈。

## 发布内容与边界

发布[控制台体验改造](../project/console-experience-implementation.md)：聊天与范围错误修复、导航重组、伙伴首页、独立陪伴/工作页面、统一页面壳、温和浅深配色、移动聊天和文案收敛。

本次沿用当前运行镜像的服务端 JAR，只替换 `/app/public`。数据库、Redis、备份容器与数据卷没有重建；没有修改数据库结构、轮换凭据、切换 LAN 模式、刷写固件或执行实体动作。8080 保持既有 LAN HTTP 开发部署，不能据此视为 HTTPS 生产部署。

## 可核查证据

| 项目 | 结果 |
| --- | --- |
| 新镜像 | `stackchan-foundation-server:console-ux-20260921` |
| 镜像 ID | `sha256:b651acd6bd36ceb8edf564af6735975b9b5475d76699b58629cc5ec7e58d426c` |
| 原镜像（保留） | `stackchan-foundation-server:companion-v51-20260914`，`sha256:db5cd4afd52c61c1bbdc3ed1916f31ddccb0c7ba07d4ecc4b299bdcd0edf61af` |
| 发布前后 JAR SHA-256 | `bf9b6eee5c4d235152eb12ac4a0ff2947da5b1f017810222ed0f1c60ada14f10`，完全一致 |
| 首页 SHA-256 | `fee00e497893949d58e221c7e04b89c5a7e7d21938afa206a2d4a26270606d0d`，HTTP 返回与本地构建一致 |
| 页面入口 | `/assets/index-B4Gtc23O.js` |
| HTTP 资源 | 首页 200；HTML 引用的 15 个静态资源全部 200 且 SHA-256 与本地 dist 一致 |
| 服务健康 | `/api/v1/health` 返回 `status: ok`；检查时 running、重启计数 0 |
| 配置与挂载 | 发布脚本比较所有环境键值（仅允许构建版本变化）及卷名/路径/读写属性，全部一致 |

已有 39 文件 141/141 回归、包含 vue-tsc 的生产构建、定向静态检查通过。浏览器曾验证首页、短屏聊天和伙伴切换；剩余 A08、A10–A16 等体验检查按用户要求移交用户，未记录为全部通过。

## 构建与发布

先按开发环境引导运行控制台生产构建。将 `apps/stackchan-console/dist` 复制为一个新建临时构建目录下的 `public/`，然后：

```powershell
docker build -f server/Dockerfile.console-update -t stackchan-foundation-server:console-ux-20260921 <临时构建目录>
./scripts/deploy-console-ui.ps1
```

本次临时构建目录为 `.tools/console-ui-release-20260921`，镜像配方与 `server/Dockerfile.console-update` 一致。不要把 `.tools/ux-preview.mjs` 的合成数据或预览登录脚本打包进 dist；本次生产首页按 dist 原文发布，不含预览注入。

脚本读取现有容器配置，仅在进程内传给 Compose，不输出或落盘秘密。预检 JAR、完整环境、数据卷后，仅重建 server；健康轮询通过才报告发布成功。页面更新会短暂重启服务，会话可能需要重新登录。没有执行外部 Git 推送。

## 回退

此 UI 版本与原 V51 镜像使用同一 JAR 和数据库结构，因此可仅切回旧 UI 镜像：

```powershell
./scripts/deploy-console-ui.ps1 -Rollback
```

回退同样检查运行 JAR 一致性和配置/卷保持；如以后后端或配置已变化，脚本将拒绝此快捷回退。它不恢复或覆盖数据库，不删除发布后产生的数据。更早的 V49 回退仍遵循 V51 手册，不能套用此 UI-only 边界。

## 用户访问

继续访问原来的 8080 地址。若旧标签页仍显示旧界面，使用 Ctrl+F5 强制刷新；新版入口应为“伙伴首页”“聊天”“我的伙伴”“我的事务”“机器人”“设置与数据”。后续反馈按页面、操作步骤和实际结果记录。
