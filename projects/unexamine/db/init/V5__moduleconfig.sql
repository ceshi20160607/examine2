-- moduleconfig 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_cfg_module_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `icon` varchar(100) DEFAULT NULL COMMENT '展示图标',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序值',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `created_by_member_id` bigint NOT NULL COMMENT '创建系统成员标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_group_code` (`owner_tenant_id`,`code`),
  KEY `idx_cfg_module_group_owner_id` (`owner_tenant_id`,`id`),
  KEY `idx_cfg_module_group_system` (`system_id`,`owner_tenant_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块分组配置';

CREATE TABLE `un_cfg_module` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `group_id` bigint NOT NULL COMMENT '所属模块分组标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '业务说明',
  `title_field_code` varchar(100) DEFAULT NULL COMMENT '记录标题字段编码',
  `number_sequence_code` varchar(100) DEFAULT NULL COMMENT '记录编号序列编码',
  `status_field_code` varchar(100) DEFAULT NULL COMMENT '记录状态字段编码',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `created_by_member_id` bigint NOT NULL COMMENT '创建系统成员标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_code` (`owner_tenant_id`,`code`),
  KEY `idx_cfg_module_system_id` (`system_id`,`id`),
  KEY `idx_cfg_module_owner_id` (`owner_tenant_id`,`id`),
  KEY `idx_cfg_module_context_id` (`system_id`,`owner_tenant_id`,`id`),
  KEY `idx_cfg_module_group` (`group_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务模块草稿定义';

CREATE TABLE `un_cfg_dictionary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `dictionary_type` varchar(32) NOT NULL DEFAULT 'LIST' COMMENT '字典业务类型',
  `category_code` varchar(100) DEFAULT NULL COMMENT '字典分类编码',
  `built_in` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统内置字典',
  `hierarchical` tinyint NOT NULL DEFAULT '0' COMMENT '是否为层级字典',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `created_by_member_id` bigint NOT NULL COMMENT '创建系统成员标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_code` (`owner_tenant_id`,`code`),
  KEY `idx_cfg_dictionary_owner_id` (`owner_tenant_id`,`id`),
  KEY `idx_cfg_dictionary_system_id` (`system_id`,`id`),
  KEY `idx_cfg_dictionary_context_id` (`system_id`,`owner_tenant_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统数据字典定义';

CREATE TABLE `un_cfg_dictionary_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `dictionary_id` bigint NOT NULL COMMENT '所属字典标识',
  `parent_id` bigint DEFAULT NULL COMMENT '上级配置项标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `label` varchar(200) NOT NULL COMMENT '选项显示文本',
  `path_code` varchar(1000) NOT NULL COMMENT '选项层级编码路径',
  `depth_level` int NOT NULL DEFAULT '0' COMMENT '选项所在层级，根节点为零',
  `depth_path` varchar(1000) NOT NULL DEFAULT '' COMMENT '从根节点到当前选项的标识路径，使用逗号分隔',
  `color` varchar(32) DEFAULT NULL COMMENT '选项展示颜色',
  `icon` varchar(100) DEFAULT NULL COMMENT '展示图标',
  `semantic_code` varchar(64) DEFAULT NULL COMMENT '选项语义编码',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否为默认选项',
  `board_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许作为看板分组选项',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序值',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_item_code` (`dictionary_id`,`code`),
  KEY `idx_cfg_dictionary_item_dict_id` (`dictionary_id`,`id`),
  KEY `idx_cfg_dictionary_item_parent` (`dictionary_id`,`parent_id`,`sort_order`),
  CONSTRAINT `ck_cfg_dictionary_item_depth` CHECK ((`depth_level` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典草稿选项';

CREATE TABLE `un_cfg_dictionary_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `dictionary_id` bigint NOT NULL COMMENT '所属字典标识',
  `version_number` int NOT NULL COMMENT '发布版本序号',
  `draft_revision` int NOT NULL COMMENT '草稿修订号',
  `snapshot_json` json NOT NULL COMMENT '发布时冻结的完整配置快照',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '字典发布快照内容摘要',
  `published_by_member_id` bigint NOT NULL COMMENT '发布系统成员标识',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_version_number` (`dictionary_id`,`version_number`),
  KEY `idx_cfg_dictionary_version_dict_id` (`dictionary_id`,`id`),
  KEY `idx_cfg_dictionary_version_context` (`system_id`,`owner_tenant_id`,`dictionary_id`),
  CONSTRAINT `ck_cfg_dictionary_version_snapshot` CHECK (((json_type(`snapshot_json`) = _utf8mb4'OBJECT') and json_contains_path(`snapshot_json`,_utf8mb4'all',_utf8mb4'$.dictionary',_utf8mb4'$.items')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的数据字典发布版本';

CREATE TABLE `un_cfg_dictionary_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `dictionary_id` bigint NOT NULL COMMENT '所属字典标识',
  `current_version_id` bigint NOT NULL COMMENT '当前生效版本标识',
  `published_by_member_id` bigint NOT NULL COMMENT '发布系统成员标识',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_publication` (`dictionary_id`),
  KEY `idx_cfg_dictionary_publication_context` (`system_id`,`owner_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典当前生效发布版本指针';

CREATE TABLE `un_cfg_module_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `field_type` varchar(64) NOT NULL COMMENT '字段业务类型',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填',
  `unique_value` tinyint NOT NULL DEFAULT '0' COMMENT '是否要求业务值唯一',
  `searchable` tinyint NOT NULL DEFAULT '0' COMMENT '是否进入搜索范围',
  `dictionary_id` bigint DEFAULT NULL COMMENT '所属字典标识',
  `reference_module_id` bigint DEFAULT NULL COMMENT '关联数据来源模块标识',
  `parent_field_id` bigint DEFAULT NULL COMMENT '所属子表字段标识，空值表示主记录字段',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序值',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `config_json` json NOT NULL COMMENT '字段或动作的类型专属配置',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_field_code` (`module_id`,`code`),
  KEY `idx_cfg_module_field_module_id` (`module_id`,`id`),
  KEY `idx_cfg_module_field_parent_id` (`module_id`,`parent_field_id`,`id`),
  KEY `idx_cfg_module_field_order` (`module_id`,`status`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块字段草稿定义';

CREATE TABLE `un_cfg_module_page` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `page_type` varchar(32) NOT NULL COMMENT '页面用途类型',
  `page_code` varchar(100) NOT NULL COMMENT '模块内稳定页面编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `layout_json` json NOT NULL COMMENT '页面布局草稿配置',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_page_code` (`module_id`,`page_code`),
  UNIQUE KEY `uk_cfg_module_page_standard_type` (`module_id`,((case when (`page_type` <> _utf8mb4'CUSTOM') then `page_type` else NULL end))),
  CONSTRAINT `ck_cfg_module_page_code` CHECK ((char_length(trim(`page_code`)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块页面草稿配置';

CREATE TABLE `un_cfg_module_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `parent_id` bigint DEFAULT NULL COMMENT '上级配置项标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `icon` varchar(100) DEFAULT NULL COMMENT '展示图标',
  `route_path` varchar(255) NOT NULL COMMENT '菜单跳转路径',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序值',
  `visible` tinyint NOT NULL DEFAULT '1' COMMENT '是否在导航中显示',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_menu_code` (`owner_tenant_id`,`code`),
  KEY `idx_cfg_module_menu_owner_id` (`owner_tenant_id`,`id`),
  KEY `idx_cfg_module_menu_module_id` (`module_id`,`id`),
  KEY `idx_cfg_module_menu_parent` (`owner_tenant_id`,`parent_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块菜单草稿配置';

CREATE TABLE `un_cfg_module_action` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `code` varchar(64) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(100) NOT NULL COMMENT '显示名称',
  `action_type` varchar(64) NOT NULL COMMENT '动作执行类型',
  `location` varchar(64) NOT NULL COMMENT '动作展示位置',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序值',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `config_json` json NOT NULL COMMENT '字段或动作的类型专属配置',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_action_code` (`module_id`,`code`),
  KEY `idx_cfg_module_action_module_id` (`module_id`,`id`),
  KEY `idx_cfg_module_action_location` (`module_id`,`location`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块动作草稿配置';

CREATE TABLE `un_cfg_module_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `rule_type` varchar(64) NOT NULL COMMENT '规则业务类型',
  `trigger_event` varchar(64) NOT NULL COMMENT '规则触发事件',
  `expression_text` text NOT NULL COMMENT '规则表达式文本',
  `message_template` varchar(1000) DEFAULT NULL COMMENT '规则失败提示模板',
  `test_status` varchar(32) NOT NULL DEFAULT 'NOT_TESTED' COMMENT '草稿规则最近一次测试状态',
  `last_test_input_json` json DEFAULT NULL COMMENT '最近一次规则测试输入快照',
  `last_test_result_json` json DEFAULT NULL COMMENT '最近一次规则测试结果快照',
  `last_tested_by_member_id` bigint DEFAULT NULL COMMENT '最近一次执行测试的系统成员标识',
  `last_tested_time` datetime(3) DEFAULT NULL COMMENT '最近一次规则测试时间',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示排序值',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_rule_code` (`module_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块规则草稿配置';

CREATE TABLE `un_cfg_query_index` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `code` varchar(100) NOT NULL COMMENT '范围内稳定编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `unique_index` tinyint NOT NULL DEFAULT '0' COMMENT '是否为业务唯一索引',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_query_index_code` (`module_id`,`code`),
  KEY `idx_cfg_query_index_module_id` (`module_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块查询索引草稿定义';

CREATE TABLE `un_cfg_query_index_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `query_index_id` bigint NOT NULL COMMENT '所属查询索引标识',
  `field_id` bigint NOT NULL COMMENT '索引使用的模块字段标识',
  `sort_order` int NOT NULL COMMENT '显示排序值',
  `sort_direction` varchar(8) DEFAULT NULL COMMENT '索引字段排序方向',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_query_index_field` (`query_index_id`,`field_id`),
  UNIQUE KEY `uk_cfg_query_index_order` (`query_index_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='查询索引包含的模块字段';

CREATE TABLE `un_cfg_module_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `version_number` int NOT NULL COMMENT '发布版本序号',
  `draft_revision` int NOT NULL COMMENT '草稿修订号',
  `schema_hash` varchar(64) NOT NULL COMMENT '发布配置内容摘要',
  `snapshot_json` json NOT NULL COMMENT '发布时冻结的完整配置快照',
  `change_summary` varchar(1000) NOT NULL COMMENT '本次发布变更摘要',
  `published_by_member_id` bigint NOT NULL COMMENT '发布系统成员标识',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_version_number` (`module_id`,`version_number`),
  KEY `idx_cfg_module_version_module_id` (`module_id`,`id`),
  KEY `idx_cfg_module_version_tenant` (`system_id`,`owner_tenant_id`,`publish_time`),
  KEY `idx_cfg_module_version_revision` (`module_id`,`draft_revision`),
  CONSTRAINT `ck_cfg_module_version_snapshot` CHECK (((json_type(`snapshot_json`) = _utf8mb4'OBJECT') and json_contains_path(`snapshot_json`,_utf8mb4'all',_utf8mb4'$.module',_utf8mb4'$.fields',_utf8mb4'$.pages',_utf8mb4'$.actions')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的模块发布版本及完整配置快照';

CREATE TABLE `un_cfg_module_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置所属租户标识',
  `module_id` bigint NOT NULL COMMENT '所属模块标识',
  `current_version_id` bigint NOT NULL COMMENT '当前生效版本标识',
  `updated_by_member_id` bigint NOT NULL COMMENT '最近更新系统成员标识',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_publication_module` (`module_id`),
  KEY `idx_cfg_module_publication_tenant` (`system_id`,`owner_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块当前生效发布版本指针';

CREATE TABLE `un_cfg_tenant_extension` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `tenant_id` bigint NOT NULL COMMENT '扩展所属租户标识',
  `base_module_id` bigint NOT NULL COMMENT '扩展的基础模块标识',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `extension_json` json NOT NULL COMMENT '租户扩展草稿配置',
  `status` varchar(32) NOT NULL COMMENT '生命周期状态',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前生效的租户扩展发布版本标识',
  `updated_by_member_id` bigint DEFAULT NULL COMMENT '最近切换发布版本的系统成员标识',
  `created_by_member_id` bigint NOT NULL COMMENT '创建系统成员标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_tenant_extension` (`tenant_id`,`base_module_id`),
  KEY `idx_cfg_tenant_extension_context_id` (`system_id`,`id`,`base_module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户对基础模块的扩展草稿';

CREATE TABLE `un_cfg_tenant_extension_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键标识',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `extension_id` bigint NOT NULL COMMENT '所属租户扩展标识',
  `base_module_id` bigint NOT NULL COMMENT '扩展的基础模块标识',
  `version_number` int NOT NULL COMMENT '发布版本序号',
  `base_module_version_id` bigint NOT NULL COMMENT '扩展所基于的模块发布版本标识',
  `snapshot_json` json NOT NULL COMMENT '发布时冻结的完整配置快照',
  `schema_hash` varchar(64) NOT NULL COMMENT '发布配置内容摘要',
  `published_by_member_id` bigint NOT NULL COMMENT '发布系统成员标识',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_extension_version` (`extension_id`,`version_number`),
  KEY `idx_cfg_extension_version_owner_id` (`extension_id`,`id`),
  CONSTRAINT `ck_cfg_extension_version_snapshot` CHECK ((json_type(`snapshot_json`) = _utf8mb4'OBJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的租户扩展发布版本';
