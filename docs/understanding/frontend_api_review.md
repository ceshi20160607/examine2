# Frontend API 契约审查

## 审查结论：fail

当前 `docs/api.md` 已覆盖平台中心、系统管理、应用配置、模块建模、运行台、流程、文件、导出、OpenAPI、审计运维等主要 API 分组，也定义了统一响应、分页方向、动态字段值、状态枚举和错误码命名空间。但契约粒度仍偏“接口清单 + 关键字段摘要”，不足以直接生成前端 typed SDK，也不足以稳定支撑页面级按钮权限、禁用原因、动态 schema 渲染、流程工作台、OpenAPI 凭证管理和审计运维页面。

结论：**不允许进入任务拆分阶段**。需要 PM/API 先补齐阻塞项后再复核。

## 审查范围

- 页面映射：登录/我的系统、平台中心、系统管理、成员组织角色权限、应用配置、模块字段页面配置、运行台、流程、文件、导出、OpenAPI、审计运维。
- typed SDK：统一响应、分页、筛选、排序、请求 DTO、响应 VO、动态字段值、运行 schema、状态枚举、错误码。
- 交互闭环：权限禁用态、空态、错误态、loading/防重复提交、requestId 展示、凭证只展示一次、导出任务轮询、上传临时文件、流程待办、OpenAPI 签名管理。

## 阻塞 issue 表

