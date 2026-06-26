# Phase 2 API 契约管理

> 状态：`design_user_approved=true`，API review 与 contract-sync 已通过，准备冻结 API。
> 硬闸门：`api_frozen=true`、`tasks_planned=true`，Build 可按正式任务单开始；禁止脱离 TASK 输出路径实现。
> 当前设计输入：`docs/design/prototype-brief.md`、`docs/design/prototypes/index.html`、`docs/design/reviews/prototype-latest-2026-06-18.md` 顶部当前开发口径。

## 1. 本阶段目标

把已签字的原型转成可开发、可测试、可拆任务的 API 与数据契约，解决字段、状态、权限、上下文、后台任务、审计链路和跨模块边界。冻结后才允许 planner 生成正式 `docs/tasks/plan.md` 与 `TASK-*.md`。

## 2. 并行分片

| 分片 | 责任角色 | 输出 | 状态 |
|---|---|---|---|
| DB 影响 | DBA | `docs/api/_draft/db-impact.md` | completed |
| 后端接口提案 | Backend | `docs/api/_draft/backend-proposal.md` | completed |
| 前端字段映射 | Frontend | `docs/api/_draft/frontend-mapping.md` | completed |
| 测试契约 | Test | `docs/api/_draft/test-contract.md` | completed |
| PM 合并 | PM | `docs/api/api.md` | completed |
| API review | Conductor | `docs/evidence/api-review-2026-06-23.md` | pass |
| contract-sync | Skill | `docs/evidence/contract-sync-2026-06-23.md` | pass |

四个分片输出路径不重叠，可以并行。PM 合并 `docs/api/api.md` 必须在四个分片完成后串行执行。

当前结果：

- DBA、Backend、Frontend、Test 四个分片均已完成，未报告 P0/P1 阻断。
- PM 已合并生成 `docs/api/api.md`，未报告 P0/P1 阻断。
- API review 已通过，未发现新的 P0/P1 API 内容阻断。
- contract-sync 初次发现 `frontend/src/api/` 类型目录缺失，已补充 `frontend/src/api/types.ts` 与 `frontend/docs/api-contract-map.md`。
- `ISS-C01` 已关闭，contract-sync 已通过。
- 下一步进入 Build 调度。每个任务必须按 `docs/tasks/TASK-*.md` 的 `outputs`、依赖和验收标准执行。

## 3. 合并原则

- 以用户已签字的设计为边界，不新增未经确认的产品范围。
- API 对象命名优先表达业务对象，不把页面名当后端模型名。
- 平台层与系统层必须分离；平台消息、平台 Agent 不得直接操作系统业务数据。
- 系统切换、租户切换、权限快照由服务端生成并返回，前端不得自行拼接。
- 动态业务数据归属 `systemId/tenantId/moduleId`，模块组只做导航与发布关系，不作为业务数据父级。
- Secret、SSO、OpenAPI、Webhook、外部模型密钥只使用 SecretRef/版本/轮换任务，不回显明文。
- 关键长任务统一后台任务契约：`taskId`、`bizType`、`idempotencyKey`、`status`、`progress`、`resultFile`、`errorFile`、`traceId`、`auditLogId`。
- 所有接口必须具备 requestId/traceId、错误码、权限失败原因、禁用原因、审计归属。

## 4. API 冻结前必须裁决的 P2 点

- `SystemSwitchContext` 与 `EffectivePermissionSnapshot` 是合并返回还是拆对象返回。
- 动态记录的 record/value/index/child/relation/history 表边界和首期索引策略。
- 流程节点快照、任务快照、字段权限与审批节点权限冲突时的优先级。
- 消息跳转目标模型如何统一表达平台目标、系统目标、业务对象目标和后台任务目标。
- Agent 写入确认、平台 Agent 任务确认和系统业务写入确认是否拆成三类接口。
- 首期 SSO 协议支持范围与禁用态反馈。

## 5. 不允许事项

- 脱离 `docs/tasks/TASK-*.md` 修改 `backend/**`、`frontend/**`、`sql/**` 做实现。
- 让并行任务写同一输出路径。
- 用 `defaultDrawer`、generic payload 或前端临时字段兜底 P0 能力。
- 把后续开发中的字段争议口头处理；必须进入 API 契约或 issue。
