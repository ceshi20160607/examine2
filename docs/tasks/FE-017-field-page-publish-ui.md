# FE-017 字段页面配置与发布真实 UI

## 目标

让系统管理员能通过浏览器完成字段设计、列表/表单/详情 schema、菜单、动作和模块发布，发布后运行台能读取真实 schema。

## 输入

- `docs/api.md`
- `docs/tasks/FE-016-app-module-ui.md`
- `frontend/src/App.ts`
- `frontend/src/pages/module-config/moduleConfigPageModel.ts`
- `frontend/src/components/dynamic-schema/`
- 后端已实现的 `FIELD-001` 至 `FIELD-005`、`UI-001` 至 `UI-008`、`MOD-006`、`MOD-007`

## 输出

- `frontend/src/App.ts`
- `frontend/src/styles.css` 如需补充布局
- `frontend/docs/page-contracts/FE-017-field-page-publish-ui.md`
- `frontend/dist/`

## 完成标准

1. 字段页支持字段类型加载、字段列表、创建字段、编辑字段、启停字段。
2. P10 主链路字段类型至少覆盖 `TEXT`、`NUMBER`、`DATE`、`SELECT`；`RELATION`、`SUB_TABLE`、`JSON`、`ADDRESS`、`TAG` 暂不作为 E2E 必过项。
3. 页面配置页支持基于真实字段保存列表、表单、详情 schema，保存运行菜单和动作配置。
4. 页面支持发布检查和发布，能展示检查问题或发布版本号。
5. 发布后刷新运行菜单和运行 schema，不通过手工 URL 或控制台补数据。

## 验证

- `cd frontend; npm.cmd run build`
- P10 浏览器 E2E 第二段：创建字段、保存页面配置、发布检查、发布并确认运行 schema 可见。

## 不允许事项

- 不能使用伪造字段编码绕过真实字段列表。
- 不能把 PageModel-only 证据当成真实 UI 完成。
- 不能把流程工作台真实审批 UI 纳入本任务。
