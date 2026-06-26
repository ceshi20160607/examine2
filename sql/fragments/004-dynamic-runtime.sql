-- TASK-DBA-004: dynamic runtime record, value, index, relation, history and sequence schema.

CREATE TABLE IF NOT EXISTS un_module_dynamic_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  record_no VARCHAR(120) NULL COMMENT 'field',
  title VARCHAR(255) NOT NULL COMMENT 'field',
  status VARCHAR(80) NULL COMMENT 'field',
  owner_member_id BIGINT NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  created_by BIGINT NOT NULL COMMENT 'field',
  updated_by BIGINT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  KEY idx_record_scope (system_id, tenant_id, module_id, deleted),
  KEY idx_record_owner (system_id, tenant_id, module_id, owner_member_id),
  KEY idx_record_status (system_id, tenant_id, module_id, status),
  KEY idx_record_updated (system_id, tenant_id, module_id, updated_at)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_value (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  record_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  field_id BIGINT NOT NULL COMMENT 'field',
  child_row_id BIGINT NULL COMMENT 'field',
  value_text TEXT NULL COMMENT 'field',
  value_number DECIMAL(24,6) NULL COMMENT 'field',
  value_datetime DATETIME NULL COMMENT 'field',
  value_json JSON NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_record_field_row (record_id, field_id, child_row_id),
  KEY idx_field_value_number (module_id, field_id, value_number),
  KEY idx_field_value_datetime (module_id, field_id, value_datetime)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_index (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  record_id BIGINT NOT NULL COMMENT 'field',
  field_id BIGINT NOT NULL COMMENT 'field',
  index_value VARCHAR(512) NULL COMMENT 'field',
  index_number DECIMAL(24,6) NULL COMMENT 'field',
  index_datetime DATETIME NULL COMMENT 'field',
  KEY idx_filter_value (system_id, tenant_id, module_id, field_id, index_value),
  KEY idx_filter_number (system_id, tenant_id, module_id, field_id, index_number),
  KEY idx_filter_datetime (system_id, tenant_id, module_id, field_id, index_datetime),
  KEY idx_record_index (record_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_child_row (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  record_id BIGINT NOT NULL COMMENT 'field',
  child_module_id BIGINT NOT NULL COMMENT 'field',
  row_no INT NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  KEY idx_child_record (record_id, child_module_id, row_no)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  source_module_id BIGINT NOT NULL COMMENT 'field',
  source_record_id BIGINT NOT NULL COMMENT 'field',
  target_module_id BIGINT NOT NULL COMMENT 'field',
  target_record_id BIGINT NOT NULL COMMENT 'field',
  relation_type VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_relation (source_record_id, target_record_id, relation_type),
  KEY idx_source (system_id, tenant_id, source_module_id, source_record_id),
  KEY idx_target (system_id, tenant_id, target_module_id, target_record_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  record_id BIGINT NOT NULL COMMENT 'field',
  action_code VARCHAR(80) NOT NULL COMMENT 'field',
  field_diff JSON NULL COMMENT 'field',
  source_type VARCHAR(40) NOT NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  desensitize_result JSON NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NULL COMMENT 'field',
  operated_by BIGINT NOT NULL COMMENT 'field',
  operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_history_record (record_id, operated_at),
  KEY idx_history_trace (trace_id),
  KEY idx_history_scope (system_id, tenant_id, module_id, operated_at)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  draft_no VARCHAR(80) NOT NULL COMMENT 'field',
  draft_payload JSON NOT NULL COMMENT 'field',
  created_by BIGINT NOT NULL COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_draft_no (draft_no),
  KEY idx_draft_owner (system_id, tenant_id, module_id, created_by)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  record_id BIGINT NOT NULL COMMENT 'field',
  field_id BIGINT NULL COMMENT 'field',
  file_id VARCHAR(80) NOT NULL COMMENT 'field',
  file_name VARCHAR(255) NOT NULL COMMENT 'field',
  upload_status VARCHAR(40) NOT NULL COMMENT 'field',
  draft_id BIGINT NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_by BIGINT NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_attachment_record (system_id, tenant_id, module_id, record_id),
  KEY idx_attachment_file (file_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dynamic_sequence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  sequence_type VARCHAR(80) NOT NULL COMMENT 'field',
  prefix_rule VARCHAR(120) NULL COMMENT 'field',
  current_no BIGINT NOT NULL DEFAULT 0 COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_sequence_scope (system_id, tenant_id, module_id, sequence_type)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_upload_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  file_id VARCHAR(80) NOT NULL COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  file_name VARCHAR(255) NOT NULL COMMENT 'field',
  extension VARCHAR(40) NULL COMMENT 'field',
  mime_type VARCHAR(120) NULL COMMENT 'field',
  size_bytes BIGINT NOT NULL COMMENT 'field',
  sha256 VARCHAR(128) NULL COMMENT 'field',
  storage_provider VARCHAR(80) NOT NULL COMMENT 'field',
  storage_bucket VARCHAR(160) NULL COMMENT 'field',
  object_key VARCHAR(500) NOT NULL COMMENT 'field',
  storage_status VARCHAR(40) NOT NULL COMMENT 'field',
  preview_status VARCHAR(40) NULL COMMENT 'field',
  owner_account_id BIGINT NULL COMMENT 'field',
  owner_member_id BIGINT NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_upload_file_id (file_id),
  KEY idx_upload_scope (scope, system_id, tenant_id, storage_status),
  KEY idx_upload_owner (system_id, tenant_id, owner_member_id, created_at),
  KEY idx_upload_hash (sha256)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_upload_file_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  file_id VARCHAR(80) NOT NULL COMMENT 'field',
  version_no INT NOT NULL COMMENT 'field',
  object_key VARCHAR(500) NOT NULL COMMENT 'field',
  size_bytes BIGINT NOT NULL COMMENT 'field',
  sha256 VARCHAR(128) NULL COMMENT 'field',
  created_by BIGINT NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_upload_file_version (file_id, version_no),
  KEY idx_upload_version_file (file_id, created_at)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_upload_file_access_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  file_id VARCHAR(80) NOT NULL COMMENT 'field',
  access_action VARCHAR(40) NOT NULL COMMENT 'field',
  actor_account_id BIGINT NULL COMMENT 'field',
  actor_member_id BIGINT NULL COMMENT 'field',
  result VARCHAR(40) NOT NULL COMMENT 'field',
  failure_reason VARCHAR(255) NULL COMMENT 'field',
  request_id VARCHAR(80) NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_upload_access_file (file_id, created_at),
  KEY idx_upload_access_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_upload_file_recycle (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  file_id VARCHAR(80) NOT NULL COMMENT 'field',
  recycle_reason VARCHAR(255) NULL COMMENT 'field',
  recycled_by BIGINT NOT NULL COMMENT 'field',
  recycled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  restore_deadline DATETIME NULL COMMENT 'field',
  restore_status VARCHAR(40) NOT NULL COMMENT 'field',
  restored_by BIGINT NULL COMMENT 'field',
  restored_at DATETIME NULL COMMENT 'field',
  UNIQUE KEY uk_upload_recycle_file (file_id),
  KEY idx_upload_recycle_status (restore_status, restore_deadline)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_upload_storage_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  policy_code VARCHAR(80) NOT NULL COMMENT 'field',
  storage_provider VARCHAR(80) NOT NULL COMMENT 'field',
  storage_bucket VARCHAR(160) NULL COMMENT 'field',
  secret_ref_id VARCHAR(80) NULL COMMENT 'field',
  max_file_size BIGINT NULL COMMENT 'field',
  allowed_extensions JSON NULL COMMENT 'field',
  preview_enabled TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  quota_limit_bytes BIGINT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_upload_policy_scope (scope, system_id, tenant_id, policy_code),
  KEY idx_upload_policy_status (scope, system_id, tenant_id, status)
) COMMENT='table';
