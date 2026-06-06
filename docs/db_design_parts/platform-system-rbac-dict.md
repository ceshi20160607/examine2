# DBA-002 平台、系统、RBAC、字典表设计分片

## 一、分片边界

本分片只覆盖 `DBA-002`：平台账号、系统、租户、平台 RBAC、系统成员扩展、系统内组织/RBAC、系统字典及其缓存版本和引用关系。

本分片不生成 SQL，不写 `docs/db_design.md`，不设计运行记录、应用版本、字段配置、流程、上传、OpenAPI 凭证、审计日志等其它分片表。OpenAPI scope 在本分片中只作为系统角色可授权对象边界记录，不重复设计 `un_openapi_` 客户端、凭证、白名单、幂等和调用日志表。

## 二、表与功能映射

| 表名 | 功能模块 | 描述 |
| --- | --- | --- |
| `un_plat_account` | 平台账号 | 全局登录主体，承载登录名、密码哈希、账号状态和安全字段。 |
| `un_plat_system` | 平台系统 | 自定义系统容器，承载系统编码、租户模式、创建人和系统状态。 |
| `un_plat_tenant` | 平台租户 | 系统下租户，单租户系统也必须有默认租户。 |
| `un_plat_role` | 平台 RBAC | 平台中心角色，控制平台账号、系统、配置和审计入口。 |
| `un_plat_menu` | 平台 RBAC | 平台中心菜单树。 |
| `un_plat_operation` | 平台 RBAC | 平台中心操作权限点。 |
| `un_plat_account_role` | 平台 RBAC | 平台账号与平台角色关联。 |
| `un_plat_role_menu` | 平台 RBAC | 平台角色与平台菜单关联。 |
| `un_plat_role_operation` | 平台 RBAC | 平台角色与平台操作权限关联。 |
| `un_plat_config` | 平台配置 | 密码策略、会话策略、默认文件存储、OpenAPI 全局策略、审计保留策略等全局配置。 |
| `un_module_member` | 系统成员 | 平台账号在某个系统中的成员扩展，不是独立登录账号。 |
| `un_module_member_tenant` | 系统成员 | 成员可访问租户集合，支持多租户切换和默认租户。 |
| `un_module_dept` | 系统组织 | 系统内部门树。 |
| `un_module_member_dept` | 系统组织 | 成员与部门多对多关联，支持主部门。 |
| `un_module_role` | 系统 RBAC | 系统内角色，含系统超级管理员等保护角色。 |
| `un_module_member_role` | 系统 RBAC | 成员与系统角色关联。 |
| `un_module_system_menu` | 系统 RBAC | 系统管理菜单和运行菜单授权目录。 |
| `un_module_system_operation` | 系统 RBAC | 系统内操作权限目录。 |
| `un_module_role_menu` | 系统 RBAC | 系统角色菜单授权。 |
| `un_module_role_operation` | 系统 RBAC | 系统角色操作授权。 |
| `un_module_role_field_permission` | 系统 RBAC | 系统角色字段可见、可写、导出明文、OpenAPI 读写授权。 |
| `un_module_role_data_scope` | 系统 RBAC | 系统角色数据范围规则。 |
| `un_module_role_openapi_scope` | 系统 RBAC | 系统角色可授权 OpenAPI scope 边界，引用 scope 编码，不保存凭证。 |
| `un_module_role_explicit_deny` | 系统 RBAC | 显式禁用权限项，优先级高于授权并集。 |
| `un_module_permission_version` | 系统 RBAC | 系统权限缓存版本，支撑 `EffectivePermissionVO.version`。 |
| `un_module_dict_type` | 系统字典 | 系统级或租户级字典类型。 |
| `un_module_dict_item` | 系统字典 | 字典项，支持层级树、内置只读和引用限制。 |
| `un_module_dict_reference` | 系统字典 | 字典被字段、发布版本、记录值引用的关系摘要，用于 `DICT-009` 和删除/停用判断。 |

## 三、命名与公共字段规则

### 3.1 表前缀

- `un_plat_`：平台账号、系统、租户、平台 RBAC、平台配置。
- `un_module_`：系统内成员扩展、组织、角色权限、字典。
- 禁止 `un_platt_`。
- 禁止用 `un_app_` 表达 OpenAPI 或业务应用；OpenAPI 其它对象由 DBA-004 使用 `un_openapi_` 设计。

### 3.2 公共字段

以下字段默认出现在本分片所有业务表中，后续 DBA-005 汇总时可统一展开到 `docs/db_design.md`。

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | 是 | 无 | 主键，雪花 ID 或等价全局 ID。 |
| `created_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) | 创建时间。 |
| `updated_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新时间。 |
| `created_by` | BIGINT | 否 | NULL | 创建人平台账号 ID；seed 或系统任务可为空。 |
| `updated_by` | BIGINT | 否 | NULL | 更新人平台账号 ID；seed 或系统任务可为空。 |
| `version` | INT | 是 | 0 | 乐观锁版本，状态变更、授权保存、字典修改必须校验。 |
| `deleted_flag` | TINYINT | 是 | 0 | 0-未删除，1-已删除。 |
| `delete_token` | BIGINT | 是 | 0 | 唯一索引软删复用判别值；未删除为 0，软删除时写入本行 ID 或删除时间戳。 |

