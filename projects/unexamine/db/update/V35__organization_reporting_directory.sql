-- Unified tenant organization directory: position and reporting line are business relations,
-- while stable member identifiers remain internal implementation details.
CREATE TABLE `sys_member_reporting_line` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `system_id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `tenant_member_id` BIGINT NOT NULL,
  `manager_tenant_member_id` BIGINT NULL,
  `position_title` VARCHAR(100) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_member_reporting_member` (`tenant_id`, `tenant_member_id`),
  KEY `idx_sys_member_reporting_manager` (`tenant_id`, `manager_tenant_member_id`),
  CONSTRAINT `fk_sys_member_reporting_tenant` FOREIGN KEY (`system_id`, `tenant_id`) REFERENCES `sys_tenant` (`system_id`, `id`),
  CONSTRAINT `fk_sys_member_reporting_member` FOREIGN KEY (`tenant_id`, `tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`),
  CONSTRAINT `fk_sys_member_reporting_manager` FOREIGN KEY (`tenant_id`, `manager_tenant_member_id`) REFERENCES `sys_tenant_member` (`tenant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
