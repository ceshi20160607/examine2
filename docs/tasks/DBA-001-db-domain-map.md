# DBA-001 表域与命名映射设计

- taskId: DBA-001
- 标题: 表域与命名映射设计
- 负责角色: dba
- 所属大任务/模块: DB 设计
- 目标: 建立业务域、表前缀、后端模块和 API 数据落点之间的映射。
- 输入文件: `docs/prd.md`、`docs/project_understanding.md`、`docs/api.md`、`docs/api_review.md`、`docs/service_info.md`、`docs/legacy_reference.md`
- 输出文件或输出目录: `docs/db_design_parts/domain-map.md`

## 详细工作内容

- 按 `un_plat_`、`un_module_`、`un_flow_`、`un_upload_`、`un_openapi_`、`un_sys_`/`un_audit_` 梳理表域。
- 明确 `examine-plat`、`examine-module`、`examine-flow`、`examine-upload`、`examine-app` 与表域映射。
- 标记 `un_app_` 只作为旧项目历史参考，MVP 不新建该前缀表。

## 完成状态定义

- 默认状态: pending。
- 完成条件: `docs/db_design_parts/domain-map.md` 中有表与功能映射、模块表前缀与命名规则，并明确后续分片设计的归属边界。

## 验收标准

- API 的数据落点均能映射到明确表域。
- 不出现 `un_platt_`；不把 OpenAPI 表错归到 `un_app_`，OpenAPI 表必须使用 `un_openapi_`。

## 测试/自检要求

- 自检表前缀与冻结 API 的数据落点汇总一致。
- 标记后续 DBA 任务需要补充的字段级设计点。

## 依赖任务

- PLAN-001

## 可并行关系

- 不可并行；其它 DBA 表设计任务依赖本映射。

## 不允许事项

- 不生成 `sql/init.sql`。
- 不直接读取 `.codex/oldexamine/`，只能使用 `docs/legacy_reference.md` 中的摘要。
