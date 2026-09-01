ALTER TABLE `biz_record_index`
  ADD COLUMN `unique_key_hash` VARCHAR(64) NULL AFTER `index_key_hash`,
  ADD UNIQUE KEY `uk_biz_record_index_unique_value`
    (`tenant_id`, `module_id`, `query_index_id`, `unique_key_hash`);
