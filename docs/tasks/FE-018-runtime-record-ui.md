# FE-018 运行台记录真实 UI

## 目标

把运行台路由从通用占位工作区升级为可操作的动态记录页面，让用户能基于已发布模块完成记录查询、新建、详情、编辑、历史和提交入口。

## 输入

- `docs/api.md`
- `docs/tasks/FE-017-field-page-publish-ui.md`
- `frontend/src/App.ts`
- `frontend/src/pages/runtime/runtimeWorkbenchPageModel.ts`
- `frontend/src/components/dynamic-schema/`
- 后端已实现的 `RUN-001` 至 `RUN-010`

## 输出

- `frontend/src/App.ts`
- `frontend/src/styles.css` 如需补充布局
- `frontend/docs/page-contracts/FE-018-runtime-record-ui.md`
- `frontend/dist/`

## 完成标准

1. 运行台首页加载真实菜单，能进入已发布模块。
2. 运行模块页加载 schema、记录列表和动态表单。
3. 页面能通过真实表单创建记录、查看详情、编辑保存、查看历史。
4. 提交入口按契约处理：未绑定流程但允许直接提交时状态可进入提交态；若返回契约内流程绑定错误，P10 记录为流程前置缺失，完整流程工作台留 P11。
5. 页面展示真实 `recordId`、记录状态、版本、requestId 和后端错误。

## 验证

- `cd frontend; npm.cmd run build`
- P10 浏览器 E2E 第三段：运行台菜单、schema、列表、新建、详情、编辑、历史和提交入口。

## 不允许事项

- 不允许用控制台直调 `RUN-*` API 代替页面操作。
- 不允许把动态 schema 组件演示数据当成后端运行态数据。
- 不允许把 P10 结论扩大为完整项目可上线。
