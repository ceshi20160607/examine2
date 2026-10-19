-- 模块基础结构。仅使用《表意义.md》的原表名；旧 SQL 只提供字段语义参考。
-- 先执行 00_database.sql。既有业务数据的后续结构变化须另写增量迁移。
USE `unexamine`;

CREATE TABLE IF NOT EXISTS `un_module_group` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块分组主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级分组主键，0 为根分组',
    `code` VARCHAR(64) NOT NULL COMMENT '系统内唯一的分组编码',
    `name` VARCHAR(100) NOT NULL COMMENT '分组名称',
    `icon` VARCHAR(100) NULL COMMENT '分组图标标识',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '同级显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '分组状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_group_system_code` (`system_id`, `code`),
    KEY `idx_module_group_parent` (`system_id`, `parent_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块分组配置';

CREATE TABLE IF NOT EXISTS `un_module` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '业务模块主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `group_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块分组主键',
    `code` VARCHAR(64) NOT NULL COMMENT '系统内唯一的模块编码',
    `name` VARCHAR(100) NOT NULL COMMENT '模块名称',
    `icon` VARCHAR(100) NULL COMMENT '模块图标标识',
    `description` VARCHAR(500) NULL COMMENT '模块业务说明',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '分组内显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '模块状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_system_code` (`system_id`, `code`),
    KEY `idx_module_group_sort` (`group_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块';

