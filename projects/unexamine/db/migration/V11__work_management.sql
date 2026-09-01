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