MySQL 唯一索引不直接依赖 nullable 字段表达业务唯一性。根节点、系统级字典等 API 为空值的场景，DB 层使用 `0` 作为归一化 key，例如 `parent_id=0` 表示根节点，`scope_tenant_id=0` 表示系统级字典。

## 四、平台域表字段

### 4.1 `un_plat_account`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `login_name` | VARCHAR(64) | 是 | 无 | 登录名，全局唯一。 |
| `password_hash` | VARCHAR(255) | 是 | 无 | 密码哈希，不返回前端。 |
| `display_name` | VARCHAR(64) | 是 | 无 | 展示名称。 |
| `mobile` | VARCHAR(32) | 否 | NULL | 手机号。 |
| `email` | VARCHAR(128) | 否 | NULL | 邮箱。 |
| `status` | VARCHAR(32) | 是 | `NORMAL` | 账号状态：`NORMAL`、`DISABLED`、`LOCKED`。 |
| `first_login_change_pwd` | TINYINT | 是 | 0 | 是否首次登录必须改密。 |
| `failed_login_count` | INT | 是 | 0 | 连续登录失败次数。 |
| `locked_until` | DATETIME(3) | 否 | NULL | 锁定截止时间。 |
| `last_login_at` | DATETIME(3) | 否 | NULL | 最近登录时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_account_login_name(login_name, delete_token)`。
- 普通索引：`idx_plat_account_status(status)`、`idx_plat_account_mobile(mobile)`、`idx_plat_account_email(email)`。
- 逻辑外键：被 `un_module_member.account_id`、`un_plat_account_role.account_id` 引用。

### 4.2 `un_plat_system`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `code` | VARCHAR(64) | 是 | 无 | 系统编码，全局唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 系统名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 系统描述。 |
| `tenant_mode` | VARCHAR(16) | 是 | `SINGLE` | 租户模式：`SINGLE`、`MULTI`。 |
| `default_tenant_id` | BIGINT | 否 | NULL | 默认租户 ID，创建系统事务内回填。 |
| `owner_account_id` | BIGINT | 是 | 无 | 创建人平台账号 ID。 |
| `owner_member_id` | BIGINT | 否 | NULL | 创建人在系统内的成员扩展 ID，初始化完成后回填。 |
| `status` | VARCHAR(32) | 是 | `DRAFT` | 系统状态：`DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED`。 |
| `domain` | VARCHAR(128) | 否 | NULL | 可选系统访问域名或入口标识。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_system_code(code, delete_token)`。
- 普通索引：`idx_plat_system_owner(owner_account_id)`、`idx_plat_system_status(status)`、`idx_plat_system_default_tenant(default_tenant_id)`。
- 逻辑外键：`owner_account_id -> un_plat_account.id`，`default_tenant_id -> un_plat_tenant.id`，`owner_member_id -> un_module_member.id`。

### 4.3 `un_plat_tenant`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `code` | VARCHAR(64) | 是 | 无 | 租户编码，同系统唯一；默认租户为 `default`。 |
| `name` | VARCHAR(128) | 是 | 无 | 租户名称。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 租户状态：`ENABLED`、`DISABLED`。 |
| `description` | VARCHAR(512) | 否 | NULL | 租户描述。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_tenant_system_code(system_id, code, delete_token)`。
- 普通索引：`idx_plat_tenant_system_status(system_id, status)`。
- 逻辑外键：`system_id -> un_plat_system.id`。

### 4.4 `un_plat_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `code` | VARCHAR(64) | 是 | 无 | 平台角色编码，全局唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 平台角色名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 角色说明。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 角色状态：`ENABLED`、`DISABLED`。 |
| `protected_flag` | TINYINT | 是 | 0 | 是否保护角色；保护角色禁止删除和关键权限移除。 |
| `sort_order` | INT | 是 | 0 | 排序。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_role_code(code, delete_token)`。
- 普通索引：`idx_plat_role_status(status)`。

### 4.5 `un_plat_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `parent_id` | BIGINT | 是 | 0 | 父菜单 ID，0 表示根菜单。 |
| `code` | VARCHAR(64) | 是 | 无 | 菜单编码，同父级唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 菜单名称。 |
| `path` | VARCHAR(255) | 否 | NULL | 前端路由或入口路径。 |
| `icon` | VARCHAR(128) | 否 | NULL | 图标编码。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 菜单状态：`ENABLED`、`DISABLED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 菜单层级。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 菜单路径，用于树查询。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_menu_parent_code(parent_id, code, delete_token)`。
- 普通索引：`idx_plat_menu_parent(parent_id, sort_order)`、`idx_plat_menu_status(status)`。

