-- source: init/V1__foundation.sql
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

-- source: update/V2__default_member_role.sql
-- 用途：为已有主租户补齐默认普通成员角色；新系统由创建事务直接初始化此角色。
-- 仅插入缺失角色，不改写既有授权、成员关系或业务数据；依赖 (tenant_id, code) 唯一约束保持幂等。
INSERT INTO `un_sys_role` (`system_id`, `tenant_id`, `code`, `name`, `status`)
SELECT tenant.`system_id`, tenant.`id`, 'member', '普通成员', 'ACTIVE'
FROM `un_sys_tenant` AS tenant
LEFT JOIN `un_sys_role` AS role
  ON role.`tenant_id` = tenant.`id` AND role.`code` = 'member'
WHERE tenant.`main_flag` = 1 AND role.`id` IS NULL;

-- source: update/V3__record_identity.sql
-- 用途：给通用业务记录增加可在列表与详情显示的标题和稳定编号。
-- 增量修改现有表，不删除旧记录；先为已有记录补值，再建立租户与模块内的编号唯一约束。
ALTER TABLE `un_module_record`
  ADD COLUMN `title` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '业务记录在列表与详情中显示的标题' AFTER `owner_member_id`,
  ADD COLUMN `record_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务记录在租户和模块内唯一的稳定编号' AFTER `title`;

-- 迁移已有数据：旧记录原来没有标题和编号，用主键生成可追溯占位值；新写入由业务服务生成编号。
UPDATE `un_module_record`
SET `title` = CONCAT('记录 ', `id`), `record_no` = CONCAT('R', `id`)
WHERE `record_no` = '';

ALTER TABLE `un_module_record`
  ADD UNIQUE KEY `uk_module_record_no` (`tenant_id`, `module_id`, `record_no`);

-- source: update/V4__basic_approval.sql
-- 用途：为已发布模块的业务记录增加最小可运行审批；旧记录保留 ACTIVE 状态，无需回填或重建。
-- 本阶段每个模块只配置一个指定成员审批节点；版本快照独立保存，后续扩展节点时不改写历史实例。
CREATE TABLE `un_flow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程定义主键',
  `system_id` BIGINT NOT NULL COMMENT '所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '所属主租户主键',
  `module_id` BIGINT NOT NULL COMMENT '绑定的业务模块主键',
  `name` VARCHAR(120) NOT NULL COMMENT '审批流程名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '流程状态，草稿或已发布',
  `published_version_id` BIGINT DEFAULT NULL COMMENT '当前生效的不可变发布版本主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '流程创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '流程更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_module` (`tenant_id`,`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块的基础审批流程定义';

