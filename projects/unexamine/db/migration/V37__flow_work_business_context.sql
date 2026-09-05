-- Flow runtime layers and business-object work context.
-- Published definitions remain in flow_version; runtime execution, history and jobs are separated here.

ALTER TABLE `flow_instance`
  ADD COLUMN `started_by_tenant_member_id` BIGINT NULL AFTER `started_by_account_id`,
  ADD KEY `idx_flow_instance_starter_member` (`tenant_id`, `started_by_tenant_member_id`),
  ADD CONSTRAINT `fk_flow_instance_starter_member`
    FOREIGN KEY (`started_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_task`
  ADD COLUMN `assignee_tenant_member_id` BIGINT NULL AFTER `assignee_account_id`,
  ADD KEY `idx_flow_task_assignee_member` (`assignee_tenant_member_id`, `status`, `due_at`),
  ADD CONSTRAINT `fk_flow_task_assignee_member`
    FOREIGN KEY (`assignee_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_action`
  ADD COLUMN `acted_by_tenant_member_id` BIGINT NULL AFTER `acted_by_account_id`,
  ADD KEY `idx_flow_action_actor_member` (`acted_by_tenant_member_id`, `acted_at`),
  ADD CONSTRAINT `fk_flow_action_actor_member`
    FOREIGN KEY (`acted_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `flow_exception`
  ADD COLUMN `resolved_by_tenant_member_id` BIGINT NULL AFTER `resolved_by_account_id`,
  ADD CONSTRAINT `fk_flow_exception_resolver_member`
    FOREIGN KEY (`resolved_by_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

CREATE TABLE `flow_execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `parent_execution_id` BIGINT NULL,
  `node_key` VARCHAR(100) NOT NULL,
  `node_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `entered_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `left_at` DATETIME(3) NULL,
  `result_code` VARCHAR(64) NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_flow_execution_active` (`instance_id`, `status`, `entered_at`),
  CONSTRAINT `fk_flow_execution_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_execution_parent` FOREIGN KEY (`parent_execution_id`) REFERENCES `flow_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_history_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `execution_id` BIGINT NULL,
  `task_id` BIGINT NULL,
  `node_key` VARCHAR(100) NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `event_name` VARCHAR(200) NOT NULL,
  `actor_account_id` BIGINT NULL,
  `actor_tenant_member_id` BIGINT NULL,
  `before_status` VARCHAR(32) NULL,
  `after_status` VARCHAR(32) NULL,
  `detail_json` JSON NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_flow_history_instance` (`instance_id`, `occurred_at`),
  KEY `idx_flow_history_task` (`task_id`, `occurred_at`),
  CONSTRAINT `fk_flow_history_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_history_execution` FOREIGN KEY (`execution_id`) REFERENCES `flow_execution` (`id`),
  CONSTRAINT `fk_flow_history_task` FOREIGN KEY (`task_id`) REFERENCES `flow_task` (`id`),
  CONSTRAINT `fk_flow_history_actor` FOREIGN KEY (`actor_account_id`) REFERENCES `plat_account` (`id`),
  CONSTRAINT `fk_flow_history_actor_member` FOREIGN KEY (`actor_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flow_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `execution_id` BIGINT NULL,
  `node_key` VARCHAR(100) NULL,
  `job_type` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `payload_json` JSON NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `max_attempts` INT NOT NULL DEFAULT 3,
  `next_run_at` DATETIME(3) NOT NULL,
  `locked_at` DATETIME(3) NULL,
  `locked_by` VARCHAR(100) NULL,
  `last_error_code` VARCHAR(100) NULL,
  `last_error_message` VARCHAR(2000) NULL,
  `completed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_job_idempotency` (`instance_id`, `idempotency_key`),
  KEY `idx_flow_job_due` (`status`, `next_run_at`),
  CONSTRAINT `fk_flow_job_instance` FOREIGN KEY (`instance_id`) REFERENCES `flow_instance` (`id`),
  CONSTRAINT `fk_flow_job_execution` FOREIGN KEY (`execution_id`) REFERENCES `flow_execution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `work_project`
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL AFTER `owner_account_id`,
  ADD CONSTRAINT `fk_work_project_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_project_member`
  ADD COLUMN `tenant_member_id` BIGINT NULL AFTER `account_id`,
  ADD KEY `idx_work_project_member_tenant_member` (`tenant_member_id`, `project_id`),
  ADD CONSTRAINT `fk_work_project_member_tenant_member`
    FOREIGN KEY (`tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task`
  ADD COLUMN `owner_tenant_member_id` BIGINT NULL AFTER `owner_account_id`,
  ADD COLUMN `business_type` VARCHAR(64) NULL AFTER `custom_values_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL AFTER `business_id`,
  ADD KEY `idx_work_task_owner_member` (`owner_tenant_member_id`, `status`, `due_at`),
  ADD KEY `idx_work_task_business` (`system_id`, `tenant_id`, `business_type`, `business_id`),
  ADD CONSTRAINT `fk_work_task_owner_member`
    FOREIGN KEY (`owner_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_task_member`
  ADD COLUMN `tenant_member_id` BIGINT NULL AFTER `account_id`,
  ADD KEY `idx_work_task_member_tenant_member` (`tenant_member_id`, `task_id`),
  ADD CONSTRAINT `fk_work_task_member_tenant_member`
    FOREIGN KEY (`tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

ALTER TABLE `work_log`
  ADD COLUMN `author_tenant_member_id` BIGINT NULL AFTER `author_account_id`,
  ADD COLUMN `business_type` VARCHAR(64) NULL AFTER `custom_values_json`,
  ADD COLUMN `business_id` VARCHAR(100) NULL AFTER `business_type`,
  ADD COLUMN `business_title` VARCHAR(500) NULL AFTER `business_id`,
  ADD KEY `idx_work_log_author_member` (`author_tenant_member_id`, `work_date`),
  ADD KEY `idx_work_log_business` (`system_id`, `tenant_id`, `business_type`, `business_id`),
  ADD CONSTRAINT `fk_work_log_author_member`
    FOREIGN KEY (`author_tenant_member_id`) REFERENCES `sys_tenant_member` (`id`);

UPDATE `flow_instance` fi
JOIN `sys_member` sm ON sm.system_id = fi.system_id AND sm.account_id = fi.started_by_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = fi.tenant_id
SET fi.started_by_tenant_member_id = stm.id
WHERE fi.context_type = 'SYSTEM' AND fi.started_by_tenant_member_id IS NULL;

UPDATE `flow_task` ft
JOIN `flow_instance` fi ON fi.id = ft.instance_id
JOIN `sys_member` sm ON sm.system_id = fi.system_id AND sm.account_id = ft.assignee_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = fi.tenant_id
SET ft.assignee_tenant_member_id = stm.id
WHERE fi.context_type = 'SYSTEM' AND ft.assignee_tenant_member_id IS NULL;

UPDATE `work_project` wp
JOIN `sys_member` sm ON sm.system_id = wp.system_id AND sm.account_id = wp.owner_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wp.tenant_id
SET wp.owner_tenant_member_id = stm.id
WHERE wp.context_type = 'SYSTEM' AND wp.owner_tenant_member_id IS NULL;

UPDATE `work_task` wt
JOIN `sys_member` sm ON sm.system_id = wt.system_id AND sm.account_id = wt.owner_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wt.tenant_id
SET wt.owner_tenant_member_id = stm.id
WHERE wt.context_type = 'SYSTEM' AND wt.owner_tenant_member_id IS NULL;

UPDATE `work_log` wl
JOIN `sys_member` sm ON sm.system_id = wl.system_id AND sm.account_id = wl.author_account_id
JOIN `sys_tenant_member` stm ON stm.system_member_id = sm.id AND stm.tenant_id = wl.tenant_id
SET wl.author_tenant_member_id = stm.id
WHERE wl.context_type = 'SYSTEM' AND wl.author_tenant_member_id IS NULL;

DELIMITER $$
CREATE TRIGGER `trg_flow_history_event_immutable_update`
BEFORE UPDATE ON `flow_history_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_history_event is immutable';
END$$
CREATE TRIGGER `trg_flow_history_event_immutable_delete`
BEFORE DELETE ON `flow_history_event` FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'flow_history_event is immutable';
END$$
DELIMITER ;
