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
