# 平台级权限：表名与用途

平台控制台采用 **RBAC**：**角色（role）**、**菜单/权限项（menu，含 `perm_code`）**、**角色-菜单**、**账号-角色**。与**自建系统内** `un_module_*` 权限无关。

| 表名 | 用途 |
|------|------|
| **`un_plat_menu`** | 菜单树与权限项：`perm_code` 与 `PlatPermCodes` 及接口校验一致；目录行可为空。 |
| **`un_plat_role`** | 平台角色（如 `plat_super_admin`、`plat_user`）。 |
| **`un_plat_role_menu`** | 角色拥有的菜单（含隐含的权限码集合）。 |
| **`un_plat_account_role`** | 平台账号与角色的多对多绑定；**有任一行则走 RBAC**，否则回退 `un_plat_account.plat_perm_codes`。 |
| **`un_plat_account`** | 账号列 `can_create_system`、`plat_perm_codes` 仍可用于兼容旧数据；`/permissions/me` 中 `canCreateSystem` 会综合 **有效权限含 `SYSTEM_CREATE`** 与 `can_create_system`。 |

脚本：`docs/sql/12_plat_rbac_ddl.sql`（建表）、`13_plat_rbac_seed.sql`（内置角色与菜单种子）、`14_plat_rbac_backfill_account_role.sql`（可选，老账号补角色）。

**代码约定**：模板生成的基础 CRUD 仍在 **`com.unique.examine.plat`**（如 `entity.po`、`mapper`、`service`）。手写编排与 RBAC 等为 **`plat.manage`**（直接 **`@Service` 类**，无单独 `impl` 包）；聚合/复杂查询 Mapper 与生成物统一在 **`plat.mapper`**（可增 XML）；DTO 为 **`plat.entity.dto`**。不单独拆 Maven 模块。全项目统一写法见 **[backend-java-conventions.md](backend-java-conventions.md)**。
