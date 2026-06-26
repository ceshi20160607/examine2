-- unexamine initial schema baseline.
-- Generated from sql/fragments/*.sql in dependency order for TASK-DBA-010.
-- Regenerate by concatenating fragments 001 through 009 exactly once.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- source: sql/fragments/001-platform-identity.sql
-- ============================================================================

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



-- ============================================================================
-- source: sql/fragments/002-permission-rbac.sql
-- ============================================================================

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


-- ============================================================================
-- source: sql/fragments/003-module-config.sql
-- ============================================================================

-- TASK-DBA-003: module group, module, field, dictionary, scene and publish config schema.

CREATE TABLE IF NOT EXISTS un_module_group (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  group_code VARCHAR(80) NOT NULL COMMENT 'field',
  group_name VARCHAR(120) NOT NULL COMMENT 'field',
  icon VARCHAR(80) NULL COMMENT 'field',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'field',
  visible_role_ids JSON NULL COMMENT 'field',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'field',
  published_version VARCHAR(80) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_group_code_deleted (system_id, tenant_id, group_code, deleted),
  KEY idx_group_sort (system_id, tenant_id, sort_order)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  group_id BIGINT NOT NULL COMMENT 'field',
  module_code VARCHAR(80) NOT NULL COMMENT 'field',
  module_name VARCHAR(120) NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'field',
  current_version VARCHAR(80) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_module_code_deleted (system_id, tenant_id, module_code, deleted),
  KEY idx_group_status (system_id, tenant_id, group_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_field_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  field_code VARCHAR(80) NOT NULL COMMENT 'field',
  field_name VARCHAR(120) NOT NULL COMMENT 'field',
  field_type VARCHAR(40) NOT NULL COMMENT 'field',
  storage_type VARCHAR(40) NOT NULL COMMENT 'field',
  required TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  sortable TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  filter_operators JSON NULL COMMENT 'field',
  default_value JSON NULL COMMENT 'field',
  validation_rule JSON NULL COMMENT 'field',
  mask_rule JSON NULL COMMENT 'field',
  import_export_rule JSON NULL COMMENT 'field',
  dict_type_id BIGINT NULL COMMENT 'field',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_field_code_deleted (module_id, field_code, deleted),
  KEY idx_field_type (system_id, tenant_id, field_type, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dict_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  dict_code VARCHAR(80) NOT NULL COMMENT 'field',
  dict_name VARCHAR(120) NOT NULL COMMENT 'field',
  dict_kind VARCHAR(40) NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  published_version VARCHAR(80) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_dict_code_deleted (system_id, tenant_id, dict_code, deleted)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_dict_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  dict_type_id BIGINT NOT NULL COMMENT 'field',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT 'field',
  item_code VARCHAR(80) NOT NULL COMMENT 'field',
  item_name VARCHAR(120) NOT NULL COMMENT 'field',
  color VARCHAR(32) NULL COMMENT 'field',
  icon VARCHAR(80) NULL COMMENT 'field',
  semantic VARCHAR(80) NULL COMMENT 'field',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'field',
  default_flag TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  kanban_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  disabled_at DATETIME NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_dict_item_code (dict_type_id, item_code),
  KEY idx_dict_parent (dict_type_id, parent_id, sort_order)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_list_scene (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  scene_code VARCHAR(80) NOT NULL COMMENT 'field',
  scene_name VARCHAR(120) NOT NULL COMMENT 'field',
  columns_config JSON NOT NULL COMMENT 'field',
  filters_config JSON NULL COMMENT 'field',
  sort_config JSON NULL COMMENT 'field',
  row_click_target VARCHAR(80) NOT NULL COMMENT 'field',
  default_flag TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_scene_code (module_id, scene_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_action_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  action_code VARCHAR(80) NOT NULL COMMENT 'field',
  action_name VARCHAR(120) NOT NULL COMMENT 'field',
  action_type VARCHAR(40) NOT NULL COMMENT 'field',
  selection_rule JSON NULL COMMENT 'field',
  permission_code VARCHAR(120) NULL COMMENT 'field',
  result_contract JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_module_action (module_id, action_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_import_export_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  import_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  export_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  export_all_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  template_file_id VARCHAR(80) NULL COMMENT 'field',
  result_task_required TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  precheck_rule JSON NULL COMMENT 'field',
  permission_code VARCHAR(120) NULL COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_module_import_export (module_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_print_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  template_code VARCHAR(80) NOT NULL COMMENT 'field',
  template_name VARCHAR(120) NOT NULL COMMENT 'field',
  template_file_id VARCHAR(80) NOT NULL COMMENT 'field',
  field_mapping JSON NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_print_template_code (module_id, template_code)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_work_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  config_type VARCHAR(40) NOT NULL COMMENT 'field',
  field_list JSON NOT NULL COMMENT 'field',
  card_fields JSON NULL COMMENT 'field',
  kanban_column_field_id BIGINT NULL COMMENT 'field',
  kanban_swimlane_field_id BIGINT NULL COMMENT 'field',
  kanban_group_field_id BIGINT NULL COMMENT 'field',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'field',
  published_version VARCHAR(80) NULL COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_work_config_type (system_id, tenant_id, config_type)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_module_publish_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  object_type VARCHAR(40) NOT NULL COMMENT 'field',
  object_id BIGINT NOT NULL COMMENT 'field',
  version_no VARCHAR(80) NOT NULL COMMENT 'field',
  publish_status VARCHAR(32) NOT NULL COMMENT 'field',
  impact_refs JSON NULL COMMENT 'field',
  failure_items JSON NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_publish_version (object_type, object_id, version_no),
  KEY idx_publish_scope (system_id, tenant_id, object_type, publish_status)
) COMMENT='table';


-- ============================================================================
-- source: sql/fragments/004-dynamic-runtime.sql
-- ============================================================================

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


-- ============================================================================
-- source: sql/fragments/005-workflow-approval.sql
-- ============================================================================

-- TASK-DBA-005: workflow definition, snapshot, instance and approval task schema.

CREATE TABLE IF NOT EXISTS un_flow_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  flow_code VARCHAR(80) NOT NULL COMMENT 'field',
  flow_name VARCHAR(120) NOT NULL COMMENT 'field',
  bound_module_id BIGINT NULL COMMENT 'field',
  trigger_rule JSON NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  current_version VARCHAR(80) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_flow_code_deleted (system_id, tenant_id, flow_code, deleted),
  KEY idx_flow_module (system_id, tenant_id, bound_module_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_flow_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  flow_id BIGINT NOT NULL COMMENT 'field',
  node_key VARCHAR(80) NOT NULL COMMENT 'field',
  node_type VARCHAR(40) NOT NULL COMMENT 'field',
  node_name VARCHAR(120) NOT NULL COMMENT 'field',
  position_payload JSON NULL COMMENT 'field',
  property_payload JSON NOT NULL COMMENT 'field',
  status TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_flow_node_key (flow_id, node_key),
  KEY idx_flow_node_type (flow_id, node_type)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_flow_edge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  flow_id BIGINT NOT NULL COMMENT 'field',
  edge_key VARCHAR(80) NOT NULL COMMENT 'field',
  source_node_key VARCHAR(80) NOT NULL COMMENT 'field',
  target_node_key VARCHAR(80) NOT NULL COMMENT 'field',
  branch_label VARCHAR(120) NULL COMMENT 'field',
  condition_payload JSON NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_flow_edge_key (flow_id, edge_key),
  KEY idx_flow_edge_source (flow_id, source_node_key),
  KEY idx_flow_edge_target (flow_id, target_node_key)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_flow_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  flow_id BIGINT NOT NULL COMMENT 'field',
  version_no VARCHAR(80) NOT NULL COMMENT 'field',
  node_payload JSON NOT NULL COMMENT 'field',
  edge_payload JSON NOT NULL COMMENT 'field',
  publish_check_result JSON NULL COMMENT 'field',
  published_by BIGINT NOT NULL COMMENT 'field',
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  UNIQUE KEY uk_flow_version (flow_id, version_no)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_flow_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  flow_id BIGINT NOT NULL COMMENT 'field',
  flow_version VARCHAR(80) NOT NULL COMMENT 'field',
  module_id BIGINT NOT NULL COMMENT 'field',
  record_id BIGINT NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  current_node_ids JSON NULL COMMENT 'field',
  started_by BIGINT NOT NULL COMMENT 'field',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  ended_at DATETIME NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  KEY idx_instance_record (system_id, tenant_id, module_id, record_id),
  KEY idx_instance_status (system_id, tenant_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_flow_approval_action_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  instance_id BIGINT NOT NULL COMMENT 'field',
  task_id BIGINT NULL COMMENT 'field',
  action_code VARCHAR(40) NOT NULL COMMENT 'field',
  action_result VARCHAR(40) NOT NULL COMMENT 'field',
  action_reason VARCHAR(500) NULL COMMENT 'field',
  operator_member_id BIGINT NOT NULL COMMENT 'field',
  next_node_payload JSON NULL COMMENT 'field',
  idempotency_key VARCHAR(160) NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NOT NULL COMMENT 'field',
  operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_action_instance (instance_id, operated_at),
  KEY idx_action_task (task_id),
  KEY idx_action_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_flow_simulation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  flow_id BIGINT NOT NULL COMMENT 'field',
  version_no VARCHAR(80) NULL COMMENT 'field',
  input_payload JSON NOT NULL COMMENT 'field',
  output_payload JSON NOT NULL COMMENT 'field',
  failure_items JSON NULL COMMENT 'field',
  simulated_by BIGINT NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_simulation_flow (flow_id, created_at),
  KEY idx_simulation_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_flow_approval_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  instance_id BIGINT NOT NULL COMMENT 'field',
  task_no VARCHAR(80) NOT NULL COMMENT 'field',
  node_id VARCHAR(80) NOT NULL COMMENT 'field',
  node_name VARCHAR(120) NOT NULL COMMENT 'field',
  assignee_member_id BIGINT NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  action_result VARCHAR(40) NULL COMMENT 'field',
  action_reason VARCHAR(500) NULL COMMENT 'field',
  field_permission_snapshot JSON NULL COMMENT 'field',
  due_at DATETIME NULL COMMENT 'field',
  operated_at DATETIME NULL COMMENT 'field',
  idempotency_key VARCHAR(120) NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_assignee_status (assignee_member_id, status, due_at),
  KEY idx_instance_node (instance_id, node_id)
) COMMENT='table';


-- ============================================================================
-- source: sql/fragments/006-message-todo-log.sql
-- ============================================================================

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


-- ============================================================================
-- source: sql/fragments/007-work-management.sql
-- ============================================================================

-- TASK-DBA-007: work dashboard, project/plain task, kanban and daily report schema.

CREATE TABLE IF NOT EXISTS un_work_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  project_code VARCHAR(80) NOT NULL COMMENT 'field',
  project_name VARCHAR(160) NOT NULL COMMENT 'field',
  owner_member_id BIGINT NOT NULL COMMENT 'field',
  status VARCHAR(80) NOT NULL COMMENT 'field',
  progress INT NOT NULL DEFAULT 0 COMMENT 'field',
  start_date DATE NULL COMMENT 'field',
  end_date DATE NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  UNIQUE KEY uk_project_code_deleted (system_id, tenant_id, project_code, deleted),
  KEY idx_project_status (system_id, tenant_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  task_type VARCHAR(20) NOT NULL COMMENT 'field',
  project_id BIGINT NULL COMMENT 'field',
  task_title VARCHAR(255) NOT NULL COMMENT 'field',
  assignee_member_id BIGINT NOT NULL COMMENT 'field',
  collaborators JSON NULL COMMENT 'field',
  status VARCHAR(80) NOT NULL COMMENT 'field',
  tags JSON NULL COMMENT 'field',
  progress INT NOT NULL DEFAULT 0 COMMENT 'field',
  due_at DATETIME NULL COMMENT 'field',
  completed_at DATETIME NULL COMMENT 'field',
  related_object JSON NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'field',
  KEY idx_task_assignee (system_id, tenant_id, task_type, assignee_member_id, status, due_at),
  KEY idx_task_project (system_id, tenant_id, project_id, status)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_task_collaborator (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  task_id BIGINT NOT NULL COMMENT 'field',
  member_id BIGINT NOT NULL COMMENT 'field',
  collaborator_type VARCHAR(40) NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_task_collaborator (task_id, member_id, collaborator_type),
  KEY idx_collaborator_member (member_id, collaborator_type)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_task_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  task_id BIGINT NOT NULL COMMENT 'field',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT 'field',
  commenter_id BIGINT NOT NULL COMMENT 'field',
  content TEXT NOT NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_task_comment (task_id, parent_id, created_at)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_task_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  task_id BIGINT NOT NULL COMMENT 'field',
  event_type VARCHAR(40) NOT NULL COMMENT 'field',
  before_payload JSON NULL COMMENT 'field',
  after_payload JSON NULL COMMENT 'field',
  operator_member_id BIGINT NOT NULL COMMENT 'field',
  trace_id VARCHAR(80) NOT NULL COMMENT 'field',
  audit_log_id VARCHAR(80) NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_task_event (task_id, created_at),
  KEY idx_task_event_trace (trace_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_task_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  task_id BIGINT NOT NULL COMMENT 'field',
  related_type VARCHAR(40) NOT NULL COMMENT 'field',
  related_id VARCHAR(120) NOT NULL COMMENT 'field',
  relation_payload JSON NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_task_relation (task_id, related_type, related_id),
  KEY idx_related_object (related_type, related_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_kanban_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  task_type VARCHAR(20) NOT NULL COMMENT 'field',
  column_field_id BIGINT NOT NULL COMMENT 'field',
  swimlane_field_id BIGINT NULL COMMENT 'field',
  group_field_id BIGINT NULL COMMENT 'field',
  card_fields JSON NOT NULL COMMENT 'field',
  published_version VARCHAR(80) NULL COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_kanban_task_type (system_id, tenant_id, task_type)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_daily_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  report_date DATE NOT NULL COMMENT 'field',
  submitter_id BIGINT NOT NULL COMMENT 'field',
  status VARCHAR(40) NOT NULL COMMENT 'field',
  content TEXT NOT NULL COMMENT 'field',
  source_summary JSON NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  submitted_at DATETIME NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_report_date_submitter (system_id, tenant_id, report_date, submitter_id),
  KEY idx_report_status (system_id, tenant_id, status, report_date)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_daily_report_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  report_id BIGINT NULL COMMENT 'field',
  draft_no VARCHAR(80) NULL COMMENT 'field',
  source_type VARCHAR(40) NOT NULL COMMENT 'field',
  source_id VARCHAR(120) NOT NULL COMMENT 'field',
  source_snapshot JSON NOT NULL COMMENT 'field',
  permission_snapshot_id VARCHAR(80) NULL COMMENT 'field',
  selected TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_report_source (report_id, source_type),
  KEY idx_source_object (source_type, source_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_calendar_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  member_id BIGINT NOT NULL COMMENT 'field',
  item_date DATE NOT NULL COMMENT 'field',
  item_type VARCHAR(40) NOT NULL COMMENT 'field',
  item_id VARCHAR(120) NOT NULL COMMENT 'field',
  title VARCHAR(255) NOT NULL COMMENT 'field',
  target_payload JSON NULL COMMENT 'field',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'field',
  KEY idx_calendar_member_date (system_id, tenant_id, member_id, item_date),
  KEY idx_calendar_item (item_type, item_id)
) COMMENT='table';

CREATE TABLE IF NOT EXISTS un_work_daily_report_auto_source_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'field',
  system_id BIGINT NOT NULL COMMENT 'field',
  tenant_id BIGINT NOT NULL COMMENT 'field',
  source_types JSON NOT NULL COMMENT 'field',
  task_scope JSON NULL COMMENT 'field',
  todo_scope JSON NULL COMMENT 'field',
  message_scope JSON NULL COMMENT 'field',
  log_scope JSON NULL COMMENT 'field',
  approval_scope JSON NULL COMMENT 'field',
  permission_policy JSON NOT NULL COMMENT 'field',
  manual_confirm_required TINYINT NOT NULL DEFAULT 1 COMMENT 'field',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'field',
  UNIQUE KEY uk_daily_rule (system_id, tenant_id)
) COMMENT='table';


-- ============================================================================
-- source: sql/fragments/008-secret-openapi-agent.sql
-- ============================================================================

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


-- ============================================================================
-- source: sql/fragments/009-async-ops.sql
-- ============================================================================

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


SET FOREIGN_KEY_CHECKS = 1;
