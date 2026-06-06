# unexamine API 契约最终冻结结论

## API 审查结论

本文件为 PM 在 API 契约冻结阶段输出的最终结论。冻结版 API 的数据源为：

- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- 四角色复核报告：`docs/understanding/dba_api_rereview.md`、`docs/understanding/backend_api_rereview_2.md`、`docs/understanding/frontend_api_rereview.md`、`docs/understanding/test_api_rereview_2.md`

API 审查循环使用 `2/3` 次。第 1 轮关闭 DBA、frontend 和大部分 backend/test issue；第 2 轮关闭剩余 `BAPI-001`、`TAPI-005`、`TAPI-006`。截至本结论，DBA、backend、frontend、test 均为 `pass`，全部 32 个 API 审查 issue 均为 `closed`，无剩余阻塞 issue。

| 角色 | 最终结论 | 关闭范围 | PM 结论 |
| --- | --- | --- | --- |
| DBA | pass | `DBA-API-001` 至 `DBA-API-003`，以及需 DBA 参与复核的文件引用、初始化边界项已关闭。 | 数据落点、表域、幂等、动态字段唯一性和初始化边界不再阻塞 API 冻结。 |
| backend | pass | `BAPI-001` 至 `BAPI-010` 均关闭。 | 接口边界、BO/DTO/VO、权限上下文、幂等、事务、流程、文件、导出、OpenAPI 和字典契约可进入任务拆分。 |
| frontend | pass | `FEAPI-001` 至 `FEAPI-011` 均关闭。 | API 足以支撑 typed SDK、页面到接口映射、权限禁用态、动态 schema、运行台、流程、文件导出、OpenAPI、审计运维页面任务拆分。 |
| test | pass | `TAPI-001` 至 `TAPI-008` 均关闭。 | 正常、异常、权限、边界、幂等/并发、OpenAPI 签名限流、字典配置和 E2E 前置数据均已有可测试契约。 |

## PM 最终决策

PM 最终决策：通过 API 契约冻结。

冻结版本号：`api-frozen-v1-2026-06-06`。

冻结时间：`2026-06-06`。

是否允许进入任务拆分阶段：允许。下一阶段只能产出 `docs/task_plan.md` 和 `docs/tasks/`，并基于冻结版 `docs/api.md` 拆分 DBA、backend、frontend、test、validator、reviewer 任务。

阶段限制：API 冻结不等于允许直接进入 DB/SQL/代码实现。当前仍不允许创建或修改 `docs/db_design.md`、`sql/init.sql`、`backend/`、`frontend/` 等实现产物；必须先完成任务拆分并形成 `docs/task_plan.md` 与 `docs/tasks/`。

## 冻结后变更规则

冻结后，后续任何接口、字段、枚举、错误码、状态、权限语义、鉴权方式、幂等策略、事务边界、前后端字段映射或测试断言变化，均必须重新打开 API 契约评审。

backend、frontend、DBA、test 在任务拆分或后续实现中如发现契约无法落地，不得私自修改冻结版接口主体；必须登记契约变更问题，由 PM 组织 API 契约复审后再决定通过、退回 PRD、退回需求分析或退回 API 修改。

## Issue 关闭台账

