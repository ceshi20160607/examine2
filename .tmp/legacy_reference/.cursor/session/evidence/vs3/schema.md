# VS3 Schema 验证证据

- checked_at: `2026-07-15T11:25:30+08:00`
- verdict: `pass`
- migrations: `sql/migration/V3_0_0__vs3_config_publish.sql`, `sql/migration/V3_1_0__vs3_reference_integrity.sql`, `sql/migration/V3_2_0__vs3_permission_reference_scope.sql`

## 结果

- V1→V2→V3.2 在 MySQL `8.0.44` 和 `8.4.10` 空库均成功：应用表 `48`（不含 Flyway history），`un_module_*` 表 `17`，成功 migration `5`。
- MySQL 8.4 实际 schema 共 `105` 个外键、`157` 个 CHECK、`86` 个 UNIQUE 和 `40` 个真实生成列。
- 规范化共享草稿、配置引用、检查问题、不可变版本、发布记录和 active root pointer 分表保存；运行 snapshot 限制 `2 MiB`。
- 布尔列使用 `BOOLEAN`，MySQL 8.0 Flyway 不再产生显式 `TINYINT(1)` display width 弃用告警。

## 负约束

- system `102` 的模块引用 system `101` 的模块组：FK 拒绝。
- 同 system 不同 dictionary 的 closure path：组合 FK 拒绝。
- 同模块同类型第二个 active default page：生成键唯一约束拒绝。
- 不符合稳定编码格式的模块组编码：CHECK 拒绝。
- 配置引用的 `source_kind/target_kind/reference_type` 非法组合或 owner 不一致：V3.1 CHECK/FK 拒绝。
- 配置引用指向另一 system 的平台权限：V3.2 `(system_id,target_permission_id)` 组合 FK 拒绝；被引用键为 `uk_plat_permission_system_id`。

## 回归

Java 21 `mvn.cmd test` 六模块 reactor 通过；现有 VS1/VS2 Testcontainers 在 Flyway 自动应用 V1/V2/V3.2 后全部通过，证明新增表和 mapper 装配未破坏既有身份、context 和组织权限旅程。
