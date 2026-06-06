# DBA API 契约审查

## 审查结论：fail

`docs/api.md` 已覆盖平台账号、系统/租户、系统成员扩展、系统内 RBAC、动态模块、运行记录、流程、文件、导出、OpenAPI、审计日志等主要领域对象，也明确了大部分状态枚举、数据落点和事务边界。全文未发现 `un_platt_`，平台与 OpenAPI 表域方向基本符合 `un_plat_`、`un_openapi_` 要求。

但仍有 3 个会直接影响 DB 设计冻结的阻塞点：动态字段唯一性规则未冻结、幂等落库维度未冻结、`un_app_` 与 `un_module_` 的应用/版本表域边界在 API 中仍留给 DB 阶段确认。当前不建议进入任务拆分或 DB 设计。

## 审查范围

- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- `docs/legacy_reference.md`
- `docs/service_info.md`
- `.codex/state.json`

本次未读取 `.codex/oldexamine/`，旧项目信息仅依据 `docs/legacy_reference.md`。

## 阻塞 issue 表

| issueId | 问题 | 影响 | 建议责任方 | 建议修改点 | 是否阻塞 API 冻结 |
| --- | --- | --- | --- | --- | --- |
| DBA-API-001 | 动态字段 `unique` 仅说明“DB/API 后续细化软删除复用规则”，未冻结唯一性作用域、软删除后是否复用、空值是否参与唯一、哪些字段类型允许唯一、启用唯一时历史数据冲突如何处理。 | DBA 无法稳定设计 `un_module_` 动态值/索引表唯一约束；后续若业务口径变化，可能导致唯一索引重建或历史数据迁移。 | PM/API，DBA 复核 | 在 `模块建模` 的字段入参和 `运行填报` 的保存契约中补充唯一性规则：建议明确唯一维度为 `systemId + tenantId + moduleId + fieldId + typedValue`，说明是否只约束非 `DELETED` 记录，空值/空数组是否跳过唯一校验，不支持唯一的字段类型清单，以及创建/启用唯一字段时的冲突检查返回。 | 是 |
| DBA-API-002 | `idempotencyKey` 分散出现在运行记录、流程任务、导出任务和 OpenAPI，但未冻结幂等键作用域、请求摘要比对、结果快照、保留时间、并发锁定和冲突返回规则。 | 无法确定幂等表唯一键和索引维度，例如按 `accountId/clientId + systemId + tenantId + bizAction + idempotencyKey` 还是按接口路径全局唯一；高并发下可能重复创建记录、审批动作或导出任务。 | PM/API，backend 协同，DBA 复核 | 增加“幂等策略”小节，分别覆盖 RUN-004/RUN-006/RUN-008、FLOW-009/FLOW-010、EXP-004/EXP-007/EXP-008、OPN-003/OPN-004/OPN-005/OPN-006：冻结唯一维度、请求 hash、同 key 同参数返回既有结果、同 key 不同参数返回冲突、结果引用字段、过期/清理规则和失败重试边界。 | 是 |
| DBA-API-003 | API 中一处说明“业务应用和动态模块归入动态模块域”，但表前缀汇总又写“应用/版本独立域如 DB 阶段确认拆分则使用 `un_app_`”。`docs/prd.md` 和 `docs/legacy_reference.md` 已说明旧 `un_app_` 是 OpenAPI 历史域，新项目 OpenAPI 用 `un_openapi_`，因此当前 API 对 `un_app_` 是否在 MVP 建表不够冻结。 | 表名前缀和代码生成模块映射会产生歧义：应用、应用版本、配置版本到底落 `un_module_` 还是 `un_app_` 不明确，可能影响 `docs/db_design.md` 和 `sql/init.sql` 的表名一致性。 | PM/API，DBA 复核 | 在 `应用配置`、`模块建模` 和 `数据落点和事务边界汇总` 中明确 MVP 表域：若业务应用、模块、配置版本统一落 `un_module_`，则声明 `un_app_` 在 MVP 不建表或仅预留给未来客户端应用配置；若确需 `un_app_`，需列明哪些领域对象使用该前缀，并保证 OpenAPI 仍只使用 `un_openapi_`。 | 是 |

## 非阻塞跟踪项

| 项目 | 说明 | DB 阶段注意点 |
| --- | --- | --- |
| 初始化数据 | `docs/prd.md` 已明确建库 seed、创建系统事务内初始化、测试样例隔离；`docs/api.md` 也描述 PLAT-002 创建系统的事务初始化。 | DB 设计可依据 PRD 设计平台默认账号、平台角色、平台菜单、平台配置、字段类型元数据，以及创建系统时的默认租户、创建人成员、系统超级管理员角色、系统菜单/权限、默认应用入口。 |
| 权限快照 | 导出任务已要求保存筛选、字段权限和脱敏快照，但 API 未展开快照 JSON 结构。 | DB 阶段可先按 JSON 快照字段设计，至少保留操作者成员快照、角色/权限摘要、字段清单、数据范围、脱敏策略、requestId。 |
| 调用日志 | OpenAPI 调用日志、审计日志、请求日志、错误日志已有数据落点和查询入口。 | DB 阶段需区分 `un_openapi_` 调用日志与 `un_audit_`/`un_sys_` 审计日志，避免重复存储但保留 requestId 串联能力。 |
| 文件引用 | 文件上传和业务绑定边界明确，临时文件和已引用文件状态明确。 | DB 阶段需设计文件引用表，支持动态字段、导出结果、流程附件等引用类型，并保证已引用文件不物理删除。 |
| `project_understanding.md` 状态文字 | 该文件仍保留早期“当前不允许进入 API 契约阶段”的描述，而 `.codex/state.json` 显示已进入 step 9。 | 这不是 DBA API 内容阻塞点，但 PM 在后续 `docs/api_review.md` 中应说明以当前 state 与 API 草案为准，或刷新项目理解门禁描述。 |

## 进入下游结论

不允许进入任务拆分或 DB 设计。建议 PM/API 先修订 `docs/api.md`，关闭 DBA-API-001 至 DBA-API-003 后，由 DBA 复核。复核通过后，DB 阶段即可依据冻结 API、PRD 和旧项目参考继续设计 `docs/db_design.md` 与 `sql/init.sql`。
