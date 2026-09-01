-- Complete the durable application-call security and observability contract.

ALTER TABLE `app_call`
  ADD COLUMN `request_timestamp` DATETIME(3) NOT NULL AFTER `trace_id`,
  ADD COLUMN `nonce` VARCHAR(128) NOT NULL AFTER `request_timestamp`,
  ADD COLUMN `permission_snapshot_json` JSON NULL AFTER `request_hash`,
  ADD COLUMN `response_json` JSON NULL AFTER `response_code`,
  ADD COLUMN `target_reference` VARCHAR(255) NULL AFTER `response_json`,
  ADD COLUMN `replay_count` INT NOT NULL DEFAULT 0 AFTER `target_reference`,
  ADD UNIQUE KEY `uk_app_call_nonce` (`application_id`, `nonce`);
