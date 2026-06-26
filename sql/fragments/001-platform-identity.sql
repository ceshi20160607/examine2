-- TASK-DBA-001: platform identity, system, tenant, member and login audit schema.

CREATE TABLE IF NOT EXISTS un_plat_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  account_name VARCHAR(80) NOT NULL COMMENT 'field',
  mobile VARCHAR(32) NULL COMMENT 'field',
  email VARCHAR(120) NULL COMMENT 'field',
  password_hash VARCHAR(255) NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  last_login_at DATETIME NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_account_name_deleted (account_name, deleted),
  UNIQUE KEY uk_mobile_deleted (mobile, deleted),
  UNIQUE KEY uk_email_deleted (email, deleted)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_system (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_code VARCHAR(64) NOT NULL COMMENT 'field',
  system_name VARCHAR(120) NOT NULL COMMENT 'field',
  tenant_mode TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  owner_account_id BIGINT NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  disabled_reason VARCHAR(255) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_system_code_deleted (system_code, deleted),
  KEY idx_owner_status (owner_account_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_tenant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_code VARCHAR(64) NOT NULL COMMENT 'field',
  tenant_name VARCHAR(120) NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  domain VARCHAR(255) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_system_tenant_code_deleted (system_id, tenant_code, deleted),
  KEY idx_system_status (system_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT 'field',
  dept_code VARCHAR(64) NOT NULL COMMENT 'field',
  dept_name VARCHAR(120) NOT NULL COMMENT 'field',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_dept_code_deleted (system_id, tenant_id, dept_code, deleted),
  KEY idx_parent (system_id, tenant_id, parent_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  dept_id BIGINT NULL COMMENT 'field',
  member_name VARCHAR(80) NOT NULL COMMENT 'field',
  employee_no VARCHAR(80) NULL COMMENT 'field',
  mobile VARCHAR(32) NULL COMMENT 'field',
  email VARCHAR(120) NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  KEY idx_member_scope (system_id, tenant_id, status),
  KEY idx_member_dept (system_id, tenant_id, dept_id),
  KEY idx_employee_no (system_id, tenant_id, employee_no)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_account_member_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  account_id BIGINT NOT NULL COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  system_member_id BIGINT NOT NULL COMMENT 'field',
  binding_status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_account_system_tenant_member (account_id, system_id, tenant_id, system_member_id),
  KEY idx_member_binding (system_id, tenant_id, system_member_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_sso_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  account_id BIGINT NOT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  identity_provider VARCHAR(80) NOT NULL COMMENT 'field',
  external_user_id VARCHAR(160) NOT NULL COMMENT 'field',
  external_dept_id VARCHAR(160) NULL COMMENT 'field',
  binding_status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_provider_external (identity_provider, external_user_id),
  KEY idx_account_provider (account_id, identity_provider),
  KEY idx_system_tenant_external (system_id, tenant_id, external_user_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_plat_no_member_access_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  request_no VARCHAR(80) NOT NULL COMMENT 'field',
  status VARCHAR(32) NOT NULL COMMENT 'field',
  identity_provider VARCHAR(80) NOT NULL COMMENT 'field',
  external_user_id VARCHAR(160) NOT NULL COMMENT 'field',
  target_system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  request_role VARCHAR(160) NULL COMMENT 'field',
  approver_id BIGINT NULL COMMENT 'field',
  approve_result VARCHAR(255) NULL COMMENT 'field',
  role_ids JSON NULL COMMENT 'field',
  data_scope JSON NULL COMMENT 'field',
  reject_reason VARCHAR(255) NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_request_no (request_no),
  KEY idx_target_status (target_system_id, tenant_id, status),
  KEY idx_external (identity_provider, external_user_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_audit_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  account_id BIGINT NULL COMMENT 'field',
  system_id BIGINT NULL COMMENT 'field',
  tenant_id BIGINT NULL COMMENT 'field',
  identity_provider VARCHAR(80) NULL COMMENT 'field',
  external_user_id VARCHAR(160) NULL COMMENT 'field',
  auth_method VARCHAR(40) NOT NULL COMMENT 'field',
  mfa_result VARCHAR(40) NULL COMMENT 'field',
  account_binding_result VARCHAR(80) NULL COMMENT 'field',
  member_mapping_result VARCHAR(80) NULL COMMENT 'field',
  login_result VARCHAR(40) NOT NULL COMMENT 'field',
  failure_reason VARCHAR(255) NULL COMMENT 'field',
  ip VARCHAR(64) NULL COMMENT 'field',
  device VARCHAR(255) NULL COMMENT 'field',
  request_id VARCHAR(80) NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_account_time (account_id, created_at),
  KEY idx_scope_time (system_id, tenant_id, created_at),
  KEY idx_trace (trace_id)
) COMMENT='table';

