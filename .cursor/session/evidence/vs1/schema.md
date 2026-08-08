# VS1 Schema 与迁移证据

- checked_at: `2026-07-10T22:46:04+08:00`
- verdict: `pass`
- migration: `sql/migration/V1_0_0__vs1_identity_context.sql`

## 检查

1. 在空的 `examine2_regen_test` schema 直接执行最终 SQL：成功。
2. 读回业务表 `16`、索引记录 `104`、CHECK 约束 `25`。
3. Testcontainers MySQL `8.0.44` 由 Flyway 从空库迁移到 `1.0.0`：成功。
4. 最终 jar 在清空的 `examine2_app_test` 启动，Flyway history 为 `1.0.0 / success=1 / checksum=563345225`，业务表 `16`。
5. 应用重启后健康检查返回 `UP`，随后真实浏览器旅程通过。

## 边界

本证据证明 VS1 schema 可空装、由 Flyway 执行并支持当前旅程；不声明 VS2-VS12 的表已经实现，也不替代未来版本升级/回滚演练。
