-- source: init/V1__identity_core.sql
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

-- source: init/V2__system_tenancy.sql
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

-- source: init/V3__authorization_context.sql
-- Initial baseline: organization, roles, action/data/field permissions and resolved sessions.

CREATE TABLE `plat_department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT NOT NULL,
  `parent_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `path_code` VARCHAR(1000) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_department_code` (`platform_id`, `code`),
  UNIQUE KEY `uk_plat_department_platform_id` (`platform_id`, `id`),
  KEY `idx_plat_department_parent` (`platform_id`, `parent_id`),
  CONSTRAINT `fk_plat_department_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_plat_department_parent` FOREIGN KEY (`platform_id`, `parent_id`) REFERENCES `plat_department` (`platform_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `department_id` BIGINT NULL,
  `title` VARCHAR(100) NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_account` (`platform_id`, `account_id`),
  UNIQUE KEY `uk_plat_member_platform_id` (`platform_id`, `id`),
  KEY `idx_plat_member_department` (`platform_id`, `department_id`),
  CONSTRAINT `fk_plat_member_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_plat_member_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_plat_member_department` FOREIGN KEY (`platform_id`, `department_id`) REFERENCES `plat_department` (`platform_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `built_in` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_code` (`platform_id`, `code`),
  UNIQUE KEY `uk_plat_role_platform_id` (`platform_id`, `id`),
  CONSTRAINT `fk_plat_role_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_member_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_member_role` (`member_id`, `role_id`),
  KEY `idx_plat_member_role_role` (`platform_id`, `role_id`),
  CONSTRAINT `fk_plat_member_role_member` FOREIGN KEY (`platform_id`, `member_id`) REFERENCES `plat_member` (`platform_id`, `id`),
  CONSTRAINT `fk_plat_member_role_role` FOREIGN KEY (`platform_id`, `role_id`) REFERENCES `plat_role` (`platform_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plat_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `resource_code` VARCHAR(150) NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `data_scope_type` VARCHAR(64) NOT NULL DEFAULT 'PLATFORM',
  `data_scope_json` JSON NULL,
  `effect` VARCHAR(16) NOT NULL DEFAULT 'ALLOW',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_role_permission` (`role_id`, `resource_type`, `resource_code`, `action_code`),
  CONSTRAINT `fk_plat_role_permission_role` FOREIGN KEY (`platform_id`, `role_id`) REFERENCES `plat_role` (`platform_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `parent_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `path_code` VARCHAR(1000) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_department_code` (`tenant_id`, `code`),
  UNIQUE KEY `uk_sys_department_tenant_id` (`tenant_id`, `id`),
  KEY `idx_sys_department_parent` (`tenant_id`, `parent_id`),
  KEY `idx_sys_department_system` (`system_id`),
  CONSTRAINT `fk_sys_department_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_sys_department_parent` FOREIGN KEY (`tenant_id`, `parent_id`) REFERENCES `sys_department` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_tenant_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `system_member_id` BIGINT NOT NULL,
  `department_id` BIGINT NULL,
  `tenant_admin` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_tenant_member` (`tenant_id`, `system_member_id`),
  UNIQUE KEY `uk_sys_tenant_member_tenant_id` (`tenant_id`, `id`),
  KEY `idx_sys_tenant_member_department` (`tenant_id`, `department_id`),
  CONSTRAINT `fk_sys_tenant_member_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_sys_tenant_member_member` FOREIGN KEY (`system_id`, `system_member_id`) REFERENCES `sys_member` (`system_id`, `id`),
  CONSTRAINT `fk_sys_tenant_member_department` FOREIGN KEY (`tenant_id`, `department_id`) REFERENCES `sys_department` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `built_in` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`tenant_id`, `code`),
  UNIQUE KEY `uk_sys_role_tenant_id` (`tenant_id`, `id`),
  KEY `idx_sys_role_system` (`system_id`),
  CONSTRAINT `fk_sys_role_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_member_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `tenant_member_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_role` (`tenant_member_id`, `role_id`),
  KEY `idx_sys_member_role_role` (`tenant_id`, `role_id`),
  CONSTRAINT `fk_sys_member_role_member` FOREIGN KEY (`tenant_id`, `tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_sys_member_role_role` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `sys_role` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sys_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `resource_code` VARCHAR(150) NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `data_scope_type` VARCHAR(64) NOT NULL,
  `data_scope_json` JSON NULL,
  `effect` VARCHAR(16) NOT NULL DEFAULT 'ALLOW',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`, `resource_type`, `resource_code`, `action_code`),
  KEY `idx_sys_permission_resource` (`tenant_id`, `resource_type`, `resource_code`),
  CONSTRAINT `fk_sys_role_permission_role` FOREIGN KEY (`tenant_id`, `role_id`) REFERENCES `sys_role` (`tenant_id`, `id`),
  CONSTRAINT `fk_sys_role_permission_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auth_field_policy` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `role_id` BIGINT NOT NULL,
  `resource_code` VARCHAR(150) NOT NULL,
  `field_code` VARCHAR(100) NOT NULL,
  `channel` VARCHAR(32) NOT NULL,
  `readable` TINYINT NOT NULL DEFAULT 1,
  `writable` TINYINT NOT NULL DEFAULT 0,
  `mask_strategy` VARCHAR(64) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_field_policy` (`context_type`, `role_id`, `resource_code`, `field_code`, `channel`),
  KEY `idx_auth_field_context` (`system_id`, `tenant_id`, `resource_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auth_permission_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `version_number` BIGINT NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `changed_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_permission_version` (`context_type`, `platform_id`, `system_id`, `tenant_id`, `version_number`),
  CONSTRAINT `fk_auth_permission_version_actor` FOREIGN KEY (`changed_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auth_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `access_token_hash` VARCHAR(64) NOT NULL,
  `refresh_token_hash` VARCHAR(64) NOT NULL,
  `account_id` BIGINT NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `member_id` BIGINT NULL,
  `tenant_member_id` BIGINT NULL,
  `role_ids_json` JSON NOT NULL,
  `action_permissions_json` JSON NOT NULL,
  `data_scopes_json` JSON NOT NULL,
  `permission_version` BIGINT NOT NULL DEFAULT 0,
  `mfa_level` VARCHAR(32) NOT NULL DEFAULT 'NONE',
  `access_expires_at` DATETIME(3) NOT NULL,
  `refresh_expires_at` DATETIME(3) NOT NULL,
  `revoked` TINYINT NOT NULL DEFAULT 0,
  `revoked_reason` VARCHAR(255) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_session_access_token` (`access_token_hash`),
  UNIQUE KEY `uk_auth_session_refresh_token` (`refresh_token_hash`),
  KEY `idx_auth_session_account` (`account_id`, `revoked`, `refresh_expires_at`),
  KEY `idx_auth_session_context` (`system_id`, `tenant_id`, `revoked`),
  CONSTRAINT `fk_auth_session_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_auth_session_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_auth_session_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_auth_session_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_auth_session_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `sys_member` (`system_id`, `id`),
  CONSTRAINT `fk_auth_session_tenant_member` FOREIGN KEY (`tenant_id`, `tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: init/V4__audit.sql
-- Initial baseline: append-only audit, field changes and permanent retention markers.

CREATE TABLE `audit_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trace_id` VARCHAR(64) NOT NULL,
  `request_id` VARCHAR(64) NULL,
  `context_type` VARCHAR(32) NOT NULL,
  `actor_account_id` BIGINT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `member_id` BIGINT NULL,
  `application_id` BIGINT NULL,
  `event_category` VARCHAR(64) NOT NULL,
  `event_code` VARCHAR(100) NOT NULL,
  `object_type` VARCHAR(100) NULL,
  `object_id` VARCHAR(100) NULL,
  `result_code` VARCHAR(64) NOT NULL,
  `client_ip` VARCHAR(64) NULL,
  `user_agent` VARCHAR(1000) NULL,
  `permission_snapshot` JSON NULL,
  `detail_json` JSON NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_trace` (`trace_id`),
  KEY `idx_audit_object` (`object_type`, `object_id`, `occurred_at`),
  KEY `idx_audit_context` (`system_id`, `tenant_id`, `occurred_at`),
  KEY `idx_audit_category` (`event_category`, `occurred_at`),
  CONSTRAINT `fk_audit_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_audit_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_audit_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_audit_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_audit_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_field_change` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `audit_event_id` BIGINT NOT NULL,
  `field_code` VARCHAR(100) NOT NULL,
  `value_type` VARCHAR(32) NOT NULL,
  `before_value_json` JSON NULL,
  `after_value_json` JSON NULL,
  `sensitivity` VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_field_event` (`audit_event_id`),
  KEY `idx_audit_field_code` (`field_code`, `created_at`),
  CONSTRAINT `fk_audit_field_event` FOREIGN KEY (`audit_event_id`) REFERENCES `audit_event` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_retention_marker` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `object_type` VARCHAR(100) NOT NULL,
  `object_id` VARCHAR(100) NOT NULL,
  `business_key` VARCHAR(255) NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NULL,
  `purge_reason` VARCHAR(1000) NOT NULL,
  `purged_by_account_id` BIGINT NOT NULL,
  `purged_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_retention_object` (`context_type`, `system_id`, `tenant_id`, `object_type`, `object_id`),
  KEY `idx_audit_retention_time` (`purged_at`),
  CONSTRAINT `fk_audit_retention_actor` FOREIGN KEY (`purged_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_audit_event_immutable_update`
BEFORE UPDATE ON `audit_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is immutable';
END$$
CREATE TRIGGER `trg_audit_event_immutable_delete`
BEFORE DELETE ON `audit_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is immutable';
END$$
CREATE TRIGGER `trg_audit_field_immutable_update`
BEFORE UPDATE ON `audit_field_change` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_field_change is immutable';
END$$
CREATE TRIGGER `trg_audit_field_immutable_delete`
BEFORE DELETE ON `audit_field_change` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_field_change is immutable';
END$$
CREATE TRIGGER `trg_audit_retention_immutable_update`
BEFORE UPDATE ON `audit_retention_marker` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_retention_marker is immutable';
END$$
CREATE TRIGGER `trg_audit_retention_immutable_delete`
BEFORE DELETE ON `audit_retention_marker` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_retention_marker is immutable';
END$$
DELIMITER ;

-- source: init/V5__platform_runtime_foundation.sql
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

-- source: init/V6__background_jobs.sql
-- Initial baseline: finite-retry background jobs with per-item results.

CREATE TABLE `job_background` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `job_type` VARCHAR(100) NOT NULL,
  `source_type` VARCHAR(100) NOT NULL,
  `source_id` VARCHAR(100) NULL,
  `parameter_json` JSON NOT NULL,
  `authorization_snapshot_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `progress_current` BIGINT NOT NULL DEFAULT 0,
  `progress_total` BIGINT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL DEFAULT 3,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_run_at` DATETIME(3) NULL,
  `heartbeat_at` DATETIME(3) NULL,
  `result_summary_json` JSON NULL,
  `error_code` VARCHAR(100) NULL,
  `error_message` VARCHAR(2000) NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_job_background_claim` (`status`, `next_run_at`, `created_at`),
  KEY `idx_job_background_context` (`system_id`, `tenant_id`, `job_type`, `created_at`),
  CONSTRAINT `fk_job_background_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `job_attempt` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `job_id` BIGINT NOT NULL,
  `attempt_number` INT NOT NULL,
  `worker_id` VARCHAR(100) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NULL,
  `error_code` VARCHAR(100) NULL,
  `error_message` VARCHAR(2000) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_attempt_number` (`job_id`, `attempt_number`),
  CONSTRAINT `fk_job_attempt_job` FOREIGN KEY (`job_id`) REFERENCES `job_background` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `job_item_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `job_id` BIGINT NOT NULL,
  `item_key` VARCHAR(255) NOT NULL,
  `row_number` BIGINT NULL,
  `status` VARCHAR(32) NOT NULL,
  `result_json` JSON NULL,
  `error_code` VARCHAR(100) NULL,
  `error_message` VARCHAR(2000) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_item_result` (`job_id`, `item_key`),
  KEY `idx_job_item_status` (`job_id`, `status`, `row_number`),
  CONSTRAINT `fk_job_item_result_job` FOREIGN KEY (`job_id`) REFERENCES `job_background` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: init/V7__lowcode_definition.sql
-- Initial baseline: metadata-only low-code definitions, draft publication and tenant extension.

CREATE TABLE `cfg_module_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `icon` VARCHAR(100) NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_group_code` (`owner_tenant_id`, `code`),
  UNIQUE KEY `uk_cfg_module_group_owner_id` (`owner_tenant_id`, `id`),
  KEY `idx_cfg_module_group_system` (`system_id`, `owner_tenant_id`, `sort_order`),
  CONSTRAINT `fk_cfg_module_group_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_group_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `group_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `title_field_code` VARCHAR(100) NULL,
  `number_sequence_code` VARCHAR(100) NULL,
  `status_field_code` VARCHAR(100) NULL,
  `status` VARCHAR(32) NOT NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_code` (`owner_tenant_id`, `code`),
  UNIQUE KEY `uk_cfg_module_system_id` (`system_id`, `id`),
  UNIQUE KEY `uk_cfg_module_owner_id` (`owner_tenant_id`, `id`),
  KEY `idx_cfg_module_group` (`group_id`, `status`),
  CONSTRAINT `fk_cfg_module_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_group` FOREIGN KEY (`owner_tenant_id`, `group_id`) REFERENCES `cfg_module_group` (`owner_tenant_id`, `id`),
  CONSTRAINT `fk_cfg_module_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_dictionary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `hierarchical` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_code` (`owner_tenant_id`, `code`),
  UNIQUE KEY `uk_cfg_dictionary_owner_id` (`owner_tenant_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_dictionary_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dictionary_id` BIGINT NOT NULL,
  `parent_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `label` VARCHAR(200) NOT NULL,
  `path_code` VARCHAR(1000) NOT NULL,
  `color` VARCHAR(32) NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_item_code` (`dictionary_id`, `code`),
  UNIQUE KEY `uk_cfg_dictionary_item_dict_id` (`dictionary_id`, `id`),
  KEY `idx_cfg_dictionary_item_parent` (`dictionary_id`, `parent_id`, `sort_order`),
  CONSTRAINT `fk_cfg_dictionary_item_dict` FOREIGN KEY (`dictionary_id`) REFERENCES `cfg_dictionary` (`id`),
  CONSTRAINT `fk_cfg_dictionary_item_parent` FOREIGN KEY (`dictionary_id`, `parent_id`) REFERENCES `cfg_dictionary_item` (`dictionary_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `field_type` VARCHAR(64) NOT NULL,
  `required` TINYINT NOT NULL DEFAULT 0,
  `unique_value` TINYINT NOT NULL DEFAULT 0,
  `searchable` TINYINT NOT NULL DEFAULT 0,
  `dictionary_id` BIGINT NULL,
  `reference_module_id` BIGINT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `config_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_field_code` (`module_id`, `code`),
  UNIQUE KEY `uk_cfg_module_field_module_id` (`module_id`, `id`),
  KEY `idx_cfg_module_field_order` (`module_id`, `status`, `sort_order`),
  CONSTRAINT `fk_cfg_module_field_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_field_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_field_dictionary` FOREIGN KEY (`dictionary_id`) REFERENCES `cfg_dictionary` (`id`),
  CONSTRAINT `fk_cfg_module_field_reference` FOREIGN KEY (`system_id`, `reference_module_id`) REFERENCES `cfg_module` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module_page` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `page_type` VARCHAR(32) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `layout_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_page_type` (`module_id`, `page_type`),
  CONSTRAINT `fk_cfg_module_page_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_page_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `parent_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `icon` VARCHAR(100) NULL,
  `route_path` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `visible` TINYINT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_menu_code` (`owner_tenant_id`, `code`),
  UNIQUE KEY `uk_cfg_module_menu_owner_id` (`owner_tenant_id`, `id`),
  KEY `idx_cfg_module_menu_parent` (`owner_tenant_id`, `parent_id`, `sort_order`),
  CONSTRAINT `fk_cfg_module_menu_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_menu_parent` FOREIGN KEY (`owner_tenant_id`, `parent_id`) REFERENCES `cfg_module_menu` (`owner_tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `action_type` VARCHAR(64) NOT NULL,
  `location` VARCHAR(64) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `config_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_action_code` (`module_id`, `code`),
  UNIQUE KEY `uk_cfg_module_action_module_id` (`module_id`, `id`),
  KEY `idx_cfg_module_action_location` (`module_id`, `location`, `sort_order`),
  CONSTRAINT `fk_cfg_module_action_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_action_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `rule_type` VARCHAR(64) NOT NULL,
  `trigger_event` VARCHAR(64) NOT NULL,
  `expression_text` TEXT NOT NULL,
  `message_template` VARCHAR(1000) NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_rule_code` (`module_id`, `code`),
  CONSTRAINT `fk_cfg_module_rule_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_rule_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_query_index` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `unique_index` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_query_index_code` (`module_id`, `code`),
  UNIQUE KEY `uk_cfg_query_index_module_id` (`module_id`, `id`),
  CONSTRAINT `fk_cfg_query_index_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_query_index_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `query_index_id` BIGINT NOT NULL,
  `field_id` BIGINT NOT NULL,
  `sort_order` INT NOT NULL,
  `sort_direction` VARCHAR(8) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_query_index_field` (`query_index_id`, `field_id`),
  UNIQUE KEY `uk_cfg_query_index_order` (`query_index_id`, `sort_order`),
  CONSTRAINT `fk_cfg_query_index_field_index` FOREIGN KEY (`query_index_id`) REFERENCES `cfg_query_index` (`id`),
  CONSTRAINT `fk_cfg_query_index_field_field` FOREIGN KEY (`field_id`) REFERENCES `cfg_module_field` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `schema_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `change_summary` VARCHAR(1000) NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_version_number` (`module_id`, `version_number`),
  UNIQUE KEY `uk_cfg_module_version_revision` (`module_id`, `draft_revision`),
  UNIQUE KEY `uk_cfg_module_version_module_id` (`module_id`, `id`),
  KEY `idx_cfg_module_version_tenant` (`system_id`, `owner_tenant_id`, `published_at`),
  CONSTRAINT `fk_cfg_module_version_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_version_publisher` FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_module_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `updated_by_member_id` BIGINT NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_module_publication_module` (`module_id`),
  KEY `idx_cfg_module_publication_tenant` (`system_id`, `owner_tenant_id`),
  CONSTRAINT `fk_cfg_module_publication_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_module_publication_version` FOREIGN KEY (`module_id`, `current_version_id`) REFERENCES `cfg_module_version` (`module_id`, `id`),
  CONSTRAINT `fk_cfg_module_publication_updater` FOREIGN KEY (`system_id`, `updated_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_tenant_extension` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `base_module_id` BIGINT NOT NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `extension_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_tenant_extension` (`tenant_id`, `base_module_id`),
  CONSTRAINT `fk_cfg_tenant_extension_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_tenant_extension_module` FOREIGN KEY (`system_id`, `base_module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_cfg_tenant_extension_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_tenant_extension_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `extension_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `base_module_version_id` BIGINT NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `schema_hash` VARCHAR(64) NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_extension_version` (`extension_id`, `version_number`),
  CONSTRAINT `fk_cfg_extension_version_extension` FOREIGN KEY (`extension_id`) REFERENCES `cfg_tenant_extension` (`id`),
  CONSTRAINT `fk_cfg_extension_version_base` FOREIGN KEY (`base_module_version_id`) REFERENCES `cfg_module_version` (`id`),
  CONSTRAINT `fk_cfg_extension_version_publisher` FOREIGN KEY (`published_by_member_id`) REFERENCES `sys_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_cfg_module_version_immutable_update`
BEFORE UPDATE ON `cfg_module_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cfg_module_version is immutable';
END$$
CREATE TRIGGER `trg_cfg_module_version_immutable_delete`
BEFORE DELETE ON `cfg_module_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cfg_module_version is immutable';
END$$
CREATE TRIGGER `trg_cfg_extension_version_immutable_update`
BEFORE UPDATE ON `cfg_tenant_extension_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cfg_tenant_extension_version is immutable';
END$$
CREATE TRIGGER `trg_cfg_extension_version_immutable_delete`
BEFORE DELETE ON `cfg_tenant_extension_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cfg_tenant_extension_version is immutable';
END$$
DELIMITER ;

-- source: init/V8__record_runtime.sql
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

-- source: init/V9__flow.sql
-- Initial baseline: visual Flow definition, immutable versions, binding and runtime execution.

CREATE TABLE `flow_definition` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `owner_tenant_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_definition_code` (`context_type`, `platform_id`, `system_id`, `owner_tenant_id`, `code`),
  UNIQUE KEY `uk_flow_definition_context_id` (`context_type`, `id`),
  KEY `idx_flow_definition_context` (`system_id`, `owner_tenant_id`, `status`),
  CONSTRAINT `fk_flow_definition_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `flow_id` BIGINT NOT NULL,
  `node_key` VARCHAR(100) NOT NULL,
  `node_type` VARCHAR(64) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `position_x` DECIMAL(12, 3) NOT NULL,
  `position_y` DECIMAL(12, 3) NOT NULL,
  `assignee_policy_json` JSON NULL,
  `form_policy_json` JSON NULL,
  `timeout_policy_json` JSON NULL,
  `exception_policy_json` JSON NULL,
  `config_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_key` (`flow_id`, `node_key`),
  CONSTRAINT `fk_flow_node_definition` FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_edge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `flow_id` BIGINT NOT NULL,
  `edge_key` VARCHAR(100) NOT NULL,
  `source_node_key` VARCHAR(100) NOT NULL,
  `target_node_key` VARCHAR(100) NOT NULL,
  `condition_expression` TEXT NULL,
  `priority_order` INT NOT NULL DEFAULT 0,
  `config_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_edge_key` (`flow_id`, `edge_key`),
  KEY `idx_flow_edge_source` (`flow_id`, `source_node_key`, `priority_order`),
  CONSTRAINT `fk_flow_edge_definition` FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `flow_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `definition_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `simulation_result_json` JSON NOT NULL,
  `change_summary` VARCHAR(1000) NOT NULL,
  `published_by_account_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_version_number` (`flow_id`, `version_number`),
  UNIQUE KEY `uk_flow_version_revision` (`flow_id`, `draft_revision`),
  UNIQUE KEY `uk_flow_version_flow_id` (`flow_id`, `id`),
  CONSTRAINT `fk_flow_version_definition` FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`),
  CONSTRAINT `fk_flow_version_publisher` FOREIGN KEY (`published_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `flow_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `updated_by_account_id` BIGINT NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_publication_flow` (`flow_id`),
  CONSTRAINT `fk_flow_publication_definition` FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`),
  CONSTRAINT `fk_flow_publication_version` FOREIGN KEY (`flow_id`, `current_version_id`) REFERENCES `flow_version` (`flow_id`, `id`),
  CONSTRAINT `fk_flow_publication_updater` FOREIGN KEY (`updated_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_trigger_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `action_id` BIGINT NULL,
  `trigger_event` VARCHAR(64) NOT NULL,
  `flow_id` BIGINT NOT NULL,
  `priority_order` INT NOT NULL DEFAULT 0,
  `condition_expression` TEXT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_trigger_binding` (`owner_tenant_id`, `module_id`, `trigger_event`, `priority_order`),
  KEY `idx_flow_binding_flow` (`flow_id`, `status`),
  CONSTRAINT `fk_flow_binding_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_flow_binding_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_flow_binding_action` FOREIGN KEY (`action_id`) REFERENCES `cfg_module_action` (`id`),
  CONSTRAINT `fk_flow_binding_definition` FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_binding_resolution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `trigger_event` VARCHAR(64) NOT NULL,
  `binding_id` BIGINT NOT NULL,
  `flow_version_id` BIGINT NOT NULL,
  `resolution_reason` VARCHAR(500) NOT NULL,
  `resolved_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_flow_resolution_lookup` (`tenant_id`, `module_id`, `trigger_event`, `resolved_at`),
  CONSTRAINT `fk_flow_resolution_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_flow_resolution_binding` FOREIGN KEY (`binding_id`) REFERENCES `flow_trigger_binding` (`id`),
  CONSTRAINT `fk_flow_resolution_version` FOREIGN KEY (`flow_version_id`) REFERENCES `flow_version` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_instance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `flow_id` BIGINT NOT NULL,
  `flow_version_id` BIGINT NOT NULL,
  `business_type` VARCHAR(100) NULL,
  `business_id` VARCHAR(100) NULL,
  `business_snapshot_json` JSON NULL,
  `title` VARCHAR(500) NOT NULL,
  `current_node_key` VARCHAR(100) NULL,
  `status` VARCHAR(32) NOT NULL,
  `started_by_account_id` BIGINT NOT NULL,
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  `error_code` VARCHAR(100) NULL,
  `error_message` VARCHAR(2000) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_flow_instance_context` (`system_id`, `tenant_id`, `status`, `started_at`),
  KEY `idx_flow_instance_business` (`business_type`, `business_id`),
  CONSTRAINT `fk_flow_instance_definition` FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`),
  CONSTRAINT `fk_flow_instance_version` FOREIGN KEY (`flow_version_id`) REFERENCES `flow_version` (`id`),
  CONSTRAINT `fk_flow_instance_starter` FOREIGN KEY (`started_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_instance_variable` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `variable_key` VARCHAR(100) NOT NULL,
  `value_type` VARCHAR(32) NOT NULL,
  `value_json` JSON NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_instance_variable` (`instance_id`, `variable_key`),
  CONSTRAINT `fk_flow_variable_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `node_key` VARCHAR(100) NOT NULL,
  `task_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `assignee_account_id` BIGINT NULL,
  `assignee_snapshot_json` JSON NOT NULL,
  `due_at` DATETIME(3) NULL,
  `claimed_at` DATETIME(3) NULL,
  `completed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_task_node` (`instance_id`, `node_key`, `id`),
  KEY `idx_flow_task_assignee` (`assignee_account_id`, `status`, `due_at`),
  CONSTRAINT `fk_flow_task_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_task_assignee` FOREIGN KEY (`assignee_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_task_candidate` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `candidate_type` VARCHAR(32) NOT NULL,
  `candidate_id` VARCHAR(100) NOT NULL,
  `resolution_reason` VARCHAR(500) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_task_candidate` (`task_id`, `candidate_type`, `candidate_id`),
  CONSTRAINT `fk_flow_candidate_task` FOREIGN KEY (`task_id`) REFERENCES `flow_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_action` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `task_id` BIGINT NULL,
  `node_key` VARCHAR(100) NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `comment_text` VARCHAR(2000) NULL,
  `input_json` JSON NULL,
  `result_json` JSON NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `acted_by_account_id` BIGINT NOT NULL,
  `acted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_action_idempotency` (`instance_id`, `idempotency_key`),
  KEY `idx_flow_action_instance` (`instance_id`, `acted_at`),
  CONSTRAINT `fk_flow_action_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_action_task` FOREIGN KEY (`task_id`) REFERENCES `flow_task` (`id`),
  CONSTRAINT `fk_flow_action_actor` FOREIGN KEY (`acted_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_exception` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `node_key` VARCHAR(100) NULL,
  `exception_type` VARCHAR(64) NOT NULL,
  `error_code` VARCHAR(100) NOT NULL,
  `error_message` VARCHAR(2000) NOT NULL,
  `policy_action` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `resolved_by_account_id` BIGINT NULL,
  `resolution_comment` VARCHAR(2000) NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `resolved_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_flow_exception_instance` (`instance_id`, `status`, `occurred_at`),
  CONSTRAINT `fk_flow_exception_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_exception_resolver` FOREIGN KEY (`resolved_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_flow_version_immutable_update`
BEFORE UPDATE ON `flow_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_version is immutable';
END$$
CREATE TRIGGER `trg_flow_version_immutable_delete`
BEFORE DELETE ON `flow_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_version is immutable';
END$$
DELIMITER ;

-- source: init/V10__application_bridge.sql
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

-- source: init/V11__work_management.sql
-- Initial baseline: ordinary tasks, project tasks and manually authored daily work logs.

CREATE TABLE `work_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(2000) NULL,
  `owner_account_id` BIGINT NOT NULL,
  `start_date` DATE NULL,
  `due_date` DATE NULL,
  `progress_percent` DECIMAL(5, 2) NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_project_code` (`context_type`, `platform_id`, `system_id`, `tenant_id`, `code`),
  KEY `idx_work_project_owner` (`owner_account_id`, `status`, `due_date`),
  CONSTRAINT `fk_work_project_owner` FOREIGN KEY (`owner_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_project_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `project_role` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `joined_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_project_member` (`project_id`, `account_id`),
  CONSTRAINT `fk_work_project_member_project` FOREIGN KEY (`project_id`) REFERENCES `work_project` (`id`),
  CONSTRAINT `fk_work_project_member_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_task_group` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_task_group_order` (`project_id`, `sort_order`),
  CONSTRAINT `fk_work_task_group_project` FOREIGN KEY (`project_id`) REFERENCES `work_project` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `project_id` BIGINT NULL,
  `task_group_id` BIGINT NULL,
  `parent_task_id` BIGINT NULL,
  `title` VARCHAR(500) NOT NULL,
  `description` TEXT NULL,
  `task_type` VARCHAR(32) NOT NULL,
  `priority` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `progress_percent` DECIMAL(5, 2) NOT NULL DEFAULT 0,
  `owner_account_id` BIGINT NOT NULL,
  `start_at` DATETIME(3) NULL,
  `due_at` DATETIME(3) NULL,
  `completed_at` DATETIME(3) NULL,
  `custom_values_json` JSON NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_task_context_id` (`context_type`, `id`),
  KEY `idx_work_task_owner` (`owner_account_id`, `status`, `due_at`),
  KEY `idx_work_task_project` (`project_id`, `task_group_id`, `status`),
  KEY `idx_work_task_context` (`system_id`, `tenant_id`, `status`, `updated_at`),
  CONSTRAINT `fk_work_task_project` FOREIGN KEY (`project_id`) REFERENCES `work_project` (`id`),
  CONSTRAINT `fk_work_task_group` FOREIGN KEY (`task_group_id`) REFERENCES `work_task_group` (`id`),
  CONSTRAINT `fk_work_task_parent` FOREIGN KEY (`parent_task_id`) REFERENCES `work_task` (`id`),
  CONSTRAINT `fk_work_task_owner` FOREIGN KEY (`owner_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_work_task_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_task_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `member_type` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_task_member` (`task_id`, `account_id`, `member_type`),
  KEY `idx_work_task_member_account` (`account_id`, `task_id`),
  CONSTRAINT `fk_work_task_member_task` FOREIGN KEY (`task_id`) REFERENCES `work_task` (`id`),
  CONSTRAINT `fk_work_task_member_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_task_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `before_json` JSON NULL,
  `after_json` JSON NOT NULL,
  `comment_text` VARCHAR(2000) NULL,
  `changed_by_account_id` BIGINT NOT NULL,
  `changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_work_task_history` (`task_id`, `changed_at`),
  CONSTRAINT `fk_work_task_history_task` FOREIGN KEY (`task_id`) REFERENCES `work_task` (`id`),
  CONSTRAINT `fk_work_task_history_actor` FOREIGN KEY (`changed_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `author_account_id` BIGINT NOT NULL,
  `work_date` DATE NOT NULL,
  `title` VARCHAR(500) NOT NULL,
  `content_text` TEXT NOT NULL,
  `duration_minutes` INT NULL,
  `status` VARCHAR(32) NOT NULL,
  `custom_values_json` JSON NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_work_log_author_date` (`author_account_id`, `work_date`),
  KEY `idx_work_log_context` (`system_id`, `tenant_id`, `work_date`),
  CONSTRAINT `fk_work_log_author` FOREIGN KEY (`author_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_log_revision` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_log_id` BIGINT NOT NULL,
  `revision_number` INT NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `revision_reason` VARCHAR(1000) NOT NULL,
  `revised_by_account_id` BIGINT NOT NULL,
  `revised_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_revision` (`work_log_id`, `revision_number`),
  CONSTRAINT `fk_work_log_revision_log` FOREIGN KEY (`work_log_id`) REFERENCES `work_log` (`id`),
  CONSTRAINT `fk_work_log_revision_actor` FOREIGN KEY (`revised_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `work_field_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `target_type` VARCHAR(32) NOT NULL,
  `field_code` VARCHAR(100) NOT NULL,
  `field_name` VARCHAR(200) NOT NULL,
  `field_type` VARCHAR(64) NOT NULL,
  `required` TINYINT NOT NULL DEFAULT 0,
  `sort_order` INT NOT NULL DEFAULT 0,
  `config_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_field_config` (`context_type`, `platform_id`, `system_id`, `tenant_id`, `target_type`, `field_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: init/V12__notification.sql
-- Initial baseline: actionable todo projection, in-app messages and channel deliveries.

CREATE TABLE `todo_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `assignee_account_id` BIGINT NOT NULL,
  `todo_type` VARCHAR(64) NOT NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `source_id` VARCHAR(100) NOT NULL,
  `title` VARCHAR(500) NOT NULL,
  `summary` VARCHAR(1000) NULL,
  `target_route` VARCHAR(1000) NOT NULL,
  `priority` VARCHAR(32) NOT NULL,
  `due_at` DATETIME(3) NULL,
  `status` VARCHAR(32) NOT NULL,
  `completed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_todo_source_assignee` (`assignee_account_id`, `source_type`, `source_id`, `todo_type`),
  KEY `idx_todo_assignee` (`assignee_account_id`, `status`, `due_at`, `created_at`),
  KEY `idx_todo_context` (`system_id`, `tenant_id`, `status`),
  CONSTRAINT `fk_todo_assignee` FOREIGN KEY (`assignee_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `channel` VARCHAR(32) NOT NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `subject_template` VARCHAR(500) NULL,
  `content_template` TEXT NOT NULL,
  `variable_schema_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_template_code` (`context_type`, `platform_id`, `system_id`, `tenant_id`, `code`, `channel`),
  CONSTRAINT `fk_msg_template_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_template_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `published_by_account_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_template_version` (`template_id`, `version_number`),
  UNIQUE KEY `uk_msg_template_revision` (`template_id`, `draft_revision`),
  CONSTRAINT `fk_msg_template_version_template` FOREIGN KEY (`template_id`) REFERENCES `msg_template` (`id`),
  CONSTRAINT `fk_msg_template_version_publisher` FOREIGN KEY (`published_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `template_version_id` BIGINT NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `source_id` VARCHAR(100) NULL,
  `subject` VARCHAR(500) NOT NULL,
  `content_text` TEXT NOT NULL,
  `target_type` VARCHAR(64) NULL,
  `target_id` VARCHAR(100) NULL,
  `target_route` VARCHAR(1000) NULL,
  `sensitivity` VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  `created_by_account_id` BIGINT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_msg_message_context` (`system_id`, `tenant_id`, `created_at`),
  CONSTRAINT `fk_msg_message_template_version` FOREIGN KEY (`template_version_id`) REFERENCES `msg_template_version` (`id`),
  CONSTRAINT `fk_msg_message_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_recipient` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `message_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'UNREAD',
  `read_at` DATETIME(3) NULL,
  `archived_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_recipient` (`message_id`, `account_id`),
  KEY `idx_msg_recipient_inbox` (`account_id`, `status`, `created_at`),
  CONSTRAINT `fk_msg_recipient_message` FOREIGN KEY (`message_id`) REFERENCES `msg_message` (`id`),
  CONSTRAINT `fk_msg_recipient_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_delivery` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `message_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `channel` VARCHAR(32) NOT NULL,
  `destination_masked` VARCHAR(255) NULL,
  `status` VARCHAR(32) NOT NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME(3) NULL,
  `provider_message_id` VARCHAR(255) NULL,
  `provider_receipt_json` JSON NULL,
  `last_error` VARCHAR(2000) NULL,
  `sent_at` DATETIME(3) NULL,
  `delivered_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_delivery_channel` (`message_id`, `account_id`, `channel`),
  KEY `idx_msg_delivery_retry` (`status`, `next_attempt_at`),
  CONSTRAINT `fk_msg_delivery_message` FOREIGN KEY (`message_id`) REFERENCES `msg_message` (`id`),
  CONSTRAINT `fk_msg_delivery_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `msg_user_preference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `account_id` BIGINT NOT NULL,
  `context_type` VARCHAR(32) NOT NULL,
  `system_id` BIGINT NULL,
  `event_code` VARCHAR(100) NOT NULL,
  `channel` VARCHAR(32) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `quiet_hours_json` JSON NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_user_preference` (`account_id`, `context_type`, `system_id`, `event_code`, `channel`),
  CONSTRAINT `fk_msg_user_preference_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: init/V13__ai.sql
-- Initial baseline: platform AI models, system Agent policy and human-confirmed execution.

CREATE TABLE `ai_model` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `platform_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `provider` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(200) NOT NULL,
  `endpoint_url` VARCHAR(1000) NULL,
  `credential_ref` VARCHAR(255) NOT NULL,
  `capabilities_json` JSON NOT NULL,
  `limit_policy_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_code` (`platform_id`, `code`),
  CONSTRAINT `fk_ai_model_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_system_model_grant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `model_id` BIGINT NOT NULL,
  `system_id` BIGINT NOT NULL,
  `usage_limit_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `granted_by_account_id` BIGINT NOT NULL,
  `granted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `revoked_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_system_model_grant` (`model_id`, `system_id`),
  CONSTRAINT `fk_ai_model_grant_model` FOREIGN KEY (`model_id`) REFERENCES `ai_model` (`id`),
  CONSTRAINT `fk_ai_model_grant_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_ai_model_grant_actor` FOREIGN KEY (`granted_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_agent` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `model_grant_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `system_prompt_text` TEXT NOT NULL,
  `context_policy_json` JSON NOT NULL,
  `confirmation_policy_json` JSON NOT NULL,
  `fallback_policy_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_code` (`owner_tenant_id`, `code`),
  CONSTRAINT `fk_ai_agent_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_ai_agent_model_grant` FOREIGN KEY (`model_grant_id`) REFERENCES `ai_system_model_grant` (`id`),
  CONSTRAINT `fk_ai_agent_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_agent_tool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `agent_id` BIGINT NOT NULL,
  `tool_type` VARCHAR(64) NOT NULL,
  `resource_type` VARCHAR(64) NOT NULL,
  `resource_id` VARCHAR(100) NOT NULL,
  `action_code` VARCHAR(64) NOT NULL,
  `field_scope_json` JSON NOT NULL,
  `data_scope_json` JSON NOT NULL,
  `requires_confirmation` TINYINT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_tool` (`agent_id`, `tool_type`, `resource_type`, `resource_id`, `action_code`),
  CONSTRAINT `fk_ai_agent_tool_agent` FOREIGN KEY (`agent_id`) REFERENCES `ai_agent` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_agent_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `agent_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_version` (`agent_id`, `version_number`),
  UNIQUE KEY `uk_ai_agent_revision` (`agent_id`, `draft_revision`),
  UNIQUE KEY `uk_ai_agent_version_agent_id` (`agent_id`, `id`),
  CONSTRAINT `fk_ai_agent_version_agent` FOREIGN KEY (`agent_id`) REFERENCES `ai_agent` (`id`),
  CONSTRAINT `fk_ai_agent_version_publisher` FOREIGN KEY (`published_by_member_id`) REFERENCES `sys_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_agent_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `agent_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `updated_by_member_id` BIGINT NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_publication` (`agent_id`),
  CONSTRAINT `fk_ai_agent_publication_agent` FOREIGN KEY (`agent_id`) REFERENCES `ai_agent` (`id`),
  CONSTRAINT `fk_ai_agent_publication_version` FOREIGN KEY (`agent_id`, `current_version_id`) REFERENCES `ai_agent_version` (`agent_id`, `id`),
  CONSTRAINT `fk_ai_agent_publication_actor` FOREIGN KEY (`updated_by_member_id`) REFERENCES `sys_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `agent_id` BIGINT NOT NULL,
  `agent_version_id` BIGINT NOT NULL,
  `account_id` BIGINT NOT NULL,
  `entry_context_json` JSON NOT NULL,
  `title` VARCHAR(500) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ai_conversation_user` (`account_id`, `status`, `updated_at`),
  KEY `idx_ai_conversation_context` (`system_id`, `tenant_id`, `updated_at`),
  CONSTRAINT `fk_ai_conversation_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_ai_conversation_agent` FOREIGN KEY (`agent_id`) REFERENCES `ai_agent` (`id`),
  CONSTRAINT `fk_ai_conversation_version` FOREIGN KEY (`agent_version_id`) REFERENCES `ai_agent_version` (`id`),
  CONSTRAINT `fk_ai_conversation_account` FOREIGN KEY (`account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT NOT NULL,
  `role_type` VARCHAR(32) NOT NULL,
  `content_text` LONGTEXT NOT NULL,
  `structured_content_json` JSON NULL,
  `model_usage_json` JSON NULL,
  `error_code` VARCHAR(100) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ai_message_conversation` (`conversation_id`, `created_at`),
  CONSTRAINT `fk_ai_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT NOT NULL,
  `request_message_id` BIGINT NOT NULL,
  `agent_version_id` BIGINT NOT NULL,
  `authorization_snapshot_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `fallback_used` TINYINT NOT NULL DEFAULT 0,
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  `error_code` VARCHAR(100) NULL,
  `error_message` VARCHAR(2000) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ai_execution_conversation` (`conversation_id`, `started_at`),
  CONSTRAINT `fk_ai_execution_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `ai_conversation` (`id`),
  CONSTRAINT `fk_ai_execution_message` FOREIGN KEY (`request_message_id`) REFERENCES `ai_message` (`id`),
  CONSTRAINT `fk_ai_execution_agent_version` FOREIGN KEY (`agent_version_id`) REFERENCES `ai_agent_version` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_execution_step` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `execution_id` BIGINT NOT NULL,
  `step_number` INT NOT NULL,
  `step_type` VARCHAR(32) NOT NULL,
  `tool_id` BIGINT NULL,
  `input_json` JSON NULL,
  `output_json` JSON NULL,
  `status` VARCHAR(32) NOT NULL,
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  `error_message` VARCHAR(2000) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_execution_step` (`execution_id`, `step_number`),
  CONSTRAINT `fk_ai_execution_step_execution` FOREIGN KEY (`execution_id`) REFERENCES `ai_execution` (`id`),
  CONSTRAINT `fk_ai_execution_step_tool` FOREIGN KEY (`tool_id`) REFERENCES `ai_agent_tool` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_pending_write` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `execution_id` BIGINT NOT NULL,
  `step_id` BIGINT NOT NULL,
  `target_type` VARCHAR(64) NOT NULL,
  `target_id` VARCHAR(100) NULL,
  `proposed_payload_json` JSON NOT NULL,
  `preview_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `confirmed_by_account_id` BIGINT NULL,
  `confirmed_at` DATETIME(3) NULL,
  `result_json` JSON NULL,
  `error_message` VARCHAR(2000) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ai_pending_write_status` (`status`, `created_at`),
  CONSTRAINT `fk_ai_pending_write_execution` FOREIGN KEY (`execution_id`) REFERENCES `ai_execution` (`id`),
  CONSTRAINT `fk_ai_pending_write_step` FOREIGN KEY (`step_id`) REFERENCES `ai_execution_step` (`id`),
  CONSTRAINT `fk_ai_pending_write_confirmer` FOREIGN KEY (`confirmed_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_ai_agent_version_immutable_update`
BEFORE UPDATE ON `ai_agent_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_agent_version is immutable';
END$$
CREATE TRIGGER `trg_ai_agent_version_immutable_delete`
BEFORE DELETE ON `ai_agent_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_agent_version is immutable';
END$$
DELIMITER ;

-- source: init/V14__file_storage.sql
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

-- source: init/V15__data_exchange.sql
-- Initial baseline: module actions for import preview/execution and permission-scoped export.

CREATE TABLE `exchange_mapping` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `mapping_type` VARCHAR(32) NOT NULL,
  `column_mapping_json` JSON NOT NULL,
  `validation_policy_json` JSON NOT NULL,
  `conflict_policy_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_mapping_code` (`owner_tenant_id`, `module_id`, `code`),
  CONSTRAINT `fk_exchange_mapping_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_exchange_mapping_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_exchange_mapping_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exchange_import_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `mapping_id` BIGINT NOT NULL,
  `source_file_id` BIGINT NOT NULL,
  `job_id` BIGINT NULL,
  `mode` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `total_rows` BIGINT NOT NULL DEFAULT 0,
  `valid_rows` BIGINT NOT NULL DEFAULT 0,
  `success_rows` BIGINT NOT NULL DEFAULT 0,
  `failed_rows` BIGINT NOT NULL DEFAULT 0,
  `authorization_snapshot_json` JSON NOT NULL,
  `summary_json` JSON NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_exchange_import_context` (`tenant_id`, `module_id`, `created_at`),
  KEY `idx_exchange_import_status` (`status`, `created_at`),
  CONSTRAINT `fk_exchange_import_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_exchange_import_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_exchange_import_mapping` FOREIGN KEY (`mapping_id`) REFERENCES `exchange_mapping` (`id`),
  CONSTRAINT `fk_exchange_import_file` FOREIGN KEY (`source_file_id`) REFERENCES `file_object` (`id`),
  CONSTRAINT `fk_exchange_import_job` FOREIGN KEY (`job_id`) REFERENCES `job_background` (`id`),
  CONSTRAINT `fk_exchange_import_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exchange_import_row` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `batch_id` BIGINT NOT NULL,
  `row_number` BIGINT NOT NULL,
  `raw_json` JSON NOT NULL,
  `normalized_json` JSON NULL,
  `status` VARCHAR(32) NOT NULL,
  `target_record_id` BIGINT NULL,
  `error_json` JSON NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_import_row` (`batch_id`, `row_number`),
  KEY `idx_exchange_import_row_status` (`batch_id`, `status`, `row_number`),
  CONSTRAINT `fk_exchange_import_row_batch` FOREIGN KEY (`batch_id`) REFERENCES `exchange_import_batch` (`id`),
  CONSTRAINT `fk_exchange_import_row_record` FOREIGN KEY (`target_record_id`) REFERENCES `biz_record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `exchange_export_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `mapping_id` BIGINT NULL,
  `job_id` BIGINT NULL,
  `filter_snapshot_json` JSON NOT NULL,
  `authorization_snapshot_json` JSON NOT NULL,
  `selected_fields_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `total_rows` BIGINT NOT NULL DEFAULT 0,
  `exported_rows` BIGINT NOT NULL DEFAULT 0,
  `result_file_id` BIGINT NULL,
  `error_message` VARCHAR(2000) NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `started_at` DATETIME(3) NULL,
  `finished_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_exchange_export_context` (`tenant_id`, `module_id`, `created_at`),
  KEY `idx_exchange_export_status` (`status`, `created_at`),
  CONSTRAINT `fk_exchange_export_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_exchange_export_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_exchange_export_mapping` FOREIGN KEY (`mapping_id`) REFERENCES `exchange_mapping` (`id`),
  CONSTRAINT `fk_exchange_export_job` FOREIGN KEY (`job_id`) REFERENCES `job_background` (`id`),
  CONSTRAINT `fk_exchange_export_file` FOREIGN KEY (`result_file_id`) REFERENCES `file_object` (`id`),
  CONSTRAINT `fk_exchange_export_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: init/V16__analytics.sql
-- Initial baseline: versioned data sources, dashboards, KPIs and reminder closure.

CREATE TABLE `ana_data_source` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `owner_tenant_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `definition_json` JSON NOT NULL,
  `permission_policy_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_data_source_code` (`context_type`, `platform_id`, `system_id`, `owner_tenant_id`, `code`),
  CONSTRAINT `fk_ana_data_source_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_data_source_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `data_source_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `definition_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `published_by_account_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_source_version` (`data_source_id`, `version_number`),
  UNIQUE KEY `uk_ana_source_revision` (`data_source_id`, `draft_revision`),
  CONSTRAINT `fk_ana_source_version_source` FOREIGN KEY (`data_source_id`) REFERENCES `ana_data_source` (`id`),
  CONSTRAINT `fk_ana_source_version_actor` FOREIGN KEY (`published_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_dashboard` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `owner_tenant_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `status` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_code` (`context_type`, `platform_id`, `system_id`, `owner_tenant_id`, `code`),
  CONSTRAINT `fk_ana_dashboard_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_dashboard_component` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dashboard_id` BIGINT NOT NULL,
  `component_key` VARCHAR(100) NOT NULL,
  `component_type` VARCHAR(64) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `data_source_id` BIGINT NULL,
  `layout_json` JSON NOT NULL,
  `query_parameter_json` JSON NOT NULL,
  `display_config_json` JSON NOT NULL,
  `drill_target_json` JSON NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_component` (`dashboard_id`, `component_key`),
  CONSTRAINT `fk_ana_component_dashboard` FOREIGN KEY (`dashboard_id`) REFERENCES `ana_dashboard` (`id`),
  CONSTRAINT `fk_ana_component_source` FOREIGN KEY (`data_source_id`) REFERENCES `ana_data_source` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_dashboard_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dashboard_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `published_by_account_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_version` (`dashboard_id`, `version_number`),
  UNIQUE KEY `uk_ana_dashboard_revision` (`dashboard_id`, `draft_revision`),
  UNIQUE KEY `uk_ana_dashboard_version_id` (`dashboard_id`, `id`),
  CONSTRAINT `fk_ana_dashboard_version_dashboard` FOREIGN KEY (`dashboard_id`) REFERENCES `ana_dashboard` (`id`),
  CONSTRAINT `fk_ana_dashboard_version_actor` FOREIGN KEY (`published_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_dashboard_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dashboard_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `updated_by_account_id` BIGINT NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_dashboard_publication` (`dashboard_id`),
  CONSTRAINT `fk_ana_dashboard_pub_dashboard` FOREIGN KEY (`dashboard_id`) REFERENCES `ana_dashboard` (`id`),
  CONSTRAINT `fk_ana_dashboard_pub_version` FOREIGN KEY (`dashboard_id`, `current_version_id`) REFERENCES `ana_dashboard_version` (`dashboard_id`, `id`),
  CONSTRAINT `fk_ana_dashboard_pub_actor` FOREIGN KEY (`updated_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_kpi` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `owner_tenant_id` BIGINT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `data_source_version_id` BIGINT NOT NULL,
  `target_expression` VARCHAR(1000) NOT NULL,
  `calculation_schedule` VARCHAR(100) NOT NULL,
  `dimension_json` JSON NOT NULL,
  `reminder_policy_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_account_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_kpi_code` (`context_type`, `platform_id`, `system_id`, `owner_tenant_id`, `code`),
  CONSTRAINT `fk_ana_kpi_source_version` FOREIGN KEY (`data_source_version_id`) REFERENCES `ana_data_source_version` (`id`),
  CONSTRAINT `fk_ana_kpi_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_kpi_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `kpi_id` BIGINT NOT NULL,
  `period_key` VARCHAR(100) NOT NULL,
  `dimension_key` VARCHAR(500) NOT NULL,
  `actual_value` DECIMAL(30, 8) NULL,
  `target_value` DECIMAL(30, 8) NULL,
  `achievement_rate` DECIMAL(12, 6) NULL,
  `status` VARCHAR(32) NOT NULL,
  `explanation_json` JSON NOT NULL,
  `calculated_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_kpi_result` (`kpi_id`, `period_key`, `dimension_key`),
  KEY `idx_ana_kpi_result_status` (`kpi_id`, `status`, `calculated_at`),
  CONSTRAINT `fk_ana_kpi_result_kpi` FOREIGN KEY (`kpi_id`) REFERENCES `ana_kpi` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ana_kpi_reminder` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `kpi_result_id` BIGINT NOT NULL,
  `todo_id` BIGINT NULL,
  `message_id` BIGINT NULL,
  `recipient_account_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `sent_at` DATETIME(3) NULL,
  `acknowledged_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ana_kpi_reminder` (`kpi_result_id`, `recipient_account_id`),
  CONSTRAINT `fk_ana_kpi_reminder_result` FOREIGN KEY (`kpi_result_id`) REFERENCES `ana_kpi_result` (`id`),
  CONSTRAINT `fk_ana_kpi_reminder_todo` FOREIGN KEY (`todo_id`) REFERENCES `todo_item` (`id`),
  CONSTRAINT `fk_ana_kpi_reminder_message` FOREIGN KEY (`message_id`) REFERENCES `msg_message` (`id`),
  CONSTRAINT `fk_ana_kpi_reminder_recipient` FOREIGN KEY (`recipient_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_ana_source_version_immutable_update`
BEFORE UPDATE ON `ana_data_source_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ana_data_source_version is immutable';
END$$
CREATE TRIGGER `trg_ana_source_version_immutable_delete`
BEFORE DELETE ON `ana_data_source_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ana_data_source_version is immutable';
END$$
CREATE TRIGGER `trg_ana_dashboard_version_immutable_update`
BEFORE UPDATE ON `ana_dashboard_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ana_dashboard_version is immutable';
END$$
CREATE TRIGGER `trg_ana_dashboard_version_immutable_delete`
BEFORE DELETE ON `ana_dashboard_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ana_dashboard_version is immutable';
END$$
DELIMITER ;

-- source: init/V17__print.sql
-- Initial baseline: module print templates, immutable versions, preview and output jobs.

CREATE TABLE `print_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `name` VARCHAR(200) NOT NULL,
  `page_size` VARCHAR(32) NOT NULL,
  `orientation` VARCHAR(32) NOT NULL,
  `draft_revision` INT NOT NULL DEFAULT 1,
  `template_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_code` (`owner_tenant_id`, `module_id`, `code`),
  UNIQUE KEY `uk_print_template_module_id` (`module_id`, `id`),
  CONSTRAINT `fk_print_template_tenant` FOREIGN KEY (`system_id`, `owner_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_print_template_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_print_template_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `print_template_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_version` (`template_id`, `version_number`),
  UNIQUE KEY `uk_print_template_revision` (`template_id`, `draft_revision`),
  UNIQUE KEY `uk_print_template_version_id` (`template_id`, `id`),
  CONSTRAINT `fk_print_version_template` FOREIGN KEY (`template_id`) REFERENCES `print_template` (`id`),
  CONSTRAINT `fk_print_version_publisher` FOREIGN KEY (`published_by_member_id`) REFERENCES `sys_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `print_template_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `updated_by_member_id` BIGINT NOT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_print_template_publication` (`template_id`),
  CONSTRAINT `fk_print_publication_template` FOREIGN KEY (`template_id`) REFERENCES `print_template` (`id`),
  CONSTRAINT `fk_print_publication_version` FOREIGN KEY (`template_id`, `current_version_id`) REFERENCES `print_template_version` (`template_id`, `id`),
  CONSTRAINT `fk_print_publication_actor` FOREIGN KEY (`updated_by_member_id`) REFERENCES `sys_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `print_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `module_id` BIGINT NOT NULL,
  `record_id` BIGINT NOT NULL,
  `template_version_id` BIGINT NOT NULL,
  `background_job_id` BIGINT NULL,
  `authorization_snapshot_json` JSON NOT NULL,
  `record_snapshot_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `output_file_id` BIGINT NULL,
  `error_message` VARCHAR(2000) NULL,
  `created_by_member_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `finished_at` DATETIME(3) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_print_job_record` (`record_id`, `created_at`),
  KEY `idx_print_job_status` (`status`, `created_at`),
  CONSTRAINT `fk_print_job_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_print_job_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  CONSTRAINT `fk_print_job_record` FOREIGN KEY (`tenant_id`, `record_id`) REFERENCES `biz_record` (`tenant_id`, `id`),
  CONSTRAINT `fk_print_job_template_version` FOREIGN KEY (`template_version_id`) REFERENCES `print_template_version` (`id`),
  CONSTRAINT `fk_print_job_background` FOREIGN KEY (`background_job_id`) REFERENCES `job_background` (`id`),
  CONSTRAINT `fk_print_job_output` FOREIGN KEY (`output_file_id`) REFERENCES `file_object` (`id`),
  CONSTRAINT `fk_print_job_creator` FOREIGN KEY (`system_id`, `created_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_print_version_immutable_update`
BEFORE UPDATE ON `print_template_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'print_template_version is immutable';
END$$
CREATE TRIGGER `trg_print_version_immutable_delete`
BEFORE DELETE ON `print_template_version` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'print_template_version is immutable';
END$$
DELIMITER ;

-- source: init/V18__operations_security.sql
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

-- source: update/V19__tenant_main_constraint.sql
ALTER TABLE `sys_tenant`
  DROP CHECK `chk_sys_tenant_main_marker`,
  ADD CONSTRAINT `chk_sys_tenant_main_marker`
    CHECK ((`is_main` = 1 AND `main_marker` IS NOT NULL AND `main_marker` = 'MAIN')
      OR (`is_main` = 0 AND `main_marker` IS NULL));

-- source: update/V20__access_request_context.sql
ALTER TABLE `sys_access_request`
  ADD COLUMN `identity_provider` VARCHAR(100) NOT NULL DEFAULT 'LOCAL' AFTER `account_id`,
  ADD COLUMN `external_user_id` VARCHAR(255) NULL AFTER `identity_provider`,
  ADD COLUMN `requested_role` VARCHAR(100) NULL AFTER `request_reason`,
  ADD COLUMN `approved_role_ids_json` JSON NULL AFTER `decision_comment`,
  ADD COLUMN `approved_data_scope_json` JSON NULL AFTER `approved_role_ids_json`,
  ADD COLUMN `request_trace_id` VARCHAR(64) NULL AFTER `approved_data_scope_json`,
  ADD COLUMN `decision_trace_id` VARCHAR(64) NULL AFTER `request_trace_id`,
  ADD KEY `idx_sys_access_external_identity` (`identity_provider`, `external_user_id`),
  ADD KEY `idx_sys_access_request_trace` (`request_trace_id`);

UPDATE `sys_access_request`
SET `request_trace_id` = CONCAT('migration-', `id`)
WHERE `request_trace_id` IS NULL;

ALTER TABLE `sys_access_request`
  MODIFY COLUMN `request_trace_id` VARCHAR(64) NOT NULL;

-- source: update/V21__sso_provider_version_lifecycle.sql
CREATE TABLE `plat_sso_provider_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `provider_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `protocol` VARCHAR(32) NOT NULL,
  `issuer` VARCHAR(500) NOT NULL,
  `client_id` VARCHAR(255) NOT NULL,
  `client_secret_ref` VARCHAR(255) NULL,
  `protocol_config_json` JSON NOT NULL,
  `allowed_domains_json` JSON NOT NULL,
  `attribute_mapping_json` JSON NOT NULL,
  `jit_policy_json` JSON NOT NULL,
  `mfa_policy_json` JSON NOT NULL,
  `callback_uris_json` JSON NOT NULL,
  `test_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_TESTED',
  `test_report_json` JSON NULL,
  `tested_at` DATETIME(3) NULL,
  `tested_by_account_id` BIGINT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  `published_at` DATETIME(3) NULL,
  `published_by_account_id` BIGINT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plat_sso_provider_version` (`provider_id`, `version_number`),
  KEY `idx_plat_sso_version_status` (`status`, `test_status`),
  CONSTRAINT `fk_plat_sso_version_provider` FOREIGN KEY (`provider_id`) REFERENCES `plat_sso_provider` (`id`),
  CONSTRAINT `fk_plat_sso_version_tester` FOREIGN KEY (`tested_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_plat_sso_version_publisher` FOREIGN KEY (`published_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `plat_sso_provider`
  ADD COLUMN `published_version_id` BIGINT NULL AFTER `status`,
  ADD COLUMN `published_version_number` INT NULL AFTER `published_version_id`,
  ADD KEY `idx_plat_sso_published_version` (`published_version_id`),
  ADD CONSTRAINT `fk_plat_sso_published_version`
    FOREIGN KEY (`published_version_id`) REFERENCES `plat_sso_provider_version` (`id`);

-- source: update/V22__dictionary_version_lifecycle.sql
CREATE TABLE `cfg_dictionary_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `dictionary_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `draft_revision` INT NOT NULL,
  `snapshot_json` JSON NOT NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_version_number` (`dictionary_id`, `version_number`),
  UNIQUE KEY `uk_cfg_dictionary_version_dict_id` (`dictionary_id`, `id`),
  KEY `idx_cfg_dictionary_version_context` (`system_id`, `owner_tenant_id`, `dictionary_id`),
  CONSTRAINT `fk_cfg_dictionary_version_dictionary`
    FOREIGN KEY (`owner_tenant_id`, `dictionary_id`) REFERENCES `cfg_dictionary` (`owner_tenant_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_version_publisher`
    FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cfg_dictionary_publication` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `owner_tenant_id` BIGINT NOT NULL,
  `dictionary_id` BIGINT NOT NULL,
  `current_version_id` BIGINT NOT NULL,
  `published_by_member_id` BIGINT NOT NULL,
  `published_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cfg_dictionary_publication` (`dictionary_id`),
  KEY `idx_cfg_dictionary_publication_context` (`system_id`, `owner_tenant_id`),
  CONSTRAINT `fk_cfg_dictionary_publication_dictionary`
    FOREIGN KEY (`owner_tenant_id`, `dictionary_id`) REFERENCES `cfg_dictionary` (`owner_tenant_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_publication_version`
    FOREIGN KEY (`dictionary_id`, `current_version_id`) REFERENCES `cfg_dictionary_version` (`dictionary_id`, `id`),
  CONSTRAINT `fk_cfg_dictionary_publication_publisher`
    FOREIGN KEY (`system_id`, `published_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V23__runtime_unique_index_key.sql
ALTER TABLE `biz_record_index`
  ADD COLUMN `unique_key_hash` VARCHAR(64) NULL AFTER `index_key_hash`,
  ADD UNIQUE KEY `uk_biz_record_index_unique_value`
    (`tenant_id`, `module_id`, `query_index_id`, `unique_key_hash`);

-- source: update/V24__module_rule_test_result.sql
ALTER TABLE `cfg_module_rule`
  ADD COLUMN `test_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_TESTED' AFTER `message_template`,
  ADD COLUMN `last_test_input_json` JSON NULL AFTER `test_status`,
  ADD COLUMN `last_test_result_json` JSON NULL AFTER `last_test_input_json`,
  ADD COLUMN `last_tested_by_member_id` BIGINT NULL AFTER `last_test_result_json`,
  ADD COLUMN `last_tested_at` DATETIME(3) NULL AFTER `last_tested_by_member_id`,
  ADD CONSTRAINT `fk_cfg_module_rule_last_tester`
    FOREIGN KEY (`system_id`, `last_tested_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`);

-- source: update/V25__tenant_extension_publication_pointer.sql
ALTER TABLE `cfg_tenant_extension_version`
  ADD UNIQUE KEY `uk_cfg_extension_version_owner_id` (`extension_id`, `id`);

ALTER TABLE `cfg_tenant_extension`
  ADD COLUMN `current_version_id` BIGINT NULL AFTER `status`,
  ADD COLUMN `updated_by_member_id` BIGINT NULL AFTER `current_version_id`,
  ADD CONSTRAINT `fk_cfg_tenant_extension_current_version`
    FOREIGN KEY (`id`, `current_version_id`) REFERENCES `cfg_tenant_extension_version` (`extension_id`, `id`),
  ADD CONSTRAINT `fk_cfg_tenant_extension_updater`
    FOREIGN KEY (`system_id`, `updated_by_member_id`) REFERENCES `sys_member` (`system_id`, `id`);

-- source: update/V26__module_rollback_version.sql
ALTER TABLE `cfg_module_version`
  DROP INDEX `uk_cfg_module_version_revision`,
  ADD KEY `idx_cfg_module_version_revision` (`module_id`, `draft_revision`);

-- source: update/V27__application_call_contract.sql
-- Complete the durable application-call security and observability contract.

ALTER TABLE `app_call`
  ADD COLUMN `request_timestamp` DATETIME(3) NOT NULL AFTER `trace_id`,
  ADD COLUMN `nonce` VARCHAR(128) NOT NULL AFTER `request_timestamp`,
  ADD COLUMN `permission_snapshot_json` JSON NULL AFTER `request_hash`,
  ADD COLUMN `response_json` JSON NULL AFTER `response_code`,
  ADD COLUMN `target_reference` VARCHAR(255) NULL AFTER `response_json`,
  ADD COLUMN `replay_count` INT NOT NULL DEFAULT 0 AFTER `target_reference`,
  ADD UNIQUE KEY `uk_app_call_nonce` (`application_id`, `nonce`);

-- source: update/V28__application_signing_secret_and_nonce.sql
-- Keep signing material encrypted behind a reference and persist every accepted nonce.

ALTER TABLE `app_credential`
  ADD COLUMN `signing_secret_ref` VARCHAR(2000) NULL AFTER `secret_hash`;

CREATE TABLE `app_call_nonce` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `credential_version` INT NOT NULL,
  `nonce` VARCHAR(128) NOT NULL,
  `request_id` VARCHAR(64) NOT NULL,
  `request_timestamp` DATETIME(3) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_call_nonce_value` (`application_id`, `nonce`),
  KEY `idx_app_call_nonce_expiry` (`expires_at`),
  CONSTRAINT `fk_app_call_nonce_definition` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V29__application_call_rejection_log.sql
-- A rejected call may fail before a matching grant exists; retain that attempt for audit.

ALTER TABLE `app_call`
  DROP FOREIGN KEY `fk_app_call_grant`;

ALTER TABLE `app_call`
  MODIFY COLUMN `grant_id` BIGINT NULL;

ALTER TABLE `app_call`
  ADD CONSTRAINT `fk_app_call_grant` FOREIGN KEY (`grant_id`) REFERENCES `app_grant` (`id`);

-- source: update/V30__application_grant_history_link.sql
-- Draft grants are mutable; completed call evidence must survive grant replacement.

ALTER TABLE `app_call`
  DROP FOREIGN KEY `fk_app_call_grant`;

ALTER TABLE `app_call`
  ADD CONSTRAINT `fk_app_call_grant`
  FOREIGN KEY (`grant_id`) REFERENCES `app_grant` (`id`) ON DELETE SET NULL;

-- source: update/V31__message_event_deduplication.sql
-- Message events must be idempotent within one platform/system/tenant context.
ALTER TABLE `msg_message`
  ADD COLUMN `dedup_context_key` VARCHAR(160)
    GENERATED ALWAYS AS (
      CONCAT(`context_type`, ':', IFNULL(`platform_id`, 0), ':', IFNULL(`system_id`, 0), ':', IFNULL(`tenant_id`, 0))
    ) STORED,
  ADD UNIQUE KEY `uk_msg_message_event_dedup` (`dedup_context_key`, `source_type`, `source_id`);

-- source: update/V32__module_import_action.sql
-- C43: every module exposes import as an explicit, publishable action.

INSERT INTO `cfg_module_action` (
  `system_id`, `owner_tenant_id`, `module_id`, `code`, `name`, `action_type`,
  `location`, `sort_order`, `status`, `config_json`, `version`
)
SELECT
  m.`system_id`, m.`owner_tenant_id`, m.`id`, 'IMPORT', '导入', 'BUILTIN',
  'LIST_TOOLBAR', 100, 'ACTIVE', JSON_OBJECT(), 0
FROM `cfg_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'IMPORT'
);

UPDATE `cfg_module` m
SET m.`draft_revision` = m.`draft_revision` + 1,
    m.`updated_at` = CURRENT_TIMESTAMP(3)
WHERE EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'IMPORT'
)
AND EXISTS (
  SELECT 1 FROM `cfg_module_publication` p
  WHERE p.`module_id` = m.`id`
);

-- source: update/V33__module_export_action.sql
-- C44: every module exposes permission-scoped export as an explicit, publishable action.

INSERT INTO `cfg_module_action` (
  `system_id`, `owner_tenant_id`, `module_id`, `code`, `name`, `action_type`,
  `location`, `sort_order`, `status`, `config_json`, `version`
)
SELECT
  m.`system_id`, m.`owner_tenant_id`, m.`id`, 'EXPORT', '导出', 'BUILTIN',
  'LIST_TOOLBAR', 110, 'ACTIVE', JSON_OBJECT(), 0
FROM `cfg_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'EXPORT'
);

UPDATE `cfg_module` m
SET m.`draft_revision` = m.`draft_revision` + 1,
    m.`updated_at` = CURRENT_TIMESTAMP(3)
WHERE EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'EXPORT'
)
AND EXISTS (
  SELECT 1 FROM `cfg_module_publication` p
  WHERE p.`module_id` = m.`id`
);

-- source: update/V34__operations_observability.sql
-- Persisted request observations make requestId searchable without server access.
-- Query strings, authorization headers, cookies and secret values are intentionally not stored.
CREATE TABLE `ops_request_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `request_id` VARCHAR(64) NOT NULL,
  `trace_id` VARCHAR(64) NOT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `account_id` BIGINT NULL,
  `client_source` VARCHAR(32) NOT NULL,
  `http_method` VARCHAR(16) NOT NULL,
  `request_path` VARCHAR(500) NOT NULL,
  `status_code` INT NOT NULL,
  `result_code` VARCHAR(64) NOT NULL,
  `duration_millis` BIGINT NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ops_request_id` (`request_id`, `occurred_at`),
  KEY `idx_ops_request_context` (`system_id`, `tenant_id`, `occurred_at`),
  KEY `idx_ops_request_result` (`status_code`, `result_code`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V35__organization_reporting_directory.sql
-- Unified tenant organization directory: position and reporting line are business relations,
-- while stable member identifiers remain internal implementation details.
CREATE TABLE `sys_member_reporting_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `tenant_member_id` BIGINT NOT NULL,
  `manager_tenant_member_id` BIGINT NULL,
  `position_title` VARCHAR(100) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_reporting_member` (`tenant_id`, `tenant_member_id`),
  KEY `idx_sys_member_reporting_manager` (`tenant_id`, `manager_tenant_member_id`),
  CONSTRAINT `fk_sys_member_reporting_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_sys_member_reporting_member` FOREIGN KEY (`tenant_id`, `tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_sys_member_reporting_manager` FOREIGN KEY (`tenant_id`, `manager_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- source: update/V36__backfill_system_root_department.sql
-- Systems created before the atomic bootstrap receive the same usable organization root.
INSERT INTO `sys_department` (`system_id`, `tenant_id`, `parent_id`, `code`, `name`, `path_code`, `sort_order`, `status`)
SELECT tenant.`system_id`, tenant.`id`, NULL, 'root', '全公司', '/root', 0, 'ACTIVE'
FROM `sys_tenant` tenant
WHERE tenant.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` department
    WHERE department.`tenant_id` = tenant.`id` AND department.`parent_id` IS NULL
  );

UPDATE `sys_tenant_member` membership
JOIN `sys_department` root_department
  ON root_department.`tenant_id` = membership.`tenant_id`
 AND root_department.`parent_id` IS NULL
SET membership.`department_id` = root_department.`id`, membership.`version` = membership.`version` + 1
WHERE membership.`status` = 'ACTIVE' AND membership.`department_id` IS NULL;

-- source: update/V37__flow_work_business_context.sql
-- Flow runtime layers and business-object work context.
-- Published definitions remain in flow_version; runtime execution, history and jobs are separated here.

ALTER TABLE `flow_instance`
  ADD COLUMN `started_by_tenant_member_id` BIGINT NULL AFTER `started_by_account_id`,
  ADD KEY `idx_flow_instance_starter_member` (`tenant_id`, `started_by_tenant_member_id`),
  ADD CONSTRAINT `fk_flow_instance_starter_member`
    FOREIGN KEY (`started_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_task`
  ADD COLUMN `assignee_tenant_member_id` BIGINT NULL AFTER `assignee_account_id`,
  ADD KEY `idx_flow_task_assignee_member` (`assignee_tenant_member_id`, `status`, `due_at`),
  ADD CONSTRAINT `fk_flow_task_assignee_member`
    FOREIGN KEY (`assignee_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_action`
  ADD COLUMN `acted_by_tenant_member_id` BIGINT NULL AFTER `acted_by_account_id`,
  ADD KEY `idx_flow_action_actor_member` (`acted_by_tenant_member_id`, `acted_at`),
  ADD CONSTRAINT `fk_flow_action_actor_member`
    FOREIGN KEY (`acted_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_exception`
  ADD COLUMN `resolved_by_tenant_member_id` BIGINT NULL AFTER `resolved_by_account_id`,
  ADD CONSTRAINT `fk_flow_exception_resolver_member`
    FOREIGN KEY (`resolved_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

CREATE TABLE `flow_execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `parent_execution_id` BIGINT NULL,
  `node_key` VARCHAR(100) NOT NULL,
  `node_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `entered_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `left_at` DATETIME(3) NULL,
  `result_code` VARCHAR(64) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_flow_execution_active` (`instance_id`, `status`, `entered_at`),
  CONSTRAINT `fk_flow_execution_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_execution_parent` FOREIGN KEY (`parent_execution_id`) REFERENCES `flow_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_history_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `execution_id` BIGINT NULL,
  `task_id` BIGINT NULL,
  `node_key` VARCHAR(100) NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `event_name` VARCHAR(200) NOT NULL,
  `actor_account_id` BIGINT NULL,
  `actor_tenant_member_id` BIGINT NULL,
  `before_status` VARCHAR(32) NULL,
  `after_status` VARCHAR(32) NULL,
  `detail_json` JSON NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_flow_history_instance` (`instance_id`, `occurred_at`),
  KEY `idx_flow_history_task` (`task_id`, `occurred_at`),
  CONSTRAINT `fk_flow_history_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_history_execution` FOREIGN KEY (`execution_id`) REFERENCES `flow_execution` (`id`),
  CONSTRAINT `fk_flow_history_task` FOREIGN KEY (`task_id`) REFERENCES `flow_task` (`id`),
  CONSTRAINT `fk_flow_history_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_flow_history_actor_member` FOREIGN KEY (`actor_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `execution_id` BIGINT NULL,
  `node_key` VARCHAR(100) NULL,
  `job_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `payload_json` JSON NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL DEFAULT 3,
  `next_run_at` DATETIME(3) NOT NULL,
  `locked_at` DATETIME(3) NULL,
  `locked_by` VARCHAR(100) NULL,
  `last_error_code` VARCHAR(100) NULL,
  `last_error_message` VARCHAR(2000) NULL,
  `completed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_job_idempotency` (`instance_id`, `idempotency_key`),
  KEY `idx_flow_job_due` (`status`, `next_run_at`),
  CONSTRAINT `fk_flow_job_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_job_execution` FOREIGN KEY (`execution_id`) REFERENCES `flow_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `work_project`
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL AFTER `owner_account_id`,
  ADD CONSTRAINT `fk_work_project_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_project_member`
  ADD COLUMN `tenant_member_id` BIGINT NULL AFTER `account_id`,
  ADD KEY `idx_work_project_member_tenant_member` (`tenant_member_id`, `project_id`),
  ADD CONSTRAINT `fk_work_project_member_tenant_member`
    FOREIGN KEY (`tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task`
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL AFTER `owner_account_id`,
  ADD COLUMN `business_type` VARCHAR(64) NULL AFTER `custom_values_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL AFTER `business_id`,
  ADD KEY `idx_work_task_owner_member` (`owner_tenant_member_id`, `status`, `due_at`),
  ADD KEY `idx_work_task_business` (`system_id`, `tenant_id`, `business_type`, `business_id`),
  ADD CONSTRAINT `fk_work_task_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task_member`
  ADD COLUMN `tenant_member_id` BIGINT NULL AFTER `account_id`,
  ADD KEY `idx_work_task_member_tenant_member` (`tenant_member_id`, `task_id`),
  ADD CONSTRAINT `fk_work_task_member_tenant_member`
    FOREIGN KEY (`tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_log`
  ADD COLUMN `author_tenant_member_id` BIGINT NULL AFTER `author_account_id`,
  ADD COLUMN `business_type` VARCHAR(64) NULL AFTER `custom_values_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL AFTER `business_id`,
  ADD KEY `idx_work_log_author_member` (`author_tenant_member_id`, `work_date`),
  ADD KEY `idx_work_log_business` (`system_id`, `tenant_id`, `business_type`, `business_id`),
  ADD CONSTRAINT `fk_work_log_author_member`
    FOREIGN KEY (`author_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

UPDATE `flow_instance` fi
JOIN `sys_member` sm ON sm.system_id = fi.system_id AND sm.account_id = fi.started_by_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = fi.tenant_id
SET fi.started_by_tenant_member_id = stm.id
WHERE fi.context_type = 'SYSTEM' AND fi.started_by_tenant_member_id IS NULL;

UPDATE `flow_task` ft
JOIN `flow_instance` fi ON fi.id = ft.instance_id
JOIN `sys_member` sm ON sm.system_id = fi.system_id AND sm.account_id = ft.assignee_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = fi.tenant_id
SET ft.assignee_tenant_member_id = stm.id
WHERE fi.context_type = 'SYSTEM' AND ft.assignee_tenant_member_id IS NULL;

UPDATE `work_project` wp
JOIN `sys_member` sm ON sm.system_id = wp.system_id AND sm.account_id = wp.owner_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wp.tenant_id
SET wp.owner_tenant_member_id = stm.id
WHERE wp.context_type = 'SYSTEM' AND wp.owner_tenant_member_id IS NULL;

UPDATE `work_task` wt
JOIN `sys_member` sm ON sm.system_id = wt.system_id AND sm.account_id = wt.owner_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wt.tenant_id
SET wt.owner_tenant_member_id = stm.id
WHERE wt.context_type = 'SYSTEM' AND wt.owner_tenant_member_id IS NULL;

UPDATE `work_log` wl
JOIN `sys_member` sm ON sm.system_id = wl.system_id AND sm.account_id = wl.author_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wl.tenant_id
SET wl.author_tenant_member_id = stm.id
WHERE wl.context_type = 'SYSTEM' AND wl.author_tenant_member_id IS NULL;

DELIMITER $$
CREATE TRIGGER `trg_flow_history_event_immutable_update`
BEFORE UPDATE ON `flow_history_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_history_event is immutable';
END$$
CREATE TRIGGER `trg_flow_history_event_immutable_delete`
BEFORE DELETE ON `flow_history_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_history_event is immutable';
END$$
DELIMITER ;

-- source: update/V38__work_log_structured_links.sql
ALTER TABLE `work_log`
  ADD COLUMN `project_id` BIGINT NULL AFTER `tenant_id`,
  ADD KEY `idx_work_log_project_date` (`project_id`, `work_date`),
  ADD CONSTRAINT `fk_work_log_project` FOREIGN KEY (`project_id`) REFERENCES `work_project` (`id`);

CREATE TABLE `work_log_task_link` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `work_log_id` BIGINT NOT NULL,
  `task_id` BIGINT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_task_link` (`work_log_id`, `task_id`),
  KEY `idx_work_log_task_link_task` (`task_id`, `work_log_id`),
  CONSTRAINT `fk_work_log_task_link_log` FOREIGN KEY (`work_log_id`) REFERENCES `work_log` (`id`),
  CONSTRAINT `fk_work_log_task_link_task` FOREIGN KEY (`task_id`) REFERENCES `work_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

UPDATE `work_log`
SET `project_id` = CAST(JSON_UNQUOTE(JSON_EXTRACT(`custom_values_json`, '$.projectId')) AS UNSIGNED)
WHERE JSON_EXTRACT(`custom_values_json`, '$.projectId') IS NOT NULL;

INSERT IGNORE INTO `work_log_task_link` (`work_log_id`, `task_id`)
SELECT wl.id, CAST(jt.task_id AS UNSIGNED)
FROM `work_log` wl
JOIN JSON_TABLE(wl.custom_values_json, '$.taskIds[*]' COLUMNS(task_id VARCHAR(32) PATH '$')) AS jt ON TRUE
WHERE wl.custom_values_json IS NOT NULL;

-- source: update/V39__application_call_immutable_version.sql
-- Application calls execute the immutable published version, never mutable draft grant rows.
ALTER TABLE `app_call`
  ADD COLUMN `application_version_id` BIGINT NULL AFTER `application_id`,
  ADD KEY `idx_app_call_version` (`application_id`, `application_version_id`),
  ADD CONSTRAINT `fk_app_call_version`
    FOREIGN KEY (`application_id`, `application_version_id`)
    REFERENCES `app_version` (`application_id`, `id`);

-- source: update/V40__direct_action_without_flow.sql
-- A configured DIRECT action saves normally and does not require an unrelated Flow.
ALTER TABLE `flow_trigger_binding`
  DROP FOREIGN KEY `fk_flow_binding_definition`;
ALTER TABLE `flow_trigger_binding` MODIFY COLUMN `flow_id` BIGINT NULL;
ALTER TABLE `flow_trigger_binding` ADD CONSTRAINT `fk_flow_binding_definition`
    FOREIGN KEY (`flow_id`) REFERENCES `flow_definition` (`id`);

-- source: update/V41__conversion_completion_clock_repair.sql
-- 修复已确认的转化时间混用：旧容器以 UTC 写 finished_at，创建和成功审计由 +08:00 数据库连接写入。
-- 只处理完成早于创建、加八小时不早于创建，且与同一转化成功审计相差不超过两秒的记录。
-- 不修改来源/目标正文、关系状态、版本、映射快照及原成功审计。未满足证据条件的数据保持原样。
-- 先追加含原值、修复值的永久审计，供追溯和按记录恢复；禁止对所有历史时间统一加八小时。
INSERT INTO audit_event
    (trace_id, request_id, context_type, system_id, tenant_id, event_category,
     event_code, object_type, object_id, result_code, detail_json)
SELECT 'migration-v41-conversion-clock', 'migration-v41-conversion-clock', 'SYSTEM',
       c.system_id, c.tenant_id, 'DATA_REPAIR', 'CONVERSION_CLOCK_REPAIRED',
       'BUSINESS_RECORD_CONVERSION', CAST(c.id AS CHAR), 'SUCCESS',
       JSON_OBJECT('beforeTime', DATE_FORMAT(c.finished_at, '%Y-%m-%d %H:%i:%s.%f'),
                   'afterTime', DATE_FORMAT(DATE_ADD(c.finished_at, INTERVAL 8 HOUR), '%Y-%m-%d %H:%i:%s.%f'),
                   'reason', '转化完成时间与数据库业务时区对齐，原值保留用于恢复')
FROM biz_record_conversion c
WHERE c.status IN ('SUCCEEDED', 'UNLINKED')
  AND c.finished_at < c.created_at
  AND DATE_ADD(c.finished_at, INTERVAL 8 HOUR) >= c.created_at
  AND EXISTS (
      SELECT 1 FROM audit_event a
      WHERE a.system_id = c.system_id AND a.tenant_id = c.tenant_id
        AND a.event_code = 'BUSINESS_RECORD_CONVERTED' AND a.result_code = 'SUCCESS'
        AND a.object_type = 'BUSINESS_RECORD' AND a.object_id = CAST(c.source_record_id AS CHAR)
        AND JSON_EXTRACT(a.detail_json, '$.conversionId') = c.id
        AND ABS(TIMESTAMPDIFF(MICROSECOND, DATE_ADD(c.finished_at, INTERVAL 8 HOUR), a.occurred_at)) <= 2000000)
  AND NOT EXISTS (
      SELECT 1 FROM audit_event r
      WHERE r.event_code = 'CONVERSION_CLOCK_REPAIRED' AND r.object_type = 'BUSINESS_RECORD_CONVERSION'
        AND r.object_id = CAST(c.id AS CHAR) AND r.request_id = 'migration-v41-conversion-clock');

-- 仅应用刚才审计中的明确修复；原值校验保证重跑不会重复平移或覆盖其他修复。
UPDATE biz_record_conversion c
JOIN audit_event r ON r.object_id = CAST(c.id AS CHAR)
  AND r.system_id = c.system_id AND r.tenant_id = c.tenant_id
  AND r.event_code = 'CONVERSION_CLOCK_REPAIRED' AND r.object_type = 'BUSINESS_RECORD_CONVERSION'
  AND r.request_id = 'migration-v41-conversion-clock'
SET c.finished_at = CAST(JSON_UNQUOTE(JSON_EXTRACT(r.detail_json, '$.afterTime')) AS DATETIME(3))
WHERE c.finished_at = CAST(JSON_UNQUOTE(JSON_EXTRACT(r.detail_json, '$.beforeTime')) AS DATETIME(3));

-- 恢复方案：从修复审计取 beforeTime，仅在当前值仍等于 afterTime 时按 conversion id 恢复；
-- 原成功审计及修复审计均保留，并为恢复另记审计。不能重写已被后续人工修复的时间。

-- source: update/V42__tenant_default_organization_repair.sql
-- 补齐创建租户时遗漏的默认组织，只处理有效系统的有效租户；不修改手工根部门或已有部门数据。
-- 没有任何顶级部门且 root 编码未被占用才补根；停用根、多根、编码冲突均留待人工核对，不重启用或任意选根。
INSERT INTO `sys_department` (`system_id`, `tenant_id`, `parent_id`, `code`, `name`, `path_code`, `sort_order`, `status`)
SELECT tenant.`system_id`, tenant.`id`, NULL, 'root', '全公司', '/root', 0, 'ACTIVE'
FROM `sys_tenant` tenant
JOIN `sys_system` system_definition ON system_definition.`id` = tenant.`system_id`
WHERE tenant.`status` = 'ACTIVE' AND system_definition.`status` = 'ACTIVE' AND system_definition.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` department
    WHERE department.`tenant_id` = tenant.`id`
      AND (department.`parent_id` IS NULL OR department.`code` = 'root')
  );

-- 有效成员未配置部门时归入本租户唯一的有效顶级部门；保留已有归属并递增版本，防止旧页面覆盖修复结果。
-- 只补真正活动的账号/系统成员，不把禁用历史成员重新纳入组织；多根或停用根不会被任意作为默认部门。
UPDATE `sys_tenant_member` membership
JOIN `sys_tenant` tenant ON tenant.`id` = membership.`tenant_id` AND tenant.`system_id` = membership.`system_id`
JOIN `sys_system` system_definition ON system_definition.`id` = tenant.`system_id`
JOIN `sys_member` member ON member.`id` = membership.`system_member_id` AND member.`system_id` = membership.`system_id`
JOIN `plat_account` account ON account.`id` = member.`account_id`
JOIN `sys_department` root_department ON root_department.`tenant_id` = membership.`tenant_id`
  AND root_department.`system_id` = membership.`system_id` AND root_department.`parent_id` IS NULL
SET membership.`department_id` = root_department.`id`, membership.`version` = membership.`version` + 1
WHERE membership.`status` = 'ACTIVE' AND membership.`department_id` IS NULL
  AND tenant.`status` = 'ACTIVE' AND system_definition.`status` = 'ACTIVE' AND system_definition.`deleted` = 0
  AND member.`status` = 'ACTIVE' AND account.`status` = 'ACTIVE' AND root_department.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` other_root
    WHERE other_root.`tenant_id` = tenant.`id` AND other_root.`parent_id` IS NULL
      AND other_root.`id` <> root_department.`id`
  );

-- source: update/V43__tenant_module_range_share.sql
-- 将现有记录共享扩展为模块数据范围共享，保留授权主键、使用日志及旧时间字段。
-- 先允许模块归属为空，再依据原记录回填；不复制业务数据、不删除历史授权。
ALTER TABLE `biz_tenant_share`
  ADD COLUMN `module_id` BIGINT NULL COMMENT '共享数据所属模块，记录授权由原记录归属回填' AFTER `target_tenant_id`,
  ADD COLUMN `scope_type` VARCHAR(32) NOT NULL DEFAULT 'RECORD' COMMENT '授权范围类型：RECORD指定记录，DATA_RANGE动态数据范围' AFTER `record_id`,
  ADD COLUMN `scope_json` JSON NULL COMMENT '范围条件、字段类型绑定及授予时来源共享权限约束快照' AFTER `scope_type`,
  ADD COLUMN `start_time` DATETIME(3) NULL COMMENT '授权开始生效时间，为空表示授予后立即生效' AFTER `status`;

UPDATE `biz_tenant_share` s
JOIN `biz_record` r ON r.`id` = s.`record_id` AND r.`tenant_id` = s.`source_tenant_id`
SET s.`module_id` = r.`module_id`;

-- 为被修改表的全部字段补充中文业务备注；旧 *_at 映射保持兼容，新增时间仅使用 *_time。
ALTER TABLE `biz_tenant_share`
  COMMENT = '同系统跨租户记录及动态模块范围共享授权，不改变数据所有权',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '共享授权主键，历史使用记录保持引用',
  MODIFY COLUMN `system_id` BIGINT NOT NULL COMMENT '共享双方所属系统',
  MODIFY COLUMN `source_tenant_id` BIGINT NOT NULL COMMENT '拥有业务数据的来源租户',
  MODIFY COLUMN `target_tenant_id` BIGINT NOT NULL COMMENT '获得访问关系的目标租户',
  MODIFY COLUMN `module_id` BIGINT NOT NULL COMMENT '共享数据所属模块',
  MODIFY COLUMN `record_id` BIGINT NULL COMMENT '指定记录授权的原记录主键，动态范围授权为空',
  MODIFY COLUMN `permission_json` JSON NOT NULL COMMENT '明确允许的业务动作，仍与目标用户角色权限取交集',
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '授权存储状态：ACTIVE有效，REVOKED已撤销',
  MODIFY COLUMN `expires_at` DATETIME(3) NULL COMMENT '授权失效时间，旧接口和字段保持兼容',
  MODIFY COLUMN `granted_by_member_id` BIGINT NOT NULL COMMENT '来源租户授予授权的系统成员',
  MODIFY COLUMN `granted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近授予时间，旧字段保持兼容',
  MODIFY COLUMN `revoked_at` DATETIME(3) NULL COMMENT '授权撤销时间，历史授权不删除',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '授权修改的乐观锁版本',
  ADD KEY `idx_biz_share_module_scope` (`system_id`, `target_tenant_id`, `module_id`, `scope_type`, `status`),
  ADD CONSTRAINT `fk_biz_share_module` FOREIGN KEY (`system_id`, `module_id`) REFERENCES `cfg_module` (`system_id`, `id`),
  ADD CONSTRAINT `ck_biz_share_scope` CHECK (
    (`scope_type` = 'RECORD' AND `record_id` IS NOT NULL AND `scope_json` IS NULL)
    OR (`scope_type` = 'DATA_RANGE' AND `record_id` IS NULL AND JSON_TYPE(`scope_json`) = 'OBJECT' AND `scope_json` IS NOT NULL)
  );

-- source: update/V44__application_icon_preset.sql
-- 应用图标只保存平台预设代码，页面从固定图标库选择，不接收任意文件或原始配置。
-- 现有应用统一回填 api；本迁移保留应用、版本、凭证和调用历史。
ALTER TABLE `app_definition`
  ADD COLUMN `icon_code` VARCHAR(32) NOT NULL DEFAULT 'api' COMMENT '应用预设图标代码' AFTER `application_type`,
  COMMENT = '平台或系统范围内的受控访问应用定义',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用主键',
  MODIFY COLUMN `context_type` VARCHAR(32) NOT NULL COMMENT '应用所属上下文：PLATFORM平台、SYSTEM系统',
  MODIFY COLUMN `platform_id` BIGINT NULL COMMENT '所属平台主键',
  MODIFY COLUMN `owner_system_id` BIGINT NULL COMMENT '所属系统主键，平台应用为空',
  MODIFY COLUMN `owner_tenant_id` BIGINT NULL COMMENT '所属租户主键，平台应用为空',
  MODIFY COLUMN `code` VARCHAR(100) NOT NULL COMMENT '当前上下文内唯一的应用编码',
  MODIFY COLUMN `name` VARCHAR(200) NOT NULL COMMENT '应用名称',
  MODIFY COLUMN `description` VARCHAR(1000) NULL COMMENT '应用用途说明',
  MODIFY COLUMN `application_type` VARCHAR(32) NOT NULL COMMENT '应用类型：SERVICE服务调用、WEBHOOK事件回调',
  MODIFY COLUMN `draft_revision` INT NOT NULL DEFAULT 1 COMMENT '草稿修订号',
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '应用状态：DRAFT草稿、ACTIVE启用、DISABLED停用、DELETED删除',
  MODIFY COLUMN `created_by_account_id` BIGINT NOT NULL COMMENT '创建应用的平台账号主键',
  MODIFY COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，旧字段保持兼容',
  MODIFY COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，旧字段保持兼容',
  MODIFY COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '应用修改的乐观锁版本',
  ADD CONSTRAINT `ck_app_definition_icon` CHECK (`icon_code` IN ('api', 'link', 'workflow', 'database', 'robot', 'message'));

-- source: update/V45__application_credential_expiry_backfill.sql
-- 为旧版本签发且没有失效时间的应用凭证补齐一年有效期。
-- 只更新空值，保留原生效时间、撤销状态、密钥版本和历史调用关联。
UPDATE `app_credential`
SET `expires_at` = DATE_ADD(COALESCE(`valid_from`, `created_at`, CURRENT_TIMESTAMP(3)), INTERVAL 365 DAY)
WHERE `expires_at` IS NULL;

-- source: update/V46__application_bridge_identity.sql
-- 应用跨系统身份映射：草稿保存结构化来源身份与目标执行身份，发布版本只冻结业务引用。
CREATE TABLE `app_bridge_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用身份映射主键',
  `application_id` BIGINT NOT NULL COMMENT '所属应用主键',
  `source_system_id` BIGINT NOT NULL COMMENT '允许调用的来源系统主键',
  `source_tenant_id` BIGINT NOT NULL COMMENT '允许调用的来源租户主键',
  `source_tenant_member_id` BIGINT NOT NULL COMMENT '允许调用的来源租户成员主键',
  `target_system_id` BIGINT NOT NULL COMMENT '目标执行系统主键',
  `target_tenant_id` BIGINT NOT NULL COMMENT '目标执行租户主键',
  `target_tenant_member_id` BIGINT NOT NULL COMMENT '目标执行租户成员主键',
  `status` VARCHAR(32) NOT NULL COMMENT '映射状态：ACTIVE启用、DISABLED停用',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_bridge_identity_source` (`application_id`, `source_system_id`, `source_tenant_id`, `source_tenant_member_id`),
  KEY `idx_app_bridge_identity_target` (`application_id`, `target_system_id`, `target_tenant_id`, `status`),
  CONSTRAINT `fk_app_bridge_identity_application` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_bridge_identity_source_system` FOREIGN KEY (`source_system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_app_bridge_identity_source_tenant` FOREIGN KEY (`source_system_id`, `source_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_source_member` FOREIGN KEY (`source_tenant_id`, `source_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_target_system` FOREIGN KEY (`target_system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_app_bridge_identity_target_tenant` FOREIGN KEY (`target_system_id`, `target_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_target_member` FOREIGN KEY (`target_tenant_id`, `target_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_app_bridge_identity_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_bridge_identity_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用跨系统调用身份映射';

-- source: update/V47__application_provider_connection.sql
-- 外部平台接入复用现有应用授权边界；连接、密钥版本、事件路由和回调去重分别持久化。
CREATE TABLE `app_provider_connection` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台连接主键',
  `application_id` BIGINT NOT NULL COMMENT '所属受控应用主键',
  `provider_code` VARCHAR(32) NOT NULL COMMENT '平台编码：WECOM企业微信、FEISHU飞书、DINGTALK钉钉、WECHAT微信',
  `product_code` VARCHAR(64) NOT NULL COMMENT '平台产品形态编码',
  `connection_key` VARCHAR(64) NOT NULL COMMENT '不可猜测的公开回调连接标识',
  `display_name` VARCHAR(200) NOT NULL COMMENT '连接显示名称',
  `tenant_key` VARCHAR(255) NOT NULL COMMENT '平台租户标识，例如企业微信CorpID',
  `application_key` VARCHAR(255) NULL COMMENT '平台应用标识，例如企业微信AgentID',
  `status` VARCHAR(32) NOT NULL COMMENT '连接状态：DRAFT草稿、ACTIVE启用、DISABLED停用',
  `diagnostic_status` VARCHAR(32) NOT NULL COMMENT '诊断状态：NOT_RUN未执行、LOCAL_PASSED本地协议通过、FAILED失败、EXTERNAL_PASSED外部联通通过',
  `diagnostic_summary` VARCHAR(1000) NULL COMMENT '不含敏感材料的最近诊断摘要',
  `diagnostic_time` DATETIME(3) NULL COMMENT '最近诊断时间',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_connection_key` (`connection_key`),
  UNIQUE KEY `uk_app_provider_connection_tenant` (`application_id`, `provider_code`, `product_code`, `tenant_key`),
  KEY `idx_app_provider_connection_status` (`application_id`, `status`),
  CONSTRAINT `fk_app_provider_connection_application` FOREIGN KEY (`application_id`) REFERENCES `app_definition` (`id`),
  CONSTRAINT `fk_app_provider_connection_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_connection_provider` CHECK (`provider_code` IN ('WECOM', 'FEISHU', 'DINGTALK', 'WECHAT')),
  CONSTRAINT `chk_app_provider_connection_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'DISABLED')),
  CONSTRAINT `chk_app_provider_connection_diagnostic` CHECK (`diagnostic_status` IN ('NOT_RUN', 'LOCAL_PASSED', 'FAILED', 'EXTERNAL_PASSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台连接';

CREATE TABLE `app_provider_secret` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台密钥版本主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `secret_type` VARCHAR(32) NOT NULL COMMENT '密钥用途：API_SECRET接口密钥、CALLBACK_TOKEN回调令牌、ENCODING_AES_KEY回调加密密钥',
  `secret_version` INT NOT NULL COMMENT '同一连接同一用途的递增版本号',
  `secret_ciphertext` VARCHAR(4000) NOT NULL COMMENT '使用应用主密钥加密后的密文',
  `secret_hint` VARCHAR(32) NOT NULL COMMENT '仅用于辨认版本的脱敏尾号',
  `status` VARCHAR(32) NOT NULL COMMENT '密钥状态：ACTIVE启用、REVOKED已撤销',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `revoked_time` DATETIME(3) NULL COMMENT '撤销时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_secret_version` (`connection_id`, `secret_type`, `secret_version`),
  KEY `idx_app_provider_secret_active` (`connection_id`, `secret_type`, `status`),
  CONSTRAINT `fk_app_provider_secret_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_secret_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_secret_type` CHECK (`secret_type` IN ('API_SECRET', 'CALLBACK_TOKEN', 'ENCODING_AES_KEY')),
  CONSTRAINT `chk_app_provider_secret_status` CHECK (`status` IN ('ACTIVE', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台密钥版本';

CREATE TABLE `app_provider_route` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台事件路由主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `event_code` VARCHAR(100) NOT NULL COMMENT '规范化平台事件编码',
  `resource_type` VARCHAR(32) NOT NULL COMMENT '已发布应用授权资源类型',
  `resource_id` VARCHAR(100) NOT NULL COMMENT '已发布应用授权资源业务编码',
  `action_code` VARCHAR(64) NOT NULL COMMENT '已发布应用授权动作编码',
  `target_system_id` BIGINT NULL COMMENT '目标系统主键',
  `target_tenant_id` BIGINT NULL COMMENT '目标租户主键',
  `status` VARCHAR(32) NOT NULL COMMENT '路由状态：ACTIVE启用、DISABLED停用',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_route_event` (`connection_id`, `event_code`),
  KEY `idx_app_provider_route_target` (`resource_type`, `resource_id`, `action_code`, `status`),
  CONSTRAINT `fk_app_provider_route_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_route_system` FOREIGN KEY (`target_system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_app_provider_route_tenant` FOREIGN KEY (`target_system_id`, `target_tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `chk_app_provider_route_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台事件路由';

CREATE TABLE `app_provider_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台回调事件主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `provider_event_id` VARCHAR(255) NOT NULL COMMENT '平台事件幂等标识或服务端稳定摘要',
  `event_code` VARCHAR(100) NOT NULL COMMENT '规范化平台事件编码',
  `sender_external_id` VARCHAR(255) NULL COMMENT '平台侧发送人标识',
  `payload_hash` VARCHAR(64) NOT NULL COMMENT '解密后原始载荷SHA-256摘要',
  `status` VARCHAR(32) NOT NULL COMMENT '处理状态：RECEIVED已接收、PROCESSED已完成、IGNORED无路由、FAILED失败',
  `application_call_id` BIGINT NULL COMMENT '进入应用网关后生成的调用主键',
  `response_code` VARCHAR(64) NULL COMMENT '安全的处理结果编码',
  `received_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收时间',
  `processed_time` DATETIME(3) NULL COMMENT '处理完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_event_dedup` (`connection_id`, `provider_event_id`),
  KEY `idx_app_provider_event_status` (`connection_id`, `status`, `received_time`),
  CONSTRAINT `fk_app_provider_event_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_event_call` FOREIGN KEY (`application_call_id`) REFERENCES `app_call` (`id`),
  CONSTRAINT `chk_app_provider_event_status` CHECK (`status` IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台回调事件';

-- source: update/V48__application_provider_identity_delivery.sql
-- 企业微信成员映射把平台侧用户绑定到当前应用范围内的真实成员；外部投递扩展继续复用统一消息和投递记录。
CREATE TABLE `app_provider_identity` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台成员映射主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `external_user_id` VARCHAR(255) NOT NULL COMMENT '平台侧稳定用户标识，例如企业微信UserID',
  `target_type` VARCHAR(32) NOT NULL COMMENT '目标成员类型：PLATFORM平台成员、SYSTEM系统租户成员',
  `target_platform_id` BIGINT NULL COMMENT '目标平台主键',
  `target_platform_member_id` BIGINT NULL COMMENT '目标平台成员主键',
  `target_system_id` BIGINT NULL COMMENT '目标系统主键',
  `target_tenant_id` BIGINT NULL COMMENT '目标租户主键',
  `target_tenant_member_id` BIGINT NULL COMMENT '目标租户成员主键',
  `status` VARCHAR(32) NOT NULL COMMENT '映射状态：ACTIVE启用、DISABLED停用',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建人平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_identity_external` (`connection_id`, `external_user_id`),
  KEY `idx_app_provider_identity_target_platform` (`target_platform_id`, `target_platform_member_id`, `status`),
  KEY `idx_app_provider_identity_target_system` (`target_system_id`, `target_tenant_id`, `target_tenant_member_id`, `status`),
  CONSTRAINT `fk_app_provider_identity_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_identity_platform_member` FOREIGN KEY (`target_platform_id`, `target_platform_member_id`) REFERENCES `plat_member` (`platform_id`, `id`),
  CONSTRAINT `fk_app_provider_identity_tenant_member` FOREIGN KEY (`target_tenant_id`, `target_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_app_provider_identity_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_identity_status` CHECK (`status` IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT `chk_app_provider_identity_target` CHECK (
    (`target_type` = 'PLATFORM' AND `target_platform_id` IS NOT NULL AND `target_platform_member_id` IS NOT NULL
      AND `target_system_id` IS NULL AND `target_tenant_id` IS NULL AND `target_tenant_member_id` IS NULL)
    OR
    (`target_type` = 'SYSTEM' AND `target_platform_id` IS NULL AND `target_platform_member_id` IS NULL
      AND `target_system_id` IS NOT NULL AND `target_tenant_id` IS NOT NULL AND `target_tenant_member_id` IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台成员映射';

CREATE TABLE `app_provider_delivery` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '外部平台消息投递扩展主键',
  `connection_id` BIGINT NOT NULL COMMENT '所属外部平台连接主键',
  `identity_id` BIGINT NOT NULL COMMENT '目标外部平台成员映射主键',
  `message_id` BIGINT NOT NULL COMMENT '统一消息主键',
  `message_delivery_id` BIGINT NOT NULL COMMENT '统一消息投递主键',
  `idempotency_key` VARCHAR(255) NOT NULL COMMENT '同一连接内的业务幂等键',
  `request_hash` VARCHAR(64) NOT NULL COMMENT '规范化投递请求SHA-256摘要',
  `deep_link` VARCHAR(1000) NOT NULL COMMENT '按当前上下文生成的站内业务深链',
  `status` VARCHAR(32) NOT NULL COMMENT '投递状态：PENDING待发送、BLOCKED_EXTERNAL缺外部条件、SENT已发送、DELIVERED已送达、FAILED失败、CANCELED已撤销',
  `provider_message_id` VARCHAR(255) NULL COMMENT '平台返回的消息标识',
  `error_code` VARCHAR(100) NULL COMMENT '不含敏感信息的最近错误编码',
  `created_by_account_id` BIGINT NOT NULL COMMENT '发起人平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `sent_time` DATETIME(3) NULL COMMENT '实际发送时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_provider_delivery_idempotency` (`connection_id`, `idempotency_key`),
  UNIQUE KEY `uk_app_provider_delivery_message_delivery` (`message_delivery_id`),
  KEY `idx_app_provider_delivery_status` (`connection_id`, `status`, `create_time`),
  CONSTRAINT `fk_app_provider_delivery_connection` FOREIGN KEY (`connection_id`) REFERENCES `app_provider_connection` (`id`),
  CONSTRAINT `fk_app_provider_delivery_identity` FOREIGN KEY (`identity_id`) REFERENCES `app_provider_identity` (`id`),
  CONSTRAINT `fk_app_provider_delivery_message` FOREIGN KEY (`message_id`) REFERENCES `msg_message` (`id`),
  CONSTRAINT `fk_app_provider_delivery_message_delivery` FOREIGN KEY (`message_delivery_id`) REFERENCES `msg_delivery` (`id`),
  CONSTRAINT `fk_app_provider_delivery_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `chk_app_provider_delivery_status` CHECK (`status` IN ('PENDING', 'BLOCKED_EXTERNAL', 'SENT', 'DELIVERED', 'FAILED', 'CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应用外部平台消息投递扩展';

-- source: update/V49__file_storage_credential_capacity.sql
-- 对象存储访问密钥使用随机向量加密，密文长度不能受早期短引用字段限制。
ALTER TABLE `file_storage_backend`
  MODIFY COLUMN `credential_ref` TEXT NULL COMMENT '对象存储访问凭证密文，仅服务端解密，接口不得回传';

-- source: update/V50__work_task_lifecycle.sql
-- 补齐项目、任务组和任务主链路所需的结构化字段；成员类型继续复用 work_task_member，过程记录继续复用 work_task_history。

ALTER TABLE `work_project`
  COMMENT = '工作项目',
  ADD COLUMN `customer_name` VARCHAR(200) NULL COMMENT '客户名称' AFTER `description`,
  ADD COLUMN `internal_department_name` VARCHAR(200) NULL COMMENT '内部部门名称' AFTER `customer_name`,
  ADD COLUMN `stage` VARCHAR(64) NULL COMMENT '项目阶段' AFTER `internal_department_name`,
  ADD COLUMN `labels_json` JSON NULL COMMENT '项目标签列表' AFTER `stage`,
  ADD COLUMN `business_type` VARCHAR(64) NULL COMMENT '关联业务类型' AFTER `labels_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL COMMENT '关联业务记录标识' AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL COMMENT '关联业务记录标题' AFTER `business_id`,
  ADD KEY `idx_work_project_business` (`system_id`, `tenant_id`, `business_type`, `business_id`);

ALTER TABLE `work_task_group`
  COMMENT = '项目任务组',
  ADD COLUMN `owner_account_id` BIGINT NULL COMMENT '任务组负责人账号标识' AFTER `status`,
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL COMMENT '任务组负责人租户成员标识' AFTER `owner_account_id`,
  ADD COLUMN `due_time` DATETIME(3) NULL COMMENT '任务组截止时间' AFTER `owner_tenant_member_id`,
  ADD COLUMN `progress_percent` DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '任务组完成百分比' AFTER `due_time`,
  ADD KEY `idx_work_task_group_owner` (`owner_account_id`, `status`, `due_time`),
  ADD CONSTRAINT `fk_work_task_group_owner`
    FOREIGN KEY (`owner_account_id`) REFERENCES `plat_account` (`id`),
  ADD CONSTRAINT `fk_work_task_group_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task`
  COMMENT = '工作任务',
  ADD COLUMN `estimated_hours` DECIMAL(8, 2) NULL COMMENT '预计工时' AFTER `progress_percent`,
  ADD COLUMN `actual_hours` DECIMAL(8, 2) NULL COMMENT '实际工时' AFTER `estimated_hours`,
  ADD COLUMN `labels_json` JSON NULL COMMENT '任务标签列表' AFTER `actual_hours`,
  ADD COLUMN `checklist_json` JSON NULL COMMENT '任务检查清单' AFTER `labels_json`,
  ADD COLUMN `completion_note` VARCHAR(2000) NULL COMMENT '完成说明' AFTER `completed_at`,
  ADD COLUMN `completion_submitted_time` DATETIME(3) NULL COMMENT '提交完成时间' AFTER `completion_note`,
  ADD COLUMN `completion_accepted_time` DATETIME(3) NULL COMMENT '验收通过时间' AFTER `completion_submitted_time`,
  ADD COLUMN `archive_time` DATETIME(3) NULL COMMENT '归档时间' AFTER `completion_accepted_time`,
  ADD KEY `idx_work_task_archive` (`system_id`, `tenant_id`, `archive_time`);

-- source: update/V51__work_task_soft_delete.sql
-- 任务删除保留审计和历史记录，列表与详情按 delete_time 隐藏，避免物理删除破坏关联证据。

ALTER TABLE `work_task`
  ADD COLUMN `delete_time` DATETIME(3) NULL COMMENT '任务删除时间' AFTER `archive_time`,
  ADD KEY `idx_work_task_delete` (`system_id`, `tenant_id`, `delete_time`);

-- source: update/V52__work_log_business_flow.sql
-- 工作日志业务化：补充结构化日报内容、可见范围、选材快照和沟通记录。
-- 旧 content_text 保留为兼容摘要；迁移不删除旧数据，历史日志仍可直接读取。

ALTER TABLE `work_log`
  ADD COLUMN `completed_text` TEXT NULL COMMENT '今日已完成事项' AFTER `content_text`,
  ADD COLUMN `in_progress_text` TEXT NULL COMMENT '当前进行中事项' AFTER `completed_text`,
  ADD COLUMN `next_plan_text` TEXT NULL COMMENT '下一工作日计划' AFTER `in_progress_text`,
  ADD COLUMN `risk_text` TEXT NULL COMMENT '风险与阻塞说明' AFTER `next_plan_text`,
  ADD COLUMN `assistance_text` TEXT NULL COMMENT '需要协助的事项' AFTER `risk_text`,
  ADD COLUMN `visibility_type` VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '可见范围：本人、直属管理、项目成员、同部门或指定角色' AFTER `status`,
  ADD COLUMN `visible_role_ids_json` JSON NULL COMMENT '指定可见角色标识快照' AFTER `visibility_type`,
  ADD COLUMN `submitted_time` DATETIME(3) NULL COMMENT '最近一次提交时间' AFTER `visible_role_ids_json`,
  ADD COLUMN `withdrawn_time` DATETIME(3) NULL COMMENT '最近一次撤回时间' AFTER `submitted_time`,
  ADD KEY `idx_work_log_visibility` (`system_id`, `tenant_id`, `visibility_type`, `work_date`);

UPDATE `work_log`
SET `completed_text` = `content_text`
WHERE `completed_text` IS NULL;

CREATE TABLE `work_log_material` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志选材主键',
  `work_log_id` BIGINT NOT NULL COMMENT '工作日志主键',
  `material_type` VARCHAR(32) NOT NULL COMMENT '材料类型：任务、待办、消息或业务操作',
  `material_id` VARCHAR(100) NOT NULL COMMENT '来源材料标识',
  `material_title` VARCHAR(500) NOT NULL COMMENT '选中时的材料标题快照',
  `material_summary` VARCHAR(2000) NULL COMMENT '选中时的材料摘要快照',
  `source_time` DATETIME(3) NULL COMMENT '来源材料发生时间',
  `created_by_account_id` BIGINT NOT NULL COMMENT '选择材料的平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '材料快照创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_log_material` (`work_log_id`, `material_type`, `material_id`),
  KEY `idx_work_log_material_source` (`material_type`, `material_id`),
  CONSTRAINT `fk_work_log_material_log` FOREIGN KEY (`work_log_id`) REFERENCES `work_log` (`id`),
  CONSTRAINT `fk_work_log_material_actor` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作日志人工选材快照';

CREATE TABLE `work_log_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志沟通记录主键',
  `work_log_id` BIGINT NOT NULL COMMENT '工作日志主键',
  `comment_type` VARCHAR(32) NOT NULL COMMENT '沟通类型：评论或反馈',
  `comment_text` VARCHAR(2000) NOT NULL COMMENT '评论或反馈正文',
  `created_by_account_id` BIGINT NOT NULL COMMENT '发表人的平台账号主键',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发表时间',
  PRIMARY KEY (`id`),
  KEY `idx_work_log_comment` (`work_log_id`, `created_time`),
  CONSTRAINT `fk_work_log_comment_log` FOREIGN KEY (`work_log_id`) REFERENCES `work_log` (`id`),
  CONSTRAINT `fk_work_log_comment_actor` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作日志评论与管理反馈';

-- source: update/V53__todo_contact_rule.sql
-- 今日联系规则把已发布模块的日期字段投影为当前负责人的待办，不复制业务记录，也不要求用户填写 JSON。
CREATE TABLE `todo_contact_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '今日联系规则主键',
  `context_type` VARCHAR(32) NOT NULL COMMENT '配置上下文类型：平台或系统',
  `platform_id` BIGINT NOT NULL COMMENT '所属平台主键',
  `system_id` BIGINT NULL COMMENT '所属系统主键，平台配置时为空',
  `tenant_id` BIGINT NULL COMMENT '所属租户主键，平台配置时为空',
  `scope_key` VARCHAR(160) NOT NULL COMMENT '平台、系统和租户组成的稳定配置范围键',
  `name` VARCHAR(200) NOT NULL COMMENT '规则显示名称',
  `module_code` VARCHAR(100) NOT NULL COMMENT '已发布业务模块编码',
  `module_name_snapshot` VARCHAR(200) NOT NULL COMMENT '保存规则时的业务模块名称快照',
  `contact_field_code` VARCHAR(100) NOT NULL COMMENT '用于计算联系日期的字段编码',
  `contact_field_name_snapshot` VARCHAR(200) NOT NULL COMMENT '保存规则时的日期字段名称快照',
  `contact_field_type` VARCHAR(32) NOT NULL COMMENT '联系字段类型：日期或日期时间',
  `lead_days` INT NOT NULL DEFAULT 0 COMMENT '提前生成待办的天数，零表示字段日期当天',
  `priority` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '生成待办的优先级',
  `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '规则状态：草稿、已发布或已停用',
  `created_by_account_id` BIGINT NOT NULL COMMENT '创建规则的平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '规则创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '规则最后更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_todo_contact_rule_scope` (`scope_key`, `module_code`, `contact_field_code`, `lead_days`),
  KEY `idx_todo_contact_rule_runtime` (`system_id`, `tenant_id`, `status`),
  CONSTRAINT `fk_todo_contact_rule_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_todo_contact_rule_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_todo_contact_rule_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `sys_tenant` (`id`),
  CONSTRAINT `fk_todo_contact_rule_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务模块日期字段的今日联系待办生成规则';
