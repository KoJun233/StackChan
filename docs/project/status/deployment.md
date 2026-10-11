# 部署工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-11
- 当前分支：`codex/bark-voice-routing`
- 基准提交：`ec53bfe`
- 最后验证提交：`ec53bfe`

## 当前目标

沿原部署授权安装 [COMPANION-010](../bark-agent-tool.md) 语音路由修复，保留已有环境、数据、Bark 和原固件。

## 已完成

安装冻结 `a37d220e7a2a0189b28412ffd420b2173fbbfc13`，独立工作树 `D:\stackchan\.worktrees\bark-voice-integration`；由已安装 dc2ba2f 增加三个修复文件，保留原 V57–V60。镜像 `stackchan-foundation-server:bark-voice-a37d220e7a2a`，运行 `bark-voice-a37d220`，数据库 V60；清单 `.tools/device008-a37d220e7a2a-7a2d9e5604724bbba43989834fc23066/manifest.json`。

镜像 ID `sha256:b2baf5bd452ae8bedb7fd97ea53fbfabdae99553bf1ae7350b2045061e5214a4`；JAR SHA256 `CAB78B61CF733A24B5FFCAD9B041C508AC1EDC94F6D0042456B6AAAC43FE04AC`。真实 JAR 与全部 288 个资源匹配清单；PreserveConsole 预检查证明 index 保持。缓存依赖的 Git 树、四项文件指纹、固定 pnpm 和前后镜像 ID 通过，源码重新生产构建。

停服一致备份及内部隔离 V60 恢复先于替换，通过且保留 `stackchan-release-20261011152920`，备份 `2026-10-11T07:29:24Z`。恢复禁用委托调度、不开放外部端口与外网；五项加密设置、管理员、Skill、DPAPI、旧 checksum 与个人记录通过，合成管理员仅存在副本。旧恢复点保持。

原 1 个任务、3 条历史、完整 Bark 设置与通知回执指纹一致；个人数量不减少。Key 配置、Bark 启用，LAN true、production false、DoH true、卷/身份/凭据保持。`http://192.168.1.5:8080` 健康 ok、控制台 200、匿名委托 API 401；固件 `device008-ui-r3-b872f2f` 保持。

## 正在进行

安装与核验完成；用户在原设备复测确认基本正常并授权推送当前任务分支。自动化没有实际发送通知。

## 下一步操作

用户在 GitHub 创建并审核当前任务分支的 PR；实际安装来源继续保持 a37d220。后续升级前必须保留未合入的 V57，按既有 runbook 备份与验证。

## 阻塞项

无。未知投递先看手机，不自动重发。

## 关键文件

`scripts/build-companion-candidate.ps1`、`scripts/deploy-body-motion.ps1`、`scripts/verify-companion-full-restore.ps1`；本轮均未改。

## 验证命令与最近结果

最终冻结相关测试 103/103，随后串行候选完整 vue-tsc/Vite 和服务端打包；`-ExpectedSchema 60 -PreserveConsole -PreflightOnly`、正式安装及产物/数据验证均退出 0。收据位于上述集成工作树 `.tools/bark-installed-verification.json`、`.tools/last-deployment-restore-verification.json`、`.tools/bark-preupgrade-state.json`、`.tools/bark-voice-deployment.log`、`.tools/bark-voice-build.log`。

`git diff --check`、`pnpm docs:check` 通过。最初冻结提交因未引导 pnpm 拒绝，环境引导后正常提交；初次捕获脚本路径拼写已修正，正式捕获与安装成功。失败命令不计通过，未改变线上数据或发布来源。

## 相关设计、计划和决策

[设计](../bark-agent-tool.md)、[ADR0075](../decisions/0075-bark-agent-notification-tool.md)、[使用说明](../runbooks/recurring-research-and-bark.md)、[生产边界](../../runbooks/secure-deployment.md)、[备份](../../runbooks/personal-data-backup.md)。

## 安全与兼容性约束

任务分支包含未合入依赖且缺 V57，禁止直接安装该分支；实际冻结集成已保留 V57–V60。用户原工作树保持；本轮已授权仅推送任务分支，PR 创建、审核与合并由用户执行，部署模式、凭据与固件保持。