CREATE TABLE IF NOT EXISTS `un_module_dict` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块字典选项主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `module_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属模块主键，0 为系统共用字典',
    `dict_code` VARCHAR(64) NOT NULL COMMENT '字典编码',
    `item_code` VARCHAR(64) NOT NULL COMMENT '字典选项编码',
    `item_name` VARCHAR(100) NOT NULL COMMENT '字典选项名称',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '选项显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '选项状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_dict_item` (`system_id`, `module_id`, `dict_code`, `item_code`),
    KEY `idx_module_dict_list` (`system_id`, `module_id`, `dict_code`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统数据字典基础';

CREATE TABLE IF NOT EXISTS `un_module_field` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块字段主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `field_name` VARCHAR(64) NOT NULL COMMENT '稳定的字段英文编码',
    `name` VARCHAR(100) NOT NULL COMMENT '字段显示名称',
    `type` VARCHAR(32) NOT NULL COMMENT '字段类型编码，如 text、number、datetime、select',
    `remark` VARCHAR(255) NULL COMMENT '字段业务说明',
    `input_tips` VARCHAR(255) NULL COMMENT '表单输入提示',
    `max_length` INT NULL COMMENT '文本允许的最大字符数',
    `default_value` TEXT NULL COMMENT '新建表单的默认值',
    `must_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填：1 是，0 否',
    `union_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '模块内字段值是否唯一：1 是，0 否',
    `hidden_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否隐藏：1 是，0 否',
    `add_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '新建表单是否显示：1 是，0 否',
    `index_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '默认列表是否显示：1 是，0 否',
    `detail_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '详情是否显示：1 是，0 否',
    `sortable_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '列表是否允许按此字段排序：1 是，0 否',
    `dict_code` VARCHAR(64) NULL COMMENT '单选或多选字段引用的字典编码',
    `option_data` JSON NULL COMMENT '字段专用选项与显示配置',
    `precision_scale` TINYINT UNSIGNED NULL COMMENT '数字字段保留的小数位数',
    `min_num_restrict` DECIMAL(30,8) NULL COMMENT '数字字段允许的最小值',
    `max_num_restrict` DECIMAL(30,8) NULL COMMENT '数字字段允许的最大值',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '字段状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_field_name` (`module_id`, `field_name`),
    KEY `idx_module_field_status` (`module_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块字段';

CREATE TABLE IF NOT EXISTS `un_module_menu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块菜单配置主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `menu_code` VARCHAR(64) NOT NULL COMMENT '模块内唯一的菜单或操作编码',
    `menu_name` VARCHAR(100) NOT NULL COMMENT '菜单或操作显示名称',
    `menu_type` VARCHAR(32) NOT NULL COMMENT '菜单类型：list、detail、create、edit、delete 等',
    `permission_code` VARCHAR(100) NOT NULL COMMENT '授权检查使用的权限编码',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '菜单显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '菜单状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_menu_code` (`module_id`, `menu_code`),
    KEY `idx_module_menu_sort` (`module_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块菜单配置';

CREATE TABLE IF NOT EXISTS `un_module_action` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块动作主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `action_code` VARCHAR(64) NOT NULL COMMENT '模块内唯一的动作编码',
    `name` VARCHAR(100) NOT NULL COMMENT '动作显示名称',
    `action_type` VARCHAR(32) NOT NULL COMMENT '动作类型，如状态变更、提交审批或启动流程',
    `permission_code` VARCHAR(100) NOT NULL COMMENT '执行动作所需权限编码',
    `config_json` JSON NULL COMMENT '动作参数与字段赋值配置',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '动作显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '动作状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_action_code` (`module_id`, `action_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块动作配置';

CREATE TABLE IF NOT EXISTS `un_module_rule` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块规则主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `rule_code` VARCHAR(64) NOT NULL COMMENT '模块内唯一的规则编码',
    `name` VARCHAR(100) NOT NULL COMMENT '规则名称',
    `trigger_type` VARCHAR(32) NOT NULL COMMENT '规则触发方式，如保存或执行动作',
    `condition_json` JSON NULL COMMENT '规则生效条件',
    `effect_json` JSON NOT NULL COMMENT '字段校验或业务处理配置',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '规则执行顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '规则状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_rule_code` (`module_id`, `rule_code`),
    KEY `idx_module_rule_order` (`module_id`, `trigger_type`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块规则配置';

CREATE TABLE IF NOT EXISTS `un_module_query_field` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块查询字段配置主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `field_name` VARCHAR(64) NOT NULL COMMENT '可用于查询的字段编码',
    `operators_json` JSON NOT NULL COMMENT '此字段允许使用的查询操作符',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '查询项显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '查询项状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_query_field` (`module_id`, `field_name`),
    KEY `idx_module_query_order` (`module_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询模块字段配置';

CREATE TABLE IF NOT EXISTS `un_module_tenant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户模块关系主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '开放给租户的基础模块主键',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '租户内模块显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '租户模块状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_tenant` (`tenant_id`, `module_id`),
    KEY `idx_module_tenant_list` (`tenant_id`, `status`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户对基础模块关系记录';

CREATE TABLE IF NOT EXISTS `un_module_tenant_field` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户字段扩展主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `base_field_id` BIGINT UNSIGNED NULL COMMENT '被覆盖的基础字段主键，空值表示租户新增字段',
    `field_name` VARCHAR(64) NOT NULL COMMENT '有效字段英文编码',
    `name` VARCHAR(100) NOT NULL COMMENT '租户内字段显示名称',
    `type` VARCHAR(32) NULL COMMENT '租户新增字段类型；覆盖基础字段时沿用原类型',
    `remark` VARCHAR(255) NULL COMMENT '租户字段业务说明',
    `input_tips` VARCHAR(255) NULL COMMENT '租户表单输入提示',
    `max_length` INT NULL COMMENT '新增文本字段的最大字符数',
    `default_value` TEXT NULL COMMENT '租户表单默认值',
    `union_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '租户字段值是否唯一：1 是，0 否',
    `must_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '租户表单是否必填：1 是，0 否',
    `hidden_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '租户内是否隐藏：1 是，0 否',
    `add_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '租户新建表单是否显示：1 是，0 否',
    `index_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '租户默认列表是否显示：1 是，0 否',
    `detail_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '租户详情是否显示：1 是，0 否',
    `sortable_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '租户列表是否允许排序：1 是，0 否',
    `dict_code` VARCHAR(64) NULL COMMENT '租户字段引用的字典编码',
    `option_data` JSON NULL COMMENT '租户字段选项与显示配置',
    `precision_scale` TINYINT UNSIGNED NULL COMMENT '租户数字字段保留的小数位数',
    `min_num_restrict` DECIMAL(30,8) NULL COMMENT '租户数字字段允许的最小值',
    `max_num_restrict` DECIMAL(30,8) NULL COMMENT '租户数字字段允许的最大值',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '租户字段状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_tenant_field_name` (`tenant_id`, `module_id`, `field_name`),
    UNIQUE KEY `uk_module_tenant_base_field` (`tenant_id`, `module_id`, `base_field_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户对字段的扩展记录';

CREATE TABLE IF NOT EXISTS `un_module_field_sort` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块字段排序主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属租户主键，0 为基础模块默认顺序',
    `view_type` VARCHAR(16) NOT NULL COMMENT '排序适用位置：form、detail 或 list',
    `field_name` VARCHAR(64) NOT NULL COMMENT '需要排序的字段编码',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '排序配置创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '顺序更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_field_sort` (`module_id`, `tenant_id`, `view_type`, `field_name`),
    KEY `idx_module_field_sort_order` (`module_id`, `tenant_id`, `view_type`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块字段排序';

CREATE TABLE IF NOT EXISTS `un_module_record` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块业务记录主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '记录所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '记录所属模块主键',
    `name` VARCHAR(255) NULL COMMENT '记录显示名称',
    `num` VARCHAR(64) NULL COMMENT '模块内业务编号',
    `remark` VARCHAR(500) NULL COMMENT '记录备注',
    `record_status` TINYINT NOT NULL DEFAULT 1 COMMENT '记录状态：0 草稿，1 正常，2 已删除',
    `owner_member_id` BIGINT UNSIGNED NULL COMMENT '记录负责人主键',
    `owner_department_id` BIGINT UNSIGNED NULL COMMENT '记录归属部门主键',
    `examine_example_id` BIGINT UNSIGNED NULL COMMENT '当前关联的审批实例主键',
    `flow_example_id` BIGINT UNSIGNED NULL COMMENT '当前关联的流程实例主键',
    `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '更新版本号，用于避免并发覆盖',
    `create_member_id` BIGINT UNSIGNED NOT NULL COMMENT '创建人成员主键',
    `update_member_id` BIGINT UNSIGNED NOT NULL COMMENT '最近修改人成员主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `delete_time` DATETIME(3) NULL COMMENT '逻辑删除时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_record_num` (`tenant_id`, `module_id`, `num`),
    KEY `idx_module_record_page` (`tenant_id`, `module_id`, `record_status`, `create_time`, `id`),
    KEY `idx_module_record_owner` (`tenant_id`, `owner_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块通用业务主记录';

-- 动态值保留原始文本/集合，并分列保存数值与时间；列表按对应类型列排序后再分页。
CREATE TABLE IF NOT EXISTS `un_module_record_data` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块业务字段值主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '记录所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '记录所属模块主键',
    `record_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务主记录主键',
    `field_name` VARCHAR(64) NOT NULL COMMENT '基础或租户扩展字段的稳定编码',
    `value_type` VARCHAR(16) NOT NULL COMMENT '值的存储类型：text、number、time 或 json',
    `value_text` LONGTEXT NULL COMMENT '文本值，文本排序按完整内容比较',
    `value_number` DECIMAL(30,8) NULL COMMENT '数值字段的可比较数值',
    `value_time` DATETIME(3) NULL COMMENT '日期时间字段的可比较时间',
    `value_json` JSON NULL COMMENT '多选、文件等集合结构的值',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_record_field` (`record_id`, `field_name`),
    KEY `idx_module_value_number` (`tenant_id`, `module_id`, `field_name`, `value_number`, `record_id`),
    KEY `idx_module_value_time` (`tenant_id`, `module_id`, `field_name`, `value_time`, `record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块通用业务扩展记录';

CREATE TABLE IF NOT EXISTS `un_module_scence` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块场景主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '场景所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '场景所属模块主键',
    `owner_member_id` BIGINT UNSIGNED NULL COMMENT '个人场景的所属成员主键，共享场景可为空',
    `name` VARCHAR(100) NOT NULL COMMENT '场景名称',
    `visibility` VARCHAR(16) NOT NULL COMMENT '场景可见范围：personal、tenant 或 public',
    `default_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为适用范围内默认场景：1 是，0 否',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '场景状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_module_scence_list` (`tenant_id`, `module_id`, `visibility`, `owner_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块场景';

CREATE TABLE IF NOT EXISTS `un_module_scence_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块场景配置主键',
    `scence_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块场景主键',
    `columns_json` JSON NOT NULL COMMENT '列表表头的字段、显隐、宽度与顺序配置',
    `filters_json` JSON NULL COMMENT '场景默认筛选条件',
    `sort_json` JSON NULL COMMENT '场景默认字段排序及方向',
    `page_size` SMALLINT UNSIGNED NOT NULL DEFAULT 20 COMMENT '场景默认每页记录数',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '配置创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '配置更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_scence_config` (`scence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块场景配置';

CREATE TABLE IF NOT EXISTS `un_module_teamer` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块团队成员关系主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属模块主键',
    `record_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务记录主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '参与成员主键',
    `role_code` VARCHAR(32) NOT NULL COMMENT '记录团队中的成员角色编码',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_teamer_member` (`record_id`, `member_id`, `role_code`),
    KEY `idx_module_teamer_member` (`tenant_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块团队成员';

CREATE TABLE IF NOT EXISTS `un_module_relation_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块关联配置主键',
    `source_module_id` BIGINT UNSIGNED NOT NULL COMMENT '关联起点模块主键',
    `target_module_id` BIGINT UNSIGNED NOT NULL COMMENT '关联终点模块主键',
    `relation_code` VARCHAR(64) NOT NULL COMMENT '起点模块内唯一的关联编码',
    `name` VARCHAR(100) NOT NULL COMMENT '关联显示名称',
    `direction` VARCHAR(16) NOT NULL DEFAULT 'forward' COMMENT '关联方向：forward 或 both',
    `mapping_json` JSON NULL COMMENT '上下游记录的字段映射配置',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '关联配置状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_relation_code` (`source_module_id`, `relation_code`),
    KEY `idx_module_relation_target` (`target_module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块上下游关联关系配置';

CREATE TABLE IF NOT EXISTS `un_module_relation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块记录关联主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '关联所属租户主键',
    `relation_config_id` BIGINT UNSIGNED NOT NULL COMMENT '使用的模块关联配置主键',
    `source_record_id` BIGINT UNSIGNED NOT NULL COMMENT '关联起点记录主键',
    `target_record_id` BIGINT UNSIGNED NOT NULL COMMENT '关联终点记录主键',
    `create_member_id` BIGINT UNSIGNED NOT NULL COMMENT '建立关联的成员主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关联创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_relation_record` (`tenant_id`, `relation_config_id`, `source_record_id`, `target_record_id`),
    KEY `idx_module_relation_target_record` (`tenant_id`, `target_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块关联关系';

CREATE TABLE IF NOT EXISTS `un_module_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块变动日志主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '记录所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '记录所属模块主键',
    `record_id` BIGINT UNSIGNED NOT NULL COMMENT '发生变动的业务记录主键',
    `actor_member_id` BIGINT UNSIGNED NOT NULL COMMENT '执行变动的成员主键',
    `operation_code` VARCHAR(64) NOT NULL COMMENT '业务操作编码',
    `before_json` JSON NULL COMMENT '变动前涉及的字段和值',
    `after_json` JSON NULL COMMENT '变动后涉及的字段和值',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '变动发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_module_log_record` (`tenant_id`, `module_id`, `record_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块变动记录';

CREATE TABLE IF NOT EXISTS `un_module_examine` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块审批绑定主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '适用租户主键，0 为模块默认绑定',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务模块主键',
    `examine_id` BIGINT UNSIGNED NOT NULL COMMENT '所绑定的审批定义主键',
    `binding_code` VARCHAR(64) NOT NULL COMMENT '模块内审批绑定编码',
    `start_action_code` VARCHAR(64) NOT NULL COMMENT '触发审批的模块动作编码',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '绑定状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_examine_binding` (`tenant_id`, `module_id`, `binding_code`),
    KEY `idx_module_examine_definition` (`examine_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块审批';

CREATE TABLE IF NOT EXISTS `un_module_flow` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块流程绑定主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '适用租户主键，0 为模块默认绑定',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务模块主键',
    `flow_id` BIGINT UNSIGNED NOT NULL COMMENT '所绑定的流程定义主键',
    `binding_code` VARCHAR(64) NOT NULL COMMENT '模块内流程绑定编码',
    `start_action_code` VARCHAR(64) NOT NULL COMMENT '触发流程的模块动作编码',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '绑定状态：1 启用，0 停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_flow_binding` (`tenant_id`, `module_id`, `binding_code`),
    KEY `idx_module_flow_definition` (`flow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块流程';
