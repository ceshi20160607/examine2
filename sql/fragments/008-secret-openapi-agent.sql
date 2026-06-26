-- TASK-DBA-008: SecretRef, SSO provider, OpenAPI app and AI Agent schema.

CREATE TABLE IF NOT EXISTS un_sys_secret_ref (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  secret_ref_id VARCHAR(80) NOT NULL COMMENT 'field',
  ref_type VARCHAR(40) NOT NULL COMMENT 'field',
  display_name VARCHAR(120) NOT NULL COMMENT 'field',
  version_no VARCHAR(80) NOT NULL COMMENT 'field',
  expires_at DATETIME NULL COMMENT 'field',
  rotation_status VARCHAR(40) NULL COMMENT 'field',
  last_used_at DATETIME NULL COMMENT 'field',
  storage_ref VARCHAR(255) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_secret_ref_version (secret_ref_id, version_no),
  KEY idx_secret_type (ref_type, rotation_status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_sys_secret_rotation_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  job_id VARCHAR(80) NOT NULL COMMENT 'field',
  secret_ref_id VARCHAR(80) NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  new_version VARCHAR(80) NOT NULL COMMENT 'field',
  rollback_plan JSON NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_rotation_job (job_id),
  KEY idx_secret_job (secret_ref_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_identity_provider (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  provider_code VARCHAR(80) NOT NULL COMMENT 'field',
  provider_name VARCHAR(120) NOT NULL COMMENT 'field',
  protocol VARCHAR(40) NOT NULL COMMENT 'field',
  issuer VARCHAR(255) NULL COMMENT 'field',
  client_id VARCHAR(160) NULL COMMENT 'field',
  secret_ref_id VARCHAR(80) NULL COMMENT 'field',
  cert_ref_id VARCHAR(80) NULL COMMENT 'field',
  domain_whitelist JSON NULL COMMENT 'field',
  jit_policy JSON NULL COMMENT 'field',
  mfa_policy JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_provider_code (provider_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_system_sso_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  enabled_provider_ids JSON NOT NULL COMMENT 'field',
  tenant_domains JSON NULL COMMENT 'field',
  org_mapping JSON NULL COMMENT 'field',
  employee_binding JSON NULL COMMENT 'field',
  jit_member_policy JSON NULL COMMENT 'field',
  no_member_feedback JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_system_sso_policy (system_id, tenant_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_openapi_app (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  external_app_code VARCHAR(80) NOT NULL COMMENT 'field',
  app_name VARCHAR(120) NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  openapi_secret_ref_id VARCHAR(80) NOT NULL COMMENT 'field',
  scopes JSON NOT NULL COMMENT 'field',
  callback_url VARCHAR(255) NULL COMMENT 'field',
  rate_limit JSON NULL COMMENT 'field',
  last_used_at DATETIME NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_openapi_app_code (system_id, tenant_id, external_app_code),
  KEY idx_openapi_status (system_id, tenant_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_openapi_call_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  external_app_id BIGINT NOT NULL COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  scope_code VARCHAR(120) NOT NULL COMMENT 'field',
  request_method VARCHAR(20) NOT NULL COMMENT 'field',
  request_path VARCHAR(255) NOT NULL COMMENT 'field',
  result VARCHAR(40) NOT NULL COMMENT 'field',
  idempotency_key VARCHAR(160) NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  failure_reason VARCHAR(500) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_openapi_app_time (external_app_id, created_at),
  KEY idx_openapi_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_agent_model_authorization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  authorization_code VARCHAR(80) NOT NULL COMMENT 'field',
  model_provider VARCHAR(80) NOT NULL COMMENT 'field',
  model_name VARCHAR(120) NOT NULL COMMENT 'field',
  model_credential_ref_id VARCHAR(80) NOT NULL COMMENT 'field',
  quota_config JSON NULL COMMENT 'field',
  data_outbound_policy JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  version_no VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_agent_auth_code (authorization_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_agent_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  policy_code VARCHAR(80) NOT NULL COMMENT 'field',
  module_scope JSON NOT NULL COMMENT 'field',
  field_scope JSON NOT NULL COMMENT 'field',
  action_scope JSON NOT NULL COMMENT 'field',
  data_scope_expression JSON NULL COMMENT 'field',
  outbound_limit JSON NULL COMMENT 'field',
  desensitize_policy JSON NULL COMMENT 'field',
  policy_version VARCHAR(80) NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_agent_policy_version (system_id, tenant_id, policy_code, policy_version)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_agent_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  session_id VARCHAR(80) NOT NULL COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  system_member_id BIGINT NULL COMMENT 'field',
  authorization_code VARCHAR(80) NOT NULL COMMENT 'field',
  model_version VARCHAR(80) NOT NULL COMMENT 'field',
  prompt_version VARCHAR(80) NOT NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  created_by BIGINT NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_agent_session (session_id),
  KEY idx_agent_session_scope (scope, system_id, tenant_id, system_member_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_agent_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  session_id VARCHAR(80) NOT NULL COMMENT 'field',
  confirmation_id VARCHAR(80) NULL COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  model_version VARCHAR(80) NOT NULL COMMENT 'field',
  prompt_version VARCHAR(80) NOT NULL COMMENT 'field',
  policy_version VARCHAR(80) NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  conversation_snapshot JSON NOT NULL COMMENT 'field',
  tool_call_snapshot JSON NULL COMMENT 'field',
  desensitize_result JSON NULL COMMENT 'field',
  outbound_snapshot JSON NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_agent_audit_session (session_id, created_at),
  KEY idx_agent_audit_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_agent_confirmation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  confirmation_id VARCHAR(80) NOT NULL COMMENT 'field',
  confirm_type VARCHAR(40) NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  source_conversation JSON NOT NULL COMMENT 'field',
  diff_payload JSON NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  confirmed_by BIGINT NULL COMMENT 'field',
  confirmed_at DATETIME NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_agent_confirmation (confirmation_id),
  KEY idx_agent_confirm_status (confirm_type, status)
) COMMENT='table';
