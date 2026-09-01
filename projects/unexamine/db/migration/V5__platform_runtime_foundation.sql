-- Initial baseline: settings, feature flags, quotas, idempotency, numbering and local events.

CREATE TABLE `core_setting` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `category` VARCHAR(100) NOT NULL,
  `setting_key` VARCHAR(150) NOT NULL,
  `value_type` VARCHAR(32) NOT NULL,
  `value_json` JSON NOT NULL,
  `sensitive` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_setting` (`context_type`, `platform_id`, `system_id`, `tenant_id`, `category`, `setting_key`),
  KEY `idx_core_setting_context` (`system_id`, `tenant_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `core_feature_flag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `flag_key` VARCHAR(150) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 0,
  `rollout_json` JSON NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_feature_flag` (`context_type`, `platform_id`, `system_id`, `tenant_id`, `flag_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `core_quota_policy` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `quota_code` VARCHAR(100) NOT NULL,
  `period_type` VARCHAR(32) NOT NULL,
  `hard_limit` BIGINT NOT NULL,
  `warning_threshold` BIGINT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_quota_policy` (`context_type`, `platform_id`, `system_id`, `tenant_id`, `quota_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `core_quota_usage` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `quota_policy_id` BIGINT NOT NULL,
  `period_key` VARCHAR(64) NOT NULL,
  `used_value` BIGINT NOT NULL DEFAULT 0,
  `reserved_value` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_quota_usage` (`quota_policy_id`, `period_key`),
  CONSTRAINT `fk_core_quota_usage_policy` FOREIGN KEY (`quota_policy_id`) REFERENCES `core_quota_policy` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `core_idempotency_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_key` VARCHAR(255) NOT NULL,
  `operation_code` VARCHAR(100) NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `request_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `response_code` VARCHAR(64) NULL,
  `response_json` JSON NULL,
  `locked_until` DATETIME(3) NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_idempotency` (`context_key`, `operation_code`, `idempotency_key`),
  KEY `idx_core_idempotency_expiry` (`expires_at`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `core_sequence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `sequence_code` VARCHAR(100) NOT NULL,
  `pattern` VARCHAR(255) NOT NULL,
  `reset_period` VARCHAR(32) NOT NULL,
  `current_period_key` VARCHAR(64) NOT NULL,
  `current_value` BIGINT NOT NULL DEFAULT 0,
  `step_value` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_sequence` (`tenant_id`, `sequence_code`),
  CONSTRAINT `fk_core_sequence_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `core_event_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `aggregate_type` VARCHAR(100) NOT NULL,
  `aggregate_id` VARCHAR(100) NOT NULL,
  `event_type` VARCHAR(100) NOT NULL,
  `payload_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `available_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `published_at` DATETIME(3) NULL,
  `retry_count` INT NOT NULL DEFAULT 0,
  `last_error` VARCHAR(2000) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_core_outbox_dispatch` (`status`, `available_at`),
  KEY `idx_core_outbox_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `core_cache_epoch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_key` VARCHAR(255) NOT NULL,
  `cache_namespace` VARCHAR(100) NOT NULL,
  `epoch_value` BIGINT NOT NULL DEFAULT 1,
  `reason` VARCHAR(500) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_core_cache_epoch` (`context_key`, `cache_namespace`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
