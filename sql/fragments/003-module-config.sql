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