CREATE TABLE `un_flow_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批草稿节点主键',
  `flow_id` BIGINT NOT NULL COMMENT '所属流程定义主键',
  `node_name` VARCHAR(120) NOT NULL COMMENT '节点展示名称',
  `approver_member_id` BIGINT NOT NULL COMMENT '指定审批人的系统成员主键',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '节点顺序，当前基础审批只允许一个节点',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '草稿节点创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '草稿节点更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_order` (`flow_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程草稿节点与指定审批人';

CREATE TABLE `un_flow_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程发布版本主键',
  `flow_id` BIGINT NOT NULL COMMENT '所属流程定义主键',
  `version_number` INT NOT NULL COMMENT '同流程内连续版本号',
  `snapshot_json` JSON NOT NULL COMMENT '发布时节点与审批人的不可变快照',
  `published_by_account_id` BIGINT NOT NULL COMMENT '执行发布的平台账号主键',
  `published_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_version_number` (`flow_id`,`version_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程不可变发布版本';

CREATE TABLE `un_flow_example` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批运行实例主键',
  `flow_id` BIGINT NOT NULL COMMENT '流程定义主键',
  `flow_version_id` BIGINT NOT NULL COMMENT '发起时采用的发布版本主键',
  `system_id` BIGINT NOT NULL COMMENT '实例所属系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '实例所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '实例绑定的业务模块主键',
  `record_id` BIGINT NOT NULL COMMENT '实例绑定的业务记录主键',
  `attempt_number` INT NOT NULL COMMENT '同一记录的发起轮次',
  `applicant_member_id` BIGINT NOT NULL COMMENT '发起人的系统成员主键',
  `snapshot_json` JSON NOT NULL COMMENT '实例创建时复制的流程版本快照',
  `status` VARCHAR(20) NOT NULL COMMENT '实例状态，待审批、通过或拒绝',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '实例发起时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '实例结束时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '实例乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_example_attempt` (`record_id`,`attempt_number`),
  KEY `idx_flow_example_scope` (`tenant_id`,`module_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记录的一轮审批运行实例';

CREATE TABLE `un_flow_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批待办任务主键',
  `example_id` BIGINT NOT NULL COMMENT '所属流程实例主键',
  `approver_member_id` BIGINT NOT NULL COMMENT '可办理任务的系统成员主键',
  `node_name` VARCHAR(120) NOT NULL COMMENT '发起时快照中的节点名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务待办、通过或拒绝状态',
  `opinion` VARCHAR(1000) DEFAULT NULL COMMENT '办理意见，拒绝时必填',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '待办产生时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '任务办理完成时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '任务乐观锁版本，阻止重复办理',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_todo` (`approver_member_id`,`status`,`create_time`),
  KEY `idx_flow_task_example` (`example_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指定审批人的流程待办任务';

CREATE TABLE `un_flow_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批事件主键',
  `example_id` BIGINT NOT NULL COMMENT '所属流程实例主键',
  `task_id` BIGINT DEFAULT NULL COMMENT '有关联时的待办任务主键',
  `actor_member_id` BIGINT NOT NULL COMMENT '执行操作的系统成员主键',
  `event_type` VARCHAR(20) NOT NULL COMMENT '提交、同意或拒绝事件类型',
  `opinion` VARCHAR(1000) DEFAULT NULL COMMENT '操作意见或拒绝原因',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_event_example` (`example_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变的审批操作历史';

-- source: update/V5__record_files_export.sql
-- 用途：为通用业务记录增加本地文件元数据/引用，并为模块数据导出保存异步任务状态。
-- 已有记录与审批数据不变；新表从空表开始，不删除或重建现有业务表。
CREATE TABLE `un_file_object` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件对象主键',
  `system_id` BIGINT NOT NULL COMMENT '文件所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '文件所属租户主键',
  `storage_key` VARCHAR(80) NOT NULL COMMENT '本地存储中随机生成的文件名',
  `original_name` VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
  `content_type` VARCHAR(120) NOT NULL COMMENT '服务端识别的安全文件类型',
  `size_bytes` BIGINT NOT NULL COMMENT '文件实际字节数',
  `sha256_hex` CHAR(64) NOT NULL COMMENT '文件内容的 SHA-256 摘要',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '文件可用或已移除状态',
  `created_by_account_id` BIGINT NOT NULL COMMENT '上传文件的平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件上传时间',
  `delete_time` DATETIME(3) DEFAULT NULL COMMENT '文件移除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`storage_key`),
  KEY `idx_file_object_scope` (`tenant_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地文件内容的元数据与存储定位';

CREATE TABLE `un_file_reference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件引用主键',
  `file_id` BIGINT NOT NULL COMMENT '被引用的文件对象主键',
  `record_id` BIGINT NOT NULL COMMENT '引用文件的业务记录主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '附件加入业务记录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_reference_file` (`file_id`),
  KEY `idx_file_reference_record` (`record_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记录与附件的引用关系';

CREATE TABLE `un_exchange_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块数据导出任务主键',
  `system_id` BIGINT NOT NULL COMMENT '任务所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '任务所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '任务导出的业务模块主键',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '提交任务的平台账号主键',
  `filter_json` JSON NOT NULL COMMENT '提交时的标题和字段筛选条件快照',
  `columns_json` JSON NOT NULL COMMENT '提交时选择的模块字段编码快照',
  `status` VARCHAR(20) NOT NULL DEFAULT 'QUEUED' COMMENT '排队、运行、成功或失败状态',
  `result_key` VARCHAR(80) DEFAULT NULL COMMENT '成功结果在本地存储中的随机文件名',
  `row_count` INT NOT NULL DEFAULT 0 COMMENT '导出成功的数据行数',
  `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '失败时给请求者展示的原因',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务提交时间',
  `start_time` DATETIME(3) DEFAULT NULL COMMENT '任务开始处理时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '任务结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_job_owner` (`tenant_id`,`requested_by_account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块筛选数据的异步导出任务';

-- source: update/V6__export_action_permission.sql
-- 用途：把模块导出作为可授权页面动作，避免仅凭查看权限取走批量业务数据。
-- 现有角色默认不获得导出权限；已有模块草稿补导出动作，管理员重新发布模块后才对运行态生效。
ALTER TABLE `un_sys_role_menu`
  ADD COLUMN `can_export` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许导出当前有权查看的模块记录' AFTER `can_delete`;

-- 迁移已有模块草稿：只补缺失的导出动作，不修改已有发布快照或授权。
INSERT INTO `un_module_action` (`module_id`, `code`, `name`, `action_type`, `enabled_flag`, `sort_order`)
SELECT m.`id`, 'export', '导出', 'EXPORT', 1, 3
FROM `un_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `un_module_action` a WHERE a.`module_id` = m.`id` AND a.`code` = 'export'
);
