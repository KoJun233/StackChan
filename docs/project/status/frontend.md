# 前端工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-05
- 当前分支：`codex/device-interaction-upgrade`
- 基准提交：`effa511`
- 最后验证提交：`effa511`

Git基线与安装来源分开记录。V56服务器/控制台仍来自 `a840d0d17194c8aecedfd9b7d19b9e1ca6a7bb4b`；已安装显示固件来源 `1de3d7a7c86527e6f5613b5251e838a7b880b570`，由 `.tools/device-display-r2/manifest.json` 冻结，第二次修订应用OTA已获许可完成；首轮显示来源保留在 `.tools/device-display-candidate`。此前USB仅应用修复714c030及物理服务地址迁移来源仍保留在 `.tools/device008-offline-repair/manifest.json`；补文档不改变制品来源。

## 当前目标

保持现有控制台与 DEVICE-008 新设备菜单兼容，交付同源内置页面。

## 已完成

新确认/菜单为独立设备 HTTP API 与连接能力，不改变原控制台设备、设置或伙伴 API。服务端设置变更通知新设备刷新，旧控制台设置不会被陈旧设备缓存覆盖。控制台本轮无需产品代码修改；用户 `components.d.ts` 的无内容差异不暂存。

## 正在进行

内置控制台已随 `device008-a840d0d` 同源镜像部署。运行 `/app/public/index.html` 摘要与冻结manifest一致，页面HTTP200；现有前端产品代码未改。

## 下一步操作

在已部署V56上验收原伙伴/设置/待办页面与设备菜单的双向同步，步骤见[安装记录](../../runbooks/device-interaction-20261002.md)。软件变更后的最小复验：控制台目录 `node ../../node_modules/vitest/vitest.mjs run --maxWorkers=1`。

## 阻塞项

无前端软件阻塞；页面健康且设备恢复在线，可继续用户实体菜单与控制台设置同步验收。实际触摸结果尚未收到，不以在线核验替代。

## 关键文件

`apps/stackchan-console/src/api/modules/devices.ts`、`views/devices/`、`views/settings/speech/`、`server/src/main/java/com/kj/stackchan/device/DeviceUiService.java`。

## 验证命令与最近结果

未改前端的基线回归 41 文件、147/147；既有 localhost:3000 代理 ECONNREFUSED/Vite 退出警告保留。同源镜像生产构建另由候选日志证明，不把未部署候选记作线上成功。

## 相关设计、计划和决策

[计划](../device-interaction-upgrade.md)、[设备协议](../../protocol/device-ui.md)、[ADR0071](../decisions/0071-device-eyes-cards-and-local-tracking.md)、[runbook](../../runbooks/device-interaction-20261002.md)。

## 安全与兼容性约束

保留旧入口、用户生成文件与缓存；不外推、创建/合并 PR 或改变部署模式。
