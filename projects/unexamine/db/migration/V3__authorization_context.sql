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
