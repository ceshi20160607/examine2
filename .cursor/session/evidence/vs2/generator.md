# VS2 MyBatis-Plus Generator 证据

- checked_at: `2026-07-11T09:44:00+08:00`
- schema: `examine2_vs2_clean`
- verdict: `pass`

## 删除后重生成

受影响的 generated base 目录先验证绝对路径位于对应 module 内，再删除并从最终 V2 schema 执行生成器。业务 `manage` 层未纳入删除范围，generated 文件未手改。

| module/prefix | tables | generated files |
|---|---:|---:|
| `examine-plat / un_plat_` | 25 | 125 |
| `examine-core / un_sys_` | 4 | 20 |
| `examine-core / un_audit_` | 2 | 10 |
| 合计 | 31 | 155 |

- generated controller: `0`
- generated entity `toString`: `0`
- `DepartmentClosure` 正确生成可写 `tenantId`，不生成 `tenantKey/nodeLow/nodeHigh`。
- schema 中所有被忽略的 `tenant_key`、`authz_scope_key`、`target_tenant_key`、closure node 边界均确认是 `STORED GENERATED`。
- 报告：`backend/generator-reports/*.json`，每份只列本次 prefix 的实际文件。

重新生成后 `examine-core`、`examine-plat`、`examine-web` 已完成干净编译；HTTP 集成测试结论单独记录在 backend evidence，不由本文件代替。
