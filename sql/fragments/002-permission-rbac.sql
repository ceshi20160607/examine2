-- TASK-DBA-002: role, permission, data scope, deny policy and effective snapshot schema.

CREATE TABLE IF NOT EXISTS un_plat_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  role_code VARCHAR(80) NOT NULL COMMENT 'field',
  role_name VARCHAR(120) NOT NULL COMMENT 'field',
  role_type VARCHAR(40) NOT NULL COMMENT 'field',
  builtin TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  description VARCHAR(255) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_role_scope_code_deleted (scope, system_id, tenant_id, role_code, deleted),
  KEY idx_role_scope_status (scope, system_id, tenant_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_role_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  role_id BIGINT NOT NULL COMMENT 'field',
  account_id BIGINT NULL COMMENT 'field',
  system_member_id BIGINT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_role_principal (role_id, account_id, system_member_id),
  KEY idx_member_roles (system_id, tenant_id, system_member_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_permission_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  scope VARCHAR(20) NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  permission_version VARCHAR(80) NOT NULL COMMENT 'field',
  published_by BIGINT NOT NULL COMMENT 'field',
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  change_summary VARCHAR(500) NULL COMMENT 'field',
  UNIQUE KEY uk_permission_version (permission_version),
  KEY idx_scope_time (scope, system_id, tenant_id, published_at)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  role_id BIGINT NOT NULL COMMENT 'field',
  permission_version VARCHAR(80) NOT NULL COMMENT 'field',
  target_type VARCHAR(40) NOT NULL COMMENT 'field',
  target_code VARCHAR(160) NOT NULL COMMENT 'field',
  effect VARCHAR(20) NOT NULL DEFAULT 'ALLOW' COMMENT 'field',
  permission_payload JSON NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_role_target (role_id, target_type, target_code),
  KEY idx_permission_version (permission_version)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_role_field_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  role_id BIGINT NOT NULL COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  field_id BIGINT NOT NULL COMMENT 'field',
  permission_mode VARCHAR(40) NOT NULL COMMENT 'field',
  mask_rule JSON NULL COMMENT 'field',
  disabled_reason VARCHAR(255) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_role_field_permission (role_id, module_id, field_id),
  KEY idx_field_permission_lookup (system_id, tenant_id, module_id, field_id, permission_mode)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_data_scope_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  role_id BIGINT NOT NULL COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NULL COMMENT 'field',
  scope_type VARCHAR(40) NOT NULL COMMENT 'field',
  scope_expression JSON NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_role_module (role_id, module_id),
  KEY idx_scope_lookup (system_id, tenant_id, module_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_deny_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  policy_code VARCHAR(80) NOT NULL COMMENT 'field',
  policy_name VARCHAR(120) NOT NULL COMMENT 'field',
  target_type VARCHAR(40) NOT NULL COMMENT 'field',
  target_code VARCHAR(160) NOT NULL COMMENT 'field',
  condition_payload JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_deny_policy_code (system_id, tenant_id, policy_code),
  KEY idx_deny_target (system_id, tenant_id, target_type, target_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_permission_preview_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  system_member_id BIGINT NULL COMMENT 'field',
  role_ids JSON NULL COMMENT 'field',
  module_id BIGINT NULL COMMENT 'field',
  record_id BIGINT NULL COMMENT 'field',
  action_code VARCHAR(80) NULL COMMENT 'field',
  decision_payload JSON NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_permission_preview_member (system_id, tenant_id, system_member_id, created_at),
  KEY idx_permission_preview_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_effective_permission_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  snapshot_id VARCHAR(80) NOT NULL COMMENT 'field',
  permission_version VARCHAR(80) NOT NULL COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  system_member_id BIGINT NOT NULL COMMENT 'field',
  source_role_ids JSON NOT NULL COMMENT 'field',
  deny_policy_ids JSON NULL COMMENT 'field',
  field_permissions JSON NOT NULL COMMENT 'field',
  action_permissions JSON NOT NULL COMMENT 'field',
  data_scope JSON NOT NULL COMMENT 'field',
  disabled_reason VARCHAR(255) NULL COMMENT 'field',
  explain_payload JSON NULL COMMENT 'field',
  expires_at DATETIME NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_snapshot_id (snapshot_id),
  KEY idx_member_version (system_id, tenant_id, system_member_id, permission_version),
  KEY idx_expires_at (expires_at)
) COMMENT='table';
