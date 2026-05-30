# Flyway 与「已手工执行过 SQL」的库

## 默认策略（Spring `application.yml` / `application-prod.yml`）

- **`spring.flyway.enabled: true`**
- **`baseline-on-migrate: true`**：当库**已有对象**且**尚无** `flyway_schema_history` 时，会先打 baseline，再往后续版本迁移。
- **`baseline-version`**：默认 **`22`**（可用环境变量 `EXAMINE_FLYWAY_BASELINE_VERSION` 覆盖）。

含义：**版本号 ≤ baseline 的脚本不会在本库再执行**；未执行的更高版本（如 **`V23`**）会在启动时自动跑。

适合你当前情况：表结构 / 种子已通过 `docs/sql` 或历史手工跑完，等价于已到 **V22**，只差 **typed 列** 等增量。

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `EXAMINE_FLYWAY_BASELINE_VERSION` | `22` | 调高则多跑低级脚本（慎用，可能与已存在对象冲突）；调低只会多执行更高版本之上的迁移，一般不动 |
| `EXAMINE_FLYWAY_VALIDATE_ON_MIGRATE` | `true` | 若某版本脚本文本改过导致 `Validate failed`，可临时设为 `false` 启动，或对应用 **`flyway repair`** 修正校验和 |

示例（PowerShell）：

```powershell
$env:EXAMINE_FLYWAY_BASELINE_VERSION = "22"
$env:EXAMINE_FLYWAY_VALIDATE_ON_MIGRATE = "true"
```

若你希望由 Flyway **补跑 V22**（超管种子），可把 baseline 降为 **`21`**（仅在没有冲突、且能接受重复执行语义时）。

## 已存在 `flyway_schema_history` 时

Baseline **不会重复执行**。Flyway 只跑 **success 表中尚未成功**的版本。此时不必改 `baseline-version`，除非你改用新库重装。

## 校验失败 / failed migration（success=0）

**现象**：启动报错 `Detected failed migration to version N` 或 `Validate failed`。

**推荐顺序**：

1. **dev 默认**已开 `repair-on-migrate=true`（`EXAMINE_FLYWAY_REPAIR_ON_MIGRATE`），会先清理失败记录再迁移。
2. 仍失败时执行手工 SQL：`docs/sql/manual/flyway_repair_failed_migration.sql`  
   或 Windows 显式指定目标库后运行：

```powershell
$env:JDBC_URL = "jdbc:mysql://你的数据库:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
$env:DB_USER = "root"
$env:DB_PASS = "你的密码"
.\scripts\db\repair-flyway-failed.ps1
```
3. **V14**（`ref_model_id` / `ref_display_field`）脚本已改为**幂等**。**列已手工执行**时：跑 `docs/sql/manual/flyway_repair_v14_already_applied.sql` 清失败记录即可，**不要重复 ALTER**；重启后 Flyway 仅登记 V14 成功并继续更高版本。
4. 改过已成功版本的脚本文本导致 checksum 不一致：对目标库 `flyway repair`，或短时 `EXAMINE_FLYWAY_VALIDATE_ON_MIGRATE=false` 启动后再改回 `true`。

## 与本仓库手工脚本的关系

- 增量 DDL 仍可放在 `docs/sql/` 备忘；线上以 **classpath `db/migration`** 为准时，应保持与 Flyway 版本一致。
- **`docs/sql/17_module_record_data_typed_alter.sql`** 与 **`V23`** 内容对应；启用 Flyway 且 baseline≥22 时由 **V23** 自动执行即可，无需再手工跑 17（除非关闭了 Flyway）。
