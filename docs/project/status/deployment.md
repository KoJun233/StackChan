# 部署工作流

- 状态：READY_FOR_REVIEW
- 最后更新：2026-10-02
- 当前分支：`codex/companion-interaction-delivery`
- 基准提交：`69a98cd`
- 最后验证提交：`69a98cd`
- 最后验证范围：原数据隔离 V55 恢复、当前候选镜像/JAR/首页及 15 项引用 /assets/ 资源指纹、健康和保留 NVS OTA 通过；初版 19 项仅为历史统计范围

## 当前目标

完成 COMPANION-007 D01：一致提交制品、备份隔离 V55 恢复、原 LAN 部署与保留 NVS 应用 OTA。用户已明确授权；实体动作另确认。

## 已完成

git archive HEAD 候选构建与提交/镜像/JAR/首页来源清单脚本。修复原部署脚本忽略候选参数的固定 override，所有环境/卷保持；跨 schema 失败不自动运行旧应用。软件证据见[本轮记录](../../runbooks/companion-interaction-20261001.md)。

## 正在进行

提醒页手机分页补丁 companion007-d3459d2 已发布，schema 55；停写备份 stackchan-release-20261002111654 隔离恢复通过。镜像源标签、JAR/首页及 15 项引用 /assets/ 指纹一致，health=ok、restartCount=0；无头真实登录/桌面/移动/16 张截图通过，临时账号和容器零残留，注销后 401。服务端/JAR 与 1f4b185 相同，原 638/638 仍适用；固件 d8b6444 组件代码相同、不重复 OTA。来源见[本轮记录](../../runbooks/companion-interaction-20261001.md)。

## 下一步操作

维持当前 companion007-d3459d2 服务端/控制台与 d8b6444 固件，保留来源清单、备份和旧候选。2026-10-02 用户已确认全部非动作清单正常，并明确授权只推送当前任务分支；推送并核对远端 HEAD 后由用户创建 PR、审核/合并。不为交接文档重复部署或 OTA，不启用舵机。

## 阻塞项

没有部署或浏览器阻塞。发布时健康/来源指纹及无头实际 GET 的设备 d8b6444 / motion_disabled / DISABLED / calibrated=true / failure=NONE 通过。用户功能验收已确认，见[总览](overview.md)；量化指标未独立核验，用户免测动作不作为阻塞，不以发布/合成 UI 成绩冒充体验证明。

## 关键文件

scripts/build-companion-candidate.ps1、test-server-linux.ps1、verify-companion-full-restore.ps1、deploy-body-motion.ps1、install-body-motion-ota.ps1、compose.console-ux.yaml。

## 验证命令与最近结果

前端仅新增提醒页分页样式，147/147、类型/build/定向 lint 及新版本无头回归通过。固件未变，双构建/栈预算、交互五组和安全六组/500 次竞争的已有证据适用。server 源/JAR 未变，既有 149 份 XML 638/638、零失败/错误/跳过复核，不计为新运行。停写 V55 恢复、镜像/JAR/首页/15 项资源、健康和设备禁用通过；最终用户确认全部非动作清单正常，本次仅更新验收记录，不新增发布成绩。

## 相关设计、计划和决策

[交付文档](../companion-interaction-delivery.md)、[本轮发布](../../runbooks/companion-interaction-20261001.md)、[备份手册](../../runbooks/personal-data-backup.md)。旧卷/来源见[历史发布](../../runbooks/device-quiet-voice-20261001.md)。

## 安全与兼容性约束

不切模式、不改凭据/计费，生产 HTTPS-only。秘密只在进程内，备份用 Windows DPAPI；恢复不覆盖原数据库，不打印认证载荷。舵机禁用，不自动启用/测试转动；本次外部推送仅授权 codex/companion-interaction-delivery，不推送 master，PR/审核/合并由用户。
