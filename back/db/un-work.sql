-- 普通任务、项目任务和手动工作日志共用作用域，系统任务仍按系统及租户隔离。
CREATE TABLE IF NOT EXISTS un_work_project (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工作项目主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统工作作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统项目所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统项目所属租户主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内项目编号',
  name VARCHAR(160) NOT NULL COMMENT '项目名称',
  description TEXT DEFAULT NULL COMMENT '项目业务说明',
  owner_account_id BIGINT NOT NULL COMMENT '项目负责人平台账号主键',
  department_id BIGINT DEFAULT NULL COMMENT '项目归属部门主键',
  related_module_id BIGINT DEFAULT NULL COMMENT '关联业务模块主键',
  related_record_id BIGINT DEFAULT NULL COMMENT '关联业务记录主键',
  stage_code VARCHAR(80) DEFAULT NULL COMMENT '项目当前阶段字典值标识',
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT '项目进行完成归档等状态',
  progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '项目完成进度百分比',
  start_time DATETIME(3) DEFAULT NULL COMMENT '项目开始时间',
  due_time DATETIME(3) DEFAULT NULL COMMENT '项目计划截止时间',
  tag_json JSON DEFAULT NULL COMMENT '项目引用的标签字典项主键集合',
  create_account_id BIGINT NOT NULL COMMENT '创建项目的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id),
  -- 唯一键把平台作用域的空系统和租户映射为 0，避免多个 NULL 绕过编号约束。
  UNIQUE KEY uk_work_project_scope_code (scope_type, (COALESCE(system_id, 0)),
    (COALESCE(tenant_id, 0)), code),
  KEY ix_work_project_scope (scope_type, system_id, tenant_id, status),
  KEY ix_work_project_owner (owner_account_id, due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作项目';

CREATE TABLE IF NOT EXISTS un_work_project_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目参与人关系主键',
  project_id BIGINT NOT NULL COMMENT '所属工作项目主键',
  account_id BIGINT NOT NULL COMMENT '参与平台账号主键',
  member_type VARCHAR(30) NOT NULL COMMENT '负责人协作或观察成员身份',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '成员关系状态',
  join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入项目时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_work_project_member (project_id, account_id, member_type),
  KEY ix_work_project_member_account (account_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作项目参与人';

CREATE TABLE IF NOT EXISTS un_work_task_group (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目任务组主键',
  project_id BIGINT NOT NULL COMMENT '所属工作项目主键',
  name VARCHAR(120) NOT NULL COMMENT '任务组阶段名称',
  owner_account_id BIGINT DEFAULT NULL COMMENT '任务组负责人平台账号主键',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '任务组状态',
  progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '任务组完成进度百分比',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '任务组显示顺序',
  due_time DATETIME(3) DEFAULT NULL COMMENT '任务组计划完成时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_work_task_group_project (project_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目任务组';

CREATE TABLE IF NOT EXISTS un_work_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工作任务主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统任务作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统任务所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统任务所属租户主键',
  project_id BIGINT DEFAULT NULL COMMENT '项目任务所属项目主键，普通任务为空',
  task_group_id BIGINT DEFAULT NULL COMMENT '项目任务所属任务组主键',
  parent_task_id BIGINT DEFAULT NULL COMMENT '子任务的上级任务主键',
  title VARCHAR(200) NOT NULL COMMENT '任务标题',
  description TEXT DEFAULT NULL COMMENT '任务详细内容',
  owner_account_id BIGINT DEFAULT NULL COMMENT '当前负责人平台账号主键',
  create_account_id BIGINT NOT NULL COMMENT '创建任务的平台账号主键',
  priority_code VARCHAR(60) DEFAULT NULL COMMENT '优先级字典项标识',
  status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '未开始进行阻塞待确认完成取消等状态',
  progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '任务完成进度百分比',
  expected_hours DECIMAL(10,2) DEFAULT NULL COMMENT '预计工作小时数',
  actual_hours DECIMAL(10,2) DEFAULT NULL COMMENT '实际工作小时数',
  due_time DATETIME(3) DEFAULT NULL COMMENT '任务计划截止时间',
  complete_time DATETIME(3) DEFAULT NULL COMMENT '任务实际完成时间',
  related_module_id BIGINT DEFAULT NULL COMMENT '关联业务模块主键',
  related_record_id BIGINT DEFAULT NULL COMMENT '关联业务记录主键',
  tag_json JSON DEFAULT NULL COMMENT '任务引用的标签字典项主键集合',
  field_values_json JSON DEFAULT NULL COMMENT '已发布任务字段配置对应的值',
  archived_time DATETIME(3) DEFAULT NULL COMMENT '任务归档时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_work_task_scope (scope_type, system_id, tenant_id, status, due_time),
  KEY ix_work_task_project (project_id, task_group_id, status),
  KEY ix_work_task_owner (owner_account_id, status, due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通任务及项目任务';

CREATE TABLE IF NOT EXISTS un_work_task_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务自定义字段配置主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统字段配置作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统任务字段所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统任务字段所属租户主键',
  task_kind VARCHAR(20) NOT NULL COMMENT '普通任务或项目任务类型',
  field_key VARCHAR(80) NOT NULL COMMENT '任务类型内稳定字段英文键',
  field_name VARCHAR(100) NOT NULL COMMENT '任务字段展示名称',
  field_type SMALLINT NOT NULL COMMENT '复用公共固定字段类型编号',
  config_json JSON NOT NULL COMMENT '草稿校验字典卡片和看板配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布字段运行配置快照',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布任务字段版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_work_task_field_scope (scope_type, system_id, tenant_id, task_kind, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务字段与看板取数配置';

CREATE TABLE IF NOT EXISTS un_work_task_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务参与人关系主键',
  task_id BIGINT NOT NULL COMMENT '所属工作任务主键',
  account_id BIGINT NOT NULL COMMENT '参与平台账号主键',
  member_type VARCHAR(20) NOT NULL COMMENT '协作关注或验收成员身份',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '参与关系状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入任务时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_work_task_member (task_id, account_id, member_type),
  KEY ix_work_task_member_account (account_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务协作关注与验收成员';

CREATE TABLE IF NOT EXISTS un_work_task_history (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务操作历史主键',
  task_id BIGINT NOT NULL COMMENT '所属工作任务主键',
  operator_account_id BIGINT NOT NULL COMMENT '实际操作平台账号主键',
  operation_code VARCHAR(60) NOT NULL COMMENT '任务创建转交延期评论等操作类型',
  before_json JSON DEFAULT NULL COMMENT '操作前业务值摘要',
  after_json JSON DEFAULT NULL COMMENT '操作后业务值摘要',
  content TEXT DEFAULT NULL COMMENT '评论进度说明或操作原因',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_work_task_history_task (task_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务状态与业务操作历史';

CREATE TABLE IF NOT EXISTS un_work_daily (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '每日工作日志版本主键',
  root_id BIGINT DEFAULT NULL COMMENT '同一日志首版主键，首版自身为空',
  version_no INT NOT NULL DEFAULT 1 COMMENT '手动提交撤回或补写形成的版本号',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统工作日志作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统工作日志所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统工作日志所属租户主键',
  account_id BIGINT NOT NULL COMMENT '工作日志所有人平台账号主键',
  log_time DATETIME(3) NOT NULL COMMENT '用户选择的工作日期，零时保存并按日期样式展示',
  completed_text TEXT DEFAULT NULL COMMENT '今日已完成内容',
  ongoing_text TEXT DEFAULT NULL COMMENT '正在进行内容',
  next_text TEXT DEFAULT NULL COMMENT '明日计划内容',
  risk_text TEXT DEFAULT NULL COMMENT '问题风险内容',
  help_text TEXT DEFAULT NULL COMMENT '需要协助内容',
  relation_json JSON DEFAULT NULL COMMENT '用户主动引用的任务项目业务对象和附件',
  field_values_json JSON DEFAULT NULL COMMENT '已发布日志字段配置对应的值',
  visibility_json JSON DEFAULT NULL COMMENT '仅自己上级项目团队或角色可见范围',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿提交或撤回状态',
  is_current TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否当前有效展示版本',
  submit_time DATETIME(3) DEFAULT NULL COMMENT '本版本提交时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本版本创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '本版本更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_work_daily_owner (scope_type, system_id, tenant_id, account_id, log_time),
  KEY ix_work_daily_root (root_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户手动创建的每日工作日志及版本';

CREATE TABLE IF NOT EXISTS un_work_daily_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工作日志字段配置主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统字段配置作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统字段所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统字段所属租户主键',
  field_key VARCHAR(80) NOT NULL COMMENT '作用域内稳定字段英文键',
  field_name VARCHAR(100) NOT NULL COMMENT '工作日志字段展示名称',
  field_type SMALLINT NOT NULL COMMENT '复用公共固定字段类型编号',
  config_json JSON NOT NULL COMMENT '草稿校验权限布局和字典配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布字段运行配置快照',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布工作日志字段版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_work_daily_field_scope (scope_type, system_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日志字段配置';

CREATE TABLE IF NOT EXISTS un_work_daily_comment (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工作日志评论主键',
  daily_root_id BIGINT NOT NULL COMMENT '被评论工作日志的首版主键',
  parent_id BIGINT DEFAULT NULL COMMENT '回复的上级评论主键',
  operator_account_id BIGINT NOT NULL COMMENT '评论平台账号主键',
  content TEXT NOT NULL COMMENT '评论或管理反馈内容',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '正常或撤回状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评论时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_work_daily_comment_root (daily_root_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日志评论与管理反馈';
