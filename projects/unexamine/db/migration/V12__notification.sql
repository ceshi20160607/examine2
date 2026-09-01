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