### 4.6 `un_plat_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `menu_id` | BIGINT | 是 | 无 | 所属平台菜单。 |
| `code` | VARCHAR(64) | 是 | 无 | 操作权限编码，例如 `PLAT_ACCOUNT_CREATE`。 |
| `name` | VARCHAR(128) | 是 | 无 | 操作名称。 |
| `api_pattern` | VARCHAR(255) | 否 | NULL | 对应 API 路径模式。 |
| `method` | VARCHAR(16) | 否 | NULL | HTTP 方法。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_operation_code(code, delete_token)`。
- 普通索引：`idx_plat_operation_menu(menu_id)`。
- 逻辑外键：`menu_id -> un_plat_menu.id`。

### 4.7 `un_plat_account_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `account_id` | BIGINT | 是 | 无 | 平台账号 ID。 |
| `role_id` | BIGINT | 是 | 无 | 平台角色 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_account_role(account_id, role_id, delete_token)`。
- 普通索引：`idx_plat_account_role_role(role_id)`。
- 逻辑外键：`account_id -> un_plat_account.id`，`role_id -> un_plat_role.id`。

### 4.8 `un_plat_role_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `role_id` | BIGINT | 是 | 无 | 平台角色 ID。 |
| `menu_id` | BIGINT | 是 | 无 | 平台菜单 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_role_menu(role_id, menu_id, delete_token)`。
- 普通索引：`idx_plat_role_menu_menu(menu_id)`。

### 4.9 `un_plat_role_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `role_id` | BIGINT | 是 | 无 | 平台角色 ID。 |
| `operation_id` | BIGINT | 是 | 无 | 平台操作权限 ID。 |
| `operation_code` | VARCHAR(64) | 是 | 无 | 操作编码快照，便于权限计算。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_role_operation(role_id, operation_id, delete_token)`。
- 普通索引：`idx_plat_role_operation_code(operation_code)`。

### 4.10 `un_plat_config`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `config_key` | VARCHAR(128) | 是 | 无 | 配置 key，全局唯一。 |
| `config_name` | VARCHAR(128) | 是 | 无 | 配置名称。 |
| `config_value` | JSON | 是 | 无 | 配置值；敏感字段只保存密文或引用。 |
| `sensitive_flag` | TINYINT | 是 | 0 | 是否敏感配置。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |
| `remark` | VARCHAR(512) | 否 | NULL | 备注。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_plat_config_key(config_key, delete_token)`。
- 普通索引：`idx_plat_config_status(status)`。

## 五、系统成员与系统 RBAC 表字段

### 5.1 `un_module_member`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `account_id` | BIGINT | 是 | 无 | 引用平台账号 ID。 |
| `member_code` | VARCHAR(64) | 是 | 无 | 系统内成员编码，同系统唯一。 |
| `display_name_snapshot` | VARCHAR(128) | 是 | 无 | 平台账号展示名快照，列表展示使用。 |
| `default_tenant_id` | BIGINT | 是 | 无 | 默认租户 ID。 |
| `post_name` | VARCHAR(128) | 否 | NULL | 岗位名称。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 成员状态：`ENABLED`、`DISABLED`。 |
| `super_admin_flag` | TINYINT | 是 | 0 | 是否系统超级管理员成员。 |
| `last_enter_at` | DATETIME(3) | 否 | NULL | 最近进入系统时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_system_account(system_id, account_id, delete_token)`。
- 唯一：`uk_module_member_system_code(system_id, member_code, delete_token)`。
- 普通索引：`idx_module_member_account(account_id)`、`idx_module_member_system_status(system_id, status)`、`idx_module_member_default_tenant(default_tenant_id)`。
- 逻辑外键：`system_id -> un_plat_system.id`，`account_id -> un_plat_account.id`，`default_tenant_id -> un_plat_tenant.id`。

边界说明：平台账号是唯一登录主体；系统内成员只承载系统上下文、部门、角色、租户和数据范围。登录接口不得直接使用 `un_module_member`，系统内接口必须通过 `system_id + account_id` 解析成员。

### 5.2 `un_module_member_tenant`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `member_id` | BIGINT | 是 | 无 | 系统成员 ID。 |
| `tenant_id` | BIGINT | 是 | 无 | 可访问租户 ID。 |
| `primary_flag` | TINYINT | 是 | 0 | 是否默认/主租户。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_tenant(member_id, tenant_id, delete_token)`。
- 普通索引：`idx_module_member_tenant_system(system_id, tenant_id)`。

### 5.3 `un_module_dept`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 所属租户；0 表示系统级共享部门。 |
| `parent_id` | BIGINT | 是 | 0 | 父部门 ID，0 表示根部门。 |
| `code` | VARCHAR(64) | 是 | 无 | 部门编码，同系统同父级唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 部门名称。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 部门状态：`ENABLED`、`DISABLED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 层级。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 部门路径。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dept_parent_code(system_id, tenant_id, parent_id, code, delete_token)`。
- 普通索引：`idx_module_dept_tree(system_id, tenant_id, parent_id, sort_order)`、`idx_module_dept_path(system_id, depth_path)`。

### 5.4 `un_module_member_dept`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `member_id` | BIGINT | 是 | 无 | 成员 ID。 |
| `dept_id` | BIGINT | 是 | 无 | 部门 ID。 |
| `primary_flag` | TINYINT | 是 | 0 | 是否主部门。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_dept(member_id, dept_id, delete_token)`。
- 普通索引：`idx_module_member_dept_dept(system_id, dept_id)`。

### 5.5 `un_module_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户级角色归属；0 表示系统级角色。 |
| `code` | VARCHAR(64) | 是 | 无 | 角色编码，同系统同租户唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 角色名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 角色说明。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 角色状态：`ENABLED`、`DISABLED`。 |
| `protected_flag` | TINYINT | 是 | 0 | 是否保护角色，如 `SYS_SUPER_ADMIN`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_system_tenant_code(system_id, tenant_id, code, delete_token)`。
- 普通索引：`idx_module_role_status(system_id, status)`。

### 5.6 `un_module_member_role`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `member_id` | BIGINT | 是 | 无 | 成员 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_member_role(member_id, role_id, delete_token)`。
- 普通索引：`idx_module_member_role_role(system_id, role_id)`。

