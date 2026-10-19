-- 应用接入、工作管理、仪表盘、数据源、KPI 和打印的基础表。
-- 仅保留实际功能所需的 ID 关联，跨域归属与权限由后端校验。
USE `unexamine`;

CREATE TABLE IF NOT EXISTS `un_app` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '应用主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `tenant_id` BIGINT UNSIGNED NULL COMMENT '限定租户主键，空值表示系统范围',
    `app_key` VARCHAR(64) NOT NULL COMMENT '应用访问标识',
    `app_name` VARCHAR(100) NOT NULL COMMENT '应用名称',
    `secret_hash` VARCHAR(100) NOT NULL COMMENT '应用密钥摘要',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '应用状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_key` (`app_key`),
    KEY `idx_app_system_tenant` (`system_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台或系统范围内的受控访问应用定义';

CREATE TABLE IF NOT EXISTS `un_app_event` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '应用事件配置主键',
    `app_id` BIGINT UNSIGNED NOT NULL COMMENT '所属应用主键',
    `event_key` VARCHAR(80) NOT NULL COMMENT '事件编码',
    `callback_url` VARCHAR(1000) NOT NULL COMMENT '接收事件的回调地址',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '事件配置状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_event` (`app_id`, `event_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用事件配置';

CREATE TABLE IF NOT EXISTS `un_work_project` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作项目主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `project_code` VARCHAR(64) NOT NULL COMMENT '租户内项目编码',
    `project_name` VARCHAR(120) NOT NULL COMMENT '项目名称',
    `description` TEXT NULL COMMENT '项目说明',
    `owner_member_id` BIGINT UNSIGNED NOT NULL COMMENT '项目负责人系统成员主键',
    `status` VARCHAR(24) NOT NULL DEFAULT 'active' COMMENT '项目状态：进行中或归档',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_project_code` (`tenant_id`, `project_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作项目';

CREATE TABLE IF NOT EXISTS `un_work_project_member` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目参与人关系主键',
    `project_id` BIGINT UNSIGNED NOT NULL COMMENT '所属工作项目主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '参与的系统成员主键',
    `role_code` VARCHAR(32) NOT NULL DEFAULT 'member' COMMENT '项目内角色编码',
    `join_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入项目时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_project_member` (`project_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作项目参与人';

CREATE TABLE IF NOT EXISTS `un_work_task_group` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目任务组主键',
    `project_id` BIGINT UNSIGNED NOT NULL COMMENT '所属工作项目主键',
    `group_name` VARCHAR(100) NOT NULL COMMENT '任务组名称',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '同项目任务组显示顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_work_task_group_project_sort` (`project_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目任务组';

CREATE TABLE IF NOT EXISTS `un_work_task` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作任务主键',
    `project_id` BIGINT UNSIGNED NOT NULL COMMENT '所属工作项目主键',
    `group_id` BIGINT UNSIGNED NOT NULL COMMENT '所属任务组主键',
    `title` VARCHAR(200) NOT NULL COMMENT '任务标题',
    `description` TEXT NULL COMMENT '任务说明',
    `owner_member_id` BIGINT UNSIGNED NULL COMMENT '任务负责人系统成员主键',
    `status` VARCHAR(24) NOT NULL DEFAULT 'todo' COMMENT '任务状态编码',
    `priority` TINYINT NOT NULL DEFAULT 2 COMMENT '任务优先级，数字越小越优先',
    `due_time` DATETIME(3) NULL COMMENT '任务计划完成时间',
    `field_data` JSON NULL COMMENT '已配置任务字段的值',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '并发编辑版本号',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_work_task_group_status` (`group_id`, `status`),
    KEY `idx_work_task_owner_due` (`owner_member_id`, `due_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作任务';

CREATE TABLE IF NOT EXISTS `un_work_task_field` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务字段配置主键',
    `project_id` BIGINT UNSIGNED NOT NULL COMMENT '所属工作项目主键',
    `field_key` VARCHAR(64) NOT NULL COMMENT '项目内稳定字段编码',
    `field_name` VARCHAR(100) NOT NULL COMMENT '字段显示名称',
    `field_type` VARCHAR(32) NOT NULL COMMENT '字段值类型',
    `required_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '新建任务时是否必填',
    `option_data` JSON NULL COMMENT '选项型字段的可选值',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '字段状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_task_field_key` (`project_id`, `field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务字段';

CREATE TABLE IF NOT EXISTS `un_work_task_member` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务成员关系主键',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属工作任务主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '参与的系统成员主键',
    `relation_type` VARCHAR(24) NOT NULL COMMENT '协作、关注或验收关系类型',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入任务时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_task_member_type` (`task_id`, `member_id`, `relation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务协作、关注和验收成员';

CREATE TABLE IF NOT EXISTS `un_work_task_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务历史主键',
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '所属工作任务主键',
    `operator_member_id` BIGINT UNSIGNED NOT NULL COMMENT '操作的系统成员主键',
    `action_code` VARCHAR(48) NOT NULL COMMENT '状态或业务操作编码',
    `before_data` JSON NULL COMMENT '操作前的相关业务值',
    `after_data` JSON NULL COMMENT '操作后的相关业务值',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_work_task_history_task_time` (`task_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务状态与业务操作历史';

CREATE TABLE IF NOT EXISTS `un_work_daily` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '每日工作日志主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '填写日志的系统成员主键',
    `work_date` DATE NOT NULL COMMENT '日志对应的工作日期',
    `content` TEXT NOT NULL COMMENT '用户填写的工作内容',
    `field_data` JSON NULL COMMENT '已配置工作日志字段的值',
    `status` VARCHAR(24) NOT NULL DEFAULT 'draft' COMMENT '工作日志状态编码',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_work_daily_member_date` (`tenant_id`, `member_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户手动创建的每日工作日志';

CREATE TABLE IF NOT EXISTS `un_work_daily_field` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作日志字段配置主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `field_key` VARCHAR(64) NOT NULL COMMENT '租户内稳定字段编码',
    `field_name` VARCHAR(100) NOT NULL COMMENT '字段显示名称',
    `field_type` VARCHAR(32) NOT NULL COMMENT '字段值类型',
    `required_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '提交日志时是否必填',
    `option_data` JSON NULL COMMENT '选项型字段的可选值',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '字段状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_daily_field_key` (`tenant_id`, `field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日志字段';

CREATE TABLE IF NOT EXISTS `un_work_daily_comment` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '工作日志评论主键',
    `daily_id` BIGINT UNSIGNED NOT NULL COMMENT '所属每日工作日志主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '评论的系统成员主键',
    `content` TEXT NOT NULL COMMENT '评论或管理反馈内容',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评论时间',
    PRIMARY KEY (`id`),
    KEY `idx_work_daily_comment_daily_time` (`daily_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日志评论与管理反馈';

CREATE TABLE IF NOT EXISTS `un_dashboard` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '仪表盘主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `dashboard_name` VARCHAR(120) NOT NULL COMMENT '仪表盘名称',
    `description` VARCHAR(500) NULL COMMENT '仪表盘用途说明',
    `owner_member_id` BIGINT UNSIGNED NOT NULL COMMENT '创建的系统成员主键',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '仪表盘状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dashboard_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘';

CREATE TABLE IF NOT EXISTS `un_dashboard_component` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '仪表盘组件主键',
    `dashboard_id` BIGINT UNSIGNED NOT NULL COMMENT '所属仪表盘主键',
    `component_type` VARCHAR(32) NOT NULL COMMENT '组件展示类型',
    `title` VARCHAR(120) NOT NULL COMMENT '组件显示标题',
    `data_source_id` BIGINT UNSIGNED NULL COMMENT '组件使用的数据源主键',
    `config_data` JSON NULL COMMENT '图表展示与过滤配置',
    `layout_data` JSON NULL COMMENT '组件大小和位置',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '组件顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dashboard_component_sort` (`dashboard_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘配置包含的组件';

CREATE TABLE IF NOT EXISTS `un_dashboard_component_field` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '仪表盘组件字段主键',
    `component_id` BIGINT UNSIGNED NOT NULL COMMENT '所属组件主键',
    `field_key` VARCHAR(64) NOT NULL COMMENT '数据源中的字段编码',
    `field_label` VARCHAR(100) NOT NULL COMMENT '组件显示字段名称',
    `aggregation` VARCHAR(24) NULL COMMENT '统计聚合方式',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dashboard_component_field` (`component_id`, `field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表组件对应的字段以及相关配置';

CREATE TABLE IF NOT EXISTS `un_dashboard_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '仪表盘人员关系主键',
    `dashboard_id` BIGINT UNSIGNED NOT NULL COMMENT '所属仪表盘主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '可访问的系统成员主键',
    `permission_code` VARCHAR(24) NOT NULL DEFAULT 'view' COMMENT '查看或编辑权限编码',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dashboard_user` (`dashboard_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表组人员';

CREATE TABLE IF NOT EXISTS `un_data_source` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据源主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `source_code` VARCHAR(64) NOT NULL COMMENT '租户内数据源编码',
    `source_name` VARCHAR(120) NOT NULL COMMENT '数据源名称',
    `source_type` VARCHAR(32) NOT NULL COMMENT '数据源类型',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '数据源状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_source_code` (`tenant_id`, `source_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源';

CREATE TABLE IF NOT EXISTS `un_data_source_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据源配置主键',
    `data_source_id` BIGINT UNSIGNED NOT NULL COMMENT '所属数据源主键',
    `config_key` VARCHAR(64) NOT NULL COMMENT '配置项编码',
    `config_value` TEXT NULL COMMENT '非敏感配置项的值',
    `secret_ciphertext` TEXT NULL COMMENT '加密保存的敏感配置值',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_source_config_key` (`data_source_id`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置';

CREATE TABLE IF NOT EXISTS `un_kpi` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'KPI配置主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `kpi_code` VARCHAR(64) NOT NULL COMMENT '租户内KPI编码',
    `kpi_name` VARCHAR(120) NOT NULL COMMENT 'KPI名称',
    `data_source_id` BIGINT UNSIGNED NULL COMMENT '统计使用的数据源主键',
    `metric_field` VARCHAR(64) NOT NULL COMMENT '被统计的字段编码',
    `aggregation` VARCHAR(24) NOT NULL COMMENT '求和、计数等聚合方式',
    `filter_data` JSON NULL COMMENT 'KPI统计过滤配置',
    `cycle_type` VARCHAR(24) NOT NULL COMMENT '指标统计周期编码',
    `target_value` DECIMAL(20,4) NULL COMMENT '默认目标值',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'KPI状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kpi_code` (`tenant_id`, `kpi_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KPI配置';

CREATE TABLE IF NOT EXISTS `un_kpi_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'KPI人员关系主键',
    `kpi_id` BIGINT UNSIGNED NOT NULL COMMENT '所属KPI主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '考核或查看的系统成员主键',
    `target_value` DECIMAL(20,4) NULL COMMENT '该成员单独配置的目标值',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kpi_user` (`kpi_id`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='KPI用户';

CREATE TABLE IF NOT EXISTS `un_print` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '打印模板主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `module_id` BIGINT UNSIGNED NOT NULL COMMENT '适用业务模块主键',
    `template_code` VARCHAR(64) NOT NULL COMMENT '模块内模板编码',
    `template_name` VARCHAR(120) NOT NULL COMMENT '模板名称',
    `template_content` LONGTEXT NOT NULL COMMENT '打印模板内容',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '模板状态：1启用，0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_print_template_code` (`tenant_id`, `module_id`, `template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打印模板';
