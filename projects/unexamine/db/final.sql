-- source: init/V1__platform.sql
-- platform 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_plat_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台账号主键',
  `username` varchar(100) NOT NULL COMMENT '登录用户名',
  `email` varchar(255) DEFAULT NULL COMMENT '账号绑定邮箱',
  `mobile` varchar(40) DEFAULT NULL COMMENT '账号绑定手机号',
  `display_name` varchar(100) NOT NULL COMMENT '账号展示名称',
  `avatar_file_id` bigint DEFAULT NULL COMMENT '头像文件标识',
  `locale` varchar(32) NOT NULL DEFAULT 'zh-CN' COMMENT '界面语言区域',
  `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '账号时区',
  `status` varchar(32) NOT NULL COMMENT '平台账号状态',
  `last_login_time` datetime(3) DEFAULT NULL COMMENT '最近登录时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台账号创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '平台账号更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '平台账号乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_username` (`username`),
  UNIQUE KEY `uk_plat_account_email` (`email`),
  UNIQUE KEY `uk_plat_account_mobile` (`mobile`),
  KEY `idx_plat_account_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台账号';

CREATE TABLE `un_plat_account_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账号口令凭据主键',
  `account_id` bigint NOT NULL COMMENT '所属平台账号标识',
  `password_hash` varchar(255) NOT NULL COMMENT '口令哈希值',
  `password_algorithm` varchar(32) NOT NULL DEFAULT 'ARGON2ID' COMMENT '口令哈希算法',
  `must_change_password` tinyint NOT NULL DEFAULT '0' COMMENT '下次登录是否必须修改口令',
  `failed_attempts` int NOT NULL DEFAULT '0' COMMENT '连续登录失败次数',
  `lock_expire_time` datetime(3) DEFAULT NULL COMMENT '账号口令锁定截止时间',
  `credential_version` int NOT NULL DEFAULT '1' COMMENT '口令凭据版本号',
  `change_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '口令最近变更时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_credential_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号口令凭据';

CREATE TABLE `un_plat_account_mfa` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账号多因素认证方式主键',
  `account_id` bigint NOT NULL COMMENT '所属平台账号标识',
  `method_type` varchar(32) NOT NULL COMMENT '多因素认证方式类型',
  `secret_ref` varchar(255) NOT NULL COMMENT '认证密钥的受控引用',
  `display_label` varchar(100) NOT NULL COMMENT '认证方式展示标签',
  `verify_time` datetime(3) DEFAULT NULL COMMENT '认证方式最近验证时间',
  `status` varchar(32) NOT NULL COMMENT '账号多因素认证方式状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '账号多因素认证方式创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '账号多因素认证方式更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '账号多因素认证方式乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_mfa` (`account_id`,`method_type`,`display_label`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号多因素认证方式';

CREATE TABLE `un_plat_sso_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台身份源主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `code` varchar(100) NOT NULL COMMENT '平台范围内身份源编码',
  `name` varchar(200) NOT NULL COMMENT '身份源显示名称',
  `protocol` varchar(32) NOT NULL COMMENT '身份源认证协议',
  `issuer` varchar(500) NOT NULL COMMENT '身份源发行者标识',
  `client_id` varchar(255) NOT NULL COMMENT '身份源客户端标识',
  `client_secret_ref` varchar(255) DEFAULT NULL COMMENT '客户端密钥的受控引用',
  `metadata_json` json NOT NULL COMMENT '身份源公共元数据',
  `status` varchar(32) NOT NULL COMMENT '身份源启用状态',
  `published_version_id` bigint DEFAULT NULL COMMENT '当前发布的身份源版本标识',
  `published_version_number` int DEFAULT NULL COMMENT '当前发布的身份源版本号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '身份源创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '身份源更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_sso_provider_code` (`platform_id`,`code`),
  KEY `idx_plat_sso_provider_platform_id` (`platform_id`,`id`),
  KEY `idx_plat_sso_published_version` (`published_version_id`),
  CONSTRAINT `chk_plat_sso_published_pair` CHECK ((((`published_version_id` is null) and (`published_version_number` is null)) or ((`published_version_id` is not null) and (`published_version_number` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台统一身份源配置';

CREATE TABLE `un_plat_sso_provider_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '身份源版本主键',
  `provider_id` bigint NOT NULL COMMENT '所属平台身份源标识',
  `version_number` int NOT NULL COMMENT '身份源内递增的版本号',
  `protocol` varchar(32) NOT NULL COMMENT 'OIDC、SAML、OAuth2、LDAP 或企业身份协议',
  `issuer` varchar(500) NOT NULL COMMENT '身份源发行者标识',
  `client_id` varchar(255) NOT NULL COMMENT '身份源客户端标识',
  `client_secret_ref` varchar(255) DEFAULT NULL COMMENT '客户端密钥的受控引用',
  `protocol_config_json` json NOT NULL COMMENT '协议连接与证书配置快照',
  `allowed_domains_json` json NOT NULL COMMENT '允许登录的域名白名单',
  `attribute_mapping_json` json NOT NULL COMMENT '外部身份属性映射快照',
  `jit_policy_json` json NOT NULL COMMENT '即时建号与成员策略快照',
  `mfa_policy_json` json NOT NULL COMMENT '多因素认证策略快照',
  `callback_uris_json` json NOT NULL COMMENT '允许的回调地址快照',
  `test_status` varchar(32) NOT NULL DEFAULT 'NOT_TESTED' COMMENT '连接测试状态',
  `test_report_json` json DEFAULT NULL COMMENT '连接测试与预演报告',
  `tested_time` datetime(3) DEFAULT NULL COMMENT '最近一次测试完成时间',
  `tested_by_account_id` bigint DEFAULT NULL COMMENT '最近一次测试操作账号',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿、发布或归档状态',
  `published_time` datetime(3) DEFAULT NULL COMMENT '版本发布时间',
  `published_by_account_id` bigint DEFAULT NULL COMMENT '发布操作账号',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_sso_provider_version` (`provider_id`,`version_number`),
  KEY `idx_plat_sso_version_owner_id` (`provider_id`,`id`),
  KEY `idx_plat_sso_version_owner_id_number` (`provider_id`,`id`,`version_number`),
  KEY `idx_plat_sso_version_status` (`status`,`test_status`),
  CONSTRAINT `chk_plat_sso_version_publish_time` CHECK ((((`status` = _utf8mb4'DRAFT') and (`published_time` is null) and (`published_by_account_id` is null)) or ((`status` in (_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED')) and (`published_time` is not null) and (`published_by_account_id` is not null)))),
  CONSTRAINT `chk_plat_sso_version_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PUBLISHED',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台身份源的测试与发布版本';

CREATE TABLE `un_plat_sso_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '外部身份与平台账号绑定主键',
  `provider_id` bigint NOT NULL COMMENT '所属平台身份源标识',
  `external_subject` varchar(255) NOT NULL COMMENT '外部身份主体标识',
  `account_id` bigint NOT NULL COMMENT '绑定的平台账号标识',
  `attributes_json` json DEFAULT NULL COMMENT '外部身份属性快照',
  `last_login_time` datetime(3) DEFAULT NULL COMMENT '该外部身份最近登录时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '外部身份与平台账号绑定创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '外部身份与平台账号绑定更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_sso_subject` (`provider_id`,`external_subject`),
  UNIQUE KEY `uk_plat_sso_account` (`provider_id`,`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部身份与平台账号绑定';

CREATE TABLE `un_plat_password_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账号历史口令主键',
  `account_id` bigint NOT NULL COMMENT '所属平台账号标识',
  `password_hash` varchar(255) NOT NULL COMMENT '历史口令哈希值',
  `credential_version` int NOT NULL COMMENT '对应口令凭据版本号',
  `change_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '该口令启用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_password_history` (`account_id`,`credential_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号历史口令';

CREATE TABLE `un_plat_password_reset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '密码找回请求主键',
  `account_id` bigint NOT NULL COMMENT '被找回密码的平台账号主键',
  `token_hash` char(64) NOT NULL COMMENT '一次性随机令牌的 SHA-256 摘要',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '请求创建时间',
  `expire_time` datetime(3) NOT NULL COMMENT '令牌失效时间',
  `used_time` datetime(3) DEFAULT NULL COMMENT '令牌消费或被新请求取代的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_password_reset_token` (`token_hash`),
  KEY `idx_plat_password_reset_account` (`account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台账号一次性密码找回请求';

CREATE TABLE `un_plat_platform` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台配置主键',
  `code` varchar(100) NOT NULL COMMENT '平台唯一编码',
  `name` varchar(200) NOT NULL COMMENT '平台展示名称',
  `logo_file_id` bigint DEFAULT NULL COMMENT '平台标识文件标识',
  `contact_name` varchar(100) DEFAULT NULL COMMENT '平台联系人姓名',
  `contact_email` varchar(255) DEFAULT NULL COMMENT '平台联系人邮箱',
  `settings_json` json NOT NULL COMMENT '平台运行设置',
  `status` varchar(32) NOT NULL COMMENT '平台启用状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台配置创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '平台配置更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '平台配置乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_platform_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台配置';

CREATE TABLE `un_plat_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台组织部门主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `parent_id` bigint DEFAULT NULL COMMENT '上级平台部门标识',
  `code` varchar(100) NOT NULL COMMENT '平台内部门编码',
  `name` varchar(200) NOT NULL COMMENT '部门展示名称',
  `path_code` varchar(1000) NOT NULL COMMENT '部门层级编码路径',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '同级部门排序值',
  `status` varchar(32) NOT NULL COMMENT '平台组织部门状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台组织部门创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '平台组织部门更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '平台组织部门乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_department_code` (`platform_id`,`code`),
  KEY `idx_plat_department_platform_id` (`platform_id`,`id`),
  KEY `idx_plat_department_parent` (`platform_id`,`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台组织部门';

CREATE TABLE `un_plat_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台成员主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `account_id` bigint NOT NULL COMMENT '对应平台账号标识',
  `department_id` bigint DEFAULT NULL COMMENT '所属平台部门标识',
  `title` varchar(100) DEFAULT NULL COMMENT '平台成员职务名称',
  `status` varchar(32) NOT NULL COMMENT '平台成员状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台成员创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '平台成员更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '平台成员乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_account` (`platform_id`,`account_id`),
  KEY `idx_plat_member_platform_id` (`platform_id`,`id`),
  KEY `idx_plat_member_department` (`platform_id`,`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台成员';

CREATE TABLE `un_plat_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台角色主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `code` varchar(100) NOT NULL COMMENT '平台内角色编码',
  `name` varchar(200) NOT NULL COMMENT '角色展示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '角色用途说明',
  `built_in` tinyint NOT NULL DEFAULT '0' COMMENT '是否为平台内置角色',
  `status` varchar(32) NOT NULL COMMENT '平台角色状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台角色创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '平台角色更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '平台角色乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_code` (`platform_id`,`code`),
  KEY `idx_plat_role_platform_id` (`platform_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台角色';

CREATE TABLE `un_plat_member_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台成员角色关系主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `member_id` bigint NOT NULL COMMENT '获授权的平台成员标识',
  `role_id` bigint NOT NULL COMMENT '授予的平台角色标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '平台成员角色关系创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_role` (`member_id`,`role_id`),
  KEY `idx_plat_member_role_role` (`platform_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台成员角色关系';

CREATE TABLE `un_plat_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台角色授权主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `role_id` bigint NOT NULL COMMENT '平台角色标识',
  `resource_type` varchar(32) NOT NULL COMMENT '授权资源类型',
  `resource_code` varchar(150) NOT NULL COMMENT '授权资源编码',
  `action_code` varchar(64) NOT NULL COMMENT '允许执行的动作编码',
  `data_scope_type` varchar(64) NOT NULL DEFAULT 'PLATFORM' COMMENT '数据范围类型',
  `data_scope_json` json DEFAULT NULL COMMENT '数据范围参数',
  `effect` varchar(16) NOT NULL DEFAULT 'ALLOW' COMMENT '授权效果，仅允许正向授权',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_permission` (`role_id`,`resource_type`,`resource_code`,`action_code`),
  CONSTRAINT `chk_plat_role_permission_allow` CHECK ((`effect` = _utf8mb4'ALLOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台角色的正向资源动作授权';

-- source: init/V2__system.sql
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

-- source: init/V3__authentication.sql
-- authentication 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_auth_field_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字段访问策略主键',
  `context_type` varchar(32) NOT NULL COMMENT '策略范围类型，PLATFORM 或 SYSTEM',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统标识，平台策略为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户标识，平台策略为空',
  `role_id` bigint NOT NULL COMMENT '业务查询使用的角色标识',
  `platform_role_id` bigint DEFAULT NULL COMMENT '平台策略对应的平台角色标识',
  `system_role_id` bigint DEFAULT NULL COMMENT '系统策略对应的系统角色标识',
  `resource_code` varchar(150) NOT NULL COMMENT '策略约束的资源编码',
  `field_code` varchar(100) NOT NULL COMMENT '策略约束的字段编码',
  `channel` varchar(32) NOT NULL COMMENT '策略生效渠道',
  `readable` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许读取字段',
  `writable` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许写入字段',
  `mask_strategy` varchar(64) DEFAULT NULL COMMENT '字段脱敏规则',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_field_policy` (`context_type`,(coalesce(`platform_id`,0)),(coalesce(`system_id`,0)),(coalesce(`tenant_id`,0)),`role_id`,`resource_code`,`field_code`,`channel`),
  KEY `idx_auth_field_context` (`system_id`,`tenant_id`,`resource_code`),
  KEY `idx_auth_field_platform_role` (`platform_id`,`platform_role_id`),
  KEY `idx_auth_field_system_role` (`tenant_id`,`system_role_id`),
  CONSTRAINT `chk_auth_field_policy_context` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`platform_id` is not null) and (`system_id` is null) and (`tenant_id` is null) and (`platform_role_id` is not null) and (`platform_role_id` = `role_id`) and (`system_role_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`platform_id` is not null) and (`system_id` is not null) and (`tenant_id` is not null) and (`platform_role_id` is null) and (`system_role_id` is not null) and (`system_role_id` = `role_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统角色的字段读取写入策略';

CREATE TABLE `un_auth_permission_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限版本主键',
  `context_type` varchar(32) NOT NULL COMMENT '权限范围类型，PLATFORM 或 SYSTEM',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统标识，平台版本为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户标识，平台版本为空',
  `version_number` bigint NOT NULL COMMENT '该范围内递增的权限版本号',
  `reason` varchar(500) NOT NULL COMMENT '权限版本变更原因',
  `changed_by_account_id` bigint NOT NULL COMMENT '发起变更的平台账号标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本生成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_permission_version` (`context_type`,(coalesce(`platform_id`,0)),(coalesce(`system_id`,0)),(coalesce(`tenant_id`,0)),`version_number`),
  CONSTRAINT `chk_auth_permission_version_context` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`platform_id` is not null) and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`platform_id` is not null) and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统租户权限的版本流水';

CREATE TABLE `un_auth_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '登录会话主键',
  `access_token_hash` varchar(64) NOT NULL COMMENT '访问令牌哈希值',
  `refresh_token_hash` varchar(64) NOT NULL COMMENT '刷新令牌哈希值',
  `account_id` bigint NOT NULL COMMENT '登录的平台账号标识',
  `platform_id` bigint DEFAULT NULL COMMENT '当前平台范围标识',
  `system_id` bigint DEFAULT NULL COMMENT '当前业务系统范围标识',
  `tenant_id` bigint DEFAULT NULL COMMENT '当前租户范围标识',
  `member_id` bigint DEFAULT NULL COMMENT '当前系统成员标识',
  `tenant_member_id` bigint DEFAULT NULL COMMENT '当前租户成员标识',
  `role_ids_json` json NOT NULL COMMENT '会话生效角色标识快照',
  `action_permissions_json` json NOT NULL COMMENT '会话动作权限快照',
  `data_scopes_json` json NOT NULL COMMENT '会话数据范围快照',
  `permission_version` bigint NOT NULL DEFAULT '0' COMMENT '会话权限快照版本号',
  `mfa_level` varchar(32) NOT NULL DEFAULT 'NONE' COMMENT '登录通过的多因素认证级别',
  `access_expire_time` datetime(3) NOT NULL COMMENT '访问令牌失效时间',
  `refresh_expire_time` datetime(3) NOT NULL COMMENT '刷新令牌失效时间',
  `revoked` tinyint NOT NULL DEFAULT '0' COMMENT '会话是否已撤销',
  `revoked_reason` varchar(255) DEFAULT NULL COMMENT '会话撤销原因',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录会话创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '登录会话更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_access_token` (`access_token_hash`),
  UNIQUE KEY `uk_auth_session_refresh_token` (`refresh_token_hash`),
  KEY `idx_auth_session_account` (`account_id`,`revoked`,`refresh_expire_time`),
  KEY `idx_auth_session_context` (`system_id`,`tenant_id`,`revoked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录会话';

-- source: init/V4__audit.sql
-- audit 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '操作审计事件主键',
  `trace_id` varchar(64) NOT NULL COMMENT '跨服务调用链追踪标识',
  `request_id` varchar(64) DEFAULT NULL COMMENT '触发操作的请求标识',
  `context_type` varchar(32) NOT NULL COMMENT '事件所属的平台或系统范围类型',
  `actor_account_id` bigint DEFAULT NULL COMMENT '执行操作的平台账号主键，系统事件也使用统一账号',
  `platform_id` bigint DEFAULT NULL COMMENT '事件所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '事件所属系统主键，平台事件为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '事件所属租户主键，平台或系统公共事件为空',
  `member_id` bigint DEFAULT NULL COMMENT '执行操作的系统成员主键，平台事件为空',
  `application_id` bigint DEFAULT NULL COMMENT '应用调用产生事件时的应用主键',
  `event_category` varchar(64) NOT NULL COMMENT '审计事件业务类别',
  `event_code` varchar(100) NOT NULL COMMENT '审计事件动作编码',
  `object_type` varchar(100) DEFAULT NULL COMMENT '被操作业务对象类型',
  `object_id` varchar(100) DEFAULT NULL COMMENT '被操作业务对象标识',
  `result_code` varchar(64) NOT NULL COMMENT '操作结果或错误编码',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '发起操作的客户端 IP 地址',
  `user_agent` varchar(1000) DEFAULT NULL COMMENT '发起操作的客户端标识',
  `permission_snapshot` json DEFAULT NULL COMMENT '执行时的权限判定快照',
  `detail_json` json NOT NULL COMMENT '不含敏感明文的审计事件详情',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审计事件发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_trace` (`trace_id`),
  KEY `idx_audit_object` (`object_type`,`object_id`,`occur_time`),
  KEY `idx_audit_context` (`system_id`,`tenant_id`,`occur_time`),
  KEY `idx_audit_category` (`event_category`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的业务操作审计事件';

CREATE TABLE `un_audit_field_change` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字段变更审计主键',
  `audit_event_id` bigint NOT NULL COMMENT '对应的操作审计事件主键',
  `field_code` varchar(100) NOT NULL COMMENT '发生变更的业务字段编码',
  `value_type` varchar(32) NOT NULL COMMENT '字段值的数据类型',
  `before_value_json` json DEFAULT NULL COMMENT '变更前字段值，敏感信息须脱敏',
  `after_value_json` json DEFAULT NULL COMMENT '变更后字段值，敏感信息须脱敏',
  `sensitivity` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '字段值敏感等级',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '字段变更审计记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_field_event` (`audit_event_id`),
  KEY `idx_audit_field_code` (`field_code`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的业务字段变更前后值';

CREATE TABLE `un_audit_retention_marker` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '清理留痕记录主键',
  `context_type` varchar(32) NOT NULL COMMENT '被清理对象所属的平台或系统范围类型',
  `system_id` bigint DEFAULT NULL COMMENT '被清理对象所属系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '被清理对象所属租户主键',
  `object_type` varchar(100) NOT NULL COMMENT '被清理业务对象类型',
  `object_id` varchar(100) NOT NULL COMMENT '被清理业务对象标识',
  `business_key` varchar(255) DEFAULT NULL COMMENT '便于后续追溯的业务编号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '清理前业务快照的摘要',
  `snapshot_json` json DEFAULT NULL COMMENT '按留存策略保留的清理前业务快照',
  `purge_reason` varchar(1000) NOT NULL COMMENT '清理原因及业务依据',
  `purged_by_account_id` bigint NOT NULL COMMENT '执行清理的平台账号主键',
  `purge_time` datetime(3) NOT NULL COMMENT '实际清理业务数据的时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '清理留痕记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_retention_object` (`context_type`,`system_id`,`tenant_id`,`object_type`,`object_id`),
  KEY `idx_audit_retention_time` (`purge_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务数据清理后的不可变留痕';

-- source: init/V5__moduleconfig.sql
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

-- source: init/V6__runtimedata.sql
-- runtimedata 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_biz_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块主键',
  `created_config_version_id` bigint NOT NULL COMMENT '创建业务记录时固定的已发布模块版本',
  `updated_config_version_id` bigint NOT NULL COMMENT '最近修改业务记录时固定的已发布模块版本',
  `record_number` varchar(100) DEFAULT NULL COMMENT '模块内可展示的业务编号',
  `title` varchar(500) NOT NULL COMMENT '业务记录标题',
  `status` varchar(64) NOT NULL COMMENT '当前业务状态或处理状态',
  `owner_member_id` bigint NOT NULL COMMENT '当前业务记录负责人主键，缺失时由系统当前所属人补齐',
  `department_id` bigint DEFAULT NULL COMMENT '业务记录所属部门主键',
  `created_by_member_id` bigint NOT NULL COMMENT '创建业务数据的系统成员主键',
  `updated_by_member_id` bigint NOT NULL COMMENT '最近修改业务记录的系统成员主键',
  `archived` tinyint NOT NULL DEFAULT '0' COMMENT '业务记录归档标记',
  `archive_time` datetime(3) DEFAULT NULL COMMENT '业务记录归档时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `delete_time` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_number` (`tenant_id`,`module_id`,`record_number`),
  KEY `idx_biz_record_tenant_id` (`tenant_id`,`id`),
  KEY `idx_biz_record_module_id` (`module_id`,`id`),
  KEY `idx_biz_record_list` (`tenant_id`,`module_id`,`deleted`,`update_time`),
  KEY `idx_biz_record_owner` (`tenant_id`,`module_id`,`owner_member_id`),
  KEY `idx_biz_record_department` (`tenant_id`,`module_id`,`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自定义模块的通用业务主记录';

CREATE TABLE `un_biz_record_value` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `field_id` bigint NOT NULL COMMENT '模块字段配置主键',
  `field_code` varchar(100) NOT NULL COMMENT '写入时字段的稳定业务编码',
  `value_type` varchar(32) NOT NULL COMMENT '字段值存储类型',
  `value_text` text COMMENT '文本类型原始值',
  `value_number` decimal(24,8) DEFAULT NULL COMMENT '数值类型原始值',
  `value_date` date DEFAULT NULL COMMENT '日期类型原始值',
  `value_time` datetime(3) DEFAULT NULL COMMENT '日期时间类型原始值',
  `value_boolean` tinyint DEFAULT NULL COMMENT '布尔类型原始值',
  `value_reference_id` bigint DEFAULT NULL COMMENT '关联业务记录主键',
  `value_file_id` bigint DEFAULT NULL COMMENT '附件文件主键',
  `value_json` json DEFAULT NULL COMMENT '复杂类型原始值',
  `normalized_text` varchar(1000) DEFAULT NULL COMMENT '用于检索的标准化文本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_value_field` (`record_id`,`field_id`),
  KEY `idx_biz_record_value_text` (`field_id`,`normalized_text`(191)),
  KEY `idx_biz_record_value_number` (`field_id`,`value_number`),
  KEY `idx_biz_record_value_date` (`field_id`,`value_time`),
  KEY `idx_biz_record_value_reference` (`field_id`,`value_reference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务主记录的自定义字段值';

CREATE TABLE `un_biz_record_child_row` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通用子表行主键',
  `system_id` bigint NOT NULL COMMENT '主记录所属系统',
  `tenant_id` bigint NOT NULL COMMENT '主记录的数据归属租户',
  `module_id` bigint NOT NULL COMMENT '主记录所属模块',
  `record_id` bigint NOT NULL COMMENT '主业务记录主键',
  `child_field_id` bigint NOT NULL COMMENT '定义此子表的主模块字段主键',
  `row_key` varchar(100) NOT NULL COMMENT '同一记录和子表内稳定的行标识',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '子表行显示顺序',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '行逻辑删除标记',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '行创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '行最近修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '行乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_child_row_key` (`record_id`,`child_field_id`,`row_key`),
  KEY `idx_biz_child_row_field_id` (`module_id`,`child_field_id`,`id`),
  KEY `idx_biz_child_row_list` (`record_id`,`child_field_id`,`deleted`,`sort_order`),
  CONSTRAINT `ck_biz_child_row_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用业务记录的子表明细行';

CREATE TABLE `un_biz_record_child_value` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '子表行字段值主键',
  `module_id` bigint NOT NULL COMMENT '子表所属模块',
  `child_field_id` bigint NOT NULL COMMENT '子表定义字段主键',
  `child_row_id` bigint NOT NULL COMMENT '所属子表行主键',
  `field_id` bigint NOT NULL COMMENT '子表列字段主键',
  `value_type` varchar(32) NOT NULL COMMENT '字段值存储类型',
  `value_text` text COMMENT '文本类型原始值',
  `value_number` decimal(24,8) DEFAULT NULL COMMENT '数值类型原始值',
  `value_date` date DEFAULT NULL COMMENT '日期类型原始值',
  `value_time` datetime(3) DEFAULT NULL COMMENT '日期时间类型原始值',
  `value_boolean` tinyint DEFAULT NULL COMMENT '布尔类型原始值',
  `value_reference_id` bigint DEFAULT NULL COMMENT '关联记录主键，读取时继续校验目标模块和权限',
  `value_file_id` bigint DEFAULT NULL COMMENT '附件文件主键，读取时继续校验访问权限',
  `value_json` json DEFAULT NULL COMMENT '复杂类型原始值',
  `normalized_text` varchar(1000) DEFAULT NULL COMMENT '用于检索的标准化文本',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '值创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '值最近修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_child_value_field` (`child_row_id`,`field_id`),
  KEY `idx_biz_child_value_search` (`module_id`,`field_id`,`normalized_text`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用子表明细行的独立列字段值';

CREATE TABLE `un_biz_record_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '业务评论主键',
  `system_id` bigint NOT NULL COMMENT '业务记录所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务记录的数据归属租户',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块',
  `record_id` bigint NOT NULL COMMENT '被评论的业务记录主键',
  `parent_comment_id` bigint DEFAULT NULL COMMENT '同一记录内被回复的评论主键',
  `body` text NOT NULL COMMENT '评论正文',
  `created_by_member_id` bigint NOT NULL COMMENT '发表评论的系统成员主键',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评论发表时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '评论最近修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '评论逻辑删除标记',
  `deleted_time` datetime(3) DEFAULT NULL COMMENT '评论逻辑删除时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '评论乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_biz_comment_record_id` (`record_id`,`id`),
  KEY `idx_biz_comment_record_time` (`record_id`,`deleted`,`created_time`),
  CONSTRAINT `ck_biz_comment_deleted` CHECK ((((`deleted` = 0) and (`deleted_time` is null)) or ((`deleted` = 1) and (`deleted_time` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录评论与同记录回复';

CREATE TABLE `un_biz_record_index` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `query_index_id` bigint NOT NULL COMMENT '本模块查询索引定义主键',
  `index_key_hash` varchar(64) NOT NULL COMMENT '复合查询键的哈希摘要',
  `unique_key_hash` varchar(64) DEFAULT NULL COMMENT '唯一索引的规范化键摘要；非唯一索引为空',
  `index_key_text` varchar(1500) NOT NULL COMMENT '可诊断的复合查询键原文',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_index_record` (`record_id`,`query_index_id`),
  UNIQUE KEY `uk_biz_record_index_unique_value` (`tenant_id`,`module_id`,`query_index_id`,`unique_key_hash`),
  KEY `idx_biz_record_index_lookup` (`tenant_id`,`module_id`,`query_index_id`,`index_key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录按发布索引配置生成的查询键';

CREATE TABLE `un_biz_record_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `source_module_id` bigint NOT NULL COMMENT '来源业务记录所属模块',
  `source_record_id` bigint NOT NULL COMMENT '来源业务记录主键',
  `field_id` bigint NOT NULL COMMENT '来源模块中的关联字段配置主键',
  `target_module_id` bigint NOT NULL COMMENT '目标业务记录所属模块',
  `target_record_id` bigint NOT NULL COMMENT '目标业务记录主键',
  `relation_order` int NOT NULL DEFAULT '0' COMMENT '多值关联的显示顺序',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_relation` (`source_record_id`,`field_id`,`target_record_id`),
  KEY `idx_biz_relation_target` (`tenant_id`,`target_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录之间由关联字段定义的独立关系';

CREATE TABLE `un_biz_record_participant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `system_member_id` bigint NOT NULL COMMENT '参与记录的系统成员主键',
  `participant_type` varchar(32) NOT NULL DEFAULT 'PARTICIPANT' COMMENT '参与关系类型，例如协作人或关注人',
  `created_by_member_id` bigint NOT NULL COMMENT '创建业务数据的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_participant` (`record_id`,`system_member_id`,`participant_type`),
  KEY `idx_biz_record_participant_member` (`tenant_id`,`system_member_id`,`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录的负责人以外的团队参与成员';

CREATE TABLE `un_biz_record_state_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `from_status` varchar(64) DEFAULT NULL COMMENT '变更前业务状态',
  `to_status` varchar(64) NOT NULL COMMENT '变更后的业务状态',
  `reason` varchar(1000) DEFAULT NULL COMMENT '业务变更原因',
  `source_type` varchar(64) NOT NULL COMMENT '发起状态变更的来源类型',
  `source_id` varchar(100) DEFAULT NULL COMMENT '发起状态变更的来源对象标识',
  `changed_by_member_id` bigint NOT NULL COMMENT '执行变更的系统成员主键',
  `change_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务变更发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_state_record` (`record_id`,`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录状态变更历史';

CREATE TABLE `un_biz_record_owner_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `from_owner_member_id` bigint DEFAULT NULL COMMENT '变更前负责人主键',
  `to_owner_member_id` bigint DEFAULT NULL COMMENT '变更后负责人主键',
  `from_department_id` bigint DEFAULT NULL COMMENT '变更前负责部门主键',
  `to_department_id` bigint DEFAULT NULL COMMENT '变更后负责部门主键',
  `reason` varchar(1000) NOT NULL COMMENT '业务变更原因',
  `changed_by_member_id` bigint NOT NULL COMMENT '执行变更的系统成员主键',
  `change_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务变更发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_owner_record` (`record_id`,`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录负责人和部门变更历史';

CREATE TABLE `un_biz_record_conversion` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `source_module_id` bigint NOT NULL COMMENT '来源记录及转化动作共同所属模块',
  `source_record_id` bigint NOT NULL COMMENT '来源业务记录主键',
  `action_id` bigint NOT NULL COMMENT '来源模块的转化动作配置主键',
  `target_module_id` bigint NOT NULL COMMENT '成功转化永久唯一键中的目标模块',
  `status` varchar(32) NOT NULL COMMENT '转化状态：处理中、失败、成功或已解除关系',
  `mapping_snapshot_json` json NOT NULL COMMENT '执行转化时固定的字段映射快照',
  `idempotency_key` varchar(255) NOT NULL COMMENT '同租户转化请求的幂等键',
  `created_by_member_id` bigint NOT NULL COMMENT '创建业务数据的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '转化执行结束时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_conversion_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_biz_conversion_permanent_success` (((case when (`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'UNLINKED')) then `source_record_id` else NULL end)),((case when (`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'UNLINKED')) then `target_module_id` else NULL end))),
  KEY `idx_biz_conversion_result_scope` (`tenant_id`,`id`,`target_module_id`),
  KEY `idx_biz_conversion_source` (`source_record_id`,`create_time`),
  CONSTRAINT `ck_biz_conversion_status` CHECK ((`status` in (_utf8mb4'PROCESSING',_utf8mb4'FAILED',_utf8mb4'SUCCEEDED',_utf8mb4'UNLINKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录转化执行及永久成功身份';

CREATE TABLE `un_biz_record_conversion_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `conversion_id` bigint NOT NULL COMMENT '所属转化执行记录主键',
  `tenant_id` bigint NOT NULL COMMENT '来源和目标记录共同所属的数据租户',
  `target_module_id` bigint NOT NULL COMMENT '目标业务记录所属模块',
  `target_record_id` bigint NOT NULL COMMENT '目标业务记录主键',
  `result_type` varchar(32) NOT NULL COMMENT '目标记录在转化结果中的类型',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_conversion_result` (`conversion_id`,`target_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次转化创建的目标记录';

CREATE TABLE `un_biz_tenant_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '共享授权主键，历史使用记录保持引用',
  `system_id` bigint NOT NULL COMMENT '共享双方所属系统',
  `source_tenant_id` bigint NOT NULL COMMENT '拥有业务数据的来源租户',
  `target_tenant_id` bigint NOT NULL COMMENT '获得访问关系的目标租户',
  `module_id` bigint NOT NULL COMMENT '共享数据所属模块',
  `record_id` bigint DEFAULT NULL COMMENT '指定记录授权的原记录主键，动态范围授权为空',
  `scope_type` varchar(32) NOT NULL DEFAULT 'RECORD' COMMENT '授权范围类型：RECORD指定记录，DATA_RANGE动态数据范围',
  `scope_json` json DEFAULT NULL COMMENT '范围条件、字段类型绑定及授予时来源共享权限约束快照',
  `permission_json` json NOT NULL COMMENT '明确允许的业务动作，仍与目标用户角色权限取交集',
  `status` varchar(32) NOT NULL COMMENT '授权存储状态：ACTIVE有效，REVOKED已撤销',
  `start_time` datetime(3) DEFAULT NULL COMMENT '授权开始生效时间，为空表示授予后立即生效',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '授权失效时间，旧接口和字段保持兼容',
  `granted_by_member_id` bigint NOT NULL COMMENT '来源租户授予授权的系统成员',
  `grant_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近授予时间，旧字段保持兼容',
  `revoke_time` datetime(3) DEFAULT NULL COMMENT '授权撤销时间，历史授权不删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '授权修改的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_tenant_share` (`source_tenant_id`,`target_tenant_id`,`record_id`),
  KEY `idx_biz_share_target` (`target_tenant_id`,`status`,`expire_time`),
  KEY `idx_biz_share_module_scope` (`system_id`,`target_tenant_id`,`module_id`,`scope_type`,`status`),
  CONSTRAINT `ck_biz_share_scope` CHECK ((((`scope_type` = _utf8mb4'RECORD') and (`record_id` is not null) and (`scope_json` is null)) or ((`scope_type` = _utf8mb4'DATA_RANGE') and (`record_id` is null) and (json_type(`scope_json`) = _utf8mb4'OBJECT') and (`scope_json` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同系统跨租户记录及动态模块范围共享授权，不改变数据所有权';

CREATE TABLE `un_biz_tenant_share_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `share_id` bigint NOT NULL COMMENT '共享授权主键',
  `target_tenant_member_id` bigint NOT NULL COMMENT '执行共享访问的目标租户成员',
  `action_code` varchar(64) NOT NULL COMMENT '通过共享授权执行的业务动作编码',
  `result_code` varchar(64) NOT NULL COMMENT '共享访问结果编码',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '共享访问发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_share_usage` (`share_id`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目标租户通过共享授权访问业务数据的历史';

-- source: init/V7__foundation.sql
-- foundation 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_core_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置主键',
  `context_type` varchar(32) NOT NULL COMMENT '配置归属：平台、系统或租户',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统主键，平台配置为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户主键，平台和系统配置为空',
  `category` varchar(100) NOT NULL COMMENT '业务配置类别',
  `setting_key` varchar(150) NOT NULL COMMENT '归属范围内的配置键',
  `value_type` varchar(32) NOT NULL COMMENT '配置值类型',
  `value_json` json NOT NULL COMMENT '业务配置值，不承载固定实体关系',
  `sensitive` tinyint NOT NULL DEFAULT '0' COMMENT '是否属于敏感配置',
  `status` varchar(32) NOT NULL COMMENT '配置启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，后续统一更名为 create_time',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，后续统一更名为 update_time',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_setting` (`context_type`,`platform_id`,(coalesce(`system_id`,0)),(coalesce(`tenant_id`,0)),`category`,`setting_key`),
  KEY `idx_core_setting_context` (`system_id`,`tenant_id`,`category`),
  CONSTRAINT `ck_core_setting_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`platform_id` is not null) and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`platform_id` is not null) and (`system_id` is not null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'TENANT') and (`platform_id` is not null) and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台、系统和租户范围内的普通业务配置';

CREATE TABLE `un_core_feature_flag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '特性开关主键',
  `context_type` varchar(32) NOT NULL COMMENT '特性开关归属：平台或系统租户',
  `platform_id` bigint DEFAULT NULL COMMENT '平台范围主键，系统租户开关为空',
  `system_id` bigint DEFAULT NULL COMMENT '系统范围主键，平台开关为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统租户主键，平台开关为空',
  `flag_key` varchar(150) NOT NULL COMMENT '归属范围内的特性编码',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '开关默认启用状态',
  `rollout_json` json DEFAULT NULL COMMENT '生效时间、目标对象与安全回退规则',
  `status` varchar(32) NOT NULL COMMENT '配置启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，后续统一更名为 create_time',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，后续统一更名为 update_time',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_feature_flag` (`context_type`,(coalesce(`platform_id`,0)),(coalesce(`system_id`,0)),(coalesce(`tenant_id`,0)),`flag_key`),
  CONSTRAINT `ck_core_feature_flag_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`platform_id` is not null) and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`platform_id` is null) and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统租户的受控特性开关';

CREATE TABLE `un_core_quota_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配额策略主键',
  `context_type` varchar(32) NOT NULL COMMENT '配额归属：平台或系统租户',
  `platform_id` bigint DEFAULT NULL COMMENT '平台范围主键，系统租户配额为空',
  `system_id` bigint DEFAULT NULL COMMENT '系统范围主键，平台配额为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统租户主键，平台配额为空',
  `quota_code` varchar(100) NOT NULL COMMENT '归属范围内的配额编码',
  `period_type` varchar(32) NOT NULL COMMENT '统计周期类型',
  `hard_limit` bigint NOT NULL COMMENT '周期内的硬性上限',
  `warning_threshold` bigint DEFAULT NULL COMMENT '触发预警的已用阈值',
  `status` varchar(32) NOT NULL COMMENT '策略启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，后续统一更名为 create_time',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，后续统一更名为 update_time',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_quota_policy` (`context_type`,(coalesce(`platform_id`,0)),(coalesce(`system_id`,0)),(coalesce(`tenant_id`,0)),`quota_code`),
  CONSTRAINT `ck_core_quota_policy_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`platform_id` is not null) and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`platform_id` is null) and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统租户的配额上限策略';

CREATE TABLE `un_core_quota_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '周期配额用量主键',
  `quota_policy_id` bigint NOT NULL COMMENT '所属配额策略主键',
  `period_key` varchar(64) NOT NULL COMMENT '配额统计周期标识',
  `used_value` bigint NOT NULL DEFAULT '0' COMMENT '当前周期已正式占用的配额量',
  `reserved_value` bigint NOT NULL DEFAULT '0' COMMENT '当前周期已预留但尚未正式占用的配额量',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '配额用量最后更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '配额用量乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_quota_usage` (`quota_policy_id`,`period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配额周期内的已用和预留计数';

CREATE TABLE `un_core_idempotency_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '幂等请求记录主键',
  `context_key` varchar(255) NOT NULL COMMENT '幂等键所属业务范围标识',
  `operation_code` varchar(100) NOT NULL COMMENT '被保护的业务操作编码',
  `idempotency_key` varchar(255) NOT NULL COMMENT '调用方提供的幂等请求标识',
  `request_hash` varchar(64) NOT NULL COMMENT '原始请求内容摘要，用于识别同键不同请求',
  `status` varchar(32) NOT NULL COMMENT '幂等请求处理状态',
  `response_code` varchar(64) DEFAULT NULL COMMENT '首次执行的业务结果编码',
  `response_json` json DEFAULT NULL COMMENT '可供重复请求返回的首次执行结果',
  `lock_expire_time` datetime(3) DEFAULT NULL COMMENT '在途操作独占锁截止时间',
  `expire_time` datetime(3) NOT NULL COMMENT '幂等记录有效截止时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '幂等记录创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '幂等记录最后更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '幂等记录乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_idempotency` (`context_key`,`operation_code`,`idempotency_key`),
  KEY `idx_core_idempotency_expiry` (`expire_time`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务操作幂等请求及首次执行结果';

CREATE TABLE `un_core_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户业务编号序列主键',
  `system_id` bigint NOT NULL COMMENT '编号序列所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '编号序列所属租户主键',
  `sequence_code` varchar(100) NOT NULL COMMENT '租户范围内的编号序列编码',
  `pattern` varchar(255) NOT NULL COMMENT '生成业务编号的格式模板',
  `reset_period` varchar(32) NOT NULL COMMENT '计数器重置周期类型',
  `current_period_key` varchar(64) NOT NULL COMMENT '当前计数周期标识',
  `current_value` bigint NOT NULL DEFAULT '0' COMMENT '当前周期已分配的最大序号',
  `step_value` int NOT NULL DEFAULT '1' COMMENT '每次分配增加的序号步长',
  `status` varchar(32) NOT NULL COMMENT '编号序列启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '编号序列创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '编号序列更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '编号序列乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_sequence` (`tenant_id`,`sequence_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户业务编号规则及当前序号';

CREATE TABLE `un_core_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '待投递业务事件主键',
  `context_type` varchar(32) NOT NULL COMMENT '事件所属的平台或系统范围类型',
  `system_id` bigint DEFAULT NULL COMMENT '系统事件所属系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户事件所属租户主键',
  `aggregate_type` varchar(100) NOT NULL COMMENT '产生事件的业务聚合类型',
  `aggregate_id` varchar(100) NOT NULL COMMENT '产生事件的业务聚合标识',
  `event_type` varchar(100) NOT NULL COMMENT '需要投递的业务事件类型',
  `payload_json` json NOT NULL COMMENT '待投递事件的业务载荷',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '事件投递状态',
  `available_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件允许开始投递的时间',
  `publish_time` datetime(3) DEFAULT NULL COMMENT '事件成功投递完成时间',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '事件投递失败后的重试次数',
  `last_error` varchar(2000) DEFAULT NULL COMMENT '最近一次投递失败原因',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件进入待投递箱的时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '待投递事件最后更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '待投递事件乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_core_outbox_dispatch` (`status`,`available_time`),
  KEY `idx_core_outbox_aggregate` (`aggregate_type`,`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待投递的本地业务事件及投递结果';

CREATE TABLE `un_core_cache_epoch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '缓存失效世代主键',
  `context_key` varchar(255) NOT NULL COMMENT '缓存归属的业务上下文标识',
  `cache_namespace` varchar(100) NOT NULL COMMENT '需要失效的缓存命名空间',
  `epoch_value` bigint NOT NULL DEFAULT '1' COMMENT '当前缓存失效世代号',
  `reason` varchar(500) NOT NULL COMMENT '推进缓存世代的业务原因',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '缓存世代最后更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '缓存世代乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_cache_epoch` (`context_key`,`cache_namespace`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按上下文隔离的缓存命名空间失效版本';

-- source: init/V8__backgroundjobs.sql
-- backgroundjobs 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_job_background` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '后台作业业务申请主键',
  `context_type` varchar(32) NOT NULL COMMENT '申请层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统层申请所属系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统层申请所属租户主键，可为空表示系统公共作业',
  `job_type` varchar(100) NOT NULL COMMENT '作业业务类型，如导入、导出、打印或文件处理',
  `source_type` varchar(100) NOT NULL COMMENT '发起作业的业务对象类型',
  `source_id` varchar(100) DEFAULT NULL COMMENT '发起作业的业务对象标识',
  `parameter_json` json NOT NULL COMMENT '提交时固定的业务参数，不包含运行队列状态',
  `authorization_snapshot_json` json NOT NULL COMMENT '申请时固定的权限与数据范围快照',
  `final_status` varchar(32) DEFAULT NULL COMMENT '完成后写入的最终审计状态，成功、带警告成功、失败或取消',
  `result_summary_json` json DEFAULT NULL COMMENT '最终结果、结果文件引用及统计摘要',
  `error_code` varchar(100) DEFAULT NULL COMMENT '最终失败错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '最终失败原因摘要',
  `created_by_account_id` bigint NOT NULL COMMENT '发起申请的平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务申请时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '最终结果归档时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '业务审计记录更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '业务审计记录乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_job_background_context` (`platform_id`,`system_id`,`tenant_id`,`job_type`,`create_time`),
  KEY `idx_job_background_final` (`final_status`,`finish_time`),
  CONSTRAINT `chk_job_background_final` CHECK ((((`final_status` is null) and (`finish_time` is null) and (`result_summary_json` is null) and (`error_code` is null) and (`error_message` is null)) or ((`final_status` is not null) and (`final_status` in (_utf8mb4'SUCCEEDED',_utf8mb4'SUCCEEDED_WITH_WARNINGS',_utf8mb4'FAILED',_utf8mb4'CANCELLED')) and (`finish_time` is not null)))),
  CONSTRAINT `chk_job_background_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台作业业务申请与最终审计；运行状态和重试事实保存在 Redis';

CREATE TABLE `un_job_item_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '作业逐项结果主键',
  `job_id` bigint NOT NULL COMMENT '所属后台作业申请主键',
  `item_key` varchar(255) NOT NULL COMMENT '作业内稳定的业务项标识，用于幂等写入',
  `row_number` bigint DEFAULT NULL COMMENT '导入导出等行处理场景的原始行号',
  `status` varchar(32) NOT NULL COMMENT '该业务项的最终处理状态',
  `result_json` json DEFAULT NULL COMMENT '该业务项的结果数据或文件引用',
  `error_code` varchar(100) DEFAULT NULL COMMENT '该业务项的失败错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '该业务项的失败原因摘要',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '逐项结果首次记录时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '逐项结果更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_item_result` (`job_id`,`item_key`),
  KEY `idx_job_item_status` (`job_id`,`status`,`row_number`),
  CONSTRAINT `chk_job_item_result_status` CHECK ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'SKIPPED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台作业逐项业务结果，不保存运行尝试记录';

-- source: init/V9__flow.sql
-- flow 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_flow_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `context_type` varchar(32) NOT NULL COMMENT '流程所属上下文类型',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台ID',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统ID',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '配置来源租户ID',
  `code` varchar(100) NOT NULL COMMENT '稳定业务编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '业务说明',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建人平台账号ID',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_definition_code_scope` (`context_type`,`platform_id`,(coalesce(`system_id`,0)),(coalesce(`owner_tenant_id`,0)),`code`),
  KEY `idx_flow_definition_context_id` (`context_type`,`id`),
  KEY `idx_flow_definition_context` (`system_id`,`owner_tenant_id`,`status`),
  KEY `idx_flow_definition_system_tenant_id` (`system_id`,`owner_tenant_id`,`id`),
  CONSTRAINT `ck_flow_definition_scope` CHECK (((`platform_id` is not null) and (((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is not null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程定义与草稿作用域';

CREATE TABLE `un_flow_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `position_x` decimal(12,3) NOT NULL COMMENT '设计画布横坐标',
  `position_y` decimal(12,3) NOT NULL COMMENT '设计画布纵坐标',
  `assignee_policy_json` json DEFAULT NULL COMMENT '审批人解析策略',
  `form_policy_json` json DEFAULT NULL COMMENT '节点表单与字段权限策略',
  `timeout_policy_json` json DEFAULT NULL COMMENT '节点超时处理策略',
  `exception_policy_json` json DEFAULT NULL COMMENT '节点异常兜底策略',
  `config_json` json NOT NULL COMMENT '节点或连线业务配置',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_key` (`flow_id`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程草稿节点';

CREATE TABLE `un_flow_edge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `edge_key` varchar(100) NOT NULL COMMENT '流程连线稳定编码',
  `source_node_key` varchar(100) NOT NULL COMMENT '连线起点节点编码',
  `target_node_key` varchar(100) NOT NULL COMMENT '连线终点节点编码',
  `condition_expression` text COMMENT '触发或连线条件表达式',
  `priority_order` int NOT NULL DEFAULT '0' COMMENT '条件或绑定优先级',
  `config_json` json NOT NULL COMMENT '节点或连线业务配置',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_edge_key` (`flow_id`,`edge_key`),
  KEY `idx_flow_edge_source` (`flow_id`,`source_node_key`,`priority_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程草稿连线';

CREATE TABLE `un_flow_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `version_number` int NOT NULL COMMENT '流程发布版本号',
  `draft_revision` int NOT NULL COMMENT '草稿修订号',
  `definition_hash` varchar(64) NOT NULL COMMENT '发布定义内容摘要',
  `snapshot_json` json NOT NULL COMMENT '发布时冻结的完整流程定义快照',
  `simulation_result_json` json NOT NULL COMMENT '发布前模拟验证结果',
  `change_summary` varchar(1000) NOT NULL COMMENT '发布变更说明',
  `published_by_account_id` bigint NOT NULL COMMENT '发布人平台账号ID',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_version_number` (`flow_id`,`version_number`),
  UNIQUE KEY `uk_flow_version_revision` (`flow_id`,`draft_revision`),
  KEY `idx_flow_version_flow_id` (`flow_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变流程发布版本';

CREATE TABLE `un_flow_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `current_version_id` bigint NOT NULL COMMENT '当前生效流程版本ID',
  `updated_by_account_id` bigint NOT NULL COMMENT '最后修改人平台账号ID',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_publication_flow` (`flow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程当前发布指针';

CREATE TABLE `un_flow_trigger_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `system_id` bigint NOT NULL COMMENT '所属系统ID',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置来源租户ID',
  `module_id` bigint NOT NULL COMMENT '关联业务模块ID',
  `action_id` bigint DEFAULT NULL COMMENT '绑定的模块动作ID',
  `trigger_event` varchar(64) NOT NULL COMMENT '模块业务触发事件编码',
  `flow_id` bigint DEFAULT NULL COMMENT '命中的流程定义ID；直接执行业务动作时为空',
  `priority_order` int NOT NULL DEFAULT '0' COMMENT '条件或绑定优先级',
  `condition_expression` text COMMENT '触发或连线条件表达式',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_binding_active_scope` (((case when (`status` = _utf8mb4'ACTIVE') then `owner_tenant_id` else NULL end)),((case when (`status` = _utf8mb4'ACTIVE') then `module_id` else NULL end)),((case when (`status` = _utf8mb4'ACTIVE') then `trigger_event` else NULL end)),((case when (`status` = _utf8mb4'ACTIVE') then coalesce(`action_id`,0) else NULL end))),
  KEY `idx_flow_binding_flow` (`flow_id`,`status`),
  KEY `idx_flow_binding_system_module_id` (`system_id`,`module_id`,`id`),
  KEY `idx_flow_binding_id_source_tenant` (`id`,`owner_tenant_id`),
  KEY `idx_flow_binding_scope_history` (`owner_tenant_id`,`module_id`,`trigger_event`,`action_id`,`create_time`),
  CONSTRAINT `ck_flow_binding_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块事件和动作的租户流程绑定';

CREATE TABLE `un_flow_binding_resolution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `system_id` bigint NOT NULL COMMENT '所属系统ID',
  `tenant_id` bigint NOT NULL COMMENT '实际运行租户ID',
  `module_id` bigint NOT NULL COMMENT '关联业务模块ID',
  `trigger_event` varchar(64) NOT NULL COMMENT '模块业务触发事件编码',
  `binding_id` bigint NOT NULL COMMENT '命中的流程绑定ID',
  `flow_version_id` bigint NOT NULL COMMENT '固定的流程发布版本ID',
  `resolution_reason` varchar(500) NOT NULL COMMENT '默认继承或租户覆盖原因',
  `resolve_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定解析时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_resolution_lookup` (`tenant_id`,`module_id`,`trigger_event`,`resolve_time`),
  KEY `idx_flow_resolution_binding_version` (`id`,`binding_id`,`flow_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='触发时的最终生效绑定解析';

CREATE TABLE `un_flow_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `context_type` varchar(32) NOT NULL COMMENT '流程所属上下文类型',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台ID',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '实际运行租户ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `flow_version_id` bigint NOT NULL COMMENT '固定的流程发布版本ID',
  `module_id` bigint DEFAULT NULL COMMENT '启动时关联的业务模块ID；平台独立流程为空',
  `module_version_id` bigint DEFAULT NULL COMMENT '启动时固定的模块发布版本ID',
  `business_record_id` bigint DEFAULT NULL COMMENT '关联的通用业务记录ID；非记录流程为空',
  `binding_resolution_id` bigint DEFAULT NULL COMMENT '本次触发绑定解析记录ID；独立发起为空',
  `binding_id` bigint DEFAULT NULL COMMENT '本次命中的流程绑定ID；独立发起为空',
  `binding_source_tenant_id` bigint DEFAULT NULL COMMENT '绑定来源租户ID，可与实例实际租户不同',
  `business_type` varchar(100) DEFAULT NULL COMMENT '关联业务对象类型',
  `business_id` varchar(100) DEFAULT NULL COMMENT '关联业务对象外部标识',
  `business_snapshot_json` json DEFAULT NULL COMMENT '启动时业务对象数据快照',
  `runtime_snapshot_json` json NOT NULL COMMENT '启动时复制的节点、连线、条件、字段权限及高级设置',
  `title` varchar(500) NOT NULL COMMENT '流程实例标题',
  `current_node_key` varchar(100) DEFAULT NULL COMMENT '当前运行节点编码',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `started_by_account_id` bigint NOT NULL COMMENT '发起人平台账号ID',
  `started_by_tenant_member_id` bigint DEFAULT NULL COMMENT '发起人在实际运行租户的成员ID',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '流程启动时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '流程结束时间',
  `error_code` varchar(100) DEFAULT NULL COMMENT '失败错误编码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '失败原因说明',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_instance_context` (`system_id`,`tenant_id`,`status`,`start_time`),
  KEY `idx_flow_instance_business` (`business_type`,`business_id`),
  KEY `idx_flow_instance_starter_member` (`tenant_id`,`started_by_tenant_member_id`),
  KEY `idx_flow_instance_module_record` (`module_id`,`business_record_id`),
  CONSTRAINT `ck_flow_instance_binding_source` CHECK ((((`binding_id` is null) and (`binding_source_tenant_id` is null) and (`binding_resolution_id` is null)) or ((`binding_id` is not null) and (`binding_source_tenant_id` is not null)))),
  CONSTRAINT `ck_flow_instance_module_version` CHECK ((((`module_id` is null) and (`module_version_id` is null) and (`business_record_id` is null)) or ((`module_id` is not null) and (`module_version_id` is not null)))),
  CONSTRAINT `ck_flow_instance_runtime_snapshot` CHECK ((json_type(`runtime_snapshot_json`) = _utf8mb4'OBJECT')),
  CONSTRAINT `ck_flow_instance_scope` CHECK (((`platform_id` is not null) and (((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行流程实例及启动快照';

CREATE TABLE `un_flow_subflow_link` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '子流程关系主键',
  `parent_instance_id` bigint NOT NULL COMMENT '父流程实例ID',
  `parent_execution_id` bigint NOT NULL COMMENT '启动子流程的父节点执行轮次ID',
  `child_instance_id` bigint NOT NULL COMMENT '子流程实例ID',
  `parent_node_key` varchar(100) NOT NULL COMMENT '父流程节点编码快照',
  `status` varchar(32) NOT NULL COMMENT '子流程关系状态',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关系创建时间',
  `completed_time` datetime(3) DEFAULT NULL COMMENT '子流程结束并处理父流程的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_subflow_parent_execution` (`parent_instance_id`,`parent_execution_id`),
  UNIQUE KEY `uk_flow_subflow_child` (`child_instance_id`),
  CONSTRAINT `ck_flow_subflow_not_self` CHECK ((`parent_instance_id` <> `child_instance_id`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='父子流程实例关系';

CREATE TABLE `un_flow_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `parent_execution_id` bigint DEFAULT NULL COMMENT '父节点执行轮次ID',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `enter_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '节点进入时间',
  `leave_time` datetime(3) DEFAULT NULL COMMENT '节点离开时间',
  `result_code` varchar(64) DEFAULT NULL COMMENT '节点执行结果编码',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_execution_active` (`instance_id`,`status`,`enter_time`),
  KEY `idx_flow_execution_instance_id` (`instance_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点执行轮次';

CREATE TABLE `un_flow_instance_variable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `variable_key` varchar(100) NOT NULL COMMENT '实例变量编码',
  `value_type` varchar(32) NOT NULL COMMENT '实例变量值类型',
  `value_json` json DEFAULT NULL COMMENT '实例变量JSON值',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_instance_variable` (`instance_id`,`variable_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程实例变量';

CREATE TABLE `un_flow_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `execution_id` bigint NOT NULL COMMENT '任务所属的节点执行轮次',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `task_type` varchar(32) NOT NULL COMMENT '待办任务类型',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `assignee_account_id` bigint DEFAULT NULL COMMENT '当前处理人平台账号ID',
  `assignee_tenant_member_id` bigint DEFAULT NULL COMMENT '当前处理人在实际运行租户的成员ID',
  `assignee_snapshot_json` json NOT NULL COMMENT '任务创建时处理人解析快照',
  `due_time` datetime(3) DEFAULT NULL COMMENT '任务应完成时间',
  `claim_time` datetime(3) DEFAULT NULL COMMENT '任务领取时间',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '任务完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_node` (`instance_id`,`node_key`,`id`),
  KEY `idx_flow_task_assignee` (`assignee_account_id`,`status`,`due_time`),
  KEY `idx_flow_task_assignee_member` (`assignee_tenant_member_id`,`status`,`due_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点执行轮次的待办任务';

CREATE TABLE `un_flow_task_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '所属流程待办任务ID',
  `candidate_type` varchar(32) NOT NULL COMMENT '候选主体类型',
  `candidate_id` varchar(100) NOT NULL COMMENT '候选主体标识',
  `resolution_reason` varchar(500) NOT NULL COMMENT '默认继承或租户覆盖原因',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_task_candidate` (`task_id`,`candidate_type`,`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待办任务候选处理人';

CREATE TABLE `un_flow_action` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `task_id` bigint DEFAULT NULL COMMENT '所属流程待办任务ID',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `action_code` varchar(64) NOT NULL COMMENT '流程操作编码',
  `comment_text` varchar(2000) DEFAULT NULL COMMENT '操作意见正文',
  `input_json` json DEFAULT NULL COMMENT '本次操作输入快照',
  `result_json` json NOT NULL COMMENT '本次操作结果快照',
  `idempotency_key` varchar(255) NOT NULL COMMENT '操作幂等键',
  `acted_by_account_id` bigint NOT NULL COMMENT '操作人平台账号ID',
  `acted_by_tenant_member_id` bigint DEFAULT NULL COMMENT '操作人在实际运行租户的成员ID',
  `act_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_action_idempotency` (`instance_id`,`idempotency_key`),
  KEY `idx_flow_action_instance` (`instance_id`,`act_time`),
  KEY `idx_flow_action_actor_member` (`acted_by_tenant_member_id`,`act_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程操作及幂等记录';

CREATE TABLE `un_flow_exception` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '流程节点稳定编码',
  `exception_type` varchar(64) NOT NULL COMMENT '流程异常类型',
  `error_code` varchar(100) NOT NULL COMMENT '失败错误编码',
  `error_message` varchar(2000) NOT NULL COMMENT '失败原因说明',
  `policy_action` varchar(64) NOT NULL COMMENT '异常策略选择的处置动作',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `resolved_by_account_id` bigint DEFAULT NULL COMMENT '异常处置人平台账号ID',
  `resolved_by_tenant_member_id` bigint DEFAULT NULL COMMENT '异常处置人在运行租户的成员ID',
  `resolution_comment` varchar(2000) DEFAULT NULL COMMENT '异常处置说明',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件或异常发生时间',
  `resolve_time` datetime(3) DEFAULT NULL COMMENT '绑定解析时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_exception_instance` (`instance_id`,`status`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程异常与处置';

CREATE TABLE `un_flow_history_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `execution_id` bigint DEFAULT NULL COMMENT '关联节点执行轮次ID',
  `task_id` bigint DEFAULT NULL COMMENT '所属流程待办任务ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '流程节点稳定编码',
  `event_type` varchar(64) NOT NULL COMMENT '历史事件类型',
  `event_name` varchar(200) NOT NULL COMMENT '历史事件显示名称',
  `actor_account_id` bigint DEFAULT NULL COMMENT '事件操作者平台账号ID',
  `actor_tenant_member_id` bigint DEFAULT NULL COMMENT '事件操作者租户成员ID',
  `before_status` varchar(32) DEFAULT NULL COMMENT '事件发生前状态',
  `after_status` varchar(32) DEFAULT NULL COMMENT '事件发生后状态',
  `detail_json` json NOT NULL COMMENT '事件业务详情快照',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件或异常发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_history_instance` (`instance_id`,`occur_time`),
  KEY `idx_flow_history_task` (`task_id`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变流程事件历史';

CREATE TABLE `un_flow_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `execution_id` bigint DEFAULT NULL COMMENT '关联节点执行轮次ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '流程节点稳定编码',
  `job_type` varchar(64) NOT NULL COMMENT '流程任务类型',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `payload_json` json NOT NULL COMMENT '流程异步任务内容',
  `idempotency_key` varchar(255) NOT NULL COMMENT '操作幂等键',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已尝试执行次数',
  `max_attempts` int NOT NULL DEFAULT '3' COMMENT '最大允许执行次数',
  `next_run_time` datetime(3) NOT NULL COMMENT '下次可执行时间',
  `lock_time` datetime(3) DEFAULT NULL COMMENT '任务锁定时间',
  `locked_by` varchar(100) DEFAULT NULL COMMENT '当前执行节点标识',
  `last_error_code` varchar(100) DEFAULT NULL COMMENT '最近一次失败编码',
  `last_error_message` varchar(2000) DEFAULT NULL COMMENT '最近一次失败说明',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '任务完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_job_idempotency` (`instance_id`,`idempotency_key`),
  KEY `idx_flow_job_due` (`status`,`next_run_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程定时与异步等待任务';

-- source: init/V10__application.sql
-- application 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_app_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应用主键',
  `context_type` varchar(32) NOT NULL COMMENT '应用所属上下文：PLATFORM平台、SYSTEM系统',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台主键',
  `owner_system_id` bigint DEFAULT NULL COMMENT '所属系统主键，平台应用为空',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '所属租户主键，平台应用为空',
  `code` varchar(100) NOT NULL COMMENT '当前上下文内唯一的应用编码',
  `name` varchar(200) NOT NULL COMMENT '应用名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '应用用途说明',
  `application_type` varchar(32) NOT NULL COMMENT '应用类型：SERVICE服务调用、WEBHOOK事件回调',
  `icon_code` varchar(32) NOT NULL DEFAULT 'api' COMMENT '应用预设图标代码',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `status` varchar(32) NOT NULL COMMENT '应用状态：DRAFT草稿、ACTIVE启用、DISABLED停用、DELETED删除',
  `created_by_account_id` bigint NOT NULL COMMENT '创建应用的平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，旧字段保持兼容',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，旧字段保持兼容',
  `version` int NOT NULL DEFAULT '0' COMMENT '应用修改的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_definition_code` (`context_type`,`platform_id`,(coalesce(`owner_system_id`,0)),(coalesce(`owner_tenant_id`,0)),`code`),
  KEY `idx_app_definition_owner` (`owner_system_id`,`owner_tenant_id`,`status`),
  CONSTRAINT `chk_app_definition_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`platform_id` is not null) and (`owner_system_id` is null) and (`owner_tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`platform_id` is not null) and (`owner_system_id` is not null) and (`owner_tenant_id` is not null)))),
  CONSTRAINT `ck_app_definition_icon` CHECK ((`icon_code` in (_utf8mb4'api',_utf8mb4'link',_utf8mb4'workflow',_utf8mb4'database',_utf8mb4'robot',_utf8mb4'message')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统范围内的受控访问应用定义';

CREATE TABLE `un_app_callback` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '回调主键',
  `application_id` bigint NOT NULL COMMENT '所属应用主键',
  `callback_type` varchar(32) NOT NULL COMMENT '回调业务类型',
  `url` varchar(1000) NOT NULL COMMENT '接收回调的HTTPS地址',
  `event_codes_json` json NOT NULL COMMENT '订阅的业务事件编码列表',
  `signing_secret_ref` varchar(255) DEFAULT NULL COMMENT '回调签名密钥安全引用',
  `timeout_millis` int NOT NULL DEFAULT '5000' COMMENT '单次回调超时毫秒数',
  `max_attempts` int NOT NULL DEFAULT '3' COMMENT '最大投递次数',
  `status` varchar(32) NOT NULL COMMENT '回调启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_app_callback_application` (`application_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用事件回调配置';

CREATE TABLE `un_app_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '授权主键',
  `application_id` bigint NOT NULL COMMENT '所属应用主键',
  `target_type` varchar(32) NOT NULL COMMENT '授权目标：PLATFORM平台、SYSTEM系统',
  `target_system_id` bigint DEFAULT NULL COMMENT '目标系统主键，平台授权为空',
  `target_tenant_id` bigint DEFAULT NULL COMMENT '目标租户主键，平台授权为空',
  `resource_type` varchar(32) NOT NULL COMMENT '授权资源类型：FLOW流程、MODULE模块',
  `resource_id` varchar(100) NOT NULL COMMENT '目标资源业务编码',
  `action_code` varchar(64) NOT NULL COMMENT '允许调用的动作编码',
  `data_scope_json` json NOT NULL COMMENT '授权数据范围规则',
  `rate_limit_json` json DEFAULT NULL COMMENT '限流和访问通道控制规则',
  `status` varchar(32) NOT NULL COMMENT '授权启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_grant_resource` (`application_id`,`target_type`,(coalesce(`target_system_id`,0)),(coalesce(`target_tenant_id`,0)),`resource_type`,`resource_id`,`action_code`),
  KEY `idx_app_grant_target` (`target_system_id`,`target_tenant_id`,`status`),
  CONSTRAINT `chk_app_grant_scope` CHECK ((((`target_type` = _utf8mb4'PLATFORM') and (`target_system_id` is null) and (`target_tenant_id` is null) and (`resource_type` = _utf8mb4'FLOW')) or ((`target_type` = _utf8mb4'SYSTEM') and (`target_system_id` is not null) and (`target_tenant_id` is not null) and (`resource_type` in (_utf8mb4'MODULE',_utf8mb4'FLOW')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用按目标资源和动作配置的访问授权草稿';

CREATE TABLE `un_app_grant_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '授权字段主键',
  `grant_id` bigint NOT NULL COMMENT '所属授权主键',
  `field_code` varchar(100) NOT NULL COMMENT '目标模块字段编码',
  `readable` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许返回字段值',
  `writable` tinyint NOT NULL DEFAULT '0' COMMENT '是否允许写入字段值',
  `mask_strategy` varchar(64) DEFAULT NULL COMMENT '字段返回时的脱敏方式',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_grant_field` (`grant_id`,`field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用授权内的字段读写与脱敏策略';

CREATE TABLE `un_app_provider_connection` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '外部平台连接主键',
  `application_id` bigint NOT NULL COMMENT '所属受控应用主键',
  `provider_code` varchar(32) NOT NULL COMMENT '平台编码：WECOM企业微信、FEISHU飞书、DINGTALK钉钉、WECHAT微信',
  `product_code` varchar(64) NOT NULL COMMENT '平台产品形态编码',
  `connection_key` varchar(64) NOT NULL COMMENT '不可猜测的公开回调连接标识',
  `display_name` varchar(200) NOT NULL COMMENT '连接显示名称',
  `tenant_key` varchar(255) NOT NULL COMMENT '平台租户标识，例如企业微信CorpID',
  `application_key` varchar(255) DEFAULT NULL COMMENT '平台应用标识，例如企业微信AgentID',
  `status` varchar(32) NOT NULL COMMENT '连接状态：DRAFT草稿、ACTIVE启用、DISABLED停用',
  `diagnostic_status` varchar(32) NOT NULL COMMENT '诊断状态：NOT_RUN未执行、LOCAL_PASSED本地协议通过、FAILED失败、EXTERNAL_PASSED外部联通通过',
  `diagnostic_summary` varchar(1000) DEFAULT NULL COMMENT '不含敏感材料的最近诊断摘要',
  `diagnostic_time` datetime(3) DEFAULT NULL COMMENT '最近诊断时间',
  `created_by_account_id` bigint NOT NULL COMMENT '创建人平台账号主键',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_connection_key` (`connection_key`),
  UNIQUE KEY `uk_app_provider_connection_tenant` (`application_id`,`provider_code`,`product_code`,`tenant_key`),
  KEY `idx_app_provider_connection_status` (`application_id`,`status`),
  CONSTRAINT `chk_app_provider_connection_diagnostic` CHECK ((`diagnostic_status` in (_utf8mb4'NOT_RUN',_utf8mb4'LOCAL_PASSED',_utf8mb4'FAILED',_utf8mb4'EXTERNAL_PASSED'))),
  CONSTRAINT `chk_app_provider_connection_provider` CHECK ((`provider_code` in (_utf8mb4'WECOM',_utf8mb4'FEISHU',_utf8mb4'DINGTALK',_utf8mb4'WECHAT'))),
  CONSTRAINT `chk_app_provider_connection_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台连接';

CREATE TABLE `un_app_provider_secret` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '外部平台密钥版本主键',
  `connection_id` bigint NOT NULL COMMENT '所属外部平台连接主键',
  `secret_type` varchar(32) NOT NULL COMMENT '密钥用途：API_SECRET接口密钥、CALLBACK_TOKEN回调令牌、ENCODING_AES_KEY回调加密密钥',
  `secret_version` int NOT NULL COMMENT '同一连接同一用途的递增版本号',
  `secret_ciphertext` varchar(4000) NOT NULL COMMENT '使用应用主密钥加密后的密文',
  `secret_hint` varchar(32) NOT NULL COMMENT '仅用于辨认版本的脱敏尾号',
  `status` varchar(32) NOT NULL COMMENT '密钥状态：ACTIVE启用、REVOKED已撤销',
  `created_by_account_id` bigint NOT NULL COMMENT '创建人平台账号主键',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `revoked_time` datetime(3) DEFAULT NULL COMMENT '撤销时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_secret_version` (`connection_id`,`secret_type`,`secret_version`),
  KEY `idx_app_provider_secret_active` (`connection_id`,`secret_type`,`status`),
  CONSTRAINT `chk_app_provider_secret_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'REVOKED'))),
  CONSTRAINT `chk_app_provider_secret_type` CHECK ((`secret_type` in (_utf8mb4'API_SECRET',_utf8mb4'CALLBACK_TOKEN',_utf8mb4'ENCODING_AES_KEY')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台密钥版本';

CREATE TABLE `un_app_provider_route` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '外部平台事件路由主键',
  `connection_id` bigint NOT NULL COMMENT '所属外部平台连接主键',
  `event_code` varchar(100) NOT NULL COMMENT '规范化平台事件编码',
  `resource_type` varchar(32) NOT NULL COMMENT '已发布应用授权资源类型',
  `resource_id` varchar(100) NOT NULL COMMENT '已发布应用授权资源业务编码',
  `action_code` varchar(64) NOT NULL COMMENT '已发布应用授权动作编码',
  `target_system_id` bigint DEFAULT NULL COMMENT '目标系统主键',
  `target_tenant_id` bigint DEFAULT NULL COMMENT '目标租户主键',
  `status` varchar(32) NOT NULL COMMENT '路由状态：ACTIVE启用、DISABLED停用',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_route_event` (`connection_id`,`event_code`),
  KEY `idx_app_provider_route_target` (`resource_type`,`resource_id`,`action_code`,`status`),
  CONSTRAINT `chk_app_provider_route_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台事件路由';

CREATE TABLE `un_app_provider_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '外部平台回调事件主键',
  `connection_id` bigint NOT NULL COMMENT '所属外部平台连接主键',
  `provider_event_id` varchar(255) NOT NULL COMMENT '平台事件幂等标识或服务端稳定摘要',
  `event_code` varchar(100) NOT NULL COMMENT '规范化平台事件编码',
  `sender_external_id` varchar(255) DEFAULT NULL COMMENT '平台侧发送人标识',
  `payload_hash` varchar(64) NOT NULL COMMENT '解密后原始载荷SHA-256摘要',
  `status` varchar(32) NOT NULL COMMENT '处理状态：RECEIVED已接收、PROCESSED已完成、IGNORED无路由、FAILED失败',
  `application_call_id` bigint DEFAULT NULL COMMENT '进入应用网关后生成的调用主键',
  `response_code` varchar(64) DEFAULT NULL COMMENT '安全的处理结果编码',
  `received_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收时间',
  `processed_time` datetime(3) DEFAULT NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_event_dedup` (`connection_id`,`provider_event_id`),
  KEY `idx_app_provider_event_status` (`connection_id`,`status`,`received_time`),
  CONSTRAINT `chk_app_provider_event_status` CHECK ((`status` in (_utf8mb4'RECEIVED',_utf8mb4'PROCESSED',_utf8mb4'IGNORED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台回调事件';

CREATE TABLE `un_app_provider_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '外部平台消息投递扩展主键',
  `connection_id` bigint NOT NULL COMMENT '所属外部平台连接主键',
  `identity_id` bigint NOT NULL COMMENT '目标外部平台成员映射主键',
  `message_id` bigint NOT NULL COMMENT '统一消息主键',
  `message_delivery_id` bigint NOT NULL COMMENT '统一消息投递主键',
  `idempotency_key` varchar(255) NOT NULL COMMENT '同一连接内的业务幂等键',
  `request_hash` varchar(64) NOT NULL COMMENT '规范化投递请求SHA-256摘要',
  `deep_link` varchar(1000) NOT NULL COMMENT '按当前上下文生成的站内业务深链',
  `status` varchar(32) NOT NULL COMMENT '投递状态：PENDING待发送、BLOCKED_EXTERNAL缺外部条件、SENT已发送、DELIVERED已送达、FAILED失败、CANCELED已撤销',
  `provider_message_id` varchar(255) DEFAULT NULL COMMENT '平台返回的消息标识',
  `error_code` varchar(100) DEFAULT NULL COMMENT '不含敏感信息的最近错误编码',
  `created_by_account_id` bigint NOT NULL COMMENT '发起人平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `sent_time` datetime(3) DEFAULT NULL COMMENT '实际发送时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_delivery_idempotency` (`connection_id`,`idempotency_key`),
  UNIQUE KEY `uk_app_provider_delivery_message_delivery` (`message_delivery_id`),
  KEY `idx_app_provider_delivery_status` (`connection_id`,`status`,`create_time`),
  CONSTRAINT `chk_app_provider_delivery_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'BLOCKED_EXTERNAL',_utf8mb4'SENT',_utf8mb4'DELIVERED',_utf8mb4'FAILED',_utf8mb4'CANCELED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台消息投递扩展';

CREATE TABLE `un_app_provider_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '外部平台成员映射主键',
  `connection_id` bigint NOT NULL COMMENT '所属外部平台连接主键',
  `external_user_id` varchar(255) NOT NULL COMMENT '平台侧稳定用户标识，例如企业微信UserID',
  `target_type` varchar(32) NOT NULL COMMENT '目标成员类型：PLATFORM平台成员、SYSTEM系统租户成员',
  `target_platform_id` bigint DEFAULT NULL COMMENT '目标平台主键',
  `target_platform_member_id` bigint DEFAULT NULL COMMENT '目标平台成员主键',
  `target_system_id` bigint DEFAULT NULL COMMENT '目标系统主键',
  `target_tenant_id` bigint DEFAULT NULL COMMENT '目标租户主键',
  `target_tenant_member_id` bigint DEFAULT NULL COMMENT '目标租户成员主键',
  `status` varchar(32) NOT NULL COMMENT '映射状态：ACTIVE启用、DISABLED停用',
  `created_by_account_id` bigint NOT NULL COMMENT '创建人平台账号主键',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_identity_external` (`connection_id`,`external_user_id`),
  KEY `idx_app_provider_identity_target_platform` (`target_platform_id`,`target_platform_member_id`,`status`),
  KEY `idx_app_provider_identity_target_system` (`target_system_id`,`target_tenant_id`,`target_tenant_member_id`,`status`),
  CONSTRAINT `chk_app_provider_identity_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED'))),
  CONSTRAINT `chk_app_provider_identity_target` CHECK ((((`target_type` = _utf8mb4'PLATFORM') and (`target_platform_id` is not null) and (`target_platform_member_id` is not null) and (`target_system_id` is null) and (`target_tenant_id` is null) and (`target_tenant_member_id` is null)) or ((`target_type` = _utf8mb4'SYSTEM') and (`target_platform_id` is null) and (`target_platform_member_id` is null) and (`target_system_id` is not null) and (`target_tenant_id` is not null) and (`target_tenant_member_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台成员映射';

CREATE TABLE `un_app_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应用版本主键',
  `application_id` bigint NOT NULL COMMENT '所属应用主键',
  `version_number` int NOT NULL COMMENT '递增的发布版本号',
  `draft_revision` int NOT NULL COMMENT '发布时的草稿修订号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '发布快照内容摘要',
  `snapshot_json` json NOT NULL COMMENT '冻结的应用、授权及回调配置',
  `published_by_account_id` bigint NOT NULL COMMENT '发布人的平台账号主键',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_version_number` (`application_id`,`version_number`),
  UNIQUE KEY `uk_app_version_revision` (`application_id`,`draft_revision`),
  KEY `idx_app_version_application_id` (`application_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用发布时冻结的不可变配置版本';

CREATE TABLE `un_app_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发布指针主键',
  `application_id` bigint NOT NULL COMMENT '所属应用主键',
  `current_version_id` bigint NOT NULL COMMENT '当前生效应用版本主键',
  `updated_by_account_id` bigint NOT NULL COMMENT '修改发布指针的平台账号主键',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '指针更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_publication_definition` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用当前生效版本指针';

CREATE TABLE `un_app_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '凭证主键',
  `application_id` bigint NOT NULL COMMENT '所属应用主键',
  `credential_version` int NOT NULL COMMENT '应用内递增的凭证版本号',
  `client_id` varchar(255) NOT NULL COMMENT '公开的应用访问标识',
  `secret_hash` varchar(255) NOT NULL COMMENT '凭证密钥的不可逆摘要',
  `signing_secret_ref` varchar(2000) DEFAULT NULL COMMENT '签名材料的受控密钥引用，不存明文',
  `secret_hint` varchar(32) NOT NULL COMMENT '仅供识别的脱敏尾号',
  `valid_start_time` datetime(3) NOT NULL COMMENT '凭证生效时间',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '凭证到期时间',
  `status` varchar(32) NOT NULL COMMENT '凭证状态：ACTIVE启用或REVOKED撤销',
  `revoke_time` datetime(3) DEFAULT NULL COMMENT '凭证撤销时间',
  `created_by_account_id` bigint NOT NULL COMMENT '创建凭证的平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_credential_version` (`application_id`,`credential_version`),
  UNIQUE KEY `uk_app_credential_client` (`client_id`),
  KEY `idx_app_credential_active` (`application_id`,`status`,`valid_start_time`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用访问凭证与密钥版本';

CREATE TABLE `un_app_call` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '调用主键',
  `application_id` bigint NOT NULL COMMENT '被调用的目标应用主键',
  `application_version_id` bigint DEFAULT NULL COMMENT '实际执行的不可变应用发布版本主键，前置拒绝时为空',
  `credential_version` int NOT NULL COMMENT '验签所用凭证版本，内部可信调用为0',
  `grant_id` bigint DEFAULT NULL COMMENT '命中的草稿授权主键，前置拒绝或授权历史清理后为空',
  `request_id` varchar(64) NOT NULL COMMENT '调用请求唯一标识',
  `trace_id` varchar(64) NOT NULL COMMENT '跨服务追踪标识',
  `request_time` datetime(3) NOT NULL COMMENT '调用方签名请求时间',
  `nonce` varchar(128) NOT NULL COMMENT '本次签名请求的防重放随机值',
  `idempotency_key` varchar(255) DEFAULT NULL COMMENT '调用方提供的幂等键',
  `source_address` varchar(255) DEFAULT NULL COMMENT '调用方网络地址',
  `target_system_id` bigint DEFAULT NULL COMMENT '请求指向的目标系统主键',
  `target_tenant_id` bigint DEFAULT NULL COMMENT '请求指向的目标租户主键',
  `resource_type` varchar(32) NOT NULL COMMENT '请求的资源类型',
  `resource_id` varchar(100) NOT NULL COMMENT '请求的资源业务编码',
  `action_code` varchar(64) NOT NULL COMMENT '请求的动作编码',
  `request_hash` varchar(64) NOT NULL COMMENT '规范化请求内容摘要',
  `permission_snapshot_json` json DEFAULT NULL COMMENT '调用时已发布授权和目标身份的不可变权限快照',
  `status` varchar(32) NOT NULL COMMENT '调用处理状态',
  `response_code` varchar(64) DEFAULT NULL COMMENT '业务响应编码',
  `response_json` json DEFAULT NULL COMMENT '允许审计读取的业务响应结果',
  `target_reference` varchar(255) DEFAULT NULL COMMENT '调用产生或访问的目标业务对象引用',
  `replay_count` int NOT NULL DEFAULT '0' COMMENT '相同幂等请求复用结果的次数',
  `duration_millis` bigint DEFAULT NULL COMMENT '请求处理耗时毫秒数',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '不含敏感信息的错误摘要',
  `call_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始调用时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '完成调用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_call_request` (`application_id`,`request_id`),
  UNIQUE KEY `uk_app_call_nonce` (`application_id`,`nonce`),
  UNIQUE KEY `uk_app_call_idempotency` (`application_id`,`idempotency_key`),
  KEY `idx_app_call_target` (`target_system_id`,`target_tenant_id`,`call_time`),
  KEY `idx_app_call_status` (`application_id`,`status`,`call_time`),
  KEY `idx_app_call_version` (`application_id`,`application_version_id`),
  CONSTRAINT `chk_app_call_success_evidence` CHECK (((`status` <> _utf8mb4'SUCCESS') or ((`application_version_id` is not null) and (`permission_snapshot_json` is not null) and (`response_json` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用调用审计与幂等结果';

CREATE TABLE `un_app_call_nonce` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '已接受签名nonce主键',
  `application_id` bigint NOT NULL COMMENT '目标应用主键',
  `credential_version` int NOT NULL COMMENT '验签所用应用凭证版本号',
  `nonce` varchar(128) NOT NULL COMMENT '请求防重放随机值',
  `request_id` varchar(64) NOT NULL COMMENT '首次接受该nonce的请求标识',
  `request_time` datetime(3) NOT NULL COMMENT '调用方签名请求时间',
  `expire_time` datetime(3) NOT NULL COMMENT 'nonce失效时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_call_nonce_value` (`application_id`,`nonce`),
  KEY `idx_app_call_nonce_expiry` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已接受应用签名nonce的防重放记录';

CREATE TABLE `un_app_callback_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '回调投递主键',
  `callback_id` bigint NOT NULL COMMENT '所属回调配置主键',
  `event_id` varchar(100) NOT NULL COMMENT '业务事件唯一标识',
  `payload_hash` varchar(64) NOT NULL COMMENT '投递内容摘要',
  `status` varchar(32) NOT NULL COMMENT '投递状态',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已尝试投递次数',
  `next_attempt_time` datetime(3) DEFAULT NULL COMMENT '下次允许重试时间',
  `response_status` int DEFAULT NULL COMMENT '接收端HTTP状态码',
  `response_body_excerpt` varchar(2000) DEFAULT NULL COMMENT '接收端响应的脱敏摘要',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_callback_event` (`callback_id`,`event_id`),
  KEY `idx_app_callback_retry` (`status`,`next_attempt_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用事件回调投递及重试结果';

-- source: init/V11__work.sql
-- work 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_work_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `context_type` varchar(32) NOT NULL COMMENT '业务上下文类型：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统标识，平台范围为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户标识，平台范围为空',
  `code` varchar(100) NOT NULL COMMENT '同一工作范围内的项目编码',
  `name` varchar(200) NOT NULL COMMENT '名称',
  `description` varchar(2000) DEFAULT NULL COMMENT '业务说明',
  `customer_name` varchar(200) DEFAULT NULL COMMENT '客户名称',
  `internal_department_name` varchar(200) DEFAULT NULL COMMENT '内部部门名称',
  `stage` varchar(64) DEFAULT NULL COMMENT '项目阶段',
  `labels_json` json DEFAULT NULL COMMENT '项目标签列表',
  `business_type` varchar(64) DEFAULT NULL COMMENT '关联业务类型',
  `business_id` varchar(100) DEFAULT NULL COMMENT '关联业务记录标识',
  `business_title` varchar(500) DEFAULT NULL COMMENT '关联业务记录标题',
  `owner_account_id` bigint NOT NULL COMMENT '负责人平台账号标识',
  `owner_tenant_member_id` bigint DEFAULT NULL COMMENT '项目负责人租户成员ID',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `due_date` date DEFAULT NULL COMMENT '截止日期',
  `progress_percent` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '完成百分比',
  `status` varchar(32) NOT NULL COMMENT '业务状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_project_code` (`context_type`,`platform_id`,(coalesce(`system_id`,0)),(coalesce(`tenant_id`,0)),`code`),
  KEY `idx_work_project_owner` (`owner_account_id`,`status`,`due_date`),
  KEY `idx_work_project_business` (`system_id`,`tenant_id`,`business_type`,`business_id`),
  CONSTRAINT `chk_work_project_business_pair` CHECK ((((`business_type` is null) and (`business_id` is null)) or ((`business_type` is not null) and (`business_id` is not null)))),
  CONSTRAINT `chk_work_project_owner_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`owner_tenant_member_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`owner_tenant_member_id` is not null)))),
  CONSTRAINT `chk_work_project_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作项目';

CREATE TABLE `un_work_project_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '所属项目标识',
  `account_id` bigint NOT NULL COMMENT '参与人平台账号标识',
  `tenant_member_id` bigint DEFAULT NULL COMMENT '项目参与人租户成员ID',
  `project_role` varchar(64) NOT NULL COMMENT '项目成员角色',
  `status` varchar(32) NOT NULL COMMENT '业务状态',
  `join_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_project_member` (`project_id`,`account_id`),
  KEY `idx_work_project_member_tenant_member` (`tenant_member_id`,`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作项目参与人';

CREATE TABLE `un_work_task_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '所属项目标识',
  `name` varchar(200) NOT NULL COMMENT '名称',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '组内排序',
  `status` varchar(32) NOT NULL COMMENT '业务状态',
  `owner_account_id` bigint DEFAULT NULL COMMENT '任务组负责人账号标识',
  `owner_tenant_member_id` bigint DEFAULT NULL COMMENT '任务组负责人租户成员标识',
  `due_time` datetime(3) DEFAULT NULL COMMENT '任务组截止时间',
  `progress_percent` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '任务组完成百分比',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_task_group_order` (`project_id`,`sort_order`),
  KEY `idx_work_task_group_owner` (`owner_account_id`,`status`,`due_time`),
  CONSTRAINT `chk_work_task_group_owner_pair` CHECK ((((`owner_account_id` is null) and (`owner_tenant_member_id` is null)) or (`owner_account_id` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目任务组';

CREATE TABLE `un_work_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `context_type` varchar(32) NOT NULL COMMENT '业务上下文类型：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统标识，平台范围为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户标识，平台范围为空',
  `project_id` bigint DEFAULT NULL COMMENT '所属项目标识',
  `task_group_id` bigint DEFAULT NULL COMMENT '所属项目任务组标识',
  `parent_task_id` bigint DEFAULT NULL COMMENT '父任务标识',
  `title` varchar(500) NOT NULL COMMENT '标题',
  `description` text COMMENT '业务说明',
  `task_type` varchar(32) NOT NULL COMMENT '任务类型：普通或项目任务',
  `priority` varchar(32) NOT NULL COMMENT '任务优先级',
  `status` varchar(32) NOT NULL COMMENT '业务状态',
  `progress_percent` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '完成百分比',
  `estimated_hours` decimal(8,2) DEFAULT NULL COMMENT '预计工时',
  `actual_hours` decimal(8,2) DEFAULT NULL COMMENT '实际工时',
  `labels_json` json DEFAULT NULL COMMENT '任务标签列表',
  `checklist_json` json DEFAULT NULL COMMENT '任务检查清单',
  `owner_account_id` bigint NOT NULL COMMENT '负责人平台账号标识',
  `owner_tenant_member_id` bigint DEFAULT NULL COMMENT '任务负责人租户成员ID',
  `start_time` datetime(3) DEFAULT NULL COMMENT '任务开始时间',
  `due_time` datetime(3) DEFAULT NULL COMMENT '任务截止时间',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '任务完成时间',
  `completion_note` varchar(2000) DEFAULT NULL COMMENT '完成说明',
  `completion_submitted_time` datetime(3) DEFAULT NULL COMMENT '提交完成时间',
  `completion_accepted_time` datetime(3) DEFAULT NULL COMMENT '验收通过时间',
  `archive_time` datetime(3) DEFAULT NULL COMMENT '归档时间',
  `delete_time` datetime(3) DEFAULT NULL COMMENT '任务删除时间',
  `custom_values_json` json DEFAULT NULL COMMENT '可配置字段值',
  `business_type` varchar(64) DEFAULT NULL COMMENT '关联业务对象类型',
  `business_id` varchar(100) DEFAULT NULL COMMENT '关联业务对象标识',
  `business_title` varchar(500) DEFAULT NULL COMMENT '关联业务对象标题快照',
  `created_by_account_id` bigint NOT NULL COMMENT '创建人平台账号标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_work_task_context_id` (`context_type`,`id`),
  KEY `idx_work_task_owner` (`owner_account_id`,`status`,`due_time`),
  KEY `idx_work_task_project` (`project_id`,`task_group_id`,`status`),
  KEY `idx_work_task_context` (`system_id`,`tenant_id`,`status`,`update_time`),
  KEY `idx_work_task_owner_member` (`owner_tenant_member_id`,`status`,`due_time`),
  KEY `idx_work_task_business` (`system_id`,`tenant_id`,`business_type`,`business_id`),
  KEY `idx_work_task_archive` (`system_id`,`tenant_id`,`archive_time`),
  KEY `idx_work_task_delete` (`system_id`,`tenant_id`,`delete_time`),
  CONSTRAINT `chk_work_task_business_pair` CHECK ((((`business_type` is null) and (`business_id` is null)) or ((`business_type` is not null) and (`business_id` is not null)))),
  CONSTRAINT `chk_work_task_group_project` CHECK (((`task_group_id` is null) or (`project_id` is not null))),
  CONSTRAINT `chk_work_task_owner_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`owner_tenant_member_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`owner_tenant_member_id` is not null)))),
  CONSTRAINT `chk_work_task_project_type` CHECK (((`task_type` <> _utf8mb4'PROJECT') or (`project_id` is not null))),
  CONSTRAINT `chk_work_task_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作任务';

CREATE TABLE `un_work_task_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '关联任务标识',
  `account_id` bigint NOT NULL COMMENT '参与人平台账号标识',
  `tenant_member_id` bigint DEFAULT NULL COMMENT '任务参与人租户成员ID',
  `member_type` varchar(32) NOT NULL COMMENT '任务成员参与类型',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_task_member` (`task_id`,`account_id`,`member_type`),
  KEY `idx_work_task_member_account` (`account_id`,`task_id`),
  KEY `idx_work_task_member_tenant_member` (`tenant_member_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务协作、关注和验收成员';

CREATE TABLE `un_work_task_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '关联任务标识',
  `action_code` varchar(64) NOT NULL COMMENT '任务操作编码',
  `before_json` json DEFAULT NULL COMMENT '操作前任务快照',
  `after_json` json NOT NULL COMMENT '操作后任务快照',
  `comment_text` varchar(2000) DEFAULT NULL COMMENT '操作补充说明',
  `changed_by_account_id` bigint NOT NULL COMMENT '操作人平台账号标识',
  `change_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_work_task_history` (`task_id`,`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务状态与业务操作历史';

CREATE TABLE `un_work_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `context_type` varchar(32) NOT NULL COMMENT '业务上下文类型：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统标识，平台范围为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户标识，平台范围为空',
  `project_id` bigint DEFAULT NULL COMMENT '日志关联的同一工作范围项目标识',
  `author_account_id` bigint NOT NULL COMMENT '日志作者平台账号标识',
  `author_tenant_member_id` bigint DEFAULT NULL COMMENT '工作日志作者租户成员ID',
  `work_date` date NOT NULL COMMENT '工作日志日期',
  `title` varchar(500) NOT NULL COMMENT '标题',
  `content_text` text NOT NULL COMMENT '工作日志摘要',
  `completed_text` text COMMENT '今日已完成事项',
  `in_progress_text` text COMMENT '当前进行中事项',
  `next_plan_text` text COMMENT '下一工作日计划',
  `risk_text` text COMMENT '风险与阻塞说明',
  `assistance_text` text COMMENT '需要协助的事项',
  `duration_minutes` int DEFAULT NULL COMMENT '记录工时分钟数',
  `status` varchar(32) NOT NULL COMMENT '业务状态',
  `visibility_type` varchar(32) NOT NULL DEFAULT 'SELF' COMMENT '可见范围：本人、直属管理、项目成员、同部门或指定角色',
  `visible_role_ids_json` json DEFAULT NULL COMMENT '指定可见角色标识快照',
  `submitted_time` datetime(3) DEFAULT NULL COMMENT '最近一次提交时间',
  `withdrawn_time` datetime(3) DEFAULT NULL COMMENT '最近一次撤回时间',
  `custom_values_json` json DEFAULT NULL COMMENT '可配置字段值',
  `business_type` varchar(64) DEFAULT NULL COMMENT '关联业务对象类型',
  `business_id` varchar(100) DEFAULT NULL COMMENT '关联业务对象标识',
  `business_title` varchar(500) DEFAULT NULL COMMENT '关联业务对象标题快照',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_work_log_author_date` (`author_account_id`,`work_date`),
  KEY `idx_work_log_context` (`system_id`,`tenant_id`,`work_date`),
  KEY `idx_work_log_author_member` (`author_tenant_member_id`,`work_date`),
  KEY `idx_work_log_business` (`system_id`,`tenant_id`,`business_type`,`business_id`),
  KEY `idx_work_log_project_date` (`project_id`,`work_date`),
  KEY `idx_work_log_visibility` (`system_id`,`tenant_id`,`visibility_type`,`work_date`),
  CONSTRAINT `chk_work_log_author_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`author_tenant_member_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`author_tenant_member_id` is not null)))),
  CONSTRAINT `chk_work_log_business_pair` CHECK ((((`business_type` is null) and (`business_id` is null)) or ((`business_type` is not null) and (`business_id` is not null)))),
  CONSTRAINT `chk_work_log_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户手动创建的每日工作日志';

CREATE TABLE `un_work_log_task_link` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志任务关联主键',
  `work_log_id` bigint NOT NULL COMMENT '所属工作日志标识',
  `task_id` bigint NOT NULL COMMENT '同一工作范围的任务标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关联创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_task_link` (`work_log_id`,`task_id`),
  KEY `idx_work_log_task_link_task` (`task_id`,`work_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同一范围的工作日志与任务关联';

CREATE TABLE `un_work_log_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `work_log_id` bigint NOT NULL COMMENT '关联工作日志标识',
  `revision_number` int NOT NULL COMMENT '日志修订序号',
  `snapshot_json` json NOT NULL COMMENT '日志修订内容快照',
  `revision_reason` varchar(1000) NOT NULL COMMENT '日志修订原因',
  `revised_by_account_id` bigint NOT NULL COMMENT '修订人平台账号标识',
  `revise_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '日志修订时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_revision` (`work_log_id`,`revision_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作日志修订快照';

CREATE TABLE `un_work_log_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志选材主键',
  `work_log_id` bigint NOT NULL COMMENT '工作日志主键',
  `material_type` varchar(32) NOT NULL COMMENT '材料类型：任务、待办、消息或业务操作',
  `material_id` varchar(100) NOT NULL COMMENT '来源材料标识',
  `material_title` varchar(500) NOT NULL COMMENT '选中时的材料标题快照',
  `material_summary` varchar(2000) DEFAULT NULL COMMENT '选中时的材料摘要快照',
  `source_time` datetime(3) DEFAULT NULL COMMENT '来源材料发生时间',
  `created_by_account_id` bigint NOT NULL COMMENT '选择材料的平台账号主键',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '材料快照创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_material` (`work_log_id`,`material_type`,`material_id`),
  KEY `idx_work_log_material_source` (`material_type`,`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作日志人工选材快照';

CREATE TABLE `un_work_log_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志沟通记录主键',
  `work_log_id` bigint NOT NULL COMMENT '工作日志主键',
  `comment_type` varchar(32) NOT NULL COMMENT '沟通类型：评论或反馈',
  `comment_text` varchar(2000) NOT NULL COMMENT '评论或反馈正文',
  `created_by_account_id` bigint NOT NULL COMMENT '发表人的平台账号主键',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发表时间',
  PRIMARY KEY (`id`),
  KEY `idx_work_log_comment` (`work_log_id`,`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作日志评论与管理反馈';

CREATE TABLE `un_work_field_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `context_type` varchar(32) NOT NULL COMMENT '业务上下文类型：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统标识，平台范围为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '所属租户标识，平台范围为空',
  `target_type` varchar(32) NOT NULL COMMENT '可配置字段所属对象类型',
  `field_code` varchar(100) NOT NULL COMMENT '同一对象范围内的字段编码',
  `field_name` varchar(200) NOT NULL COMMENT '字段显示名称',
  `field_type` varchar(64) NOT NULL COMMENT '字段数据类型',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '组内排序',
  `config_json` json NOT NULL COMMENT '字段展示与校验配置',
  `status` varchar(32) NOT NULL COMMENT '业务状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_field_config` (`context_type`,`platform_id`,(coalesce(`system_id`,0)),(coalesce(`tenant_id`,0)),`target_type`,`field_code`),
  CONSTRAINT `chk_work_field_config_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作域的可配置字段';

-- source: init/V12__notification.sql
-- notification 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_plat_todo_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台待办主键',
  `platform_id` bigint NOT NULL COMMENT '待办所属平台主键',
  `assignee_account_id` bigint NOT NULL COMMENT '待办接收平台成员账号主键',
  `todo_type` varchar(64) NOT NULL COMMENT '平台待办类别',
  `source_type` varchar(64) NOT NULL COMMENT '平台来源业务类别',
  `source_id` varchar(100) NOT NULL COMMENT '平台来源业务标识',
  `title` varchar(500) NOT NULL COMMENT '待办标题快照',
  `summary` varchar(1000) DEFAULT NULL COMMENT '待办摘要快照',
  `target_route` varchar(1000) NOT NULL COMMENT '平台路由或系统切换入口，不得直达系统业务详情',
  `priority` varchar(32) NOT NULL COMMENT '待办优先级',
  `due_time` datetime(3) DEFAULT NULL COMMENT '待办截止时间',
  `status` varchar(32) NOT NULL COMMENT '平台待办投影状态',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '待办处理完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '待办创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '待办更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_todo_source_assignee` (`platform_id`,`assignee_account_id`,`source_type`,`source_id`,`todo_type`),
  KEY `idx_plat_todo_assignee` (`platform_id`,`assignee_account_id`,`status`,`due_time`,`create_time`),
  CONSTRAINT `chk_plat_todo_route` CHECK (((`target_route` = _utf8mb4'/systems') or (`target_route` = _utf8mb4'/profile') or (`target_route` = _utf8mb4'/platform') or (`target_route` like _utf8mb4'/platform/%')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台自身业务的待办投影';

CREATE TABLE `un_plat_msg_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台消息模板主键',
  `platform_id` bigint NOT NULL COMMENT '模板所属平台主键',
  `code` varchar(100) NOT NULL COMMENT '平台范围内的模板编码',
  `name` varchar(200) NOT NULL COMMENT '模板显示名称',
  `channel` varchar(32) NOT NULL COMMENT '通知发送渠道',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '当前草稿修订号',
  `subject_template` varchar(500) DEFAULT NULL COMMENT '消息主题模板',
  `content_template` text NOT NULL COMMENT '消息正文模板',
  `variable_schema_json` json NOT NULL COMMENT '模板变量定义',
  `status` varchar(32) NOT NULL COMMENT '模板草稿或发布状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建模板的平台成员账号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '模板创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '模板更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_msg_template_code` (`platform_id`,`code`,`channel`),
  KEY `idx_plat_msg_template_scope_id` (`id`,`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台消息模板草稿';

CREATE TABLE `un_plat_msg_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台消息模板发布版本主键',
  `template_id` bigint NOT NULL COMMENT '所属平台消息模板主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `version_number` int NOT NULL COMMENT '模板发布版本号',
  `draft_revision` int NOT NULL COMMENT '被发布的草稿修订号',
  `snapshot_json` json NOT NULL COMMENT '发布时不可变的模板内容快照',
  `published_by_account_id` bigint NOT NULL COMMENT '发布模板的平台成员账号',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '模板发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_msg_template_version` (`template_id`,`version_number`),
  UNIQUE KEY `uk_plat_msg_template_revision` (`template_id`,`draft_revision`),
  KEY `idx_plat_msg_template_version_scope_id` (`id`,`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台消息模板不可变发布版本';

CREATE TABLE `un_plat_msg_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台消息主键',
  `platform_id` bigint NOT NULL COMMENT '消息所属平台主键',
  `template_version_id` bigint DEFAULT NULL COMMENT '模板消息的发布版本主键；平台内部事件消息为空',
  `source_type` varchar(64) NOT NULL COMMENT '平台通知来源类别',
  `source_id` varchar(100) DEFAULT NULL COMMENT '来源事件去重键，为空时不参与去重',
  `subject` varchar(500) NOT NULL COMMENT '发送时渲染的主题快照',
  `content_text` text NOT NULL COMMENT '发送时渲染的正文快照',
  `target_type` varchar(64) DEFAULT NULL COMMENT '平台目标类别，不得指向系统业务详情',
  `target_id` varchar(100) DEFAULT NULL COMMENT '平台目标标识或系统切换提示标识',
  `target_route` varchar(1000) DEFAULT NULL COMMENT '平台路由或系统切换入口',
  `sensitivity` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '消息敏感级别',
  `created_by_account_id` bigint DEFAULT NULL COMMENT '发起通知的平台成员账号，自动通知可为空',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_msg_message_event_dedup` (`platform_id`,`source_type`,`source_id`),
  KEY `idx_plat_msg_message_scope_id` (`id`,`platform_id`),
  KEY `idx_plat_msg_message_context` (`platform_id`,`create_time`),
  CONSTRAINT `chk_plat_msg_target_route` CHECK (((`target_route` is null) or (`target_route` = _utf8mb4'/systems') or (`target_route` = _utf8mb4'/profile') or (`target_route` = _utf8mb4'/platform') or (`target_route` like _utf8mb4'/platform/%'))),
  CONSTRAINT `chk_plat_msg_target_type` CHECK (((`target_type` is null) or (`target_type` not in (_utf8mb4'SYSTEM_ROUTE',_utf8mb4'RUNTIME_RECORD',_utf8mb4'MODULE_RECORD',_utf8mb4'BIZ_RECORD'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台自身通知消息及受限跳转目标';

CREATE TABLE `un_plat_msg_recipient` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台消息接收记录主键',
  `message_id` bigint NOT NULL COMMENT '所属平台消息主键',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `account_id` bigint NOT NULL COMMENT '接收平台成员账号主键',
  `status` varchar(32) NOT NULL DEFAULT 'UNREAD' COMMENT '消息阅读状态',
  `read_time` datetime(3) DEFAULT NULL COMMENT '首次阅读时间',
  `archive_time` datetime(3) DEFAULT NULL COMMENT '归档时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收记录创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '接收记录更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_msg_recipient` (`message_id`,`account_id`),
  KEY `idx_plat_msg_recipient_inbox` (`platform_id`,`account_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台消息成员收件箱及回执';

CREATE TABLE `un_plat_msg_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台消息渠道投递主键',
  `message_id` bigint NOT NULL COMMENT '所属平台消息主键',
  `account_id` bigint NOT NULL COMMENT '投递目标账号主键',
  `channel` varchar(32) NOT NULL COMMENT '投递渠道',
  `destination_masked` varchar(255) DEFAULT NULL COMMENT '脱敏后的投递目的地',
  `status` varchar(32) NOT NULL COMMENT '投递状态',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已执行的投递次数',
  `next_attempt_time` datetime(3) DEFAULT NULL COMMENT '下一次允许重试时间',
  `provider_message_id` varchar(255) DEFAULT NULL COMMENT '外部渠道返回的消息标识',
  `provider_receipt_json` json DEFAULT NULL COMMENT '外部渠道投递回执',
  `last_error` varchar(2000) DEFAULT NULL COMMENT '最近一次失败摘要，不含密钥',
  `send_time` datetime(3) DEFAULT NULL COMMENT '发送成功时间',
  `deliver_time` datetime(3) DEFAULT NULL COMMENT '渠道确认送达时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '投递记录创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '投递记录更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_msg_delivery_channel` (`message_id`,`account_id`,`channel`),
  KEY `idx_plat_msg_delivery_retry` (`status`,`next_attempt_time`),
  CONSTRAINT `chk_plat_msg_delivery_attempt` CHECK ((`attempt_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台消息渠道投递及失败重试记录';

CREATE TABLE `un_plat_msg_user_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '平台消息偏好主键',
  `platform_id` bigint NOT NULL COMMENT '偏好所属平台主键',
  `account_id` bigint NOT NULL COMMENT '偏好所属平台成员账号主键',
  `event_code` varchar(100) NOT NULL COMMENT '通知事件编码',
  `channel` varchar(32) NOT NULL COMMENT '偏好对应投递渠道',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用此类通知',
  `quiet_hours_json` json DEFAULT NULL COMMENT '免打扰时段定义',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '偏好创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '偏好更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_msg_user_preference` (`platform_id`,`account_id`,`event_code`,`channel`),
  CONSTRAINT `chk_plat_msg_preference_enabled` CHECK ((`enabled` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台成员消息渠道和免打扰偏好';

CREATE TABLE `un_todo_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统待办主键',
  `context_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '固定为系统上下文',
  `platform_id` bigint NOT NULL COMMENT '系统所属平台主键',
  `system_id` bigint NOT NULL COMMENT '待办所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '待办所属租户主键',
  `assignee_account_id` bigint NOT NULL COMMENT '待办接收账号主键',
  `assignee_member_id` bigint NOT NULL COMMENT '接收人在当前系统中的成员主键',
  `todo_type` varchar(64) NOT NULL COMMENT '待办类别，例如审批或工作任务',
  `source_type` varchar(64) NOT NULL COMMENT '来源业务对象类别',
  `source_id` varchar(100) NOT NULL COMMENT '来源业务对象标识，处理时须重新校验来源状态',
  `title` varchar(500) NOT NULL COMMENT '待办标题快照',
  `summary` varchar(1000) DEFAULT NULL COMMENT '待办摘要快照',
  `target_route` varchar(1000) NOT NULL COMMENT '系统范围内的跳转路由，打开前须切换并验证权限',
  `priority` varchar(32) NOT NULL COMMENT '待办优先级',
  `due_time` datetime(3) DEFAULT NULL COMMENT '待办截止时间',
  `status` varchar(32) NOT NULL COMMENT '待办投影状态，不替代来源状态',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '待办处理完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '待办创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '待办更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_todo_source_assignee` (`system_id`,`tenant_id`,`assignee_account_id`,`source_type`,`source_id`,`todo_type`),
  KEY `idx_todo_assignee` (`system_id`,`tenant_id`,`assignee_account_id`,`status`,`due_time`,`create_time`),
  CONSTRAINT `chk_todo_system_scope` CHECK (((`context_type` = _utf8mb4'SYSTEM') and ((`target_route` = concat(_utf8mb4'/systems/',`system_id`)) or (`target_route` like concat(_utf8mb4'/systems/',`system_id`,_utf8mb4'?%')) or (`target_route` like concat(_utf8mb4'/systems/',`system_id`,_utf8mb4'/%')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统租户内可处理待办投影';

CREATE TABLE `un_msg_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统消息模板主键',
  `context_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '固定为系统上下文',
  `platform_id` bigint NOT NULL COMMENT '系统所属平台主键',
  `system_id` bigint NOT NULL COMMENT '模板所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '模板所属租户主键',
  `code` varchar(100) NOT NULL COMMENT '租户范围内的模板编码',
  `name` varchar(200) NOT NULL COMMENT '模板显示名称',
  `channel` varchar(32) NOT NULL COMMENT '通知发送渠道',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '当前草稿修订号',
  `subject_template` varchar(500) DEFAULT NULL COMMENT '消息主题模板',
  `content_template` text NOT NULL COMMENT '消息正文模板',
  `variable_schema_json` json NOT NULL COMMENT '模板变量定义',
  `status` varchar(32) NOT NULL COMMENT '模板草稿或发布状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建模板的系统成员账号',
  `created_by_member_id` bigint NOT NULL COMMENT '创建模板的当前系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '模板创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '模板更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_template_code` (`system_id`,`tenant_id`,`code`,`channel`),
  KEY `idx_msg_template_scope_id` (`id`,`system_id`,`tenant_id`),
  CONSTRAINT `chk_msg_template_scope` CHECK ((`context_type` = _utf8mb4'SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统租户消息模板草稿';

CREATE TABLE `un_msg_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统消息模板发布版本主键',
  `template_id` bigint NOT NULL COMMENT '所属消息模板主键',
  `system_id` bigint NOT NULL COMMENT '模板所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '模板所属租户主键',
  `version_number` int NOT NULL COMMENT '模板发布版本号',
  `draft_revision` int NOT NULL COMMENT '被发布的草稿修订号',
  `snapshot_json` json NOT NULL COMMENT '发布时不可变的模板内容快照',
  `published_by_account_id` bigint NOT NULL COMMENT '发布模板的系统成员账号',
  `published_by_member_id` bigint NOT NULL COMMENT '发布模板的当前系统成员主键',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '模板发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_template_version` (`template_id`,`version_number`),
  UNIQUE KEY `uk_msg_template_revision` (`template_id`,`draft_revision`),
  KEY `idx_msg_template_version_scope_id` (`id`,`system_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统消息模板不可变发布版本';

CREATE TABLE `un_msg_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统消息主键',
  `context_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '固定为系统上下文',
  `platform_id` bigint NOT NULL COMMENT '系统所属平台主键',
  `system_id` bigint NOT NULL COMMENT '消息所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '消息所属租户主键',
  `template_version_id` bigint DEFAULT NULL COMMENT '模板消息的发布版本主键；内部事件消息为空',
  `source_type` varchar(64) NOT NULL COMMENT '通知来源业务类别',
  `source_id` varchar(100) DEFAULT NULL COMMENT '来源事件去重键，为空时不参与事件去重',
  `subject` varchar(500) NOT NULL COMMENT '发送时渲染的主题快照',
  `content_text` text NOT NULL COMMENT '发送时渲染的正文快照',
  `target_type` varchar(64) DEFAULT NULL COMMENT '系统业务目标类别',
  `target_id` varchar(100) DEFAULT NULL COMMENT '系统业务目标标识',
  `target_route` varchar(1000) DEFAULT NULL COMMENT '当前系统路由，打开前须复核成员及对象权限',
  `sensitivity` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '消息敏感级别',
  `created_by_account_id` bigint DEFAULT NULL COMMENT '发起通知的系统成员账号，自动通知可为空',
  `created_by_member_id` bigint DEFAULT NULL COMMENT '发起通知的系统成员主键，自动通知可为空',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_message_event_dedup` (`system_id`,`tenant_id`,`source_type`,`source_id`),
  KEY `idx_msg_message_scope_id` (`id`,`system_id`,`tenant_id`),
  KEY `idx_msg_message_context` (`system_id`,`tenant_id`,`create_time`),
  CONSTRAINT `chk_msg_message_scope` CHECK (((`context_type` = _utf8mb4'SYSTEM') and (((`created_by_account_id` is null) and (`created_by_member_id` is null)) or ((`created_by_account_id` is not null) and (`created_by_member_id` is not null))) and ((`target_route` is null) or (`target_route` = concat(_utf8mb4'/systems/',`system_id`)) or (`target_route` like concat(_utf8mb4'/systems/',`system_id`,_utf8mb4'?%')) or (`target_route` like concat(_utf8mb4'/systems/',`system_id`,_utf8mb4'/%')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统租户通知消息及跳转目标快照';

CREATE TABLE `un_msg_recipient` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统消息接收记录主键',
  `message_id` bigint NOT NULL COMMENT '所属系统消息主键',
  `system_id` bigint NOT NULL COMMENT '所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户主键',
  `account_id` bigint NOT NULL COMMENT '接收人的平台账号主键',
  `system_member_id` bigint NOT NULL COMMENT '接收人在系统中的成员主键',
  `status` varchar(32) NOT NULL DEFAULT 'UNREAD' COMMENT '消息阅读状态',
  `read_time` datetime(3) DEFAULT NULL COMMENT '首次阅读时间',
  `archive_time` datetime(3) DEFAULT NULL COMMENT '归档时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收记录创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '接收记录更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_recipient` (`message_id`,`account_id`),
  KEY `idx_msg_recipient_inbox` (`system_id`,`tenant_id`,`account_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统消息成员收件箱及回执';

CREATE TABLE `un_msg_delivery` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统消息渠道投递主键',
  `message_id` bigint NOT NULL COMMENT '所属系统消息主键',
  `account_id` bigint NOT NULL COMMENT '投递目标账号主键',
  `channel` varchar(32) NOT NULL COMMENT '投递渠道',
  `destination_masked` varchar(255) DEFAULT NULL COMMENT '脱敏后的投递目的地',
  `status` varchar(32) NOT NULL COMMENT '投递状态',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已执行的投递次数',
  `next_attempt_time` datetime(3) DEFAULT NULL COMMENT '下一次允许重试时间',
  `provider_message_id` varchar(255) DEFAULT NULL COMMENT '外部渠道返回的消息标识',
  `provider_receipt_json` json DEFAULT NULL COMMENT '外部渠道投递回执',
  `last_error` varchar(2000) DEFAULT NULL COMMENT '最近一次失败摘要，不含密钥',
  `send_time` datetime(3) DEFAULT NULL COMMENT '发送成功时间',
  `deliver_time` datetime(3) DEFAULT NULL COMMENT '渠道确认送达时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '投递记录创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '投递记录更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_delivery_channel` (`message_id`,`account_id`,`channel`),
  KEY `idx_msg_delivery_retry` (`status`,`next_attempt_time`),
  CONSTRAINT `chk_msg_delivery_attempt` CHECK ((`attempt_count` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统消息渠道投递及失败重试记录';

CREATE TABLE `un_msg_user_preference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系统消息偏好主键',
  `account_id` bigint NOT NULL COMMENT '偏好所属平台账号主键',
  `system_member_id` bigint NOT NULL COMMENT '偏好所属系统成员主键',
  `context_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '固定为系统上下文',
  `platform_id` bigint NOT NULL COMMENT '系统所属平台主键',
  `system_id` bigint NOT NULL COMMENT '偏好所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '偏好所属租户主键',
  `event_code` varchar(100) NOT NULL COMMENT '通知事件编码',
  `channel` varchar(32) NOT NULL COMMENT '偏好对应的投递渠道',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用此类通知',
  `quiet_hours_json` json DEFAULT NULL COMMENT '免打扰时段定义',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '偏好创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '偏好更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_user_preference` (`account_id`,`system_id`,`tenant_id`,`event_code`,`channel`),
  CONSTRAINT `chk_msg_preference_scope` CHECK (((`context_type` = _utf8mb4'SYSTEM') and (`enabled` in (0,1))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统租户成员消息渠道和免打扰偏好';

CREATE TABLE `un_todo_contact_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '今日联系规则主键',
  `context_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '固定为系统上下文，平台不承载模块数据联系规则',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint NOT NULL COMMENT '所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户主键',
  `scope_key` varchar(160) NOT NULL COMMENT '平台、系统和租户组成的稳定配置范围键',
  `name` varchar(200) NOT NULL COMMENT '规则显示名称',
  `module_code` varchar(100) NOT NULL COMMENT '已发布业务模块编码',
  `module_name_snapshot` varchar(200) NOT NULL COMMENT '保存规则时的业务模块名称快照',
  `contact_field_code` varchar(100) NOT NULL COMMENT '用于计算联系日期的字段编码',
  `contact_field_name_snapshot` varchar(200) NOT NULL COMMENT '保存规则时的日期字段名称快照',
  `contact_field_type` varchar(32) NOT NULL COMMENT '联系字段类型：日期或日期时间',
  `lead_days` int NOT NULL DEFAULT '0' COMMENT '提前生成待办的天数，零表示字段日期当天',
  `priority` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '生成待办的优先级',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '规则状态：草稿、已发布或已停用',
  `created_by_account_id` bigint NOT NULL COMMENT '创建规则的平台账号主键',
  `created_by_member_id` bigint NOT NULL COMMENT '创建规则的当前系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '规则创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '规则最后更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_todo_contact_rule_scope` (`system_id`,`tenant_id`,`module_code`,`contact_field_code`,`lead_days`),
  KEY `idx_todo_contact_rule_runtime` (`system_id`,`tenant_id`,`status`),
  CONSTRAINT `chk_todo_contact_rule_scope` CHECK (((`context_type` = _utf8mb4'SYSTEM') and (`scope_key` = concat(_utf8mb4'P:',`platform_id`,_utf8mb4':S:',`system_id`,_utf8mb4':T:',`tenant_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务模块日期字段的今日联系待办生成规则';

-- source: init/V13__ai.sql
-- ai 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型主键',
  `platform_id` bigint NOT NULL COMMENT '模型所属平台标识',
  `scope_type` varchar(32) NOT NULL COMMENT '模型配置归属层级，PLATFORM 或 SYSTEM',
  `scope_owner_id` bigint NOT NULL COMMENT '平台模型为平台主键，系统模型为系统主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统自建模型所属系统主键，平台模型为空',
  `code` varchar(100) NOT NULL COMMENT '平台内模型编码',
  `name` varchar(200) NOT NULL COMMENT '模型显示名称',
  `provider` varchar(64) NOT NULL COMMENT '模型供应商',
  `model_name` varchar(200) NOT NULL COMMENT '供应商模型名称',
  `endpoint_url` varchar(1000) DEFAULT NULL COMMENT '模型接口地址',
  `credential_ref` varchar(255) NOT NULL COMMENT '受控密钥引用码，不保存密钥明文；V18 建表后绑定密钥引用',
  `credential_ref_id` bigint NOT NULL COMMENT '平台受控密钥引用主键',
  `credential_scope_type` varchar(32) NOT NULL DEFAULT 'PLATFORM' COMMENT '模型密钥固定为平台范围',
  `credential_version` varchar(100) NOT NULL COMMENT '当前密钥引用版本',
  `capabilities_json` json NOT NULL COMMENT '模型可用能力配置',
  `limit_policy_json` json NOT NULL COMMENT '额度和并发限制配置',
  `outbound_policy_json` json NOT NULL COMMENT '外发及数据出境策略',
  `masking_policy_json` json NOT NULL COMMENT '发送模型前的脱敏策略',
  `status` varchar(32) NOT NULL COMMENT '模型启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '模型配置乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_scope_code` (`scope_type`,`scope_owner_id`,`code`),
  KEY `idx_ai_model_scope_id` (`platform_id`,`id`),
  KEY `idx_ai_model_system` (`system_id`,`status`),
  CONSTRAINT `chk_ai_model_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`scope_owner_id` = `platform_id`) and (`system_id` is null) and (`credential_scope_type` = _utf8mb4'PLATFORM')) or ((`scope_type` = _utf8mb4'SYSTEM') and (`scope_owner_id` = `system_id`) and (`system_id` is not null) and (`credential_scope_type` = _utf8mb4'SYSTEM'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台模型与受控密钥引用配置';

CREATE TABLE `un_ai_system_model_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型授权主键',
  `platform_id` bigint NOT NULL COMMENT '授权所属平台标识',
  `model_id` bigint NOT NULL COMMENT '平台模型标识',
  `system_id` bigint NOT NULL COMMENT '获授权系统标识',
  `usage_limit_json` json NOT NULL COMMENT '系统可用额度与并发限制',
  `status` varchar(32) NOT NULL COMMENT '授权状态',
  `granted_by_account_id` bigint NOT NULL COMMENT '授权平台账号标识',
  `grant_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  `revoke_time` datetime(3) DEFAULT NULL COMMENT '撤销时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '授权草稿乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_system_model_grant` (`model_id`,`system_id`),
  KEY `idx_ai_model_grant_platform_id` (`id`,`platform_id`),
  KEY `idx_ai_model_grant_scope_id` (`system_id`,`id`,`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统模型授权草稿与状态';

CREATE TABLE `un_ai_system_model_grant_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '不可变模型授权版本主键',
  `grant_id` bigint NOT NULL COMMENT '所属系统模型授权标识',
  `platform_id` bigint NOT NULL COMMENT '授权版本所属平台标识',
  `version_number` int NOT NULL COMMENT '授权发布版本号',
  `policy_snapshot_json` json NOT NULL COMMENT '授权额度、外发、脱敏及模型范围快照',
  `published_by_account_id` bigint NOT NULL COMMENT '发布授权的平台账号标识',
  `published_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权版本发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_grant_version` (`grant_id`,`version_number`),
  KEY `idx_ai_model_grant_version_id` (`grant_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变系统模型授权版本';

CREATE TABLE `un_ai_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Agent 主键',
  `context_type` varchar(32) NOT NULL COMMENT 'Agent 范围：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统 Agent 所属系统，平台 Agent 为空',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '系统 Agent 所属租户，平台 Agent 为空',
  `model_id` bigint NOT NULL COMMENT '使用的平台模型标识',
  `model_grant_id` bigint DEFAULT NULL COMMENT '系统 Agent 使用的系统模型授权，平台 Agent 为空',
  `code` varchar(100) NOT NULL COMMENT '当前范围内 Agent 编码',
  `name` varchar(200) NOT NULL COMMENT 'Agent 显示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT 'Agent 业务说明',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '可编辑草稿修订号',
  `system_prompt_text` text NOT NULL COMMENT '草稿系统提示词正文',
  `context_policy_json` json NOT NULL COMMENT '草稿上下文和数据范围策略',
  `confirmation_policy_json` json NOT NULL COMMENT '草稿人工确认策略',
  `fallback_policy_json` json NOT NULL COMMENT '草稿模型失败降级策略',
  `status` varchar(32) NOT NULL COMMENT 'Agent 草稿或发布状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建平台账号标识',
  `created_by_platform_member_account_id` bigint DEFAULT NULL COMMENT '平台 Agent 创建人平台成员账号，系统 Agent 为空',
  `created_by_member_id` bigint DEFAULT NULL COMMENT '系统 Agent 创建系统成员标识',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '草稿乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_code` (`context_type`,`platform_id`,(coalesce(`system_id`,0)),(coalesce(`owner_tenant_id`,0)),`code`),
  KEY `idx_ai_agent_scope_id` (`id`,`context_type`,`platform_id`),
  KEY `idx_ai_agent_system_id` (`id`,`system_id`,`owner_tenant_id`),
  KEY `idx_ai_agent_model_only_id` (`id`,`model_id`),
  KEY `idx_ai_agent_model_id` (`id`,`model_id`,`model_grant_id`),
  CONSTRAINT `chk_ai_agent_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null) and (`model_grant_id` is null) and (`created_by_member_id` is null) and (`created_by_platform_member_account_id` is not null) and (`created_by_platform_member_account_id` = `created_by_account_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is not null) and (`model_grant_id` is not null) and (`created_by_member_id` is not null) and (`created_by_platform_member_account_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统 Agent 可编辑草稿';

CREATE TABLE `un_ai_agent_tool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Agent 工具主键',
  `agent_id` bigint NOT NULL COMMENT '所属 Agent 标识',
  `context_type` varchar(32) NOT NULL COMMENT '工具可用平台或系统范围',
  `platform_id` bigint NOT NULL COMMENT '工具所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统工具所属系统标识',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '系统工具所属租户标识',
  `tool_type` varchar(64) NOT NULL COMMENT '查询、建议或写入工具类别',
  `resource_type` varchar(64) NOT NULL COMMENT '可访问资源类别',
  `resource_id` varchar(100) NOT NULL COMMENT '可访问资源业务标识',
  `action_code` varchar(64) NOT NULL COMMENT '授权动作编码',
  `field_scope_json` json NOT NULL COMMENT '允许字段范围',
  `data_scope_json` json NOT NULL COMMENT '允许数据范围表达式',
  `outbound_policy_json` json NOT NULL COMMENT '工具外发范围和开关',
  `masking_policy_json` json NOT NULL COMMENT '工具输入输出脱敏规则',
  `policy_version` int NOT NULL DEFAULT '1' COMMENT '工具策略修订号',
  `requires_confirmation` tinyint NOT NULL DEFAULT '1' COMMENT '执行前是否必须人工确认',
  `status` varchar(32) NOT NULL COMMENT '工具启停状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_tool` (`agent_id`,`tool_type`,`resource_type`,`resource_id`,`action_code`),
  KEY `idx_ai_agent_tool_agent_id` (`agent_id`,`id`),
  CONSTRAINT `chk_ai_agent_tool_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null) and (`resource_type` in (_utf8mb4'PLATFORM_TASK',_utf8mb4'PLATFORM_MESSAGE',_utf8mb4'PLATFORM_LOG',_utf8mb4'PLATFORM_FLOW',_utf8mb4'PLATFORM_SYSTEM',_utf8mb4'PLATFORM_HEALTH',_utf8mb4'MODEL_QUOTA'))) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 工具及显式访问范围草稿';

CREATE TABLE `un_ai_agent_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Agent 不可变发布版本主键',
  `agent_id` bigint NOT NULL COMMENT '所属 Agent 标识',
  `context_type` varchar(32) NOT NULL COMMENT '发布版本平台或系统范围',
  `platform_id` bigint NOT NULL COMMENT '发布版本所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统发布版本所属系统标识',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '系统发布版本所属租户标识',
  `model_id` bigint NOT NULL COMMENT '发布时固定模型标识',
  `model_grant_id` bigint DEFAULT NULL COMMENT '系统发布时固定模型授权标识',
  `model_grant_version_id` bigint DEFAULT NULL COMMENT '系统发布时固定不可变授权版本标识',
  `model_config_version` int NOT NULL COMMENT '发布时模型配置版本',
  `credential_ref` varchar(255) NOT NULL COMMENT '发布时使用的受控密钥引用码',
  `credential_version` varchar(100) NOT NULL COMMENT '发布时使用的密钥版本',
  `policy_version` int NOT NULL COMMENT 'Agent 权限、外发和脱敏策略版本',
  `version_number` int NOT NULL COMMENT 'Agent 发布版本号',
  `draft_revision` int NOT NULL COMMENT '被发布的草稿修订号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '不可变配置快照摘要',
  `snapshot_json` json NOT NULL COMMENT '提示词、工具及业务配置完整快照',
  `policy_snapshot_json` json NOT NULL COMMENT '模块、字段、动作、数据范围及外发脱敏策略快照',
  `published_by_account_id` bigint NOT NULL COMMENT '发布平台账号标识',
  `published_by_platform_member_account_id` bigint DEFAULT NULL COMMENT '平台发布者成员账号，系统发布为空',
  `published_by_member_id` bigint DEFAULT NULL COMMENT '系统发布成员标识',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_version` (`agent_id`,`version_number`),
  UNIQUE KEY `uk_ai_agent_revision` (`agent_id`,`draft_revision`),
  KEY `idx_ai_agent_version_agent_id` (`agent_id`,`id`),
  KEY `idx_ai_agent_version_model_snapshot` (`id`,`model_id`,`model_config_version`,`credential_ref`,`credential_version`,`policy_version`),
  KEY `idx_ai_agent_version_grant_snapshot` (`id`,`model_grant_id`,`model_grant_version_id`),
  CONSTRAINT `chk_ai_agent_version_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null) and (`model_grant_id` is null) and (`model_grant_version_id` is null) and (`published_by_member_id` is null) and (`published_by_platform_member_account_id` is not null) and (`published_by_platform_member_account_id` = `published_by_account_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is not null) and (`model_grant_id` is not null) and (`model_grant_version_id` is not null) and (`published_by_member_id` is not null) and (`published_by_platform_member_account_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变 Agent 发布配置、模型及策略快照';

CREATE TABLE `un_ai_agent_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Agent 发布指针主键',
  `agent_id` bigint NOT NULL COMMENT '所属 Agent 标识',
  `context_type` varchar(32) NOT NULL COMMENT '发布指针平台或系统范围',
  `platform_id` bigint NOT NULL COMMENT '所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统发布指针所属系统标识',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '系统发布指针所属租户标识',
  `current_version_id` bigint NOT NULL COMMENT '当前生效 Agent 版本标识',
  `updated_by_account_id` bigint NOT NULL COMMENT '更新发布指针的平台账号标识',
  `updated_by_platform_member_account_id` bigint DEFAULT NULL COMMENT '平台发布指针更新人成员账号，系统更新为空',
  `updated_by_member_id` bigint DEFAULT NULL COMMENT '系统发布指针更新成员标识',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '发布指针乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_publication` (`agent_id`),
  CONSTRAINT `chk_ai_agent_publication_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null) and (`updated_by_member_id` is null) and (`updated_by_platform_member_account_id` is not null) and (`updated_by_platform_member_account_id` = `updated_by_account_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is not null) and (`updated_by_member_id` is not null) and (`updated_by_platform_member_account_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 当前生效发布版本指针';

CREATE TABLE `un_ai_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'AI 对话主键',
  `context_type` varchar(32) NOT NULL COMMENT '对话平台或系统范围',
  `platform_id` bigint NOT NULL COMMENT '对话所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统对话所属系统标识',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统对话所属租户标识',
  `agent_id` bigint NOT NULL COMMENT '对话使用 Agent 标识',
  `agent_version_id` bigint NOT NULL COMMENT '对话固定已发布 Agent 版本标识',
  `account_id` bigint NOT NULL COMMENT '对话发起平台账号标识',
  `platform_member_account_id` bigint DEFAULT NULL COMMENT '平台对话发起人成员账号，系统对话为空',
  `system_member_id` bigint DEFAULT NULL COMMENT '系统对话发起系统成员标识',
  `entry_context_json` json NOT NULL COMMENT '入口场景与来源信息快照',
  `title` varchar(500) NOT NULL COMMENT '对话标题',
  `status` varchar(32) NOT NULL COMMENT '对话状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversation_agent_version` (`id`,`agent_id`,`agent_version_id`),
  KEY `idx_ai_conversation_platform_scope` (`id`,`context_type`,`platform_id`),
  KEY `idx_ai_conversation_system_scope` (`id`,`system_id`,`tenant_id`),
  KEY `idx_ai_conversation_user` (`account_id`,`status`,`update_time`),
  KEY `idx_ai_conversation_context` (`system_id`,`tenant_id`,`update_time`),
  CONSTRAINT `chk_ai_conversation_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null) and (`system_member_id` is null) and (`platform_member_account_id` is not null) and (`platform_member_account_id` = `account_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null) and (`system_member_id` is not null) and (`platform_member_account_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定 Agent 版本与成员上下文的 AI 对话';

CREATE TABLE `un_ai_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '对话消息主键',
  `conversation_id` bigint NOT NULL COMMENT '所属 AI 对话标识',
  `role_type` varchar(32) NOT NULL COMMENT '消息角色：用户、助手或工具',
  `content_text` longtext NOT NULL COMMENT '消息正文',
  `structured_content_json` json DEFAULT NULL COMMENT '结构化识别结果或呈现内容',
  `model_usage_json` json DEFAULT NULL COMMENT '模型调用用量与费用摘要',
  `error_code` varchar(100) DEFAULT NULL COMMENT '失败错误码',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation_id` (`conversation_id`,`id`),
  KEY `idx_ai_message_conversation` (`conversation_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 对话消息与结构化识别内容';

CREATE TABLE `un_ai_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'AI 执行主键',
  `conversation_id` bigint NOT NULL COMMENT '所属对话标识',
  `context_type` varchar(32) NOT NULL COMMENT '执行平台或系统范围',
  `platform_id` bigint NOT NULL COMMENT '执行所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统执行所属系统标识',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统执行所属租户标识',
  `agent_id` bigint NOT NULL COMMENT '实际执行 Agent 标识',
  `request_message_id` bigint NOT NULL COMMENT '触发本次执行的用户消息标识',
  `agent_version_id` bigint NOT NULL COMMENT '执行固定不可变 Agent 版本标识',
  `model_id` bigint NOT NULL COMMENT '执行固定模型标识',
  `model_config_version` int NOT NULL COMMENT '执行固定模型配置版本',
  `model_grant_id` bigint DEFAULT NULL COMMENT '系统执行固定模型授权标识',
  `model_grant_version_id` bigint DEFAULT NULL COMMENT '系统执行固定模型授权发布版本标识',
  `credential_ref` varchar(255) NOT NULL COMMENT '执行使用的受控密钥引用码',
  `credential_version` varchar(100) NOT NULL COMMENT '执行使用的密钥版本',
  `policy_version` int NOT NULL COMMENT '执行使用的 Agent 策略版本',
  `authorization_snapshot_json` json NOT NULL COMMENT '当前角色、字段、数据范围与应用授权快照',
  `policy_snapshot_json` json NOT NULL COMMENT '执行时外发、脱敏与确认策略快照',
  `status` varchar(32) NOT NULL COMMENT '执行状态',
  `fallback_used` tinyint NOT NULL DEFAULT '0' COMMENT '是否进入失败降级路径',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '结束时间',
  `error_code` varchar(100) DEFAULT NULL COMMENT '执行失败错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '执行失败摘要，不含密钥明文',
  `version` int NOT NULL DEFAULT '0' COMMENT '执行状态乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ai_execution_scope_id` (`id`,`context_type`,`platform_id`),
  KEY `idx_ai_execution_system_id` (`id`,`system_id`,`tenant_id`),
  KEY `idx_ai_execution_agent_id` (`id`,`agent_id`),
  KEY `idx_ai_execution_conversation` (`conversation_id`,`start_time`),
  CONSTRAINT `chk_ai_execution_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null) and (`model_grant_id` is null) and (`model_grant_version_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null) and (`model_grant_id` is not null) and (`model_grant_version_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定模型授权与权限策略快照的 AI 执行';

CREATE TABLE `un_ai_execution_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行步骤主键',
  `execution_id` bigint NOT NULL COMMENT '所属执行标识',
  `agent_id` bigint NOT NULL COMMENT '执行 Agent 标识，用于限制工具归属',
  `step_number` int NOT NULL COMMENT '本次执行内步骤序号',
  `step_type` varchar(32) NOT NULL COMMENT '步骤类别',
  `tool_id` bigint DEFAULT NULL COMMENT '使用的 Agent 工具标识',
  `input_json` json DEFAULT NULL COMMENT '步骤输入摘要，不含密钥明文',
  `output_json` json DEFAULT NULL COMMENT '步骤输出及脱敏摘要',
  `status` varchar(32) NOT NULL COMMENT '步骤状态',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '结束时间',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '步骤失败摘要',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_execution_step` (`execution_id`,`step_number`),
  KEY `idx_ai_execution_step_exec_id` (`execution_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 执行步骤、工具调用与结果摘要';

CREATE TABLE `un_ai_pending_write` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '待确认 AI 写入主键',
  `execution_id` bigint NOT NULL COMMENT '提出建议的 AI 执行标识',
  `step_id` bigint NOT NULL COMMENT '提出建议的执行步骤标识',
  `context_type` varchar(32) NOT NULL COMMENT '待确认写入平台或系统范围',
  `platform_id` bigint NOT NULL COMMENT '待确认写入所属平台标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统写入的目标系统标识',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统写入的目标租户标识',
  `target_type` varchar(64) NOT NULL COMMENT '待写入目标类别；平台只允许任务、消息、日志',
  `target_id` varchar(100) DEFAULT NULL COMMENT '已有目标业务标识；新增时为空',
  `target_module_id` bigint DEFAULT NULL COMMENT '系统业务记录或配置目标模块标识',
  `target_expected_version` int DEFAULT NULL COMMENT '确认时用于防止覆盖后续修改的目标版本',
  `proposed_payload_json` json NOT NULL COMMENT '供用户确认的结构化提议，正式数据尚未写入',
  `preview_json` json NOT NULL COMMENT '字段映射、置信度和影响范围预览',
  `status` varchar(32) NOT NULL COMMENT '等待确认、拒绝或执行结果状态',
  `confirmed_by_account_id` bigint DEFAULT NULL COMMENT '人工确认平台账号标识',
  `confirmed_by_platform_member_account_id` bigint DEFAULT NULL COMMENT '平台确认者成员账号，系统确认时为空',
  `confirmed_by_member_id` bigint DEFAULT NULL COMMENT '系统写入确认成员标识',
  `confirm_time` datetime(3) DEFAULT NULL COMMENT '人工确认时间',
  `result_json` json DEFAULT NULL COMMENT '确认后业务写入结果及审计关联',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '拒绝或失败原因摘要',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '确认状态乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ai_pending_write_status` (`status`,`create_time`),
  CONSTRAINT `chk_ai_pending_write_confirmation` CHECK ((((`confirmed_by_account_id` is null) and (`confirmed_by_platform_member_account_id` is null) and (`confirmed_by_member_id` is null) and (`confirm_time` is null)) or ((`confirmed_by_account_id` is not null) and (`confirm_time` is not null) and ((`context_type` = _utf8mb4'PLATFORM') or (`confirmed_by_member_id` is not null))))),
  CONSTRAINT `chk_ai_pending_write_module` CHECK (((`target_type` <> _utf8mb4'BIZ_RECORD') or (`target_module_id` is not null))),
  CONSTRAINT `chk_ai_pending_write_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null) and (`target_module_id` is null) and (`confirmed_by_member_id` is null) and (((`confirmed_by_account_id` is null) and (`confirmed_by_platform_member_account_id` is null)) or ((`confirmed_by_account_id` is not null) and (`confirmed_by_platform_member_account_id` is not null) and (`confirmed_by_platform_member_account_id` = `confirmed_by_account_id`))) and (`target_type` in (_utf8mb4'PLATFORM_TASK',_utf8mb4'PLATFORM_MESSAGE',_utf8mb4'PLATFORM_LOG'))) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null) and (`confirmed_by_platform_member_account_id` is null) and (`target_type` in (_utf8mb4'BIZ_RECORD',_utf8mb4'WORK_TASK',_utf8mb4'WORK_LOG',_utf8mb4'FLOW_INSTANCE',_utf8mb4'CONFIG_DRAFT',_utf8mb4'SYSTEM_MESSAGE')))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人工确认前不得写入正式业务表的 AI 提议';

-- source: init/V14__file.sql
-- file 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_file_storage_backend` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '存储后端主键',
  `context_type` varchar(32) NOT NULL COMMENT '后端作用域：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '后端所属平台',
  `system_id` bigint DEFAULT NULL COMMENT '系统后端所属系统，平台后端为空',
  `scope_owner_id` bigint NOT NULL COMMENT '后端作用域主体，平台取平台标识，系统取系统标识',
  `code` varchar(100) NOT NULL COMMENT '同一作用域内的后端编码',
  `name` varchar(200) NOT NULL COMMENT '存储后端显示名称',
  `backend_type` varchar(32) NOT NULL COMMENT '存储类型：LOCAL 或 S3',
  `endpoint` varchar(1000) DEFAULT NULL COMMENT 'S3 兼容服务地址',
  `bucket_name` varchar(255) NOT NULL COMMENT '本地目录或对象存储桶名称',
  `credential_ref_id` bigint DEFAULT NULL COMMENT 'S3 凭证的受控密钥引用主键，不保存凭证明文或密文',
  `config_json` json NOT NULL COMMENT '非敏感存储参数，禁止写入访问密钥',
  `status` varchar(32) NOT NULL COMMENT '后端启用状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_backend_scope_code` (`context_type`,`scope_owner_id`,`code`),
  KEY `idx_file_backend_platform_id` (`id`,`context_type`,`platform_id`),
  KEY `idx_file_backend_system_id` (`id`,`context_type`,`system_id`),
  KEY `idx_file_backend_platform` (`platform_id`,`status`),
  CONSTRAINT `chk_file_backend_credential` CHECK ((((`backend_type` = _utf8mb4'LOCAL') and (`credential_ref_id` is null)) or ((`backend_type` = _utf8mb4'S3') and (`credential_ref_id` is not null)))),
  CONSTRAINT `chk_file_backend_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统的文件存储后端配置';

CREATE TABLE `un_file_upload_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '上传会话主键',
  `context_type` varchar(32) NOT NULL COMMENT '上传作用域：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '上传所属平台',
  `system_id` bigint DEFAULT NULL COMMENT '系统上传所属系统，平台上传为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统上传所属租户，平台上传为空',
  `storage_backend_id` bigint NOT NULL COMMENT '实际写入的存储后端主键',
  `uploader_account_id` bigint NOT NULL COMMENT '上传人的平台账号主键',
  `original_name` varchar(500) NOT NULL COMMENT '上传时的原始文件名',
  `content_type` varchar(255) NOT NULL COMMENT '客户端声明的媒体类型',
  `expected_size` bigint NOT NULL COMMENT '上传完成后预期的文件字节数',
  `expected_sha256` varchar(64) DEFAULT NULL COMMENT '可选的完整文件 SHA-256 校验值',
  `object_key` varchar(1000) NOT NULL COMMENT '存储后端内的目标对象键',
  `status` varchar(32) NOT NULL COMMENT '上传会话状态',
  `upload_token_hash` varchar(64) NOT NULL COMMENT '上传令牌哈希，不保存原始令牌',
  `expire_time` datetime(3) NOT NULL COMMENT '上传会话过期时间',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '上传完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_upload_token` (`upload_token_hash`),
  KEY `idx_file_upload_platform_id` (`id`,`context_type`,`platform_id`),
  KEY `idx_file_upload_tenant_id` (`id`,`context_type`,`system_id`,`tenant_id`),
  KEY `idx_file_upload_backend_id` (`id`,`storage_backend_id`),
  KEY `idx_file_upload_expiry` (`status`,`expire_time`),
  CONSTRAINT `chk_file_upload_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null)))),
  CONSTRAINT `chk_file_upload_size` CHECK ((`expected_size` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大文件上传会话及目标范围';

CREATE TABLE `un_file_upload_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '上传分片主键',
  `upload_session_id` bigint NOT NULL COMMENT '所属上传会话主键',
  `chunk_number` int NOT NULL COMMENT '从零开始的分片序号',
  `byte_offset` bigint NOT NULL COMMENT '分片在完整文件中的起始字节位置',
  `size_bytes` bigint NOT NULL COMMENT '分片实际字节数',
  `sha256` varchar(64) NOT NULL COMMENT '分片内容 SHA-256 校验值',
  `status` varchar(32) NOT NULL COMMENT '分片接收和校验状态',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '分片记录创建时间',
  `completed_time` datetime(3) DEFAULT NULL COMMENT '分片写入并校验完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_chunk_number` (`upload_session_id`,`chunk_number`),
  UNIQUE KEY `uk_file_chunk_offset` (`upload_session_id`,`byte_offset`),
  CONSTRAINT `chk_file_chunk_position` CHECK (((`chunk_number` >= 0) and (`byte_offset` >= 0) and (`size_bytes` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上传会话的分片校验与完成明细';

CREATE TABLE `un_file_object` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件对象主键',
  `context_type` varchar(32) NOT NULL COMMENT '文件作用域：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '文件所属平台',
  `system_id` bigint DEFAULT NULL COMMENT '系统文件所属系统，平台文件为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统文件所属租户，平台文件为空',
  `storage_backend_id` bigint NOT NULL COMMENT '文件字节所在存储后端主键',
  `upload_session_id` bigint DEFAULT NULL COMMENT '原始上传会话主键；生成的预览产物可为空',
  `object_key` varchar(1000) NOT NULL COMMENT '后端内存储对象键',
  `object_key_hash` varchar(64) NOT NULL COMMENT '对象键 SHA-256，用于稳定唯一约束',
  `original_name` varchar(500) NOT NULL COMMENT '展示和下载时使用的文件名',
  `content_type` varchar(255) NOT NULL COMMENT '文件实际媒体类型',
  `size_bytes` bigint NOT NULL COMMENT '文件完整字节数',
  `sha256` varchar(64) NOT NULL COMMENT '完整文件 SHA-256 校验值',
  `scan_status` varchar(32) NOT NULL COMMENT '安全扫描结果状态',
  `preview_status` varchar(32) NOT NULL COMMENT '预览处理汇总状态，产物详见 un_file_preview',
  `status` varchar(32) NOT NULL COMMENT '文件可用或删除状态',
  `uploaded_by_account_id` bigint NOT NULL COMMENT '原文件上传人或预览产物生成主体账号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `delete_time` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`storage_backend_id`,`object_key_hash`),
  UNIQUE KEY `uk_file_object_upload` (`upload_session_id`),
  KEY `idx_file_object_platform_id` (`id`,`context_type`,`platform_id`),
  KEY `idx_file_object_tenant_id` (`id`,`context_type`,`system_id`,`tenant_id`),
  KEY `idx_file_object_hash` (`sha256`,`status`),
  KEY `idx_file_object_context` (`system_id`,`tenant_id`,`create_time`),
  CONSTRAINT `chk_file_object_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null)))),
  CONSTRAINT `chk_file_object_size` CHECK ((`size_bytes` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可被业务引用的文件对象元数据';

CREATE TABLE `un_file_preview` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件预览任务主键',
  `source_file_id` bigint NOT NULL COMMENT '需要预览的原文件对象主键',
  `preview_file_id` bigint DEFAULT NULL COMMENT '成功生成的预览文件对象主键',
  `context_type` varchar(32) NOT NULL COMMENT '预览所属作用域：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '预览所属平台',
  `system_id` bigint DEFAULT NULL COMMENT '预览所属系统，平台预览为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '预览所属租户，平台预览为空',
  `preview_format` varchar(32) NOT NULL COMMENT '预览产物格式或处理方式',
  `status` varchar(32) NOT NULL COMMENT '预览处理状态',
  `failure_code` varchar(100) DEFAULT NULL COMMENT '预览失败原因编码',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '预览任务创建时间',
  `completed_time` datetime(3) DEFAULT NULL COMMENT '预览处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_preview_format` (`source_file_id`,`preview_format`),
  UNIQUE KEY `uk_file_preview_result` (`preview_file_id`),
  CONSTRAINT `chk_file_preview_not_self` CHECK (((`preview_file_id` is null) or (`source_file_id` <> `preview_file_id`))),
  CONSTRAINT `chk_file_preview_result` CHECK ((((`status` = _utf8mb4'AVAILABLE') and (`preview_file_id` is not null)) or ((`status` <> _utf8mb4'AVAILABLE') and (`preview_file_id` is null)))),
  CONSTRAINT `chk_file_preview_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件预览状态及同范围衍生文件';

CREATE TABLE `un_file_reference` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件引用主键',
  `file_id` bigint NOT NULL COMMENT '被引用的文件对象主键',
  `context_type` varchar(32) NOT NULL COMMENT '引用作用域：PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '引用所属平台',
  `system_id` bigint DEFAULT NULL COMMENT '引用所属系统，平台引用为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '引用所属租户，平台引用为空',
  `owner_type` varchar(64) NOT NULL COMMENT '引用目标类型，由业务服务核验目标身份和权限',
  `owner_id` varchar(100) NOT NULL COMMENT '引用目标在该类型下的稳定业务标识',
  `field_code` varchar(100) DEFAULT NULL COMMENT '业务记录中所属附件字段编码',
  `reference_type` varchar(32) NOT NULL COMMENT '附件或业务关联类别',
  `created_by_account_id` bigint NOT NULL COMMENT '建立引用的平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_reference_owner` (`file_id`,`owner_type`,`owner_id`,(coalesce(`field_code`,_utf8mb4''))),
  KEY `idx_file_reference_lookup` (`context_type`,`platform_id`,`system_id`,`tenant_id`,`owner_type`,`owner_id`),
  CONSTRAINT `chk_file_reference_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件与业务对象的同范围引用关系';

CREATE TABLE `un_file_security_scan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '安全扫描主键',
  `file_id` bigint NOT NULL COMMENT '被扫描文件对象主键',
  `scanner` varchar(100) NOT NULL COMMENT '扫描器标识',
  `scan_version` varchar(100) DEFAULT NULL COMMENT '扫描规则或引擎版本',
  `status` varchar(32) NOT NULL COMMENT '扫描任务状态和结果',
  `result_code` varchar(100) DEFAULT NULL COMMENT '扫描结果编码',
  `result_detail_json` json DEFAULT NULL COMMENT '不含文件正文的扫描详情',
  `start_time` datetime(3) NOT NULL COMMENT '扫描开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '扫描结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_scan_object` (`file_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件安全扫描结果与历史';

-- source: init/V15__dataexchange.sql
-- dataexchange 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_exchange_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模块导入导出映射草稿主键',
  `system_id` bigint NOT NULL COMMENT '所属系统主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '映射配置所属租户主键',
  `module_id` bigint NOT NULL COMMENT '映射所属模块主键',
  `code` varchar(100) NOT NULL COMMENT '模块内映射编码',
  `name` varchar(200) NOT NULL COMMENT '映射名称',
  `mapping_type` varchar(32) NOT NULL COMMENT '映射类型，IMPORT 或 EXPORT',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号，发布时据此检查并发修改',
  `column_mapping_json` json NOT NULL COMMENT '草稿中的外部列与模块字段映射',
  `validation_policy_json` json NOT NULL COMMENT '草稿中的导入校验及预检策略',
  `conflict_policy_json` json NOT NULL COMMENT '草稿中的唯一字段匹配和冲突策略',
  `status` varchar(32) NOT NULL COMMENT '草稿、已发布或停用等配置状态',
  `created_by_member_id` bigint NOT NULL COMMENT '创建映射的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '草稿乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_mapping_code` (`owner_tenant_id`,`module_id`,`code`),
  KEY `idx_exchange_mapping_module_id` (`module_id`,`id`),
  KEY `idx_exchange_mapping_owner_id` (`owner_tenant_id`,`id`),
  CONSTRAINT `chk_exchange_mapping_revision` CHECK ((`draft_revision` > 0)),
  CONSTRAINT `chk_exchange_mapping_type` CHECK ((`mapping_type` in (_utf8mb4'IMPORT',_utf8mb4'EXPORT')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块数据导入导出字段映射草稿';

CREATE TABLE `un_exchange_mapping_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '映射不可变发布版本主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '映射所属租户主键，用于校验发布人',
  `module_id` bigint NOT NULL COMMENT '映射所属模块主键',
  `mapping_id` bigint NOT NULL COMMENT '来源映射草稿主键',
  `module_config_version_id` bigint NOT NULL COMMENT '发布时引用的不可变模块配置版本',
  `version_number` int NOT NULL COMMENT '同一映射内递增的发布版本号',
  `draft_revision` int NOT NULL COMMENT '发布所依据的草稿修订号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '映射快照摘要',
  `snapshot_json` json NOT NULL COMMENT '字段映射、校验和冲突策略的不可变快照',
  `published_by_member_id` bigint NOT NULL COMMENT '发布映射的系统成员主键',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_mapping_version_number` (`mapping_id`,`version_number`),
  UNIQUE KEY `uk_exchange_mapping_version_revision` (`mapping_id`,`draft_revision`),
  KEY `idx_exchange_mapping_version_mapping_id` (`mapping_id`,`id`),
  KEY `idx_exchange_mapping_version_module_id` (`module_id`,`id`),
  KEY `idx_exchange_mapping_version_module_config` (`mapping_id`,`id`,`module_config_version_id`),
  CONSTRAINT `chk_exchange_mapping_version_number` CHECK (((`version_number` > 0) and (`draft_revision` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变的模块导入导出映射发布版本';

CREATE TABLE `un_exchange_mapping_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '映射当前发布指针主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '映射所属租户主键',
  `mapping_id` bigint NOT NULL COMMENT '映射草稿主键',
  `current_version_id` bigint NOT NULL COMMENT '当前生效的不可变映射版本',
  `updated_by_member_id` bigint NOT NULL COMMENT '最近发布或回滚的系统成员主键',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近切换发布时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '发布指针乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_mapping_publication` (`mapping_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块导入导出映射当前生效版本指针';

CREATE TABLE `un_exchange_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导入批次主键',
  `system_id` bigint NOT NULL COMMENT '执行系统主键',
  `tenant_id` bigint NOT NULL COMMENT '执行租户主键',
  `module_id` bigint NOT NULL COMMENT '导入目标模块主键',
  `module_config_version_id` bigint NOT NULL COMMENT '导入预检时固定的模块配置版本',
  `mapping_id` bigint NOT NULL COMMENT '导入字段映射主键',
  `mapping_version_id` bigint NOT NULL COMMENT '导入预检时固定的映射发布版本',
  `permission_version_id` bigint NOT NULL COMMENT '导入预检时固定的权限版本',
  `source_file_id` bigint NOT NULL COMMENT '原始导入文件主键',
  `error_file_id` bigint DEFAULT NULL COMMENT '预检或执行失败行结果文件主键',
  `job_id` bigint DEFAULT NULL COMMENT '异步作业业务申请主键，运行状态存 Redis',
  `mode` varchar(32) NOT NULL COMMENT '新增、更新或混合导入模式',
  `status` varchar(32) NOT NULL COMMENT '预检、执行和回滚业务状态',
  `total_rows` bigint NOT NULL DEFAULT '0' COMMENT '原始数据行数',
  `valid_rows` bigint NOT NULL DEFAULT '0' COMMENT '预检通过行数',
  `success_rows` bigint NOT NULL DEFAULT '0' COMMENT '成功写入行数',
  `failed_rows` bigint NOT NULL DEFAULT '0' COMMENT '失败行数',
  `authorization_snapshot_json` json NOT NULL COMMENT '预检时固定的权限、字段和数据范围快照',
  `summary_json` json DEFAULT NULL COMMENT '预检、执行和回滚的结果摘要',
  `created_by_member_id` bigint NOT NULL COMMENT '发起导入的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `start_time` datetime(3) DEFAULT NULL COMMENT '开始执行时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '完成或回滚时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '导入批次乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_import_batch_scope_id` (`id`,`tenant_id`,`module_id`),
  KEY `idx_exchange_import_context` (`tenant_id`,`module_id`,`create_time`),
  KEY `idx_exchange_import_status` (`status`,`create_time`),
  CONSTRAINT `chk_exchange_import_counts` CHECK (((`total_rows` >= 0) and (`valid_rows` >= 0) and (`success_rows` >= 0) and (`failed_rows` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定模块、映射和权限版本的导入预检与执行批次';

CREATE TABLE `un_exchange_import_row` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导入原始行处理结果主键',
  `batch_id` bigint NOT NULL COMMENT '所属导入批次主键',
  `tenant_id` bigint NOT NULL COMMENT '批次执行租户主键',
  `module_id` bigint NOT NULL COMMENT '批次目标模块主键',
  `row_number` bigint NOT NULL COMMENT '原文件中的行号',
  `raw_json` json NOT NULL COMMENT '原文件行内容快照',
  `normalized_json` json DEFAULT NULL COMMENT '映射及预检后的字段值快照',
  `status` varchar(32) NOT NULL COMMENT '预检、执行或回滚的行级状态',
  `operation_type` varchar(32) DEFAULT NULL COMMENT '预检确定的 CREATE、UPDATE 或 SKIP 操作',
  `target_record_id` bigint DEFAULT NULL COMMENT '命中或写入的业务记录主键',
  `expected_record_version` int DEFAULT NULL COMMENT 'UPDATE 执行前预期的目标记录乐观锁版本',
  `before_snapshot_json` json DEFAULT NULL COMMENT 'UPDATE 前完整业务数据快照，仅用于回滚本批修改',
  `applied_record_version` int DEFAULT NULL COMMENT '本批写入后得到的业务记录乐观锁版本',
  `rollback_record_version` int DEFAULT NULL COMMENT '本批回滚成功后业务记录的乐观锁版本',
  `rollback_status` varchar(32) DEFAULT NULL COMMENT '本批回滚结果，成功或后续修改冲突',
  `rollback_time` datetime(3) DEFAULT NULL COMMENT '本批行级回滚完成或冲突确认时间',
  `error_json` json DEFAULT NULL COMMENT '预检、写入或回滚失败原因',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '行级结果乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_import_row` (`batch_id`,`row_number`),
  KEY `idx_exchange_import_row_status` (`batch_id`,`status`,`row_number`),
  CONSTRAINT `chk_exchange_import_row_applied` CHECK (((`applied_record_version` is null) or (`applied_record_version` >= 0))),
  CONSTRAINT `chk_exchange_import_row_operation` CHECK (((`operation_type` is null) or (`operation_type` in (_utf8mb4'CREATE',_utf8mb4'UPDATE',_utf8mb4'SKIP')))),
  CONSTRAINT `chk_exchange_import_row_success` CHECK (((`status` <> _utf8mb4'SUCCEEDED') or ((`operation_type` is not null) and (`operation_type` in (_utf8mb4'CREATE',_utf8mb4'UPDATE')) and (`target_record_id` is not null) and (`applied_record_version` is not null) and ((`operation_type` = _utf8mb4'CREATE') or ((`expected_record_version` is not null) and (`before_snapshot_json` is not null)))))),
  CONSTRAINT `chk_exchange_import_row_versions` CHECK (((`expected_record_version` is null) or (`expected_record_version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导入逐行操作、前镜像及版本化回滚依据';

CREATE TABLE `un_exchange_export_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导出批次主键',
  `system_id` bigint NOT NULL COMMENT '执行系统主键',
  `tenant_id` bigint NOT NULL COMMENT '执行租户主键',
  `module_id` bigint NOT NULL COMMENT '导出来源模块主键',
  `module_config_version_id` bigint NOT NULL COMMENT '导出提交时固定的模块配置版本',
  `permission_version_id` bigint NOT NULL COMMENT '导出提交时固定的权限版本',
  `mapping_id` bigint DEFAULT NULL COMMENT '采用已发布导出映射时的映射主键',
  `mapping_version_id` bigint DEFAULT NULL COMMENT '采用已发布导出映射时固定的版本主键',
  `job_id` bigint DEFAULT NULL COMMENT '异步作业业务申请主键，运行状态存 Redis',
  `export_format` varchar(32) NOT NULL DEFAULT 'CSV' COMMENT '结果格式，如 CSV、XLSX 或 PDF',
  `filter_snapshot_json` json NOT NULL COMMENT '提交时固定的筛选条件和数据范围',
  `authorization_snapshot_json` json NOT NULL COMMENT '提交时固定的权限与字段脱敏规则',
  `selected_fields_json` json NOT NULL COMMENT '导出字段及列顺序快照',
  `status` varchar(32) NOT NULL COMMENT '导出业务状态',
  `total_rows` bigint NOT NULL DEFAULT '0' COMMENT '导出范围内总行数',
  `exported_rows` bigint NOT NULL DEFAULT '0' COMMENT '已成功写入结果的行数',
  `result_file_id` bigint DEFAULT NULL COMMENT '成功导出的结果文件主键',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '导出失败原因摘要',
  `created_by_member_id` bigint NOT NULL COMMENT '发起导出的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `start_time` datetime(3) DEFAULT NULL COMMENT '开始执行时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '执行完成时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '导出批次乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_export_context` (`tenant_id`,`module_id`,`create_time`),
  KEY `idx_exchange_export_status` (`status`,`create_time`),
  CONSTRAINT `chk_exchange_export_counts` CHECK (((`total_rows` >= 0) and (`exported_rows` >= 0))),
  CONSTRAINT `chk_exchange_export_mapping_pair` CHECK ((((`mapping_id` is null) and (`mapping_version_id` is null)) or ((`mapping_id` is not null) and (`mapping_version_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定数据范围、字段、权限和结果文件的异步导出批次';

-- source: init/V16__analytics.sql
-- analytics 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_ana_data_source` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据源主键',
  `context_type` varchar(32) NOT NULL COMMENT '运行上下文，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统数据源所属系统主键',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '租户扩展数据源所属租户主键，系统默认配置为空',
  `scope_type` varchar(32) NOT NULL COMMENT '唯一归属层级，PLATFORM、SYSTEM 或 TENANT',
  `scope_owner_id` bigint NOT NULL COMMENT '归属层级对应的平台、系统或租户主键',
  `code` varchar(100) NOT NULL COMMENT '同一归属层级内稳定的数据源编码',
  `name` varchar(200) NOT NULL COMMENT '数据源显示名称',
  `source_type` varchar(32) NOT NULL COMMENT '数据源类型：平台能力、模块记录、模块关联及内部事项',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '当前草稿修订号',
  `definition_json` json NOT NULL COMMENT '输出字段、关联、筛选、公式与连接参数定义，不含密钥',
  `permission_policy_json` json NOT NULL COMMENT '数据范围及字段权限策略定义，执行时仍需复核当前权限',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前发布的数据源版本主键，草稿修改不改变已发布版本',
  `status` varchar(32) NOT NULL COMMENT '草稿、已发布或停用状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建者账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_data_source_code` (`scope_type`,`scope_owner_id`,`code`),
  KEY `idx_ana_source_scope_id` (`id`,`scope_type`,`scope_owner_id`),
  CONSTRAINT `chk_ana_source_platform_kind` CHECK (((`scope_type` <> _utf8mb4'PLATFORM') or (`source_type` in (_utf8mb4'PLATFORM_SYSTEMS',_utf8mb4'TODO_ITEMS',_utf8mb4'MESSAGE_ITEMS',_utf8mb4'WORK_PROJECTS')))),
  CONSTRAINT `chk_ana_source_published` CHECK (((`status` <> _utf8mb4'PUBLISHED') or (`current_version_id` is not null))),
  CONSTRAINT `chk_ana_source_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`scope_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`scope_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is null) and (`scope_owner_id` = `system_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`scope_type` = _utf8mb4'TENANT') and (`system_id` is not null) and (`owner_tenant_id` is not null) and (`scope_owner_id` = `owner_tenant_id`)))),
  CONSTRAINT `chk_ana_source_type` CHECK ((`source_type` in (_utf8mb4'PLATFORM_SYSTEMS',_utf8mb4'MODULE_RECORDS',_utf8mb4'MODULE_REPORT',_utf8mb4'TODO_ITEMS',_utf8mb4'MESSAGE_ITEMS',_utf8mb4'WORK_PROJECTS')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可复用数据源草稿及当前发布指针';

CREATE TABLE `un_ana_data_source_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据源发布版本主键',
  `data_source_id` bigint NOT NULL COMMENT '所属数据源主键',
  `scope_type` varchar(32) NOT NULL COMMENT '发布时固定的归属层级',
  `scope_owner_id` bigint NOT NULL COMMENT '发布时固定的归属主体主键',
  `version_number` int NOT NULL COMMENT '递增发布版本号',
  `draft_revision` int NOT NULL COMMENT '发布时采用的草稿修订号',
  `definition_hash` varchar(64) NOT NULL COMMENT '数据源发布快照的 SHA-256 摘要',
  `snapshot_json` json NOT NULL COMMENT '不可变的数据源定义、输出字段与权限口径快照',
  `published_by_account_id` bigint NOT NULL COMMENT '发布者账号主键',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_source_version` (`data_source_id`,`version_number`),
  UNIQUE KEY `uk_ana_source_revision` (`data_source_id`,`draft_revision`),
  KEY `idx_ana_source_version_pair` (`data_source_id`,`id`),
  KEY `idx_ana_source_version_scope` (`id`,`scope_type`,`scope_owner_id`),
  CONSTRAINT `chk_ana_source_version_numbers` CHECK (((`version_number` > 0) and (`draft_revision` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变数据源发布版本';

CREATE TABLE `un_ana_dashboard` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仪表盘主键',
  `context_type` varchar(32) NOT NULL COMMENT '运行上下文，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统仪表盘所属系统主键',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '租户扩展仪表盘所属租户主键',
  `scope_type` varchar(32) NOT NULL COMMENT '唯一归属层级，PLATFORM、SYSTEM 或 TENANT',
  `scope_owner_id` bigint NOT NULL COMMENT '归属层级对应的平台、系统或租户主键',
  `dashboard_kind` varchar(32) NOT NULL COMMENT '平台首页、系统首页、模块首页或个人首页',
  `module_id` bigint DEFAULT NULL COMMENT '模块首页绑定的同系统模块主键，租户使用共享模块时运行态复核发布与共享权限',
  `owner_account_id` bigint DEFAULT NULL COMMENT '个人仪表盘所有者账号主键',
  `code` varchar(100) NOT NULL COMMENT '同一归属层级内稳定的仪表盘编码',
  `name` varchar(200) NOT NULL COMMENT '仪表盘显示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '仪表盘用途说明',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '当前可视化草稿修订号',
  `status` varchar(32) NOT NULL COMMENT '草稿、已发布或停用状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建者账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_code` (`scope_type`,`scope_owner_id`,`code`),
  KEY `idx_ana_dashboard_scope_id` (`id`,`scope_type`,`scope_owner_id`),
  KEY `idx_ana_dashboard_owner` (`scope_type`,`scope_owner_id`,`dashboard_kind`,`owner_account_id`),
  CONSTRAINT `chk_ana_dashboard_kind` CHECK ((((`dashboard_kind` = _utf8mb4'PLATFORM_HOME') and (`scope_type` = _utf8mb4'PLATFORM') and (`module_id` is null) and (`owner_account_id` is null)) or ((`dashboard_kind` = _utf8mb4'SYSTEM_HOME') and (`scope_type` in (_utf8mb4'SYSTEM',_utf8mb4'TENANT')) and (`module_id` is null) and (`owner_account_id` is null)) or ((`dashboard_kind` = _utf8mb4'MODULE_HOME') and (`scope_type` = _utf8mb4'TENANT') and (`module_id` is not null) and (`owner_account_id` is null)) or ((`dashboard_kind` = _utf8mb4'PERSONAL') and (`module_id` is null) and (`owner_account_id` is not null)))),
  CONSTRAINT `chk_ana_dashboard_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`scope_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`scope_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is null) and (`scope_owner_id` = `system_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`scope_type` = _utf8mb4'TENANT') and (`system_id` is not null) and (`owner_tenant_id` is not null) and (`scope_owner_id` = `owner_tenant_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台、系统、模块或个人仪表盘草稿';

CREATE TABLE `un_ana_dashboard_component` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仪表盘组件主键',
  `dashboard_id` bigint NOT NULL COMMENT '所属仪表盘主键',
  `scope_type` varchar(32) NOT NULL COMMENT '组件归属层级，与仪表盘及数据源版本一致',
  `scope_owner_id` bigint NOT NULL COMMENT '组件归属主体主键',
  `component_key` varchar(100) NOT NULL COMMENT '仪表盘内稳定的组件编码',
  `component_type` varchar(64) NOT NULL COMMENT '统计卡、图表、列表、待办、KPI 或快捷入口类型',
  `title` varchar(200) NOT NULL COMMENT '组件显示标题',
  `data_source_id` bigint DEFAULT NULL COMMENT '组件使用的数据源主键，无查询来源的组件可为空',
  `data_source_version_id` bigint DEFAULT NULL COMMENT '草稿明确绑定的已发布数据源版本主键',
  `source_scope_type` varchar(32) DEFAULT NULL COMMENT '被引用数据源版本的归属层级，允许同系统默认或主租户共享来源',
  `source_scope_owner_id` bigint DEFAULT NULL COMMENT '被引用数据源版本的归属主体主键',
  `layout_json` json NOT NULL COMMENT '拖拽坐标和组件尺寸草稿',
  `query_parameter_json` json NOT NULL COMMENT '统计字段、过滤条件和时间范围草稿',
  `display_config_json` json NOT NULL COMMENT '图表和展示样式草稿',
  `drill_target_json` json DEFAULT NULL COMMENT '点击后跳转的模块列表或详情配置',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '同一仪表盘内的排序序号',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_component` (`dashboard_id`,`component_key`),
  CONSTRAINT `chk_ana_component_source_pair` CHECK ((((`data_source_id` is null) and (`data_source_version_id` is null) and (`source_scope_type` is null) and (`source_scope_owner_id` is null)) or ((`data_source_id` is not null) and (`data_source_version_id` is not null) and (`source_scope_type` is not null) and (`source_scope_owner_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可视化仪表盘组件草稿与固定数据源版本';

CREATE TABLE `un_ana_dashboard_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仪表盘发布版本主键',
  `dashboard_id` bigint NOT NULL COMMENT '所属仪表盘主键',
  `scope_type` varchar(32) NOT NULL COMMENT '发布时固定的归属层级',
  `scope_owner_id` bigint NOT NULL COMMENT '发布时固定的归属主体主键',
  `version_number` int NOT NULL COMMENT '递增发布版本号',
  `draft_revision` int NOT NULL COMMENT '发布时采用的草稿修订号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '仪表盘发布快照的 SHA-256 摘要',
  `snapshot_json` json NOT NULL COMMENT '不可变组件、布局及数据源版本快照',
  `published_by_account_id` bigint NOT NULL COMMENT '发布者账号主键',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_version` (`dashboard_id`,`version_number`),
  UNIQUE KEY `uk_ana_dashboard_revision` (`dashboard_id`,`draft_revision`),
  KEY `idx_ana_dashboard_version_id` (`dashboard_id`,`id`),
  KEY `idx_ana_dashboard_version_scope` (`id`,`scope_type`,`scope_owner_id`),
  CONSTRAINT `chk_ana_dashboard_version_numbers` CHECK (((`version_number` > 0) and (`draft_revision` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变仪表盘发布版本及组件快照';

CREATE TABLE `un_ana_dashboard_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仪表盘当前发布指针主键',
  `dashboard_id` bigint NOT NULL COMMENT '所属仪表盘主键',
  `current_version_id` bigint NOT NULL COMMENT '当前对运行态生效的仪表盘版本主键',
  `updated_by_account_id` bigint NOT NULL COMMENT '最后发布或回滚操作者账号主键',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '发布指针更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_publication` (`dashboard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行态仪表盘发布和回滚指针';

CREATE TABLE `un_ana_dashboard_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仪表盘可见权限主键',
  `dashboard_id` bigint NOT NULL COMMENT '所属仪表盘主键',
  `scope_type` varchar(32) NOT NULL COMMENT '权限归属层级，与仪表盘一致',
  `scope_owner_id` bigint NOT NULL COMMENT '权限归属主体主键',
  `platform_id` bigint NOT NULL COMMENT '权限所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统权限所属系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统角色或租户成员所属租户主键',
  `permission_code` varchar(32) NOT NULL COMMENT 'VIEW 或 EDIT；运行时还需叠加数据和字段权限',
  `grantee_type` varchar(32) NOT NULL COMMENT '平台或系统范围内的全员、角色或成员',
  `platform_role_id` bigint DEFAULT NULL COMMENT '被授权的平台角色主键',
  `platform_member_id` bigint DEFAULT NULL COMMENT '被授权的平台成员主键',
  `system_role_id` bigint DEFAULT NULL COMMENT '被授权的租户角色主键',
  `tenant_member_id` bigint DEFAULT NULL COMMENT '被授权的租户成员主键',
  `created_by_account_id` bigint NOT NULL COMMENT '授权操作者账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_permission` (`dashboard_id`,`permission_code`,`grantee_type`,(coalesce(`tenant_id`,0)),(coalesce(`platform_role_id`,0)),(coalesce(`platform_member_id`,0)),(coalesce(`system_role_id`,0)),(coalesce(`tenant_member_id`,0))),
  CONSTRAINT `chk_ana_dash_perm_grantee` CHECK (((`permission_code` in (_utf8mb4'VIEW',_utf8mb4'EDIT')) and (((`grantee_type` = _utf8mb4'ALL') and (`platform_role_id` is null) and (`platform_member_id` is null) and (`system_role_id` is null) and (`tenant_member_id` is null)) or ((`grantee_type` = _utf8mb4'PLATFORM_ROLE') and (`scope_type` = _utf8mb4'PLATFORM') and (`platform_role_id` is not null) and (`platform_member_id` is null) and (`system_role_id` is null) and (`tenant_member_id` is null)) or ((`grantee_type` = _utf8mb4'PLATFORM_MEMBER') and (`scope_type` = _utf8mb4'PLATFORM') and (`platform_role_id` is null) and (`platform_member_id` is not null) and (`system_role_id` is null) and (`tenant_member_id` is null)) or ((`grantee_type` = _utf8mb4'SYSTEM_ROLE') and (`scope_type` in (_utf8mb4'SYSTEM',_utf8mb4'TENANT')) and (`tenant_id` is not null) and (`platform_role_id` is null) and (`platform_member_id` is null) and (`system_role_id` is not null) and (`tenant_member_id` is null)) or ((`grantee_type` = _utf8mb4'SYSTEM_MEMBER') and (`scope_type` in (_utf8mb4'SYSTEM',_utf8mb4'TENANT')) and (`tenant_id` is not null) and (`platform_role_id` is null) and (`platform_member_id` is null) and (`system_role_id` is null) and (`tenant_member_id` is not null))))),
  CONSTRAINT `chk_ana_dash_perm_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`platform_id` = `scope_owner_id`) and (`system_id` is null) and (`tenant_id` is null)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`system_id` = `scope_owner_id`)) or ((`scope_type` = _utf8mb4'TENANT') and (`system_id` is not null) and (`tenant_id` = `scope_owner_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仪表盘可见及编辑授权，实际数据权限仍由运行态逐次校验';

CREATE TABLE `un_ana_dashboard_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '仪表盘组件缓存主键',
  `dashboard_id` bigint NOT NULL COMMENT '所属仪表盘主键',
  `dashboard_version_id` bigint NOT NULL COMMENT '缓存对应的不可变仪表盘发布版本主键',
  `scope_type` varchar(32) NOT NULL COMMENT '仪表盘归属层级',
  `scope_owner_id` bigint NOT NULL COMMENT '仪表盘归属主体主键',
  `platform_id` bigint NOT NULL COMMENT '当前访问平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '当前访问系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '当前访问租户主键',
  `viewer_account_id` bigint NOT NULL COMMENT '缓存所属访问者账号主键，防止跨人共享',
  `viewer_platform_account_id` bigint DEFAULT NULL COMMENT '平台访问者账号主键，系统缓存为空以免强制系统成员同时是平台成员',
  `viewer_system_member_id` bigint DEFAULT NULL COMMENT '系统访问者在当前系统的成员主键',
  `component_key` varchar(100) NOT NULL COMMENT '发布快照中的组件编码',
  `permission_hash` varchar(64) NOT NULL COMMENT '当前角色、字段及数据范围权限指纹',
  `query_hash` varchar(64) NOT NULL COMMENT '筛选、时间范围及统计口径指纹',
  `payload_json` json NOT NULL COMMENT '已脱敏且限于当前权限的数据结果快照',
  `refresh_status` varchar(32) NOT NULL COMMENT '缓存刷新状态',
  `expire_time` datetime(3) NOT NULL COMMENT '缓存失效时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '缓存创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '缓存刷新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_cache` (`dashboard_version_id`,`component_key`,`viewer_account_id`,(coalesce(`tenant_id`,0)),`permission_hash`,`query_hash`),
  KEY `idx_ana_dashboard_cache_expire` (`expire_time`),
  CONSTRAINT `chk_ana_dash_cache_scope` CHECK ((((`scope_type` = _utf8mb4'PLATFORM') and (`platform_id` = `scope_owner_id`) and (`system_id` is null) and (`tenant_id` is null) and (`viewer_system_member_id` is null) and (`viewer_platform_account_id` is not null) and (`viewer_platform_account_id` = `viewer_account_id`)) or ((`scope_type` = _utf8mb4'SYSTEM') and (`system_id` = `scope_owner_id`) and (`tenant_id` is not null) and (`viewer_system_member_id` is not null) and (`viewer_platform_account_id` is null)) or ((`scope_type` = _utf8mb4'TENANT') and (`system_id` is not null) and (`tenant_id` = `scope_owner_id`) and (`viewer_system_member_id` is not null) and (`viewer_platform_account_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按发布版本、访问者、租户和权限口径隔离的仪表盘组件缓存';

CREATE TABLE `un_ana_kpi` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'KPI 目标主键',
  `context_type` varchar(32) NOT NULL COMMENT '运行上下文，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统 KPI 所属系统主键',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '租户 KPI 所属租户主键',
  `scope_type` varchar(32) NOT NULL COMMENT '唯一归属层级，PLATFORM、SYSTEM 或 TENANT',
  `scope_owner_id` bigint NOT NULL COMMENT '归属层级对应的平台、系统或租户主键',
  `code` varchar(100) NOT NULL COMMENT '同一归属层级内稳定的 KPI 编码',
  `name` varchar(200) NOT NULL COMMENT 'KPI 显示名称',
  `data_source_id` bigint NOT NULL COMMENT '指标统计口径所属数据源主键',
  `data_source_version_id` bigint NOT NULL COMMENT '固定的已发布数据源版本主键',
  `source_scope_type` varchar(32) NOT NULL COMMENT '被引用数据源版本的归属层级，可为本租户、系统默认或主租户共享',
  `source_scope_owner_id` bigint NOT NULL COMMENT '被引用数据源版本的归属主体主键',
  `target_subject_type` varchar(32) NOT NULL COMMENT '目标对象类型：全部、人员、部门或角色',
  `target_subject_id` bigint DEFAULT NULL COMMENT '人员、部门或角色目标对象主键，全部对象时为空',
  `target_expression` varchar(1000) NOT NULL COMMENT '目标值或计算表达式',
  `calculation_schedule` varchar(100) NOT NULL COMMENT '目标周期，MONTH、QUARTER 或 YEAR',
  `dimension_json` json NOT NULL COMMENT '分组维度、汇总字段与可见范围配置',
  `reminder_policy_json` json NOT NULL COMMENT '未达标提醒策略',
  `status` varchar(32) NOT NULL COMMENT '草稿、启用或停用状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建者账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁及计算口径版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_kpi_code` (`scope_type`,`scope_owner_id`,`code`),
  KEY `idx_ana_kpi_scope_id` (`id`,`scope_type`,`scope_owner_id`),
  CONSTRAINT `chk_ana_kpi_period` CHECK ((`calculation_schedule` in (_utf8mb4'MONTH',_utf8mb4'QUARTER',_utf8mb4'YEAR'))),
  CONSTRAINT `chk_ana_kpi_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`scope_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`scope_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is null) and (`scope_owner_id` = `system_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`scope_type` = _utf8mb4'TENANT') and (`system_id` is not null) and (`owner_tenant_id` is not null) and (`scope_owner_id` = `owner_tenant_id`)))),
  CONSTRAINT `chk_ana_kpi_target` CHECK ((((`target_subject_type` = _utf8mb4'ALL') and (`target_subject_id` is null)) or ((`target_subject_type` in (_utf8mb4'PERSON',_utf8mb4'DEPARTMENT',_utf8mb4'ROLE')) and (`target_subject_id` is not null) and (`scope_type` <> _utf8mb4'SYSTEM'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按发布数据源版本计算的 KPI 目标及提醒口径';

CREATE TABLE `un_ana_kpi_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '一次 KPI 维度计算结果主键',
  `kpi_id` bigint NOT NULL COMMENT '所属 KPI 目标主键',
  `scope_type` varchar(32) NOT NULL COMMENT '计算结果归属层级，与 KPI 一致',
  `scope_owner_id` bigint NOT NULL COMMENT '计算结果归属主体主键',
  `period_key` varchar(100) NOT NULL COMMENT '月、季度或年的周期标识',
  `dimension_key` varchar(500) NOT NULL COMMENT '人员、部门、角色或分组维度稳定键',
  `calculation_batch_key` varchar(64) NOT NULL COMMENT '同一次重算中各维度共享的计算批次键',
  `calculation_revision` int NOT NULL COMMENT '同一周期和维度递增的计算修订号，最大号为当前结果',
  `kpi_config_version` int NOT NULL COMMENT '计算时固定的 KPI 配置乐观锁版本',
  `data_source_version_id` bigint NOT NULL COMMENT '本次计算实际使用的数据源发布版本主键',
  `source_scope_type` varchar(32) NOT NULL COMMENT '本次计算数据源版本的归属层级，可与 KPI 扩展归属不同',
  `source_scope_owner_id` bigint NOT NULL COMMENT '本次计算数据源版本的归属主体主键',
  `actual_value` decimal(30,8) DEFAULT NULL COMMENT '根据模块数据统计出的实际值',
  `target_value` decimal(30,8) DEFAULT NULL COMMENT '本次计算采用的目标值',
  `achievement_rate` decimal(12,6) DEFAULT NULL COMMENT '本次计算得到的达成率',
  `status` varchar(32) NOT NULL COMMENT '达标、未达标或计算失败状态',
  `explanation_json` json NOT NULL COMMENT '来源模块、筛选、公式、样本与失败原因快照',
  `calculate_time` datetime(3) NOT NULL COMMENT '实际计算时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '计算记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_kpi_result_revision` (`kpi_id`,`period_key`,`dimension_key`,`calculation_revision`),
  UNIQUE KEY `uk_ana_kpi_result_batch` (`kpi_id`,`period_key`,`dimension_key`,`calculation_batch_key`),
  KEY `idx_ana_kpi_result_status` (`kpi_id`,`status`,`calculate_time`),
  CONSTRAINT `chk_ana_kpi_result_revision` CHECK (((`calculation_revision` > 0) and (`kpi_config_version` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可覆盖的 KPI 计算批次与重算历史';

CREATE TABLE `un_ana_kpi_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'KPI 未达标提醒主键',
  `kpi_result_id` bigint NOT NULL COMMENT '触发提醒的 KPI 计算结果主键',
  `context_type` varchar(32) NOT NULL COMMENT '提醒投影上下文，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '平台或系统所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统提醒所属系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统提醒实际投递租户主键',
  `platform_todo_id` bigint DEFAULT NULL COMMENT '平台待办投影主键',
  `system_todo_id` bigint DEFAULT NULL COMMENT '系统待办投影主键',
  `platform_message_id` bigint DEFAULT NULL COMMENT '平台消息投影主键',
  `system_message_id` bigint DEFAULT NULL COMMENT '系统消息投影主键',
  `recipient_account_id` bigint NOT NULL COMMENT '提醒接收者账号主键',
  `recipient_platform_account_id` bigint DEFAULT NULL COMMENT '平台提醒接收者账号，系统提醒为空',
  `recipient_system_member_id` bigint DEFAULT NULL COMMENT '系统提醒接收者在当前系统的成员主键',
  `status` varchar(32) NOT NULL COMMENT '待发送、已发送、已确认或失败状态',
  `send_time` datetime(3) DEFAULT NULL COMMENT '发送时间',
  `acknowledge_time` datetime(3) DEFAULT NULL COMMENT '确认时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_kpi_reminder` (`kpi_result_id`,`recipient_account_id`,(coalesce(`tenant_id`,0))),
  CONSTRAINT `chk_ana_kpi_reminder_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null) and (`recipient_system_member_id` is null) and (`recipient_platform_account_id` is not null) and (`recipient_platform_account_id` = `recipient_account_id`) and (`system_todo_id` is null) and (`system_message_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null) and (`recipient_system_member_id` is not null) and (`recipient_platform_account_id` is null) and (`platform_todo_id` is null) and (`platform_message_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='KPI 结果关联同上下文待办与消息的提醒投影';

-- source: init/V17__print.sql
-- print 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_print_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '打印模板草稿主键',
  `system_id` bigint NOT NULL COMMENT '模板所属系统主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '模板配置所属租户主键',
  `module_id` bigint NOT NULL COMMENT '模板绑定的业务模块主键',
  `code` varchar(100) NOT NULL COMMENT '模块内模板编码',
  `name` varchar(200) NOT NULL COMMENT '模板展示名称',
  `page_size` varchar(32) NOT NULL COMMENT '纸张尺寸',
  `orientation` varchar(32) NOT NULL COMMENT '页面方向',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `template_json` json NOT NULL COMMENT '页眉、页脚、签章区和明细表格等草稿布局',
  `status` varchar(32) NOT NULL COMMENT '草稿、已发布或停用等模板状态',
  `created_by_member_id` bigint NOT NULL COMMENT '创建模板的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '草稿乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_code` (`owner_tenant_id`,`module_id`,`code`),
  KEY `idx_print_template_module_id` (`module_id`,`id`),
  KEY `idx_print_template_owner_module_id` (`owner_tenant_id`,`module_id`,`id`),
  CONSTRAINT `chk_print_template_revision` CHECK ((`draft_revision` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绑定业务模块的打印模板草稿';

CREATE TABLE `un_print_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '不可变打印模板版本主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '模板配置所属租户主键，用于校验发布人',
  `module_id` bigint NOT NULL COMMENT '模板绑定的业务模块主键',
  `template_id` bigint NOT NULL COMMENT '来源模板草稿主键',
  `module_config_version_id` bigint NOT NULL COMMENT '发布时固定的模块配置版本',
  `version_number` int NOT NULL COMMENT '同一模板内递增的发布版本号',
  `draft_revision` int NOT NULL COMMENT '发布所依据的草稿修订号',
  `snapshot_hash` varchar(64) NOT NULL COMMENT '模板与字段引用快照摘要',
  `snapshot_json` json NOT NULL COMMENT '页眉、页脚、明细和签章等不可变布局快照',
  `published_by_member_id` bigint NOT NULL COMMENT '发布模板的系统成员主键',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_version` (`template_id`,`version_number`),
  UNIQUE KEY `uk_print_template_revision` (`template_id`,`draft_revision`),
  KEY `idx_print_template_version_id` (`template_id`,`id`),
  KEY `idx_print_template_version_module_id` (`module_id`,`id`),
  KEY `idx_print_template_version_module_config` (`module_id`,`id`,`module_config_version_id`),
  CONSTRAINT `chk_print_version_number` CHECK (((`version_number` > 0) and (`draft_revision` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变打印模板发布版本与模块配置依赖';

CREATE TABLE `un_print_template_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板当前发布指针主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '模板配置所属租户主键',
  `module_id` bigint NOT NULL COMMENT '模板绑定的业务模块主键',
  `template_id` bigint NOT NULL COMMENT '模板草稿主键',
  `current_version_id` bigint NOT NULL COMMENT '当前生效的不可变模板版本',
  `updated_by_member_id` bigint NOT NULL COMMENT '最近发布或回滚的系统成员主键',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '发布指针乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_publication` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打印模板当前生效版本指针，可回滚至历史版本';

CREATE TABLE `un_print_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '打印预览或输出作业主键',
  `system_id` bigint NOT NULL COMMENT '执行系统主键',
  `tenant_id` bigint NOT NULL COMMENT '执行租户主键',
  `module_id` bigint NOT NULL COMMENT '目标业务模块主键',
  `record_id` bigint NOT NULL COMMENT '打印目标业务记录主键',
  `record_version` int NOT NULL COMMENT '生成快照时目标记录乐观锁版本',
  `template_version_id` bigint NOT NULL COMMENT '固定使用的不可变打印模板版本',
  `module_config_version_id` bigint NOT NULL COMMENT '模板版本固定的模块配置版本',
  `permission_version_id` bigint NOT NULL COMMENT '打印时固定的权限版本',
  `background_job_id` bigint DEFAULT NULL COMMENT '异步作业业务申请主键，运行状态存 Redis',
  `authorization_snapshot_json` json NOT NULL COMMENT '打印时字段、附件、签章与数据范围权限快照',
  `record_snapshot_json` json NOT NULL COMMENT '打印时目标记录及关联字段展示值快照',
  `status` varchar(32) NOT NULL COMMENT '生成中、成功或失败状态',
  `output_file_id` bigint DEFAULT NULL COMMENT '生成的 PDF 文件主键',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '失败原因摘要',
  `created_by_member_id` bigint NOT NULL COMMENT '发起打印的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '生成完成时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '打印作业乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_print_job_record` (`record_id`,`create_time`),
  KEY `idx_print_job_status` (`status`,`create_time`),
  CONSTRAINT `chk_print_job_output` CHECK (((`status` <> _utf8mb4'SUCCEEDED') or ((`output_file_id` is not null) and (`finish_time` is not null)))),
  CONSTRAINT `chk_print_job_record_version` CHECK ((`record_version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绑定同模块模板版本与记录的打印结果审计';

-- source: init/V18__operations.sql
-- operations 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_ops_secret_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '受控密钥引用主键',
  `context_type` varchar(32) NOT NULL COMMENT '密钥归属层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统密钥所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '稳定归属主键，平台密钥为平台主键，系统密钥为系统主键',
  `secret_code` varchar(255) NOT NULL COMMENT '同一归属内稳定的密钥引用编码',
  `provider` varchar(64) NOT NULL COMMENT '受控密钥服务提供方',
  `reference_path` varchar(1000) NOT NULL COMMENT '受控密钥服务中的引用路径，不是密钥明文',
  `current_version` varchar(100) NOT NULL COMMENT '当前启用的密钥版本标识',
  `status` varchar(32) NOT NULL COMMENT '密钥引用的启停状态',
  `last_verified_time` datetime(3) DEFAULT NULL COMMENT '最近一次验证密钥可用性的时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '密钥引用创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '密钥引用更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '密钥引用乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_secret_ref` (`context_type`,`scope_owner_id`,`secret_code`),
  KEY `idx_ops_secret_scope_id` (`id`,`context_type`,`scope_owner_id`),
  KEY `idx_ops_secret_scope_code_id` (`id`,`context_type`,`scope_owner_id`,`secret_code`),
  CONSTRAINT `chk_ops_secret_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控密钥引用及当前版本，不保存密钥明文';

CREATE TABLE `un_ops_secret_rotation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '密钥轮换任务主键',
  `secret_ref_id` bigint NOT NULL COMMENT '被轮换的受控密钥引用主键',
  `from_version` varchar(100) NOT NULL COMMENT '轮换前密钥版本',
  `to_version` varchar(100) NOT NULL COMMENT '待切换到的密钥版本',
  `status` varchar(32) NOT NULL COMMENT '新版本验证、切换、旧版停用或回滚状态',
  `verification_json` json DEFAULT NULL COMMENT '双版本验证与失败回滚的审计结果',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起轮换的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '轮换开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '轮换结束或回滚完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '轮换申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '轮换任务乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_secret_rotation` (`secret_ref_id`,`create_time`),
  CONSTRAINT `chk_ops_secret_rotation_versions` CHECK ((`from_version` <> `to_version`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控密钥版本轮换与验证审计';

CREATE TABLE `un_ops_health_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '健康检查运行主键',
  `context_type` varchar(32) NOT NULL COMMENT '检查层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统级检查所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `run_type` varchar(32) NOT NULL COMMENT '健康检查类型',
  `release_version` varchar(100) DEFAULT NULL COMMENT '检查时运行的产品版本',
  `status` varchar(32) NOT NULL COMMENT '健康检查结果状态',
  `summary_json` json DEFAULT NULL COMMENT '检查汇总和处置建议',
  `started_by_account_id` bigint NOT NULL COMMENT '发起检查的平台账号主键',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '健康检查开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '健康检查结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_health_context` (`context_type`,`scope_owner_id`,`status`,`start_time`),
  CONSTRAINT `chk_ops_health_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统健康检查运行及结果汇总';

CREATE TABLE `un_ops_health_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '健康检查项目主键',
  `health_run_id` bigint NOT NULL COMMENT '所属健康检查运行主键',
  `check_code` varchar(100) NOT NULL COMMENT '检查项稳定编码',
  `check_name` varchar(200) NOT NULL COMMENT '检查项显示名称',
  `category` varchar(64) NOT NULL COMMENT '检查项业务类别',
  `status` varchar(32) NOT NULL COMMENT '检查项结果状态',
  `message` varchar(2000) DEFAULT NULL COMMENT '结果说明和修复建议',
  `metric_json` json DEFAULT NULL COMMENT '检查项数值与证据',
  `check_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '检查项完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_health_item` (`health_run_id`,`check_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康检查逐项结果';

CREATE TABLE `un_ops_release` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品发布版本主键',
  `version_name` varchar(100) NOT NULL COMMENT '全局唯一的产品发布版本号',
  `artifact_hash` varchar(64) NOT NULL COMMENT '部署产物校验摘要',
  `database_version` varchar(100) NOT NULL COMMENT '数据库结构版本',
  `config_version` varchar(100) NOT NULL COMMENT '配置结构版本',
  `compatibility_json` json NOT NULL COMMENT '升级兼容性及环境要求',
  `release_notes` text NOT NULL COMMENT '发布内容和升级说明',
  `status` varchar(32) NOT NULL COMMENT '发布版本状态',
  `created_by_account_id` bigint NOT NULL COMMENT '登记版本的平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布版本登记时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_release_version` (`version_name`),
  UNIQUE KEY `uk_ops_release_hash` (`artifact_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局部署产物及兼容性版本目录';

CREATE TABLE `un_ops_deployment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '环境部署记录主键',
  `release_id` bigint NOT NULL COMMENT '目标产品发布版本主键',
  `environment_code` varchar(100) NOT NULL COMMENT '开发、测试或生产等环境编码',
  `deployment_type` varchar(32) NOT NULL COMMENT '部署或回滚操作类型',
  `from_release_id` bigint DEFAULT NULL COMMENT '部署前产品发布版本主键',
  `status` varchar(32) NOT NULL COMMENT '部署执行结果状态',
  `step_state_json` json NOT NULL COMMENT '部署步骤及校验结果',
  `rollback_point_json` json DEFAULT NULL COMMENT '可恢复的回滚点信息',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起部署的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '部署开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '部署结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '部署申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '部署记录乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_deployment_env` (`environment_code`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多环境部署、验证及回滚证据';

CREATE TABLE `un_ops_backup` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '备份任务主键',
  `context_type` varchar(32) NOT NULL COMMENT '备份层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统备份所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `backup_type` varchar(32) NOT NULL COMMENT '数据库、文件、配置或密钥策略等备份类型',
  `status` varchar(32) NOT NULL COMMENT '备份执行结果状态',
  `manifest_hash` varchar(64) DEFAULT NULL COMMENT '备份清单校验摘要',
  `encryption_secret_ref_id` bigint NOT NULL COMMENT '同范围备份加密密钥引用主键，不保存密钥明文',
  `retention_until_time` datetime(3) NOT NULL COMMENT '备份保留截止时间',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起备份的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '备份开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '备份结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '备份记录乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_backup_scope_id` (`id`,`context_type`,`scope_owner_id`),
  KEY `idx_ops_backup_context` (`context_type`,`scope_owner_id`,`status`,`create_time`),
  CONSTRAINT `chk_ops_backup_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分范围备份任务及加密密钥引用';

CREATE TABLE `un_ops_backup_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '备份内容项主键',
  `backup_id` bigint NOT NULL COMMENT '所属备份任务主键',
  `item_type` varchar(32) NOT NULL COMMENT '数据库、文件、配置或模板等内容类别',
  `storage_uri` varchar(2000) NOT NULL COMMENT '备份内容的受控存储位置',
  `size_bytes` bigint NOT NULL COMMENT '备份内容字节大小',
  `sha256` varchar(64) NOT NULL COMMENT '备份内容 SHA-256 校验摘要',
  `status` varchar(32) NOT NULL COMMENT '备份内容校验状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份内容登记时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_backup_item` (`backup_id`,`item_type`,`storage_uri`(191)),
  CONSTRAINT `chk_ops_backup_item_size` CHECK ((`size_bytes` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备份清单内容及完整性校验';

CREATE TABLE `un_ops_restore_drill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '恢复演练主键',
  `backup_id` bigint NOT NULL COMMENT '被验证备份任务主键',
  `environment_code` varchar(100) NOT NULL COMMENT '隔离恢复环境编码',
  `status` varchar(32) NOT NULL COMMENT '恢复演练执行结果状态',
  `verification_json` json DEFAULT NULL COMMENT '恢复完整性及业务验收结果',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起演练的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '恢复演练开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '恢复演练结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '恢复演练申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '恢复演练乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_restore_backup` (`backup_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备份恢复演练及可恢复性证据';

CREATE TABLE `un_ops_upgrade` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品升级任务主键',
  `release_id` bigint NOT NULL COMMENT '升级目标发布版本主键',
  `backup_id` bigint NOT NULL COMMENT '升级前同范围备份任务主键',
  `context_type` varchar(32) NOT NULL COMMENT '升级层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统级升级所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `impact_report_json` json NOT NULL COMMENT '升级前模块、权限、数据和兼容性影响报告',
  `status` varchar(32) NOT NULL COMMENT '升级执行结果状态',
  `rollback_point_json` json NOT NULL COMMENT '失败时可恢复的回滚点及处置方案',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起升级的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '升级开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '升级结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '升级申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '升级任务乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_upgrade_status` (`context_type`,`scope_owner_id`,`status`,`create_time`),
  CONSTRAINT `chk_ops_upgrade_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有备份和回滚点的产品升级任务';

CREATE TABLE `un_ops_upgrade_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '升级步骤主键',
  `upgrade_id` bigint NOT NULL COMMENT '所属升级任务主键',
  `step_number` int NOT NULL COMMENT '升级任务内执行顺序号',
  `step_type` varchar(32) NOT NULL COMMENT '数据库、配置、页面、流程或权限升级步骤类型',
  `source_version` varchar(100) NOT NULL COMMENT '步骤执行前版本',
  `target_version` varchar(100) NOT NULL COMMENT '步骤目标版本',
  `mapping_json` json DEFAULT NULL COMMENT '配置或数据升级映射及校验依据',
  `status` varchar(32) NOT NULL COMMENT '步骤执行结果状态',
  `start_time` datetime(3) DEFAULT NULL COMMENT '升级步骤开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '升级步骤结束时间',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '步骤失败原因与处理提示',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_upgrade_step` (`upgrade_id`,`step_number`),
  CONSTRAINT `chk_ops_upgrade_step_number` CHECK ((`step_number` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='升级步骤及失败处置证据';

CREATE TABLE `un_ops_verification_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品验收运行主键',
  `verification_type` varchar(32) NOT NULL COMMENT '验收类型，如部署、升级或业务场景',
  `context_type` varchar(32) NOT NULL COMMENT '验收层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统验收所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `release_id` bigint DEFAULT NULL COMMENT '验收对应的产品发布版本主键',
  `scenario_code` varchar(100) NOT NULL COMMENT '可复现的验收场景编码',
  `status` varchar(32) NOT NULL COMMENT '验收结果状态',
  `input_json` json NOT NULL COMMENT '验收输入、前置条件和步骤参数',
  `result_json` json DEFAULT NULL COMMENT '验收读回结果及业务证据',
  `threshold_json` json NOT NULL COMMENT '验收通过条件和限制',
  `started_by_account_id` bigint NOT NULL COMMENT '发起验收的平台账号主键',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '验收开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '验收结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_verification_type` (`context_type`,`scope_owner_id`,`verification_type`,`status`,`start_time`),
  CONSTRAINT `chk_ops_verification_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可复现的产品功能与部署验收记录';

CREATE TABLE `un_ops_verification_finding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '验收发现问题主键',
  `verification_run_id` bigint NOT NULL COMMENT '所属验收运行主键',
  `severity` varchar(32) NOT NULL COMMENT '问题严重程度',
  `finding_code` varchar(100) NOT NULL COMMENT '问题稳定编码',
  `title` varchar(500) NOT NULL COMMENT '问题标题',
  `detail_text` text NOT NULL COMMENT '复现步骤、预期与实际结果',
  `evidence_json` json DEFAULT NULL COMMENT '日志、截图或读回数据证据引用',
  `status` varchar(32) NOT NULL COMMENT '问题处理状态',
  `resolved_by_account_id` bigint DEFAULT NULL COMMENT '解决问题的平台账号主键',
  `resolve_time` datetime(3) DEFAULT NULL COMMENT '问题解决时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '问题登记时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '问题记录乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_finding_code` (`verification_run_id`,`finding_code`),
  KEY `idx_ops_finding_run` (`verification_run_id`,`severity`,`status`),
  CONSTRAINT `chk_ops_finding_resolution` CHECK (((`resolve_time` is null) or (`resolved_by_account_id` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='验收发现的问题及整改证据';

CREATE TABLE `un_ops_request_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '请求观测记录主键',
  `request_id` varchar(64) NOT NULL COMMENT '对外请求标识，用于定位一次调用',
  `trace_id` varchar(64) NOT NULL COMMENT '跨服务调用链追踪标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统上下文主键，平台请求为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户上下文主键，平台或系统公共请求为空',
  `account_id` bigint DEFAULT NULL COMMENT '已识别的平台账号主键，匿名请求为空',
  `client_source` varchar(32) NOT NULL COMMENT '发起请求的客户端渠道',
  `http_method` varchar(16) NOT NULL COMMENT 'HTTP 请求方法',
  `request_path` varchar(500) NOT NULL COMMENT '不含查询串的请求路径',
  `status_code` int NOT NULL COMMENT 'HTTP 响应状态码',
  `result_code` varchar(64) NOT NULL COMMENT '应用层结果或错误编码',
  `duration_millis` bigint NOT NULL COMMENT '请求处理耗时，单位毫秒',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '请求发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_request_id` (`request_id`,`occur_time`),
  KEY `idx_ops_request_context` (`system_id`,`tenant_id`,`occur_time`),
  KEY `idx_ops_request_result` (`status_code`,`result_code`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不含敏感请求内容的调用观测与追踪记录';

-- source: update/V19__flow_external_identity.sql
-- 外部 Flow 沿用现有实例、任务和操作表；应用 ID 是身份命名空间，外部标识不映射成平台账号。
ALTER TABLE `un_flow_instance`
  ADD COLUMN `source_application_id` bigint DEFAULT NULL COMMENT '外部发起来源应用ID；内部发起为空' AFTER `started_by_tenant_member_id`,
  ADD COLUMN `source_external_principal_id` varchar(100) DEFAULT NULL COMMENT '应用命名空间内的外部发起人标识' AFTER `source_application_id`,
  ADD KEY `idx_flow_instance_source_app` (`source_application_id`,`source_external_principal_id`,`start_time`);

ALTER TABLE `un_flow_task`
  ADD COLUMN `assignee_application_id` bigint DEFAULT NULL COMMENT '外部审批人所属应用ID；内部任务为空' AFTER `assignee_tenant_member_id`,
  ADD COLUMN `assignee_external_principal_id` varchar(100) DEFAULT NULL COMMENT '应用命名空间内的外部审批人标识；候选池任务为空' AFTER `assignee_application_id`,
  ADD KEY `idx_flow_task_external_assignee` (`assignee_application_id`,`assignee_external_principal_id`,`status`);

ALTER TABLE `un_flow_action`
  ADD COLUMN `actor_application_id` bigint DEFAULT NULL COMMENT '外部操作来源应用ID；内部操作为空' AFTER `acted_by_tenant_member_id`,
  ADD COLUMN `actor_external_principal_id` varchar(100) DEFAULT NULL COMMENT '应用命名空间内的外部操作人标识' AFTER `actor_application_id`,
  ADD KEY `idx_flow_action_external_actor` (`actor_application_id`,`actor_external_principal_id`,`act_time`);

-- source: update/V20__flow_application_owner.sql
-- 外部应用配置的 Flow 记录来源应用，便于草稿读取、修改和发布时逐次校验归属。
ALTER TABLE `un_flow_definition`
  ADD COLUMN `owner_application_id` bigint DEFAULT NULL COMMENT '配置此流程的应用ID；系统后台配置为空' AFTER `owner_tenant_id`,
  ADD KEY `idx_flow_definition_owner_application` (`owner_application_id`,`id`);

-- source: update/V21__operations_release_artifact_index.sql
-- 一个制品可配合多个配置版本发布；版本号仍唯一，摘要只用于查询和校验。
ALTER TABLE `un_ops_release`
  DROP INDEX `uk_ops_release_hash`,
  ADD KEY `idx_ops_release_hash` (`artifact_hash`);

-- source: update/V22__print_legacy_upgrade.sql
-- 升级时固定旧打印模板的兼容范围；新装库和升级后新建的模板不会进入本表。
-- 管理员发布 PRINT 动作后填写退出时间，避免停用或回滚时重新放开旧兼容入口。
CREATE TABLE `un_print_legacy_template` (
  `module_id` bigint NOT NULL COMMENT '升级时已有打印模板的业务模块主键',
  `template_id` bigint NOT NULL COMMENT '升级时已发布的打印模板主键',
  `system_id` bigint NOT NULL COMMENT '业务模块所属系统主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '业务模块配置所属租户主键',
  `source_module_version_id` bigint NOT NULL COMMENT '升级时固定的模块发布版本主键',
  `retired_time` datetime(3) DEFAULT NULL COMMENT '模块正式发布打印动作后的兼容退出时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '升级兼容记录创建时间',
  PRIMARY KEY (`module_id`,`template_id`),
  KEY `idx_print_legacy_scope` (`system_id`,`owner_tenant_id`,`module_id`,`retired_time`),
  KEY `idx_print_legacy_template` (`template_id`,`retired_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='升级前已发布打印模板的限定兼容清单';

-- 只从当前活跃模块、当前已发布模块快照和已发布打印模板取样，避免草稿或新装库获得兼容权。
INSERT INTO `un_print_legacy_template`
  (`module_id`,`template_id`,`system_id`,`owner_tenant_id`,`source_module_version_id`,`retired_time`,`create_time`)
SELECT DISTINCT m.`id`, t.`id`, m.`system_id`, m.`owner_tenant_id`, mp.`current_version_id`, NULL, CURRENT_TIMESTAMP(3)
FROM `un_cfg_module` m
JOIN `un_cfg_module_publication` mp ON mp.`module_id` = m.`id`
JOIN `un_cfg_module_version` mv ON mv.`id` = mp.`current_version_id`
JOIN `un_print_template` t ON t.`module_id` = m.`id`
  AND t.`system_id` = m.`system_id` AND t.`owner_tenant_id` = m.`owner_tenant_id`
JOIN `un_print_template_publication` tp ON tp.`template_id` = t.`id`
WHERE m.`status` = 'ACTIVE'
  AND JSON_SEARCH(mv.`snapshot_json`, 'one', 'PRINT', NULL, '$.actions[*].code') IS NULL;

-- source: update/V23__module_dashboard_legacy.sql
-- 只固定升级前已经发布的模块首页；新装库、新首页和未发布草稿不会获得旧入口兼容。
-- 模块与仪表盘的旧发布快照均保持不变；运行时仍按当前系统、租户、模块和成员 LIST 权限重查。
CREATE TABLE `un_ana_legacy_module_dashboard` (
  `dashboard_id` bigint NOT NULL COMMENT '升级前已发布的模块首页主键',
  `module_id` bigint NOT NULL COMMENT '首页绑定的当前业务模块主键',
  `system_id` bigint NOT NULL COMMENT '模块与首页共同所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '首页归属租户主键，不向其他租户回退',
  `source_module_version_id` bigint NOT NULL COMMENT '升级时模块当前发布版本主键，用于核对来源',
  `source_dashboard_version_id` bigint NOT NULL COMMENT '升级时首页当前发布版本主键，用于核对来源',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '兼容清单建立时间',
  PRIMARY KEY (`dashboard_id`),
  KEY `idx_ana_legacy_dashboard_scope` (`system_id`,`tenant_id`,`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='升级前已发布模块首页的限定兼容清单';

-- 同时核对模块和首页的有效发布指针，只保存缺少 DASHBOARD 动作的旧模块首页。
INSERT INTO `un_ana_legacy_module_dashboard`
  (`dashboard_id`,`module_id`,`system_id`,`tenant_id`,`source_module_version_id`,
   `source_dashboard_version_id`,`create_time`)
SELECT d.`id`, m.`id`, m.`system_id`, d.`owner_tenant_id`, mp.`current_version_id`,
       dp.`current_version_id`, CURRENT_TIMESTAMP(3)
FROM `un_ana_dashboard` d
JOIN `un_ana_dashboard_publication` dp ON dp.`dashboard_id` = d.`id`
JOIN `un_ana_dashboard_version` dv ON dv.`id` = dp.`current_version_id`
  AND dv.`dashboard_id` = d.`id` AND dv.`scope_type` = d.`scope_type`
  AND dv.`scope_owner_id` = d.`scope_owner_id`
JOIN `un_cfg_module` m ON m.`id` = d.`module_id` AND m.`system_id` = d.`system_id`
JOIN `un_cfg_module_publication` mp ON mp.`module_id` = m.`id`
  AND mp.`system_id` = m.`system_id` AND mp.`owner_tenant_id` = m.`owner_tenant_id`
JOIN `un_cfg_module_version` mv ON mv.`id` = mp.`current_version_id`
  AND mv.`module_id` = m.`id` AND mv.`system_id` = m.`system_id`
  AND mv.`owner_tenant_id` = m.`owner_tenant_id`
WHERE d.`context_type` = 'SYSTEM' AND d.`scope_type` = 'TENANT'
  AND d.`dashboard_kind` = 'MODULE_HOME' AND d.`owner_tenant_id` IS NOT NULL
  AND m.`status` = 'ACTIVE'
  AND JSON_SEARCH(mv.`snapshot_json`, 'one', 'DASHBOARD', NULL, '$.actions[*].code') IS NULL;

-- source: update/V24__audit_platform_scope.sql
-- 用途：将既有平台审计事件绑定到唯一平台，供平台后台按平台主键安全查询。
-- 升级前备份数据库；升级后对照 PLATFORM 事件总数、已绑定数及仍无归属数。
-- 多平台数据库无法从旧事件反推出归属，保持无归属且不对任何平台展示，需人工依据外部证据修复。
UPDATE `un_audit_event` AS event_row
JOIN (
  SELECT MIN(`id`) AS `platform_id`
  FROM `un_plat_platform`
  HAVING COUNT(*) = 1
) AS only_platform
SET event_row.`platform_id` = only_platform.`platform_id`
WHERE event_row.`context_type` = 'PLATFORM'
  AND event_row.`platform_id` IS NULL
  AND event_row.`system_id` IS NULL
  AND event_row.`tenant_id` IS NULL;
