# 窄屏侧栏导航修复发布与回退

- 文档状态：REFERENCE
- 发布时间：2026-09-29 11:38（Asia/Shanghai）
- 任务分支：`codex/fix-sidebar-navigation`
- UI 修复提交：`ceb4562`
- 用户授权：将侧栏修复部署到现有 8080，供其测试

> 用户在 AI 配置页和其他页面复测后确认侧栏仍消失，且手机与 PC 都会发生。本版只修复移动端入口，没有修复路由整理时删除主布局的根因；后续修正见[路由布局修复发布](sidebar-route-layout-20260929-release.md)。本记录保留首次发布事实，不作为问题已解决的验收结论。

## 发布内容

窄屏路由切换收起侧栏后，悬浮菜单入口持续可见；模式切换或布局卸载会清理页面滚动锁。复用当前 BODY-002 服务端镜像，只替换 `/app/public` 静态资源。部署脚本接受明确候选镜像与版本，并在重建前核对服务端 JAR、环境配置和数据卷。部署仍为原 LAN HTTP 开发模式。

## 验证记录

| 项目 | 结果 |
| --- | --- |
| 前端 | 40 文件 143/143、类型检查、production build 和改动文件 ESLint/Stylelint 通过 |
| 原镜像 | `stackchan-foundation-server:body002-think-7d8c55a`，`sha256:8e29dfe1e4f2ac78d198c2121b18804708962f39f0f589af6a5bd13b645c7a11`，保留可回退 |
| 新镜像 | `stackchan-foundation-server:sidebar-fix-ceb4562`，`sha256:972efd8415c666cd84af8287dfec4056804a40224c5a4937e826bad1d97c50d8` |
| 服务端 JAR | 发布前后 SHA-256 均为 `906110888de0b3533d73c3181cc415153e5b69f8f310ba80563851bf944ed95b` |
| 配置和数据卷 | 发布脚本预检通过；仅 `COMPANION_BUILD_VERSION` 更新为 `sidebar-fix-ceb4562` |
| HTTP | 健康为 `ok`；首页 SHA-256 与本地构建同为 `614FDAB21E6CBE11C77DE324AB37669F2E8AD974A94442203FB380B2B0CBBEA1`；HTML 引用的 15 个静态资源全部与本地哈希一致 |
| 容器 | server 运行且重启计数 0；PostgreSQL、Redis、备份容器未重建 |

本次会话未取得可调用的浏览器控制接口，因此没有代替用户宣称 625px 窄屏点击验收通过。用户刷新现有 8080 页面后，从聊天进入提醒、待办和设置，检查悬浮菜单入口与侧栏重新打开行为。旧标签页若仍使用缓存资源，可强制刷新。

## 回退

原 BODY-002 镜像保留在本机。若需恢复旧 UI，运行以下命令；脚本仍要求服务端 JAR、环境配置和数据卷一致，否则会停止：

```powershell
./scripts/deploy-console-ui.ps1 -CandidateImage 'stackchan-foundation-server:body002-think-7d8c55a' -BuildVersion 'body002-think-7d8c55a'
```

回退只切换页面镜像，不恢复数据库，也不改变固件。
