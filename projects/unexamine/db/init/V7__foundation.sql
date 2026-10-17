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
