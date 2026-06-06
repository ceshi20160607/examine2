# backend API 契约第 1 次闭环复核

## 复核结论：fail

本次为第 1 轮 API 契约闭环修订复核。`docs/api.md` 对 DTO/VO 粒度、平台账号/角色、字典、RBAC、权限上下文、运行保存/提交、附件、流程、导出、OpenAPI 和幂等规则的补充，已经可以支撑后端进入接口设计与实现拆分。

但 BAPI-001 仍未关闭：`docs/project_understanding.md` 仍明确写着当前活动区 `docs/prd.md` 不存在、当前不允许进入 API 契约阶段、仅允许进入 PRD 生成；这与 `.codex/state.json`、`docs/api.md`、`docs/api_review.md` 中当前已处于 step 13 API 闭环复核的说明冲突。API/review/state 可以说明“当前正在 API 复核”，但不能替代 `docs/project_understanding.md` 作为上游冻结产物被修订。后端实现阶段会同时读取 `docs/project_understanding.md` 和 `docs/api.md`，若直接冻结 API，会留下输入产物互相否定的问题。

因此 backend 角度不允许 API 冻结，不允许进入任务拆分。需要 PM 退回项目理解模式修订 `docs/project_understanding.md`，明确 PRD 已存在、理解 issue 已关闭或降级、允许进入 API 契约阶段，然后再由 PM/API 输出冻结结论。

## 复核范围

- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- `docs/api_review.md`
- `docs/understanding/backend_api_review.md`
- `docs/legacy_reference.md`
- `docs/service_info.md`
- `.codex/state.json`

本次未读取 `.codex/oldexamine/`，未写代码、未生成 SQL、未修改 `docs/api.md`。

## issue 关闭复核表

| issueId | 原问题 | 复核结论 | 依据 | 如仍 open 的修改建议 |
| --- | --- | --- | --- | --- |
| BAPI-001 | `project_understanding.md` 历史状态与当前 API 阶段冲突。 | still_open | `docs/project_understanding.md` 仍保留“活动区 PRD 不存在”“当前不允许进入 API 契约阶段”“仅允许进入 PRD 生成”的旧结论；`docs/api.md` 和 `docs/api_review.md` 只说明当前以 state 的 step 13 为准，未修订上游冻结产物本身。 | 第 1 轮 BAPI-001 未关闭。PM 需退回项目理解模式修订 `docs/project_understanding.md`：确认当前 PRD 存在且已通过理解复核、理解 issue 关闭或降级、允许进入 API 契约阶段；随后再更新 API review 冻结结论。 |
| BAPI-002 | 多数接口未按 API ID 冻结 BO/DTO/VO、校验、来源、可写性、分页筛选。 | closed | `docs/api.md` 已补统一响应、分页 DSL、动态字段值结构、逐模块 DTO/VO 最低粒度索引、关键 VO 字段和各模块关键入参/出参；后端可据此创建 Controller、BO/DTO、VO，后续只读扩展字段不改变契约语义。 | 无。 |
| BAPI-003 | 平台账号/角色和系统字典接口缺失。 | closed | `docs/api.md` 已补 PLAT-013 至 PLAT-020，覆盖账号详情、编辑、重置密码、授权、平台角色创建/编辑/启停、权限目录；已补 DICT-001 至 DICT-009，覆盖字典类型和字典项 CRUD、启停、引用影响。 | 无。 |
| BAPI-004 | 状态流转、错误码触发条件、提示和可重试性不足。 | closed | `docs/api.md` 已补核心对象状态流转矩阵、错误码结构字段和核心错误码断言表；各模块保留模块错误码清单，后端可按统一结构维护枚举和异常映射。 | 无。 |
| BAPI-005 | 权限上下文、租户一致性、RBAC 数据范围结构不足。 | closed | `docs/api.md` 已定义 RequestContext 来源、`systemId`/`tenantId`/`memberId`/`clientId` 校验顺序、`DataScopeRuleDTO`、`EffectivePermissionVO`、字段权限、显式禁用和 RBAC 授权补充接口。 | 无。 |
| BAPI-006 | 运行记录保存和提交审批语义冲突，幂等和乐观锁不足。 | closed | `docs/api.md` 已冻结 RUN-004/RUN-006 只保存，RUN-008 提交审批；`RUN-008` 成功进入 `IN_APPROVAL`，未绑定流程按规则返回错误或进入 `SUBMITTED`；运行写接口要求幂等键和 `recordVersion`。 | 无。 |
| BAPI-007 | 附件引用、绑定/解绑、回滚补偿、导出结果文件权限不足。 | closed | `docs/api.md` 已定义 TEMP/REFERENCED 文件状态、`FileBindDTO[]` 附件值、记录保存事务内绑定/解绑、业务失败按临时文件过期清理、导出结果文件下载复用 FILE-005 并追加权限复核。 | 无。 |
| BAPI-008 | 流程工作台缺抄送、我的申请、领取/取消领取、退回策略和并发字段。 | closed | `docs/api.md` 已补 FLOW-013 至 FLOW-021；`FlowActionBO` 包含 `targetNodeId`、`returnStrategy`、`taskVersion`、`recordVersion`、`idempotencyKey`；任务处理事务边界和状态联动已明确。 | 无。 |
| BAPI-009 | OpenAPI 签名、canonical string、body hash、nonce、幂等、scope 结构不足。 | closed | `docs/api.md` 已冻结 HMAC-SHA256、规范化字符串、body SHA256、时间窗口、nonce TTL、IP 白名单、限流维度、OpenAPI 幂等策略、模块上下文和字段级 scope 结构。 | 无。 |
| BAPI-010 | 导出任务领取、筛选与选中优先级、状态流转、失败原因和结果文件权限不足。 | closed | `docs/api.md` 已定义导出任务状态流转、创建入参、筛选/权限/脱敏快照、`selectedRecordIds` 优先级、runner 内部 service 边界、原子领取、失败原因结构、结果文件下载权限。 | 无。 |

