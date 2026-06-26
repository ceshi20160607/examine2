-- Legacy identity schema compatibility migration.
--
-- Purpose:
--   Bring an older `examine` / `unexamine` database closer to the current
--   `sql/init.sql` identity schema without dropping old columns or data.
--
-- When to use:
--   Run this only when `/api/v1/health` reports `schema=MISMATCH` and the
--   database already contains older tables such as:
--     - un_plat_account.login_name
--     - un_plat_system.code
--     - un_plat_tenant.code
--     - un_plat_role.code
--
-- Notes:
--   1. Execute with a database user that has ALTER and CREATE privileges.
--   2. Back up the target database before running.
--   3. MySQL versions differ on `ADD COLUMN IF NOT EXISTS`; this file uses
--      plain ALTER statements so DBA tools can apply/check them explicitly.

ALTER TABLE un_plat_account ADD COLUMN account_name VARCHAR(80) NULL AFTER id;
ALTER TABLE un_plat_account ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;
ALTER TABLE un_plat_account MODIFY login_name VARCHAR(80) NULL;
ALTER TABLE un_plat_account MODIFY display_name VARCHAR(80) NULL;

ALTER TABLE un_plat_system ADD COLUMN system_code VARCHAR(64) NULL AFTER id;
ALTER TABLE un_plat_system ADD COLUMN system_name VARCHAR(120) NULL AFTER system_code;
ALTER TABLE un_plat_system ADD COLUMN disabled_reason VARCHAR(255) NULL;
ALTER TABLE un_plat_system ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;
ALTER TABLE un_plat_system MODIFY code VARCHAR(64) NULL;
ALTER TABLE un_plat_system MODIFY name VARCHAR(120) NULL;

ALTER TABLE un_plat_tenant ADD COLUMN tenant_code VARCHAR(64) NULL AFTER system_id;
ALTER TABLE un_plat_tenant ADD COLUMN tenant_name VARCHAR(120) NULL AFTER tenant_code;
ALTER TABLE un_plat_tenant ADD COLUMN domain VARCHAR(255) NULL;
ALTER TABLE un_plat_tenant ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;
ALTER TABLE un_plat_tenant MODIFY code VARCHAR(64) NULL;
ALTER TABLE un_plat_tenant MODIFY name VARCHAR(120) NULL;

ALTER TABLE un_plat_role ADD COLUMN scope VARCHAR(20) NULL AFTER id;
ALTER TABLE un_plat_role ADD COLUMN system_id BIGINT NULL AFTER scope;
ALTER TABLE un_plat_role ADD COLUMN tenant_id BIGINT NULL AFTER system_id;
ALTER TABLE un_plat_role ADD COLUMN role_code VARCHAR(80) NULL AFTER tenant_id;
ALTER TABLE un_plat_role ADD COLUMN role_name VARCHAR(120) NULL AFTER role_code;
ALTER TABLE un_plat_role ADD COLUMN role_type VARCHAR(40) NULL AFTER role_name;
ALTER TABLE un_plat_role ADD COLUMN builtin TINYINT NOT NULL DEFAULT 0 AFTER role_type;
ALTER TABLE un_plat_role ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0;
ALTER TABLE un_plat_role MODIFY code VARCHAR(80) NULL;
ALTER TABLE un_plat_role MODIFY name VARCHAR(120) NULL;

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
