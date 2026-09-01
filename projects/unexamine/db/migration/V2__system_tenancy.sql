-- Initial baseline: platform information, isolated systems and tenant lifecycle.

CREATE TABLE `plat_platform` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `logo_file_id` BIGINT NULL,
  `contact_name` VARCHAR(100) NULL,
  `contact_email` VARCHAR(255) NULL,
  `settings_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_platform_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_system` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `logo_file_id` BIGINT NULL,
  `creator_account_id` BIGINT NOT NULL,
  `tenant_mode` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_system_code` (`platform_id`, `code`),
  UNIQUE KEY `uk_sys_system_id` (`id`, `platform_id`),
  KEY `idx_sys_system_creator` (`creator_account_id`),
  KEY `idx_sys_system_status` (`platform_id`, `status`, `deleted`),
  CONSTRAINT `fk_sys_system_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_sys_system_creator` FOREIGN KEY (`creator_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_system_domain` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `domain_type` VARCHAR(32) NOT NULL,
  `host` VARCHAR(255) NOT NULL,
  `base_path` VARCHAR(255) NOT NULL DEFAULT '/',
  `tls_required` TINYINT NOT NULL DEFAULT 1,
  `verified_at` DATETIME(3) NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_domain_host` (`host`, `base_path`),
  KEY `idx_sys_domain_system` (`system_id`, `status`),
  CONSTRAINT `fk_sys_domain_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_tenant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `is_main` TINYINT NOT NULL DEFAULT 0,
  `main_marker` VARCHAR(100) NULL,
  `creator_account_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_code` (`system_id`, `code`),
  UNIQUE KEY `uk_sys_tenant_main` (`system_id`, `main_marker`),
  UNIQUE KEY `uk_sys_tenant_system_id` (`system_id`, `id`),
  KEY `idx_sys_tenant_creator` (`creator_account_id`),
  CONSTRAINT `fk_sys_tenant_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_sys_tenant_creator` FOREIGN KEY (`creator_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_sys_tenant_main_marker` CHECK ((`is_main` = 1 AND `main_marker` = 'MAIN') OR (`is_main` = 0 AND `main_marker` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `employee_number` VARCHAR(100) NULL,
  `display_name` VARCHAR(100) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `joined_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_account` (`system_id`, `account_id`),
  UNIQUE KEY `uk_sys_member_number` (`system_id`, `employee_number`),
  UNIQUE KEY `uk_sys_member_system_id` (`system_id`, `id`),
  KEY `idx_sys_member_account` (`account_id`),
  CONSTRAINT `fk_sys_member_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_sys_member_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_access_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NULL,
  `account_id` BIGINT NOT NULL,
  `request_reason` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `decided_by_member_id` BIGINT NULL,
  `decision_comment` VARCHAR(1000) NULL,
  `decided_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_access_open` (`system_id`, `tenant_id`, `account_id`, `status`),
  KEY `idx_sys_access_queue` (`system_id`, `status`, `created_at`),
  CONSTRAINT `fk_sys_access_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_sys_access_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_sys_access_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_sys_access_decider` FOREIGN KEY (`system_id`, `decided_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_tenant_migration` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `from_mode` VARCHAR(32) NOT NULL,
  `to_mode` VARCHAR(32) NOT NULL,
  `impact_snapshot_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `job_id` BIGINT NULL,
  `requested_by_member_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `rollback_snapshot_json` JSON NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_sys_tenant_migration` (`system_id`, `status`, `created_at`),
  CONSTRAINT `fk_sys_tenant_migration_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_sys_tenant_migration_requester` FOREIGN KEY (`system_id`, `requested_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
