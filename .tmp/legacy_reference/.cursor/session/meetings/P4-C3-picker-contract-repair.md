# MEETING-P4-C3-PICKER-CONTRACT-REPAIR：关系候选搜索合同修订

## 1. 身份

- issue_ids: `P4-C3-RELATION-PICKER-SERVER-SEARCH`
- organized_by: pm
- date: `2026-07-21`
- participants: `pm, product, architect, backend, frontend, test`
- absent_roles_and_reason: `uiux` 的既有选择器交互合同未变；`dba` 无新增表或迁移
- status: completed

## 2. 待解决问题

- 决策问题：RELATION 的目标显示字段可读但不一定是通用列表的 searchable/filterable 字段时，如何仍提供服务端分页、范围过滤的候选搜索。
- 当前阻断：前端复用目标模块 `records:query` 的 `q`；合法的目标 schema 没有 searchable 字段时返回 `QUERY_FIELD_UNAVAILABLE`，退回空查询或客户端过滤都会违反当前节点合同。
- 决策权边界：这是已接受 P4-C3 行为的跨层补全，不改变产品范围，不需要用户裁决。

## 3. 各角色独立意见

| role | evidence path | position | risks | recommendation |
|---|---|---|---|---|
| frontend | `frontend/tests/e2e/p4-c3.spec.ts` | 通用 query 无法表达 source relation field、display field 和候选权限 | 空结果不稳定且会诱发客户端过滤 | 使用 source module + relation field 的专用候选 API |
| backend | `backend/examine-module/.../RecordQueryCompiler.java` | `q` 只允许 active readable searchable fields，当前拒绝是正确行为 | 放宽通用 query 会改变所有列表的权限/索引语义 | 不修改通用 query；增加窄合同 |
| architect | `.cursor/session/current-node.md` §10/§12 | 候选搜索必须同时知道 source relation contract 与 target scope | 只按 target module 查询会丢失 display/filter 语义 | 候选 API 由 source relation field 解析目标与显示字段 |
| product/test | `.cursor/session/evidence/p4-c3/detail-mobile.png` 与失败截图 | 普通成员必须能按名称选择目标且不得看到技术 id | fixture 加 searchable 只能掩盖产品缺口 | 以无 searchable 目标字段作为正向回归向量 |

## 4. 分歧与取舍

| option | benefits | costs/risks | affected scope | acceptance impact |
|---|---|---|---|---|
| A：把目标显示字段强制改为 searchable | 实现最少 | 改写已冻结配置语义，旧发布版本失效 | 配置/迁移 | 拒绝 |
| B：通用 `records:query` 始终搜索 record title | 无新 endpoint | 忽略 relation displayField/filter，改变所有 query 语义 | P4-B3 + P4-C3 | 拒绝 |
| C：专用 relation candidates GET | source/target/field/scope 语义完整，前端不做过滤 | 增加一个窄读合同与验证 | P4-C3 backend/frontend | 采用 |

## 5. 决定

- decision: 增加 `GET /modules/{sourceModuleCode}/relations/{fieldCode}/candidates?q=&page=&size=`。服务端从 active source RELATION snapshot 解析 target module、display field 和 filter，在同一查询中应用 target module permission、tenant/system boundary、record scope、relation filter 与 query text，只返回 `targetRecordId,targetVersion,title` 的当前页。
- reason: 这是唯一不放宽通用 query、不要求配置 searchable、也不把过滤下放客户端的方案。
- authority: `architect + backend` 技术合同决定，`pm` 确认仍在 P4-C3 已接受范围内，`test` 独立复核。
- rejected_options: A、B，以及客户端拉全量后过滤。
- changed_contracts: `.cursor/session/rebuild/vs4-api-contract.schema.json`、runtime controller/service、frontend service/type/control；当前节点 delivers/doesNotDeliver 不变。

## 6. 行动与复核

| action | owner | output | due node | verifier | status |
|---|---|---|---|---|---|
| 冻结候选 API schema 与权限绑定 | architect/backend | VS4 API contract | P4-C3 | test | PASS |
| 实现 display/filter/scope 候选查询 | backend | runtime service/controller tests | P4-C3-02 repair | test | PASS |
| 选择器改用候选 API并覆盖桌面/移动 | frontend | component + Playwright | P4-C3-03 | test | PASS |

## 7. 升级

- unresolved: none
- leader_decision_needed: no；修复证据已纳入 P4-C3 节点 Gate 复核
- user_decision_needed: no
- pending_decision_path: none
