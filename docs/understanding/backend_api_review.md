# backend API 契约审查

## 审查结论：fail

当前 `docs/api.md` 已覆盖主要业务域和接口方向，但还不能作为后端实现冻结契约进入任务拆分。主要阻塞点是：上游 `docs/project_understanding.md` 与当前 API 阶段状态冲突；多数组接口仍停留在“关键字段清单”层级，未冻结到可直接落地 Controller、BO/DTO、VO、枚举、错误码触发条件、权限上下文、幂等和事务补偿规则。

## 审查范围

- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- `docs/legacy_reference.md`
- `docs/service_info.md`
- `.codex/state.json`

本次未读取 `.codex/oldexamine/`，旧项目信息仅基于 `docs/legacy_reference.md` 判断。

## 阻塞 issue 表

| issueId | 问题 | 影响 | 建议责任方 | 建议修改点 | 是否阻塞 API 冻结 |
| --- | --- | --- | --- | --- | --- |
| BAPI-001 | `docs/project_understanding.md` 仍表述“当前不允许进入 API 契约阶段”“活动区 PRD 不存在”，但 `.codex/state.json` 与 `docs/api.md` 已进入 API 草案生成阶段。 | API 文档的生成前置条件和项目理解结论不一致，后续 reviewer/test 无法判断 API 是否基于已关闭的理解 issue 生成。 | PM | 重新生成或修订 `docs/project_understanding.md`，明确 PRD 复核已通过、哪些理解 issue 已关闭或降级、允许进入 API 契约阶段的结论，再据此修订 API 生成说明。 | 是 |
| BAPI-002 | 多数接口只有接口清单和“关键入参/出参”，没有按 API ID 冻结请求 BO/DTO、查询 BO、响应 VO 的字段、类型、必填、默认值、校验规则、来源、前端可写性和分页筛选规则。 | 后端实现时无法稳定创建 Controller 方法签名和 BO/VO；同一字段在列表、详情、保存、更新、状态变更中的可写性容易返工；测试无法逐接口断言。 | PM/API | 按 MVP API ID 补齐契约表，例如 `PLAT-006`、`MEM-001`、`RBAC-009`、`RUN-003`、`EXP-005` 等分页接口的 query/filter/sort/page 结构；保存、更新、状态变更接口分别列 SaveBO/UpdateBO/StatusBO 和对应 VO。 | 是 |
| BAPI-003 | PRD 明确平台账号/角色管理、系统字典、模块建模字典能力，但 API 缺少平台账号编辑、重置密码、平台账号角色授权、平台角色创建编辑，以及系统字典 CRUD/启停接口；`模块建模` 总览提到“字典”，正文未提供接口。 | MVP 范围存在缺口，后端若按 PRD 实现会新增隐藏接口；若只按 API 实现则平台账号角色和字典页面无法闭环。 | PM/API | 补齐或明确降级：平台账号详情/编辑/重置密码/授权、平台角色新增编辑启停、系统字典类型和字典项 CRUD/状态接口；若字典不进 MVP，需要在 API 与 PRD 中同步标为 ENH/PLACEHOLDER。 | 是 |
| BAPI-004 | 状态枚举只列出值，没有按对象和操作冻结允许流转、禁止流转、前置状态和目标状态；错误码只列名称，缺少触发条件、前端提示和是否可重试。 | `PATCH status`、审批动作、导出重试/取消、OpenAPI 客户端启停等实现会出现不同口径；错误码枚举无法准确维护，前后端和测试无法对齐。 | PM/API | 为账号、系统、租户、应用、模块、字段、记录、流程模板/实例/任务、文件、导出任务、OpenAPI 客户端补充状态流转矩阵；为每个模块错误码补齐触发条件、前端提示、是否可重试、适用 API ID。 | 是 |
| BAPI-005 | 平台登录、系统进入、租户切换、成员上下文和 RBAC 校验顺序有方向，但缺少会话上下文返回/刷新规则、`X-Tenant-Id` 与路径 `systemId` 的一致性校验、单租户/多租户差异、显式禁用优先和数据范围结构化表达。 | 安全过滤器、拦截器和业务 service 的权限顺序无法完全落地；平台管理员、系统成员、租户管理员、OpenAPI 客户端的边界容易产生越权或误拒。 | PM/API | 补齐 `SYS-001`、`SYS-007` 的请求/响应和上下文落点；定义 `EffectivePermissionVO`、`DataScopeRuleDTO`、字段权限/导出权限/OpenAPI scope 的结构；明确每类接口的权限校验顺序和失败错误码。 | 是 |
| BAPI-006 | 运行记录保存与提交审批语义冲突：`RUN-004` 允许 `submitAfterSave`，状态影响写“进入 SUBMITTED”；`RUN-008` 又单独提交并创建流程实例，PRD 则要求提交后同步创建实例并进入审批中。`idempotencyKey` 对运行写入也是可选。 | 后端无法判断保存并提交应调用哪套事务编排；记录状态 `SUBMITTED` 与 `IN_APPROVAL` 的落点不清；自动编号、附件绑定和流程创建在重复请求下存在并发风险。 | PM/API | 明确保存草稿、保存并提交、单独提交三种语义是否都支持；若支持 `submitAfterSave`，需定义它与 `RUN-008` 等价的事务和响应；运行写接口、提交接口应要求幂等键或明确服务端幂等策略，并补充 recordVersion/乐观锁规则。 | 是 |
| BAPI-007 | 附件与文件引用契约不够细：上传入参包含 `recordId` 但业务保存前可能没有记录；没有冻结动态字段附件值到文件引用的绑定/解绑 BO、引用对象类型、回滚补偿、已引用文件删除限制和导出结果文件下载权限。 | 业务保存、附件绑定、导出结果文件和文件中心权限实现边界不清，可能出现临时文件泄漏、引用计数错误或绕过业务对象权限下载。 | PM/API | 定义 `FileRefDTO`、附件字段值格式、记录保存时绑定/解绑规则、业务失败时保留过期清理还是本次上传补偿删除、导出结果文件的权限校验和下载入口复用规则。 | 是 |
| BAPI-008 | 流程工作台 API 未覆盖 PRD 的待办、抄送、我的申请、领取/取消领取等场景；`RETURN` 动作缺少退回目标节点或退回策略；流程动作请求缺少业务记录版本/任务版本并发校验字段。 | 审批实现会缺少 MVP 页面入口和并发防重依据；退回、转交、终止等动作容易在状态联动和业务回写上返工。 | PM/API | 补齐或降级抄送、我的申请、领取/取消领取接口；为 `FLOW-009` 补充 `targetNodeId`/退回策略、`taskVersion` 或并发 token、动作到实例状态/业务记录状态的映射矩阵。 | 是 |
| BAPI-009 | OpenAPI 外部调用只写“按签名算法生成”，未冻结 canonical string、body hash、时间窗口、nonce TTL、幂等键存储维度、冲突响应；外部记录路径没有稳定的模块上下文，scope 也未细化到模块/字段/动作/数据范围结构。 | 外部系统无法按契约实现签名；后端 OpenAPI 拦截器和幂等服务不可测试；记录查询/写入可能无法准确命中模块、字段权限和数据范围。 | PM/API | 补充签名算法规范、参与签名字段、请求体摘要、时间窗口、nonce 有效期、幂等 key 维度和重复/冲突响应；外部记录接口显式包含 `moduleCode` 路径或必填参数；定义 OpenAPI scope 结构和字段级读写授权。 | 是 |
| BAPI-010 | 导出任务契约缺少异步任务领取/执行内部边界、筛选与 `selectedRecordIds` 同传时的优先级、任务状态流转细则、重试次数/失败原因结构、结果文件下载与权限复核规则。 | 导出任务是 MVP 闭环，后端 runner、重试、取消、结果下载和权限快照实现没有冻结依据。 | PM/API | 补充导出任务状态流转、创建幂等规则、筛选快照结构、字段权限/脱敏快照结构、runner 领取锁或内部 service 边界、失败原因结构、结果文件下载复用 `FILE-005` 还是单独入口。 | 是 |

## 非阻塞跟踪项

- 多模块、`base/manage` 分层、`examine-web` 只做启动与装配、`examine-generator` 只生成 `base` 层的方向是清楚的；实现阶段需在任务计划中继续拆到具体 Maven 子模块。
- `examine-generator` 运维接口作为 PLACEHOLDER/INTERNAL 可以不进入首期前端，但后端实现阶段仍必须按要求提供命令行生成器和 `backend/docs/mybatis-plus-generation.md`。
- 统一响应、分页响应、动态字段值基础结构已有方向；待阻塞 issue 修订后，可作为 typed SDK 和后端通用模型基础。
- OpenAPI 与内部 API 已在路径前缀上分离；仍需补齐签名、scope 和幂等细则后才能进入冻结。

## 允许/不允许进入任务拆分的结论

不允许进入任务拆分。

需先由 PM/API 修订 `docs/project_understanding.md` 与 `docs/api.md`，关闭或降级上述阻塞 issue，并重新组织 backend API 复核。当前版本若直接进入后端实现，会导致隐藏接口、BO/VO 返工、权限边界返工以及 OpenAPI/流程/导出等核心能力不可测试。
