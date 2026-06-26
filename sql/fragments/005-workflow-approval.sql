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