### 5.7 `un_module_system_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户级菜单归属；0 表示系统级。 |
| `parent_id` | BIGINT | 是 | 0 | 父菜单 ID。 |
| `code` | VARCHAR(64) | 是 | 无 | 菜单编码。 |
| `name` | VARCHAR(128) | 是 | 无 | 菜单名称。 |
| `menu_type` | VARCHAR(32) | 是 | `ADMIN` | 菜单类型：`ADMIN`、`RUNTIME`、`APP`。 |
| `source_type` | VARCHAR(32) | 是 | `SYSTEM` | 来源：`SYSTEM`、`MODULE`、`PAGE`、`FLOW`、`OPENAPI`。 |
| `source_id` | BIGINT | 否 | NULL | 关联来源对象 ID，模块/页面由 DBA-003 设计。 |
| `path` | VARCHAR(255) | 否 | NULL | 前端路由。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 层级。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 菜单路径。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_sys_menu_parent_code(system_id, tenant_id, parent_id, code, delete_token)`。
- 普通索引：`idx_module_sys_menu_tree(system_id, tenant_id, parent_id, sort_order)`、`idx_module_sys_menu_source(source_type, source_id)`。

### 5.8 `un_module_system_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `menu_id` | BIGINT | 否 | NULL | 所属菜单；系统级操作可为空。 |
| `code` | VARCHAR(64) | 是 | 无 | 操作编码，如 `SYS_MEMBER_VIEW`、`RECORD_EDIT`。 |
| `name` | VARCHAR(128) | 是 | 无 | 操作名称。 |
| `operation_type` | VARCHAR(32) | 是 | `API` | 类型：`API`、`BUTTON`、`FLOW_ACTION`、`EXPORT`、`OPENAPI_SCOPE`。 |
| `resource_type` | VARCHAR(32) | 是 | `SYSTEM` | 资源类型：`SYSTEM`、`MODULE`、`FIELD`、`FLOW`、`EXPORT`、`OPENAPI`。 |
| `resource_id` | BIGINT | 否 | NULL | 资源 ID，跨分片逻辑引用。 |
| `api_pattern` | VARCHAR(255) | 否 | NULL | 对应 API 路径模式。 |
| `method` | VARCHAR(16) | 否 | NULL | HTTP 方法。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_sys_operation_code(system_id, code, delete_token)`。
- 普通索引：`idx_module_sys_operation_menu(menu_id)`、`idx_module_sys_operation_resource(system_id, resource_type, resource_id)`。

### 5.9 `un_module_role_menu`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `menu_id` | BIGINT | 是 | 无 | 系统菜单 ID。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_menu(role_id, menu_id, delete_token)`。
- 普通索引：`idx_module_role_menu_menu(system_id, menu_id)`。

### 5.10 `un_module_role_operation`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `operation_id` | BIGINT | 是 | 无 | 操作权限 ID。 |
| `operation_code` | VARCHAR(64) | 是 | 无 | 操作编码快照。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_operation(role_id, operation_id, delete_token)`。
- 普通索引：`idx_module_role_operation_code(system_id, operation_code)`。

### 5.11 `un_module_role_field_permission`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `module_id` | BIGINT | 是 | 无 | 模块 ID，逻辑引用 DBA-003 模块表。 |
| `field_id` | BIGINT | 是 | 无 | 字段 ID，逻辑引用 DBA-003 字段表。 |
| `field_code` | VARCHAR(64) | 是 | 无 | 字段编码快照。 |
| `visible` | TINYINT | 是 | 0 | 字段是否可见。 |
| `writable` | TINYINT | 是 | 0 | 字段是否可写。 |
| `export_plain` | TINYINT | 是 | 0 | 导出时是否允许明文。 |
| `openapi_readable` | TINYINT | 是 | 0 | OpenAPI 是否可读。 |
| `openapi_writable` | TINYINT | 是 | 0 | OpenAPI 是否可写。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_field(role_id, module_id, field_id, delete_token)`。
- 普通索引：`idx_module_role_field_lookup(system_id, module_id, field_code)`。

