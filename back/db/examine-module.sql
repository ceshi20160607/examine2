-- 模块配置与运行数据。配置发布和记录授权由模块业务 Service 校验。
CREATE TABLE IF NOT EXISTS un_module_group (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块组主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义模块组的租户主键',
  code VARCHAR(80) NOT NULL COMMENT '系统内模块组编码',
  name VARCHAR(100) NOT NULL COMMENT '运行态模块组名称',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '运行态导航排序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '模块组草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '最近发布版本号',
  create_account_id BIGINT NOT NULL COMMENT '创建平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_group_code (system_id, tenant_id, code),
  KEY ix_module_group_navigation (system_id, tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块分组配置';

CREATE TABLE IF NOT EXISTS un_module (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务模块主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义模块的租户主键',
  group_id BIGINT NOT NULL COMMENT '所属模块组主键',
  code VARCHAR(80) NOT NULL COMMENT '系统内模块编码',
  name VARCHAR(100) NOT NULL COMMENT '业务模块名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '模块业务说明',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '模块草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '最近发布版本号',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '组内模块导航排序',
  create_account_id BIGINT NOT NULL COMMENT '创建平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_code (system_id, tenant_id, code),
  KEY ix_module_group (group_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块';

CREATE TABLE IF NOT EXISTS un_module_dict (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统字典主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义字典的租户主键',
  code VARCHAR(80) NOT NULL COMMENT '系统内字典编码',
  name VARCHAR(100) NOT NULL COMMENT '字典名称',
  options_json JSON NOT NULL COMMENT '选项编码名称顺序与停用状态',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '字典发布状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '字典发布版本号',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_dict_code (system_id, tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统数据字典基础';

CREATE TABLE IF NOT EXISTS un_module_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块字段主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义字段的租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  field_code VARCHAR(80) NOT NULL COMMENT '模块内字段编码',
  field_name VARCHAR(100) NOT NULL COMMENT '字段展示名称',
  field_type VARCHAR(40) NOT NULL COMMENT '字段数据类型',
  required_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '保存时是否必填',
  readonly_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '表单是否只读',
  hidden_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '表单是否隐藏',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '设计器保存的字段顺序',
  layout_json JSON NOT NULL COMMENT '设计器保存的位置分组及占位宽度',
  config_json JSON NOT NULL COMMENT '类型校验默认值选项及权限配置',
  published_layout_json JSON DEFAULT NULL COMMENT '最近发布时的字段位置和占位宽度快照',
  published_config_json JSON DEFAULT NULL COMMENT '最近发布时的字段名称类型校验等配置快照',
  published_sort_no INT DEFAULT NULL COMMENT '最近发布时的字段顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '字段草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '字段生效版本号',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_field_code (module_id, tenant_id, field_code),
  KEY ix_module_field_form (module_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块字段';

CREATE TABLE IF NOT EXISTS un_module_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块菜单配置主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义菜单的租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  menu_id BIGINT DEFAULT NULL COMMENT '关联系统菜单主键',
  label VARCHAR(100) NOT NULL COMMENT '运行态菜单名称',
  route VARCHAR(255) DEFAULT NULL COMMENT '模块页面路由',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '菜单排序序号',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '菜单配置发布状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_module_menu_module (module_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块菜单配置';

CREATE TABLE IF NOT EXISTS un_module_action (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块动作配置主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '定义动作的租户主键',
  action_code VARCHAR(80) NOT NULL COMMENT '新建编辑删除导入等动作编码',
  enabled_flag TINYINT(1) NOT NULL DEFAULT 1 COMMENT '动作是否启用',
  config_json JSON DEFAULT NULL COMMENT '动作触发流程审批等配置',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '动作配置发布状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '动作生效版本号',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_action_code (module_id, tenant_id, action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块动作配置';

CREATE TABLE IF NOT EXISTS un_module_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块规则配置主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '定义规则的租户主键',
  rule_code VARCHAR(80) NOT NULL COMMENT '业务规则编码',
  rule_type VARCHAR(40) NOT NULL COMMENT '校验唯一联动等规则类型',
  rule_json JSON NOT NULL COMMENT '业务规则表达和参数',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '规则发布状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '规则生效版本号',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_rule_code (module_id, tenant_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块规则配置';

CREATE TABLE IF NOT EXISTS un_module_query_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '查询字段配置主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '配置所属租户主键',
  field_id BIGINT NOT NULL COMMENT '模块字段主键',
  search_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否用于快速搜索',
  filter_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否用于高级筛选',
  sort_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许结果排序',
  config_json JSON DEFAULT NULL COMMENT '查询方式及可用操作符配置',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '查询配置发布状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_query_field (module_id, tenant_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询模块字段配置';

CREATE TABLE IF NOT EXISTS un_module_tenant (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户模块关系主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '使用模块的租户主键',
  module_id BIGINT NOT NULL COMMENT '主租户或本租户模块主键',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '租户模块使用状态',
  config_json JSON DEFAULT NULL COMMENT '租户允许扩展的模块配置',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_tenant (tenant_id, module_id), KEY ix_module_tenant_module (module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户对基础模块关系记录';

CREATE TABLE IF NOT EXISTS un_module_tenant_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户字段扩展主键',
  module_id BIGINT NOT NULL COMMENT '基础业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '扩展字段的租户主键',
  field_id BIGINT NOT NULL COMMENT '租户扩展模块字段主键',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '租户扩展字段发布状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '租户扩展生效版本号',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_tenant_field (tenant_id, field_id), KEY ix_module_tenant_field_module (module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户对字段的扩展记录';

CREATE TABLE IF NOT EXISTS un_module_field_sort (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '个人列表列配置主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '用户当前租户主键',
  account_id BIGINT NOT NULL COMMENT '配置该列表的平台账号主键',
  field_id BIGINT NOT NULL COMMENT '对应模块字段主键',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '个人列表列顺序',
  width_px INT DEFAULT NULL COMMENT '个人列表列宽像素',
  hidden_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '个人列表列隐藏标记',
  fixed_side VARCHAR(10) DEFAULT NULL COMMENT '列固定在左侧或右侧',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_field_sort (module_id, tenant_id, account_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块字段排序';

CREATE TABLE IF NOT EXISTS un_module_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通用业务记录主键',
  system_id BIGINT NOT NULL COMMENT '记录所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '记录原始所属租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  name VARCHAR(255) DEFAULT NULL COMMENT '业务记录标题',
  num VARCHAR(100) DEFAULT NULL COMMENT '业务记录编号',
  status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  owner_user_id BIGINT NOT NULL COMMENT '当前负责人平台账号主键',
  owner_dept_id BIGINT DEFAULT NULL COMMENT '负责人在本租户所属部门主键',
  create_user_id BIGINT NOT NULL COMMENT '创建业务归属账号主键',
  actual_operator_id BIGINT NOT NULL COMMENT '实际提交操作的账号主键',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_record_num (module_id, tenant_id, num),
  KEY ix_module_record_list (module_id, tenant_id, deleted_time, id),
  KEY ix_module_record_owner (module_id, tenant_id, owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块通用业务主记录';

CREATE TABLE IF NOT EXISTS un_module_record_data (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务字段值主键',
  record_id BIGINT NOT NULL COMMENT '通用业务主记录主键',
  field_id BIGINT NOT NULL COMMENT '保存时对应的模块字段主键',
  field_code VARCHAR(80) NOT NULL COMMENT '保存时的字段编码快照',
  value_json JSON DEFAULT NULL COMMENT '按字段类型保存的业务值',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_record_field (record_id, field_id),
  KEY ix_module_record_data_field (field_id, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块通用业务扩展记录';

CREATE TABLE IF NOT EXISTS un_module_scence (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块场景主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  owner_account_id BIGINT DEFAULT NULL COMMENT '个人场景的创建账号主键，公共场景为空',
  code VARCHAR(80) NOT NULL COMMENT '场景编码',
  name VARCHAR(100) NOT NULL COMMENT '场景展示名称',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '场景草稿发布状态',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '场景展示顺序',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_scence_code (module_id, tenant_id, code),
  KEY ix_module_scence_list (module_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块场景';

CREATE TABLE IF NOT EXISTS un_module_scence_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '场景查询配置主键',
  scence_id BIGINT NOT NULL COMMENT '关联场景主键',
  filter_json JSON DEFAULT NULL COMMENT '已保存的结构化筛选条件',
  column_json JSON DEFAULT NULL COMMENT '场景默认列表列配置',
  sort_json JSON DEFAULT NULL COMMENT '场景默认排序配置',
  published_version INT NOT NULL DEFAULT 0 COMMENT '场景查询配置发布版本号',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_scence_config (scence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块场景配置';

CREATE TABLE IF NOT EXISTS un_module_teamer (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录团队成员主键',
  record_id BIGINT NOT NULL COMMENT '业务主记录主键',
  account_id BIGINT NOT NULL COMMENT '团队平台账号主键',
  member_type VARCHAR(30) NOT NULL COMMENT '负责协作只读或关注身份',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '团队关系状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_teamer (record_id, account_id, member_type),
  KEY ix_module_teamer_account (account_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块团队成员';

CREATE TABLE IF NOT EXISTS un_module_relation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务记录关联主键',
  source_record_id BIGINT NOT NULL COMMENT '来源业务记录主键',
  target_record_id BIGINT NOT NULL COMMENT '目标业务记录主键',
  relation_config_id BIGINT NOT NULL COMMENT '对应模块关联配置主键',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '子表明细或关联展示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '关联状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_relation (source_record_id, target_record_id, relation_config_id),
  KEY ix_module_relation_target (target_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块关联关系';

CREATE TABLE IF NOT EXISTS un_module_relation_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块关联配置主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义关联的租户主键',
  source_module_id BIGINT NOT NULL COMMENT '来源业务模块主键',
  target_module_id BIGINT NOT NULL COMMENT '目标业务模块主键',
  source_field_id BIGINT DEFAULT NULL COMMENT '关联或子表来源字段主键',
  relation_type VARCHAR(40) NOT NULL COMMENT '单选多选子表或上下游关系类型',
  config_json JSON DEFAULT NULL COMMENT '显示字段筛选条件及反向配置',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '关联配置发布状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_module_relation_config_source (source_module_id, status),
  KEY ix_module_relation_config_target (target_module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块关联关系配置';

CREATE TABLE IF NOT EXISTS un_module_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块变动日志主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '操作租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  record_id BIGINT DEFAULT NULL COMMENT '变动业务记录主键',
  operator_account_id BIGINT NOT NULL COMMENT '实际操作平台账号主键',
  action_code VARCHAR(80) NOT NULL COMMENT '配置发布或记录操作类型',
  detail_json JSON DEFAULT NULL COMMENT '变动字段与业务上下文摘要',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  PRIMARY KEY (id), KEY ix_module_log_record (record_id, create_time),
  KEY ix_module_log_module (module_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块变动记录';
