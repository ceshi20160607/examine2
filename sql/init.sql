-- unexamine initialization SQL generated from docs/db_design.md by DBA-006.
-- Production seed only; no demo business data and no plaintext default password.
CREATE DATABASE IF NOT EXISTS `examine1` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `examine1`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `un_plat_account`;
CREATE TABLE `un_plat_account` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `login_name` VARCHAR(64) NOT NULL COMMENT '登录名，全局唯一。',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希，不返回前端。',
  `display_name` VARCHAR(64) NOT NULL COMMENT '展示名称。',
  `mobile` VARCHAR(32) NULL DEFAULT NULL COMMENT '手机号。',
  `email` VARCHAR(128) NULL DEFAULT NULL COMMENT '邮箱。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '账号状态：NORMAL、DISABLED、LOCKED。',
  `first_login_change_pwd` TINYINT NOT NULL DEFAULT 0 COMMENT '是否首次登录必须改密。',
  `failed_login_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数。',
  `locked_until` DATETIME(3) NULL DEFAULT NULL COMMENT '锁定截止时间。',
  `last_login_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近登录时间。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_login_name` (`login_name`, `delete_token`),
  KEY `idx_plat_account_status` (`status`),
  KEY `idx_plat_account_mobile` (`mobile`),
  KEY `idx_plat_account_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局登录主体，承载登录名、密码哈希、状态和安全字段。';

