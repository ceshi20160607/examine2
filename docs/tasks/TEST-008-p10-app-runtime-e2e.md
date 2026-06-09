# TEST-008 P10 应用配置到运行台浏览器 E2E

## 目标

验证 P10 是否能由用户通过浏览器完成应用、模块、字段、页面配置、发布和运行台记录主链路。

## 输入

- `frontend/dist/`
- `docs/tasks/FE-016-app-module-ui.md`
- `docs/tasks/FE-017-field-page-publish-ui.md`
- `docs/tasks/FE-018-runtime-record-ui.md`
- 后端本机服务和前端预览服务

## 输出

- `docs/test_runs/p10-app-runtime-ui-e2e-20260609.md`
- `docs/test_report.md` 如需更新总测试结论
- 浏览器截图，建议保存到 `frontend/docs/p10-app-runtime-e2e-final.png`

## 完成标准

1. 使用 `platform_admin / 123123aa` 登录并通过 `SYS-001` 进入真实系统。
2. 应用页通过页面触发 `APP-001`、`APP-002`，新应用列表回显。
3. 模块页通过页面触发 `MOD-001`、`MOD-002`，新模块列表回显。
4. 字段页通过页面触发 `FIELD-001`、`FIELD-005`、`FIELD-002`，字段列表回显。
5. 页面配置页通过页面触发 `UI-001` 至 `UI-008` 的 MVP 保存能力，并触发 `MOD-006`、`MOD-007` 发布。
6. 运行台通过页面触发 `RUN-001`、`RUN-002`、`RUN-003`、`RUN-004`、`RUN-005`、`RUN-006`、`RUN-009`，提交入口按契约记录 `RUN-008` 结果。
7. 执行记录必须包含系统/租户/成员 ID、应用 ID、模块 ID、字段 ID、记录 ID、API ID、HTTP 状态、requestId 和关键页面反馈。

## 判定

- `pass`：全部核心链路由页面点击/输入完成，核心 API 返回成功或契约内提交前置结果。
- `partial`：某一段只能通过 API/SDK 辅助完成，或提交因 P11 流程前置缺失返回契约内错误，但保存、编辑、历史已通过。
- `fail`：登录、真实上下文、应用/模块/字段/发布或运行记录核心写操作无法由页面完成，或出现非预期 4xx/5xx。

## 不允许事项

- 不允许只用接口脚本代替浏览器 E2E。
- 不允许使用 `preview-*` 上下文或 URL 参数伪造系统上下文。
- 不允许只验证 GET，不验证写操作、回显和 requestId。
