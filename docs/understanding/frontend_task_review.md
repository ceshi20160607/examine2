# 前端任务拆分审查

## 审查结论：fail

从 frontend 角度看，`FE-001` 至 `FE-012` 已覆盖 typed SDK、路由上下文、登录/我的系统、平台中心、系统/RBAC/dict、应用配置、动态 schema、运行台、流程、文件导出、OpenAPI/审计/运维和前端自检主链路；依赖顺序也基本符合 `typed SDK -> 路由上下文 -> 页面`、`动态 schema -> 运行台/流程/文件导出`。

但任务计划当前宣称部分前端任务可并行，同时多个并行任务的输出都只声明为 `frontend/`，没有拆到不重叠子路径；页面任务也没有把每个任务的完成证据绑定到明确的页面接口映射产物。该问题会影响 Orchestrator 判断并行安全性和后续前端任务完成状态，因此 frontend 角度不建议冻结任务计划。

## 审查范围

- `docs/task_plan.md`
- `docs/tasks/`
- `docs/tasks/FE-001-typed-sdk-contract-map.md`
- `docs/tasks/FE-002-routing-layout-auth-context.md`
- `docs/tasks/FE-003-login-my-systems.md`
- `docs/tasks/FE-004-platform-center-pages.md`
- `docs/tasks/FE-005-system-member-rbac-dict-pages.md`
- `docs/tasks/FE-006-app-module-field-config-pages.md`
- `docs/tasks/FE-007-dynamic-schema-renderer.md`
- `docs/tasks/FE-008-runtime-workbench-pages.md`
- `docs/tasks/FE-009-flow-workbench-pages.md`
- `docs/tasks/FE-010-file-export-pages.md`
- `docs/tasks/FE-011-openapi-audit-ops-pages.md`
- `docs/tasks/FE-012-frontend-self-check.md`
- `docs/api.md`
- `docs/api_review.md`
- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/service_info.md`
- `.codex/state.json`

## 阻塞 issue 表

| issueId | 问题 | 影响 | 建议责任方 | 建议修改点 | 是否阻塞任务冻结 |
| --- | --- | --- | --- | --- | --- |
| FETASK-001 | `docs/task_plan.md` 的 B5/B6 声明 `FE-003`、`FE-004`、`FE-005`、`FE-011` 以及 `FE-008`、`FE-009`、`FE-010` 可并行，但这些任务文件的输出均为 `frontend/`，未拆分到不重叠的页面、组件、store、路由或文档子路径。 | Orchestrator 无法证明前端并行任务输出不冲突；并行实现时可能同时修改 `frontend/src/router`、`frontend/src/stores`、共享组件、`frontend/docs/api-contract-map.md` 等公共文件，导致覆盖或合并不确定。 | planner | 将 FE 任务输出拆到明确子路径并声明公共文件归属。例如：`FE-002` 负责 `frontend/src/router`、`frontend/src/layouts`、`frontend/src/stores`；`FE-003` 负责 `frontend/src/pages/auth` 和 `frontend/src/pages/my-systems`；`FE-004` 负责 `frontend/src/pages/platform`；`FE-005` 负责 `frontend/src/pages/system`；`FE-006` 负责 `frontend/src/pages/module-config`；`FE-007` 负责 `frontend/src/components/dynamic-schema`；`FE-008` 负责 `frontend/src/pages/runtime`；`FE-009` 负责 `frontend/src/pages/flow`；`FE-010` 负责 `frontend/src/pages/files` 和 `frontend/src/pages/export`；`FE-011` 负责 `frontend/src/pages/openapi`、`frontend/src/pages/audit`、`frontend/src/pages/ops`；`FE-012` 负责 `frontend/docs/api-contract-map.md` 和固定自检记录。若不拆路径，则将这些页面任务标记为串行。 | 是 |
| FETASK-002 | `FE-001` 只要求建立页面到 API ID 的映射草稿，`FE-012` 最终汇总 `frontend/docs/api-contract-map.md`；但 `FE-003` 至 `FE-011` 的完成条件未要求各自补齐页面级接口映射、必填参数、响应字段、枚举/状态/错误码、权限禁用态、空态/错误态和 requestId 行为。 | 单个页面任务可能在缺少契约映射证据的情况下被标记完成，问题集中到 `FE-012` 才暴露，导致前端实现阶段难以判断页面任务是否真正闭环。 | planner | 在 `FE-003` 至 `FE-011` 的输出/验收中增加页面级契约映射要求：每个任务必须补齐对应页面的路由、API ID、必填入参、响应字段、上下文依赖、枚举/状态/错误码、权限禁用态、空态/错误态、requestId 展示和无旁路请求检查。可以统一写入 `frontend/docs/api-contract-map.md` 的对应章节，或写入 `frontend/docs/page-contracts/FE-00x.md` 后由 `FE-012` 汇总校验。 | 是 |

## 非阻塞跟踪项

| itemId | 跟踪项 | 建议 |
| --- | --- | --- |
| FE-NB-001 | `FE-001`/`FE-002` 未显式写明前端工程脚手架、构建脚本、UI 库、状态管理和 Axios 封装落位。 | 可在 planner 修订时明确由 `FE-001` 创建 API SDK 与统一请求层，由 `FE-002` 补齐 Vite、路由、状态管理、布局和构建脚本；具体 React/Vue 选型仍按 `docs/service_info.md`、PRD 和实现阶段技术栈要求决定。 |
| FE-NB-002 | `FE-012` 的输出写了“前端自检记录”，但未固定文件路径。 | 建议固定为 `frontend/docs/frontend-self-check.md`，便于 validator/reviewer 查验 typecheck、lint/build 前检查、无旁路请求扫描和契约映射同步结果。 |
| FE-NB-003 | `FE-004`、`FE-005`、`FE-006` 的任务文件已覆盖接口命名空间和权限禁用，但空态、加载态、错误态、requestId 展示没有逐页展开。 | 可在修订 FETASK-002 时同步补到页面级契约映射，避免实现时遗漏平台/系统/配置页的异常和空态闭环。 |

## 是否允许任务计划冻结：frontend 角度结论

不允许冻结。

前端功能覆盖和依赖主线可接受，但在 planner 补齐前端并行任务的非冲突输出路径、页面级契约映射完成证据和固定自检记录路径前，任务拆分不足以可靠支撑后续 typed SDK、页面、状态和自检阶段。
