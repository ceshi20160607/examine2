-- Initial baseline: applications are the only bridge for cross-system and external access.

CREATE TABLE `app_definition` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `owner_system_id` BIGINT NULL,
  `owner_tenant_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `application_type` VARCHAR(32) NOT NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_definition_code` (`context_type`, `platform_id`, `owner_system_id`, `owner_tenant_id`, `code`),
  KEY `idx_app_definition_owner` (`owner_system_id`, `owner_tenant_id`, `status`),
  CONSTRAINT `fk_app_definition_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_callback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `callback_type` VARCHAR(32) NOT NULL,
  `url` VARCHAR(1000) NOT NULL,
  `event_codes_json` JSON NOT NULL,
  `signing_secret_ref` VARCHAR(255) NULL,
  `timeout_millis` INT NOT NULL DEFAULT 5000,
  `max_attempts` INT NOT NULL DEFAULT 3,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_app_callback_application` (`application_id`, `status`),
  CONSTRAINT `fk_app_callback_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_grant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `target_type` VARCHAR(32) NOT NULL,
  `target_system_id` BIGINT NULL,
  `target_tenant_id` BIGINT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `resource_id` VARCHAR(100) NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `data_scope_json` JSON NOT NULL,
  `rate_limit_json` JSON NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_grant_resource` (`application_id`, `target_type`, `target_system_id`, `target_tenant_id`, `resource_type`, `resource_id`, `action_code`),
  KEY `idx_app_grant_target` (`target_system_id`, `target_tenant_id`, `status`),
  CONSTRAINT `fk_app_grant_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_grant_system` FOREIGN KEY (`target_system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_app_grant_tenant` FOREIGN KEY (`target_system_id`, `target_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_grant_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `grant_id` BIGINT NOT NULL,
  `field_code` VARCHAR(100) NOT NULL,
  `readable` TINYINT NOT NULL DEFAULT 1,
  `writable` TINYINT NOT NULL DEFAULT 0,
  `mask_strategy` VARCHAR(64) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_grant_field` (`grant_id`, `field_code`),
  CONSTRAINT `fk_app_grant_field_grant` FOREIGN KEY (`grant_id`) REFERENCES `app_grant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `published_by_account_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_version_number` (`application_id`, `version_number`),
  UNIQUE KEY `uk_app_version_revision` (`application_id`, `draft_revision`),
  UNIQUE KEY `uk_app_version_application_id` (`application_id`, `id`),
  CONSTRAINT `fk_app_version_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_version_publisher` FOREIGN KEY (`published_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `updated_by_account_id` BIGINT NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_publication_definition` (`application_id`),
  CONSTRAINT `fk_app_publication_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_publication_version` FOREIGN KEY (`application_id`, `current_version_id`) REFERENCES `app_version` (`application_id`, `id`),
  CONSTRAINT `fk_app_publication_updater` FOREIGN KEY (`updated_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `credential_version` INT NOT NULL,
  `client_id` VARCHAR(255) NOT NULL,
  `secret_hash` VARCHAR(255) NOT NULL,
  `secret_hint` VARCHAR(32) NOT NULL,
  `valid_from` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NULL,
  `status` VARCHAR(32) NOT NULL,
  `revoked_at` DATETIME(3) NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_credential_version` (`application_id`, `credential_version`),
  UNIQUE KEY `uk_app_credential_client` (`client_id`),
  KEY `idx_app_credential_active` (`application_id`, `status`, `valid_from`, `expires_at`),
  CONSTRAINT `fk_app_credential_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_credential_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_call` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `credential_version` INT NOT NULL,
  `grant_id` BIGINT NOT NULL,
  `request_id` VARCHAR(64) NOT NULL,
  `trace_id` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(255) NULL,
  `source_address` VARCHAR(255) NULL,
  `target_system_id` BIGINT NULL,
  `target_tenant_id` BIGINT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `resource_id` VARCHAR(100) NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `request_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `response_code` VARCHAR(64) NULL,
  `duration_millis` BIGINT NULL,
  `error_message` VARCHAR(2000) NULL,
  `called_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_call_request` (`application_id`, `request_id`),
  UNIQUE KEY `uk_app_call_idempotency` (`application_id`, `idempotency_key`),
  KEY `idx_app_call_target` (`target_system_id`, `target_tenant_id`, `called_at`),
  KEY `idx_app_call_status` (`application_id`, `status`, `called_at`),
  CONSTRAINT `fk_app_call_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_call_grant` FOREIGN KEY (`grant_id`) REFERENCES `app_grant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_callback_delivery` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `callback_id` BIGINT NOT NULL,
  `event_id` VARCHAR(100) NOT NULL,
  `payload_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME(3) NULL,
  `response_status` INT NULL,
  `response_body_excerpt` VARCHAR(2000) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_callback_event` (`callback_id`, `event_id`),
  KEY `idx_app_callback_retry` (`status`, `next_attempt_at`),
  CONSTRAINT `fk_app_callback_delivery_callback` FOREIGN KEY (`callback_id`) REFERENCES `app_callback` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_app_version_immutable_update`
BEFORE UPDATE ON `app_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'app_version is immutable';
END$$
CREATE TRIGGER `trg_app_version_immutable_delete`
BEFORE DELETE ON `app_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'app_version is immutable';
END$$
DELIMITER ;
