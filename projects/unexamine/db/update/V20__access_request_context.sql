ALTER TABLE `sys_access_request`
  ADD COLUMN `identity_provider` VARCHAR(100) NOT NULL DEFAULT 'LOCAL' AFTER `account_id`,
  ADD COLUMN `external_user_id` VARCHAR(255) NULL AFTER `identity_provider`,
  ADD COLUMN `requested_role` VARCHAR(100) NULL AFTER `request_reason`,
  ADD COLUMN `approved_role_ids_json` JSON NULL AFTER `decision_comment`,
  ADD COLUMN `approved_data_scope_json` JSON NULL AFTER `approved_role_ids_json`,
  ADD COLUMN `request_trace_id` VARCHAR(64) NULL AFTER `approved_data_scope_json`,
  ADD COLUMN `decision_trace_id` VARCHAR(64) NULL AFTER `request_trace_id`,
  ADD KEY `idx_sys_access_external_identity` (`identity_provider`, `external_user_id`),
  ADD KEY `idx_sys_access_request_trace` (`request_trace_id`);

UPDATE `sys_access_request`
SET `request_trace_id` = CONCAT('migration-', `id`)
WHERE `request_trace_id` IS NULL;

ALTER TABLE `sys_access_request`
  MODIFY COLUMN `request_trace_id` VARCHAR(64) NOT NULL;