### 5.12 `un_module_role_data_scope`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `resource_type` | VARCHAR(32) | 是 | `MODULE` | 资源类型：`SYSTEM`、`MODULE`、`FLOW`、`EXPORT`、`OPENAPI`。 |
| `resource_id` | BIGINT | 是 | 0 | 资源 ID；0 表示该类型全局。 |
| `scope_type` | VARCHAR(32) | 是 | `SELF` | 数据范围：`SELF`、`DEPT`、`DEPT_TREE`、`ALL`、`CUSTOM`。 |
| `dept_ids_json` | JSON | 否 | NULL | 部门范围。 |
| `member_ids_json` | JSON | 否 | NULL | 成员范围。 |
| `custom_conditions` | JSON | 否 | NULL | 结构化自定义条件。 |
| `min_visible_rule` | VARCHAR(32) | 是 | `INTERSECTION` | 多角色合并规则：`INTERSECTION`、`UNION_LIMITED`。MVP 按最小可见规则。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_data_scope(role_id, resource_type, resource_id, delete_token)`。
- 普通索引：`idx_module_role_data_scope_system(system_id, resource_type, resource_id)`。

### 5.13 `un_module_role_openapi_scope`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `scope_code` | VARCHAR(128) | 是 | 无 | OpenAPI scope 编码，例如记录读写、流程动作、文件下载。 |
| `module_id` | BIGINT | 否 | NULL | 模块级 scope 绑定。 |
| `field_codes_json` | JSON | 否 | NULL | 字段级 OpenAPI 读写范围快照。 |
| `scope_action` | VARCHAR(32) | 是 | `READ` | scope 动作：`READ`、`WRITE`、`FLOW_ACTION`、`FILE_DOWNLOAD`。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_openapi_scope(role_id, scope_code, module_id, delete_token)`。
- 普通索引：`idx_module_role_openapi_scope(system_id, scope_code)`。
- 边界：只保存系统角色对 scope 的授权边界；真实客户端、凭证、scope catalog、白名单和调用日志由 `un_openapi_` 分片设计。

### 5.14 `un_module_role_explicit_deny`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `role_id` | BIGINT | 是 | 无 | 系统角色 ID。 |
| `deny_type` | VARCHAR(32) | 是 | 无 | 禁用类型：`MENU`、`OPERATION`、`FIELD_READ`、`FIELD_WRITE`、`DATA_SCOPE`、`EXPORT`、`OPENAPI_SCOPE`。 |
| `target_id` | BIGINT | 是 | 0 | 禁用对象 ID；0 表示按编码禁用。 |
| `target_code` | VARCHAR(128) | 否 | NULL | 禁用对象编码。 |
| `reason` | VARCHAR(512) | 否 | NULL | 禁用原因。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_role_deny(role_id, deny_type, target_id, target_code, delete_token)`。
- 普通索引：`idx_module_role_deny_system(system_id, deny_type)`。

### 5.15 `un_module_permission_version`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `tenant_id` | BIGINT | 是 | 0 | 租户归属；0 表示系统级。 |
| `version_no` | BIGINT | 是 | 1 | 权限缓存版本号。 |
| `changed_reason` | VARCHAR(128) | 否 | NULL | 最近变更原因，如 `ROLE_AUTH_SAVE`。 |
| `changed_at` | DATETIME(3) | 是 | CURRENT_TIMESTAMP(3) | 最近变更时间。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_perm_version(system_id, tenant_id, delete_token)`。
- 并发规则：角色授权、成员角色、菜单/操作/字段/数据范围/OpenAPI scope 变更成功后，在同一事务内递增 `version_no`；缓存刷新失败时不得回滚已提交权限，但必须返回可重试错误或登记补偿。

## 六、字典表字段

### 6.1 `un_module_dict_type`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `scope_type` | VARCHAR(16) | 是 | `SYSTEM` | 作用域：`SYSTEM`、`TENANT`。 |
| `scope_tenant_id` | BIGINT | 是 | 0 | 作用域租户 ID；`SYSTEM` 为 0，`TENANT` 必须为有效租户 ID。 |
| `code` | VARCHAR(64) | 是 | 无 | 字典类型编码，同作用域唯一。 |
| `name` | VARCHAR(128) | 是 | 无 | 字典类型名称。 |
| `description` | VARCHAR(512) | 否 | NULL | 描述。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`、`DELETED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `system_built_in` | TINYINT | 是 | 0 | 是否内置只读。 |
| `cache_version` | BIGINT | 是 | 1 | 字典缓存版本；写操作成功后递增。 |
| `item_count` | INT | 是 | 0 | 字典项数量冗余。 |
| `enabled_item_count` | INT | 是 | 0 | 启用字典项数量冗余。 |
| `referenced_flag` | TINYINT | 是 | 0 | 是否存在字段或记录引用。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dict_type_scope(system_id, scope_type, scope_tenant_id, code, delete_token)`。
- 普通索引：`idx_module_dict_type_status(system_id, status)`、`idx_module_dict_type_tenant(system_id, scope_tenant_id)`。
- 校验：`scope_type=SYSTEM` 时 `scope_tenant_id=0`；`scope_type=TENANT` 时 `scope_tenant_id>0`。

### 6.2 `un_module_dict_item`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID，冗余用于隔离和索引。 |
| `dict_type_id` | BIGINT | 是 | 无 | 字典类型 ID。 |
| `parent_id` | BIGINT | 是 | 0 | 父字典项 ID，0 表示根项。 |
| `code` | VARCHAR(64) | 是 | 无 | 字典项编码，同父级唯一。 |
| `label` | VARCHAR(128) | 是 | 无 | 展示文本。 |
| `value` | VARCHAR(128) | 是 | 无 | 业务值，同父级唯一。 |
| `description` | VARCHAR(512) | 否 | NULL | 描述。 |
| `status` | VARCHAR(32) | 是 | `ENABLED` | 状态：`ENABLED`、`DISABLED`、`DELETED`。 |
| `sort_order` | INT | 是 | 0 | 排序。 |
| `depth_level` | INT | 是 | 1 | 层级，最大 5。 |
| `depth_path` | VARCHAR(512) | 是 | `/` | 路径，形如 `/rootId/childId`。 |
| `leaf_flag` | TINYINT | 是 | 1 | 是否叶子节点。 |
| `system_built_in` | TINYINT | 是 | 0 | 是否内置只读。 |
| `referenced_flag` | TINYINT | 是 | 0 | 是否被记录值引用。 |
| `ext_json` | JSON | 否 | NULL | 扩展信息，不允许存敏感信息。 |
| `cache_version` | BIGINT | 是 | 1 | 冗余字典类型缓存版本，便于返回和测试断言。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dict_item_code(dict_type_id, parent_id, code, delete_token)`。
- 唯一：`uk_module_dict_item_value(dict_type_id, parent_id, value, delete_token)`。
- 普通索引：`idx_module_dict_item_tree(dict_type_id, parent_id, sort_order)`、`idx_module_dict_item_status(system_id, status)`、`idx_module_dict_item_path(dict_type_id, depth_path)`。
- 校验：`depth_level <= 5`；父级停用时禁止新增或启用子项；移动父级必须防环。

