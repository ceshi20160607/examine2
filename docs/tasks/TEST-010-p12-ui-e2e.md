# TEST-010 P12 UI 可用性 E2E

- 所属期次：P12-uiux-frontend-rework
- 负责人：test
- 状态：blocked/rework（真实链路已跑到模块发布和导出成功；运行台 UI 新建记录 `COMMON_PARAM_INVALID`，且查询已存在记录时报 `Cannot read properties of undefined (reading 'find')`，阻塞提交审批/流程处理）

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