| issueId | 提出角色 | 处理轮次 | 当前状态 | 复核结论 |
| --- | --- | --- | --- | --- |
| DBA-API-001 | DBA | 1/3 | closed | 动态字段唯一性作用域、软删除复用、空值、字段类型和历史冲突规则已可支撑 DB 设计。 |
| DBA-API-002 | DBA | 1/3 | closed | 幂等键作用域、请求摘要、结果快照、TTL、并发锁定和冲突语义已冻结。 |
| DBA-API-003 | DBA | 1/3 | closed | `un_module_`、`un_openapi_`、历史 `un_app_` 表域边界已明确。 |
| BAPI-001 | backend | 2/3 | closed | `docs/project_understanding.md` 历史状态与当前 API 阶段冲突已消除。 |
| BAPI-002 | backend | 1/3 | closed | BO/DTO/VO、校验、来源、前端可写性、分页筛选已有契约索引。 |
| BAPI-003 | backend | 1/3 | closed | 平台账号、平台角色和系统字典接口已补齐。 |
| BAPI-004 | backend | 1/3 | closed | 状态流转、错误码触发条件、前端提示和可重试语义已可断言。 |
| BAPI-005 | backend | 1/3 | closed | 权限上下文、租户一致性、RBAC 数据范围结构已冻结。 |
| BAPI-006 | backend | 1/3 | closed | 运行记录保存、编辑、提交审批语义及幂等/乐观锁边界已明确。 |
| BAPI-007 | backend | 1/3 | closed | 附件引用、绑定/解绑、失败补偿和导出结果文件权限已明确。 |
| BAPI-008 | backend | 1/3 | closed | 流程工作台抄送、我的申请、领取/取消领取、退回策略和并发字段已补齐。 |
| BAPI-009 | backend | 1/3 | closed | OpenAPI 签名、canonical request、body hash、nonce、幂等和 scope 结构已冻结。 |
| BAPI-010 | backend | 1/3 | closed | 导出任务领取、筛选快照、状态流转、失败原因和结果文件权限已明确。 |
| FEAPI-001 | frontend | 1/3 | closed | 统一响应、分页、筛选、错误结构和 DTO/VO 索引已支撑 typed SDK。 |
| FEAPI-002 | frontend | 1/3 | closed | 权限提示、按钮禁用态和字段权限模型已明确。 |
| FEAPI-003 | frontend | 1/3 | closed | 平台账号和平台角色页面接口已补齐。 |
| FEAPI-004 | frontend | 1/3 | closed | RBAC 授权详情和可授权目录接口已补齐。 |
| FEAPI-005 | frontend | 1/3 | closed | 动态字段、页面配置、运行 schema 结构已冻结。 |
| FEAPI-006 | frontend | 1/3 | closed | 运行台查询、详情、字段错误和关联数据结构已补齐。 |
| FEAPI-007 | frontend | 1/3 | closed | 流程工作台和流程模板接口已支撑页面闭环。 |
| FEAPI-008 | frontend | 1/3 | closed | 文件中心和导出任务字段、轮询和禁用原因已补齐。 |
| FEAPI-009 | frontend | 1/3 | closed | OpenAPI scope catalog、凭证展示状态和调用日志字段已补齐。 |
| FEAPI-010 | frontend | 1/3 | closed | 审计/运维日志详情和健康检查结构已补齐。 |
| FEAPI-011 | frontend | 1/3 | closed | 幂等/防重复提交接口清单和冲突响应已明确。 |
| TAPI-001 | test | 1/3 | closed | 错误码、HTTP 状态、触发条件、重试、`errors[]` 和字段定位已可测试。 |
| TAPI-002 | test | 1/3 | closed | 平台初始化、生产 seed 和最小测试数据边界已明确。 |
| TAPI-003 | test | 1/3 | closed | 状态流转矩阵和状态冲突错误码已可断言。 |
| TAPI-004 | test | 1/3 | closed | 幂等 scope、TTL、hash、回放、冲突和处理中语义已可测试。 |
| TAPI-005 | test | 2/3 | closed | OpenAPI 签名、canonical query/body/header、HMAC 小写 hex、timestamp/nonce、限流响应和测试样例已可复现。 |
| TAPI-006 | test | 2/3 | closed | 字典类型/字典项 DTO/VO、唯一性、状态、层级、引用限制、缓存刷新和错误码已可测试。 |
| TAPI-007 | test | 1/3 | closed | 审计字段已能断言业务变更、幂等结果和 OpenAPI 安全结果。 |
| TAPI-008 | test | 1/3 | closed | 流程工作台我的申请、抄送、实例和历史入口已可覆盖测试。 |

## 是否允许进入任务拆分阶段

允许进入任务拆分阶段。

任务拆分阶段必须以冻结版 `docs/api.md`、本文件、`docs/prd.md`、`docs/project_understanding.md` 和 `docs/service_info.md` 为输入，先产出 `docs/task_plan.md` 与 `docs/tasks/`。在任务拆分产物完成并通过后，才允许按任务依赖进入 DB 设计、SQL、后端、前端、测试、验证和审查阶段。