### 6.3 `un_module_dict_reference`

| 字段 | 类型 | 是否必填 | 默认值 | 含义 |
| --- | --- | --- | --- | --- |
| `system_id` | BIGINT | 是 | 无 | 所属系统 ID。 |
| `dict_type_id` | BIGINT | 是 | 无 | 字典类型 ID。 |
| `dict_item_id` | BIGINT | 是 | 0 | 字典项 ID；0 表示类型级引用。 |
| `reference_type` | VARCHAR(32) | 是 | 无 | 引用类型：`FIELD_CONFIG`、`PUBLISHED_FIELD`、`RECORD_VALUE`。 |
| `module_id` | BIGINT | 否 | NULL | 模块 ID。 |
| `field_id` | BIGINT | 否 | NULL | 字段 ID。 |
| `field_code` | VARCHAR(64) | 否 | NULL | 字段编码快照。 |
| `published_version_id` | BIGINT | 否 | NULL | 发布版本 ID，逻辑引用 DBA-003。 |
| `record_id` | BIGINT | 否 | NULL | 记录 ID，逻辑引用 DBA-003。 |
| `usage_count` | BIGINT | 是 | 1 | 引用数量摘要；记录值批量统计可累加。 |
| `active_flag` | TINYINT | 是 | 1 | 当前是否有效引用。 |

索引与约束：

- 主键：`id`。
- 唯一：`uk_module_dict_ref_unique(dict_type_id, dict_item_id, reference_type, module_id, field_id, published_version_id, record_id, delete_token)`。
- 普通索引：`idx_module_dict_ref_type(dict_type_id, reference_type, active_flag)`、`idx_module_dict_ref_item(dict_item_id, reference_type, active_flag)`、`idx_module_dict_ref_field(module_id, field_id)`。
- 边界：字段配置和发布版本引用由 DBA-003 字段/发布流程维护；记录值引用由运行记录保存事务维护。本表只保存引用摘要，不替代 EAV 字段值表。

## 七、关系说明

1. 平台账号与系统成员：`un_plat_account.id` 一对多 `un_module_member.account_id`。同一系统内通过 `system_id + account_id` 唯一，确保一个平台账号在一个系统只有一个成员扩展。
2. 系统与租户：`un_plat_system.id` 一对多 `un_plat_tenant.system_id`；单租户系统也初始化 `code=default` 的租户。
3. 系统创建初始化：`un_plat_system`、`un_plat_tenant`、`un_module_member`、`un_module_role`、`un_module_system_menu`、`un_module_system_operation`、`un_module_member_role`、`un_module_role_*`、`un_module_permission_version` 必须在 `PLAT-002` 同一事务内完成。
4. 平台 RBAC：平台账号通过 `un_plat_account_role` 关联平台角色；平台角色通过 `un_plat_role_menu` 和 `un_plat_role_operation` 获得平台中心权限。平台权限不授予系统内业务数据写入。
5. 系统 RBAC：系统成员通过 `un_module_member_role` 关联角色；角色授权由菜单、操作、字段权限、数据范围、OpenAPI scope 和显式禁用表共同计算。
6. 部门关系：部门树由 `un_module_dept.parent_id` 与 `depth_path` 表达；成员与部门是多对多，主部门由 `un_module_member_dept.primary_flag` 标记。
7. 字典关系：`un_module_dict_type` 一对多 `un_module_dict_item`；字典项自关联形成树；`un_module_dict_reference` 记录字段配置、发布版本和记录值引用摘要。
8. OpenAPI scope 边界：`un_module_role_openapi_scope.scope_code` 只用于系统角色授权计算，真实 scope catalog、客户端凭证和调用日志由 DBA-004 的 `un_openapi_` 表域承接。

## 八、状态枚举

