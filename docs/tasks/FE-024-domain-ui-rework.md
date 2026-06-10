# FE-024 业务域 UI 可用化改造

- 所属期次：P12-uiux-frontend-rework
- 负责人：frontend
- 状态：pending

## 目标

按 `docs/ui/ui-design.md` 改造系统设置、应用配置、运行台、流程、文件导出、OpenAPI、审计运维页面，使页面从工程型表格表单升级为正常用户可使用的业务界面。

## 输入

- `docs/ui/ui-design.md`
- `docs/api.md`
- `frontend/src/App.ts`
- `frontend/src/pages/`
- `frontend/src/api/`

## 输出

- `frontend/src/`
- `frontend/docs/page-contracts/FE-024-domain-ui-rework.md`

## 验收标准

- 新建/编辑/授权使用明确表单区、抽屉或详情区，不再散落在页面顶部。
- 应用配置形成“应用 -> 模块 -> 字段 -> 页面 -> 发布检查 -> 发布”的步骤式体验。
- 运行台详情具备详情、附件、审批、历史 Tabs。
- 流程、文件、导出、OpenAPI、审计运维具备清晰状态反馈和错误恢复。
- 空态、加载态、错误态、权限禁用态统一。

## 自检

- `npm.cmd run build`
- 浏览器完成 P12 主链路。
