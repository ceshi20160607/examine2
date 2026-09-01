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
