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
