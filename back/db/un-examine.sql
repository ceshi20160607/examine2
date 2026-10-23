-- 审批定义和实例保留独立业务语义；旧 SQL 仅作字段关系参考，未复制其历史结构。
CREATE TABLE IF NOT EXISTS un_module_examine (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块审批配置主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '审批绑定定义租户主键',
  module_id BIGINT NOT NULL COMMENT '关联业务模块主键',
  examine_id BIGINT NOT NULL COMMENT '关联审批定义主键',
  action_menu_id BIGINT DEFAULT NULL COMMENT '手动发起审批操作菜单主键',
  trigger_code VARCHAR(80) DEFAULT NULL COMMENT '新建编辑状态变动等触发事件编码',
  condition_json JSON DEFAULT NULL COMMENT '结构化审批触发条件',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_module_examine_trigger (module_id, tenant_id, trigger_code, status),
  KEY ix_module_examine_examine (examine_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块审批关联配置';

CREATE TABLE IF NOT EXISTS un_examine_group (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批分组主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '审批分组所属租户主键',
  name VARCHAR(120) NOT NULL COMMENT '审批分组名称',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '审批分组显示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '分组启用或停用状态',
  create_account_id BIGINT NOT NULL COMMENT '创建分组的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_examine_group_scope (system_id, tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批分组';

CREATE TABLE IF NOT EXISTS un_examine (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批定义主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义审批的租户主键',
  group_id BIGINT DEFAULT NULL COMMENT '所属审批分组主键',
  module_id BIGINT DEFAULT NULL COMMENT '审批业务表单所属模块主键',
  code VARCHAR(80) NOT NULL COMMENT '租户内稳定审批编码',
  name VARCHAR(120) NOT NULL COMMENT '审批展示名称',
  icon VARCHAR(255) DEFAULT NULL COMMENT '审批展示图标',
  description VARCHAR(500) DEFAULT NULL COMMENT '审批业务说明',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '审批入口显示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布停用或归档状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '最近发布审批定义版本',
  published_snapshot_json JSON DEFAULT NULL COMMENT '最近发布的节点人员字段和高级规则快照',
  create_account_id BIGINT NOT NULL COMMENT '创建审批的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_examine_code (tenant_id, code),
  KEY ix_examine_group (group_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批定义';

CREATE TABLE IF NOT EXISTS un_examine_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批高级配置主键',
  examine_id BIGINT NOT NULL COMMENT '所属审批定义主键',
  rule_code VARCHAR(80) NOT NULL COMMENT '撤回通过超时或重审规则编码',
  rule_json JSON NOT NULL COMMENT '草稿适用条件超时兜底等规则',
  published_rule_json JSON DEFAULT NULL COMMENT '最近发布的高级规则快照',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_examine_config_rule (examine_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批撤回超时等高级配置';

CREATE TABLE IF NOT EXISTS un_examine_config_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批规则适用对象主键',
  config_id BIGINT NOT NULL COMMENT '所属审批高级配置主键',
  subject_type VARCHAR(20) NOT NULL COMMENT '适用人员部门或角色类型',
  subject_id BIGINT NOT NULL COMMENT '适用对象主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '配置时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_examine_config_user (config_id, subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批高级规则适用人员';

CREATE TABLE IF NOT EXISTS un_examine_node (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批草稿节点主键',
  examine_id BIGINT NOT NULL COMMENT '所属审批定义主键',
  node_code VARCHAR(80) NOT NULL COMMENT '审批定义内稳定节点编码',
  node_type VARCHAR(40) NOT NULL COMMENT '开始审批条件抄送动态或结束节点类型',
  name VARCHAR(120) NOT NULL COMMENT '节点展示名称',
  before_node_id BIGINT DEFAULT NULL COMMENT '默认上游审批节点主键',
  after_node_id BIGINT DEFAULT NULL COMMENT '默认下游审批节点主键',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '设计器节点顺序',
  config_json JSON NOT NULL COMMENT '草稿审批方式条件转交加签和超时配置',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_examine_node_code (examine_id, node_code),
  KEY ix_examine_node_order (examine_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批设计器节点';

CREATE TABLE IF NOT EXISTS un_examine_node_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批节点字段权限主键',
  node_id BIGINT NOT NULL COMMENT '所属审批草稿节点主键',
  field_id BIGINT NOT NULL COMMENT '关联模块字段稳定主键',
  readable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '节点处理人能否读取该字段',
  writable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '节点处理人能否写入该字段',
  required_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '节点提交时是否必填',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '配置时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_examine_node_field (node_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点字段配置';

CREATE TABLE IF NOT EXISTS un_examine_node_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批节点人员配置主键',
  node_id BIGINT NOT NULL COMMENT '所属审批草稿节点主键',
  subject_type VARCHAR(30) NOT NULL COMMENT '人员部门角色上级或外部身份来源类型',
  subject_id BIGINT DEFAULT NULL COMMENT '内部人员部门或角色主键',
  external_id VARCHAR(160) DEFAULT NULL COMMENT '外部审批人配置标识',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '顺序会签的处理顺序',
  config_json JSON DEFAULT NULL COMMENT '上级截止人及人员解析补充规则',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '配置时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_examine_node_user (node_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点处理人员配置';

CREATE TABLE IF NOT EXISTS un_examine_example (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批实例主键',
  examine_id BIGINT NOT NULL COMMENT '来源审批定义主键',
  published_version INT NOT NULL COMMENT '发起时审批定义发布版本',
  system_id BIGINT NOT NULL COMMENT '审批业务所属系统主键',
  tenant_id BIGINT NOT NULL COMMENT '审批业务所属租户主键',
  module_id BIGINT DEFAULT NULL COMMENT '被审批业务模块主键',
  record_id BIGINT DEFAULT NULL COMMENT '被审批业务记录主键',
  application_id BIGINT DEFAULT NULL COMMENT '外部发起所用受控应用主键',
  initiator_account_id BIGINT DEFAULT NULL COMMENT '内部实际发起平台账号主键',
  initiator_external_id VARCHAR(160) DEFAULT NULL COMMENT '外部发起人标识',
  status VARCHAR(30) NOT NULL DEFAULT 'RUNNING' COMMENT '审批中通过拒绝撤回终止或异常状态',
  runtime_snapshot_json JSON NOT NULL COMMENT '发起时完整审批字段人员规则和业务值快照',
  start_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审批发起时间',
  finish_time DATETIME(3) DEFAULT NULL COMMENT '审批完成时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '实例更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_examine_example_record (system_id, tenant_id, module_id, record_id),
  KEY ix_examine_example_status (examine_id, status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保存发起快照的审批实例';

CREATE TABLE IF NOT EXISTS un_examine_example_node (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批实例节点主键',
  example_id BIGINT NOT NULL COMMENT '所属审批实例主键',
  node_code VARCHAR(80) NOT NULL COMMENT '发布快照中的稳定节点编码',
  round_no INT NOT NULL DEFAULT 1 COMMENT '退回重审时的节点执行轮次',
  node_type VARCHAR(40) NOT NULL COMMENT '本轮节点审批条件或抄送类型',
  node_snapshot_json JSON NOT NULL COMMENT '本轮节点人员字段条件和审批方式快照',
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '待办通过拒绝跳过或取消状态',
  auto_reason VARCHAR(500) DEFAULT NULL COMMENT '自动处理或跳过节点的可解释原因',
  start_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '节点本轮开始时间',
  finish_time DATETIME(3) DEFAULT NULL COMMENT '节点本轮完成时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_examine_example_node (example_id, node_code, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例节点执行轮次';

CREATE TABLE IF NOT EXISTS un_examine_example_node_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批实例节点处理人主键',
  example_node_id BIGINT NOT NULL COMMENT '所属审批实例节点主键',
  account_id BIGINT DEFAULT NULL COMMENT '内部审批平台账号主键',
  external_id VARCHAR(160) DEFAULT NULL COMMENT '外部审批人身份标识',
  application_id BIGINT DEFAULT NULL COMMENT '外部审批身份所属应用主键',
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '待处理通过拒绝转办或抄送已读状态',
  opinion TEXT DEFAULT NULL COMMENT '审批意见及处理原因',
  action_json JSON DEFAULT NULL COMMENT '字段回写附件和代理加签操作快照',
  action_time DATETIME(3) DEFAULT NULL COMMENT '实际办理时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '待办创建时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_examine_example_user_node (example_node_id, status),
  KEY ix_examine_example_user_account (account_id, status),
  KEY ix_examine_example_user_external (application_id, external_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例节点实际处理人员';

CREATE TABLE IF NOT EXISTS un_examine_exception (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批异常主键',
  example_id BIGINT NOT NULL COMMENT '异常所属审批实例主键',
  example_node_id BIGINT DEFAULT NULL COMMENT '异常所在审批实例节点主键',
  exception_code VARCHAR(80) NOT NULL COMMENT '空审批人超时外部调用等异常编码',
  detail_json JSON DEFAULT NULL COMMENT '异常时审批上下文与失败原因快照',
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '待处理已重试或人工关闭状态',
  handler_account_id BIGINT DEFAULT NULL COMMENT '人工处置平台账号主键',
  resolution_json JSON DEFAULT NULL COMMENT '异常处置动作和结果摘要',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '异常发现时间',
  resolve_time DATETIME(3) DEFAULT NULL COMMENT '异常处置完成时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_examine_exception_open (status, create_time),
  KEY ix_examine_exception_example (example_id, example_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批运行异常与处置';
