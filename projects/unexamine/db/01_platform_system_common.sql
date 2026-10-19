-- 平台、公共能力及系统租户的基础表。此脚本只建用户定义的原名表，不导入旧 SQL 的数据。
-- 账号与人员、系统成员与租户成员分开；跨域 ID 由业务接口校验，不设置跨域物理外键。
USE `unexamine`;

-- 平台账号保留现有登录接口使用的字段，人员资料由 un_plat_member 单独维护。
CREATE TABLE IF NOT EXISTS `un_plat_account` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台账号主键',
    `account` VARCHAR(64) NOT NULL COMMENT '登录账号',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要',
    `display_name` VARCHAR(100) NOT NULL COMMENT '账号显示名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1启用、0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_account_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号';

CREATE TABLE IF NOT EXISTS `un_plat_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台配置主键',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT NULL COMMENT '配置值',
    `description` VARCHAR(255) NULL COMMENT '配置用途说明',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台基础配置';

CREATE TABLE IF NOT EXISTS `un_plat_department` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台部门主键',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级部门主键，0表示根部门',
    `department_code` VARCHAR(64) NOT NULL COMMENT '平台内唯一的部门编码',
    `name` VARCHAR(100) NOT NULL COMMENT '部门名称',
    `manager_member_id` BIGINT UNSIGNED NULL COMMENT '部门负责人对应的平台成员主键',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '同级部门排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '部门状态：1启用、0停用',
    `remark` VARCHAR(255) NULL COMMENT '部门备注',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_department_code` (`department_code`),
    KEY `idx_un_plat_department_parent_sort` (`parent_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台组织部门';

CREATE TABLE IF NOT EXISTS `un_plat_member` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台成员主键',
    `account_id` BIGINT UNSIGNED NULL COMMENT '关联的平台登录账号主键，未开通登录时为空',
    `member_no` VARCHAR(64) NOT NULL COMMENT '平台成员编号',
    `real_name` VARCHAR(100) NOT NULL COMMENT '成员姓名',
    `department_id` BIGINT UNSIGNED NULL COMMENT '所属平台部门主键',
    `leader_member_id` BIGINT UNSIGNED NULL COMMENT '直属上级的平台成员主键',
    `mobile` VARCHAR(30) NULL COMMENT '手机号码',
    `email` VARCHAR(100) NULL COMMENT '电子邮箱',
    `gender` TINYINT NOT NULL DEFAULT 0 COMMENT '性别：0未设置、1男、2女',
    `position_name` VARCHAR(100) NULL COMMENT '岗位名称',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '成员状态：1在职、0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_member_account` (`account_id`),
    UNIQUE KEY `uk_un_plat_member_no` (`member_no`),
    KEY `idx_un_plat_member_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台成员';

CREATE TABLE IF NOT EXISTS `un_plat_role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台角色主键',
    `role_code` VARCHAR(64) NOT NULL COMMENT '平台内唯一的角色编码',
    `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
    `role_type` TINYINT NOT NULL DEFAULT 1 COMMENT '角色类型：0内置管理员、1自定义',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '角色状态：1启用、0停用',
    `remark` VARCHAR(255) NULL COMMENT '角色备注',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色';

CREATE TABLE IF NOT EXISTS `un_plat_member_role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台成员角色关系主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '平台成员主键',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '平台角色主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_member_role_pair` (`member_id`, `role_id`),
    KEY `idx_un_plat_member_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台成员角色关系';

CREATE TABLE IF NOT EXISTS `un_plat_menu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台菜单主键',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级菜单主键，0表示根菜单',
    `menu_code` VARCHAR(100) NOT NULL COMMENT '平台内唯一的菜单编码',
    `menu_name` VARCHAR(100) NOT NULL COMMENT '菜单或操作名称',
    `menu_type` TINYINT NOT NULL COMMENT '菜单类型：1目录、2页面、3操作',
    `route_path` VARCHAR(255) NULL COMMENT '页面访问路径，操作类型可为空',
    `permission_code` VARCHAR(150) NULL COMMENT '接口授权标识',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '同级菜单排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '菜单状态：1启用、0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_menu_code` (`menu_code`),
    KEY `idx_un_plat_menu_parent_sort` (`parent_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台菜单';

CREATE TABLE IF NOT EXISTS `un_plat_role_menu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台角色菜单关系主键',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '平台角色主键',
    `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '平台菜单主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_role_menu_pair` (`role_id`, `menu_id`),
    KEY `idx_un_plat_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色菜单授权';

CREATE TABLE IF NOT EXISTS `un_plat_role_data` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台角色数据权限主键',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '平台角色主键',
    `scope_type` TINYINT NOT NULL COMMENT '数据范围：1本人、2本人及下属、3本部门、4本部门及下级、5全部、6指定部门',
    `department_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '指定部门主键，非指定部门范围时为0',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_plat_role_data_scope` (`role_id`, `scope_type`, `department_id`),
    KEY `idx_un_plat_role_data_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色数据权限';

-- 登录日志保留当前登录接口依赖的列；失败尝试也记录提交的账号和原因。
CREATE TABLE IF NOT EXISTS `un_plat_log_login` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台登录日志主键',
    `account_id` BIGINT UNSIGNED NULL COMMENT '匹配到的平台账号主键',
    `login_account` VARCHAR(64) NOT NULL COMMENT '本次提交的登录账号',
    `success` TINYINT NOT NULL COMMENT '登录是否成功：1成功、0失败',
    `failure_reason` VARCHAR(100) NULL COMMENT '登录失败原因',
    `ip_address` VARCHAR(45) NOT NULL COMMENT '发起登录的客户端地址',
    `login_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_plat_log_login_account_time` (`login_account`, `login_time`),
    KEY `idx_un_plat_log_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台登录日志';

CREATE TABLE IF NOT EXISTS `un_plat_log_opt` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台操作日志主键',
    `account_id` BIGINT UNSIGNED NOT NULL COMMENT '操作平台账号主键',
    `action_code` VARCHAR(100) NOT NULL COMMENT '业务操作编码',
    `target_type` VARCHAR(64) NULL COMMENT '被操作对象类型',
    `target_id` BIGINT UNSIGNED NULL COMMENT '被操作对象主键',
    `request_path` VARCHAR(255) NULL COMMENT '请求路径',
    `success` TINYINT NOT NULL COMMENT '操作是否成功：1成功、0失败',
    `ip_address` VARCHAR(45) NULL COMMENT '客户端地址',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_plat_log_opt_account_time` (`account_id`, `create_time`),
    KEY `idx_un_plat_log_opt_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台操作日志';

CREATE TABLE IF NOT EXISTS `un_plat_message` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台消息主键',
    `recipient_member_id` BIGINT UNSIGNED NOT NULL COMMENT '接收消息的平台成员主键',
    `title` VARCHAR(200) NOT NULL COMMENT '消息标题',
    `content` TEXT NOT NULL COMMENT '消息正文',
    `message_type` VARCHAR(50) NOT NULL COMMENT '消息业务类型',
    `related_type` VARCHAR(64) NULL COMMENT '关联业务对象类型',
    `related_id` BIGINT UNSIGNED NULL COMMENT '关联业务对象主键',
    `read_time` DATETIME(3) NULL COMMENT '接收人阅读时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_plat_message_recipient_time` (`recipient_member_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息';

-- 公共资源以范围类型和范围主键确定归属，避免再造平台版与租户版资源表。
CREATE TABLE IF NOT EXISTS `un_common_file` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公共文件主键',
    `scope_type` VARCHAR(20) NOT NULL COMMENT '资源范围：platform、system或tenant',
    `scope_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属系统或租户主键，平台范围为0',
    `original_name` VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
    `storage_key` VARCHAR(500) NOT NULL COMMENT '文件在存储中的唯一位置',
    `content_type` VARCHAR(150) NULL COMMENT '文件媒体类型',
    `file_size` BIGINT UNSIGNED NOT NULL COMMENT '文件大小，单位字节',
    `uploader_id` BIGINT UNSIGNED NOT NULL COMMENT '上传人对应范围内的成员主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_common_file_storage_key` (`storage_key`),
    KEY `idx_un_common_file_scope_time` (`scope_type`, `scope_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共文件记录';

CREATE TABLE IF NOT EXISTS `un_common_notice` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公共公告主键',
    `scope_type` VARCHAR(20) NOT NULL COMMENT '公告范围：platform、system或tenant',
    `scope_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属系统或租户主键，平台范围为0',
    `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
    `content` LONGTEXT NOT NULL COMMENT '公告正文',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '公告状态：0草稿、1已发布、2已撤销',
    `publisher_id` BIGINT UNSIGNED NULL COMMENT '发布人对应范围内的成员主键',
    `publish_time` DATETIME(3) NULL COMMENT '公告发布时间',
    `expire_time` DATETIME(3) NULL COMMENT '公告过期时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_common_notice_scope_status` (`scope_type`, `scope_id`, `status`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共公告';

CREATE TABLE IF NOT EXISTS `un_common_ai` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公共AI配置主键',
    `scope_type` VARCHAR(20) NOT NULL COMMENT '配置范围：platform、system或tenant',
    `scope_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属系统或租户主键，平台范围为0',
    `config_code` VARCHAR(64) NOT NULL COMMENT '范围内唯一的AI配置编码',
    `provider_name` VARCHAR(100) NOT NULL COMMENT '模型服务商名称',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
    `api_base_url` VARCHAR(500) NULL COMMENT '模型服务接口地址',
    `secret_ciphertext` TEXT NULL COMMENT '加密存储的服务凭据密文',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '配置状态：1启用、0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_common_ai_scope_code` (`scope_type`, `scope_id`, `config_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共AI配置';

CREATE TABLE IF NOT EXISTS `un_sys_system` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '业务系统主键',
    `system_code` VARCHAR(64) NOT NULL COMMENT '平台内唯一的业务系统编码',
    `system_name` VARCHAR(100) NOT NULL COMMENT '业务系统名称',
    `description` VARCHAR(500) NULL COMMENT '业务系统用途说明',
    `owner_plat_member_id` BIGINT UNSIGNED NULL COMMENT '负责该系统的平台成员主键',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '系统状态：1启用、0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_system_code` (`system_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台内业务系统';

CREATE TABLE IF NOT EXISTS `un_sys_system_config` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统配置主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `config_key` VARCHAR(100) NOT NULL COMMENT '系统内唯一的配置键',
    `config_value` TEXT NULL COMMENT '配置值',
    `description` VARCHAR(255) NULL COMMENT '配置用途说明',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_system_config_key` (`system_id`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务系统基础配置';

CREATE TABLE IF NOT EXISTS `un_sys_tenant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统租户主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `tenant_code` VARCHAR(64) NOT NULL COMMENT '系统内唯一的租户编码',
    `tenant_name` VARCHAR(100) NOT NULL COMMENT '租户名称',
    `contact_name` VARCHAR(100) NULL COMMENT '租户联系人姓名',
    `contact_mobile` VARCHAR(30) NULL COMMENT '租户联系人电话',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '租户状态：1启用、0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_tenant_system_code` (`system_id`, `tenant_code`),
    KEY `idx_un_sys_tenant_system_status` (`system_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统租户';

-- 系统成员是系统身份；租户归属、部门及租户角色由 un_sys_tenant_member 等表维护。
CREATE TABLE IF NOT EXISTS `un_sys_member` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统成员主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `account` VARCHAR(64) NOT NULL COMMENT '系统内登录账号',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码摘要',
    `display_name` VARCHAR(100) NOT NULL COMMENT '成员显示名称',
    `real_name` VARCHAR(100) NULL COMMENT '成员真实姓名',
    `mobile` VARCHAR(30) NULL COMMENT '手机号码',
    `email` VARCHAR(100) NULL COMMENT '电子邮箱',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '成员状态：1启用、0停用',
    `last_login_time` DATETIME(3) NULL COMMENT '最近一次成功登录时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_member_system_account` (`system_id`, `account`),
    KEY `idx_un_sys_member_system_status` (`system_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统成员';

CREATE TABLE IF NOT EXISTS `un_sys_department` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户部门主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级租户部门主键，0表示根部门',
    `department_code` VARCHAR(64) NOT NULL COMMENT '租户内唯一的部门编码',
    `name` VARCHAR(100) NOT NULL COMMENT '部门名称',
    `manager_tenant_member_id` BIGINT UNSIGNED NULL COMMENT '部门负责人对应的租户成员主键',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '同级部门排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '部门状态：1启用、0停用',
    `remark` VARCHAR(255) NULL COMMENT '部门备注',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_department_tenant_code` (`tenant_id`, `department_code`),
    KEY `idx_un_sys_department_tree` (`tenant_id`, `parent_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户组织部门';

CREATE TABLE IF NOT EXISTS `un_sys_tenant_member` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户成员主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的系统成员主键',
    `department_id` BIGINT UNSIGNED NULL COMMENT '成员所属的租户部门主键',
    `member_no` VARCHAR(64) NULL COMMENT '租户内成员编号',
    `position_name` VARCHAR(100) NULL COMMENT '成员在该租户的岗位名称',
    `leader_tenant_member_id` BIGINT UNSIGNED NULL COMMENT '直属上级租户成员主键',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '租户成员状态：1在职、0停用',
    `join_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入租户时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关系创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '关系更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_tenant_member_pair` (`tenant_id`, `member_id`),
    UNIQUE KEY `uk_un_sys_tenant_member_no` (`tenant_id`, `member_no`),
    KEY `idx_un_sys_tenant_member_department` (`tenant_id`, `department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户成员';

CREATE TABLE IF NOT EXISTS `un_sys_menu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户菜单主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `parent_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上级菜单主键，0表示根菜单',
    `menu_code` VARCHAR(100) NOT NULL COMMENT '租户内唯一的菜单编码',
    `menu_name` VARCHAR(100) NOT NULL COMMENT '菜单或操作名称',
    `menu_type` TINYINT NOT NULL COMMENT '菜单类型：1目录、2页面、3操作',
    `route_path` VARCHAR(255) NULL COMMENT '页面访问路径，操作类型可为空',
    `permission_code` VARCHAR(150) NULL COMMENT '接口授权标识',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '同级菜单排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '菜单状态：1启用、0停用',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_menu_tenant_code` (`tenant_id`, `menu_code`),
    KEY `idx_un_sys_menu_tree` (`tenant_id`, `parent_id`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户菜单';

CREATE TABLE IF NOT EXISTS `un_sys_role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户角色主键',
    `tenant_id` BIGINT UNSIGNED NOT NULL COMMENT '所属租户主键',
    `role_code` VARCHAR(64) NOT NULL COMMENT '租户内唯一的角色编码',
    `role_name` VARCHAR(100) NOT NULL COMMENT '角色名称',
    `role_type` TINYINT NOT NULL DEFAULT 1 COMMENT '角色类型：0内置管理员、1自定义',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '角色状态：1启用、0停用',
    `remark` VARCHAR(255) NULL COMMENT '角色备注',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_role_tenant_code` (`tenant_id`, `role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色';

CREATE TABLE IF NOT EXISTS `un_sys_member_role` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户成员角色关系主键',
    `tenant_member_id` BIGINT UNSIGNED NOT NULL COMMENT '租户成员主键',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '租户角色主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_member_role_pair` (`tenant_member_id`, `role_id`),
    KEY `idx_un_sys_member_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户成员角色关系';

CREATE TABLE IF NOT EXISTS `un_sys_role_menu` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户角色菜单关系主键',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '租户角色主键',
    `menu_id` BIGINT UNSIGNED NOT NULL COMMENT '租户菜单主键',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_role_menu_pair` (`role_id`, `menu_id`),
    KEY `idx_un_sys_role_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色菜单授权';

CREATE TABLE IF NOT EXISTS `un_sys_role_data` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户角色数据权限主键',
    `role_id` BIGINT UNSIGNED NOT NULL COMMENT '租户角色主键',
    `scope_type` TINYINT NOT NULL COMMENT '数据范围：1本人、2本人及下属、3本部门、4本部门及下级、5全部、6指定部门',
    `department_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '指定租户部门主键，非指定部门范围时为0',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_un_sys_role_data_scope` (`role_id`, `scope_type`, `department_id`),
    KEY `idx_un_sys_role_data_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色数据权限';

CREATE TABLE IF NOT EXISTS `un_sys_log_login` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统登录日志主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '发起登录的业务系统主键',
    `member_id` BIGINT UNSIGNED NULL COMMENT '匹配到的系统成员主键',
    `login_account` VARCHAR(64) NOT NULL COMMENT '本次提交的系统登录账号',
    `success` TINYINT NOT NULL COMMENT '登录是否成功：1成功、0失败',
    `failure_reason` VARCHAR(100) NULL COMMENT '登录失败原因',
    `ip_address` VARCHAR(45) NOT NULL COMMENT '发起登录的客户端地址',
    `login_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_sys_log_login_account_time` (`system_id`, `login_account`, `login_time`),
    KEY `idx_un_sys_log_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统登录日志';

CREATE TABLE IF NOT EXISTS `un_sys_log_opt` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统操作日志主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `tenant_id` BIGINT UNSIGNED NULL COMMENT '操作所在的租户主键，系统管理操作可为空',
    `member_id` BIGINT UNSIGNED NOT NULL COMMENT '操作系统成员主键',
    `action_code` VARCHAR(100) NOT NULL COMMENT '业务操作编码',
    `target_type` VARCHAR(64) NULL COMMENT '被操作对象类型',
    `target_id` BIGINT UNSIGNED NULL COMMENT '被操作对象主键',
    `request_path` VARCHAR(255) NULL COMMENT '请求路径',
    `success` TINYINT NOT NULL COMMENT '操作是否成功：1成功、0失败',
    `ip_address` VARCHAR(45) NULL COMMENT '客户端地址',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_sys_log_opt_scope_time` (`system_id`, `tenant_id`, `create_time`),
    KEY `idx_un_sys_log_opt_member_time` (`member_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志';

CREATE TABLE IF NOT EXISTS `un_sys_message` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统消息主键',
    `system_id` BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统主键',
    `tenant_id` BIGINT UNSIGNED NULL COMMENT '消息所在租户主键，系统消息可为空',
    `recipient_member_id` BIGINT UNSIGNED NOT NULL COMMENT '接收消息的系统成员主键',
    `title` VARCHAR(200) NOT NULL COMMENT '消息标题',
    `content` TEXT NOT NULL COMMENT '消息正文',
    `message_type` VARCHAR(50) NOT NULL COMMENT '消息业务类型',
    `related_type` VARCHAR(64) NULL COMMENT '关联业务对象类型',
    `related_id` BIGINT UNSIGNED NULL COMMENT '关联业务对象主键',
    `read_time` DATETIME(3) NULL COMMENT '接收人阅读时间',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_un_sys_message_recipient_time` (`recipient_member_id`, `create_time`),
    KEY `idx_un_sys_message_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息';