DROP TABLE IF EXISTS `un_plat_system`;
CREATE TABLE `un_plat_system` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `code` VARCHAR(64) NOT NULL COMMENT '系统编码，全局唯一。',
  `name` VARCHAR(128) NOT NULL COMMENT '系统名称。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '系统描述。',
  `tenant_mode` VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '租户模式：SINGLE、MULTI。',
  `default_tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '默认租户 ID，创建系统事务内回填。',
  `owner_account_id` BIGINT UNSIGNED NOT NULL COMMENT '创建人平台账号 ID。',
  `owner_member_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人在系统内的成员扩展 ID，初始化完成后回填。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '系统状态：DRAFT、ENABLED、DISABLED、ARCHIVED。',
  `domain` VARCHAR(128) NULL DEFAULT NULL COMMENT '可选系统访问域名或入口标识。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_system_code` (`code`, `delete_token`),
  KEY `idx_plat_system_owner` (`owner_account_id`),
  KEY `idx_plat_system_status` (`status`),
  KEY `idx_plat_system_default_tenant` (`default_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自定义系统容器，承载系统编码、租户模式、创建人和状态。';

DROP TABLE IF EXISTS `un_plat_tenant`;
CREATE TABLE `un_plat_tenant` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `code` VARCHAR(64) NOT NULL COMMENT '租户编码，同系统唯一；默认租户为 default。',
  `name` VARCHAR(128) NOT NULL COMMENT '租户名称。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '租户状态：ENABLED、DISABLED。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '租户描述。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_tenant_system_code` (`system_id`, `code`, `delete_token`),
  KEY `idx_plat_tenant_system_status` (`system_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统下租户，单租户系统也初始化默认租户。';

DROP TABLE IF EXISTS `un_plat_role`;
CREATE TABLE `un_plat_role` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `code` VARCHAR(64) NOT NULL COMMENT '平台角色编码，全局唯一。',
  `name` VARCHAR(128) NOT NULL COMMENT '平台角色名称。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '角色说明。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '角色状态：ENABLED、DISABLED。',
  `protected_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否保护角色；保护角色禁止删除和关键权限移除。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_code` (`code`, `delete_token`),
  KEY `idx_plat_role_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台中心角色。';

DROP TABLE IF EXISTS `un_plat_menu`;
CREATE TABLE `un_plat_menu` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单 ID，0 表示根菜单。',
  `code` VARCHAR(64) NOT NULL COMMENT '菜单编码，同父级唯一。',
  `name` VARCHAR(128) NOT NULL COMMENT '菜单名称。',
  `path` VARCHAR(255) NULL DEFAULT NULL COMMENT '前端路由或入口路径。',
  `icon` VARCHAR(128) NULL DEFAULT NULL COMMENT '图标编码。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '菜单状态：ENABLED、DISABLED。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `depth_level` INT NOT NULL DEFAULT 1 COMMENT '菜单层级。',
  `depth_path` VARCHAR(512) NOT NULL DEFAULT '/' COMMENT '菜单路径，用于树查询。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_menu_parent_code` (`parent_id`, `code`, `delete_token`),
  KEY `idx_plat_menu_parent` (`parent_id`, `sort_order`),
  KEY `idx_plat_menu_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台中心菜单树。';

DROP TABLE IF EXISTS `un_plat_operation`;
CREATE TABLE `un_plat_operation` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '所属平台菜单。',
  `code` VARCHAR(64) NOT NULL COMMENT '操作权限编码，例如 PLAT_ACCOUNT_CREATE。',
  `name` VARCHAR(128) NOT NULL COMMENT '操作名称。',
  `api_pattern` VARCHAR(255) NULL DEFAULT NULL COMMENT '对应 API 路径模式。',
  `method` VARCHAR(16) NULL DEFAULT NULL COMMENT 'HTTP 方法。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_operation_code` (`code`, `delete_token`),
  KEY `idx_plat_operation_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台中心操作权限点。';

DROP TABLE IF EXISTS `un_plat_account_role`;
CREATE TABLE `un_plat_account_role` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `account_id` BIGINT UNSIGNED NOT NULL COMMENT '平台账号 ID。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '平台角色 ID。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_role` (`account_id`, `role_id`, `delete_token`),
  KEY `idx_plat_account_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台账号与平台角色关联。';

DROP TABLE IF EXISTS `un_plat_role_menu`;
CREATE TABLE `un_plat_role_menu` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '平台角色 ID。',
  `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '平台菜单 ID。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_menu` (`role_id`, `menu_id`, `delete_token`),
  KEY `idx_plat_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台角色与菜单授权关联。';

DROP TABLE IF EXISTS `un_plat_role_operation`;
CREATE TABLE `un_plat_role_operation` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '平台角色 ID。',
  `operation_id` BIGINT UNSIGNED NOT NULL COMMENT '平台操作权限 ID。',
  `operation_code` VARCHAR(64) NOT NULL COMMENT '操作编码快照，便于权限计算。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_operation` (`role_id`, `operation_id`, `delete_token`),
  KEY `idx_plat_role_operation_code` (`operation_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台角色与操作权限授权关联。';

DROP TABLE IF EXISTS `un_plat_config`;
CREATE TABLE `un_plat_config` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置 key，全局唯一。',
  `config_name` VARCHAR(128) NOT NULL COMMENT '配置名称。',
  `config_value` JSON NOT NULL COMMENT '配置值；敏感字段只保存密文或引用。',
  `sensitive_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否敏感配置。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED。',
  `remark` VARCHAR(512) NULL DEFAULT NULL COMMENT '备注。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_config_key` (`config_key`, `delete_token`),
  KEY `idx_plat_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='密码策略、会话策略、文件存储、OpenAPI 全局策略和审计保留配置。';

DROP TABLE IF EXISTS `un_module_member`;
CREATE TABLE `un_module_member` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `account_id` BIGINT UNSIGNED NOT NULL COMMENT '引用平台账号 ID。',
  `member_code` VARCHAR(64) NOT NULL COMMENT '系统内成员编码，同系统唯一。',
  `display_name_snapshot` VARCHAR(128) NOT NULL COMMENT '平台账号展示名快照，列表展示使用。',
  `default_tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '默认租户 ID。',
  `post_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '岗位名称。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '成员状态：ENABLED、DISABLED。',
  `super_admin_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统超级管理员成员。',
  `last_enter_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近进入系统时间。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_member_system_account` (`system_id`, `account_id`, `delete_token`),
  UNIQUE KEY `uk_module_member_system_code` (`system_id`, `member_code`, `delete_token`),
  KEY `idx_module_member_account` (`account_id`),
  KEY `idx_module_member_system_status` (`system_id`, `status`),
  KEY `idx_module_member_default_tenant` (`default_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台账号在系统内的成员扩展，不是独立登录账号。';

DROP TABLE IF EXISTS `un_module_member_tenant`;
CREATE TABLE `un_module_member_tenant` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `member_id` BIGINT UNSIGNED NOT NULL COMMENT '系统成员 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '可访问租户 ID。',
  `primary_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认/主租户。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_member_tenant` (`member_id`, `tenant_id`, `delete_token`),
  KEY `idx_module_member_tenant_system` (`system_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员可访问租户集合。';

DROP TABLE IF EXISTS `un_module_dept`;
CREATE TABLE `un_module_dept` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属租户；0 表示系统级共享部门。',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父部门 ID，0 表示根部门。',
  `code` VARCHAR(64) NOT NULL COMMENT '部门编码，同系统同父级唯一。',
  `name` VARCHAR(128) NOT NULL COMMENT '部门名称。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '部门状态：ENABLED、DISABLED。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `depth_level` INT NOT NULL DEFAULT 1 COMMENT '层级。',
  `depth_path` VARCHAR(512) NOT NULL DEFAULT '/' COMMENT '部门路径。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_dept_parent_code` (`system_id`, `tenant_id`, `parent_id`, `code`, `delete_token`),
  KEY `idx_module_dept_tree` (`system_id`, `tenant_id`, `parent_id`, `sort_order`),
  KEY `idx_module_dept_path` (`system_id`, `depth_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统内部门树。';

DROP TABLE IF EXISTS `un_module_member_dept`;
CREATE TABLE `un_module_member_dept` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `member_id` BIGINT UNSIGNED NOT NULL COMMENT '成员 ID。',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '部门 ID。',
  `primary_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否主部门。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_member_dept` (`member_id`, `dept_id`, `delete_token`),
  KEY `idx_module_member_dept_dept` (`system_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员与部门关联。';

DROP TABLE IF EXISTS `un_module_role`;
CREATE TABLE `un_module_role` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户级角色归属；0 表示系统级角色。',
  `code` VARCHAR(64) NOT NULL COMMENT '角色编码，同系统同租户唯一。',
  `name` VARCHAR(128) NOT NULL COMMENT '角色名称。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '角色说明。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '角色状态：ENABLED、DISABLED。',
  `protected_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否保护角色，如 SYS_SUPER_ADMIN。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_role_system_tenant_code` (`system_id`, `tenant_id`, `code`, `delete_token`),
  KEY `idx_module_role_status` (`system_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统内角色，含系统超级管理员。';

DROP TABLE IF EXISTS `un_module_member_role`;
CREATE TABLE `un_module_member_role` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `member_id` BIGINT UNSIGNED NOT NULL COMMENT '成员 ID。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '系统角色 ID。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_member_role` (`member_id`, `role_id`, `delete_token`),
  KEY `idx_module_member_role_role` (`system_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员与系统角色关联。';

DROP TABLE IF EXISTS `un_module_system_menu`;
CREATE TABLE `un_module_system_menu` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户级菜单归属；0 表示系统级。',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单 ID。',
  `code` VARCHAR(64) NOT NULL COMMENT '菜单编码。',
  `name` VARCHAR(128) NOT NULL COMMENT '菜单名称。',
  `menu_type` VARCHAR(32) NOT NULL DEFAULT 'ADMIN' COMMENT '菜单类型：ADMIN、RUNTIME、APP。',
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '来源：SYSTEM、MODULE、PAGE、FLOW、OPENAPI。',
  `source_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联来源对象 ID，模块/页面由 DBA-003 设计。',
  `path` VARCHAR(255) NULL DEFAULT NULL COMMENT '前端路由。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `depth_level` INT NOT NULL DEFAULT 1 COMMENT '层级。',
  `depth_path` VARCHAR(512) NOT NULL DEFAULT '/' COMMENT '菜单路径。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_sys_menu_parent_code` (`system_id`, `tenant_id`, `parent_id`, `code`, `delete_token`),
  KEY `idx_module_sys_menu_tree` (`system_id`, `tenant_id`, `parent_id`, `sort_order`),
  KEY `idx_module_sys_menu_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统管理菜单和运行菜单授权目录。';

DROP TABLE IF EXISTS `un_module_system_operation`;
CREATE TABLE `un_module_system_operation` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `menu_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属菜单；系统级操作可为空。',
  `code` VARCHAR(64) NOT NULL COMMENT '操作编码，如 SYS_MEMBER_VIEW、RECORD_EDIT。',
  `name` VARCHAR(128) NOT NULL COMMENT '操作名称。',
  `operation_type` VARCHAR(32) NOT NULL DEFAULT 'API' COMMENT '类型：API、BUTTON、FLOW_ACTION、EXPORT、OPENAPI_SCOPE。',
  `resource_type` VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '资源类型：SYSTEM、MODULE、FIELD、FLOW、EXPORT、OPENAPI。',
  `resource_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '资源 ID，跨分片逻辑引用。',
  `api_pattern` VARCHAR(255) NULL DEFAULT NULL COMMENT '对应 API 路径模式。',
  `method` VARCHAR(16) NULL DEFAULT NULL COMMENT 'HTTP 方法。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_sys_operation_code` (`system_id`, `code`, `delete_token`),
  KEY `idx_module_sys_operation_menu` (`menu_id`),
  KEY `idx_module_sys_operation_resource` (`system_id`, `resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统内操作权限目录。';

DROP TABLE IF EXISTS `un_module_role_menu`;
CREATE TABLE `un_module_role_menu` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '系统角色 ID。',
  `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '系统菜单 ID。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_role_menu` (`role_id`, `menu_id`, `delete_token`),
  KEY `idx_module_role_menu_menu` (`system_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色菜单授权。';

DROP TABLE IF EXISTS `un_module_role_operation`;
CREATE TABLE `un_module_role_operation` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '系统角色 ID。',
  `operation_id` BIGINT UNSIGNED NOT NULL COMMENT '操作权限 ID。',
  `operation_code` VARCHAR(64) NOT NULL COMMENT '操作编码快照。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_role_operation` (`role_id`, `operation_id`, `delete_token`),
  KEY `idx_module_role_operation_code` (`system_id`, `operation_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色操作授权。';

DROP TABLE IF EXISTS `un_module_role_field_permission`;
CREATE TABLE `un_module_role_field_permission` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户归属；0 表示系统级。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '系统角色 ID。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '模块 ID，逻辑引用 DBA-003 模块表。',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '字段 ID，逻辑引用 DBA-003 字段表。',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码快照。',
  `visible` TINYINT NOT NULL DEFAULT 0 COMMENT '字段是否可见。',
  `writable` TINYINT NOT NULL DEFAULT 0 COMMENT '字段是否可写。',
  `export_plain` TINYINT NOT NULL DEFAULT 0 COMMENT '导出时是否允许明文。',
  `openapi_readable` TINYINT NOT NULL DEFAULT 0 COMMENT 'OpenAPI 是否可读。',
  `openapi_writable` TINYINT NOT NULL DEFAULT 0 COMMENT 'OpenAPI 是否可写。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_role_field` (`role_id`, `module_id`, `field_id`, `delete_token`),
  KEY `idx_module_role_field_lookup` (`system_id`, `module_id`, `field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字段可见、可写、导出明文和 OpenAPI 读写授权。';

DROP TABLE IF EXISTS `un_module_role_data_scope`;
CREATE TABLE `un_module_role_data_scope` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户归属；0 表示系统级。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '系统角色 ID。',
  `resource_type` VARCHAR(32) NOT NULL DEFAULT 'MODULE' COMMENT '资源类型：SYSTEM、MODULE、FLOW、EXPORT、OPENAPI。',
  `resource_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '资源 ID；0 表示该类型全局。',
  `scope_type` VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：SELF、DEPT、DEPT_TREE、ALL、CUSTOM。',
  `dept_ids_json` JSON NULL DEFAULT NULL COMMENT '部门范围。',
  `member_ids_json` JSON NULL DEFAULT NULL COMMENT '成员范围。',
  `custom_conditions` JSON NULL DEFAULT NULL COMMENT '结构化自定义条件。',
  `min_visible_rule` VARCHAR(32) NOT NULL DEFAULT 'INTERSECTION' COMMENT '多角色合并规则：INTERSECTION、UNION_LIMITED。MVP 按最小可见规则。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_role_data_scope` (`role_id`, `resource_type`, `resource_id`, `delete_token`),
  KEY `idx_module_role_data_scope_system` (`system_id`, `resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色数据范围规则。';

DROP TABLE IF EXISTS `un_module_role_openapi_scope`;
CREATE TABLE `un_module_role_openapi_scope` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户归属；0 表示系统级。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '系统角色 ID。',
  `scope_code` VARCHAR(128) NOT NULL COMMENT 'OpenAPI scope 编码，例如记录读写、流程动作、文件下载。',
  `module_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '模块级 scope 绑定。',
  `field_codes_json` JSON NULL DEFAULT NULL COMMENT '字段级 OpenAPI 读写范围快照。',
  `scope_action` VARCHAR(32) NOT NULL DEFAULT 'READ' COMMENT 'scope 动作：READ、WRITE、FLOW_ACTION、FILE_DOWNLOAD。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_role_openapi_scope` (`role_id`, `scope_code`, `module_id`, `delete_token`),
  KEY `idx_module_role_openapi_scope` (`system_id`, `scope_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色可授权 OpenAPI scope 边界。';

DROP TABLE IF EXISTS `un_module_role_explicit_deny`;
CREATE TABLE `un_module_role_explicit_deny` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `role_id` BIGINT UNSIGNED NOT NULL COMMENT '系统角色 ID。',
  `deny_type` VARCHAR(32) NOT NULL COMMENT '禁用类型：MENU、OPERATION、FIELD_READ、FIELD_WRITE、DATA_SCOPE、EXPORT、OPENAPI_SCOPE。',
  `target_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '禁用对象 ID；0 表示按编码禁用。',
  `target_code` VARCHAR(128) NULL DEFAULT NULL COMMENT '禁用对象编码。',
  `reason` VARCHAR(512) NULL DEFAULT NULL COMMENT '禁用原因。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_role_deny` (`role_id`, `deny_type`, `target_id`, `target_code`, `delete_token`),
  KEY `idx_module_role_deny_system` (`system_id`, `deny_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='显式禁用权限项，优先于授权并集。';

DROP TABLE IF EXISTS `un_module_permission_version`;
CREATE TABLE `un_module_permission_version` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户归属；0 表示系统级。',
  `version_no` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '权限缓存版本号。',
  `changed_reason` VARCHAR(128) NULL DEFAULT NULL COMMENT '最近变更原因，如 ROLE_AUTH_SAVE。',
  `changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近变更时间。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_perm_version` (`system_id`, `tenant_id`, `delete_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限缓存版本。';

DROP TABLE IF EXISTS `un_module_dict_type`;
CREATE TABLE `un_module_dict_type` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `scope_type` VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT '作用域：SYSTEM、TENANT。',
  `scope_tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '作用域租户 ID；SYSTEM 为 0，TENANT 必须为有效租户 ID。',
  `code` VARCHAR(64) NOT NULL COMMENT '字典类型编码，同作用域唯一。',
  `name` VARCHAR(128) NOT NULL COMMENT '字典类型名称。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '描述。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED、DELETED。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `system_built_in` TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置只读。',
  `cache_version` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '字典缓存版本；写操作成功后递增。',
  `item_count` INT NOT NULL DEFAULT 0 COMMENT '字典项数量冗余。',
  `enabled_item_count` INT NOT NULL DEFAULT 0 COMMENT '启用字典项数量冗余。',
  `referenced_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否存在字段或记录引用。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_dict_type_scope` (`system_id`, `scope_type`, `scope_tenant_id`, `code`, `delete_token`),
  KEY `idx_module_dict_type_status` (`system_id`, `status`),
  KEY `idx_module_dict_type_tenant` (`system_id`, `scope_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统级或租户级字典类型。';

DROP TABLE IF EXISTS `un_module_dict_item`;
CREATE TABLE `un_module_dict_item` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID，冗余用于隔离和索引。',
  `dict_type_id` BIGINT UNSIGNED NOT NULL COMMENT '字典类型 ID。',
  `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父字典项 ID，0 表示根项。',
  `code` VARCHAR(64) NOT NULL COMMENT '字典项编码，同父级唯一。',
  `label` VARCHAR(128) NOT NULL COMMENT '展示文本。',
  `value` VARCHAR(128) NOT NULL COMMENT '业务值，同父级唯一。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '描述。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED、DELETED。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `depth_level` INT NOT NULL DEFAULT 1 COMMENT '层级，最大 5。',
  `depth_path` VARCHAR(512) NOT NULL DEFAULT '/' COMMENT '路径，形如 /rootId/childId。',
  `leaf_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否叶子节点。',
  `system_built_in` TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置只读。',
  `referenced_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否被记录值引用。',
  `ext_json` JSON NULL DEFAULT NULL COMMENT '扩展信息，不允许存敏感信息。',
  `cache_version` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '冗余字典类型缓存版本，便于返回和测试断言。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_dict_item_code` (`dict_type_id`, `parent_id`, `code`, `delete_token`),
  UNIQUE KEY `uk_module_dict_item_value` (`dict_type_id`, `parent_id`, `value`, `delete_token`),
  KEY `idx_module_dict_item_tree` (`dict_type_id`, `parent_id`, `sort_order`),
  KEY `idx_module_dict_item_status` (`system_id`, `status`),
  KEY `idx_module_dict_item_path` (`dict_type_id`, `depth_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项，支持层级和内置只读。';

DROP TABLE IF EXISTS `un_module_dict_reference`;
CREATE TABLE `un_module_dict_reference` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统 ID。',
  `dict_type_id` BIGINT UNSIGNED NOT NULL COMMENT '字典类型 ID。',
  `dict_item_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '字典项 ID；0 表示类型级引用。',
  `reference_type` VARCHAR(32) NOT NULL COMMENT '引用类型：FIELD_CONFIG、PUBLISHED_FIELD、RECORD_VALUE。',
  `module_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '模块 ID。',
  `field_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '字段 ID。',
  `field_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '字段编码快照。',
  `published_version_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '发布版本 ID，逻辑引用 DBA-003。',
  `record_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '记录 ID，逻辑引用 DBA-003。',
  `usage_count` BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '引用数量摘要；记录值批量统计可累加。',
  `active_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '当前是否有效引用。',
  `delete_token` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Soft delete unique reuse marker; active rows use 0.',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_dict_ref_unique` (`dict_type_id`, `dict_item_id`, `reference_type`, `module_id`, `field_id`, `published_version_id`, `record_id`, `delete_token`),
  KEY `idx_module_dict_ref_type` (`dict_type_id`, `reference_type`, `active_flag`),
  KEY `idx_module_dict_ref_item` (`dict_item_id`, `reference_type`, `active_flag`),
  KEY `idx_module_dict_ref_field` (`module_id`, `field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典被字段、发布版本、记录值引用的摘要。';

DROP TABLE IF EXISTS `un_module_app`;
CREATE TABLE `un_module_app` (
  `app_id` BIGINT UNSIGNED NOT NULL COMMENT '应用 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户；单租户系统使用默认租户。',
  `name` VARCHAR(128) NOT NULL COMMENT '应用名称。',
  `code` VARCHAR(64) NOT NULL COMMENT '应用编码，同系统同租户唯一。',
  `icon` VARCHAR(128) NULL DEFAULT NULL COMMENT '应用图标。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '应用描述。',
  `app_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '应用状态。',
  `current_app_version_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '当前应用版本。',
  `module_count` INT NOT NULL DEFAULT 0 COMMENT '模块数量冗余，用于列表展示。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记；未删除固定为 0，删除后写入应用 ID 或删除批次。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`app_id`),
  UNIQUE KEY `uk_module_app_code` (`system_id`, `tenant_id`, `code`, `delete_marker`),
  KEY `idx_module_app_system_status` (`system_id`, `tenant_id`, `app_status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统/租户下业务应用主表。';

DROP TABLE IF EXISTS `un_module_app_version`;
CREATE TABLE `un_module_app_version` (
  `app_version_id` BIGINT UNSIGNED NOT NULL COMMENT '应用版本 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `app_id` BIGINT UNSIGNED NOT NULL COMMENT '应用 ID。',
  `version_no` INT NOT NULL COMMENT '应用版本号，按应用递增。',
  `version_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '版本名称。',
  `publish_status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '版本状态。',
  `snapshot_json` JSON NOT NULL COMMENT '应用、模块、菜单和发布模块摘要快照。',
  `publish_remark` VARCHAR(512) NULL DEFAULT NULL COMMENT '发布说明。',
  `published_by` BIGINT UNSIGNED NOT NULL COMMENT '发布成员 ID。',
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`app_version_id`),
  UNIQUE KEY `uk_module_app_version` (`app_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用级配置版本和发布快照摘要。';

DROP TABLE IF EXISTS `un_module_model`;
CREATE TABLE `un_module_model` (
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '模块 ID，对应 API moduleId。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `app_id` BIGINT UNSIGNED NOT NULL COMMENT '所属应用。',
  `name` VARCHAR(128) NOT NULL COMMENT '模块名称。',
  `code` VARCHAR(64) NOT NULL COMMENT '模块编码，同应用下唯一。',
  `description` VARCHAR(512) NULL DEFAULT NULL COMMENT '模块描述。',
  `module_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '模块状态。',
  `current_publish_version_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '当前运行态发布版本。',
  `flow_binding_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '流程绑定逻辑引用，实际流程表由 DBA-004 设计。',
  `title_field_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '记录标题字段。',
  `record_no_field_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '记录编号字段，可为空。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`module_id`),
  UNIQUE KEY `uk_module_model_code` (`system_id`, `tenant_id`, `app_id`, `code`, `delete_marker`),
  KEY `idx_module_model_app_status` (`app_id`, `module_status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态模块主表。';

DROP TABLE IF EXISTS `un_module_field`;
CREATE TABLE `un_module_field` (
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '字段 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `name` VARCHAR(128) NOT NULL COMMENT '字段名称。',
  `code` VARCHAR(64) NOT NULL COMMENT '字段编码，同模块唯一。',
  `field_type` VARCHAR(32) NOT NULL COMMENT '字段类型。',
  `required_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填。',
  `unique_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否字段级唯一。',
  `index_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否生成查询索引。',
  `default_value_json` JSON NULL DEFAULT NULL COMMENT '默认值。',
  `dict_type_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '字典类型引用，由 DBA-002 字典表提供。',
  `relation_config_json` JSON NULL DEFAULT NULL COMMENT '关联模块、展示字段和数据范围配置。',
  `sub_table_config_json` JSON NULL DEFAULT NULL COMMENT '子表列定义。',
  `serial_config_json` JSON NULL DEFAULT NULL COMMENT '自动编号规则。',
  `validation_json` JSON NULL DEFAULT NULL COMMENT '长度、范围、格式等校验。',
  `display_config_json` JSON NULL DEFAULT NULL COMMENT '前端展示和格式化配置。',
  `field_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '字段状态。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '字段排序。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`field_id`),
  UNIQUE KEY `uk_module_field_code` (`module_id`, `code`, `delete_marker`),
  KEY `idx_module_field_module_status` (`module_id`, `field_status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块字段定义、校验、唯一、关联、子表和自动编号配置。';

DROP TABLE IF EXISTS `un_module_field_option`;
CREATE TABLE `un_module_field_option` (
  `option_id` BIGINT UNSIGNED NOT NULL COMMENT '选项 ID。',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '字段 ID。',
  `code` VARCHAR(64) NOT NULL COMMENT '选项编码。',
  `label` VARCHAR(128) NOT NULL COMMENT '展示文本。',
  `value` VARCHAR(128) NOT NULL COMMENT '选项值。',
  `color` VARCHAR(32) NULL DEFAULT NULL COMMENT '颜色标识。',
  `enabled_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`option_id`),
  UNIQUE KEY `uk_module_field_option_code` (`field_id`, `code`, `delete_marker`),
  UNIQUE KEY `uk_module_field_option_value` (`field_id`, `value`, `delete_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单选、多选、标签等字段静态选项。';

DROP TABLE IF EXISTS `un_module_unique_constraint`;
CREATE TABLE `un_module_unique_constraint` (
  `constraint_id` BIGINT UNSIGNED NOT NULL COMMENT '组合唯一约束 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `constraint_code` VARCHAR(64) NOT NULL COMMENT '约束编码。',
  `constraint_name` VARCHAR(128) NOT NULL COMMENT '约束名称。',
  `field_ids_json` JSON NOT NULL COMMENT '参与唯一的字段 ID 数组，顺序固定。',
  `field_codes_json` JSON NOT NULL COMMENT '参与唯一的字段编码数组，便于快照和错误返回。',
  `enabled_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`constraint_id`),
  UNIQUE KEY `uk_module_unique_constraint_code` (`module_id`, `constraint_code`, `delete_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组合唯一约束配置。';

DROP TABLE IF EXISTS `un_module_page_schema`;
CREATE TABLE `un_module_page_schema` (
  `schema_id` BIGINT UNSIGNED NOT NULL COMMENT '页面 schema ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `page_type` VARCHAR(32) NOT NULL COMMENT 'LIST、FORM、DETAIL。',
  `schema_code` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT 'schema 编码。',
  `schema_name` VARCHAR(128) NOT NULL COMMENT 'schema 名称。',
  `schema_json` JSON NOT NULL COMMENT '列表列、筛选、排序、表单分区或详情区块配置。',
  `draft_version` INT NOT NULL DEFAULT 1 COMMENT '草稿版本号。',
  `schema_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿状态。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`schema_id`),
  UNIQUE KEY `uk_module_page_schema` (`module_id`, `page_type`, `schema_code`, `delete_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='列表、表单、详情 schema 草稿。';

DROP TABLE IF EXISTS `un_module_menu`;
CREATE TABLE `un_module_menu` (
  `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '菜单 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户；系统级菜单可为空。',
  `parent_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '父菜单。',
  `app_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '应用入口。',
  `module_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '模块入口。',
  `code` VARCHAR(64) NOT NULL COMMENT '菜单编码。',
  `name` VARCHAR(128) NOT NULL COMMENT '菜单名称。',
  `menu_type` VARCHAR(32) NOT NULL DEFAULT 'RUNTIME' COMMENT '菜单类型，如 CONFIG、RUNTIME。',
  `route_path` VARCHAR(256) NULL DEFAULT NULL COMMENT '前端路由。',
  `icon` VARCHAR(128) NULL DEFAULT NULL COMMENT '图标。',
  `visible_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可见。',
  `enabled_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`menu_id`),
  UNIQUE KEY `uk_module_menu_code` (`system_id`, `parent_id`, `code`, `delete_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行菜单和模块入口配置。';

DROP TABLE IF EXISTS `un_module_action`;
CREATE TABLE `un_module_action` (
  `action_id` BIGINT UNSIGNED NOT NULL COMMENT '动作 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `action_code` VARCHAR(64) NOT NULL COMMENT '动作编码，如 RECORD_CREATE、RECORD_EXPORT。',
  `action_name` VARCHAR(128) NOT NULL COMMENT '动作名称。',
  `action_type` VARCHAR(32) NOT NULL COMMENT 'BUTTON、ROW、DETAIL、EXPORT、FLOW。',
  `danger_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否危险操作。',
  `confirm_required` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要确认。',
  `enabled_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用。',
  `config_json` JSON NULL DEFAULT NULL COMMENT '前端按钮、状态规则和权限提示配置。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`action_id`),
  UNIQUE KEY `uk_module_action_code` (`module_id`, `action_code`, `delete_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块按钮、行操作、详情操作和导出入口动作。';

DROP TABLE IF EXISTS `un_module_publish_version`;
CREATE TABLE `un_module_publish_version` (
  `publish_version_id` BIGINT UNSIGNED NOT NULL COMMENT '发布版本 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `app_id` BIGINT UNSIGNED NOT NULL COMMENT '应用 ID。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '模块 ID。',
  `version_no` INT NOT NULL COMMENT '模块发布版本号。',
  `publish_status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT '发布状态。',
  `field_snapshot_json` JSON NOT NULL COMMENT '字段定义、字段权限基础信息、选项和唯一配置快照。',
  `page_snapshot_json` JSON NOT NULL COMMENT '列表、表单、详情 schema 快照。',
  `menu_action_snapshot_json` JSON NOT NULL COMMENT '菜单和动作配置快照。',
  `flow_binding_snapshot_json` JSON NULL DEFAULT NULL COMMENT '流程绑定快照。',
  `export_template_snapshot_json` JSON NULL DEFAULT NULL COMMENT '可用导出模板摘要快照。',
  `check_result_json` JSON NOT NULL COMMENT '发布检查结果。',
  `publish_remark` VARCHAR(512) NULL DEFAULT NULL COMMENT '发布说明。',
  `published_by` BIGINT UNSIGNED NOT NULL COMMENT '发布成员 ID。',
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`publish_version_id`),
  UNIQUE KEY `uk_module_publish_version` (`module_id`, `version_no`),
  KEY `idx_module_publish_current` (`module_id`, `publish_status`, `published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块发布版本快照，运行态只读。';

DROP TABLE IF EXISTS `un_module_serial_sequence`;
CREATE TABLE `un_module_serial_sequence` (
  `sequence_id` BIGINT UNSIGNED NOT NULL COMMENT '序号规则 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '自动编号字段。',
  `scope_key` VARCHAR(128) NOT NULL DEFAULT 'GLOBAL' COMMENT '序号作用域，如年度、月份、租户、模块。',
  `prefix_snapshot` VARCHAR(128) NULL DEFAULT NULL COMMENT '当前前缀快照。',
  `current_value` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前最大序号。',
  `step_value` INT NOT NULL DEFAULT 1 COMMENT '步长。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，用于原子更新。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`sequence_id`),
  UNIQUE KEY `uk_module_serial_scope` (`system_id`, `tenant_id`, `module_id`, `field_id`, `scope_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动编号字段的事务内原子序号段。';

DROP TABLE IF EXISTS `un_module_record`;
CREATE TABLE `un_module_record` (
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '记录 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `app_id` BIGINT UNSIGNED NOT NULL COMMENT '所属应用。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `publish_version_id` BIGINT UNSIGNED NOT NULL COMMENT '创建或最后保存时使用的发布版本。',
  `record_no` VARCHAR(128) NULL DEFAULT NULL COMMENT '业务编号或自动编号。',
  `title` VARCHAR(256) NULL DEFAULT NULL COMMENT '列表展示标题冗余。',
  `record_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '业务记录状态。',
  `flow_status` VARCHAR(32) NULL DEFAULT NULL COMMENT '流程摘要状态。',
  `flow_instance_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '流程实例逻辑引用。',
  `locked_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否流程锁定。',
  `record_version` INT NOT NULL DEFAULT 0 COMMENT '记录乐观锁版本。',
  `active_unique_marker` VARCHAR(64) NOT NULL DEFAULT 'ACTIVE' COMMENT '唯一索引用记录状态标记；删除后写入记录 ID。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `deleted_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '删除成员 ID。',
  `deleted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '删除时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`record_id`),
  UNIQUE KEY `uk_module_record_no` (`system_id`, `tenant_id`, `module_id`, `record_no`, `active_unique_marker`),
  KEY `idx_module_record_query` (`system_id`, `tenant_id`, `module_id`, `record_status`, `updated_at`),
  KEY `idx_module_record_creator` (`module_id`, `created_by`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态业务记录主表。';

DROP TABLE IF EXISTS `un_module_record_value`;
CREATE TABLE `un_module_record_value` (
  `value_id` BIGINT UNSIGNED NOT NULL COMMENT '字段值 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '记录 ID。',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '字段 ID。',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码快照。',
  `field_type` VARCHAR(32) NOT NULL COMMENT '字段类型快照。',
  `row_key` VARCHAR(64) NOT NULL DEFAULT 'ROOT' COMMENT '主表字段为 ROOT，子表字段为子表行 ID。',
  `value_text` TEXT NULL DEFAULT NULL COMMENT '文本值或短展示值。',
  `value_number` DECIMAL(30,10) NULL DEFAULT NULL COMMENT '数值、金额。',
  `value_datetime` DATETIME(3) NULL DEFAULT NULL COMMENT '日期时间。',
  `value_date` DATE NULL DEFAULT NULL COMMENT '日期。',
  `value_bool` TINYINT NULL DEFAULT NULL COMMENT '开关值。',
  `value_json` JSON NULL DEFAULT NULL COMMENT '多选、附件、图片、关联、子表、地址、标签、JSON 原始值。',
  `display_value_json` JSON NULL DEFAULT NULL COMMENT '后端补齐的展示值。',
  `value_snapshot_json` JSON NULL DEFAULT NULL COMMENT '字典、成员、部门、文件名、关联标题等历史展示快照。',
  `value_hash` CHAR(64) NULL DEFAULT NULL COMMENT 'typed value hash，供唯一和变更比较。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`value_id`),
  UNIQUE KEY `uk_module_record_value` (`record_id`, `row_key`, `field_id`),
  KEY `idx_module_record_value_field` (`module_id`, `field_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='EAV 字段值 typed columns 和展示快照。';

DROP TABLE IF EXISTS `un_module_record_index`;
CREATE TABLE `un_module_record_index` (
  `index_id` BIGINT UNSIGNED NOT NULL COMMENT '索引值 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '记录 ID。',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '字段 ID。',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码快照。',
  `row_key` VARCHAR(64) NOT NULL DEFAULT 'ROOT' COMMENT '行标识。',
  `index_text` VARCHAR(512) NULL DEFAULT NULL COMMENT '文本查询索引。',
  `index_number` DECIMAL(30,10) NULL DEFAULT NULL COMMENT '数值索引。',
  `index_datetime` DATETIME(3) NULL DEFAULT NULL COMMENT '日期时间索引。',
  `index_date` DATE NULL DEFAULT NULL COMMENT '日期索引。',
  `index_bool` TINYINT NULL DEFAULT NULL COMMENT '开关索引。',
  `index_hash` CHAR(64) NULL DEFAULT NULL COMMENT 'IN、多值、关联等 hash 索引。',
  `record_status` VARCHAR(32) NOT NULL COMMENT '记录状态快照，用于过滤删除记录。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`index_id`),
  KEY `idx_module_index_text` (`module_id`, `field_id`, `index_text`),
  KEY `idx_module_index_number` (`module_id`, `field_id`, `index_number`),
  KEY `idx_module_index_datetime` (`module_id`, `field_id`, `index_datetime`),
  KEY `idx_module_index_hash` (`module_id`, `field_id`, `index_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态字段查询、排序、筛选 typed index。';

DROP TABLE IF EXISTS `un_module_record_unique_index`;
CREATE TABLE `un_module_record_unique_index` (
  `unique_index_id` BIGINT UNSIGNED NOT NULL COMMENT '唯一索引记录 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `constraint_code` VARCHAR(128) NOT NULL COMMENT '字段级唯一写 FIELD:{fieldId}，组合唯一写配置编码。',
  `field_ids_json` JSON NOT NULL COMMENT '参与唯一字段 ID。',
  `field_codes_json` JSON NOT NULL COMMENT '参与唯一字段编码。',
  `combined_value_hash` CHAR(64) NOT NULL COMMENT 'typed value 组合 hash。',
  `display_values_json` JSON NULL DEFAULT NULL COMMENT '冲突提示展示值。',
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '记录 ID。',
  `active_unique_marker` VARCHAR(64) NOT NULL DEFAULT 'ACTIVE' COMMENT '非删除记录为 ACTIVE，记录软删除后写入记录 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`unique_index_id`),
  UNIQUE KEY `uk_module_record_unique` (`system_id`, `tenant_id`, `module_id`, `constraint_code`, `combined_value_hash`, `active_unique_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字段级唯一和组合唯一 typed hash 索引。';

DROP TABLE IF EXISTS `un_module_record_relation`;
CREATE TABLE `un_module_record_relation` (
  `relation_id` BIGINT UNSIGNED NOT NULL COMMENT '关联关系 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `source_module_id` BIGINT UNSIGNED NOT NULL COMMENT '来源模块。',
  `source_record_id` BIGINT UNSIGNED NOT NULL COMMENT '来源记录。',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '关联字段。',
  `row_key` VARCHAR(64) NOT NULL DEFAULT 'ROOT' COMMENT '主表或子表行标识。',
  `target_module_id` BIGINT UNSIGNED NOT NULL COMMENT '目标模块。',
  `target_record_id` BIGINT UNSIGNED NOT NULL COMMENT '目标记录。',
  `relation_type` VARCHAR(32) NOT NULL DEFAULT 'FIELD_RELATION' COMMENT '关系类型。',
  `display_snapshot_json` JSON NULL DEFAULT NULL COMMENT '目标记录标题等展示快照。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uk_module_relation` (`source_record_id`, `field_id`, `row_key`, `target_record_id`, `delete_marker`),
  KEY `idx_module_relation_target` (`target_module_id`, `target_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关联字段保存的记录间关系。';

DROP TABLE IF EXISTS `un_module_record_child_row`;
CREATE TABLE `un_module_record_child_row` (
  `child_row_id` BIGINT UNSIGNED NOT NULL COMMENT '子表行 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '主记录 ID。',
  `parent_field_id` BIGINT UNSIGNED NOT NULL COMMENT '子表字段 ID。',
  `row_key` VARCHAR(64) NOT NULL COMMENT '行 key，写入 un_module_record_value.row_key。',
  `row_order` INT NOT NULL DEFAULT 0 COMMENT '行顺序。',
  `row_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、DELETED。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`child_row_id`),
  UNIQUE KEY `uk_module_child_row` (`record_id`, `parent_field_id`, `row_key`, `delete_marker`),
  KEY `idx_module_child_record` (`record_id`, `parent_field_id`, `row_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='子表字段行数据和行顺序。';

DROP TABLE IF EXISTS `un_module_record_history`;
CREATE TABLE `un_module_record_history` (
  `history_id` BIGINT UNSIGNED NOT NULL COMMENT '历史 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '记录 ID。',
  `record_version` INT NOT NULL COMMENT '变更后的记录版本。',
  `publish_version_id` BIGINT UNSIGNED NOT NULL COMMENT '本次变更解释使用的发布版本。',
  `operation_type` VARCHAR(32) NOT NULL COMMENT 'CREATE、UPDATE、DELETE、SUBMIT、FLOW_UPDATE、RESTORE。',
  `before_status` VARCHAR(32) NULL DEFAULT NULL COMMENT '变更前状态。',
  `after_status` VARCHAR(32) NOT NULL COMMENT '变更后状态。',
  `changed_fields_json` JSON NOT NULL COMMENT '变更字段编码数组。',
  `before_snapshot_json` JSON NULL DEFAULT NULL COMMENT '变更前字段值和展示快照。',
  `after_snapshot_json` JSON NOT NULL COMMENT '变更后字段值、附件和展示快照。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `operator_member_id` BIGINT UNSIGNED NOT NULL COMMENT '操作成员 ID。',
  `remark` VARCHAR(512) NULL DEFAULT NULL COMMENT '保存备注或系统说明。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_module_record_history` (`record_id`, `record_version`, `operation_type`),
  KEY `idx_module_history_record` (`record_id`, `created_at`),
  KEY `idx_module_history_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录变更、状态、附件和发布版本历史快照。';

DROP TABLE IF EXISTS `un_module_export_template`;
CREATE TABLE `un_module_export_template` (
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '导出模板 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块。',
  `template_code` VARCHAR(64) NOT NULL COMMENT '模板编码。',
  `template_name` VARCHAR(128) NOT NULL COMMENT '模板名称。',
  `template_status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '模板状态。',
  `file_name_pattern` VARCHAR(256) NULL DEFAULT NULL COMMENT '结果文件名规则。',
  `export_format` VARCHAR(32) NOT NULL DEFAULT 'XLSX' COMMENT '导出格式，MVP 默认 XLSX。',
  `include_history_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否导出历史，MVP 默认否。',
  `config_json` JSON NULL DEFAULT NULL COMMENT '表头、冻结列、样式等配置。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `delete_marker` VARCHAR(64) NOT NULL DEFAULT '0' COMMENT '软删除唯一复用标记。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新成员 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_module_export_template_code` (`system_id`, `tenant_id`, `module_id`, `template_code`, `delete_marker`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出模板主表。';

DROP TABLE IF EXISTS `un_module_export_template_field`;
CREATE TABLE `un_module_export_template_field` (
  `template_field_id` BIGINT UNSIGNED NOT NULL COMMENT '模板字段 ID。',
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '模板 ID。',
  `field_id` BIGINT UNSIGNED NOT NULL COMMENT '字段 ID。',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码快照。',
  `header_name` VARCHAR(128) NOT NULL COMMENT '导出表头。',
  `column_order` INT NOT NULL DEFAULT 0 COMMENT '列顺序。',
  `plain_required_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否要求明文导出权限。',
  `mask_strategy` VARCHAR(64) NULL DEFAULT NULL COMMENT '脱敏策略编码。',
  `format_json` JSON NULL DEFAULT NULL COMMENT '日期、金额、字典展示格式。',
  `enabled_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`template_field_id`),
  UNIQUE KEY `uk_module_export_template_field` (`template_id`, `field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出模板字段列、顺序和脱敏配置。';

DROP TABLE IF EXISTS `un_module_export_job`;
CREATE TABLE `un_module_export_job` (
  `job_id` BIGINT UNSIGNED NOT NULL COMMENT '导出任务 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '导出模块。',
  `template_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '导出模板；允许使用默认模板时为空。',
  `publish_version_id` BIGINT UNSIGNED NOT NULL COMMENT '创建任务时的发布版本。',
  `job_status` VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT '导出任务状态。',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '进度 0-100。',
  `selected_record_ids_json` JSON NULL DEFAULT NULL COMMENT '选中记录 ID 快照；优先于筛选条件。',
  `filter_snapshot_json` JSON NOT NULL COMMENT '筛选条件快照。',
  `sorter_snapshot_json` JSON NULL DEFAULT NULL COMMENT '排序快照。',
  `field_snapshot_json` JSON NOT NULL COMMENT '导出字段和发布字段定义快照。',
  `permission_snapshot_json` JSON NOT NULL COMMENT '字段可见、导出明文、操作权限、数据范围和脱敏权限快照。',
  `data_scope_snapshot_json` JSON NOT NULL COMMENT '数据范围命中规则快照。',
  `file_name` VARCHAR(256) NULL DEFAULT NULL COMMENT '结果文件名。',
  `result_file_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '导出结果文件逻辑引用，对应 un_upload_ 文件主表。',
  `failure_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '失败错误码。',
  `failure_message` VARCHAR(512) NULL DEFAULT NULL COMMENT '失败提示。',
  `failure_snapshot_json` JSON NULL DEFAULT NULL COMMENT 'failureReason，含 retryable、stackSummary、failedAt。',
  `retryable_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否可重试。',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数。',
  `max_retry_count` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数。',
  `claimed_by` VARCHAR(128) NULL DEFAULT NULL COMMENT '后台 runner 标识。',
  `claimed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '领取时间。',
  `started_at` DATETIME(3) NULL DEFAULT NULL COMMENT '开始处理时间。',
  `finished_at` DATETIME(3) NULL DEFAULT NULL COMMENT '完成时间。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '创建任务请求 ID。',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键。',
  `request_hash` CHAR(64) NOT NULL COMMENT '请求摘要。',
  `created_by` BIGINT UNSIGNED NOT NULL COMMENT '创建成员 ID。',
  `version` INT NOT NULL DEFAULT 0 COMMENT '任务领取和状态流转乐观锁。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`job_id`),
  UNIQUE KEY `uk_module_export_idempotency` (`system_id`, `created_by`, `idempotency_key`),
  KEY `idx_module_export_job_status` (`job_status`, `created_at`),
  KEY `idx_module_export_job_creator` (`system_id`, `created_by`, `created_at`),
  KEY `idx_module_export_job_result` (`result_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出任务、筛选快照、权限快照、结果文件引用和重试状态。';

DROP TABLE IF EXISTS `un_module_export_job_log`;
CREATE TABLE `un_module_export_job_log` (
  `log_id` BIGINT UNSIGNED NOT NULL COMMENT '日志 ID。',
  `job_id` BIGINT UNSIGNED NOT NULL COMMENT '导出任务 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户。',
  `log_type` VARCHAR(32) NOT NULL COMMENT '日志类型。',
  `from_status` VARCHAR(32) NULL DEFAULT NULL COMMENT '变更前状态。',
  `to_status` VARCHAR(32) NOT NULL COMMENT '变更后状态。',
  `message` VARCHAR(512) NULL DEFAULT NULL COMMENT '日志说明。',
  `snapshot_json` JSON NULL DEFAULT NULL COMMENT '领取、失败、重试等上下文快照。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求或后台任务追踪 ID。',
  `operator_id` VARCHAR(128) NULL DEFAULT NULL COMMENT '操作成员 ID或 runner 标识。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  `id` BIGINT UNSIGNED NOT NULL COMMENT '主键，雪花 ID 或等价全局唯一 ID。',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0-正常，1-删除。',
  `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人平台账号或系统成员 ID，按业务上下文解释。',
  `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人平台账号或系统成员 ID。',
  PRIMARY KEY (`log_id`),
  KEY `idx_module_export_job_log` (`job_id`, `created_at`),
  KEY `idx_module_export_log_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出任务状态流转、领取、失败和重试日志。';

DROP TABLE IF EXISTS `un_flow_template`;
CREATE TABLE `un_flow_template` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `code` VARCHAR(64) NOT NULL COMMENT '流程模板编码，同系统租户内唯一。',
  `name` VARCHAR(128) NOT NULL COMMENT '流程模板名称。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、PUBLISHED、DISABLED。',
  `current_version_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '当前发布版本 ID。',
  `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '模板说明。',
  `version_no` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_template_code` (`system_id`, `tenant_id`, `code`, `deleted`),
  KEY `idx_flow_template_system_status` (`system_id`, `tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程模板主表。';

DROP TABLE IF EXISTS `un_flow_template_version`;
CREATE TABLE `un_flow_template_version` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '流程模板 ID。',
  `version_no` INT NOT NULL COMMENT '发布版本号，同模板递增。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED、DISCARDED。',
  `publish_comment` VARCHAR(500) NULL DEFAULT NULL COMMENT '发布说明。',
  `graph_snapshot_json` JSON NOT NULL COMMENT '发布时流程图完整快照，用于历史解释。',
  `check_result_json` JSON NULL DEFAULT NULL COMMENT '发布检查结果快照。',
  `published_by` BIGINT UNSIGNED NOT NULL COMMENT '发布人系统成员 ID。',
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_template_version` (`template_id`, `version_no`),
  KEY `idx_flow_version_template_status` (`template_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程发布版本和结构快照。';

DROP TABLE IF EXISTS `un_flow_template_node`;
CREATE TABLE `un_flow_template_node` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `template_version_id` BIGINT UNSIGNED NOT NULL COMMENT '流程发布版本 ID。',
  `node_key` VARCHAR(64) NOT NULL COMMENT '节点稳定编码，版本内唯一。',
  `node_name` VARCHAR(128) NOT NULL COMMENT '节点名称。',
  `node_type` VARCHAR(32) NOT NULL COMMENT 'START、APPROVAL、CC、END。',
  `actor_strategy` VARCHAR(32) NULL DEFAULT NULL COMMENT '审批人策略，如 ROLE、MEMBER、DEPT_MANAGER、INITIATOR。',
  `actor_config_json` JSON NULL DEFAULT NULL COMMENT '候选人配置快照。',
  `approval_required` TINYINT NOT NULL DEFAULT 1 COMMENT '是否需要审批意见。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '图中排序。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_key` (`template_version_id`, `node_key`),
  KEY `idx_flow_node_version` (`template_version_id`, `node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发布版本内节点结构。';

DROP TABLE IF EXISTS `un_flow_template_line`;
CREATE TABLE `un_flow_template_line` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `template_version_id` BIGINT UNSIGNED NOT NULL COMMENT '流程发布版本 ID。',
  `line_key` VARCHAR(64) NOT NULL COMMENT '连线稳定编码，版本内唯一。',
  `from_node_key` VARCHAR(64) NOT NULL COMMENT '起点节点编码。',
  `to_node_key` VARCHAR(64) NOT NULL COMMENT '终点节点编码。',
  `condition_mode` VARCHAR(32) NOT NULL DEFAULT 'ALWAYS' COMMENT 'ALWAYS、EXPRESSION。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '条件匹配顺序。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_line_key` (`template_version_id`, `line_key`),
  KEY `idx_flow_line_from` (`template_version_id`, `from_node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发布版本内连线结构。';

DROP TABLE IF EXISTS `un_flow_template_condition`;
CREATE TABLE `un_flow_template_condition` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `line_id` BIGINT UNSIGNED NOT NULL COMMENT '连线 ID。',
  `field_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '动态字段编码。',
  `operator` VARCHAR(32) NOT NULL COMMENT '条件操作符，如 EQ、NE、GT、IN、EMPTY。',
  `compare_value_json` JSON NULL DEFAULT NULL COMMENT '比较值快照。',
  `expression_json` JSON NULL DEFAULT NULL COMMENT '复杂表达式结构。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '条件排序。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_flow_condition_line` (`line_id`),
  KEY `idx_flow_condition_field` (`system_id`, `field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连线条件表达式结构化存储。';

DROP TABLE IF EXISTS `un_flow_binding`;
CREATE TABLE `un_flow_binding` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '动态模块 ID，逻辑关联 un_module_。',
  `action_code` VARCHAR(64) NOT NULL DEFAULT 'SUBMIT' COMMENT '触发动作，MVP 为提交审批。',
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '流程模板 ID。',
  `template_version_id` BIGINT UNSIGNED NOT NULL COMMENT '绑定的发布版本 ID。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED、DISABLED。',
  `version_no` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_binding_module_action` (`system_id`, `tenant_id`, `module_id`, `action_code`, `deleted`),
  KEY `idx_flow_binding_version` (`template_version_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块提交动作与流程版本绑定。';

DROP TABLE IF EXISTS `un_flow_instance`;
CREATE TABLE `un_flow_instance` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '动态模块 ID。',
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '业务记录 ID。',
  `template_id` BIGINT UNSIGNED NOT NULL COMMENT '流程模板 ID。',
  `template_version_id` BIGINT UNSIGNED NOT NULL COMMENT '发起时发布版本 ID。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'IN_APPROVAL' COMMENT 'IN_APPROVAL、APPROVED、REJECTED、WITHDRAWN、TERMINATED。',
  `starter_member_id` BIGINT UNSIGNED NOT NULL COMMENT '发起人系统成员 ID。',
  `current_node_keys` VARCHAR(500) NULL DEFAULT NULL COMMENT '当前活跃节点编码列表。',
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发起时间。',
  `finished_at` DATETIME(3) NULL DEFAULT NULL COMMENT '结束时间。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '发起请求 requestId。',
  `version_no` INT NOT NULL DEFAULT 0 COMMENT '实例乐观锁版本。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_flow_instance_record` (`system_id`, `tenant_id`, `module_id`, `record_id`),
  KEY `idx_flow_instance_status` (`system_id`, `tenant_id`, `status`, `updated_at`),
  KEY `idx_flow_instance_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录发起后的流程实例。';

DROP TABLE IF EXISTS `un_flow_task`;
CREATE TABLE `un_flow_task` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '流程实例 ID。',
  `node_key` VARCHAR(64) NOT NULL COMMENT '当前节点编码。',
  `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、DONE、CANCELED、TRANSFERRED、RETURNED。',
  `claim_member_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '领取人系统成员 ID。',
  `handler_member_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '实际处理人系统成员 ID。',
  `due_at` DATETIME(3) NULL DEFAULT NULL COMMENT '到期时间。',
  `claimed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '领取时间。',
  `handled_at` DATETIME(3) NULL DEFAULT NULL COMMENT '处理时间。',
  `task_version` INT NOT NULL DEFAULT 0 COMMENT '任务并发控制版本。',
  `idempotency_key` VARCHAR(128) NULL DEFAULT NULL COMMENT '最近一次处理幂等键。',
  `request_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '最近一次处理 requestId。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_todo` (`system_id`, `tenant_id`, `status`, `updated_at`),
  KEY `idx_flow_task_instance` (`instance_id`, `node_key`),
  KEY `idx_flow_task_handler` (`handler_member_id`, `status`, `handled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待办任务、领取、处理和并发版本控制。';

DROP TABLE IF EXISTS `un_flow_task_actor`;
CREATE TABLE `un_flow_task_actor` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `task_id` BIGINT UNSIGNED NOT NULL COMMENT '流程任务 ID。',
  `actor_member_id` BIGINT UNSIGNED NOT NULL COMMENT '候选或处理成员 ID。',
  `actor_type` VARCHAR(32) NOT NULL DEFAULT 'CANDIDATE' COMMENT 'CANDIDATE、CLAIMER、HANDLER、TRANSFER_TARGET。',
  `source_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '来源，如角色、部门、成员、发起人。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、INACTIVE。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_task_actor` (`task_id`, `actor_member_id`, `actor_type`),
  KEY `idx_flow_actor_member` (`system_id`, `actor_member_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务候选人、处理人和转交目标。';

DROP TABLE IF EXISTS `un_flow_cc`;
CREATE TABLE `un_flow_cc` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '流程实例 ID。',
  `task_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '来源任务 ID。',
  `cc_member_id` BIGINT UNSIGNED NOT NULL COMMENT '抄送成员 ID。',
  `read_status` VARCHAR(32) NOT NULL DEFAULT 'UNREAD' COMMENT 'UNREAD、READ。',
  `read_at` DATETIME(3) NULL DEFAULT NULL COMMENT '已读时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_cc_once` (`instance_id`, `task_id`, `cc_member_id`),
  KEY `idx_flow_cc_member` (`system_id`, `cc_member_id`, `read_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程抄送与已读状态。';

DROP TABLE IF EXISTS `un_flow_action_log`;
CREATE TABLE `un_flow_action_log` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '流程实例 ID。',
  `task_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '流程任务 ID。',
  `action` VARCHAR(32) NOT NULL COMMENT 'APPROVE、REJECT、TRANSFER、RETURN、TERMINATE、WITHDRAW、CLAIM、UNCLAIM。',
  `operator_member_id` BIGINT UNSIGNED NOT NULL COMMENT '操作成员 ID。',
  `comment` VARCHAR(1000) NULL DEFAULT NULL COMMENT '审批意见。',
  `from_node_key` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源节点。',
  `to_node_key` VARCHAR(64) NULL DEFAULT NULL COMMENT '目标节点。',
  `result_status` VARCHAR(32) NOT NULL COMMENT '操作后的实例或任务状态。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_flow_action_instance` (`instance_id`, `created_at`),
  KEY `idx_flow_action_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批动作日志。';

DROP TABLE IF EXISTS `un_flow_trace_log`;
CREATE TABLE `un_flow_trace_log` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `instance_id` BIGINT UNSIGNED NOT NULL COMMENT '流程实例 ID。',
  `from_node_key` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源节点。',
  `to_node_key` VARCHAR(64) NULL DEFAULT NULL COMMENT '目标节点。',
  `event_type` VARCHAR(32) NOT NULL COMMENT 'START、ENTER_NODE、LEAVE_NODE、FINISH、CANCEL。',
  `event_snapshot_json` JSON NULL DEFAULT NULL COMMENT '节点变量、候选人和条件命中快照。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_flow_trace_instance` (`instance_id`, `created_at`),
  KEY `idx_flow_trace_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程推进轨迹日志。';

DROP TABLE IF EXISTS `un_upload_storage_config`;
CREATE TABLE `un_upload_storage_config` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属系统；为空表示平台默认配置。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `code` VARCHAR(64) NOT NULL COMMENT '存储配置编码。',
  `name` VARCHAR(128) NOT NULL COMMENT '存储配置名称。',
  `storage_type` VARCHAR(32) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL、S3、MINIO、OSS。',
  `endpoint` VARCHAR(255) NULL DEFAULT NULL COMMENT '对象存储 endpoint。',
  `bucket_name` VARCHAR(128) NULL DEFAULT NULL COMMENT 'bucket。',
  `root_path` VARCHAR(500) NULL DEFAULT NULL COMMENT '本地或对象根路径。',
  `config_json` JSON NULL DEFAULT NULL COMMENT '非敏感配置。',
  `secret_ref` VARCHAR(255) NULL DEFAULT NULL COMMENT '密钥引用，不保存明文。',
  `default_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认配置。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED、DISABLED。',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_upload_storage_code` (`system_id`, `tenant_id`, `code`, `deleted`),
  KEY `idx_upload_storage_default` (`system_id`, `tenant_id`, `default_flag`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='存储配置和默认存储策略。';

DROP TABLE IF EXISTS `un_upload_file`;
CREATE TABLE `un_upload_file` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `storage_config_id` BIGINT UNSIGNED NOT NULL COMMENT '存储配置 ID。',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名。',
  `extension` VARCHAR(32) NULL DEFAULT NULL COMMENT '扩展名。',
  `content_type` VARCHAR(128) NULL DEFAULT NULL COMMENT 'MIME 类型。',
  `file_size` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小，字节。',
  `sha256` CHAR(64) NULL DEFAULT NULL COMMENT '文件内容 SHA-256。',
  `storage_key` VARCHAR(500) NOT NULL COMMENT '对象存储 key 或本地相对路径。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'TEMP' COMMENT 'TEMP、REFERENCED、DELETED、EXPIRED。',
  `previewable` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持预览。',
  `owner_member_id` BIGINT UNSIGNED NOT NULL COMMENT '上传人系统成员 ID。',
  `ref_count` INT NOT NULL DEFAULT 0 COMMENT '当前有效引用数。',
  `temp_expires_at` DATETIME(3) NULL DEFAULT NULL COMMENT '临时文件过期时间。',
  `deleted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '删除或过期时间。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '上传请求 requestId。',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_upload_file_system_status` (`system_id`, `tenant_id`, `status`, `created_at`),
  KEY `idx_upload_file_owner` (`system_id`, `owner_member_id`, `created_at`),
  KEY `idx_upload_file_temp_expire` (`status`, `temp_expires_at`),
  KEY `idx_upload_file_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据、临时状态、对象存储定位和安全属性。';

DROP TABLE IF EXISTS `un_upload_file_part`;
CREATE TABLE `un_upload_file_part` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `file_id` BIGINT UNSIGNED NOT NULL COMMENT '文件 ID。',
  `upload_id` VARCHAR(128) NOT NULL COMMENT '分片上传会话 ID。',
  `part_no` INT NOT NULL COMMENT '分片序号。',
  `part_size` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '分片大小。',
  `part_sha256` CHAR(64) NULL DEFAULT NULL COMMENT '分片 SHA-256。',
  `storage_etag` VARCHAR(255) NULL DEFAULT NULL COMMENT '对象存储 ETag。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED、MERGED、FAILED。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_upload_part` (`file_id`, `upload_id`, `part_no`),
  KEY `idx_upload_part_status` (`file_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分片上传预留表。';

DROP TABLE IF EXISTS `un_upload_file_reference`;
CREATE TABLE `un_upload_file_reference` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '所属租户。',
  `file_id` BIGINT UNSIGNED NOT NULL COMMENT '文件 ID。',
  `biz_type` VARCHAR(64) NOT NULL COMMENT '引用类型，如 MODULE_RECORD_FIELD、EXPORT_RESULT、FLOW_COMMENT。',
  `biz_id` BIGINT UNSIGNED NOT NULL COMMENT '业务对象 ID。',
  `module_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '动态模块 ID。',
  `record_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '业务记录 ID。',
  `field_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '附件字段编码。',
  `display_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '引用展示名。',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、UNBOUND。',
  `bound_by` BIGINT UNSIGNED NOT NULL COMMENT '绑定人系统成员 ID。',
  `bound_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定时间。',
  `unbound_at` DATETIME(3) NULL DEFAULT NULL COMMENT '解绑时间。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '绑定请求 requestId。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_upload_ref` (`file_id`, `biz_type`, `biz_id`, `field_code`, `status`),
  KEY `idx_upload_ref_biz` (`system_id`, `tenant_id`, `biz_type`, `biz_id`),
  KEY `idx_upload_ref_record` (`system_id`, `module_id`, `record_id`, `field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件与业务对象、动态字段、导出结果之间的引用关系。';

DROP TABLE IF EXISTS `un_openapi_client`;
CREATE TABLE `un_openapi_client` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '绑定系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '绑定租户。',
  `code` VARCHAR(64) NOT NULL COMMENT '客户端编码。',
  `name` VARCHAR(128) NOT NULL COMMENT '客户端名称。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、ENABLED、DISABLED、EXPIRED。',
  `data_scope_json` JSON NULL DEFAULT NULL COMMENT '客户端默认数据范围快照。',
  `rate_limit_policy_json` JSON NULL DEFAULT NULL COMMENT '管理端展示用限流策略快照。',
  `expires_at` DATETIME(3) NULL DEFAULT NULL COMMENT '客户端过期时间。',
  `last_used_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近调用时间。',
  `version_no` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本。',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_client_code` (`system_id`, `tenant_id`, `code`, `deleted`),
  KEY `idx_openapi_client_status` (`system_id`, `tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部客户端，绑定系统、租户和状态。';

DROP TABLE IF EXISTS `un_openapi_client_credential`;
CREATE TABLE `un_openapi_client_credential` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `client_id` BIGINT UNSIGNED NOT NULL COMMENT 'OpenAPI 客户端 ID。',
  `access_key` VARCHAR(128) NOT NULL COMMENT 'AK，外部请求传入。',
  `secret_hash` CHAR(64) NOT NULL COMMENT 'secret 哈希，用于辅助校验或审计，不保存明文。',
  `sign_secret_enc` VARCHAR(1000) NOT NULL COMMENT '签名密钥密文。',
  `masked_secret` VARCHAR(64) NOT NULL COMMENT '脱敏展示值。',
  `algorithm` VARCHAR(32) NOT NULL DEFAULT 'HMAC-SHA256' COMMENT '签名算法。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、EXPIRED、REVOKED。',
  `secret_visible_once` TINYINT NOT NULL DEFAULT 1 COMMENT 'secret 是否仍处于创建/轮换响应一次性展示窗口。',
  `issued_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '签发时间。',
  `expires_at` DATETIME(3) NULL DEFAULT NULL COMMENT '凭证过期时间。',
  `revoked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '吊销时间。',
  `last_used_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近使用时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_access_key` (`access_key`),
  KEY `idx_openapi_credential_client` (`client_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AK/SK 凭证、密钥密文、轮换和过期状态。';

DROP TABLE IF EXISTS `un_openapi_client_scope`;
CREATE TABLE `un_openapi_client_scope` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `client_id` BIGINT UNSIGNED NOT NULL COMMENT 'OpenAPI 客户端 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '绑定系统。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '绑定租户。',
  `scope_code` VARCHAR(64) NOT NULL COMMENT 'scope，如 record:read、record:create、flow:task:handle、file:download。',
  `module_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '限定动态模块。',
  `field_permission_json` JSON NULL DEFAULT NULL COMMENT '字段可读、可写授权。',
  `data_scope_json` JSON NULL DEFAULT NULL COMMENT '数据范围规则。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED、DISABLED。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_scope` (`client_id`, `scope_code`, `module_id`),
  KEY `idx_openapi_scope_system` (`system_id`, `tenant_id`, `scope_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='scope、模块、动作、字段读写权限和数据范围。';

DROP TABLE IF EXISTS `un_openapi_ip_whitelist`;
CREATE TABLE `un_openapi_ip_whitelist` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `client_id` BIGINT UNSIGNED NOT NULL COMMENT 'OpenAPI 客户端 ID。',
  `ip_rule` VARCHAR(64) NOT NULL COMMENT 'IP 或 CIDR。',
  `rule_type` VARCHAR(16) NOT NULL DEFAULT 'CIDR' COMMENT 'IP、CIDR。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED、DISABLED。',
  `description` VARCHAR(255) NULL DEFAULT NULL COMMENT '说明。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_ip_rule` (`client_id`, `ip_rule`),
  KEY `idx_openapi_ip_status` (`client_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP/CIDR 白名单。';

DROP TABLE IF EXISTS `un_openapi_nonce`;
CREATE TABLE `un_openapi_nonce` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `client_id` BIGINT UNSIGNED NOT NULL COMMENT 'OpenAPI 客户端 ID。',
  `access_key` VARCHAR(128) NOT NULL COMMENT 'AK。',
  `nonce` VARCHAR(128) NOT NULL COMMENT '请求 nonce。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `source_ip` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源 IP。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间，默认请求时间后 10 分钟且不小于时间窗口 2 倍。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_nonce` (`client_id`, `access_key`, `nonce`),
  KEY `idx_openapi_nonce_expire` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='nonce 去重和 TTL。';

DROP TABLE IF EXISTS `un_openapi_idempotency_record`;
CREATE TABLE `un_openapi_idempotency_record` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `client_id` BIGINT UNSIGNED NOT NULL COMMENT 'OpenAPI 客户端 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `api_id` VARCHAR(32) NOT NULL COMMENT 'API ID，如 OPN-003。',
  `biz_action` VARCHAR(64) NOT NULL COMMENT '业务动作。',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键。',
  `scope_key` VARCHAR(500) NOT NULL COMMENT '归一化幂等 scope。',
  `request_hash` CHAR(64) NOT NULL COMMENT '请求摘要 SHA-256。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING、SUCCESS、FAILED、CONFLICT。',
  `result_snapshot_json` JSON NULL DEFAULT NULL COMMENT '结果快照，含 code、success、data 标识、requestId。',
  `signature_result` VARCHAR(32) NULL DEFAULT NULL COMMENT '签名结果。',
  `scope_result` VARCHAR(32) NULL DEFAULT NULL COMMENT 'scope 命中结果。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '首次请求 requestId。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '幂等记录过期时间，默认 72 小时且不小于 24 小时。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_idempotency` (`scope_key`),
  KEY `idx_openapi_idempotency_expire` (`expires_at`),
  KEY `idx_openapi_idempotency_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部写接口幂等记录、请求摘要和结果快照。';

DROP TABLE IF EXISTS `un_openapi_rate_limit_policy`;
CREATE TABLE `un_openapi_rate_limit_policy` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `client_id` BIGINT UNSIGNED NOT NULL COMMENT 'OpenAPI 客户端 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `api_id` VARCHAR(32) NULL DEFAULT NULL COMMENT 'API ID；为空表示客户端默认策略。',
  `scope_code` VARCHAR(64) NULL DEFAULT NULL COMMENT 'scope；为空表示通用策略。',
  `source_ip` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源 IP 限定；为空表示不限定。',
  `window_seconds` INT NOT NULL DEFAULT 60 COMMENT '限流窗口秒数。',
  `max_requests` INT NOT NULL COMMENT '窗口最大请求数。',
  `burst` INT NOT NULL DEFAULT 0 COMMENT '突发额度。',
  `effective_from` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生效时间。',
  `effective_to` DATETIME(3) NULL DEFAULT NULL COMMENT '失效时间。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED、DISABLED。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_openapi_rate_policy` (`client_id`, `api_id`, `scope_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端限流策略。';

DROP TABLE IF EXISTS `un_openapi_rate_limit_counter`;
CREATE TABLE `un_openapi_rate_limit_counter` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `policy_id` BIGINT UNSIGNED NOT NULL COMMENT '限流策略 ID。',
  `dimension_key` VARCHAR(500) NOT NULL COMMENT 'clientId + systemId + tenantId + apiId + scopeCode + sourceIp。',
  `window_start_at` DATETIME(3) NOT NULL COMMENT '窗口开始时间。',
  `window_end_at` DATETIME(3) NOT NULL COMMENT '窗口结束时间。',
  `request_count` INT NOT NULL DEFAULT 0 COMMENT '当前窗口计数。',
  `last_request_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '最近一次 requestId。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_rate_counter` (`policy_id`, `dimension_key`, `window_start_at`),
  KEY `idx_openapi_rate_counter_window` (`window_end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='限流窗口计数。';

DROP TABLE IF EXISTS `un_openapi_access_log`;
CREATE TABLE `un_openapi_access_log` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `trace_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '链路追踪 ID。',
  `client_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '客户端 ID；AK 无效时可为空。',
  `access_key` VARCHAR(128) NULL DEFAULT NULL COMMENT 'AK，必要时脱敏。',
  `system_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `api_id` VARCHAR(32) NULL DEFAULT NULL COMMENT 'API ID。',
  `method` VARCHAR(16) NOT NULL COMMENT 'HTTP 方法。',
  `path` VARCHAR(500) NOT NULL COMMENT '请求路径。',
  `source_ip` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源 IP。',
  `body_hash` CHAR(64) NULL DEFAULT NULL COMMENT '请求 body SHA-256。',
  `signature_result` VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT 'PASS、FAIL、NOT_CHECKED。',
  `nonce_result` VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT 'PASS、REPLAY、NOT_CHECKED。',
  `scope_result` VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT 'PASS、DENIED、NOT_CHECKED。',
  `rate_limit_result` VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT 'PASS、LIMITED、NOT_CHECKED。',
  `idempotency_result` VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED' COMMENT 'NEW、REPLAY、CONFLICT、PROCESSING、NOT_CHECKED。',
  `result` VARCHAR(32) NOT NULL COMMENT 'SUCCESS、FAILED。',
  `http_status` INT NOT NULL COMMENT 'HTTP 状态码。',
  `error_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '错误码。',
  `duration_ms` INT NULL DEFAULT NULL COMMENT '耗时毫秒。',
  `biz_type` VARCHAR(64) NULL DEFAULT NULL COMMENT '业务对象类型。',
  `biz_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '业务对象 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_access_request` (`request_id`),
  KEY `idx_openapi_access_client` (`client_id`, `created_at`),
  KEY `idx_openapi_access_system` (`system_id`, `tenant_id`, `created_at`),
  KEY `idx_openapi_access_error` (`error_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部调用日志。';

DROP TABLE IF EXISTS `un_sys_idempotency_record`;
CREATE TABLE `un_sys_idempotency_record` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `caller_type` VARCHAR(32) NOT NULL DEFAULT 'INTERNAL' COMMENT 'INTERNAL；OpenAPI 使用专表。',
  `account_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '平台账号 ID。',
  `member_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '系统成员 ID。',
  `system_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `api_id` VARCHAR(32) NOT NULL COMMENT 'API ID。',
  `biz_action` VARCHAR(64) NOT NULL COMMENT '业务动作。',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键。',
  `scope_key` VARCHAR(500) NOT NULL COMMENT '归一化幂等 scope。',
  `request_hash` CHAR(64) NOT NULL COMMENT '请求摘要 SHA-256。',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING、SUCCESS、FAILED、CONFLICT。',
  `result_snapshot_json` JSON NULL DEFAULT NULL COMMENT '结果快照。',
  `request_id` VARCHAR(64) NOT NULL COMMENT '首次请求 requestId。',
  `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间；默认 24 小时，流程任务和导出任务 72 小时。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_idempotency_scope` (`scope_key`),
  KEY `idx_sys_idempotency_expire` (`expires_at`),
  KEY `idx_sys_idempotency_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内部写接口幂等记录。';

DROP TABLE IF EXISTS `un_sys_request_log`;
CREATE TABLE `un_sys_request_log` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `trace_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '链路追踪 ID。',
  `operator_type` VARCHAR(32) NOT NULL COMMENT 'ACCOUNT、MEMBER、OPENAPI_CLIENT、SYSTEM。',
  `operator_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '操作主体 ID。',
  `system_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `method` VARCHAR(16) NOT NULL COMMENT 'HTTP 方法。',
  `path` VARCHAR(500) NOT NULL COMMENT '请求路径。',
  `module` VARCHAR(64) NULL DEFAULT NULL COMMENT '模块命名空间。',
  `client_ip` VARCHAR(64) NULL DEFAULT NULL COMMENT '客户端 IP。',
  `http_status` INT NOT NULL COMMENT 'HTTP 状态码。',
  `result` VARCHAR(32) NOT NULL COMMENT 'SUCCESS、FAILED。',
  `duration_ms` INT NULL DEFAULT NULL COMMENT '耗时毫秒。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_request_id` (`request_id`),
  KEY `idx_sys_request_system` (`system_id`, `tenant_id`, `created_at`),
  KEY `idx_sys_request_operator` (`operator_type`, `operator_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内部请求日志。';

DROP TABLE IF EXISTS `un_sys_error_log`;
CREATE TABLE `un_sys_error_log` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `trace_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '链路追踪 ID。',
  `system_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `error_code` VARCHAR(64) NOT NULL COMMENT '模块化错误码。',
  `error_message` VARCHAR(1000) NOT NULL COMMENT '稳定错误提示。',
  `stack_summary` TEXT NULL DEFAULT NULL COMMENT '脱敏栈摘要。',
  `biz_type` VARCHAR(64) NULL DEFAULT NULL COMMENT '业务对象类型。',
  `biz_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '业务对象 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_sys_error_request` (`request_id`),
  KEY `idx_sys_error_code` (`error_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错误码、栈摘要和 requestId。';

DROP TABLE IF EXISTS `un_audit_operation_log`;
CREATE TABLE `un_audit_operation_log` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `trace_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '链路追踪 ID。',
  `operator_type` VARCHAR(32) NOT NULL COMMENT 'ACCOUNT、MEMBER、OPENAPI_CLIENT、SYSTEM。',
  `operator_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '操作主体 ID。',
  `operator_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '操作主体名称快照。',
  `system_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `module` VARCHAR(64) NOT NULL COMMENT '模块，如 FLOW、UPLOAD、OPENAPI。',
  `biz_type` VARCHAR(64) NULL DEFAULT NULL COMMENT '业务对象类型。',
  `biz_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '业务对象 ID。',
  `action` VARCHAR(64) NOT NULL COMMENT '操作动作。',
  `result` VARCHAR(32) NOT NULL COMMENT 'SUCCESS、FAILED。',
  `error_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '错误码。',
  `summary` VARCHAR(1000) NULL DEFAULT NULL COMMENT '审计摘要。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operation_request` (`request_id`),
  KEY `idx_audit_operation_biz` (`system_id`, `tenant_id`, `biz_type`, `biz_id`, `created_at`),
  KEY `idx_audit_operation_operator` (`operator_type`, `operator_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台、系统、运行、流程、文件和 OpenAPI 操作审计。';

DROP TABLE IF EXISTS `un_audit_record_change`;
CREATE TABLE `un_audit_record_change` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `system_id` BIGINT UNSIGNED NOT NULL COMMENT '系统 ID。',
  `tenant_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '租户 ID。',
  `module_id` BIGINT UNSIGNED NOT NULL COMMENT '动态模块 ID。',
  `record_id` BIGINT UNSIGNED NOT NULL COMMENT '业务记录 ID。',
  `change_type` VARCHAR(32) NOT NULL COMMENT 'CREATE、UPDATE、DELETE、STATUS_CHANGE、FLOW_ACTION。',
  `before_snapshot_json` JSON NULL DEFAULT NULL COMMENT '变更前快照。',
  `after_snapshot_json` JSON NULL DEFAULT NULL COMMENT '变更后快照。',
  `changed_by_type` VARCHAR(32) NOT NULL COMMENT 'MEMBER、OPENAPI_CLIENT、SYSTEM。',
  `changed_by_id` VARCHAR(64) NOT NULL COMMENT '变更主体 ID。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_audit_record_change_record` (`system_id`, `tenant_id`, `module_id`, `record_id`, `created_at`),
  KEY `idx_audit_record_change_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态记录字段变更前后快照。';

DROP TABLE IF EXISTS `un_sys_health_check_result`;
CREATE TABLE `un_sys_health_check_result` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `component` VARCHAR(64) NOT NULL COMMENT '组件，如 DB、REDIS、UPLOAD_STORAGE、OPENAPI_KEY。',
  `status` VARCHAR(32) NOT NULL COMMENT 'UP、WARN、DOWN。',
  `result` VARCHAR(32) NOT NULL COMMENT 'SUCCESS、FAILED。',
  `message` VARCHAR(1000) NULL DEFAULT NULL COMMENT '检查消息。',
  `suggestion` VARCHAR(1000) NULL DEFAULT NULL COMMENT '修复建议。',
  `checked_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '检查时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_sys_health_component` (`component`, `checked_at`),
  KEY `idx_sys_health_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康检查结果。';

DROP TABLE IF EXISTS `un_sys_runtime_config_check`;
CREATE TABLE `un_sys_runtime_config_check` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `request_id` VARCHAR(64) NOT NULL COMMENT '请求追踪 ID。',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键。',
  `component` VARCHAR(64) NOT NULL COMMENT '所属组件。',
  `status` VARCHAR(32) NOT NULL COMMENT 'PASS、WARN、FAIL。',
  `message` VARCHAR(1000) NULL DEFAULT NULL COMMENT '检查消息。',
  `suggestion` VARCHAR(1000) NULL DEFAULT NULL COMMENT '修复建议。',
  `checked_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '检查时间。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  KEY `idx_sys_config_check_key` (`config_key`, `checked_at`),
  KEY `idx_sys_config_check_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行配置检查结果。';

DROP TABLE IF EXISTS `un_sys_migration_status`;
CREATE TABLE `un_sys_migration_status` (
  `id` BIGINT UNSIGNED NOT NULL COMMENT 'Primary key ID.',
  `version` VARCHAR(64) NOT NULL COMMENT 'migration 版本。',
  `description` VARCHAR(255) NULL DEFAULT NULL COMMENT 'migration 描述。',
  `status` VARCHAR(32) NOT NULL COMMENT 'SUCCESS、FAILED、PENDING、REPAIRED。',
  `checksum` VARCHAR(128) NULL DEFAULT NULL COMMENT '校验值。',
  `installed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '安装时间。',
  `execution_time_ms` INT NULL DEFAULT NULL COMMENT '执行耗时。',
  `error_message` VARCHAR(1000) NULL DEFAULT NULL COMMENT '失败摘要。',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Created time.',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Updated time.',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_migration_version` (`version`),
  KEY `idx_sys_migration_status` (`status`, `installed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='DB migration 状态查询落点。';

SET FOREIGN_KEY_CHECKS = 1;

-- Production seed: platform account, platform RBAC, platform configuration and field type metadata.
INSERT INTO `un_plat_account` (`id`,`login_name`,`password_hash`,`display_name`,`status`,`first_login_change_pwd`,`failed_login_count`,`delete_token`,`created_at`,`updated_at`) VALUES
(1,'platform_admin','__REPLACE_WITH_DEPLOYMENT_PASSWORD_HASH__','Platform Administrator','NORMAL',1,0,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `display_name`=VALUES(`display_name`),`status`=VALUES(`status`),`first_login_change_pwd`=VALUES(`first_login_change_pwd`),`updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_plat_role` (`id`,`code`,`name`,`description`,`status`,`protected_flag`,`sort_order`,`delete_token`,`created_at`,`updated_at`) VALUES
(100,'PLAT_SUPER_ADMIN','Platform Super Admin','Built-in platform super admin role.','ENABLED',1,10,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(101,'PLAT_ADMIN','Platform Admin','Platform account, system and configuration admin role.','ENABLED',1,20,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(102,'PLAT_AUDITOR','Platform Auditor','Platform audit read-only role.','ENABLED',1,30,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`description`=VALUES(`description`),`status`=VALUES(`status`),`protected_flag`=VALUES(`protected_flag`),`updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_plat_menu` (`id`,`parent_id`,`code`,`name`,`path`,`icon`,`status`,`sort_order`,`depth_level`,`depth_path`,`delete_token`,`created_at`,`updated_at`) VALUES
(200,0,'PLAT_MY_SYSTEM','My Systems','/platform/my-systems','layout-dashboard','ENABLED',10,1,'/PLAT_MY_SYSTEM',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(201,0,'PLAT_SYSTEM','Systems','/platform/systems','layout-template','ENABLED',20,1,'/PLAT_SYSTEM',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(202,0,'PLAT_TENANT','Tenants','/platform/tenants','building','ENABLED',30,1,'/PLAT_TENANT',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(203,0,'PLAT_ACCOUNT','Platform Accounts','/platform/accounts','users','ENABLED',40,1,'/PLAT_ACCOUNT',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(204,0,'PLAT_ROLE','Platform Roles','/platform/roles','shield','ENABLED',50,1,'/PLAT_ROLE',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(205,0,'PLAT_CONFIG','Platform Configs','/platform/configs','settings','ENABLED',60,1,'/PLAT_CONFIG',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(206,0,'PLAT_HEALTH','Health Checks','/platform/ops/health','activity','ENABLED',70,1,'/PLAT_HEALTH',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(207,0,'PLAT_AUDIT_LOG','Platform Audit','/platform/audit/operation-logs','file-search','ENABLED',80,1,'/PLAT_AUDIT_LOG',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(208,0,'PLAT_VERSION','Version','/platform/ops/version','badge-info','ENABLED',90,1,'/PLAT_VERSION',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(209,0,'PLAT_OPENAPI_POLICY','OpenAPI Policies','/platform/openapi/policies','key-round','ENABLED',100,1,'/PLAT_OPENAPI_POLICY',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`status`=VALUES(`status`),`sort_order`=VALUES(`sort_order`),`updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_plat_operation` (`id`,`menu_id`,`code`,`name`,`api_pattern`,`method`,`status`,`delete_token`,`created_at`,`updated_at`) VALUES
(300,201,'PLAT_SYSTEM_CREATE','Create System','/api/v1/platform/systems','POST','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(301,201,'PLAT_SYSTEM_VIEW','View Systems','/api/v1/platform/systems/**','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(302,201,'PLAT_SYSTEM_STATUS','Change System Status','/api/v1/platform/systems/*/status','PUT','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(303,203,'PLAT_ACCOUNT_VIEW','View Accounts','/api/v1/platform/accounts/**','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(304,203,'PLAT_ACCOUNT_CREATE','Create Account','/api/v1/platform/accounts','POST','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(305,203,'PLAT_ACCOUNT_STATUS','Change Account Status','/api/v1/platform/accounts/*/status','PUT','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(306,204,'PLAT_ROLE_VIEW','View Roles','/api/v1/platform/roles/**','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(307,204,'PLAT_ROLE_AUTH','Authorize Role','/api/v1/platform/roles/*/permissions','PUT','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(308,205,'PLAT_CONFIG_VIEW','View Config','/api/v1/platform/configs/**','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(309,205,'PLAT_CONFIG_EDIT','Edit Config','/api/v1/platform/configs/*','PUT','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(310,207,'PLAT_AUDIT_VIEW','View Audit','/api/v1/platform/audit/**','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(311,206,'OPS_HEALTH_VIEW','View Health','/api/v1/ops/health','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(312,208,'OPS_VERSION_VIEW','View Version','/api/v1/ops/version','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(313,209,'OPENAPI_POLICY_VIEW','View OpenAPI Policy','/api/v1/platform/openapi/**','GET','ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`api_pattern`=VALUES(`api_pattern`),`method`=VALUES(`method`),`status`=VALUES(`status`),`updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_plat_account_role` (`id`,`account_id`,`role_id`,`delete_token`,`created_at`,`updated_at`) VALUES (400,1,100,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_plat_role_menu` (`id`,`role_id`,`menu_id`,`delete_token`,`created_at`,`updated_at`) VALUES
(500,100,200,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(501,100,201,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(502,100,202,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(503,100,203,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(504,100,204,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(505,100,205,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(506,100,206,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(507,100,207,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(508,100,208,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(509,100,209,0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_plat_role_operation` (`id`,`role_id`,`operation_id`,`operation_code`,`delete_token`,`created_at`,`updated_at`) VALUES
(600,100,300,'PLAT_SYSTEM_CREATE',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(601,100,301,'PLAT_SYSTEM_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(602,100,302,'PLAT_SYSTEM_STATUS',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(603,100,303,'PLAT_ACCOUNT_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(604,100,304,'PLAT_ACCOUNT_CREATE',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(605,100,305,'PLAT_ACCOUNT_STATUS',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(606,100,306,'PLAT_ROLE_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(607,100,307,'PLAT_ROLE_AUTH',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(608,100,308,'PLAT_CONFIG_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(609,100,309,'PLAT_CONFIG_EDIT',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(610,100,310,'PLAT_AUDIT_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(611,100,311,'OPS_HEALTH_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(612,100,312,'OPS_VERSION_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(613,100,313,'OPENAPI_POLICY_VIEW',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `operation_code`=VALUES(`operation_code`),`updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_plat_config` (`id`,`config_key`,`config_name`,`config_value`,`sensitive_flag`,`status`,`remark`,`delete_token`,`created_at`,`updated_at`) VALUES
(700,'SECURITY_PASSWORD_POLICY','Password Policy',CAST('{"minLength":12,"requireUpper":true,"requireLower":true,"requireNumber":true,"requireSymbol":true,"maxFailedLogin":5,"lockMinutes":30}' AS JSON),0,'ENABLED','Default password complexity and lock policy.',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(701,'SESSION_POLICY','Session Policy',CAST('{"accessTokenMinutes":120,"refreshTokenDays":7,"singleSession":false}' AS JSON),0,'ENABLED','Default session policy.',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(702,'FILE_STORAGE_DEFAULT','Default File Storage',CAST('{"storageCode":"PLATFORM_LOCAL_DEFAULT","maxFileSizeMb":100,"allowedExtensions":[]}' AS JSON),0,'ENABLED','Default upload storage policy; secrets are managed by secret_ref.',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(703,'OPENAPI_GLOBAL_POLICY','OpenAPI Global Policy',CAST('{"timestampWindowSeconds":300,"nonceTtlSeconds":600,"defaultRateLimitPerMinute":600}' AS JSON),0,'ENABLED','OpenAPI signature, timestamp, nonce and rate policy.',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(704,'AUDIT_RETENTION_POLICY','Audit Retention Policy',CAST('{"requestLogDays":180,"operationLogDays":365,"errorLogDays":365,"openApiLogDays":180}' AS JSON),0,'ENABLED','Default audit retention policy.',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
(705,'MODULE_FIELD_TYPE_CATALOG','Field Type Catalog',CAST('{"types":["TEXT","TEXTAREA","NUMBER","MONEY","DATE","DATETIME","SELECT","MULTI_SELECT","SWITCH","MEMBER","DEPT","ATTACHMENT","IMAGE","AUTO_NO","RELATION","SUB_TABLE","ADDRESS","TAG","JSON"]}' AS JSON),0,'ENABLED','MVP field type metadata for module field configuration.',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `config_name`=VALUES(`config_name`),`config_value`=VALUES(`config_value`),`sensitive_flag`=VALUES(`sensitive_flag`),`status`=VALUES(`status`),`remark`=VALUES(`remark`),`updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_upload_storage_config` (`id`,`system_id`,`tenant_id`,`code`,`name`,`storage_type`,`root_path`,`config_json`,`secret_ref`,`default_flag`,`status`,`deleted`,`created_at`,`updated_at`) VALUES
(800,NULL,NULL,'PLATFORM_LOCAL_DEFAULT','Platform Local Default Storage','LOCAL','uploads',CAST('{"mode":"local"}' AS JSON),NULL,1,'ENABLED',0,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`storage_type`=VALUES(`storage_type`),`root_path`=VALUES(`root_path`),`config_json`=VALUES(`config_json`),`default_flag`=VALUES(`default_flag`),`status`=VALUES(`status`),`updated_at`=CURRENT_TIMESTAMP(3);

INSERT INTO `un_sys_migration_status` (`id`,`version`,`description`,`status`,`checksum`,`installed_at`,`execution_time_ms`,`error_message`,`created_at`,`updated_at`) VALUES
(900,'DBA-006_INIT_SQL','DBA-006 init SQL import marker','SUCCESS',NULL,CURRENT_TIMESTAMP(3),NULL,NULL,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE `status`=VALUES(`status`),`installed_at`=VALUES(`installed_at`),`error_message`=NULL,`updated_at`=CURRENT_TIMESTAMP(3);