| issueId | 问题 | 影响 | 建议责任方 | 建议修改点 | 是否阻塞 API 冻结 |
| --- | --- | --- | --- | --- | --- |
| FEAPI-001 | 多数接口只给出分组级“关键入参/关键出参”，没有按 API ID 定义 request/response DTO、字段必填、可空、枚举、分页、筛选、排序、错误响应和返回数据结构。涉及 PLAT-001、PLAT-003、MEM-001、RBAC-005、APP-001、MOD-001、RUN-003、FLOW-007、FILE-002、EXP-005、OPM-001、AUD-001 等列表接口。 | 前端无法生成可靠 typed SDK，也无法建立页面到接口字段映射；列表筛选、表单提交和错误提示会依赖猜测。 | PM/API | 为每个 MVP API 补充独立 DTO/VO 表；列表接口明确是否分页、允许筛选字段、允许排序字段、records item 结构；详情/保存接口明确字段来源、是否前端可写、空值规则。 | 是 |
| FEAPI-002 | 权限与按钮禁用态契约不足。API 仅在 RUN-005 提到 `actions`，UI 配置提到未定义结构的 `permissionHints`，没有统一的 `actionCode`、`visible`、`enabled`、`disabledReason`、`requiredPermission`、`stateReason` 结构。 | PRD 要求新增、编辑、删除、提交审批、导出、发布、启停、授权、轮换凭证等按钮按权限和状态过滤或禁用并说明原因；当前前端无法稳定判断按钮展示、禁用和提示文案。 | PM/API | 定义统一 `PermissionHint`、`AvailableAction`、`FieldPermission` 响应模型；在我的系统、平台中心、成员角色、应用、模块、页面配置、运行记录详情、流程任务、导出任务、OpenAPI 客户端等页面响应中明确返回位置。 | 是 |
| FEAPI-003 | 平台账号/角色页面接口缺失。PRD 页面包含账号新增、编辑、启停、重置密码、角色授权；当前只有账号创建、账号状态变更、角色列表和角色菜单授权，缺少账号编辑、管理员重置密码、账号角色绑定、角色创建/编辑/启停/详情、平台菜单/操作权限目录。 | 平台账号/角色管理 MVP 页面无法闭环，前端不能渲染授权树，也不能实现编辑和重置密码按钮。 | PM/API | 补充平台账号编辑、重置密码、账号角色分配接口；补充平台角色 CRUD/status/detail；补充平台菜单和操作权限 catalog 接口，并定义角色授权详情响应。 | 是 |
| FEAPI-004 | 系统内 RBAC 授权编辑契约不完整。RBAC-009 仅定义保存入参，未定义角色权限详情读取、系统菜单/操作/字段/数据范围/OpenAPI scope/导出权限的可授权对象目录，也未定义数据范围结构化条件。 | 角色权限页无法加载现有授权、无法构建菜单/按钮/字段/数据范围授权控件，也无法在保存后正确刷新权限缓存和禁用原因。 | PM/API | 增加 `GET /rbac/roles/{roleId}` 或 `GET /rbac/roles/{roleId}/permissions`；增加系统权限 catalog 接口，返回菜单树、操作码、模块字段、导出权限、OpenAPI scope、数据范围类型和结构化条件 schema。 | 是 |
| FEAPI-005 | 动态字段、页面配置和运行 schema 结构未冻结。`listSchema`、`formSchema`、`detailSchema`、`columns`、`filters`、`formSections`、`detailBlocks`、`permissionHints`、字段 `options`、`relationConfig`、`subTableConfig`、`serialConfig` 均没有字段级结构。发布检查也未定义检查项结构。 | 模块/字段设计器、页面配置器、运行台列表和动态表单不能 typed 渲染；发布失败无法展示检查清单和修复入口。 | PM/API | 定义 `FieldDefinition`、`FieldOption`、`RelationConfig`、`SubTableConfig`、`ListViewSchema`、`FormSchema`、`DetailSchema`、`RuntimeModuleSchema`、`PublishCheckResult`、`CheckIssue` 等模型，并标明草稿/发布版本字段。 | 是 |
| FEAPI-006 | 运行台记录查询与详情契约不足。RUN-003 未定义查询请求的筛选操作符、关键字字段、高级筛选结构、返回行的动态字段值、主显示字段、状态/流程状态、行级动作；RUN-004/RUN-006 未定义字段级校验错误返回结构；RUN-010 未定义关联数据查询结构。 | 业务列表、表单/详情、字段错误定位、关联字段选择器、批量操作和状态按钮无法实现；前端无法做到“校验失败定位字段并显示 requestId”。 | PM/API | 补充动态查询 DSL、允许筛选/排序字段来源、`RecordListItem`、`RecordDetail`、`RecordAction`、`FieldValidationError`、关联数据查询响应；明确 `recordStatus`、`flowStatus`、`flowSummary`、`fileRefs` 的结构。 | 是 |
| FEAPI-007 | 流程工作台接口不足。PRD 要求待办/抄送/我的申请、领取、撤回、终止、流程图、历史；当前只有 todo、任务详情、动作、撤回、实例详情/图，缺少抄送列表、我的申请列表、领取接口、任务列表分页筛选和任务详情字段结构；流程模板也缺少图读取、基础信息更新和状态变更接口。 | 流程工作台不能覆盖 PRD 页面；审批详情、流程配置、重复处理提示和按钮禁用态都无法稳定实现。 | PM/API | 补充 todo/cc/started 列表接口或统一工作台查询接口；补充 claim/领取接口或明确不做领取并修改 PRD；定义 `FlowTaskListItem`、`FlowTaskDetail`、`FlowHistoryItem`、`FlowDiagram`、`FlowActionResult`；补充流程模板详情/图读取/status 接口。 | 是 |
| FEAPI-008 | 文件中心与导出任务契约仍不够页面化。FILE-002 未定义文件列表分页、筛选、引用对象、上传人、预览/下载可用原因；FILE-001 未定义 multipart 字段、上传响应和临时文件过期信息；EXP-005/006 未定义任务列表字段、轮询建议、结果文件下载入口和失败重试边界字段。 | 文件列表/导出任务页面无法完整展示文件状态、引用对象、失败原因、可重试、可下载/可预览禁用原因；上传临时文件和导出轮询只能猜测。 | PM/API | 定义 `FileUploadRequest`、`FileInfo`、`FileListItem`、`FileReference`、`ExportJobCreateRequest`、`ExportJobListItem`、`ExportJobDetail`；补充 `previewableReason`、`downloadableReason`、`tempExpiresAt`、`pollingIntervalMs` 或轮询策略说明。 | 是 |
| FEAPI-009 | OpenAPI 管理契约缺少 scope catalog、签名算法展示信息和凭证展示状态。OPM-001/002/005 只给出 `secretOnce`，未定义既有客户端是否返回 `maskedSecret`、`secretVisibleOnce`、轮换响应结构；OPM-006 未定义 scope 的模块/字段/流程/文件粒度；OPM-008 未定义调用日志分页和字段。 | OpenAPI 客户端页无法构建 scope 选择器、凭证一次性展示弹窗、复制状态和调用日志筛选；签名管理说明页缺少稳定数据来源。 | PM/API | 增加 scope catalog 接口；定义 `OpenApiClientListItem`、`OpenApiCredentialOnce`、`OpenApiScope`、`OpenApiRateLimitPolicy`、`OpenApiAccessLog`；明确签名算法、签名串示例字段和 secret 后续掩码展示规则。 | 是 |
| FEAPI-010 | 审计/运维接口无法支撑 PRD 中的日志详情和运维页面。AUD-* 只定义列表字段摘要，未定义分页、日志详情、错误栈摘要、操作前后快照、耗时、级别；OPS-* 只定义通用响应字段，没有健康检查项结构、组件状态枚举和刷新结果结构。 | 运维审计页面只能做粗略列表，不能实现 requestId 检索、日志详情、健康异常处理建议和导出日志预留入口。 | PM/API | 定义审计日志分页 DTO、`AuditLogDetail`、`BeforeAfterSnapshot`、`ErrorStackSummary`、`HealthCheckResult`、`OpsComponentStatus`；明确 `result/level/status` 枚举。 | 是 |
| FEAPI-011 | 幂等/防重复提交契约覆盖不完整。RUN、FLOW、EXP、OpenAPI 已部分包含 `idempotencyKey`，但创建系统、应用、模块、字段、发布、流程模板发布、文件上传、OpenAPI 凭证轮换等高风险按钮没有统一幂等字段或重复提交返回规则。 | 前端虽可在 loading 中禁用按钮，但刷新、重试、网络抖动或多端重复点击时仍可能产生重复数据；typed SDK 无法统一处理 `COMMON_IDEMPOTENCY_CONFLICT`。 | PM/API | 明确哪些写接口必须支持 `idempotencyKey` 或 `X-Idempotency-Key`，哪些只靠状态锁；为重复提交定义统一错误码、可重试策略和响应结构。 | 是 |

