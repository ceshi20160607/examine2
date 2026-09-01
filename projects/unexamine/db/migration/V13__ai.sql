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
