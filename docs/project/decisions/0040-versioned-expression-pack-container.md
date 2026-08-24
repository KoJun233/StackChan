# ADR 0040：版本化表情资源容器与互斥活动槽

- 状态：ACCEPTED
- 日期：2026-08-23

## 背景

MEDIA-003 已证明官方 EAF player 可用于有限生命周期片段，同时确认原生 LVGL 更适合持续表情和安全状态。现有 V1 PNG 包已经占用设备上两个 1.5 MiB A/B 资源分区；如果另建一套常驻分区或允许两种资源同时激活，会扩大存储、恢复和 OTA 状态空间。

## 决策

- 保留 V1 `SCEPKG1` 静态 PNG 包，新增 V2 `SCEPKG2` 生命周期 EAF 包。
- V1/V2 共享现有 A/B 分区和原子安装状态，同一设备只能激活一个资源包。
- V2 只接受 `boot_appear`、`wake`、`role_switch` 三个有限事件及严格 160×160 RLE4 子集。
- 固件将片段复制到 PSRAM 后交给官方 LVGL EAF player，一次播放完成即删除对象并释放内存。
- 原生 LVGL 始终负责持续表情、高优先级系统/交互状态和所有失败回退。
- 能力由设备显式上报；服务端不得因字段缺失推断支持。

## 结果

- 不增加分区、不改变 NVS 安装状态布局，既有 V1 包和旧固件保持兼容。
- 生命周期片段获得可管理、可校验、可回滚的正式资源路径，但不能成为任意视频播放器或第二套长期刷新架构。
- V1 与 V2 不能叠加；需要同时自定义静态表情和生命周期动画时，必须先另立任务重新评估分区与合成策略。

## 相关资料

- [ADR 0039](0039-native-renderer-with-bounded-eaf-lifecycle-clips.md)
- [表情资源包 V2](../../protocol/expression-pack-v2.md)
- [实机验收](../../runbooks/eaf-lifecycle-pack-smoke-test.md)
