# DBA issue 复核

## 复核结论

**结论：pass**

本次仅复核 `DBA-PRD-001 / PU-018` 与 `DBA-PRD-002 / PU-017` 是否关闭，不做全量 DBA 新问题扩展。基于当前允许读取的文档，两项原阻塞 issue 均已补齐到 DBA 可继续下游判断的程度。

## 复核范围

- `docs/prd.md`
- `docs/legacy_reference.md`
- `docs/requirement_analysis.md`
- `docs/project_understanding.md`
- `docs/understanding/dba_prd_review.md`
- `docs/service_info.md`

未读取旧项目目录，未生成 SQL，未修改代码或其它文档。

## issue 关闭复核

| issueId | 关联 PU | 复核结论 | 关闭依据 |
| --- | --- | --- | --- |
| DBA-PRD-001 | PU-018 | closed / pass | `docs/legacy_reference.md` 已新增 “DBA 可直接使用的旧表参考边界（PU-018 补充）”，并按模块列出 `可参考旧表`、`需重命名旧表`、`禁止直接沿用` 和 DBA 注意事项。OpenAPI 历史 `un_app_*` 已明确迁移为 `un_openapi_*`，并列出 `un_app_client`、`un_app_client_credential`、`un_app_client_scope`、`un_app_ip_whitelist`、`un_app_access_log` 到新表的映射与风险。flow 已明确 V7 DDL 确认 21 张表可参考，额外 9 张未建表实体禁止直接纳入新 DB 设计，且禁止从 `examine-flow/entity/po` 全量反推建表。 |
| DBA-PRD-002 | PU-017 | closed / pass | `docs/prd.md` 已新增 “初始化数据边界”，明确生产 `init.sql` 建库 seed、创建自定义系统事务内初始化、测试样例数据隔离，以及 seed 编码和归属统一规则。默认平台管理员、平台角色、平台菜单、平台配置、默认字段类型元数据、默认租户、创建人成员扩展、系统超级管理员角色、系统默认菜单/权限、默认应用或默认应用入口、字段类型引用均给出名称、编码、状态、归属和唯一规则。 |

## 关键复核点

1. 旧表分类：已满足。`legacy_reference` 已提供 DBA 可用的旧表分类边界，可区分可参考、需重命名、禁止直接沿用。
2. `un_app_*` 到 `un_openapi_*`：已满足。映射、历史来源和新库禁止继续创建 OpenAPI 用途 `un_app_*` 表的风险已明确。
3. flow DDL 与实体不一致：已满足。已明确 V7/V26 来源、可参考 flow 表边界，以及未被 Flyway DDL 确认的实体表名禁止直接沿用。
4. 初始化数据：已满足。PRD 已明确建库 seed、创建系统事务内初始化、测试样例隔离，以及默认账号、角色、菜单、字段类型、系统超级管理员角色、默认租户、默认应用的名称、编码、状态、归属和唯一规则。

## 剩余 issue

本次限定复核范围内无剩余阻塞 issue。

`docs/project_understanding.md` 和 `docs/prd.md` 中仍保留早期 “PU-018 待 analyst 补充 / 阻塞 DB 设计” 的历史描述，但当前 `docs/legacy_reference.md` 已补齐对应关闭条件；该状态同步应由后续 PM 汇总更新，不构成本次 DBA 复核范围内的硬阻塞。

## 阶段结论

从 DBA 复核角度，`DBA-PRD-001 / PU-018` 与 `DBA-PRD-002 / PU-017` 已关闭，允许进入 API 契约阶段。

仍不允许直接进入 DB 设计或 SQL 生成：数据库设计应等待冻结版 `docs/api.md`、API 契约评审结论和 `docs/task_plan.md` 完成后再开始。
