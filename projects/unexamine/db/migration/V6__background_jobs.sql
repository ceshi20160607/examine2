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
