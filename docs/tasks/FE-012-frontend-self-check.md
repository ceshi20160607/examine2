# FE-012 前端自检与契约闭环

- taskId: FE-012
- 标题: 前端自检与契约闭环
- 负责角色: frontend
- 所属大任务/模块: 前端 / 自检
- 目标: 汇总页面级 API 映射证据，完成前端类型、契约和交互自检。
- 输入文件: `docs/api.md`、`frontend/`、`frontend/docs/page-contracts/`
- 输出文件或输出目录: `frontend/docs/api-contract-map.md`、`frontend/docs/frontend-self-check.md`

## 详细工作内容

- 校验页面、SDK、枚举、错误码、状态值和 `docs/api.md` 一致。
- 汇总 FE-002 至 FE-011 的页面级 API 映射证据，生成最终 `frontend/docs/api-contract-map.md`。
- 检查无散落 axios/fetch 旁路调用。
- 执行 typecheck、lint 或等价前端自检并记录结果。

## 完成状态定义

- 默认状态: done。
- 完成条件: `frontend/docs/api-contract-map.md` 与 `frontend/docs/frontend-self-check.md` 存在，前端自检通过或输出明确失败点。

## 完成记录

- 完成时间: 2026-06-07。
- 输出: `frontend/docs/api-contract-map.md`、`frontend/docs/frontend-self-check.md`。
- 自检: 已汇总 FE-002 至 FE-011 页面级证据；路由引用和页面证据中的真实 API ID 均存在于 `frontend/src/api/endpoints.ts`；源码目录未发现 `fetch`、`axios`、`XMLHttpRequest`、`new Request` 或硬编码 URL 旁路请求。
- 限制: 当前 `frontend/` 目录无 `package.json`/`tsconfig.json`，本机无 `tsc` 命令，正式 typecheck/lint/build 无法执行，已记录到 `frontend/docs/frontend-self-check.md`。

## 验收标准

- `frontend/docs/api-contract-map.md` 覆盖所有 MVP 页面和接口。
- 错误态、空态、权限禁用态和 requestId 展示有闭环。
- `frontend/docs/frontend-self-check.md` 记录 typecheck/lint/build 前检查、无旁路请求扫描、页面证据汇总结果和失败摘要。

## 测试/自检要求

- 运行前端类型检查和契约映射检查。

## 依赖任务

- FE-002
- FE-003
- FE-004
- FE-005
- FE-006
- FE-007
- FE-008
- FE-009
- FE-010
- FE-011

## 可并行关系

- 不可并行；必须在前端页面完成后执行。

## 不允许事项

- 不修改冻结 API。
- 不以构建缓存通过代替契约同步检查。
