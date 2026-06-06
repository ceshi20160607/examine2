# DBA API 契约第 1 次闭环复核

## 复核结论：pass

第 1 轮 API 契约闭环修订后，`docs/api.md` 已补齐 DBA 原阻塞 issue 所需的落库口径：动态字段唯一性、幂等表设计关键维度、`un_module_`/`un_openapi_`/`un_app_` 表域边界均已明确。`docs/api_review.md` 中标记需 DBA 参与复核的 BAPI-007、TAPI-002 也已达到后续 DB 设计可继续的程度。

DBA 角度当前无剩余阻塞 issue。API 是否最终冻结仍需等待 backend、frontend、test 复核和 PM 最终冻结结论。

## 复核范围

- `docs/prd.md`
- `docs/project_understanding.md`
- `docs/api.md`
- `docs/api_review.md`
- `docs/understanding/dba_api_review.md`
- `docs/legacy_reference.md`
- `docs/service_info.md`
- `.codex/state.json`

本次未读取 `.codex/oldexamine/`，未写 SQL，未修改 `docs/db_design.md`、`docs/api.md`、backend、frontend 或 sql 目录。

`.codex/state.json` 显示当前处于 step 13，`api_review_loops=1`，`api_frozen=false`；因此本文件仅为 DBA 对第 1 次 API 契约闭环修订的复核结论。

## issue 关闭复核表

| issueId | 原问题 | 复核结论 | 依据 | 如仍 open 的修改建议 |
| --- | --- | --- | --- | --- |
| DBA-API-001 | 动态字段唯一性作用域、软删除复用、空值、字段类型、历史冲突未冻结。 | closed | `docs/api.md` 已在“动态字段唯一性规则”中明确字段级唯一作用域为 `systemId + tenantId + moduleId + fieldId + typedValueHash`，组合唯一作用域为 `systemId + tenantId + moduleId + constraintCode + combinedTypedValueHash`；同时明确非 `DELETED` 记录约束、软删除后允许复用、恢复需重校验、`null`/空字符串/空数组跳过唯一校验、支持/不支持唯一的字段类型、发布或启用唯一时扫描历史非删除记录并返回 `FIELD_UNIQUE_EXISTING_CONFLICT`。这些规则足以支撑后续唯一索引、逻辑删除标记、组合唯一索引和历史冲突校验设计。 | 无。 |
| DBA-API-002 | 幂等键作用域、请求摘要、结果快照、TTL、并发锁定、冲突语义未冻结。 | closed | `docs/api.md` 已在“幂等策略”中列出必须幂等的内部 API 与 OpenAPI 清单，冻结内部 API scope 为 `INTERNAL:{accountId}:{systemId}:{tenantId}:{apiId}:{bizAction}:{idempotencyKey}`，OpenAPI scope 为 `OPENAPI:{clientId}:{systemId}:{tenantId}:{apiId}:{bizAction}:{idempotencyKey}`；同时明确 `requestHash` 计算、`resultSnapshot` 字段、默认 TTL、导出/流程特殊 TTL、同 key 同 hash 回放、同 key 不同 hash 返回 409、处理中返回 423、日志落点和过期清理规则。DB 阶段可据此设计幂等表唯一键、请求摘要、快照字段、状态字段和清理索引。 | 无。 |
| DBA-API-003 | `un_app_` 与 `un_module_` 应用/版本表域边界不清。 | closed | `docs/api.md` 已在“应用/版本表域边界”“应用配置”和“数据落点和事务边界汇总”中明确：MVP 中业务应用、应用版本、模块、字段、页面、菜单、配置版本、动态记录、导出模板和导出任务统一归 `un_module_`；OpenAPI 客户端、凭证、scope、IP、限流、幂等、日志统一归 `un_openapi_`；`un_app_` 仅作为旧项目 OpenAPI 历史表域参考，MVP 不新建该前缀表。该口径与 `docs/prd.md`、`docs/legacy_reference.md` 中旧 `un_app_` 迁移为 `un_openapi_` 的说明一致。 | 无。 |
| BAPI-007 | 附件引用、绑定/解绑、回滚补偿、导出结果文件权限不足，且 `api_review.md` 标记需 DBA 参与复核。 | closed | `docs/api.md` 已补充 `FileBindDTO[]`、`fileStatus` 为 `TEMP`/`REFERENCED`/`DELETED`/`EXPIRED`、业务保存事务内计算新增绑定和解绑、业务事务失败时文件保持 `TEMP` 并按过期清理、已被其它对象引用的文件不得删除；导出结果文件下载复用文件下载接口并额外校验导出任务创建人、权限快照或审计只读权限。数据落点汇总也明确文件元数据、存储对象、引用关系、导出结果文件和任务日志。DB 阶段可据此设计文件主表、文件引用表、引用类型、引用计数/状态和导出结果文件关联。 | 无。 |
| TAPI-002 | 平台初始化和最小测试数据边界不足，且 `api_review.md` 标记需 test/DBA 复核。 | closed | `docs/api.md` 已在“初始化与最小测试数据边界”中明确生产 `init.sql` 只包含平台默认管理员、平台角色、平台菜单、平台配置、字段类型元数据等建库 seed；默认管理员登录名为 `platform_admin`，密码不得在 API 中明文出现；`PLAT-002` 创建系统需在单事务内初始化默认租户、创建人成员扩展、`SYS_SUPER_ADMIN`、系统默认菜单/权限、默认应用和字段类型引用，并返回 `initializedObjects` 供测试断言；测试样例数据不进入生产 seed，应通过公开 API 或后续测试夹具准备。该边界不再阻塞 DB 初始化设计。 | 无。 |

## 剩余阻塞 issue

DBA 角度无剩余阻塞 issue。

未扩展新的 DBA issue。`docs/project_understanding.md` 中保留的早期阶段性文字不影响本次 DBA API 复核；当前轮次以 `.codex/state.json`、`docs/api.md` 和 `docs/api_review.md` 的第 1 次 API 闭环状态为准。

## 是否允许 API 冻结/是否允许进入任务拆分

DBA 角度允许 API 冻结。

DBA 角度允许在 PM 汇总 backend、frontend、test 复核均通过后进入任务拆分。进入 DB 设计阶段时，可基于当前 API、PRD、旧项目摘要和服务信息继续产出 `docs/db_design.md` 与 `sql/init.sql`。
