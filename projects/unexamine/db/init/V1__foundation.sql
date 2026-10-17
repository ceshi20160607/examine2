-- 用途：建立首条“账号、系统、模块配置、业务记录”旅程的数据基础。
-- 本版本只用于全新隔离库 unexamine_r15；旧项目库不执行本脚本，也不从旧库复制测试数据。

CREATE TABLE `un_plat_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台账号主键',
  `username` VARCHAR(80) NOT NULL COMMENT '平台唯一登录用户名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '带算法与盐信息的口令哈希编码',
  `display_name` VARCHAR(120) NOT NULL COMMENT '账号展示名称',
  `email` VARCHAR(255) DEFAULT NULL COMMENT '账号绑定邮箱',
  `mobile` VARCHAR(40) DEFAULT NULL COMMENT '账号绑定手机号',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号启用状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '账号创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '账号更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '账号乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_username` (`username`),
  UNIQUE KEY `uk_plat_account_email` (`email`),
  UNIQUE KEY `uk_plat_account_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台统一账号';

CREATE TABLE `un_sys_system` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务系统主键',
  `owner_account_id` BIGINT NOT NULL COMMENT '系统所属平台账号主键',
  `code` VARCHAR(80) NOT NULL COMMENT '系统唯一编码',
  `name` VARCHAR(160) NOT NULL COMMENT '系统显示名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '系统用途说明',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '系统启用状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '系统创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '系统更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '系统乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_system_code` (`code`),
  KEY `idx_sys_system_owner` (`owner_account_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台内的业务系统';

CREATE TABLE `un_sys_tenant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统租户主键',
  `system_id` BIGINT NOT NULL COMMENT '所属业务系统主键',
  `code` VARCHAR(80) NOT NULL COMMENT '系统内租户编码',
  `name` VARCHAR(160) NOT NULL COMMENT '租户显示名称',
  `main_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统自动创建的主租户',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '租户启用状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '租户创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '租户更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_code` (`system_id`,`code`),
  KEY `idx_sys_tenant_main` (`system_id`,`main_flag`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务系统租户及默认主租户';

CREATE TABLE `un_sys_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统成员主键',
  `system_id` BIGINT NOT NULL COMMENT '成员所属业务系统主键',
  `account_id` BIGINT NOT NULL COMMENT '成员对应平台账号主键',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '系统成员状态',
  `join_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入系统时间',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '成员关系创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '成员关系更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_account` (`system_id`,`account_id`),
  KEY `idx_sys_member_account` (`account_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号在业务系统中的成员关系';

CREATE TABLE `un_sys_tenant_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户成员关系主键',
  `system_id` BIGINT NOT NULL COMMENT '租户与成员共同所属系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '所属租户主键',
  `member_id` BIGINT NOT NULL COMMENT '所属系统成员主键',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '租户成员准入状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入租户时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '租户成员关系更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_member` (`tenant_id`,`member_id`),
  KEY `idx_sys_tenant_member_scope` (`system_id`,`member_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统成员在具体租户中的准入关系';

CREATE TABLE `un_sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户角色主键',
  `system_id` BIGINT NOT NULL COMMENT '角色所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '角色所属租户主键',
  `code` VARCHAR(80) NOT NULL COMMENT '租户内角色编码',
  `name` VARCHAR(120) NOT NULL COMMENT '角色显示名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色启用状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '角色创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '角色更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '角色乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`,`code`),
  KEY `idx_sys_role_scope` (`system_id`,`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户业务角色';

CREATE TABLE `un_sys_member_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '成员角色关系主键',
  `tenant_id` BIGINT NOT NULL COMMENT '授权所属租户主键',
  `member_id` BIGINT NOT NULL COMMENT '被授权系统成员主键',
  `role_id` BIGINT NOT NULL COMMENT '授予的租户角色主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '角色授权时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_role` (`tenant_id`,`member_id`,`role_id`),
  KEY `idx_sys_member_role_role` (`role_id`,`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统成员与租户角色关系';

CREATE TABLE `un_module_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块组主键',
  `system_id` BIGINT NOT NULL COMMENT '模块组所属业务系统主键',
  `code` VARCHAR(80) NOT NULL COMMENT '系统内模块组编码',
  `name` VARCHAR(120) NOT NULL COMMENT '运行态顶栏模块组名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '模块组显示顺序',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '模块组启用状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '模块组创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '模块组更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_group_code` (`system_id`,`code`),
  KEY `idx_module_group_order` (`system_id`,`status`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统运行态模块分组';

CREATE TABLE `un_module` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务模块主键',
  `system_id` BIGINT NOT NULL COMMENT '模块所属业务系统主键',
  `group_id` BIGINT NOT NULL COMMENT '模块所在模块组主键',
  `code` VARCHAR(80) NOT NULL COMMENT '系统内业务模块编码',
  `name` VARCHAR(160) NOT NULL COMMENT '业务模块显示名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '模块业务用途说明',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '模块草稿、发布或停用状态',
  `draft_revision` INT NOT NULL DEFAULT 0 COMMENT '当前草稿修订号',
  `published_version_id` BIGINT DEFAULT NULL COMMENT '当前生效的发布快照主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '模块创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '模块更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '模块乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_code` (`system_id`,`code`),
  KEY `idx_module_group` (`group_id`,`status`),
  KEY `idx_module_published_version` (`published_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块草稿与当前发布指针';

CREATE TABLE `un_module_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块字段主键',
  `module_id` BIGINT NOT NULL COMMENT '字段所属业务模块主键',
  `field_key` VARCHAR(80) NOT NULL COMMENT '模块内稳定的字段编码',
  `label` VARCHAR(160) NOT NULL COMMENT '字段在表单和列表中的名称',
  `field_type` VARCHAR(32) NOT NULL COMMENT '文本、数字、日期、布尔等字段类型',
  `required_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '新增和编辑时是否必填',
  `searchable_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许作为筛选条件',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
  `options_json` JSON DEFAULT NULL COMMENT '选择类字段的候选项配置',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '字段启用状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '字段创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '字段更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_field_key` (`module_id`,`field_key`),
  KEY `idx_module_field_order` (`module_id`,`status`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块的可配置字段草稿';

CREATE TABLE `un_module_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块菜单主键',
  `module_id` BIGINT NOT NULL COMMENT '菜单指向的业务模块主键',
  `code` VARCHAR(80) NOT NULL COMMENT '模块内菜单编码',
  `name` VARCHAR(120) NOT NULL COMMENT '运行态菜单显示名称',
  `menu_type` VARCHAR(20) NOT NULL DEFAULT 'LIST' COMMENT '列表或其他模块页面入口类型',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '菜单显示顺序',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '菜单启用状态',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '菜单创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '菜单更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_menu_code` (`module_id`,`code`),
  KEY `idx_module_menu_order` (`module_id`,`status`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块运行态菜单草稿';

CREATE TABLE `un_module_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块动作主键',
  `module_id` BIGINT NOT NULL COMMENT '动作所属业务模块主键',
  `code` VARCHAR(40) NOT NULL COMMENT '新增、编辑、查看等动作编码',
  `name` VARCHAR(120) NOT NULL COMMENT '页面动作显示名称',
  `action_type` VARCHAR(40) NOT NULL COMMENT '模块动作的执行类别',
  `enabled_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '动作是否在发布后可用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '动作在页面中的显示顺序',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '动作创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '动作更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_action_code` (`module_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块页面动作草稿';

CREATE TABLE `un_sys_role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色菜单授权主键',
  `role_id` BIGINT NOT NULL COMMENT '被授权租户角色主键',
  `menu_id` BIGINT NOT NULL COMMENT '被授权模块菜单主键',
  `can_view` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许查看菜单和记录',
  `can_create` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许新增记录',
  `can_update` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许编辑记录',
  `can_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许删除记录',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '角色菜单授权创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '角色菜单授权更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_menu` (`role_id`,`menu_id`),
  KEY `idx_sys_role_menu_menu` (`menu_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色对模块菜单和基础记录动作的授权';

CREATE TABLE `un_module_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块发布版本主键',
  `module_id` BIGINT NOT NULL COMMENT '所属业务模块主键',
  `version_number` INT NOT NULL COMMENT '模块内连续发布版本号',
  `snapshot_json` JSON NOT NULL COMMENT '发布时字段、菜单和动作的不可变配置快照',
  `published_by_account_id` BIGINT NOT NULL COMMENT '执行发布的平台账号主键',
  `published_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '模块版本发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_version_number` (`module_id`,`version_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块不可变发布快照';

CREATE TABLE `un_module_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通用业务记录主键',
  `system_id` BIGINT NOT NULL COMMENT '记录所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '记录所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '记录所属业务模块主键',
  `module_version_id` BIGINT NOT NULL COMMENT '记录创建时生效的模块发布版本主键',
  `owner_member_id` BIGINT NOT NULL COMMENT '记录当前负责人系统成员主键',
  `record_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '记录业务状态',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建记录的平台账号主键',
  `updated_by_account_id` BIGINT NOT NULL COMMENT '最近修改记录的平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '记录乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_module_record_list` (`tenant_id`,`module_id`,`record_status`,`create_time`),
  KEY `idx_module_record_owner` (`tenant_id`,`owner_member_id`,`record_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义模块的通用业务主记录';

CREATE TABLE `un_module_record_data` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录字段值主键',
  `record_id` BIGINT NOT NULL COMMENT '所属业务主记录主键',
  `field_id` BIGINT NOT NULL COMMENT '对应模块字段主键',
  `value_json` JSON NOT NULL COMMENT '保留原始类型和内容的字段值',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '字段值创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '字段值更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_record_data_field` (`record_id`,`field_id`),
  KEY `idx_module_record_data_field` (`field_id`,`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用业务记录的自定义字段原始值';

CREATE TABLE `un_module_record_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录查询索引主键',
  `record_id` BIGINT NOT NULL COMMENT '所属业务主记录主键',
  `field_id` BIGINT NOT NULL COMMENT '可筛选的模块字段主键',
  `text_value` VARCHAR(255) DEFAULT NULL COMMENT '用于精确和前缀筛选的文本值',
  `number_value` DECIMAL(30,10) DEFAULT NULL COMMENT '用于范围筛选的数字值',
  `date_value` DATETIME(3) DEFAULT NULL COMMENT '用于时间范围筛选的日期值',
  `boolean_value` TINYINT DEFAULT NULL COMMENT '用于布尔筛选的字段值',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '查询索引值更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_record_index_field` (`record_id`,`field_id`),
  KEY `idx_module_record_index_text` (`field_id`,`text_value`,`record_id`),
  KEY `idx_module_record_index_number` (`field_id`,`number_value`,`record_id`),
  KEY `idx_module_record_index_date` (`field_id`,`date_value`,`record_id`),
  KEY `idx_module_record_index_boolean` (`field_id`,`boolean_value`,`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可筛选字段的类型化查询索引值';
