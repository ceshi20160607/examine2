-- 系统、租户、成员与授权数据。系统创建及租户模式变更在业务事务中执行。
CREATE TABLE IF NOT EXISTS un_sys_system (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务系统主键',
  code VARCHAR(80) NOT NULL COMMENT '系统唯一编码',
  name VARCHAR(100) NOT NULL COMMENT '系统名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '系统业务说明',
  owner_account_id BIGINT NOT NULL COMMENT '当前系统所属平台账号主键',
  root_tenant_id BIGINT DEFAULT NULL COMMENT '创建时同步生成的主租户主键',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '系统状态',
  create_account_id BIGINT NOT NULL COMMENT '创建平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_system_code (code), KEY ix_sys_system_owner (owner_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台内业务系统';

CREATE TABLE IF NOT EXISTS un_sys_system_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统基础配置主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  multi_tenant_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '多租户模式开关，关闭时保留非主租户数据',
  permission_revision BIGINT NOT NULL DEFAULT 0 COMMENT '权限配置版本，变更后隔离旧缓存',
  login_title VARCHAR(120) DEFAULT NULL COMMENT '系统登录展示标题',
  login_domain VARCHAR(190) DEFAULT NULL COMMENT '系统专属登录域名',
  default_home VARCHAR(255) DEFAULT NULL COMMENT '系统默认首页路由',
  settings_json JSON DEFAULT NULL COMMENT '已配置系统功能开关和参数',
  update_account_id BIGINT DEFAULT NULL COMMENT '最近修改平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_config_system (system_id), UNIQUE KEY uk_sys_config_domain (login_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务系统基础配置';

CREATE TABLE IF NOT EXISTS un_sys_tenant (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统租户主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  code VARCHAR(80) NOT NULL COMMENT '系统内租户编码',
  name VARCHAR(100) NOT NULL COMMENT '租户名称',
  is_root TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认主租户',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '租户自身启停状态',
  login_domain VARCHAR(190) DEFAULT NULL COMMENT '租户专属登录域名',
  display_json JSON DEFAULT NULL COMMENT '租户登录展示配置',
  quota_json JSON DEFAULT NULL COMMENT '租户成员模块字段存储等配额',
  create_account_id BIGINT NOT NULL COMMENT '创建平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_tenant_code (system_id, code),
  KEY ix_sys_tenant_system_root (system_id, is_root), KEY ix_sys_tenant_domain (login_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统租户';

CREATE TABLE IF NOT EXISTS un_sys_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统成员主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  account_id BIGINT NOT NULL COMMENT '对应统一平台账号主键',
  display_name VARCHAR(100) DEFAULT NULL COMMENT '本系统显示名称',
  employee_no VARCHAR(80) DEFAULT NULL COMMENT '本系统员工编号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '系统成员状态',
  join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入系统时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_member_account (system_id, account_id),
  UNIQUE KEY uk_sys_member_employee (system_id, employee_no), KEY ix_sys_member_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统成员';

CREATE TABLE IF NOT EXISTS un_sys_department (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户部门主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  parent_id BIGINT DEFAULT NULL COMMENT '同租户上级部门主键',
  code VARCHAR(80) NOT NULL COMMENT '租户内部门编码',
  name VARCHAR(100) NOT NULL COMMENT '部门名称',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '同级排序序号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '部门状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_department_code (tenant_id, code),
  KEY ix_sys_department_parent (tenant_id, parent_id), KEY ix_sys_department_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户组织部门';

CREATE TABLE IF NOT EXISTS un_sys_tenant_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户成员关系主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  member_id BIGINT NOT NULL COMMENT '系统成员主键',
  department_id BIGINT DEFAULT NULL COMMENT '当前租户部门主键',
  is_admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否租户管理员',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '租户成员状态',
  join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入租户时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_tenant_member (tenant_id, member_id),
  KEY ix_sys_tenant_member_member (member_id), KEY ix_sys_tenant_member_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户成员';

CREATE TABLE IF NOT EXISTS un_sys_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统菜单主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义菜单的租户主键',
  parent_id BIGINT DEFAULT NULL COMMENT '上级菜单主键',
  code VARCHAR(100) NOT NULL COMMENT '系统菜单权限编码',
  name VARCHAR(100) NOT NULL COMMENT '菜单名称',
  menu_type VARCHAR(20) NOT NULL DEFAULT 'PAGE' COMMENT '菜单类型：目录页面或操作',
  route VARCHAR(255) DEFAULT NULL COMMENT '前端路由',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '菜单排序序号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '菜单状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_menu_code (tenant_id, code), KEY ix_sys_menu_parent (tenant_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户菜单';

CREATE TABLE IF NOT EXISTS un_sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户角色主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  code VARCHAR(80) NOT NULL COMMENT '租户角色编码',
  name VARCHAR(100) NOT NULL COMMENT '角色名称',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色状态',
  description VARCHAR(500) DEFAULT NULL COMMENT '角色业务说明',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_code (tenant_id, code), KEY ix_sys_role_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色';

CREATE TABLE IF NOT EXISTS un_sys_member_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户成员角色关系主键',
  tenant_member_id BIGINT NOT NULL COMMENT '租户成员关系主键',
  role_id BIGINT NOT NULL COMMENT '租户角色主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_member_role (tenant_member_id, role_id, (IF(deleted_time IS NULL, 1, NULL))), KEY ix_sys_member_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户成员角色关系';

CREATE TABLE IF NOT EXISTS un_sys_role_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户角色菜单授权主键',
  role_id BIGINT NOT NULL COMMENT '租户角色主键',
  menu_id BIGINT NOT NULL COMMENT '租户菜单主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_menu (role_id, menu_id, (IF(deleted_time IS NULL, 1, NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统租户角色菜单授权';

CREATE TABLE IF NOT EXISTS un_sys_role_data (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统角色数据权限主键',
  role_id BIGINT NOT NULL COMMENT '租户角色主键',
  menu_id BIGINT NOT NULL COMMENT '对应操作菜单主键',
  scope_code VARCHAR(40) NOT NULL COMMENT '负责人和组织数据范围编码',
  scope_config JSON DEFAULT NULL COMMENT '指定负责人和部门范围',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_data_menu (role_id, menu_id, (IF(deleted_time IS NULL, 1, NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色数据权限';

CREATE TABLE IF NOT EXISTS un_sys_role_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色模块字段许可主键',
  role_id BIGINT NOT NULL COMMENT '当前租户角色主键',
  module_id BIGINT NOT NULL COMMENT '字段所属业务模块主键',
  field_id BIGINT NOT NULL COMMENT '稳定模块字段主键',
  readable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许读取字段',
  writable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许写入字段',
  exportable_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许导出字段',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_field (role_id, module_id, field_id, (IF(deleted_time IS NULL, 1, NULL))), KEY ix_sys_role_field_module (module_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色对模块字段的控制';

CREATE TABLE IF NOT EXISTS un_sys_log_login (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统登录日志主键',
  system_id BIGINT NOT NULL COMMENT '访问业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '访问租户主键',
  account_id BIGINT DEFAULT NULL COMMENT '登录平台账号主键',
  result_code VARCHAR(40) NOT NULL COMMENT '系统登录结果编码',
  failure_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  ip_address VARCHAR(45) DEFAULT NULL COMMENT '请求地址',
  trace_id VARCHAR(80) DEFAULT NULL COMMENT '请求追踪标识',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_sys_login_system_time (system_id, create_time), KEY ix_sys_login_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统登录日志';

CREATE TABLE IF NOT EXISTS un_sys_log_opt (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统操作日志主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '操作租户主键',
  account_id BIGINT NOT NULL COMMENT '操作平台账号主键',
  action_code VARCHAR(100) NOT NULL COMMENT '操作类型编码',
  object_type VARCHAR(80) NOT NULL COMMENT '操作对象类型',
  object_id BIGINT DEFAULT NULL COMMENT '操作对象主键',
  detail_json JSON DEFAULT NULL COMMENT '操作摘要和权限影响范围',
  trace_id VARCHAR(80) DEFAULT NULL COMMENT '请求追踪标识',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_sys_opt_system_time (system_id, create_time), KEY ix_sys_opt_object (object_type, object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志';

CREATE TABLE IF NOT EXISTS un_sys_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统消息主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  recipient_member_id BIGINT NOT NULL COMMENT '接收系统成员主键',
  title VARCHAR(200) NOT NULL COMMENT '消息标题',
  content TEXT NOT NULL COMMENT '消息正文',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '关联业务类型',
  target_id BIGINT DEFAULT NULL COMMENT '关联业务主键',
  status VARCHAR(20) NOT NULL DEFAULT 'UNREAD' COMMENT '消息阅读归档状态',
  read_time DATETIME(3) DEFAULT NULL COMMENT '阅读时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_sys_message_recipient (recipient_member_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息';

-- 模块配置与运行数据。配置发布和记录授权由模块业务 Service 校验。
CREATE TABLE IF NOT EXISTS un_module_group (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块组主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义模块组的租户主键',
  code VARCHAR(80) NOT NULL COMMENT '系统内模块组编码',
  name VARCHAR(100) NOT NULL COMMENT '运行态模块组名称',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '运行态导航排序',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '模块组启停状态，保存即生效',
  create_account_id BIGINT NOT NULL COMMENT '创建平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
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
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '模块启停状态，保存即生效',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '组内模块导航排序',
  create_account_id BIGINT NOT NULL COMMENT '创建平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_code (system_id, tenant_id, code, (IF(deleted_time IS NULL, 1, NULL))),
  KEY ix_module_group (group_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块';

CREATE TABLE IF NOT EXISTS un_module_dict (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典基础项稳定主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义基础项的租户主键',
  dict_code VARCHAR(80) NOT NULL COMMENT '基础值所属的字典分类编码',
  dict_value VARCHAR(100) NOT NULL COMMENT '基础值，例如1或0；创建后不可变更含义',
  name VARCHAR(100) NOT NULL COMMENT '基础项当前名称，例如是或否',
  parent_id BIGINT DEFAULT NULL COMMENT '树形或级联基础项的父级主键，根项为空',
  depth_level INT NOT NULL DEFAULT 0 COMMENT '多级字典项从根开始的层级',
  depth_path VARCHAR(1000) DEFAULT NULL COMMENT '从根到本项的稳定主键路径',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '同级字典项展示顺序',
  color VARCHAR(30) DEFAULT NULL COMMENT '状态选项展示颜色',
  icon VARCHAR(100) DEFAULT NULL COMMENT '状态选项展示图标',
  default_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '所属字典分类中的默认选项标记',
  revision BIGINT NOT NULL DEFAULT 1 COMMENT '基础项资料提交版本，用于缓存隔离',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_dict_value (system_id, tenant_id, dict_code, dict_value),
  KEY ix_module_dict_scope (system_id, tenant_id, dict_code, parent_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逐条保存值与名称的字典基础项';

CREATE TABLE IF NOT EXISTS un_module_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块字段主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义字段的租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  field_key VARCHAR(80) NOT NULL COMMENT '固定字段或自定义字段的英文键',
  field_name VARCHAR(100) NOT NULL COMMENT '字段展示名称',
  field_type SMALLINT NOT NULL COMMENT '固定字段类型编号，按core统一枚举',
  required_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '保存时是否必填',
  readonly_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '表单是否只读',
  hidden_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '表单是否隐藏',
  unique_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '当前租户模块内是否要求值唯一',
  hint_text VARCHAR(500) DEFAULT NULL COMMENT '字段说明和输入提示',
  placeholder_text VARCHAR(200) DEFAULT NULL COMMENT '表单输入占位文案',
  default_value VARCHAR(500) DEFAULT NULL COMMENT '按字段类型校验后的默认值文本',
  display_style VARCHAR(30) NOT NULL DEFAULT 'DEFAULT' COMMENT '按字段类型解释的表单及详情展示样式',
  min_length INT DEFAULT NULL COMMENT '文本最少字符数',
  max_length INT DEFAULT NULL COMMENT '文本最多字符数',
  input_pattern VARCHAR(500) DEFAULT NULL COMMENT '文本格式校验正则',
  number_min DECIMAL(65,30) DEFAULT NULL COMMENT '数字允许的最小值',
  number_max DECIMAL(65,30) DEFAULT NULL COMMENT '数字允许的最大值',
  number_precision TINYINT UNSIGNED DEFAULT NULL COMMENT '数字总位数',
  number_scale TINYINT UNSIGNED DEFAULT NULL COMMENT '数字小数位数',
  rounding_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '超出小数位时是否四舍五入',
  unit_dict_code VARCHAR(80) DEFAULT NULL COMMENT '数字单位对应的字典编码',
  currency_code CHAR(3) DEFAULT NULL COMMENT '金额样式使用的ISO货币代码',
  min_items INT DEFAULT NULL COMMENT '多值字段最少选取项数',
  max_items INT DEFAULT NULL COMMENT '多值字段最多选取项数',
  multiple_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '选项、人员、部门或关联模块是否可多选',
  dict_code VARCHAR(80) DEFAULT NULL COMMENT '选项或级联使用的字典编码',
  config_json JSON DEFAULT NULL COMMENT '字段结构化配置：选项联动、关联方式及目标字段、自动编号和公式规则',
  relation_module_id BIGINT DEFAULT NULL COMMENT '关联字段指向的已存在目标模块主键',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '设计器保存的字段顺序',
  row_no INT NOT NULL DEFAULT 0 COMMENT '字段所在表单行号',
  column_no INT NOT NULL DEFAULT 0 COMMENT '字段所在行的列号',
  width_percent TINYINT UNSIGNED NOT NULL DEFAULT 100 COMMENT '字段在所在行的占位宽度百分比',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '字段启停状态，保存即生效',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_field_key (module_id, tenant_id, field_key),
  KEY ix_module_field_form (module_id, status, sort_no),
  KEY ix_module_field_relation (relation_module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块字段';

CREATE TABLE IF NOT EXISTS un_module_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块菜单关系主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义菜单的租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  menu_id BIGINT NOT NULL COMMENT '统一系统菜单主键',
  display_position VARCHAR(40) NOT NULL DEFAULT 'DETAIL_HEADER' COMMENT '模块内菜单展示位置',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '同位置展示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '模块菜单启停状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_menu_binding (module_id, menu_id), KEY ix_module_menu_module (module_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块与统一菜单的关联和展示配置';

CREATE TABLE IF NOT EXISTS un_module_action (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块动作执行配置主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '定义动作的租户主键',
  menu_id BIGINT NOT NULL COMMENT '动作对应的唯一菜单主键',
  execution_type VARCHAR(40) NOT NULL COMMENT '基础动作或字段更新执行类型',
  enabled_flag TINYINT(1) NOT NULL DEFAULT 1 COMMENT '草稿动作是否启用',
  config_json JSON DEFAULT NULL COMMENT '草稿字段条件转化映射及执行配置',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '动作启停状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_action_menu (menu_id), KEY ix_module_action_module (module_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单操作的模块动作执行配置';

CREATE TABLE IF NOT EXISTS un_module_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块规则配置主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '定义规则的租户主键',
  rule_code VARCHAR(80) NOT NULL COMMENT '业务规则编码',
  rule_type VARCHAR(40) NOT NULL COMMENT '校验唯一联动等规则类型',
  rule_json JSON NOT NULL COMMENT '业务规则表达和参数',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '规则启停状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
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
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '查询配置启停状态，保存即生效',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
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
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_tenant (tenant_id, module_id), KEY ix_module_tenant_module (module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户对基础模块关系记录';

CREATE TABLE IF NOT EXISTS un_module_tenant_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户字段扩展主键',
  module_id BIGINT NOT NULL COMMENT '基础业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '扩展字段的租户主键',
  field_id BIGINT NOT NULL COMMENT '租户扩展模块字段主键',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '租户扩展字段启停状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
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
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_field_sort (module_id, tenant_id, account_id, field_id, (IF(deleted_time IS NULL, 1, NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块字段排序';

CREATE TABLE IF NOT EXISTS un_module_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通用业务记录主键',
  system_id BIGINT NOT NULL COMMENT '记录所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '记录原始所属租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  name VARCHAR(255) DEFAULT NULL COMMENT '业务记录标题',
  num VARCHAR(100) DEFAULT NULL COMMENT '业务记录编号',
  status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  item_number01 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值一',
  item_number02 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值二',
  item_number03 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值三',
  item_number04 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值四',
  item_number05 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值五',
  item_number06 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值六',
  item_number07 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值七',
  item_number08 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值八',
  item_number09 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值九',
  item_number10 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值十',
  item_number11 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值十一',
  item_number12 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值十二',
  item_number13 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值十三',
  item_number14 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值十四',
  item_number15 DECIMAL(65,30) DEFAULT NULL COMMENT '动态数字字段预留值十五',
  item_text01 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值一',
  item_text02 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值二',
  item_text03 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值三',
  item_text04 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值四',
  item_text05 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值五',
  item_text06 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值六',
  item_text07 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值七',
  item_text08 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值八',
  item_text09 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值九',
  item_text10 VARCHAR(255) DEFAULT NULL COMMENT '动态短文本字段预留值十',
  item_long_text01 TEXT DEFAULT NULL COMMENT '动态长文本字段预留值一',
  item_long_text02 TEXT DEFAULT NULL COMMENT '动态长文本字段预留值二',
  item_time01 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值一',
  item_time02 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值二',
  item_time03 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值三',
  item_time04 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值四',
  item_time05 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值五',
  item_time06 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值六',
  item_time07 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值七',
  item_time08 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值八',
  item_time09 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值九',
  item_time10 DATETIME(3) DEFAULT NULL COMMENT '动态日期时间字段预留值十',
  item_user01 BIGINT DEFAULT NULL COMMENT '动态人员字段预留值一',
  item_user02 BIGINT DEFAULT NULL COMMENT '动态人员字段预留值二',
  item_user03 BIGINT DEFAULT NULL COMMENT '动态人员字段预留值三',
  item_user04 BIGINT DEFAULT NULL COMMENT '动态人员字段预留值四',
  item_user05 BIGINT DEFAULT NULL COMMENT '动态人员字段预留值五',
  item_dept01 BIGINT DEFAULT NULL COMMENT '动态部门字段预留值一',
  item_dept02 BIGINT DEFAULT NULL COMMENT '动态部门字段预留值二',
  item_dept03 BIGINT DEFAULT NULL COMMENT '动态部门字段预留值三',
  item_dept04 BIGINT DEFAULT NULL COMMENT '动态部门字段预留值四',
  item_dept05 BIGINT DEFAULT NULL COMMENT '动态部门字段预留值五',
  item_module01 BIGINT DEFAULT NULL COMMENT '动态关联模块字段预留值一',
  item_module02 BIGINT DEFAULT NULL COMMENT '动态关联模块字段预留值二',
  item_module03 BIGINT DEFAULT NULL COMMENT '动态关联模块字段预留值三',
  item_module04 BIGINT DEFAULT NULL COMMENT '动态关联模块字段预留值四',
  item_module05 BIGINT DEFAULT NULL COMMENT '动态关联模块字段预留值五',
  item_select01 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值一',
  item_select02 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值二',
  item_select03 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值三',
  item_select04 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值四',
  item_select05 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值五',
  item_select06 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值六',
  item_select07 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值七',
  item_select08 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值八',
  item_select09 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值九',
  item_select10 VARCHAR(255) DEFAULT NULL COMMENT '动态单选字段预留值十',
  owner_user_id BIGINT NOT NULL COMMENT '当前负责人平台账号主键',
  owner_dept_id BIGINT DEFAULT NULL COMMENT '负责人在本租户所属部门主键',
  create_user_id BIGINT NOT NULL COMMENT '创建业务归属账号主键',
  actual_operator_id BIGINT NOT NULL COMMENT '实际提交操作的账号主键',
  update_user_id BIGINT DEFAULT NULL COMMENT '最近一次修改业务记录的平台账号主键',
  lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '业务记录并发写入乐观锁版本',
  archived_time DATETIME(3) DEFAULT NULL COMMENT '业务记录归档时间',
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
  field_key VARCHAR(80) NOT NULL COMMENT '保存时的字段英文键快照',
  value_varchar VARCHAR(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '短字符串及附件批次UUID业务值',
  value_text TEXT DEFAULT NULL COMMENT '多行文本及其他长文本业务值',
  value_number DECIMAL(65,30) DEFAULT NULL COMMENT '数值标识和布尔等数值业务值',
  value_time DATETIME(3) DEFAULT NULL COMMENT '日期时间业务值，日期样式按零时保存',
  value_json JSON DEFAULT NULL COMMENT '多选级联和结构化字段业务值',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_record_field (record_id, field_id),
  KEY ix_module_record_data_field (field_id, record_id),
  KEY ix_module_record_data_varchar (field_id, value_varchar, record_id),
  KEY ix_module_record_data_number (field_id, value_number, record_id),
  KEY ix_module_record_data_time (field_id, value_time, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块通用业务扩展记录';

CREATE TABLE IF NOT EXISTS un_module_scence (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块场景主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  owner_account_id BIGINT DEFAULT NULL COMMENT '个人场景的创建账号主键，公共场景为空',
  code VARCHAR(80) NOT NULL COMMENT '场景编码',
  name VARCHAR(100) NOT NULL COMMENT '场景展示名称',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '场景启停状态，保存即生效',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '场景展示顺序',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_scence_code (module_id, tenant_id, code),
  KEY ix_module_scence_list (module_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块场景';

CREATE TABLE IF NOT EXISTS un_module_scence_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '场景查询配置主键',
  scence_id BIGINT NOT NULL COMMENT '关联场景主键',
  filter_json JSON DEFAULT NULL COMMENT '已保存的结构化筛选条件',
  column_json JSON DEFAULT NULL COMMENT '场景默认列表列配置',
  sort_json JSON DEFAULT NULL COMMENT '场景默认排序配置',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_scence_config (scence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块场景配置';

CREATE TABLE IF NOT EXISTS un_module_teamer (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录团队成员主键',
  record_id BIGINT NOT NULL COMMENT '业务主记录主键',
  account_id BIGINT NOT NULL COMMENT '团队平台账号主键',
  member_type VARCHAR(30) NOT NULL COMMENT '负责协作只读或关注身份',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '团队关系状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_teamer (record_id, account_id, member_type),
  KEY ix_module_teamer_account (account_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块团队成员';

CREATE TABLE IF NOT EXISTS un_module_relation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务记录关联主键',
  source_record_id BIGINT NOT NULL COMMENT '来源业务记录主键',
  target_record_id BIGINT NOT NULL COMMENT '目标业务记录主键',
  relation_config_id BIGINT DEFAULT NULL COMMENT '普通关联对应配置主键，转化关系为空',
  relation_type VARCHAR(20) NOT NULL DEFAULT 'RELATION' COMMENT '普通关联或模块转化关系',
  target_module_id BIGINT DEFAULT NULL COMMENT '转化目标模块主键，普通关联为空',
  action_menu_id BIGINT DEFAULT NULL COMMENT '产生转化关系的操作菜单主键',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '多条关联记录的展示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '关联状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_relation (source_record_id, target_record_id, relation_config_id),
  UNIQUE KEY uk_module_conversion_target (source_record_id, target_module_id, relation_type), KEY ix_module_relation_target (target_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块关联关系';

CREATE TABLE IF NOT EXISTS un_module_relation_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块关联配置主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义关联的租户主键',
  source_module_id BIGINT NOT NULL COMMENT '来源业务模块主键',
  target_module_id BIGINT NOT NULL COMMENT '目标业务模块主键',
  source_field_id BIGINT DEFAULT NULL COMMENT '普通关联来源字段主键',
  relation_type VARCHAR(40) NOT NULL COMMENT '关联或上下游业务关系类型',
  config_json JSON DEFAULT NULL COMMENT '显示字段筛选条件及反向配置',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '关联配置启停状态，保存即生效',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_module_relation_config_source (source_module_id, status),
  KEY ix_module_relation_config_target (target_module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块关联关系配置';

CREATE TABLE IF NOT EXISTS un_module_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块变动日志主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '业务记录或配置所属租户主键',
  actor_tenant_id BIGINT DEFAULT NULL COMMENT '实际操作人的当前租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  record_id BIGINT DEFAULT NULL COMMENT '变动业务记录主键',
  share_id BIGINT DEFAULT NULL COMMENT '跨租户操作使用的共享授权主键',
  operator_account_id BIGINT NOT NULL COMMENT '实际操作平台账号主键',
  action_code VARCHAR(80) NOT NULL COMMENT '配置发布或记录操作类型',
  detail_json JSON DEFAULT NULL COMMENT '变动字段与业务上下文摘要',
  permission_json JSON DEFAULT NULL COMMENT '操作发生时的菜单字段及数据权限摘要',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_module_log_record (record_id, create_time),
  KEY ix_module_log_module (module_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块变动记录';

-- 共享只授予访问关系，不复制记录或改变来源租户；撤销的历史由模块日志永久保留。
CREATE TABLE IF NOT EXISTS un_module_share (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '跨租户业务数据共享主键',
  system_id BIGINT NOT NULL COMMENT '来源与目标共同所属业务系统主键',
  source_tenant_id BIGINT NOT NULL COMMENT '业务数据原始所属租户主键',
  target_tenant_id BIGINT NOT NULL COMMENT '获授权访问的目标租户主键',
  module_id BIGINT NOT NULL COMMENT '共享业务模块主键',
  record_id BIGINT DEFAULT NULL COMMENT '指定单条共享记录主键，范围共享时为空',
  scope_json JSON DEFAULT NULL COMMENT '按负责人和结构化条件配置的共享记录范围',
  action_menu_ids_json JSON NOT NULL COMMENT '单独允许的详情编辑导出等操作菜单主键',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '共享生效停用或已撤销状态',
  effective_time DATETIME(3) NOT NULL COMMENT '共享开始生效时间',
  expire_time DATETIME(3) DEFAULT NULL COMMENT '共享到期时间',
  create_account_id BIGINT NOT NULL COMMENT '来源租户创建共享的平台账号主键',
  revoke_account_id BIGINT DEFAULT NULL COMMENT '实际撤销共享的平台账号主键',
  revoke_time DATETIME(3) DEFAULT NULL COMMENT '共享撤销时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '共享创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '共享更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_module_share_target (system_id, target_tenant_id, module_id, status, effective_time),
  KEY ix_module_share_record (record_id, target_tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨租户业务数据显式共享';

-- 编号规则由模块业务 Service 锁定同一作用域的这一行递增，不能查询最大编号推算。
CREATE TABLE IF NOT EXISTS un_module_sequence (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块业务编号序列主键',
  module_id BIGINT NOT NULL COMMENT '生成编号的业务模块主键',
  tenant_id BIGINT NOT NULL COMMENT '编号所属租户主键',
  rule_code VARCHAR(80) NOT NULL COMMENT '使用的模块编号规则编码',
  period_key VARCHAR(30) NOT NULL COMMENT '按规则重置的日期月份或固定周期键',
  current_value BIGINT NOT NULL DEFAULT 0 COMMENT '该作用域已分配最大序号',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '序号最近分配时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_sequence (module_id, tenant_id, rule_code, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块自动编号并发序列';

CREATE TABLE IF NOT EXISTS un_module_exchange_job (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块导入导出作业主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '所属租户主键',
  module_id BIGINT NOT NULL COMMENT '操作业务模块主键',
  operator_account_id BIGINT NOT NULL COMMENT '发起操作的平台账号主键',
  job_type VARCHAR(20) NOT NULL COMMENT '导入或导出作业类型',
  import_mode VARCHAR(20) DEFAULT NULL COMMENT '导入新增或匹配更新模式',
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '预演执行成功失败或回滚状态',
  config_json JSON NOT NULL COMMENT '提交时字段映射筛选和权限配置快照',
  source_file_id BIGINT DEFAULT NULL COMMENT '导入来源公共文件主键',
  result_file_id BIGINT DEFAULT NULL COMMENT '导出结果公共文件主键',
  error_file_id BIGINT DEFAULT NULL COMMENT '错误行下载公共文件主键',
  total_count INT NOT NULL DEFAULT 0 COMMENT '本批行总数',
  success_count INT NOT NULL DEFAULT 0 COMMENT '已成功处理行数',
  failure_count INT NOT NULL DEFAULT 0 COMMENT '失败行数',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '作业创建时间',
  start_time DATETIME(3) DEFAULT NULL COMMENT '作业实际开始时间',
  finish_time DATETIME(3) DEFAULT NULL COMMENT '作业完成时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '作业更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_module_exchange_scope (system_id, tenant_id, module_id, job_type, create_time),
  KEY ix_module_exchange_pending (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块数据导入导出及预演作业';

CREATE TABLE IF NOT EXISTS un_module_exchange_row (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '导入导出作业行结果主键',
  job_id BIGINT NOT NULL COMMENT '所属导入导出作业主键',
  row_no INT NOT NULL COMMENT '原始文件或结果文件行序号',
  record_id BIGINT DEFAULT NULL COMMENT '实际新增或更新业务记录主键',
  operation_code VARCHAR(20) DEFAULT NULL COMMENT '该行新增更新跳过或导出操作',
  status VARCHAR(20) NOT NULL COMMENT '预演通过成功失败或已回滚状态',
  error_code VARCHAR(80) DEFAULT NULL COMMENT '该行明确失败原因编码',
  detail_json JSON DEFAULT NULL COMMENT '错误字段及本次写入前后值快照',
  record_version BIGINT DEFAULT NULL COMMENT '回滚前必须匹配的记录乐观锁版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '行检查或处理时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_exchange_row (job_id, row_no),
  KEY ix_module_exchange_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入导出逐行业务结果与回滚依据';

-- 平台和系统 Flow 共用定义与运行模型；审批业务的专属定义及实例仍归 un-examine。
CREATE TABLE IF NOT EXISTS un_module_flow (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块可用流程关系主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '发布流程关系的租户主键',
  module_id BIGINT NOT NULL COMMENT '关联业务模块主键',
  flow_id BIGINT NOT NULL COMMENT '可用于该模块的流程定义主键',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '模块流程关系发布版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_module_flow (tenant_id, module_id, flow_id),
  KEY ix_module_flow_flow (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块可用流程配置';

CREATE TABLE IF NOT EXISTS un_flow (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程定义主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统流程作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统流程所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统流程定义租户主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内稳定流程编码',
  name VARCHAR(120) NOT NULL COMMENT '流程名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '流程用途说明',
  settings_json JSON DEFAULT NULL COMMENT '草稿超时撤回拒绝重审等高级配置',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿已发布停用或归档状态',
  latest_version INT NOT NULL DEFAULT 0 COMMENT '最近发布的不可变流程版本号',
  create_account_id BIGINT NOT NULL COMMENT '创建流程的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_scope (scope_type, system_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台或系统流程定义';

CREATE TABLE IF NOT EXISTS un_flow_node (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程草稿节点主键',
  flow_id BIGINT NOT NULL COMMENT '所属流程定义主键',
  node_code VARCHAR(80) NOT NULL COMMENT '流程内稳定节点编码',
  node_type VARCHAR(40) NOT NULL COMMENT '审批条件网关自动等待外部等节点类型',
  name VARCHAR(120) NOT NULL COMMENT '节点展示名称',
  position_json JSON DEFAULT NULL COMMENT '设计器节点位置和尺寸',
  config_json JSON NOT NULL COMMENT '草稿审批人员字段权限超时和执行配置',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '设计器稳定节点顺序',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_flow_node_code (flow_id, node_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程画布草稿节点';

CREATE TABLE IF NOT EXISTS un_flow_edge (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程草稿连线主键',
  flow_id BIGINT NOT NULL COMMENT '所属流程定义主键',
  source_node_id BIGINT NOT NULL COMMENT '连线起点节点主键',
  target_node_id BIGINT NOT NULL COMMENT '连线终点节点主键',
  condition_json JSON DEFAULT NULL COMMENT '结构化条件和分支判断配置',
  priority_no INT NOT NULL DEFAULT 0 COMMENT '同起点分支选择优先级',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_edge_source (flow_id, source_node_id, priority_no),
  KEY ix_flow_edge_target (target_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程画布草稿连线';

CREATE TABLE IF NOT EXISTS un_flow_version (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '不可变流程版本主键',
  flow_id BIGINT NOT NULL COMMENT '所属流程定义主键',
  version_no INT NOT NULL COMMENT '流程发布版本号',
  snapshot_json JSON NOT NULL COMMENT '节点连线条件字段权限和高级设置完整快照',
  snapshot_hash CHAR(64) NOT NULL COMMENT '发布快照内容摘要',
  publish_account_id BIGINT NOT NULL COMMENT '发布流程的平台账号主键',
  publish_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '流程版本发布时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_flow_version (flow_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变流程发布版本';

CREATE TABLE IF NOT EXISTS un_flow_binding (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程触发绑定主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统绑定作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统流程所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '发布绑定的租户主键',
  module_id BIGINT DEFAULT NULL COMMENT '系统模块触发时的模块主键',
  action_menu_id BIGINT DEFAULT NULL COMMENT '动作触发时的系统菜单主键',
  event_code VARCHAR(80) NOT NULL COMMENT '新建编辑状态变化手动或定时事件编码',
  flow_id BIGINT NOT NULL COMMENT '目标流程定义主键',
  flow_version_id BIGINT NOT NULL COMMENT '绑定时已发布流程版本主键',
  execution_mode VARCHAR(30) NOT NULL COMMENT '先执行后触发先审批后执行或只启动流程',
  priority_no INT NOT NULL DEFAULT 0 COMMENT '同事件绑定优先级',
  exclusive_flag TINYINT(1) NOT NULL DEFAULT 1 COMMENT '命中后是否排斥后续绑定',
  condition_json JSON DEFAULT NULL COMMENT '结构化触发条件',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '绑定自身发布版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_binding_event (system_id, tenant_id, module_id, event_code, status, priority_no),
  KEY ix_flow_binding_action (action_menu_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块动作及事件流程绑定';

CREATE TABLE IF NOT EXISTS un_flow_example (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '运行流程实例主键',
  flow_id BIGINT NOT NULL COMMENT '实际运行流程定义主键',
  flow_version_id BIGINT NOT NULL COMMENT '实际运行不可变版本主键',
  binding_id BIGINT DEFAULT NULL COMMENT '命中流程绑定主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统实例作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统实例所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '实例所属实际业务租户主键',
  source_tenant_id BIGINT DEFAULT NULL COMMENT '继承主租户流程时的配置来源租户主键',
  module_id BIGINT DEFAULT NULL COMMENT '关联业务模块主键',
  record_id BIGINT DEFAULT NULL COMMENT '关联业务记录主键',
  application_id BIGINT DEFAULT NULL COMMENT '外部发起所用受控应用主键',
  initiator_account_id BIGINT DEFAULT NULL COMMENT '内部发起平台账号主键',
  initiator_external_id VARCHAR(160) DEFAULT NULL COMMENT '外部发起人命名空间内标识',
  status VARCHAR(30) NOT NULL DEFAULT 'RUNNING' COMMENT '运行通过拒绝撤回终止或异常状态',
  runtime_snapshot_json JSON NOT NULL COMMENT '启动时流程图字段权限绑定和业务值快照',
  current_nodes_json JSON DEFAULT NULL COMMENT '并行运行中节点编码集合',
  start_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '实例启动时间',
  finish_time DATETIME(3) DEFAULT NULL COMMENT '实例结束时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '实例更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_example_subject (system_id, tenant_id, module_id, record_id),
  KEY ix_flow_example_status (flow_id, status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保存运行快照的流程实例';

CREATE TABLE IF NOT EXISTS un_flow_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程节点待办主键',
  example_id BIGINT NOT NULL COMMENT '所属运行流程实例主键',
  node_code VARCHAR(80) NOT NULL COMMENT '实例快照中的稳定节点编码',
  round_no INT NOT NULL DEFAULT 1 COMMENT '节点退回重入或循环执行轮次',
  task_type VARCHAR(30) NOT NULL COMMENT '审批抄送填写或自动任务类型',
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '待办领取完成取消或超时状态',
  node_snapshot_json JSON NOT NULL COMMENT '本轮节点规则人员字段和业务值快照',
  due_time DATETIME(3) DEFAULT NULL COMMENT '本轮任务到期时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '节点本轮进入时间',
  finish_time DATETIME(3) DEFAULT NULL COMMENT '节点本轮完成时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '任务更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_task_example (example_id, node_code, round_no),
  KEY ix_flow_task_due (status, due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点执行轮次待办';

CREATE TABLE IF NOT EXISTS un_flow_task_candidate (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程待办候选人主键',
  task_id BIGINT NOT NULL COMMENT '所属流程待办主键',
  candidate_type VARCHAR(20) NOT NULL COMMENT '平台账号或应用外部身份类型',
  account_id BIGINT DEFAULT NULL COMMENT '内部候选平台账号主键',
  external_id VARCHAR(160) DEFAULT NULL COMMENT '应用身份命名空间内外部候选人标识',
  application_id BIGINT DEFAULT NULL COMMENT '外部候选身份所属受控应用主键',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '候选待领领取完成或移除状态',
  claim_time DATETIME(3) DEFAULT NULL COMMENT '候选人领取时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '候选关系创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '关系更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_candidate_task (task_id, status),
  KEY ix_flow_candidate_account (account_id, status), KEY ix_flow_candidate_external (application_id, external_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程待办候选处理人';

CREATE TABLE IF NOT EXISTS un_flow_action (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程操作主键',
  example_id BIGINT NOT NULL COMMENT '所属运行流程实例主键',
  task_id BIGINT DEFAULT NULL COMMENT '操作所针对的节点待办主键',
  application_id BIGINT DEFAULT NULL COMMENT '外部操作所用受控应用主键',
  actor_account_id BIGINT DEFAULT NULL COMMENT '内部实际操作平台账号主键',
  actor_external_id VARCHAR(160) DEFAULT NULL COMMENT '外部实际操作人标识',
  action_code VARCHAR(40) NOT NULL COMMENT '同意拒绝转办加签撤回等操作编码',
  request_id VARCHAR(100) NOT NULL COMMENT '调用幂等请求标识',
  result_code VARCHAR(30) NOT NULL COMMENT '操作完成或拒绝结果编码',
  payload_json JSON DEFAULT NULL COMMENT '意见附件字段修改和权限校验快照',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '实际操作时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_flow_action_request (example_id, request_id),
  KEY ix_flow_action_task (task_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程操作及幂等记录';

CREATE TABLE IF NOT EXISTS un_flow_exception (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程异常主键',
  example_id BIGINT NOT NULL COMMENT '异常所属流程实例主键',
  task_id BIGINT DEFAULT NULL COMMENT '异常所属节点待办主键',
  exception_code VARCHAR(80) NOT NULL COMMENT '流程异常类型编码',
  detail_json JSON DEFAULT NULL COMMENT '错误原因及当时执行上下文快照',
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '待处理已重试或人工关闭状态',
  handler_account_id BIGINT DEFAULT NULL COMMENT '实际处置平台账号主键',
  resolution_json JSON DEFAULT NULL COMMENT '人工处置或重试结果摘要',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '异常发现时间',
  resolve_time DATETIME(3) DEFAULT NULL COMMENT '异常处置完成时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_exception_open (status, create_time),
  KEY ix_flow_exception_example (example_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程异常与人工处置';

CREATE TABLE IF NOT EXISTS un_flow_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '不可变流程事件主键',
  example_id BIGINT NOT NULL COMMENT '所属流程实例主键',
  task_id BIGINT DEFAULT NULL COMMENT '关联节点待办主键',
  action_id BIGINT DEFAULT NULL COMMENT '产生事件的流程操作主键',
  event_code VARCHAR(80) NOT NULL COMMENT '节点进入审批路由等事件编码',
  actor_account_id BIGINT DEFAULT NULL COMMENT '内部事件实际操作平台账号主键',
  actor_external_id VARCHAR(160) DEFAULT NULL COMMENT '外部事件实际操作人标识',
  detail_json JSON NOT NULL COMMENT '流程版本路径原因字段及权限审计快照',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_flow_event_example (example_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变流程事件历史';

CREATE TABLE IF NOT EXISTS un_flow_job (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流程定时作业主键',
  example_id BIGINT NOT NULL COMMENT '所属流程实例主键',
  task_id BIGINT DEFAULT NULL COMMENT '相关节点待办主键',
  job_type VARCHAR(40) NOT NULL COMMENT '超时提醒自动处理或等待唤醒类型',
  due_time DATETIME(3) NOT NULL COMMENT '作业计划执行时间',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '待执行执行中成功或失败状态',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '已尝试执行次数',
  idempotency_key VARCHAR(120) NOT NULL COMMENT '定时执行稳定幂等键',
  payload_json JSON DEFAULT NULL COMMENT '任务参数及失败原因',
  finish_time DATETIME(3) DEFAULT NULL COMMENT '作业最终结束时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '作业创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '作业更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_flow_job_idempotency (idempotency_key),
  KEY ix_flow_job_due (status, due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定时提醒与超时作业';

-- 打印是模块动作；同一模板的历史发布版本以独立记录保留供历史单据选用。
CREATE TABLE IF NOT EXISTS un_print (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '打印模板版本主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '模板定义租户主键',
  module_id BIGINT NOT NULL COMMENT '打印业务模块主键',
  template_code VARCHAR(80) NOT NULL COMMENT '模块内稳定打印模板编码',
  name VARCHAR(120) NOT NULL COMMENT '打印模板名称',
  version_no INT NOT NULL COMMENT '同一模板的版本号',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  paper_config_json JSON NOT NULL COMMENT '纸张页眉页脚签章及套打位置配置',
  body_config_json JSON NOT NULL COMMENT '字段关联值和自动分页明细配置',
  publish_time DATETIME(3) DEFAULT NULL COMMENT '本版本发布时间',
  create_account_id BIGINT NOT NULL COMMENT '创建模板的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_print_version (module_id, tenant_id, template_code, version_no),
  KEY ix_print_module (module_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块打印模板及历史发布版本';
