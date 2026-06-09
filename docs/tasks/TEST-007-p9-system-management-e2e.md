# TEST-007 P9 系统管理域浏览器 E2E

## 目标

验证 P9 成员、部门、系统角色、字典真实 UI 是否可由用户通过浏览器完成核心流程。

## 输入

- `frontend/dist/`
- `docs/tasks/FE-015-system-management-ui.md`
- `docs/issues/pm/development/p9_frontend_wenti.md`
- 后端本机服务和前端预览服务

## 输出

- `docs/test_runs/p9-system-management-ui-e2e-20260609.md`
- `docs/test_report.md` 如需更新总测试结论

## 完成标准

1. 使用 `platform_admin / 123123aa` 登录并通过 `SYS-001` 进入真实系统。
2. 成员页完成列表、邀请或编辑、启停、分配角色。
3. 部门页完成树加载、创建、编辑、删除限制验证。
4. 系统角色页完成角色创建、权限目录加载、授权保存和有效权限刷新。
5. 字典页完成类型/项创建、启停、usage 查询、删除阻断或成功删除。
6. 记录每步 API ID、HTTP 结果、requestId、关键页面反馈和失败 target。

## 不允许事项

- 不允许只用接口脚本代替浏览器 E2E。
- 不允许只验证 GET，不验证写操作和权限变化。
