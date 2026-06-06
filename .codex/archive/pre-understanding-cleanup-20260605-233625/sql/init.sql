CREATE DATABASE IF NOT EXISTS `examine1` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `examine1`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `un_platt_system` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `system_code` VARCHAR(64) NOT NULL COMMENT '系统编码，全局唯一',
  `system_name` VARCHAR(128) NOT NULL COMMENT '系统名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '系统说明',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_system_code` (`system_code`),
  KEY `idx_un_platt_system_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台系统上下文';

CREATE TABLE IF NOT EXISTS `un_platt_tenant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_code` VARCHAR(64) NOT NULL COMMENT '租户编码，全局唯一',
  `tenant_name` VARCHAR(128) NOT NULL COMMENT '租户名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED、EXPIRED',
  `admin_account_id` BIGINT DEFAULT NULL COMMENT '默认管理员账号 ID',
  `expire_at` DATETIME DEFAULT NULL COMMENT '有效期截止时间',
  `config_json` JSON DEFAULT NULL COMMENT '租户扩展配置',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_tenant_code` (`tenant_code`),
  KEY `idx_un_platt_tenant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台租户';

CREATE TABLE IF NOT EXISTS `un_platt_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(64) NOT NULL COMMENT '登录账号，全局唯一',
  `display_name` VARCHAR(128) NOT NULL COMMENT '显示名称',
  `mobile` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED、LOCKED',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最近登录时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_account_username` (`username`),
  KEY `idx_un_platt_account_status` (`status`),
  KEY `idx_un_platt_account_mobile` (`mobile`),
  KEY `idx_un_platt_account_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号';

CREATE TABLE IF NOT EXISTS `un_platt_account_tenant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account_id` BIGINT NOT NULL COMMENT '账号 ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认上下文：0-否，1-是',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_account_tenant_ctx` (`account_id`, `tenant_id`, `system_id`),
  KEY `idx_un_platt_account_tenant_tenant` (`tenant_id`, `system_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号租户系统关系';

CREATE TABLE IF NOT EXISTS `un_platt_department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父部门 ID',
  `dept_code` VARCHAR(64) NOT NULL COMMENT '部门编码',
  `dept_name` VARCHAR(128) NOT NULL COMMENT '部门名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_department_code` (`tenant_id`, `system_id`, `dept_code`, `deleted`),
  KEY `idx_un_platt_department_parent` (`tenant_id`, `system_id`, `parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户部门';

CREATE TABLE IF NOT EXISTS `un_platt_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `app_id` BIGINT DEFAULT NULL COMMENT '应用 ID，平台或系统角色为空',
  `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
  `role_name` VARCHAR(128) NOT NULL COMMENT '角色名称',
  `role_type` VARCHAR(32) NOT NULL DEFAULT 'TENANT' COMMENT '类型：PLATFORM、TENANT、APP',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_role_code` (`tenant_id`, `system_id`, `app_id`, `role_code`, `deleted`),
  KEY `idx_un_platt_role_ctx` (`tenant_id`, `system_id`, `app_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台角色';

CREATE TABLE IF NOT EXISTS `un_platt_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户 ID，平台权限可为空',
  `system_id` BIGINT DEFAULT NULL COMMENT '系统 ID',
  `app_id` BIGINT DEFAULT NULL COMMENT '应用 ID',
  `module_id` BIGINT DEFAULT NULL COMMENT '模块 ID',
  `permission_code` VARCHAR(128) NOT NULL COMMENT '权限编码',
  `permission_name` VARCHAR(128) NOT NULL COMMENT '权限名称',
  `permission_type` VARCHAR(32) NOT NULL DEFAULT 'MENU' COMMENT '类型：MENU、BUTTON、API、FIELD、DATA_SCOPE',
  `resource_path` VARCHAR(255) DEFAULT NULL COMMENT '路由、接口或字段路径',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_permission_code` (`permission_code`, `deleted`),
  KEY `idx_un_platt_permission_ctx` (`tenant_id`, `system_id`, `app_id`, `module_id`),
  KEY `idx_un_platt_permission_type` (`permission_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点';

CREATE TABLE IF NOT EXISTS `un_platt_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT NOT NULL COMMENT '角色 ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限 ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_role_permission` (`role_id`, `permission_id`),
  KEY `idx_un_platt_role_permission_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系';

CREATE TABLE IF NOT EXISTS `un_platt_account_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account_id` BIGINT NOT NULL COMMENT '账号 ID',
  `role_id` BIGINT NOT NULL COMMENT '角色 ID',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_account_role` (`account_id`, `role_id`, `tenant_id`, `system_id`),
  KEY `idx_un_platt_account_role_role` (`role_id`, `tenant_id`, `system_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号角色关系';

CREATE TABLE IF NOT EXISTS `un_platt_dict` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户 ID，全局字典为空',
  `dict_code` VARCHAR(64) NOT NULL COMMENT '字典编码',
  `dict_name` VARCHAR(128) NOT NULL COMMENT '字典名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_dict_code` (`tenant_id`, `dict_code`, `deleted`),
  KEY `idx_un_platt_dict_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典';

CREATE TABLE IF NOT EXISTS `un_platt_dict_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_id` BIGINT NOT NULL COMMENT '字典 ID',
  `item_value` VARCHAR(128) NOT NULL COMMENT '字典项值',
  `item_label` VARCHAR(128) NOT NULL COMMENT '字典项显示名',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_platt_dict_item_value` (`dict_id`, `item_value`, `deleted`),
  KEY `idx_un_platt_dict_item_sort` (`dict_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典项';

CREATE TABLE IF NOT EXISTS `un_app_application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `app_code` VARCHAR(64) NOT NULL COMMENT '应用编码',
  `app_name` VARCHAR(128) NOT NULL COMMENT '应用名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、PUBLISHED、DISABLED',
  `published_version_id` BIGINT DEFAULT NULL COMMENT '当前发布版本 ID',
  `visible_scope` VARCHAR(32) NOT NULL DEFAULT 'TENANT' COMMENT '可见范围：TENANT、ROLE、CUSTOM',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '应用说明',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_app_application_code` (`tenant_id`, `system_id`, `app_code`, `deleted`),
  KEY `idx_un_app_application_ctx` (`tenant_id`, `system_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可配置应用';

CREATE TABLE IF NOT EXISTS `un_app_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_id` BIGINT NOT NULL COMMENT '应用 ID',
  `version_no` INT NOT NULL COMMENT '版本号',
  `version_name` VARCHAR(128) NOT NULL COMMENT '版本名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、PUBLISHED、ARCHIVED',
  `snapshot_json` JSON DEFAULT NULL COMMENT '发布配置快照',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_app_version_no` (`app_id`, `version_no`),
  KEY `idx_un_app_version_status` (`app_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用版本';

CREATE TABLE IF NOT EXISTS `un_module_model` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `app_id` BIGINT NOT NULL COMMENT '应用 ID',
  `module_code` VARCHAR(64) NOT NULL COMMENT '模块编码',
  `module_name` VARCHAR(128) NOT NULL COMMENT '模块名称',
  `data_scope_type` VARCHAR(32) NOT NULL DEFAULT 'OWNER' COMMENT '数据范围：OWNER、DEPT、DEPT_TREE、ROLE、ALL',
  `flow_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用流程：0-否，1-是',
  `import_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许导入：0-否，1-是',
  `export_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许导出：0-否，1-是',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、PUBLISHED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_model_code` (`app_id`, `module_code`, `deleted`),
  KEY `idx_un_module_model_ctx` (`tenant_id`, `system_id`, `app_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态模块模型';

CREATE TABLE IF NOT EXISTS `un_module_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码',
  `field_name` VARCHAR(128) NOT NULL COMMENT '字段名称',
  `field_type` VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT '类型：TEXT、NUMBER、DECIMAL、DATE、DATETIME、SELECT、MULTI_SELECT、USER、DEPT、FILE',
  `required_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
  `unique_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否唯一：0-否，1-是',
  `list_visible` TINYINT NOT NULL DEFAULT 1 COMMENT '列表是否可见：0-否，1-是',
  `searchable` TINYINT NOT NULL DEFAULT 0 COMMENT '是否可搜索：0-否，1-是',
  `editable` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可编辑：0-否，1-是',
  `default_value` VARCHAR(500) DEFAULT NULL COMMENT '默认值',
  `validation_json` JSON DEFAULT NULL COMMENT '校验规则',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_field_code` (`module_id`, `field_code`, `deleted`),
  KEY `idx_un_module_field_type` (`module_id`, `field_type`),
  KEY `idx_un_module_field_sort` (`module_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块字段';

CREATE TABLE IF NOT EXISTS `un_module_field_option` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `field_id` BIGINT NOT NULL COMMENT '字段 ID',
  `option_value` VARCHAR(128) NOT NULL COMMENT '选项值',
  `option_label` VARCHAR(128) NOT NULL COMMENT '选项显示名',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_field_option_value` (`field_id`, `option_value`, `deleted`),
  KEY `idx_un_module_field_option_sort` (`field_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块字段选项';

CREATE TABLE IF NOT EXISTS `un_module_page` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID',
  `page_code` VARCHAR(64) NOT NULL COMMENT '页面编码',
  `page_name` VARCHAR(128) NOT NULL COMMENT '页面名称',
  `page_type` VARCHAR(32) NOT NULL DEFAULT 'LIST' COMMENT '类型：LIST、FORM、DETAIL、DASHBOARD',
  `layout_json` JSON DEFAULT NULL COMMENT '布局配置',
  `button_json` JSON DEFAULT NULL COMMENT '按钮配置',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_page_code` (`module_id`, `page_code`, `deleted`),
  KEY `idx_un_module_page_type` (`module_id`, `page_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块页面';

CREATE TABLE IF NOT EXISTS `un_module_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `app_id` BIGINT DEFAULT NULL COMMENT '应用 ID',
  `module_id` BIGINT DEFAULT NULL COMMENT '模块 ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父菜单 ID',
  `menu_code` VARCHAR(64) NOT NULL COMMENT '菜单编码',
  `menu_name` VARCHAR(128) NOT NULL COMMENT '菜单名称',
  `route_path` VARCHAR(255) DEFAULT NULL COMMENT '前端路由',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_menu_code` (`tenant_id`, `system_id`, `menu_code`, `deleted`),
  KEY `idx_un_module_menu_tree` (`tenant_id`, `system_id`, `parent_id`, `sort_order`),
  KEY `idx_un_module_menu_module` (`app_id`, `module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块菜单';

CREATE TABLE IF NOT EXISTS `un_module_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `app_id` BIGINT NOT NULL COMMENT '应用 ID',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID',
  `record_no` VARCHAR(64) NOT NULL COMMENT '业务记录编号',
  `owner_account_id` BIGINT DEFAULT NULL COMMENT '负责人账号 ID',
  `dept_id` BIGINT DEFAULT NULL COMMENT '归属部门 ID',
  `record_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、ACTIVE、FLOWING、ARCHIVED',
  `flow_instance_id` BIGINT DEFAULT NULL COMMENT '当前流程实例 ID',
  `version_no` INT NOT NULL DEFAULT 1 COMMENT '记录版本号',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_record_no` (`tenant_id`, `module_id`, `record_no`, `deleted`),
  KEY `idx_un_module_record_list` (`tenant_id`, `system_id`, `app_id`, `module_id`, `record_status`, `updated_at`),
  KEY `idx_un_module_record_owner` (`tenant_id`, `module_id`, `owner_account_id`),
  KEY `idx_un_module_record_dept` (`tenant_id`, `module_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块业务记录';

CREATE TABLE IF NOT EXISTS `un_module_record_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `record_id` BIGINT NOT NULL COMMENT '记录 ID',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID，查询冗余',
  `field_id` BIGINT NOT NULL COMMENT '字段 ID',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码，查询冗余',
  `value_text` VARCHAR(1000) DEFAULT NULL COMMENT '文本值',
  `value_number` DECIMAL(30,8) DEFAULT NULL COMMENT '数值',
  `value_datetime` DATETIME DEFAULT NULL COMMENT '日期时间值',
  `value_json` JSON DEFAULT NULL COMMENT '复杂值',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_record_value_field` (`record_id`, `field_id`),
  KEY `idx_un_module_record_value_text` (`module_id`, `field_code`, `value_text`(191)),
  KEY `idx_un_module_record_value_number` (`module_id`, `field_code`, `value_number`),
  KEY `idx_un_module_record_value_datetime` (`module_id`, `field_code`, `value_datetime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块业务记录字段值';

CREATE TABLE IF NOT EXISTS `un_module_data_scope` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID',
  `role_id` BIGINT NOT NULL COMMENT '角色 ID',
  `scope_type` VARCHAR(32) NOT NULL DEFAULT 'OWNER' COMMENT '范围：OWNER、DEPT、DEPT_TREE、ROLE、ALL、CUSTOM',
  `scope_json` JSON DEFAULT NULL COMMENT '自定义范围配置',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_module_data_scope_role` (`module_id`, `role_id`),
  KEY `idx_un_module_data_scope_type` (`module_id`, `scope_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块数据权限';

CREATE TABLE IF NOT EXISTS `un_module_export_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID',
  `job_type` VARCHAR(32) NOT NULL DEFAULT 'EXPORT' COMMENT '类型：EXPORT',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、RUNNING、SUCCESS、FAILED',
  `request_json` JSON DEFAULT NULL COMMENT '导出参数',
  `result_file_id` BIGINT DEFAULT NULL COMMENT '结果文件 ID',
  `failure_reason` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_un_module_export_job_list` (`tenant_id`, `module_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块导出任务';

CREATE TABLE IF NOT EXISTS `un_flow_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `app_id` BIGINT NOT NULL COMMENT '应用 ID',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID',
  `template_code` VARCHAR(64) NOT NULL COMMENT '流程模板编码',
  `template_name` VARCHAR(128) NOT NULL COMMENT '流程模板名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、PUBLISHED、DISABLED',
  `published_version_id` BIGINT DEFAULT NULL COMMENT '当前发布版本 ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_flow_template_code` (`module_id`, `template_code`, `deleted`),
  KEY `idx_un_flow_template_ctx` (`tenant_id`, `app_id`, `module_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程模板';

CREATE TABLE IF NOT EXISTS `un_flow_template_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` BIGINT NOT NULL COMMENT '模板 ID',
  `version_no` INT NOT NULL COMMENT '版本号',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、PUBLISHED、ARCHIVED',
  `graph_json` JSON DEFAULT NULL COMMENT '流程图快照',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_flow_template_version_no` (`template_id`, `version_no`),
  KEY `idx_un_flow_template_version_status` (`template_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程模板版本';

CREATE TABLE IF NOT EXISTS `un_flow_instance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `module_id` BIGINT NOT NULL COMMENT '模块 ID',
  `record_id` BIGINT NOT NULL COMMENT '业务记录 ID',
  `template_id` BIGINT NOT NULL COMMENT '流程模板 ID',
  `template_version_id` BIGINT NOT NULL COMMENT '流程模板版本 ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING、APPROVED、REJECTED、CANCELED、TERMINATED',
  `current_node_key` VARCHAR(128) DEFAULT NULL COMMENT '当前节点 key',
  `started_by` BIGINT NOT NULL COMMENT '发起人账号 ID',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `ended_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_un_flow_instance_record` (`tenant_id`, `module_id`, `record_id`),
  KEY `idx_un_flow_instance_status` (`tenant_id`, `status`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例';

CREATE TABLE IF NOT EXISTS `un_flow_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `instance_id` BIGINT NOT NULL COMMENT '流程实例 ID',
  `node_key` VARCHAR(128) NOT NULL COMMENT '节点 key',
  `task_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
  `assignee_id` BIGINT DEFAULT NULL COMMENT '当前处理人',
  `candidate_json` JSON DEFAULT NULL COMMENT '候选人快照',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、APPROVED、REJECTED、CANCELED、TRANSFERRED、RETURNED',
  `due_at` DATETIME DEFAULT NULL COMMENT '到期时间',
  `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_un_flow_task_inbox` (`assignee_id`, `status`, `created_at`),
  KEY `idx_un_flow_task_instance` (`instance_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程任务';

CREATE TABLE IF NOT EXISTS `un_flow_approval_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `instance_id` BIGINT NOT NULL COMMENT '流程实例 ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务 ID',
  `action_type` VARCHAR(32) NOT NULL DEFAULT 'SUBMIT' COMMENT '动作：SUBMIT、APPROVE、REJECT、TRANSFER、RETURN、CANCEL、TERMINATE',
  `operator_id` BIGINT NOT NULL COMMENT '操作人账号 ID',
  `comment_text` VARCHAR(1000) DEFAULT NULL COMMENT '审批意见',
  `snapshot_json` JSON DEFAULT NULL COMMENT '动作快照',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_un_flow_approval_log_instance` (`instance_id`, `created_at`),
  KEY `idx_un_flow_approval_log_operator` (`operator_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程审批日志';

CREATE TABLE IF NOT EXISTS `un_upload_storage_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_code` VARCHAR(64) NOT NULL COMMENT '存储配置编码',
  `storage_type` VARCHAR(32) NOT NULL DEFAULT 'LOCAL' COMMENT '类型：LOCAL、S3、MINIO、OSS',
  `bucket_name` VARCHAR(128) DEFAULT NULL COMMENT '桶名',
  `endpoint` VARCHAR(255) DEFAULT NULL COMMENT '访问端点',
  `base_path` VARCHAR(255) DEFAULT NULL COMMENT '基础路径',
  `config_json` JSON DEFAULT NULL COMMENT '扩展配置',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_upload_storage_config_code` (`config_code`),
  KEY `idx_un_upload_storage_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件存储配置';

CREATE TABLE IF NOT EXISTS `un_upload_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户 ID',
  `storage_config_id` BIGINT NOT NULL COMMENT '存储配置 ID',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_ext` VARCHAR(32) DEFAULT NULL COMMENT '文件扩展名',
  `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
  `storage_path` VARCHAR(500) NOT NULL COMMENT '存储路径',
  `sha256` VARCHAR(128) DEFAULT NULL COMMENT '文件哈希',
  `status` VARCHAR(32) NOT NULL DEFAULT 'TEMP' COMMENT '状态：TEMP、REFERENCED、DELETED',
  `uploaded_by` BIGINT DEFAULT NULL COMMENT '上传人账号 ID',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  KEY `idx_un_upload_file_tenant` (`tenant_id`, `status`, `created_at`),
  KEY `idx_un_upload_file_hash` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传文件';

CREATE TABLE IF NOT EXISTS `un_upload_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` BIGINT NOT NULL COMMENT '文件 ID',
  `biz_type` VARCHAR(64) NOT NULL COMMENT '业务类型：MODULE_RECORD、FLOW_TASK、CONFIG',
  `biz_id` BIGINT NOT NULL COMMENT '业务对象 ID',
  `field_code` VARCHAR(64) DEFAULT NULL COMMENT '关联字段编码',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_upload_attachment_ref` (`file_id`, `biz_type`, `biz_id`, `field_code`),
  KEY `idx_un_upload_attachment_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件附件引用';

CREATE TABLE IF NOT EXISTS `un_upload_import_export_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `module_id` BIGINT DEFAULT NULL COMMENT '模块 ID',
  `job_type` VARCHAR(32) NOT NULL DEFAULT 'IMPORT' COMMENT '类型：IMPORT、EXPORT',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、RUNNING、SUCCESS、FAILED',
  `source_file_id` BIGINT DEFAULT NULL COMMENT '导入源文件 ID',
  `result_file_id` BIGINT DEFAULT NULL COMMENT '结果文件 ID',
  `request_json` JSON DEFAULT NULL COMMENT '请求参数',
  `failure_reason` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_un_upload_import_export_job_list` (`tenant_id`, `module_id`, `job_type`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入导出任务';

CREATE TABLE IF NOT EXISTS `un_openapi_client` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户 ID',
  `system_id` BIGINT NOT NULL COMMENT '系统 ID',
  `client_code` VARCHAR(64) NOT NULL COMMENT '客户端编码',
  `client_name` VARCHAR(128) NOT NULL COMMENT '客户端名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED、EXPIRED',
  `rate_limit_per_minute` INT NOT NULL DEFAULT 600 COMMENT '每分钟限流',
  `expired_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_openapi_client_code` (`tenant_id`, `system_id`, `client_code`, `deleted`),
  KEY `idx_un_openapi_client_status` (`tenant_id`, `system_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 客户端';

CREATE TABLE IF NOT EXISTS `un_openapi_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `client_id` BIGINT NOT NULL COMMENT '客户端 ID',
  `access_key` VARCHAR(128) NOT NULL COMMENT '访问 key',
  `secret_hash` VARCHAR(255) NOT NULL COMMENT '密钥哈希或密文摘要',
  `secret_version` INT NOT NULL DEFAULT 1 COMMENT '密钥版本',
  `sign_algorithm` VARCHAR(32) NOT NULL DEFAULT 'HMAC_SHA256' COMMENT '签名算法：HMAC_SHA256',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_openapi_credential_access_key` (`access_key`),
  KEY `idx_un_openapi_credential_client` (`client_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 凭证';

CREATE TABLE IF NOT EXISTS `un_openapi_scope` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `client_id` BIGINT NOT NULL COMMENT '客户端 ID',
  `app_id` BIGINT DEFAULT NULL COMMENT '授权应用 ID',
  `module_id` BIGINT DEFAULT NULL COMMENT '授权模块 ID',
  `scope_code` VARCHAR(128) NOT NULL COMMENT '授权范围编码',
  `actions` VARCHAR(255) NOT NULL COMMENT '动作集合：READ、WRITE、DELETE、FLOW',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_openapi_scope_code` (`client_id`, `scope_code`),
  KEY `idx_un_openapi_scope_module` (`app_id`, `module_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 授权范围';

CREATE TABLE IF NOT EXISTS `un_openapi_ip_whitelist` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `client_id` BIGINT NOT NULL COMMENT '客户端 ID',
  `ip_value` VARCHAR(64) NOT NULL COMMENT 'IP 或 CIDR',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED、DISABLED',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_openapi_ip_whitelist_value` (`client_id`, `ip_value`),
  KEY `idx_un_openapi_ip_whitelist_status` (`client_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI IP 白名单';

CREATE TABLE IF NOT EXISTS `un_openapi_idempotent` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `client_id` BIGINT NOT NULL COMMENT '客户端 ID',
  `idempotent_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
  `request_hash` VARCHAR(128) NOT NULL COMMENT '请求摘要',
  `response_hash` VARCHAR(128) DEFAULT NULL COMMENT '响应摘要',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING、SUCCESS、FAILED',
  `expired_at` DATETIME NOT NULL COMMENT '过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_openapi_idempotent_key` (`client_id`, `idempotent_key`),
  KEY `idx_un_openapi_idempotent_expired` (`expired_at`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 幂等记录';

CREATE TABLE IF NOT EXISTS `un_openapi_access_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `client_id` BIGINT DEFAULT NULL COMMENT '客户端 ID',
  `request_id` VARCHAR(128) NOT NULL COMMENT '请求 ID',
  `request_path` VARCHAR(255) NOT NULL COMMENT '请求路径',
  `http_method` VARCHAR(16) NOT NULL COMMENT 'HTTP 方法',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS、FAILED',
  `response_code` VARCHAR(64) DEFAULT NULL COMMENT '响应码',
  `cost_ms` INT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `remote_ip` VARCHAR(64) DEFAULT NULL COMMENT '来源 IP',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误摘要',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_openapi_access_log_request` (`request_id`),
  KEY `idx_un_openapi_access_log_client` (`client_id`, `status`, `created_at`),
  KEY `idx_un_openapi_access_log_path` (`request_path`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OpenAPI 调用日志';

CREATE TABLE IF NOT EXISTS `un_sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(1000) DEFAULT NULL COMMENT '配置值',
  `config_type` VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT '类型：STRING、NUMBER、BOOLEAN、JSON',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '配置说明',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人账号 ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_un_sys_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';

CREATE TABLE IF NOT EXISTS `un_sys_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account_id` BIGINT DEFAULT NULL COMMENT '账号 ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录账号',
  `login_status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS、FAILED、LOCKED',
  `remote_ip` VARCHAR(64) DEFAULT NULL COMMENT '来源 IP',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '客户端 UA',
  `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_un_sys_login_log_account` (`account_id`, `created_at`),
  KEY `idx_un_sys_login_log_username` (`username`, `created_at`),
  KEY `idx_un_sys_login_log_status` (`login_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统登录日志';

CREATE TABLE IF NOT EXISTS `un_audit_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户 ID',
  `system_id` BIGINT DEFAULT NULL COMMENT '系统 ID',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人账号 ID',
  `operation_type` VARCHAR(64) NOT NULL COMMENT '操作类型',
  `target_type` VARCHAR(64) NOT NULL COMMENT '操作对象类型',
  `target_id` VARCHAR(128) DEFAULT NULL COMMENT '操作对象 ID',
  `request_source` VARCHAR(32) NOT NULL DEFAULT 'WEB' COMMENT '来源：WEB、MOBILE、OPENAPI、SYSTEM',
  `before_json` JSON DEFAULT NULL COMMENT '变更前',
  `after_json` JSON DEFAULT NULL COMMENT '变更后',
  `result_status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS、FAILED',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误摘要',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_un_audit_operation_log_ctx` (`tenant_id`, `system_id`, `created_at`),
  KEY `idx_un_audit_operation_log_operator` (`operator_id`, `created_at`),
  KEY `idx_un_audit_operation_log_target` (`target_type`, `target_id`),
  KEY `idx_un_audit_operation_log_result` (`result_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计操作日志';

INSERT INTO `un_platt_system` (`id`, `system_code`, `system_name`, `status`, `description`, `created_by`, `updated_by`)
VALUES (1, 'DEFAULT_SYSTEM', '默认业务系统', 'ENABLED', '初始化默认系统上下文', NULL, NULL)
ON DUPLICATE KEY UPDATE `system_name` = VALUES(`system_name`), `status` = VALUES(`status`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_tenant` (`id`, `tenant_code`, `tenant_name`, `status`, `admin_account_id`, `config_json`, `created_by`, `updated_by`)
VALUES (1, 'DEFAULT_TENANT', '默认租户', 'ENABLED', 1, JSON_OBJECT('timezone', 'Asia/Shanghai'), NULL, NULL)
ON DUPLICATE KEY UPDATE `tenant_name` = VALUES(`tenant_name`), `status` = VALUES(`status`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_account` (`id`, `username`, `display_name`, `password_hash`, `status`, `created_by`, `updated_by`)
VALUES (1, 'admin', '平台管理员', '{bcrypt}$2a$10$replaceWithRealHashAfterDeploy', 'ENABLED', NULL, NULL)
ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`), `status` = VALUES(`status`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_account_tenant` (`account_id`, `tenant_id`, `system_id`, `is_default`, `status`, `created_by`, `updated_by`)
VALUES (1, 1, 1, 1, 'ENABLED', NULL, NULL)
ON DUPLICATE KEY UPDATE `is_default` = VALUES(`is_default`), `status` = VALUES(`status`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_role` (`id`, `tenant_id`, `system_id`, `app_id`, `role_code`, `role_name`, `role_type`, `status`, `created_by`, `updated_by`)
VALUES
(1, 1, 1, NULL, 'PLATFORM_ADMIN', '平台超级管理员', 'PLATFORM', 'ENABLED', NULL, NULL),
(2, 1, 1, NULL, 'TENANT_ADMIN', '租户管理员', 'TENANT', 'ENABLED', NULL, NULL)
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `status` = VALUES(`status`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_permission` (`id`, `permission_code`, `permission_name`, `permission_type`, `resource_path`, `created_by`, `updated_by`)
VALUES
(1, 'PLATFORM_MANAGE', '平台管理', 'MENU', '/platform', NULL, NULL),
(2, 'APP_MANAGE', '应用管理', 'MENU', '/apps', NULL, NULL),
(3, 'MODULE_MANAGE', '模块管理', 'MENU', '/modules', NULL, NULL),
(4, 'FLOW_MANAGE', '流程管理', 'MENU', '/flows', NULL, NULL),
(5, 'UPLOAD_MANAGE', '文件管理', 'MENU', '/uploads', NULL, NULL),
(6, 'OPENAPI_MANAGE', 'OpenAPI 管理', 'MENU', '/openapi', NULL, NULL),
(7, 'AUDIT_VIEW', '审计查看', 'MENU', '/audit', NULL, NULL)
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_role_permission` (`role_id`, `permission_id`, `created_by`, `updated_by`)
VALUES
(1, 1, NULL, NULL), (1, 2, NULL, NULL), (1, 3, NULL, NULL), (1, 4, NULL, NULL), (1, 5, NULL, NULL), (1, 6, NULL, NULL), (1, 7, NULL, NULL),
(2, 2, NULL, NULL), (2, 3, NULL, NULL), (2, 4, NULL, NULL), (2, 5, NULL, NULL)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_account_role` (`account_id`, `role_id`, `tenant_id`, `system_id`, `created_by`, `updated_by`)
VALUES (1, 1, 1, 1, NULL, NULL), (1, 2, 1, 1, NULL, NULL)
ON DUPLICATE KEY UPDATE `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_dict` (`id`, `tenant_id`, `dict_code`, `dict_name`, `status`, `created_by`, `updated_by`)
VALUES (1, NULL, 'COMMON_STATUS', '通用状态', 'ENABLED', NULL, NULL)
ON DUPLICATE KEY UPDATE `dict_name` = VALUES(`dict_name`), `status` = VALUES(`status`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_platt_dict_item` (`dict_id`, `item_value`, `item_label`, `sort_order`, `status`, `created_by`, `updated_by`)
VALUES (1, 'ENABLED', '启用', 1, 'ENABLED', NULL, NULL), (1, 'DISABLED', '停用', 2, 'ENABLED', NULL, NULL)
ON DUPLICATE KEY UPDATE `item_label` = VALUES(`item_label`), `sort_order` = VALUES(`sort_order`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_sys_config` (`config_key`, `config_value`, `config_type`, `description`, `created_by`, `updated_by`)
VALUES
('upload.max-file-size-mb', '100', 'NUMBER', '单文件最大上传大小 MB', NULL, NULL),
('system.default-timezone', 'Asia/Shanghai', 'STRING', '默认时区', NULL, NULL),
('openapi.default-rate-limit-per-minute', '600', 'NUMBER', 'OpenAPI 默认每分钟限流', NULL, NULL)
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`), `updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `un_upload_storage_config` (`id`, `config_code`, `storage_type`, `base_path`, `status`, `created_by`, `updated_by`)
VALUES (1, 'LOCAL_DEFAULT', 'LOCAL', './data/uploads', 'ENABLED', NULL, NULL)
ON DUPLICATE KEY UPDATE `storage_type` = VALUES(`storage_type`), `base_path` = VALUES(`base_path`), `status` = VALUES(`status`), `updated_at` = CURRENT_TIMESTAMP;

SET FOREIGN_KEY_CHECKS = 1;
