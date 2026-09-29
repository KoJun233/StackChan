# 跨页面侧栏布局修复发布与回退

- 文档状态：REFERENCE
- 发布时间：2026-09-29 18:27（Asia/Shanghai）
- 任务分支：`codex/fix-sidebar-navigation`
- 用户授权：修复并部署到现有 8080 供其测试；现场确认后推送当前任务分支

## 根因与修正

动态路由整理原先会删除所有带子页面的中间路由组件。提醒、待办、AI 配置等页面的主布局恰好在该中间层，因此进入这些页面时，页面正常显示而侧栏、工具栏等布局一起消失；此问题与浏览器宽度无关。现在每条页面路径只保留最外层布局：没有上层布局的菜单分组保留子路由布局，已有布局的分组去掉重复布局。回归检查 17 个业务入口和 5 个详情页，每条路径恰有一层布局和一个页面组件。

## 发布与验证

| 项目 | 结果 |
| --- | --- |
| 前端验证 | 40 文件 144/144；类型检查与 production build、定向 ESLint 通过 |
| 前一镜像 | `stackchan-foundation-server:sidebar-fix-ceb4562`，本机保留可回退 |
| 新镜像 | `stackchan-foundation-server:sidebar-layout-v2-20260929`，`sha256:ede342b18e5cc7cfd281fc91aa1eb163af04d9c6da231e6ef099c4773a7b96fc` |
| 服务端 JAR | SHA-256 保持 `906110888de0b3533d73c3181cc415153e5b69f8f310ba80563851bf944ed95b` |
| 配置和数据卷 | 发布脚本预检通过；仅 `COMPANION_BUILD_VERSION` 更新为 `sidebar-layout-v2-20260929` |
| HTTP | 健康为 `ok`；首页 SHA-256 与本地构建同为 `E6D82E14156CAB89FC01ABAF0059985518C5136540DE2135D0AD27AFBAE9D282`；15 个引用静态资源哈希全部一致 |
| 容器 | server 运行且重启计数 0；PostgreSQL、Redis、备份容器及 CoreS3 未替换 |

浏览器控制组件缺少所需服务文件，Agent 未取得对已登录页面的独立点击证据。用户在 8080 试用后反馈“可以了，我这里看着没问题了”，跨页面侧栏问题按用户反馈完成现场复核；具体点击页面与设备范围不作超出反馈的推定。用户随后明确授权推送当前任务分支，PR 创建、审核与合并仍由用户执行。

## 回退

前一镜像保留在本机。若本版引入新的页面问题，使用现有 UI 专用脚本恢复前一版本；脚本要求服务端 JAR、环境配置和数据卷一致，否则停止：

```powershell
./scripts/deploy-console-ui.ps1 -CandidateImage 'stackchan-foundation-server:sidebar-fix-ceb4562' -BuildVersion 'sidebar-fix-ceb4562'
```

回退只切换页面资源，不恢复数据库，也不操作固件。前一版本仍有已确认的跨页面侧栏问题。
