# SQL 初始化脚本（docs/sql）

本目录只放**可直接执行**的初始化脚本，按“一个域 = 一份 DDL + 一份 seed”的方式维护。

---

## 1. 执行顺序（推荐）

> 建议先建库（utf8mb4），然后按下列顺序执行。脚本均为 MySQL 方言。

### 1.1 DDL（建表）

1. `01_platform_ddl.sql`（平台域：账号/系统/租户/日志/消息）
2. `12_plat_rbac_ddl.sql`（平台控制台 RBAC：角色/菜单/绑定）
3. `03_upload_ddl.sql`（上传域）
4. `05_module_ddl.sql`（module：无代码元数据/权限/业务表壳）
5. `07_flow_ddl.sql`（flow：流程模板/实例/任务）
6. `09_app_ddl.sql`（app：对外 client/credential/scope 等）

### 1.2 Seed（占位/演示数据）

1. `02_platform_seed.sql`（systemId=0、tenantId=0 占位）
2. `13_plat_rbac_seed.sql`（平台 RBAC 角色/菜单种子）
3. `04_upload_seed.sql`（upload 默认存储配置）
4. `06_module_seed.sql`（module 演示 app/page/model/field/role）
5. `08_flow_seed.sql`（flow 演示流程定义/版本）
6. `10_app_seed.sql`（app 演示 client/credential/scope，默认停用）

### 1.3 可选脚本

- `14_plat_rbac_backfill_account_role.sql`
  - 适用：**老库上线 RBAC** 后，为历史账号补 `un_plat_account_role` 绑定
  - 依赖：必须先执行 `12_plat_rbac_ddl.sql`、`13_plat_rbac_seed.sql`
- `15_module_record_data_eav_alter.sql`
  - 适用：曾用**旧版** `05_module_ddl.sql`（`un_module_record_data` 为单行 `data_json`）的库，需迁到 **EAV（`record_id` + `field_code`）**
  - **全新安装**直接使用当前 `05_module_ddl.sql` 即可，一般**不必**执行本脚本

---

## 2. 约定与注意事项

- **作用域列**：凡涉及业务隔离的表，使用 `system_id` / `tenant_id`；未开启多租户时 `tenant_id` 固定为 0。
- **占位数据**：`systemId=0` / `tenantId=0` 作为“平台态/默认租户”占位（见 `02_platform_seed.sql`）。
- **本地路径**：`04_upload_seed.sql` 的默认本地目录是 `D:\\data\\uploads`，按环境自行改。
- **重复执行**：
  - 大多数 seed 使用 `INSERT IGNORE` 或 `ON DUPLICATE KEY UPDATE`，可重复执行
  - 大多数 DDL 会 `DROP TABLE IF EXISTS`，重复执行会清空表（注意生产环境不要直接跑）
- **业务数据 EAV**：`un_module_record_data` 为**一行一个字段**（`field_code` + `value_text`），与 `un_module_field.field_code` 对齐；列表 DSL 过滤里对动态字段使用 **field_code**，不再使用 `data.*` JSON 路径。

