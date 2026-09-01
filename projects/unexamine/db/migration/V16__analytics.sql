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
