-- Initial baseline: health, deployment, backup, upgrade, secret rotation and verification evidence.

CREATE TABLE `ops_secret_ref` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `secret_code` VARCHAR(100) NOT NULL,
  `provider` VARCHAR(64) NOT NULL,
  `reference_path` VARCHAR(1000) NOT NULL,
  `current_version` VARCHAR(100) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `last_verified_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_secret_ref` (`context_type`, `platform_id`, `system_id`, `secret_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_secret_rotation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `secret_ref_id` BIGINT NOT NULL,
  `from_version` VARCHAR(100) NOT NULL,
  `to_version` VARCHAR(100) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `verification_json` JSON NULL,
  `requested_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ops_secret_rotation` (`secret_ref_id`, `created_at`),
  CONSTRAINT `fk_ops_secret_rotation_ref` FOREIGN KEY (`secret_ref_id`) REFERENCES `ops_secret_ref` (`id`),
  CONSTRAINT `fk_ops_secret_rotation_actor` FOREIGN KEY (`requested_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_health_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `run_type` VARCHAR(32) NOT NULL,
  `release_version` VARCHAR(100) NULL,
  `status` VARCHAR(32) NOT NULL,
  `summary_json` JSON NULL,
  `started_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ops_health_context` (`system_id`, `status`, `started_at`),
  CONSTRAINT `fk_ops_health_actor` FOREIGN KEY (`started_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_health_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `health_run_id` BIGINT NOT NULL,
  `check_code` VARCHAR(100) NOT NULL,
  `check_name` VARCHAR(200) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `message` VARCHAR(2000) NULL,
  `metric_json` JSON NULL,
  `checked_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_health_item` (`health_run_id`, `check_code`),
  CONSTRAINT `fk_ops_health_item_run` FOREIGN KEY (`health_run_id`) REFERENCES `ops_health_run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_release` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `version_name` VARCHAR(100) NOT NULL,
  `artifact_hash` VARCHAR(64) NOT NULL,
  `database_version` VARCHAR(100) NOT NULL,
  `config_version` VARCHAR(100) NOT NULL,
  `compatibility_json` JSON NOT NULL,
  `release_notes` TEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_release_version` (`version_name`),
  UNIQUE KEY `uk_ops_release_hash` (`artifact_hash`),
  CONSTRAINT `fk_ops_release_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_deployment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `release_id` BIGINT NOT NULL,
  `environment_code` VARCHAR(100) NOT NULL,
  `deployment_type` VARCHAR(32) NOT NULL,
  `from_release_id` BIGINT NULL,
  `status` VARCHAR(32) NOT NULL,
  `step_state_json` JSON NOT NULL,
  `rollback_point_json` JSON NULL,
  `requested_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ops_deployment_env` (`environment_code`, `status`, `created_at`),
  CONSTRAINT `fk_ops_deployment_release` FOREIGN KEY (`release_id`) REFERENCES `ops_release` (`id`),
  CONSTRAINT `fk_ops_deployment_from_release` FOREIGN KEY (`from_release_id`) REFERENCES `ops_release` (`id`),
  CONSTRAINT `fk_ops_deployment_actor` FOREIGN KEY (`requested_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_backup` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `backup_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `manifest_hash` VARCHAR(64) NULL,
  `encryption_key_ref` VARCHAR(255) NOT NULL,
  `retention_until` DATETIME(3) NOT NULL,
  `requested_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ops_backup_context` (`system_id`, `status`, `created_at`),
  CONSTRAINT `fk_ops_backup_actor` FOREIGN KEY (`requested_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_backup_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `backup_id` BIGINT NOT NULL,
  `item_type` VARCHAR(32) NOT NULL,
  `storage_uri` VARCHAR(2000) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `sha256` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_backup_item` (`backup_id`, `item_type`, `storage_uri`(191)),
  CONSTRAINT `fk_ops_backup_item_backup` FOREIGN KEY (`backup_id`) REFERENCES `ops_backup` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_restore_drill` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `backup_id` BIGINT NOT NULL,
  `environment_code` VARCHAR(100) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `verification_json` JSON NULL,
  `requested_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ops_restore_backup` (`backup_id`, `created_at`),
  CONSTRAINT `fk_ops_restore_backup` FOREIGN KEY (`backup_id`) REFERENCES `ops_backup` (`id`),
  CONSTRAINT `fk_ops_restore_actor` FOREIGN KEY (`requested_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_upgrade` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `release_id` BIGINT NOT NULL,
  `backup_id` BIGINT NOT NULL,
  `context_type` VARCHAR(32) NOT NULL,
  `system_id` BIGINT NULL,
  `impact_report_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `rollback_point_json` JSON NOT NULL,
  `requested_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ops_upgrade_status` (`status`, `created_at`),
  CONSTRAINT `fk_ops_upgrade_release` FOREIGN KEY (`release_id`) REFERENCES `ops_release` (`id`),
  CONSTRAINT `fk_ops_upgrade_backup` FOREIGN KEY (`backup_id`) REFERENCES `ops_backup` (`id`),
  CONSTRAINT `fk_ops_upgrade_actor` FOREIGN KEY (`requested_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_upgrade_step` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `upgrade_id` BIGINT NOT NULL,
  `step_number` INT NOT NULL,
  `step_type` VARCHAR(32) NOT NULL,
  `source_version` VARCHAR(100) NOT NULL,
  `target_version` VARCHAR(100) NOT NULL,
  `mapping_json` JSON NULL,
  `status` VARCHAR(32) NOT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `error_message` VARCHAR(2000) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_upgrade_step` (`upgrade_id`, `step_number`),
  CONSTRAINT `fk_ops_upgrade_step_upgrade` FOREIGN KEY (`upgrade_id`) REFERENCES `ops_upgrade` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_verification_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `verification_type` VARCHAR(32) NOT NULL,
  `context_type` VARCHAR(32) NOT NULL,
  `system_id` BIGINT NULL,
  `release_id` BIGINT NULL,
  `scenario_code` VARCHAR(100) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `input_json` JSON NOT NULL,
  `result_json` JSON NULL,
  `threshold_json` JSON NOT NULL,
  `started_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ops_verification_type` (`verification_type`, `status`, `started_at`),
  CONSTRAINT `fk_ops_verification_release` FOREIGN KEY (`release_id`) REFERENCES `ops_release` (`id`),
  CONSTRAINT `fk_ops_verification_actor` FOREIGN KEY (`started_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ops_verification_finding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `verification_run_id` BIGINT NOT NULL,
  `severity` VARCHAR(32) NOT NULL,
  `finding_code` VARCHAR(100) NOT NULL,
  `title` VARCHAR(500) NOT NULL,
  `detail_text` TEXT NOT NULL,
  `evidence_json` JSON NULL,
  `status` VARCHAR(32) NOT NULL,
  `resolved_by_account_id` BIGINT NULL,
  `resolved_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ops_finding_run` (`verification_run_id`, `severity`, `status`),
  CONSTRAINT `fk_ops_finding_run` FOREIGN KEY (`verification_run_id`) REFERENCES `ops_verification_run` (`id`),
  CONSTRAINT `fk_ops_finding_resolver` FOREIGN KEY (`resolved_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
