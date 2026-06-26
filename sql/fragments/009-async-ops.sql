-- TASK-DBA-009: async task, feature flag, quota, backup, archive, deployment and cache policy schema.

CREATE TABLE IF NOT EXISTS un_sys_async_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  task_id VARCHAR(80) NOT NULL COMMENT 'field',
  biz_type VARCHAR(80) NOT NULL COMMENT 'field',
  idempotency_key VARCHAR(160) NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  progress INT NOT NULL DEFAULT 0 COMMENT 'field',
  retryable TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  cancelable TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  rollback_supported TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  result_file_id VARCHAR(80) NULL COMMENT 'field',
  error_file_id VARCHAR(80) NULL COMMENT 'field',
  failure_reason VARCHAR(500) NULL COMMENT 'field',
  partial_success_count INT NULL COMMENT 'field',
  partial_failure_count INT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_by BIGINT NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_async_task_id (task_id),
  UNIQUE KEY uk_async_idempotency (biz_type, idempotency_key),
  KEY idx_task_status (biz_type, status, created_at),
  KEY idx_task_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_sys_async_task_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  task_id VARCHAR(80) NOT NULL COMMENT 'field',
  event_type VARCHAR(40) NOT NULL COMMENT 'field',
  event_payload JSON NULL COMMENT 'field',
  operator_id BIGINT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_async_event_task (task_id, created_at),
  KEY idx_async_event_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_sys_async_task_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  task_id VARCHAR(80) NOT NULL COMMENT 'field',
  file_type VARCHAR(40) NOT NULL COMMENT 'field',
  file_id VARCHAR(80) NOT NULL COMMENT 'field',
  file_name VARCHAR(255) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_async_file_task (task_id, file_type),
  KEY idx_async_file_id (file_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_sys_idempotency_key (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  idempotency_key VARCHAR(160) NOT NULL COMMENT 'field',
  request_hash VARCHAR(128) NOT NULL COMMENT 'field',
  response_snapshot JSON NULL COMMENT 'field',
  expires_at DATETIME NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_idempotency_scope (scope, system_id, tenant_id, idempotency_key),
  KEY idx_idempotency_expires (expires_at)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_health_check (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  check_type VARCHAR(80) NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  result_payload JSON NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  checked_by BIGINT NULL COMMENT 'field',
  checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_health_scope (scope, system_id, check_type, checked_at),
  KEY idx_health_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_feature_flag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  flag_code VARCHAR(80) NOT NULL COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  rules JSON NULL COMMENT 'field',
  rollback_version VARCHAR(80) NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_feature_flag_scope (flag_code, scope, system_id, tenant_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_quota (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  quota_type VARCHAR(80) NOT NULL COMMENT 'field',
  quota_limit BIGINT NOT NULL COMMENT 'field',
  quota_used BIGINT NOT NULL DEFAULT 0 COMMENT 'field',
  warn_threshold BIGINT NULL COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_quota_scope (scope, system_id, tenant_id, quota_type)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_rate_limit_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  policy_code VARCHAR(80) NOT NULL COMMENT 'field',
  limit_rule JSON NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_rate_limit_policy (scope, system_id, tenant_id, policy_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_backup_restore (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  backup_no VARCHAR(80) NOT NULL COMMENT 'field',
  backup_type VARCHAR(40) NOT NULL COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  boundary_payload JSON NOT NULL COMMENT 'field',
  result_payload JSON NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_backup_no (backup_no),
  KEY idx_backup_scope (scope, system_id, backup_type, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_archive_restore_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  request_no VARCHAR(80) NOT NULL COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  object_type VARCHAR(80) NOT NULL COMMENT 'field',
  archive_condition JSON NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  approver_id BIGINT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_archive_request_no (request_no),
  KEY idx_archive_status (scope, system_id, object_type, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_deployment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  deployment_no VARCHAR(80) NOT NULL COMMENT 'field',
  env_code VARCHAR(40) NOT NULL COMMENT 'field',
  backend_version VARCHAR(80) NULL COMMENT 'field',
  frontend_version VARCHAR(80) NULL COMMENT 'field',
  config_version VARCHAR(80) NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  rollback_plan JSON NULL COMMENT 'field',
  destructive_script_confirmed TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_deployment_no (deployment_no),
  KEY idx_deployment_env (env_code, status, created_at)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_ops_api_cache_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  policy_code VARCHAR(80) NOT NULL COMMENT 'field',
  cache_domain VARCHAR(80) NOT NULL COMMENT 'field',
  key_rule JSON NOT NULL COMMENT 'field',
  invalidation_rule JSON NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_cache_policy_code (policy_code),
  KEY idx_cache_domain (cache_domain, status)
) COMMENT='table';