| 对象 | 字段 | 枚举 |
| --- | --- | --- |
| 平台账号 | `status` | `NORMAL`、`DISABLED`、`LOCKED` |
| 平台系统 | `status` | `DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED` |
| 租户 | `status` | `ENABLED`、`DISABLED` |
| 平台角色/菜单/操作/配置 | `status` | `ENABLED`、`DISABLED` |
| 系统成员 | `status` | `ENABLED`、`DISABLED` |
| 部门 | `status` | `ENABLED`、`DISABLED` |
| 系统角色 | `status` | `ENABLED`、`DISABLED` |
| 系统菜单/操作 | `status` | `ENABLED`、`DISABLED` |
| 字典类型/字典项 | `status` | `ENABLED`、`DISABLED`、`DELETED` |
| 字典类型作用域 | `scope_type` | `SYSTEM`、`TENANT` |
| 数据范围 | `scope_type` | `SELF`、`DEPT`、`DEPT_TREE`、`ALL`、`CUSTOM` |

状态变更必须校验 `version`。字典删除是软删除终态，默认列表不返回 `DELETED`；平台账号、成员和角色 MVP 只有启停/锁定，不暴露物理删除能力。

## 九、初始化数据与 seed 边界

### 9.1 生产 `init.sql` 必须包含的建库 seed

本分片要求 DBA-006 在生产 `init.sql` 中初始化以下平台域数据，具体 SQL 由 DBA-006 生成：

- 默认平台管理员账号：`login_name=platform_admin`，`status=NORMAL`，`first_login_change_pwd=1`，密码使用安全占位哈希或部署变量注入，不写明文。
- 平台角色：`PLAT_SUPER_ADMIN`、`PLAT_ADMIN`、`PLAT_AUDITOR`。
- 平台菜单：`PLAT_MY_SYSTEM`、`PLAT_SYSTEM`、`PLAT_TENANT`、`PLAT_ACCOUNT`、`PLAT_ROLE`、`PLAT_CONFIG`、`PLAT_HEALTH`、`PLAT_AUDIT_LOG`、`PLAT_VERSION`、`PLAT_OPENAPI_POLICY`。
- 平台操作权限：围绕 `PLAT_SYSTEM_CREATE`、`PLAT_SYSTEM_VIEW`、`PLAT_SYSTEM_STATUS`、`PLAT_ACCOUNT_VIEW`、`PLAT_ACCOUNT_CREATE`、`PLAT_ACCOUNT_STATUS`、`PLAT_ROLE_VIEW`、`PLAT_ROLE_AUTH`、`PLAT_CONFIG_VIEW`、`PLAT_CONFIG_EDIT` 等 API 权限点建立。
- 平台配置：`SECURITY_PASSWORD_POLICY`、`SESSION_POLICY`、`FILE_STORAGE_DEFAULT`、`OPENAPI_GLOBAL_POLICY`、`AUDIT_RETENTION_POLICY`。
- 默认账号与 `PLAT_SUPER_ADMIN` 绑定，`PLAT_SUPER_ADMIN` 绑定全部平台菜单和平台操作。

字段类型元数据虽然在 PRD 中属于建库 seed，但其表结构由 DBA-003 模块/字段配置分片承接；本分片只在系统创建事务中预留“字段类型引用初始化”的跨分片边界。

### 9.2 `PLAT-002` 创建系统事务内 seed

创建系统必须在一个事务内初始化：

- `un_plat_system`：系统基础信息，初始状态按 API 入参和产品规则写入，成功后可为 `ENABLED` 或配置态状态。
- `un_plat_tenant`：默认租户 `code=default`，`status=ENABLED`。
- `un_module_member`：创建人成员扩展，唯一键 `system_id + account_id`。
- `un_module_member_tenant`：创建人成员绑定默认租户。
- `un_module_role`：系统超级管理员角色 `SYS_SUPER_ADMIN`，`protected_flag=1`，`status=ENABLED`。
- `un_module_member_role`：创建人成员绑定 `SYS_SUPER_ADMIN`。
- `un_module_system_menu` / `un_module_system_operation`：系统默认菜单和操作权限目录。
- `un_module_role_menu` / `un_module_role_operation` / 需要的字段、数据范围、OpenAPI scope 授权：`SYS_SUPER_ADMIN` 获得当前系统内全部管理权限。
- `un_module_permission_version`：初始化 `version_no=1`。
- 默认应用 `default_app` 和字段类型引用由 DBA-003 对应表承接，但必须与以上对象同事务提交。

任一步失败必须整体回滚，不允许出现“系统已创建但创建人没有系统超级管理员权限”。

### 9.3 不进入生产 seed

- 演示系统、演示租户、演示部门、演示成员、演示角色、演示授权。
- 用户自定义字典类型和字典项。
- OpenAPI 测试客户端、测试 scope、测试调用日志。
- 业务样例应用、模块、记录、流程、附件和导出任务。

## 十、并发、软删与缓存规则

