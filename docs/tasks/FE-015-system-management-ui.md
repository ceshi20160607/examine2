# FE-015 系统管理域真实 UI

## 目标

把成员、部门、系统角色、字典从通用占位工作区升级为可操作的系统管理业务页面，确保系统管理员进入自定义系统后可以完成基础组织、权限和字典配置。

## 输入

- `docs/process/development_governance.md`
- `docs/api.md`
- `docs/issues/pm/development/p9_frontend_wenti.md`
- `frontend/src/App.ts`
- `frontend/src/router/index.ts`
- `frontend/src/pages/system/pageModels.ts`
- `frontend/src/pages/system/types.ts`
- `frontend/src/api/`
- 后端已实现的 `SYS-*`、`MEM-*`、`RBAC-*`、`DICT-*` 接口

## 输出

- `frontend/src/App.ts`
- `frontend/src/styles.css`
- `frontend/src/pages/system/pageModels.ts` 如需适配数组返回或字典删除 body
- `frontend/docs/page-contracts/FE-015-system-management-ui.md`
- `frontend/dist/`

## 完成标准

1. `system.members` 支持成员列表、邀请或绑定成员、详情查看、成员扩展编辑、启停和分配角色。
2. `system.departments` 支持部门树加载、创建根/子部门、编辑、删除空部门；有成员或子部门时删除禁用并展示原因。
3. `system.roles` 支持角色列表、创建/编辑、启停、权限目录加载、已有权限回显和授权保存；保存后刷新有效权限。
4. `system.dict` 支持字典类型、字典项树、创建/编辑、启停、usage 查询、删除限制和缓存版本提示。
5. 所有系统内请求先依赖 `SYS-001` 得到真实系统、租户、成员和权限上下文，不使用 `preview-*` 发真实业务请求。
6. 所有请求走 typed SDK/PageModel，不新增 `fetch`、`axios`、`XMLHttpRequest` 或硬编码接口地址。
7. 页面文案默认中文，权限不足、上下文缺失、后端错误、requestId 都有明确显示。

## 验证

- `cd frontend; npm.cmd ci; npm.cmd run build`
- 本机启动前后端后执行浏览器 E2E，记录到 `docs/test_runs/p9-system-management-ui-e2e-20260609.md`。
- `git diff --check`

## 执行记录

- 实现文件：`frontend/src/App.ts`、`frontend/src/styles.css`。
- 页面证据：`frontend/docs/page-contracts/FE-015-system-management-ui.md`。
- clean build：`npm.cmd ci` pass，`npm.cmd run build` pass。
- 限制：Chrome headless dump-dom 在当前 Windows 会话中未输出 DOM；完整浏览器 E2E 仍由 `TEST-007` 执行，FE-015 暂不代表 P9 accepted。

## 不允许事项

- 不能把 PageModel-only、首个 GET 通过或 build 通过当作 P9 完成。
- 不能顺手实现应用模块、运行台、流程、文件、OpenAPI、审计运维 UI。
- 不能修改冻结 API，除非 PM 重新打开契约评审。
