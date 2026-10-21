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
  PRIMARY KEY (id), UNIQUE KEY uk_sys_system_code (code), KEY ix_sys_system_owner (owner_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台内业务系统';

CREATE TABLE IF NOT EXISTS un_sys_system_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统基础配置主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  multi_tenant_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '多租户模式开关，关闭时保留非主租户数据',
  login_title VARCHAR(120) DEFAULT NULL COMMENT '系统登录展示标题',
  login_domain VARCHAR(190) DEFAULT NULL COMMENT '系统专属登录域名',
  default_home VARCHAR(255) DEFAULT NULL COMMENT '系统默认首页路由',
  settings_json JSON DEFAULT NULL COMMENT '已配置系统功能开关和参数',
  update_account_id BIGINT DEFAULT NULL COMMENT '最近修改平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  route VARCHAR(255) DEFAULT NULL COMMENT '前端路由',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '菜单排序序号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '菜单状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_code (tenant_id, code), KEY ix_sys_role_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色';

CREATE TABLE IF NOT EXISTS un_sys_member_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户成员角色关系主键',
  tenant_member_id BIGINT NOT NULL COMMENT '租户成员关系主键',
  role_id BIGINT NOT NULL COMMENT '租户角色主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_member_role (tenant_member_id, role_id), KEY ix_sys_member_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户成员角色关系';

CREATE TABLE IF NOT EXISTS un_sys_role_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户角色菜单授权主键',
  role_id BIGINT NOT NULL COMMENT '租户角色主键',
  menu_id BIGINT NOT NULL COMMENT '租户菜单主键',
  action_code VARCHAR(80) NOT NULL DEFAULT 'VIEW' COMMENT '菜单内操作编码',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_menu_action (role_id, menu_id, action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统租户角色菜单授权';

CREATE TABLE IF NOT EXISTS un_sys_role_data (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统角色数据权限主键',
  role_id BIGINT NOT NULL COMMENT '租户角色主键',
  module_id BIGINT NOT NULL COMMENT '业务模块主键',
  scope_code VARCHAR(40) NOT NULL COMMENT '负责人和组织数据范围编码',
  scope_config JSON DEFAULT NULL COMMENT '指定负责人或部门的范围配置',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_data_module (role_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色数据权限';

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
  PRIMARY KEY (id), KEY ix_sys_message_recipient (recipient_member_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息';
