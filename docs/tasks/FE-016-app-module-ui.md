# FE-016 应用与模块配置真实 UI

## 目标

把应用、模块路由从通用占位工作区升级为可操作的业务页面，让系统管理员能通过浏览器完成应用和模块的列表、创建、编辑、状态管理和上下文选择。

## 输入

- `docs/api.md`
- `docs/tasks/FE-006-app-module-field-config-pages.md`
- `frontend/src/App.ts`
- `frontend/src/pages/module-config/moduleConfigPageModel.ts`
- `frontend/src/api/`
- 后端已实现的 `APP-001` 至 `APP-005`、`MOD-001` 至 `MOD-007`

## 输出

- `frontend/src/App.ts`
- `frontend/src/styles.css` 如需补充布局
- `frontend/docs/page-contracts/FE-016-app-module-ui.md`
- `frontend/dist/`

## 完成标准

1. `apps.list` 页面支持应用列表、创建应用、编辑应用、启停应用、选择应用进入模块。
2. `modules.list` 页面支持模块列表、创建模块、编辑模块、状态操作、选择模块进入字段/页面配置。
3. 页面展示真实 `appId`、`moduleId`、状态、更新时间和 requestId。
4. 系统内请求必须依赖 `SYS-001` 真实系统、租户、成员和权限上下文，不允许使用 `preview-*` 发真实业务请求。
5. 所有请求走 `createModuleConfigPageModel` 和 typed SDK，不新增旁路 `fetch`、`axios`、`XMLHttpRequest` 或硬编码接口地址。

## 验证

- `cd frontend; npm.cmd run build`
- P10 浏览器 E2E 第一段：登录、进入系统、创建应用、创建模块并回显。

## 不允许事项

- 不能把首个 GET 成功当成任务完成。
- 不能顺手实现流程、文件、OpenAPI、审计运维真实 UI。
- 不能修改冻结 API，发现契约问题必须登记给 PM。
