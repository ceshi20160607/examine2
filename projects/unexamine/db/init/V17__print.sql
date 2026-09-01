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
