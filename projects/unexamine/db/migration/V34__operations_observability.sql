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
