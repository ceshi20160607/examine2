SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `examine1`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `examine1`;

CREATE TABLE IF NOT EXISTS `platform_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台账号主键',
  `account` VARCHAR(64) NOT NULL COMMENT '登录账号',
  `real_name` VARCHAR(64) NOT NULL COMMENT '姓名',
  `mobile` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ENABLED-启用，DISABLED-停用',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最近登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_account_account` (`account`),
  KEY `idx_platform_account_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台账号';

CREATE TABLE IF NOT EXISTS `tenant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户主键',
  `tenant_name` VARCHAR(128) NOT NULL COMMENT '租户名称',
  `tenant_code` VARCHAR(64) NOT NULL COMMENT '租户编码',
  `owner_account_id` BIGINT DEFAULT NULL COMMENT '负责人账号ID，逻辑关联platform_account.id',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '租户状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_code`),
  KEY `idx_tenant_owner` (`owner_account_id`),
  KEY `idx_tenant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户';

CREATE TABLE IF NOT EXISTS `business_system` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统主键，即systemId',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID，逻辑关联tenant.id',
  `system_name` VARCHAR(128) NOT NULL COMMENT '系统名称',
  `system_code` VARCHAR(64) NOT NULL COMMENT '系统编码',
  `owner_account_id` BIGINT NOT NULL COMMENT '系统拥有者账号ID，逻辑关联platform_account.id',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '系统描述',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '系统状态：DRAFT-草稿，ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_system_tenant_code` (`tenant_id`, `system_code`),
  KEY `idx_business_system_owner` (`owner_account_id`),
  KEY `idx_business_system_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务系统';

CREATE TABLE IF NOT EXISTS `department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID，逻辑关联business_system.id',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID，逻辑关联tenant.id',
  `parent_id` BIGINT DEFAULT NULL COMMENT '上级部门ID，逻辑关联department.id',
  `dept_name` VARCHAR(128) NOT NULL COMMENT '部门名称',
  `dept_code` VARCHAR(64) NOT NULL COMMENT '部门编码',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '部门状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_system_code` (`system_id`, `tenant_id`, `dept_code`),
  KEY `idx_department_parent` (`system_id`, `tenant_id`, `parent_id`),
  KEY `idx_department_status` (`system_id`, `tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统部门';

CREATE TABLE IF NOT EXISTS `system_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '成员主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID，逻辑关联business_system.id',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID，逻辑关联tenant.id',
  `account_id` BIGINT NOT NULL COMMENT '平台账号ID，逻辑关联platform_account.id',
  `department_id` BIGINT DEFAULT NULL COMMENT '部门ID，逻辑关联department.id',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '成员状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_member_account` (`system_id`, `tenant_id`, `account_id`),
  KEY `idx_system_member_department` (`system_id`, `tenant_id`, `department_id`),
  KEY `idx_system_member_status` (`system_id`, `tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统成员';

CREATE TABLE IF NOT EXISTS `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色主键',
  `system_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属系统ID，0表示平台级角色',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID，0表示平台级角色',
  `role_name` VARCHAR(128) NOT NULL COMMENT '角色名称',
  `role_type` VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '角色类型：PLATFORM-平台，SYSTEM-系统，APP-应用',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '角色状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_scope_name` (`system_id`, `tenant_id`, `role_name`),
  KEY `idx_role_type_status` (`role_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE IF NOT EXISTS `member_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '成员角色关系主键',
  `member_id` BIGINT NOT NULL COMMENT '成员ID，逻辑关联system_member.id',
  `role_id` BIGINT NOT NULL COMMENT '角色ID，逻辑关联role.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_role` (`member_id`, `role_id`),
  KEY `idx_member_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成员角色关系';

CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限主键',
  `role_id` BIGINT NOT NULL COMMENT '角色ID，逻辑关联role.id',
  `system_id` BIGINT NOT NULL DEFAULT 0 COMMENT '授权系统ID，0表示平台权限',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '授权租户ID，0表示平台权限',
  `resource_type` VARCHAR(20) NOT NULL COMMENT '资源类型：MENU-菜单，PAGE-页面，FIELD-字段，ACTION-动作，DATA_SCOPE-数据范围',
  `resource_id` BIGINT DEFAULT NULL COMMENT '资源ID，动作权限可为空',
  `action_code` VARCHAR(64) DEFAULT NULL COMMENT '动作权限标识',
  `field_access` VARCHAR(20) DEFAULT NULL COMMENT '字段权限：VISIBLE-可见，EDITABLE-可编辑，READONLY-只读，HIDDEN-隐藏',
  `data_scope` VARCHAR(20) DEFAULT NULL COMMENT '数据范围：SELF-本人，DEPT-本人部门，CHILD_DEPT-下级部门，ASSIGNED_DEPT-指定部门，ALL-全部',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission_resource` (`role_id`, `resource_type`, `resource_id`, `action_code`),
  KEY `idx_role_permission_scope` (`system_id`, `tenant_id`, `resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限';

CREATE TABLE IF NOT EXISTS `app` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用主键，即appId',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID，逻辑关联business_system.id',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID，逻辑关联tenant.id',
  `app_name` VARCHAR(128) NOT NULL COMMENT '应用名称',
  `app_code` VARCHAR(64) NOT NULL COMMENT '应用编码',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '应用状态：DRAFT-草稿，ENABLED-启用，DISABLED-停用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `current_version_id` BIGINT DEFAULT NULL COMMENT '当前发布版本ID，逻辑关联app_version.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_scope_code` (`system_id`, `tenant_id`, `app_code`),
  KEY `idx_app_status_sort` (`system_id`, `tenant_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用';

CREATE TABLE IF NOT EXISTS `app_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用配置版本主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `app_id` BIGINT NOT NULL COMMENT '应用ID，逻辑关联app.id',
  `version_no` INT NOT NULL COMMENT '版本号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '配置版本状态：DRAFT-草稿，PUBLISHED-已发布，DISABLED-已停用，ROLLED_BACK-已回滚',
  `version_note` VARCHAR(500) DEFAULT NULL COMMENT '版本说明',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_version_no` (`app_id`, `version_no`),
  KEY `idx_app_version_status` (`system_id`, `tenant_id`, `app_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用配置版本';

CREATE TABLE IF NOT EXISTS `module` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块主键，即moduleId',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `app_id` BIGINT NOT NULL COMMENT '应用ID，逻辑关联app.id',
  `module_name` VARCHAR(128) NOT NULL COMMENT '模块名称',
  `module_code` VARCHAR(64) NOT NULL COMMENT '模块编码',
  `module_type` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '模块类型：NORMAL-普通模块，SUB_TABLE-子表模块',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '模块状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_scope_code` (`system_id`, `tenant_id`, `app_id`, `module_code`),
  KEY `idx_module_status` (`system_id`, `tenant_id`, `app_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块';

CREATE TABLE IF NOT EXISTS `module_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字段主键，即fieldId',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `module_id` BIGINT NOT NULL COMMENT '模块ID，逻辑关联module.id',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码',
  `field_name` VARCHAR(128) NOT NULL COMMENT '字段名称',
  `field_type` VARCHAR(32) NOT NULL COMMENT '字段类型：TEXT、LONG_TEXT、NUMBER、AMOUNT、DATE、DATETIME、BOOLEAN、SINGLE_SELECT、MULTI_SELECT、DICTIONARY、DEPARTMENT、MEMBER、ATTACHMENT、RELATION_RECORD、SUB_TABLE、AUTO_NUMBER、FORMULA、READONLY',
  `required_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
  `unique_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否唯一：0-否，1-是',
  `default_value` VARCHAR(1000) DEFAULT NULL COMMENT '默认值',
  `enum_source` VARCHAR(64) DEFAULT NULL COMMENT '枚举或字典来源',
  `validate_rule` JSON DEFAULT NULL COMMENT '校验规则JSON',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '字段状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_field_code` (`system_id`, `tenant_id`, `module_id`, `field_code`),
  KEY `idx_module_field_sort` (`module_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块字段';

CREATE TABLE IF NOT EXISTS `field_option` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字段选项主键',
  `field_id` BIGINT NOT NULL COMMENT '字段ID，逻辑关联module_field.id',
  `option_label` VARCHAR(128) NOT NULL COMMENT '选项名称',
  `option_value` VARCHAR(128) NOT NULL COMMENT '选项值',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '选项状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_option_value` (`field_id`, `option_value`),
  KEY `idx_field_option_sort` (`field_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字段选项';

CREATE TABLE IF NOT EXISTS `page_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '页面主键，即pageId',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `module_id` BIGINT NOT NULL COMMENT '模块ID，逻辑关联module.id',
  `page_type` VARCHAR(20) NOT NULL COMMENT '页面类型：LIST-列表，FORM-表单，DETAIL-详情',
  `app_version_id` BIGINT DEFAULT NULL COMMENT '应用配置版本ID，逻辑关联app_version.id',
  `layout_json` JSON NOT NULL COMMENT '页面布局JSON',
  `block_json` JSON DEFAULT NULL COMMENT '页面块配置JSON',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '页面状态：DRAFT-草稿，PUBLISHED-已发布，DISABLED-已停用，ROLLED_BACK-已回滚',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_page_config_module_type` (`system_id`, `tenant_id`, `module_id`, `page_type`, `status`),
  KEY `idx_page_config_version` (`app_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='页面配置';

CREATE TABLE IF NOT EXISTS `runtime_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单主键，即menuId',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父级菜单ID，逻辑关联runtime_menu.id',
  `app_id` BIGINT DEFAULT NULL COMMENT '绑定应用ID，逻辑关联app.id',
  `module_id` BIGINT DEFAULT NULL COMMENT '绑定模块ID，逻辑关联module.id',
  `page_id` BIGINT DEFAULT NULL COMMENT '绑定页面ID，逻辑关联page_config.id',
  `menu_name` VARCHAR(128) NOT NULL COMMENT '菜单名称',
  `menu_code` VARCHAR(64) NOT NULL COMMENT '菜单编码',
  `permission_code` VARCHAR(128) NOT NULL COMMENT '权限标识',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '菜单状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_runtime_menu_code` (`system_id`, `tenant_id`, `menu_code`),
  UNIQUE KEY `uk_runtime_menu_permission` (`system_id`, `tenant_id`, `permission_code`),
  KEY `idx_runtime_menu_parent` (`system_id`, `tenant_id`, `parent_id`, `sort_order`),
  KEY `idx_runtime_menu_bind` (`app_id`, `module_id`, `page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行台菜单';

CREATE TABLE IF NOT EXISTS `data_dictionary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `dict_code` VARCHAR(64) NOT NULL COMMENT '字典编码',
  `dict_name` VARCHAR(128) NOT NULL COMMENT '字典名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '字典状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_data_dictionary_code` (`system_id`, `tenant_id`, `dict_code`),
  KEY `idx_data_dictionary_status` (`system_id`, `tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典';

CREATE TABLE IF NOT EXISTS `dictionary_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典项主键',
  `dict_id` BIGINT NOT NULL COMMENT '字典ID，逻辑关联data_dictionary.id',
  `item_label` VARCHAR(128) NOT NULL COMMENT '字典项名称',
  `item_value` VARCHAR(64) NOT NULL COMMENT '字典项值',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '字典项状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dictionary_item_value` (`dict_id`, `item_value`),
  KEY `idx_dictionary_item_sort` (`dict_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项';

CREATE TABLE IF NOT EXISTS `business_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务记录主键，即recordId',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `app_id` BIGINT NOT NULL COMMENT '所属应用ID',
  `module_id` BIGINT NOT NULL COMMENT '所属模块ID',
  `record_no` VARCHAR(64) NOT NULL COMMENT '记录编号',
  `record_status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '记录状态：DRAFT-草稿，SUBMITTED-已提交，ARCHIVED-已归档，DELETED-已删除',
  `process_status` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '流程状态：NONE-无流程，RUNNING-运行中，APPROVED-已通过，REJECTED-已拒绝，WITHDRAWN-已撤回，TERMINATED-已终止',
  `app_version_id` BIGINT DEFAULT NULL COMMENT '运行时配置版本ID，逻辑关联app_version.id',
  `config_snapshot` JSON DEFAULT NULL COMMENT '运行时配置快照',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
  `created_by` BIGINT NOT NULL COMMENT '创建人账号ID，逻辑关联platform_account.id',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人账号ID，逻辑关联platform_account.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_record_no` (`system_id`, `tenant_id`, `module_id`, `record_no`, `is_deleted`),
  KEY `idx_business_record_list` (`system_id`, `tenant_id`, `module_id`, `record_status`, `updated_at`),
  KEY `idx_business_record_creator` (`created_by`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录';

CREATE TABLE IF NOT EXISTS `record_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录值主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `module_id` BIGINT NOT NULL COMMENT '所属模块ID',
  `record_id` BIGINT NOT NULL COMMENT '业务记录ID，逻辑关联business_record.id',
  `field_id` BIGINT NOT NULL COMMENT '字段ID，逻辑关联module_field.id',
  `string_value` VARCHAR(2000) DEFAULT NULL COMMENT '字符串、枚举、关联等值',
  `number_value` DECIMAL(30,8) DEFAULT NULL COMMENT '数字或金额值',
  `datetime_value` DATETIME DEFAULT NULL COMMENT '日期时间值',
  `boolean_value` TINYINT DEFAULT NULL COMMENT '布尔值：0-否，1-是',
  `json_value` JSON DEFAULT NULL COMMENT '多选、附件、子表、公式等复杂值',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_value_field` (`record_id`, `field_id`, `is_deleted`),
  KEY `idx_record_value_field_string` (`system_id`, `tenant_id`, `module_id`, `field_id`, `string_value`(191)),
  KEY `idx_record_value_field_number` (`system_id`, `tenant_id`, `module_id`, `field_id`, `number_value`),
  KEY `idx_record_value_field_datetime` (`system_id`, `tenant_id`, `module_id`, `field_id`, `datetime_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录字段值';

CREATE TABLE IF NOT EXISTS `record_unique_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '唯一字段值主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `module_id` BIGINT NOT NULL COMMENT '所属模块ID',
  `record_id` BIGINT NOT NULL COMMENT '业务记录ID',
  `field_id` BIGINT NOT NULL COMMENT '字段ID',
  `value_hash` VARCHAR(64) NOT NULL COMMENT '唯一字段值哈希',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_unique_value` (`system_id`, `tenant_id`, `module_id`, `field_id`, `value_hash`, `is_deleted`),
  KEY `idx_record_unique_value_record` (`record_id`, `field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记录唯一字段值';

CREATE TABLE IF NOT EXISTS `record_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `record_id` BIGINT NOT NULL COMMENT '业务记录ID',
  `comment_content` VARCHAR(1000) NOT NULL COMMENT '评论内容',
  `created_by` BIGINT NOT NULL COMMENT '评论人账号ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_comment_record` (`system_id`, `tenant_id`, `record_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录评论';

CREATE TABLE IF NOT EXISTS `file_object` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件主键，即fileId',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `storage_path` VARCHAR(500) NOT NULL COMMENT '存储位置',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
  `file_size` BIGINT NOT NULL COMMENT '文件大小，单位字节',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT '文件MIME类型',
  `status` VARCHAR(20) NOT NULL DEFAULT 'TEMP' COMMENT '附件状态：TEMP-临时，LINKED-已关联，DELETED-已删除',
  `created_by` BIGINT DEFAULT NULL COMMENT '上传人账号ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_object_scope_status` (`system_id`, `tenant_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据';

CREATE TABLE IF NOT EXISTS `file_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件关联主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `file_id` BIGINT NOT NULL COMMENT '文件ID，逻辑关联file_object.id',
  `relation_type` VARCHAR(20) NOT NULL COMMENT '关联对象类型：RECORD-业务记录，TASK-流程任务，IMPORT_EXPORT-导入导出任务',
  `relation_id` BIGINT NOT NULL COMMENT '关联对象ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_relation` (`file_id`, `relation_type`, `relation_id`),
  KEY `idx_file_relation_target` (`system_id`, `tenant_id`, `relation_type`, `relation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件关联';

CREATE TABLE IF NOT EXISTS `import_export_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '导入导出任务主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `app_id` BIGINT DEFAULT NULL COMMENT '所属应用ID',
  `module_id` BIGINT DEFAULT NULL COMMENT '所属模块ID',
  `task_type` VARCHAR(20) NOT NULL COMMENT '任务类型：IMPORT-导入，EXPORT-导出',
  `template_id` BIGINT DEFAULT NULL COMMENT '导入导出模板ID',
  `task_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待执行，RUNNING-执行中，SUCCESS-成功，FAILED-失败，CANCELED-已取消',
  `failure_reason` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `result_file_id` BIGINT DEFAULT NULL COMMENT '结果文件ID，逻辑关联file_object.id',
  `created_by` BIGINT NOT NULL COMMENT '创建人账号ID',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_import_export_task_list` (`system_id`, `tenant_id`, `module_id`, `task_type`, `task_status`, `created_at`),
  KEY `idx_import_export_task_creator` (`created_by`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导入导出任务';

CREATE TABLE IF NOT EXISTS `workflow_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程模板主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `module_id` BIGINT NOT NULL COMMENT '绑定模块ID',
  `template_name` VARCHAR(128) NOT NULL COMMENT '流程模板名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '流程模板状态：DRAFT-草稿，PUBLISHED-已发布，DISABLED-停用',
  `current_version_id` BIGINT DEFAULT NULL COMMENT '当前流程版本ID，逻辑关联workflow_version.id',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_template_module_name` (`system_id`, `tenant_id`, `module_id`, `template_name`),
  KEY `idx_workflow_template_status` (`system_id`, `tenant_id`, `module_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程模板';

CREATE TABLE IF NOT EXISTS `workflow_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程版本主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `template_id` BIGINT NOT NULL COMMENT '流程模板ID，逻辑关联workflow_template.id',
  `version_no` INT NOT NULL COMMENT '版本号',
  `node_json` JSON NOT NULL COMMENT '节点配置JSON',
  `edge_json` JSON NOT NULL COMMENT '连线配置JSON',
  `condition_json` JSON DEFAULT NULL COMMENT '条件配置JSON',
  `setting_json` JSON DEFAULT NULL COMMENT '流程设置JSON',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '流程版本状态：DRAFT-草稿，PUBLISHED-已发布，DISABLED-已停用，ROLLED_BACK-已回滚',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_version_no` (`template_id`, `version_no`),
  KEY `idx_workflow_version_status` (`system_id`, `tenant_id`, `template_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程版本';

CREATE TABLE IF NOT EXISTS `workflow_instance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程实例主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `module_id` BIGINT NOT NULL COMMENT '绑定模块ID',
  `record_id` BIGINT NOT NULL COMMENT '业务记录ID',
  `template_id` BIGINT NOT NULL COMMENT '流程模板ID',
  `version_id` BIGINT NOT NULL COMMENT '流程版本ID',
  `started_by` BIGINT NOT NULL COMMENT '发起人账号ID',
  `business_snapshot` JSON DEFAULT NULL COMMENT '发起时业务快照',
  `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '流程实例状态：RUNNING-运行中，APPROVED-已通过，REJECTED-已拒绝，WITHDRAWN-已撤回，TERMINATED-已终止',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_workflow_instance_record` (`system_id`, `tenant_id`, `module_id`, `record_id`),
  KEY `idx_workflow_instance_status` (`system_id`, `tenant_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程实例';

CREATE TABLE IF NOT EXISTS `workflow_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程任务主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `instance_id` BIGINT NOT NULL COMMENT '流程实例ID，逻辑关联workflow_instance.id',
  `task_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待处理，APPROVED-已同意，REJECTED-已拒绝，TRANSFERRED-已转交，WITHDRAWN-已撤回，TERMINATED-已终止，SKIPPED-已跳过',
  `assignee_id` BIGINT DEFAULT NULL COMMENT '实际处理人账号ID',
  `candidate_json` JSON DEFAULT NULL COMMENT '候选人配置JSON',
  `comment` VARCHAR(1000) DEFAULT NULL COMMENT '处理意见',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_workflow_task_assignee` (`assignee_id`, `task_status`, `updated_at`),
  KEY `idx_workflow_task_instance` (`system_id`, `tenant_id`, `instance_id`, `task_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程任务';

CREATE TABLE IF NOT EXISTS `openapi_client` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OpenAPI应用主键',
  `system_id` BIGINT NOT NULL COMMENT '授权系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '授权租户ID',
  `client_id` VARCHAR(64) NOT NULL COMMENT '外部应用标识',
  `client_name` VARCHAR(128) NOT NULL COMMENT '外部应用名称',
  `rate_limit_rule` JSON DEFAULT NULL COMMENT '限流规则JSON',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '应用状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_client_id` (`client_id`),
  KEY `idx_openapi_client_scope` (`system_id`, `tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAPI应用';

CREATE TABLE IF NOT EXISTS `openapi_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OpenAPI凭证主键',
  `client_pk` BIGINT NOT NULL COMMENT 'OpenAPI应用主键，逻辑关联openapi_client.id',
  `key_version` INT NOT NULL COMMENT '密钥版本',
  `secret_digest` VARCHAR(255) NOT NULL COMMENT '加密密钥摘要，不保存明文密钥',
  `expires_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '凭证状态：ENABLED-启用，DISABLED-禁用，EXPIRED-过期，ROTATED-已轮换',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_credential_version` (`client_pk`, `key_version`),
  KEY `idx_openapi_credential_status` (`client_pk`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAPI凭证';

CREATE TABLE IF NOT EXISTS `openapi_scope` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OpenAPI授权范围主键',
  `client_pk` BIGINT NOT NULL COMMENT 'OpenAPI应用主键，逻辑关联openapi_client.id',
  `scope_type` VARCHAR(20) NOT NULL COMMENT '授权范围类型：SYSTEM-系统，TENANT-租户，APP-应用，MODULE-模块，ACTION-动作，FIELD-字段',
  `scope_value` VARCHAR(128) NOT NULL COMMENT '授权范围值',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_scope` (`client_pk`, `scope_type`, `scope_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAPI授权范围';

CREATE TABLE IF NOT EXISTS `openapi_ip_whitelist` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OpenAPI IP白名单主键',
  `client_pk` BIGINT NOT NULL COMMENT 'OpenAPI应用主键，逻辑关联openapi_client.id',
  `ip_value` VARCHAR(64) NOT NULL COMMENT '白名单IP或网段',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_ip_whitelist` (`client_pk`, `ip_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAPI IP白名单';

CREATE TABLE IF NOT EXISTS `openapi_idempotency` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OpenAPI幂等记录主键',
  `client_pk` BIGINT NOT NULL COMMENT 'OpenAPI应用主键，逻辑关联openapi_client.id',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键',
  `request_hash` VARCHAR(64) NOT NULL COMMENT '请求摘要哈希',
  `response_snapshot` JSON DEFAULT NULL COMMENT '首次响应快照',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '幂等状态：PROCESSING-处理中，SUCCESS-成功，FAILED-失败',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openapi_idempotency` (`client_pk`, `idempotency_key`),
  KEY `idx_openapi_idempotency_status` (`client_pk`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OpenAPI幂等记录';

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计日志主键',
  `system_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属系统ID，0表示平台级',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID，0表示平台级',
  `actor_type` VARCHAR(20) NOT NULL COMMENT '操作主体类型：ACCOUNT-平台账号，OPENAPI-外部应用，SYSTEM-系统',
  `actor_id` VARCHAR(64) DEFAULT NULL COMMENT '操作主体ID',
  `action_type` VARCHAR(64) NOT NULL COMMENT '操作类型',
  `target_type` VARCHAR(64) DEFAULT NULL COMMENT '目标对象类型',
  `target_id` VARCHAR(64) DEFAULT NULL COMMENT '目标对象ID',
  `result` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果：SUCCESS-成功，FAILED-失败',
  `trace_id` VARCHAR(128) DEFAULT NULL COMMENT '请求追踪标识',
  `detail_json` JSON DEFAULT NULL COMMENT '日志详情JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_log_query` (`system_id`, `tenant_id`, `action_type`, `created_at`),
  KEY `idx_audit_log_trace` (`trace_id`),
  KEY `idx_audit_log_actor` (`actor_type`, `actor_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志';

CREATE TABLE IF NOT EXISTS `global_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '全局配置主键',
  `system_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属系统ID，0表示平台级配置',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID，0表示平台级配置',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(1000) DEFAULT NULL COMMENT '配置值，不保存明文敏感值',
  `secret_placeholder_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否仍为敏感占位：0-否，1-是',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '配置状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_global_config_key` (`system_id`, `tenant_id`, `config_key`),
  KEY `idx_global_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局配置';

CREATE TABLE IF NOT EXISTS `serial_sequence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自动编号序号主键',
  `system_id` BIGINT NOT NULL COMMENT '所属系统ID',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户ID',
  `module_id` BIGINT NOT NULL COMMENT '所属模块ID',
  `sequence_key` VARCHAR(128) NOT NULL COMMENT '序号规则键',
  `prefix_rule` VARCHAR(128) DEFAULT NULL COMMENT '编号前缀规则',
  `next_value` BIGINT NOT NULL DEFAULT 1 COMMENT '下一个序号值',
  `step_value` INT NOT NULL DEFAULT 1 COMMENT '序号步长',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '序号状态：ENABLED-启用，DISABLED-停用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_serial_sequence_key` (`system_id`, `tenant_id`, `module_id`, `sequence_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动编号序号';

INSERT INTO `platform_account` (`account`, `real_name`, `mobile`, `email`, `password_hash`, `status`)
VALUES ('admin', '平台管理员', NULL, NULL, '{RESET_REQUIRED}', 'ENABLED')
ON DUPLICATE KEY UPDATE
  `real_name` = VALUES(`real_name`),
  `status` = VALUES(`status`);

INSERT INTO `tenant` (`tenant_name`, `tenant_code`, `owner_account_id`, `status`)
SELECT '默认租户', 'default', `id`, 'ENABLED'
FROM `platform_account`
WHERE `account` = 'admin'
ON DUPLICATE KEY UPDATE
  `tenant_name` = VALUES(`tenant_name`),
  `status` = VALUES(`status`);

INSERT INTO `business_system` (`tenant_id`, `system_name`, `system_code`, `owner_account_id`, `description`, `status`)
SELECT t.`id`, '默认业务系统', 'default_system', a.`id`, '初始化默认系统', 'ENABLED'
FROM `tenant` t
JOIN `platform_account` a ON a.`account` = 'admin'
WHERE t.`tenant_code` = 'default'
ON DUPLICATE KEY UPDATE
  `system_name` = VALUES(`system_name`),
  `owner_account_id` = VALUES(`owner_account_id`),
  `status` = VALUES(`status`);

INSERT INTO `role` (`system_id`, `tenant_id`, `role_name`, `role_type`, `status`)
VALUES (0, 0, '平台管理员', 'PLATFORM', 'ENABLED')
ON DUPLICATE KEY UPDATE
  `role_type` = VALUES(`role_type`),
  `status` = VALUES(`status`);

INSERT INTO `global_config` (`system_id`, `tenant_id`, `config_key`, `config_value`, `secret_placeholder_flag`, `status`)
VALUES
  (0, 0, 'OPENAPI_SECRET_PLACEHOLDER_CHECK', 'ENABLED', 1, 'ENABLED'),
  (0, 0, 'STORAGE_HEALTH_CHECK', 'ENABLED', 0, 'ENABLED'),
  (0, 0, 'SCRIPT_VERSION_CHECK', 'ENABLED', 0, 'ENABLED')
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `secret_placeholder_flag` = VALUES(`secret_placeholder_flag`),
  `status` = VALUES(`status`);
