-- TASK-DBA-006: todo, message, notification template, delivery log and audit log schema.

CREATE TABLE IF NOT EXISTS un_message_todo (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  todo_type VARCHAR(40) NOT NULL COMMENT 'field',
  title VARCHAR(255) NOT NULL COMMENT 'field',
  source_name VARCHAR(120) NULL COMMENT 'field',
  assignee_id BIGINT NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  priority VARCHAR(40) NULL COMMENT 'field',
  due_at DATETIME NULL COMMENT 'field',
  target_payload JSON NOT NULL COMMENT 'field',
  primary_action JSON NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_todo_assignee (scope, system_id, tenant_id, assignee_id, status, due_at),
  KEY idx_todo_type (scope, system_id, tenant_id, todo_type, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_message_notification_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  template_code VARCHAR(80) NOT NULL COMMENT 'field',
  template_type VARCHAR(40) NOT NULL COMMENT 'field',
  variables JSON NULL COMMENT 'field',
  channels JSON NOT NULL COMMENT 'field',
  target_rule JSON NOT NULL COMMENT 'field',
  dedupe_key VARCHAR(160) NULL COMMENT 'field',
  read_receipt_required TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  quiet_policy JSON NULL COMMENT 'field',
  retry_policy JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_template_scope_code (scope, system_id, template_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_message_target (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  target_type VARCHAR(40) NOT NULL COMMENT 'field',
  target_id VARCHAR(120) NOT NULL COMMENT 'field',
  target_system_id BIGINT NULL COMMENT 'field',
  target_tenant_id BIGINT NULL COMMENT 'field',
  requires_system_switch TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  fallback_action VARCHAR(120) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_message_target_scope (scope, system_id, tenant_id, target_type),
  KEY idx_message_target_id (target_type, target_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_message_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  template_code VARCHAR(80) NOT NULL COMMENT 'field',
  receiver_id BIGINT NOT NULL COMMENT 'field',
  title VARCHAR(255) NOT NULL COMMENT 'field',
  content TEXT NOT NULL COMMENT 'field',
  message_type VARCHAR(40) NOT NULL COMMENT 'field',
  target_payload JSON NOT NULL COMMENT 'field',
  read_status TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  archive_status TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_message_receiver (scope, system_id, tenant_id, receiver_id, read_status, created_at),
  KEY idx_message_template (scope, system_id, tenant_id, template_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_message_delivery_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  message_id BIGINT NOT NULL COMMENT 'field',
  channel VARCHAR(40) NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  failure_reason VARCHAR(500) NULL COMMENT 'field',
  retry_count INT NOT NULL DEFAULT 0 COMMENT 'field',
  read_receipt VARCHAR(80) NULL COMMENT 'field',
  do_not_disturb TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  archive_status TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_delivery_message (message_id, channel),
  KEY idx_delivery_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_audit_log_export_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  log_type VARCHAR(40) NOT NULL COMMENT 'field',
  filter_payload JSON NOT NULL COMMENT 'field',
  async_task_id VARCHAR(80) NOT NULL COMMENT 'field',
  requested_by BIGINT NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_log_export_scope (scope, system_id, tenant_id, log_type, created_at),
  KEY idx_log_export_task (async_task_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_audit_business_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  log_type VARCHAR(40) NOT NULL COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  operator_id BIGINT NULL COMMENT 'field',
  action_code VARCHAR(80) NOT NULL COMMENT 'field',
  object_type VARCHAR(80) NOT NULL COMMENT 'field',
  object_id VARCHAR(120) NOT NULL COMMENT 'field',
  result VARCHAR(40) NOT NULL COMMENT 'field',
  request_id VARCHAR(80) NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  ip VARCHAR(64) NULL COMMENT 'field',
  device VARCHAR(255) NULL COMMENT 'field',
  field_diff JSON NULL COMMENT 'field',
  permission_snapshot JSON NULL COMMENT 'field',
  desensitize_result JSON NULL COMMENT 'field',
  failure_reason VARCHAR(500) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_audit_scope (scope, system_id, tenant_id, log_type, created_at),
  KEY idx_audit_object (object_type, object_id),
  KEY idx_audit_trace (trace_id)
) COMMENT='table';
