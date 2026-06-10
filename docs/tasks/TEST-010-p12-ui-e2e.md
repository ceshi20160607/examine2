# TEST-010 P12 UI 可用性 E2E

- 所属期次：P12-uiux-frontend-rework
- 负责人：test
- 状态：done/pass（2026-06-11 已完成真实浏览器复测；运行台新建记录、查询记录、提交审批和权限多选展示均通过）

## 目标

验证 P12 UI 改造后的真实用户主链路，而不是只验证接口返回。

## 输入

- `docs/ui/ui-design.md`
- `frontend/dist/`
- `backend/examine-web/target/unexamine.jar`

## 输出

- `docs/test_runs/p12-ui-usable-e2e.md`

## 验收标准

- 登录、创建系统、进入系统总览。
- 完成成员/角色基础配置。
- 创建应用、模块、字段、页面配置并发布。
- 在运行台新建记录、提交审批、处理流程、创建导出、检索审计。
- 记录页面截图或 DOM 关键断言。

## 复测结果

- 复测方式：本机 Chrome headless DevTools Protocol 真实浏览器 DOM 操作，前端 `http://127.0.0.1:4173/`，后端 `http://127.0.0.1:18080`。
- 复用上一轮已发布系统 `2064692771705384961` 和模块 `2064693917962530818`。
- 运行台新建记录 `P12 UI retest 20260610172945` 成功，列表行可见。
- 关键字查询后记录仍在 `.data-row.records` 列表行中可见。
- 点击提交后记录状态变为 `SUBMITTED`，页面显示 `RUN-008 操作成功：COMMON_OK`。
- 流程工作台刷新成功，`FLOW-007/FLOW-013/FLOW-014/FLOW-017` 返回成功，无 4xx/5xx 响应。
- 系统角色权限目录加载后，操作权限下拉不再出现 `[object Object]`。
