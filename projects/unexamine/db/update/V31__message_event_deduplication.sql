-- Message events must be idempotent within one platform/system/tenant context.
ALTER TABLE `msg_message`
  ADD COLUMN `dedup_context_key` VARCHAR(160)
    GENERATED ALWAYS AS (
      CONCAT(`context_type`, ':', IFNULL(`platform_id`, 0), ':', IFNULL(`system_id`, 0), ':', IFNULL(`tenant_id`, 0))
    ) STORED,
  ADD UNIQUE KEY `uk_msg_message_event_dedup` (`dedup_context_key`, `source_type`, `source_id`);
