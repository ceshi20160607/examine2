-- Initial baseline: upload sessions, object metadata, business references and security scanning.

CREATE TABLE `file_storage_backend` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `backend_type` VARCHAR(32) NOT NULL,
  `endpoint` VARCHAR(1000) NULL,
  `bucket_name` VARCHAR(255) NOT NULL,
  `credential_ref` VARCHAR(255) NULL,
  `config_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_storage_backend` (`context_type`, `platform_id`, `system_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_upload_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `storage_backend_id` BIGINT NOT NULL,
  `uploader_account_id` BIGINT NOT NULL,
  `original_name` VARCHAR(500) NOT NULL,
  `content_type` VARCHAR(255) NOT NULL,
  `expected_size` BIGINT NOT NULL,
  `expected_sha256` VARCHAR(64) NULL,
  `object_key` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `upload_token_hash` VARCHAR(64) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `completed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_upload_token` (`upload_token_hash`),
  KEY `idx_file_upload_expiry` (`status`, `expires_at`),
  CONSTRAINT `fk_file_upload_backend` FOREIGN KEY (`storage_backend_id`) REFERENCES `file_storage_backend` (`id`),
  CONSTRAINT `fk_file_upload_uploader` FOREIGN KEY (`uploader_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_object` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `storage_backend_id` BIGINT NOT NULL,
  `upload_session_id` BIGINT NULL,
  `object_key` VARCHAR(1000) NOT NULL,
  `object_key_hash` VARCHAR(64) NOT NULL,
  `original_name` VARCHAR(500) NOT NULL,
  `content_type` VARCHAR(255) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `sha256` VARCHAR(64) NOT NULL,
  `scan_status` VARCHAR(32) NOT NULL,
  `preview_status` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `uploaded_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `deleted_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`storage_backend_id`, `object_key_hash`),
  UNIQUE KEY `uk_file_object_upload` (`upload_session_id`),
  KEY `idx_file_object_hash` (`sha256`, `status`),
  KEY `idx_file_object_context` (`system_id`, `tenant_id`, `created_at`),
  CONSTRAINT `fk_file_object_backend` FOREIGN KEY (`storage_backend_id`) REFERENCES `file_storage_backend` (`id`),
  CONSTRAINT `fk_file_object_upload` FOREIGN KEY (`upload_session_id`) REFERENCES `file_upload_session` (`id`),
  CONSTRAINT `fk_file_object_uploader` FOREIGN KEY (`uploaded_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_reference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_id` BIGINT NOT NULL,
  `context_type` VARCHAR(32) NOT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `owner_type` VARCHAR(64) NOT NULL,
  `owner_id` VARCHAR(100) NOT NULL,
  `field_code` VARCHAR(100) NULL,
  `reference_type` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_reference_owner` (`file_id`, `owner_type`, `owner_id`, `field_code`),
  KEY `idx_file_reference_lookup` (`owner_type`, `owner_id`),
  CONSTRAINT `fk_file_reference_object` FOREIGN KEY (`file_id`) REFERENCES `file_object` (`id`),
  CONSTRAINT `fk_file_reference_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `file_security_scan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_id` BIGINT NOT NULL,
  `scanner` VARCHAR(100) NOT NULL,
  `scan_version` VARCHAR(100) NULL,
  `status` VARCHAR(32) NOT NULL,
  `result_code` VARCHAR(100) NULL,
  `result_detail_json` JSON NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_file_scan_object` (`file_id`, `created_at`),
  CONSTRAINT `fk_file_scan_object` FOREIGN KEY (`file_id`) REFERENCES `file_object` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `plat_account`
  ADD CONSTRAINT `fk_plat_account_avatar` FOREIGN KEY (`avatar_file_id`) REFERENCES `file_object` (`id`);
ALTER TABLE `plat_platform`
  ADD CONSTRAINT `fk_plat_platform_logo` FOREIGN KEY (`logo_file_id`) REFERENCES `file_object` (`id`);
ALTER TABLE `sys_system`
  ADD CONSTRAINT `fk_sys_system_logo` FOREIGN KEY (`logo_file_id`) REFERENCES `file_object` (`id`);
ALTER TABLE `biz_record_value`
  ADD CONSTRAINT `fk_biz_record_value_file` FOREIGN KEY (`value_file_id`) REFERENCES `file_object` (`id`);
