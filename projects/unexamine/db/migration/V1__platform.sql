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
