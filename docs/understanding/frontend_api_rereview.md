# Frontend API 契约第 1 次闭环复核

## 复核结论：pass

本次复核为第 1 轮 API 契约闭环修订后的 frontend 复核，范围限定为首轮 `FEAPI-001` 至 `FEAPI-011`，以及 `docs/api_review.md` 中标记由 frontend 复核的 typed SDK、页面到接口映射、权限禁用态、动态 schema、运行台、流程工作台、文件导出、OpenAPI 管理、审计运维和幂等规则。

结论：frontend 原阻塞 issue 均已关闭。修订后的 `docs/api.md` 已足以支撑前端生成基础 typed SDK、统一 API 封装、错误码/状态/枚举定义、页面到接口映射和 MVP 页面实现。`docs/api.md` 当前仍标记 `api_frozen=false`，因此最终 API 冻结仍需 PM 在四角色复核完成后统一输出冻结结论；但从 frontend 角度允许进入 API 冻结和任务拆分。

## 复核范围

- 读取材料：`docs/prd.md`、`docs/project_understanding.md`、`docs/api.md`、`docs/api_review.md`、`docs/understanding/frontend_api_review.md`、`docs/service_info.md`、`.codex/state.json`。
- 未读取旧项目目录，未读取未声明输入，未修改 `docs/api.md`、前端代码、后端代码或 SQL。
- 复核重点：统一响应/错误/分页、DTO/VO 索引、权限禁用态、平台账号角色、系统内 RBAC catalog、动态 schema、运行台、流程工作台、文件导出、OpenAPI 管理、审计运维字段、幂等/防重复提交。

## issue 关闭复核表

| issueId | 原问题 | 复核结论 | 依据 | 如仍 open 的修改建议 |
| --- | --- | --- | --- | --- |
| FEAPI-001 | MVP API 缺逐接口 request/response DTO、分页、筛选、错误结构。 | closed | `docs/api.md` 已补 `ApiResponse<T>`、`ApiErrorResponse`、`errors[]`、`PageResult<T>`、统一筛选/排序 DSL，并在“逐模块 DTO/VO 补充索引”中给出平台、成员、RBAC、动态 schema、运行台、流程、文件、导出、OpenAPI、审计运维的请求 DTO/BO 与响应 VO 映射。 | - |
| FEAPI-002 | 权限与按钮禁用态模型不足。 | closed | `docs/api.md` 已定义 `AvailableAction`、`PermissionHint`、`FieldPermission`、`DataScopeRuleDTO`、`EffectivePermissionVO`，覆盖按钮显示/禁用、禁用原因、字段可见可写、数据范围和有效权限返回。 | - |
| FEAPI-003 | 平台账号/角色页面接口缺失。 | closed | `docs/api.md` 已补 `PLAT-013` 至 `PLAT-020`，覆盖账号详情、账号编辑、重置密码、账号角色分配、平台角色创建/编辑/启停和平台权限目录；DTO/VO 索引包含 `PlatformAccount*`、`PlatformRoleVO`、`PlatformPermissionCatalogVO`。 | - |
| FEAPI-004 | 系统内 RBAC 授权详情和可授权目录缺失。 | closed | `docs/api.md` 已补 `RBAC-012`、`RBAC-013`，分别读取角色已有授权和系统权限 catalog；授权保存入参包含菜单、操作、字段权限、数据范围和显式禁用。 | - |
| FEAPI-005 | 动态字段、页面配置、运行 schema 结构未冻结。 | closed | `docs/api.md` 已补 `FieldDefinitionVO`、`RuntimeModuleSchemaVO`、`PublishCheckResultVO`、`CheckIssueVO`，并明确 `listSchema`、`formSchema`、`detailSchema`、字段定义、权限提示和状态规则的来源。 | - |
| FEAPI-006 | 运行台查询、详情、字段错误、关联数据结构不足。 | closed | `docs/api.md` 已补 `RecordQueryBO`、`RecordListItemVO`、`RecordDetailVO`、`RecordMutationResultVO`、`RecordActionVO`、`FieldValidationErrorVO`，并冻结 `RUN-004`/`RUN-006` 保存与 `RUN-008` 提交语义、`recordVersion` 和字段校验错误结构。 | - |
| FEAPI-007 | 流程工作台和流程模板接口不足。 | closed | `docs/api.md` 已补 `FLOW-013` 至 `FLOW-021`，覆盖抄送、我的申请、领取/取消领取、实例列表、历史、模板详情、模板图和状态变更；DTO/VO 索引包含 `FlowTaskListItemVO`、`FlowTaskDetailVO`、`FlowHistoryItemVO`、`FlowDiagramVO`、`FlowActionResultVO`。 | - |
| FEAPI-008 | 文件中心和导出任务字段不足。 | closed | `docs/api.md` 已补 `FileUploadRequest`、`FileInfoVO`、`FileListItemVO`、`FileReferenceVO`、`ExportJobListItemVO`、`ExportJobDetailVO`，并明确 `previewableReason`、`downloadableReason`、`tempExpiresAt`、`pollingIntervalMs`、失败原因和导出文件权限。 | - |
| FEAPI-009 | OpenAPI scope catalog、凭证展示状态、调用日志字段不足。 | closed | `docs/api.md` 已补 `OPM-009` scope catalog、`OpenApiScope`、`OpenApiCredentialOnceVO`、`OpenApiAccessLogVO`、HMAC-SHA256 签名、`maskedSecret`、`secretVisibleOnce=false`、nonce、限流和日志字段。 | - |
| FEAPI-010 | 审计/运维日志详情和健康检查结构不足。 | closed | `docs/api.md` 已补 `AuditLogDetailVO`、`BeforeAfterSnapshotVO`、`HealthCheckResultVO`、`OpsComponentStatusVO`、`AUD-007`、`AUD-008`、`OPS-006`，支持 requestId 检索、日志详情、前后快照、耗时和组件健康状态展示。 | - |
| FEAPI-011 | 幂等/防重复提交覆盖不完整。 | closed | `docs/api.md` 已定义内部 API 与 OpenAPI 幂等策略、`X-Idempotency-Key`/`idempotencyKey` 归一化、必需幂等接口清单、requestHash、TTL、结果快照、`COMMON_IDEMPOTENCY_CONFLICT`、`COMMON_IDEMPOTENCY_PROCESSING`、`OPENAPI_IDEMPOTENCY_CONFLICT` 和 `Retry-After`。 | - |

## 剩余阻塞 issue

无 frontend 阻塞 issue。

非阻塞说明：部分补充接口先出现在“第 1 次审查闭环补充契约”中，后续 PM 冻结版可把补充接口同步合并到对应模块主表，提升阅读一致性；该点不影响 frontend typed SDK 和页面实现。

## frontend 角度结论

- 是否允许 API 冻结：允许，frontend 无剩余阻塞意见。
- 是否允许进入任务拆分：允许，frontend 可基于当前契约拆分 typed SDK、统一请求层、页面到接口映射和业务页面实现任务。
- 限制条件：最终冻结状态以 PM 汇总四角色复核后的 `docs/api_review.md` 为准；当前 `.codex/state.json` 与 `docs/api.md` 仍显示 `api_frozen=false`。
