-- system 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_sys_system` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台内业务系统主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `code` varchar(100) NOT NULL COMMENT '平台内系统编码',
  `name` varchar(200) NOT NULL COMMENT '系统展示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '系统用途说明',
  `logo_file_id` bigint DEFAULT NULL COMMENT '系统标识文件标识',
  `creator_account_id` bigint NOT NULL COMMENT '当前系统所属平台账号标识，所属权转移时更新',
  `tenant_mode` varchar(32) NOT NULL COMMENT '系统租户运行模式',
  `status` varchar(32) NOT NULL COMMENT '系统运行状态',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '系统是否已逻辑删除',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台内业务系统创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '平台内业务系统更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '平台内业务系统乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_system_code` (`platform_id`,`code`),
  KEY `idx_sys_system_id` (`id`,`platform_id`),
  KEY `idx_sys_system_owner` (`creator_account_id`),
  KEY `idx_sys_system_status` (`platform_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台内业务系统';

CREATE TABLE `un_sys_system_domain` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '业务系统访问域名主键',
  `system_id` bigint NOT NULL COMMENT '所属业务系统标识',
  `tenant_id` bigint DEFAULT NULL COMMENT '访问地址绑定的系统租户标识',
  `domain_type` varchar(32) NOT NULL COMMENT '域名用途类型',
  `host` varchar(255) NOT NULL COMMENT '访问域名主机名',
  `base_path` varchar(255) NOT NULL DEFAULT '/' COMMENT '系统访问路径前缀',
  `tls_required` tinyint NOT NULL DEFAULT '1' COMMENT '是否要求加密传输',
  `verify_time` datetime(3) DEFAULT NULL COMMENT '域名归属验证时间',
  `status` varchar(32) NOT NULL COMMENT '业务系统访问域名状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务系统访问域名创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '业务系统访问域名更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '业务系统访问域名乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_domain_host` (`host`,`base_path`),
  KEY `idx_sys_domain_system` (`system_id`,`status`),
  KEY `idx_sys_domain_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务系统访问域名';

CREATE TABLE `un_sys_sso_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统身份源绑定主键',
  `platform_id` bigint NOT NULL COMMENT '系统与身份源共同所属的平台标识',
  `system_id` bigint NOT NULL COMMENT '目标系统标识',
  `tenant_id` bigint NOT NULL COMMENT '目标租户标识',
  `provider_id` bigint NOT NULL COMMENT '平台身份源标识',
  `provider_version_id` bigint NOT NULL COMMENT '固定的平台身份源发布版本标识',
  `binding_version` int NOT NULL COMMENT '该系统租户身份源绑定的配置版本号',
  `tenant_domains_json` json NOT NULL COMMENT '该绑定允许的租户登录域名集合',
  `department_mapping_json` json NOT NULL COMMENT '外部部门到系统部门的映射快照',
  `member_match_json` json NOT NULL COMMENT '外部用户到账号及员工的匹配顺序',
  `jit_policy_json` json NOT NULL COMMENT '系统成员即时创建和申请策略',
  `preflight_report_json` json DEFAULT NULL COMMENT '最近一次身份映射预演结果',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿、启用或停用状态',
  `sync_job_id` bigint DEFAULT NULL COMMENT '关联的人工确认同步作业标识',
  `created_by_member_id` bigint NOT NULL COMMENT '创建配置的系统成员标识',
  `confirmed_by_member_id` bigint DEFAULT NULL COMMENT '确认配置的系统成员标识',
  `preflight_time` datetime(3) DEFAULT NULL COMMENT '最近一次预演完成时间',
  `confirmed_time` datetime(3) DEFAULT NULL COMMENT '人工确认时间',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定配置创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '绑定配置更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_sso_binding_version` (`system_id`,`tenant_id`,`provider_id`,`binding_version`),
  UNIQUE KEY `uk_sys_sso_binding_active` (`system_id`,`tenant_id`,`provider_id`,((case when (`status` = _utf8mb4'ACTIVE') then 1 else NULL end))),
  KEY `idx_sys_sso_binding_provider_version` (`provider_id`,`provider_version_id`),
  CONSTRAINT `chk_sys_sso_binding_confirm` CHECK ((((`status` = _utf8mb4'DRAFT') and (`confirmed_time` is null) and (`confirmed_by_member_id` is null)) or ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')) and (`confirmed_time` is not null) and (`confirmed_by_member_id` is not null)))),
  CONSTRAINT `chk_sys_sso_binding_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统租户对平台身份源的独立配置与发布绑定';

CREATE TABLE `un_sys_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统租户主键',
  `system_id` bigint NOT NULL COMMENT '所属业务系统标识',
  `code` varchar(100) NOT NULL COMMENT '系统内租户编码',
  `name` varchar(200) NOT NULL COMMENT '租户展示名称',
  `login_title` varchar(200) DEFAULT NULL COMMENT '租户专属登录页标题，空值使用租户名称',
  `login_subtitle` varchar(500) DEFAULT NULL COMMENT '租户专属登录页说明，空值使用系统默认说明',
  `is_main` tinyint NOT NULL DEFAULT '0' COMMENT '是否为系统主租户',
  `main_marker` varchar(100) DEFAULT NULL COMMENT '主租户唯一标记',
  `creator_account_id` bigint NOT NULL COMMENT '创建租户的平台账号标识',
  `status` varchar(32) NOT NULL COMMENT '系统租户状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '系统租户创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '系统租户更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '系统租户乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_code` (`system_id`,`code`),
  UNIQUE KEY `uk_sys_tenant_main` (`system_id`,`main_marker`),
  KEY `idx_sys_tenant_system_id` (`system_id`,`id`),
  KEY `idx_sys_tenant_creator` (`creator_account_id`),
  CONSTRAINT `chk_sys_tenant_main_marker` CHECK ((((`is_main` = 1) and (`main_marker` is not null) and (`main_marker` = _utf8mb4'MAIN')) or ((`is_main` = 0) and (`main_marker` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统租户';

CREATE TABLE `un_sys_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统成员主键',
  `system_id` bigint NOT NULL COMMENT '所属业务系统标识',
  `account_id` bigint NOT NULL COMMENT '对应平台账号标识',
  `employee_number` varchar(100) DEFAULT NULL COMMENT '系统内成员工号',
  `display_name` varchar(100) NOT NULL COMMENT '系统内成员显示名称',
  `status` varchar(32) NOT NULL COMMENT '系统成员状态',
  `join_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入系统时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '系统成员创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '系统成员更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '系统成员乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_account` (`system_id`,`account_id`),
  UNIQUE KEY `uk_sys_member_number` (`system_id`,`employee_number`),
  KEY `idx_sys_member_system_id` (`system_id`,`id`),
  KEY `idx_sys_member_account` (`account_id`),
  KEY `idx_sys_member_scope_account` (`system_id`,`id`,`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统成员';

CREATE TABLE `un_sys_member_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统成员邀请主键',
  `system_id` bigint NOT NULL COMMENT '邀请加入的系统标识',
  `tenant_id` bigint NOT NULL COMMENT '邀请加入的租户标识',
  `target_account_id` bigint DEFAULT NULL COMMENT '指定受邀平台账号标识，为空表示持链接的登录账号可接受',
  `token_hash` char(64) NOT NULL COMMENT '一次性邀请令牌摘要',
  `department_id` bigint NOT NULL COMMENT '接受后所属部门标识',
  `role_ids_json` json NOT NULL COMMENT '接受后授予的当前租户角色标识列表',
  `employee_number` varchar(100) DEFAULT NULL COMMENT '接受后使用的系统内工号',
  `position_title` varchar(100) DEFAULT NULL COMMENT '接受后使用的岗位名称',
  `status` varchar(32) NOT NULL COMMENT '邀请状态，PENDING、ACCEPTED 或 REVOKED',
  `expire_time` datetime(3) NOT NULL COMMENT '邀请失效时间',
  `created_by_account_id` bigint NOT NULL COMMENT '发出邀请的管理员账号标识',
  `accepted_account_id` bigint DEFAULT NULL COMMENT '实际接受邀请的平台账号标识',
  `accept_time` datetime(3) DEFAULT NULL COMMENT '邀请接受时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '邀请创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '邀请更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '邀请乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_invitation_token` (`token_hash`),
  KEY `idx_sys_member_invitation_scope` (`system_id`,`tenant_id`,`status`,`create_time`),
  KEY `idx_sys_member_invitation_target` (`target_account_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统成员一次性邀请';

CREATE TABLE `un_sys_access_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问申请主键',
  `system_id` bigint NOT NULL COMMENT '目标系统标识',
  `tenant_id` bigint DEFAULT NULL COMMENT '目标租户标识，未确定租户时为空',
  `account_id` bigint NOT NULL COMMENT '申请的平台账号标识',
  `identity_provider` varchar(100) NOT NULL DEFAULT 'LOCAL' COMMENT '申请所用身份源编码',
  `external_user_id` varchar(255) DEFAULT NULL COMMENT '身份源中的外部用户标识',
  `request_reason` varchar(1000) NOT NULL COMMENT '申请原因',
  `requested_role` varchar(100) DEFAULT NULL COMMENT '申请人期望的角色',
  `status` varchar(32) NOT NULL COMMENT '申请状态，PENDING 表示未决',
  `decided_by_member_id` bigint DEFAULT NULL COMMENT '审批系统成员标识',
  `decision_comment` varchar(1000) DEFAULT NULL COMMENT '审批意见',
  `approved_role_ids_json` json DEFAULT NULL COMMENT '审批确认的角色标识快照',
  `approved_data_scope_json` json DEFAULT NULL COMMENT '审批确认的数据范围快照',
  `request_trace_id` varchar(64) NOT NULL COMMENT '申请链路追踪标识',
  `decision_trace_id` varchar(64) DEFAULT NULL COMMENT '审批链路追踪标识',
  `decide_time` datetime(3) DEFAULT NULL COMMENT '审批时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_access_open` (`system_id`,`account_id`,((case when (`status` = _utf8mb4'PENDING') then coalesce(`tenant_id`,0) else NULL end))),
  KEY `idx_sys_access_queue` (`system_id`,`status`,`create_time`),
  KEY `idx_sys_access_external_identity` (`identity_provider`,`external_user_id`),
  KEY `idx_sys_access_request_trace` (`request_trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统成员访问申请及审批历史';

CREATE TABLE `un_sys_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户组织部门主键',
  `system_id` bigint NOT NULL COMMENT '所属业务系统标识',
  `tenant_id` bigint NOT NULL COMMENT '所属租户标识',
  `parent_id` bigint DEFAULT NULL COMMENT '上级租户部门标识',
  `code` varchar(100) NOT NULL COMMENT '租户内部门编码',
  `name` varchar(200) NOT NULL COMMENT '部门展示名称',
  `path_code` varchar(1000) NOT NULL COMMENT '部门层级编码路径',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '同级部门排序值',
  `status` varchar(32) NOT NULL COMMENT '租户组织部门状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '租户组织部门创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '租户组织部门更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '租户组织部门乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_department_code` (`tenant_id`,`code`),
  KEY `idx_sys_department_tenant_id` (`tenant_id`,`id`),
  KEY `idx_sys_department_parent` (`tenant_id`,`parent_id`),
  KEY `idx_sys_department_system` (`system_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户组织部门';

CREATE TABLE `un_sys_tenant_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户成员主键',
  `system_id` bigint NOT NULL COMMENT '所属业务系统标识',
  `tenant_id` bigint NOT NULL COMMENT '所属租户标识',
  `system_member_id` bigint NOT NULL COMMENT '对应系统成员标识',
  `department_id` bigint DEFAULT NULL COMMENT '所属租户部门标识',
  `tenant_admin` tinyint NOT NULL DEFAULT '0' COMMENT '是否为租户管理员',
  `status` varchar(32) NOT NULL COMMENT '租户成员状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '租户成员创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '租户成员更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '租户成员乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_member` (`tenant_id`,`system_member_id`),
  KEY `idx_sys_tenant_member_tenant_id` (`tenant_id`,`id`),
  KEY `idx_sys_tenant_member_department` (`tenant_id`,`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户成员';

CREATE TABLE `un_sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户角色主键',
  `system_id` bigint NOT NULL COMMENT '所属业务系统标识',
  `tenant_id` bigint NOT NULL COMMENT '所属租户标识',
  `code` varchar(100) NOT NULL COMMENT '租户内角色编码',
  `name` varchar(200) NOT NULL COMMENT '角色展示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '角色用途说明',
  `built_in` tinyint NOT NULL DEFAULT '0' COMMENT '是否为租户内置角色',
  `status` varchar(32) NOT NULL COMMENT '租户角色状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '租户角色创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '租户角色更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '租户角色乐观锁版本',
  `draft_code` varchar(100) DEFAULT NULL COMMENT '待发布角色编码，为空表示没有待发布草稿',
  `draft_name` varchar(200) DEFAULT NULL COMMENT '待发布角色展示名称',
  `draft_description` varchar(1000) DEFAULT NULL COMMENT '待发布角色用途说明',
  `draft_permissions_json` json DEFAULT NULL COMMENT '待发布资源动作和数据范围配置',
  `draft_field_policies_json` json DEFAULT NULL COMMENT '待发布字段与渠道权限配置',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`,`code`),
  KEY `idx_sys_role_tenant_id` (`tenant_id`,`id`),
  KEY `idx_sys_role_system` (`system_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户角色';

CREATE TABLE `un_sys_member_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户成员角色关系主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户标识',
  `tenant_member_id` bigint NOT NULL COMMENT '获授权的租户成员标识',
  `role_id` bigint NOT NULL COMMENT '授予的租户角色标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '租户成员角色关系创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_role` (`tenant_member_id`,`role_id`),
  KEY `idx_sys_member_role_role` (`tenant_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户成员角色关系';

CREATE TABLE `un_sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统角色授权主键',
  `system_id` bigint NOT NULL COMMENT '所属系统标识',
  `tenant_id` bigint NOT NULL COMMENT '所属租户标识',
  `role_id` bigint NOT NULL COMMENT '系统角色标识',
  `resource_type` varchar(32) NOT NULL COMMENT '授权资源类型',
  `resource_code` varchar(150) NOT NULL COMMENT '授权资源编码',
  `action_code` varchar(64) NOT NULL COMMENT '允许执行的动作编码',
  `data_scope_type` varchar(64) NOT NULL COMMENT '数据范围类型',
  `data_scope_json` json DEFAULT NULL COMMENT '数据范围参数',
  `effect` varchar(16) NOT NULL DEFAULT 'ALLOW' COMMENT '授权效果，仅允许正向授权',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`,`resource_type`,`resource_code`,`action_code`),
  KEY `idx_sys_permission_resource` (`tenant_id`,`resource_type`,`resource_code`),
  CONSTRAINT `chk_sys_role_permission_allow` CHECK ((`effect` = _utf8mb4'ALLOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统租户角色的正向资源动作授权';

CREATE TABLE `un_sys_member_reporting_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员汇报关系主键',
  `system_id` bigint NOT NULL COMMENT '成员所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '成员所属租户主键',
  `tenant_member_id` bigint NOT NULL COMMENT '具有该职位和汇报关系的租户成员主键',
  `manager_tenant_member_id` bigint DEFAULT NULL COMMENT '同租户直属上级成员主键，无上级时为空',
  `position_title` varchar(100) DEFAULT NULL COMMENT '成员在租户组织中的职位名称',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '成员汇报关系创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '成员汇报关系更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '汇报关系乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_reporting_member` (`tenant_id`,`tenant_member_id`),
  KEY `idx_sys_member_reporting_manager` (`tenant_id`,`manager_tenant_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户成员职位及直属上级关系';