## api_review 中标记 backend 复核的关联项

| issueId | 复核结论 | 依据 |
| --- | --- | --- |
| DBA-API-002 | closed | 幂等 scope、requestHash、resultSnapshot、TTL、处理中/冲突响应和日志落点已补，内部 API 与 OpenAPI 可共用后端幂等服务模型。 |
| FEAPI-003 | closed | 平台账号和角色补充接口已覆盖前后端闭环，不需要后端新增隐藏接口。 |
| FEAPI-004 | closed | RBAC-012、RBAC-013 已补授权详情和权限目录，后端可实现授权页读取与保存闭环。 |
| FEAPI-009 | closed | OpenAPI scope catalog、凭证一次性返回、调用日志字段和 OPM-009 已补。 |
| FEAPI-011 | closed | 必须幂等接口清单、重复回放、冲突和处理中语义已补。 |
| TAPI-004 | closed | 幂等测试可基于 scope、TTL、hash、快照、冲突和处理中响应断言。 |
| TAPI-005 | closed | OpenAPI 签名、时间窗口、nonce、限流维度和响应错误码已可构造测试用例。 |
| TAPI-006 | closed | DICT 接口组已纳入 MVP，后端可准备字典配置与引用影响测试。 |

## 剩余阻塞 issue

| issueId | 阻塞级别 | 阻塞原因 | 需要修改 |
| --- | --- | --- | --- |
| BAPI-001 | 阻塞 API 冻结/任务拆分 | 上游 `docs/project_understanding.md` 与当前 API 阶段状态互相否定，API/review/state 的说明不能替代项目理解冻结产物本身。 | PM 退回项目理解模式修订 `docs/project_understanding.md`，再回到 API 契约闭环确认。 |

## backend 角度结论

- 是否允许 API 冻结：不允许。第 1 轮仍有 BAPI-001 未关闭。
- 是否允许进入任务拆分：不允许。任务拆分会把冲突的 `project_understanding.md` 和 `api.md` 同时作为输入，后端实现依据不一致。
- 后端实现可行性：除 BAPI-001 的上游状态冲突外，BAPI-002 至 BAPI-010 已具备后端落地所需的接口语义、BO/DTO/VO 粒度、权限上下文、事务边界、幂等、附件、流程、导出和 OpenAPI 规则。
