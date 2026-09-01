-- Initial baseline: generic runtime records only; custom modules never create physical business tables.

CREATE TABLE `biz_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `created_config_version_id` BIGINT NOT NULL,
  `updated_config_version_id` BIGINT NOT NULL,
  `record_number` VARCHAR(100) NULL,
  `title` VARCHAR(500) NOT NULL,
  `status` VARCHAR(64) NOT NULL,
  `owner_member_id` BIGINT NULL,
  `department_id` BIGINT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `updated_by_member_id` BIGINT NOT NULL,
  `archived` TINYINT NOT NULL DEFAULT 0,
  `archived_at` DATETIME(3) NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `deleted_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_number` (`tenant_id`, `module_id`, `record_number`),
  UNIQUE KEY `uk_biz_record_tenant_id` (`tenant_id`, `id`),
  UNIQUE KEY `uk_biz_record_module_id` (`module_id`, `id`),
  KEY `idx_biz_record_list` (`tenant_id`, `module_id`, `deleted`, `updated_at`),
  KEY `idx_biz_record_owner` (`tenant_id`, `module_id`, `owner_member_id`),
  KEY `idx_biz_record_department` (`tenant_id`, `module_id`, `department_id`),
  CONSTRAINT `fk_biz_record_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_biz_record_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_biz_record_created_config_version` FOREIGN KEY (`module_id`, `created_config_version_id`) REFERENCES `cfg_module_version` (`module_id`, `id`),
  CONSTRAINT `fk_biz_record_updated_config_version` FOREIGN KEY (`module_id`, `updated_config_version_id`) REFERENCES `cfg_module_version` (`module_id`, `id`),
  CONSTRAINT `fk_biz_record_owner` FOREIGN KEY (`system_id`, `owner_member_id`) REFERENCES `sys_member` (`system_id`, `id`),
  CONSTRAINT `fk_biz_record_department` FOREIGN KEY (`tenant_id`, `department_id`) REFERENCES `sys_department` (`tenant_id`, `id`),
  CONSTRAINT `fk_biz_record_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`),
  CONSTRAINT `fk_biz_record_updater` FOREIGN KEY (`system_id`, `updated_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `record_id` BIGINT NOT NULL,
  `field_id` BIGINT NOT NULL,
  `field_code` VARCHAR(100) NOT NULL,
  `value_type` VARCHAR(32) NOT NULL,
  `value_text` TEXT NULL,
  `value_number` DECIMAL(24, 8) NULL,
  `value_date` DATE NULL,
  `value_datetime` DATETIME(3) NULL,
  `value_boolean` TINYINT NULL,
  `value_reference_id` BIGINT NULL,
  `value_file_id` BIGINT NULL,
  `value_json` JSON NULL,
  `normalized_text` VARCHAR(1000) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_value_field` (`record_id`, `field_id`),
  KEY `idx_biz_record_value_text` (`field_id`, `normalized_text`(191)),
  KEY `idx_biz_record_value_number` (`field_id`, `value_number`),
  KEY `idx_biz_record_value_date` (`field_id`, `value_datetime`),
  KEY `idx_biz_record_value_reference` (`field_id`, `value_reference_id`),
  CONSTRAINT `fk_biz_record_value_record` FOREIGN KEY (`record_id`) REFERENCES `biz_record` (`id`),
  CONSTRAINT `fk_biz_record_value_field` FOREIGN KEY (`field_id`) REFERENCES `cfg_module_field` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `record_id` BIGINT NOT NULL,
  `query_index_id` BIGINT NOT NULL,
  `index_key_hash` VARCHAR(64) NOT NULL,
  `index_key_text` VARCHAR(1500) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_index_record` (`record_id`, `query_index_id`),
  KEY `idx_biz_record_index_lookup` (`tenant_id`, `module_id`, `query_index_id`, `index_key_hash`),
  CONSTRAINT `fk_biz_record_index_record` FOREIGN KEY (`tenant_id`, `record_id`) REFERENCES `biz_record` (`tenant_id`, `id`),
  CONSTRAINT `fk_biz_record_index_definition` FOREIGN KEY (`query_index_id`) REFERENCES `cfg_query_index` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_relation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `source_record_id` BIGINT NOT NULL,
  `field_id` BIGINT NOT NULL,
  `target_record_id` BIGINT NOT NULL,
  `relation_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_relation` (`source_record_id`, `field_id`, `target_record_id`),
  KEY `idx_biz_relation_target` (`tenant_id`, `target_record_id`),
  CONSTRAINT `fk_biz_relation_source` FOREIGN KEY (`tenant_id`, `source_record_id`) REFERENCES `biz_record` (`tenant_id`, `id`),
  CONSTRAINT `fk_biz_relation_target` FOREIGN KEY (`tenant_id`, `target_record_id`) REFERENCES `biz_record` (`tenant_id`, `id`),
  CONSTRAINT `fk_biz_relation_field` FOREIGN KEY (`field_id`) REFERENCES `cfg_module_field` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_participant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `record_id` BIGINT NOT NULL,
  `system_member_id` BIGINT NOT NULL,
  `participant_type` VARCHAR(32) NOT NULL DEFAULT 'PARTICIPANT',
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_participant` (`record_id`, `system_member_id`, `participant_type`),
  KEY `idx_biz_record_participant_member` (`tenant_id`, `system_member_id`, `record_id`),
  CONSTRAINT `fk_biz_record_participant_record` FOREIGN KEY (`tenant_id`, `record_id`) REFERENCES `biz_record` (`tenant_id`, `id`),
  CONSTRAINT `fk_biz_record_participant_member` FOREIGN KEY (`tenant_id`, `system_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `system_member_id`),
  CONSTRAINT `fk_biz_record_participant_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_state_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `record_id` BIGINT NOT NULL,
  `from_status` VARCHAR(64) NULL,
  `to_status` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(1000) NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `source_id` VARCHAR(100) NULL,
  `changed_by_member_id` BIGINT NOT NULL,
  `changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_biz_state_record` (`record_id`, `changed_at`),
  CONSTRAINT `fk_biz_state_record` FOREIGN KEY (`record_id`) REFERENCES `biz_record` (`id`),
  CONSTRAINT `fk_biz_state_actor` FOREIGN KEY (`changed_by_member_id`) REFERENCES `sys_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_owner_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `record_id` BIGINT NOT NULL,
  `from_owner_member_id` BIGINT NULL,
  `to_owner_member_id` BIGINT NULL,
  `from_department_id` BIGINT NULL,
  `to_department_id` BIGINT NULL,
  `reason` VARCHAR(1000) NOT NULL,
  `changed_by_member_id` BIGINT NOT NULL,
  `changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_biz_owner_record` (`record_id`, `changed_at`),
  CONSTRAINT `fk_biz_owner_record` FOREIGN KEY (`record_id`) REFERENCES `biz_record` (`id`),
  CONSTRAINT `fk_biz_owner_actor` FOREIGN KEY (`changed_by_member_id`) REFERENCES `sys_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_conversion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `source_record_id` BIGINT NOT NULL,
  `action_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `mapping_snapshot_json` JSON NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_conversion_idempotency` (`tenant_id`, `idempotency_key`),
  KEY `idx_biz_conversion_source` (`source_record_id`, `created_at`),
  CONSTRAINT `fk_biz_conversion_source` FOREIGN KEY (`tenant_id`, `source_record_id`) REFERENCES `biz_record` (`tenant_id`, `id`),
  CONSTRAINT `fk_biz_conversion_action` FOREIGN KEY (`action_id`) REFERENCES `cfg_module_action` (`id`),
  CONSTRAINT `fk_biz_conversion_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_record_conversion_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversion_id` BIGINT NOT NULL,
  `target_module_id` BIGINT NOT NULL,
  `target_record_id` BIGINT NOT NULL,
  `result_type` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_conversion_result` (`conversion_id`, `target_record_id`),
  CONSTRAINT `fk_biz_conversion_result_parent` FOREIGN KEY (`conversion_id`) REFERENCES `biz_record_conversion` (`id`),
  CONSTRAINT `fk_biz_conversion_result_module` FOREIGN KEY (`target_module_id`) REFERENCES `cfg_module` (`id`),
  CONSTRAINT `fk_biz_conversion_result_record` FOREIGN KEY (`target_record_id`) REFERENCES `biz_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_tenant_share` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `source_tenant_id` BIGINT NOT NULL,
  `target_tenant_id` BIGINT NOT NULL,
  `record_id` BIGINT NOT NULL,
  `permission_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `expires_at` DATETIME(3) NULL,
  `granted_by_member_id` BIGINT NOT NULL,
  `granted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `revoked_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_tenant_share` (`source_tenant_id`, `target_tenant_id`, `record_id`),
  KEY `idx_biz_share_target` (`target_tenant_id`, `status`, `expires_at`),
  CONSTRAINT `fk_biz_share_source_tenant` FOREIGN KEY (`system_id`, `source_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_biz_share_target_tenant` FOREIGN KEY (`system_id`, `target_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_biz_share_record` FOREIGN KEY (`source_tenant_id`, `record_id`) REFERENCES `biz_record` (`tenant_id`, `id`),
  CONSTRAINT `fk_biz_share_granter` FOREIGN KEY (`system_id`, `granted_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `biz_tenant_share_usage` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `share_id` BIGINT NOT NULL,
  `target_tenant_member_id` BIGINT NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `result_code` VARCHAR(64) NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_biz_share_usage` (`share_id`, `occurred_at`),
  CONSTRAINT `fk_biz_share_usage_share` FOREIGN KEY (`share_id`) REFERENCES `biz_tenant_share` (`id`),
  CONSTRAINT `fk_biz_share_usage_member` FOREIGN KEY (`target_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
