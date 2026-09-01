-- Initial baseline: unified platform identity and account security.
SET NAMES utf8mb4;

CREATE TABLE `plat_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(100) NOT NULL,
  `email` VARCHAR(255) NULL,
  `mobile` VARCHAR(40) NULL,
  `display_name` VARCHAR(100) NOT NULL,
  `avatar_file_id` BIGINT NULL,
  `locale` VARCHAR(32) NOT NULL DEFAULT 'zh-CN',
  `timezone` VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  `status` VARCHAR(32) NOT NULL,
  `last_login_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_username` (`username`),
  UNIQUE KEY `uk_plat_account_email` (`email`),
  UNIQUE KEY `uk_plat_account_mobile` (`mobile`),
  KEY `idx_plat_account_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_account_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `account_id` BIGINT NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `password_algorithm` VARCHAR(32) NOT NULL DEFAULT 'ARGON2ID',
  `must_change_password` TINYINT NOT NULL DEFAULT 0,
  `failed_attempts` INT NOT NULL DEFAULT 0,
  `locked_until` DATETIME(3) NULL,
  `credential_version` INT NOT NULL DEFAULT 1,
  `changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_credential_account` (`account_id`),
  CONSTRAINT `fk_plat_credential_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_account_mfa` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `account_id` BIGINT NOT NULL,
  `method_type` VARCHAR(32) NOT NULL,
  `secret_ref` VARCHAR(255) NOT NULL,
  `display_label` VARCHAR(100) NOT NULL,
  `verified_at` DATETIME(3) NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_account_mfa` (`account_id`, `method_type`, `display_label`),
  CONSTRAINT `fk_plat_mfa_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_sso_provider` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `protocol` VARCHAR(32) NOT NULL,
  `issuer` VARCHAR(500) NOT NULL,
  `client_id` VARCHAR(255) NOT NULL,
  `client_secret_ref` VARCHAR(255) NULL,
  `metadata_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_sso_provider_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_sso_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `provider_id` BIGINT NOT NULL,
  `external_subject` VARCHAR(255) NOT NULL,
  `account_id` BIGINT NOT NULL,
  `attributes_json` JSON NULL,
  `last_login_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_sso_subject` (`provider_id`, `external_subject`),
  UNIQUE KEY `uk_plat_sso_account` (`provider_id`, `account_id`),
  CONSTRAINT `fk_plat_sso_identity_provider` FOREIGN KEY (`provider_id`) REFERENCES `plat_sso_provider` (`id`),
  CONSTRAINT `fk_plat_sso_identity_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_password_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `account_id` BIGINT NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `credential_version` INT NOT NULL,
  `changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_password_history` (`account_id`, `credential_version`),
  CONSTRAINT `fk_plat_password_history_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
