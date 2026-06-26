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