1. 所有状态变更、角色授权保存、成员角色保存、字典写操作必须带 `version` 乐观锁；版本不一致返回对应状态冲突或权限冲突错误。
2. `PLAT-002` 必须配合内部幂等键，幂等记录由公共/审计或 OpenAPI 分片承接；DBA-002 表结构保证系统编码、默认成员和默认角色唯一，防止重复初始化。
3. 字典类型和字典项软删除时设置 `status=DELETED`、`deleted_flag=1`、`delete_token=id`，允许同作用域重新创建同编码字典。
4. 字典类型系统级作用域使用 `scope_tenant_id=0`，字典项根节点使用 `parent_id=0`，避免 MySQL nullable 唯一索引允许重复的问题。
5. 字典写接口 `DICT-002` 至 `DICT-011` 在同一事务内递增 `un_module_dict_type.cache_version`，同步更新受影响字典项 `cache_version`；缓存刷新失败但数据库已提交时返回 `DICT_CACHE_REFRESH_FAILED`，并由后续补偿任务处理缓存。
6. 权限写接口 `RBAC-009`、成员角色变更 `MEM-006`、部门和角色变更成功后递增 `un_module_permission_version.version_no`。`RBAC-010` 返回该版本，前端据此刷新权限。
7. 系统成员没有独立登录密码，不允许通过成员表绕过平台账号状态；账号停用/锁定时所有系统成员上下文均不可进入。
8. 平台超级管理员只拥有平台中心和审计入口权限；如未被加入某系统成为成员，不得通过系统 RBAC 表获得系统内业务写权限。

## 十一、API 数据落点

| API | 数据落点 |
| --- | --- |
| AUTH-001/002/003/004/005 | 账号主体读取或更新 `un_plat_account`；登录日志不在本分片展开。 |
| PLAT-001 | 查询 `un_plat_system`、`un_plat_tenant`、`un_module_member`、`un_module_member_role`、`un_module_role` 组合得到“我的系统”。 |
| PLAT-002 | 单事务写 `un_plat_system`、`un_plat_tenant`、`un_module_member`、`un_module_member_tenant`、`un_module_role`、`un_module_member_role`、`un_module_system_menu`、`un_module_system_operation`、`un_module_role_*`、`un_module_permission_version`；默认应用和字段类型引用由 DBA-003 表承接。 |
| PLAT-003/004/005 | 查询或更新 `un_plat_system`，停用/归档影响系统进入和后续系统内写入。 |
| PLAT-006/007/008/013/014/015/016 | 平台账号管理落 `un_plat_account`、`un_plat_account_role`。 |
| PLAT-009/010/017/018/019/020 | 平台角色、菜单和操作权限落 `un_plat_role`、`un_plat_menu`、`un_plat_operation`、`un_plat_role_menu`、`un_plat_role_operation`。 |
| PLAT-011/012 | 平台配置落 `un_plat_config`。 |
| SYS-001 | 通过 `system_id + account_id` 查询 `un_module_member`，校验 `un_plat_system`、`un_plat_tenant`、成员状态并建立上下文。 |
| SYS-002/003 | 系统基础信息读取或更新 `un_plat_system`。 |
| SYS-004/005/006/007 | 租户读取、创建、启停和切换落 `un_plat_tenant`、`un_module_member_tenant`。 |
| MEM-001 至 MEM-007 | 成员扩展、租户、部门、角色落 `un_module_member`、`un_module_member_tenant`、`un_module_member_dept`、`un_module_member_role`。 |
| RBAC-001 至 RBAC-004 | 部门树落 `un_module_dept`、`un_module_member_dept`；删除前检查子部门和成员关联。 |
| RBAC-005 至 RBAC-013 | 系统角色和权限目录落 `un_module_role`、`un_module_system_menu`、`un_module_system_operation`、`un_module_role_*`、`un_module_permission_version`。 |
| DICT-001 至 DICT-011 | 字典类型、字典项、引用和缓存版本落 `un_module_dict_type`、`un_module_dict_item`、`un_module_dict_reference`。 |
| OPM-009 / RBAC-013 scope catalog | 本分片只通过 `un_module_role_openapi_scope` 记录角色授权到 scope 的边界；真实 scope catalog 由 DBA-004 的 `un_openapi_` 表设计承接。 |

## 十二、自检结果

| 自检项 | 结果 |
| --- | --- |
| 是否只输出 DBA-002 分片 | 通过。仅设计 `docs/db_design_parts/platform-system-rbac-dict.md`，不生成 SQL，不写总 DB 设计。 |
| 表前缀是否合规 | 通过。平台域使用 `un_plat_`，系统成员/RBAC/字典使用 `un_module_`，未使用 `un_platt_` 或 `un_app_`。 |
| 平台账号与系统成员是否分离 | 通过。`un_plat_account` 是登录主体，`un_module_member` 是 `system_id + account_id` 唯一的系统成员扩展。 |
| 系统创建事务 seed 是否可落库 | 通过。已列明系统、默认租户、创建人成员、系统超级管理员、默认菜单/权限、权限版本和跨 DBA-003 默认应用/字段类型引用边界。 |
| RBAC API 是否有落点 | 通过。菜单、操作、字段权限、数据范围、OpenAPI scope、显式禁用和权限缓存版本均有表承接。 |
| 字典唯一性、层级、引用、缓存版本是否可落库 | 通过。字典类型和字典项使用非空归一化 key 规避 MySQL nullable 唯一索引问题，支持 5 级层级、引用摘要和 cacheVersion 递增。 |
| 并行输出路径是否重叠 | 通过。本任务只写 `docs/db_design_parts/platform-system-rbac-dict.md`，与 DBA-003/DBA-004 分片路径不重叠，最终汇总由 DBA-005 串行写 `docs/db_design.md`。 |
