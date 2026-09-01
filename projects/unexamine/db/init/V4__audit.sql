-- Initial baseline: append-only audit, field changes and permanent retention markers.

CREATE TABLE `audit_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `trace_id` VARCHAR(64) NOT NULL,
  `request_id` VARCHAR(64) NULL,
  `context_type` VARCHAR(32) NOT NULL,
  `actor_account_id` BIGINT NULL,
  `platform_id` BIGINT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `member_id` BIGINT NULL,
  `application_id` BIGINT NULL,
  `event_category` VARCHAR(64) NOT NULL,
  `event_code` VARCHAR(100) NOT NULL,
  `object_type` VARCHAR(100) NULL,
  `object_id` VARCHAR(100) NULL,
  `result_code` VARCHAR(64) NOT NULL,
  `client_ip` VARCHAR(64) NULL,
  `user_agent` VARCHAR(1000) NULL,
  `permission_snapshot` JSON NULL,
  `detail_json` JSON NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_trace` (`trace_id`),
  KEY `idx_audit_object` (`object_type`, `object_id`, `occurred_at`),
  KEY `idx_audit_context` (`system_id`, `tenant_id`, `occurred_at`),
  KEY `idx_audit_category` (`event_category`, `occurred_at`),
  CONSTRAINT `fk_audit_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_audit_platform` FOREIGN KEY (`platform_id`) REFERENCES `plat_platform` (`id`),
  CONSTRAINT `fk_audit_system` FOREIGN KEY (`system_id`) REFERENCES `sys_system` (`id`),
  CONSTRAINT `fk_audit_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_audit_member` FOREIGN KEY (`system_id`, `member_id`) REFERENCES `sys_member` (`system_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_field_change` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `audit_event_id` BIGINT NOT NULL,
  `field_code` VARCHAR(100) NOT NULL,
  `value_type` VARCHAR(32) NOT NULL,
  `before_value_json` JSON NULL,
  `after_value_json` JSON NULL,
  `sensitivity` VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_field_event` (`audit_event_id`),
  KEY `idx_audit_field_code` (`field_code`, `created_at`),
  CONSTRAINT `fk_audit_field_event` FOREIGN KEY (`audit_event_id`) REFERENCES `audit_event` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `audit_retention_marker` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `context_type` VARCHAR(32) NOT NULL,
  `system_id` BIGINT NULL,
  `tenant_id` BIGINT NULL,
  `object_type` VARCHAR(100) NOT NULL,
  `object_id` VARCHAR(100) NOT NULL,
  `business_key` VARCHAR(255) NULL,
  `snapshot_hash` VARCHAR(64) NOT NULL,
  `snapshot_json` JSON NULL,
  `purge_reason` VARCHAR(1000) NOT NULL,
  `purged_by_account_id` BIGINT NOT NULL,
  `purged_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_retention_object` (`context_type`, `system_id`, `tenant_id`, `object_type`, `object_id`),
  KEY `idx_audit_retention_time` (`purged_at`),
  CONSTRAINT `fk_audit_retention_actor` FOREIGN KEY (`purged_by_account_id`) REFERENCES `plat_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
CREATE TRIGGER `trg_audit_event_immutable_update`
BEFORE UPDATE ON `audit_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is immutable';
END$$
CREATE TRIGGER `trg_audit_event_immutable_delete`
BEFORE DELETE ON `audit_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_event is immutable';
END$$
CREATE TRIGGER `trg_audit_field_immutable_update`
BEFORE UPDATE ON `audit_field_change` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_field_change is immutable';
END$$
CREATE TRIGGER `trg_audit_field_immutable_delete`
BEFORE DELETE ON `audit_field_change` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_field_change is immutable';
END$$
CREATE TRIGGER `trg_audit_retention_immutable_update`
BEFORE UPDATE ON `audit_retention_marker` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_retention_marker is immutable';
END$$
CREATE TRIGGER `trg_audit_retention_immutable_delete`
BEFORE DELETE ON `audit_retention_marker` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_retention_marker is immutable';
END$$
DELIMITER ;
