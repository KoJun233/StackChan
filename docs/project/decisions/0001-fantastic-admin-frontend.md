# 0001：浏览器管理端采用 Vue 3 与 Fantastic-admin

- 状态：ACCEPTED
- 日期：2026-07-19
- 工作流：前端
- 相关提交：`14e71b1`

## 背景

浏览器后台需要使用标准的 Fantastic-admin monorepo 结构，并与既有中文管理界面、路由、表单和数据页面约定一致。

## 决策

浏览器管理端使用 Vue 3 和 Fantastic-admin。新增 UI 使用框架 `Fa*` 组件与 Composition API 模式。

## 原因

这与文字陪伴聊天和后台迁移设计中确定的标准应用结构、框架布局、路由及组件约定一致，避免将业务页面分散为手写实现。

## 影响

后续前端工作须在既有 Fantastic-admin 边界内实现。未来 Agent 不得替换所选前端栈或暴露存储的秘密；如需变更，必须有用户批准的新 superseding ADR。

## 来源

- [前端实现](../../../apps/stackchan-console/)

## 替代关系

无。