## 非阻塞跟踪项

| issueId | 跟踪项 | 建议 |
| --- | --- | --- |
| FEAPI-NB-001 | `docs/api.md` 标题仍为“草案”，状态说明与 `.codex/state.json` 的当前步骤存在轻微不一致。 | PM 冻结时统一改为冻结版本号、冻结时间和评审状态。 |
| FEAPI-NB-002 | 移动端 MVP 只覆盖轻量运行和审批，但 API 未单独区分移动端字段裁剪。 | 可先复用 Web API，前端实现阶段在 schema 中做移动端展示裁剪；若后端需移动端轻量响应，再作为增强契约。 |
| FEAPI-NB-003 | 导入接口已标记 PLACEHOLDER，MVP 不实现导入执行闭环。 | 前端实现阶段不要提供导入执行按钮，只可在导出中心保留禁用或隐藏入口。 |
| FEAPI-NB-004 | 代码生成器接口为 PLACEHOLDER/INTERNAL。 | 前端 MVP 不实现生成器页面，除非后续 task_plan 单独声明内部运维页面。 |

## 允许/不允许进入任务拆分的结论

**不允许进入任务拆分。**

当前 API 契约还不能支撑前端 typed SDK 和页面实现。PM/API 需要至少补齐：

1. 每个 MVP API 的请求/响应 DTO、分页筛选排序结构和错误结构。
2. 动态 schema、字段配置、运行记录查询、流程任务、文件导出、OpenAPI、审计运维的字段级模型。
3. 统一权限动作和禁用原因模型。
4. 平台账号/角色、系统内 RBAC、流程工作台、OpenAPI scope 等缺失接口。

复核条件：`docs/api.md` 更新后，前端可以从契约直接生成 `frontend/src/api/` 类型定义、错误码/枚举定义和 `frontend/docs/api-contract-map.md`，且不需要猜测任何页面接口、按钮权限、动态字段 schema 或错误响应字段。
