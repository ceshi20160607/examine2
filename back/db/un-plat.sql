-- 平台账号和平台管理数据。关联 ID 由业务 Service 校验，不使用数据库外键。
CREATE TABLE IF NOT EXISTS un_plat_account (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台账号主键',
  username VARCHAR(80) NOT NULL COMMENT '平台登录名',
  password_hash VARCHAR(100) NOT NULL COMMENT '密码安全哈希',
  display_name VARCHAR(100) NOT NULL COMMENT '显示名称',
  email VARCHAR(190) DEFAULT NULL COMMENT '邮箱地址',
  mobile VARCHAR(30) DEFAULT NULL COMMENT '手机号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：正常或停用',
  default_system_id BIGINT DEFAULT NULL COMMENT '个人默认进入的系统主键',
  last_login_time DATETIME(3) DEFAULT NULL COMMENT '最近登录时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_account_username (username),
  UNIQUE KEY uk_plat_account_email (email), UNIQUE KEY uk_plat_account_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号';

CREATE TABLE IF NOT EXISTS un_plat_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置主键',
  config_key VARCHAR(100) NOT NULL COMMENT '平台配置键',
  config_value JSON DEFAULT NULL COMMENT '平台配置内容',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '配置状态',
  update_account_id BIGINT DEFAULT NULL COMMENT '最近修改账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台基础配置';

CREATE TABLE IF NOT EXISTS un_plat_department (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台部门主键',
  parent_id BIGINT DEFAULT NULL COMMENT '上级平台部门主键',
  code VARCHAR(80) NOT NULL COMMENT '平台部门编码',
  name VARCHAR(100) NOT NULL COMMENT '平台部门名称',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '同级排序序号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '部门状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_department_code (code), KEY ix_plat_department_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台组织部门';

CREATE TABLE IF NOT EXISTS un_plat_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台成员主键',
  account_id BIGINT NOT NULL COMMENT '关联平台账号主键',
  department_id BIGINT DEFAULT NULL COMMENT '所属平台部门主键',
  employee_no VARCHAR(80) DEFAULT NULL COMMENT '平台员工编号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '平台成员状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_member_account (account_id),
  UNIQUE KEY uk_plat_member_employee (employee_no), KEY ix_plat_member_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台成员';

CREATE TABLE IF NOT EXISTS un_plat_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台角色主键',
  code VARCHAR(80) NOT NULL COMMENT '平台角色编码',
  name VARCHAR(100) NOT NULL COMMENT '平台角色名称',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色状态',
  description VARCHAR(500) DEFAULT NULL COMMENT '角色业务说明',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色';

CREATE TABLE IF NOT EXISTS un_plat_member_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台成员角色关系主键',
  member_id BIGINT NOT NULL COMMENT '平台成员主键',
  role_id BIGINT NOT NULL COMMENT '平台角色主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_member_role (member_id, role_id), KEY ix_plat_member_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台成员角色关系';

CREATE TABLE IF NOT EXISTS un_plat_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台菜单主键',
  parent_id BIGINT DEFAULT NULL COMMENT '上级菜单主键',
  code VARCHAR(100) NOT NULL COMMENT '菜单权限编码',
  name VARCHAR(100) NOT NULL COMMENT '菜单名称',
  route VARCHAR(255) DEFAULT NULL COMMENT '前端路由',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '菜单排序序号',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '菜单状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_menu_code (code), KEY ix_plat_menu_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台菜单';

CREATE TABLE IF NOT EXISTS un_plat_role_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台角色菜单授权主键',
  role_id BIGINT NOT NULL COMMENT '平台角色主键',
  menu_id BIGINT NOT NULL COMMENT '平台菜单主键',
  action_code VARCHAR(80) NOT NULL DEFAULT 'VIEW' COMMENT '菜单内操作编码',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_role_menu_action (role_id, menu_id, action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色菜单授权';

CREATE TABLE IF NOT EXISTS un_plat_role_data (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台角色数据权限主键',
  role_id BIGINT NOT NULL COMMENT '平台角色主键',
  resource_code VARCHAR(100) NOT NULL COMMENT '数据资源编码',
  scope_code VARCHAR(40) NOT NULL COMMENT '数据范围编码',
  scope_config JSON DEFAULT NULL COMMENT '数据范围补充配置',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_role_data_resource (role_id, resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色数据权限';

CREATE TABLE IF NOT EXISTS un_plat_log_login (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台登录日志主键',
  account_id BIGINT DEFAULT NULL COMMENT '匹配的平台账号主键',
  username VARCHAR(80) NOT NULL COMMENT '尝试登录名',
  result_code VARCHAR(40) NOT NULL COMMENT '登录结果编码',
  failure_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  ip_address VARCHAR(45) DEFAULT NULL COMMENT '请求地址',
  user_agent VARCHAR(500) DEFAULT NULL COMMENT '客户端标识',
  trace_id VARCHAR(80) DEFAULT NULL COMMENT '请求追踪标识',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录尝试时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_plat_login_account_time (account_id, create_time), KEY ix_plat_login_username_time (username, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台登录日志';

CREATE TABLE IF NOT EXISTS un_plat_log_opt (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台操作日志主键',
  account_id BIGINT NOT NULL COMMENT '操作平台账号主键',
  action_code VARCHAR(100) NOT NULL COMMENT '操作类型编码',
  object_type VARCHAR(80) NOT NULL COMMENT '操作对象类型',
  object_id BIGINT DEFAULT NULL COMMENT '操作对象主键',
  detail_json JSON DEFAULT NULL COMMENT '操作摘要和影响范围',
  trace_id VARCHAR(80) DEFAULT NULL COMMENT '请求追踪标识',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_plat_opt_actor_time (account_id, create_time), KEY ix_plat_opt_object (object_type, object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台操作日志';

CREATE TABLE IF NOT EXISTS un_plat_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台消息主键',
  recipient_account_id BIGINT NOT NULL COMMENT '接收平台账号主键',
  title VARCHAR(200) NOT NULL COMMENT '消息标题',
  content TEXT NOT NULL COMMENT '消息正文',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '关联业务类型',
  target_id BIGINT DEFAULT NULL COMMENT '关联业务主键',
  status VARCHAR(20) NOT NULL DEFAULT 'UNREAD' COMMENT '消息阅读归档状态',
  read_time DATETIME(3) DEFAULT NULL COMMENT '阅读时间',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), KEY ix_plat_message_recipient (recipient_account_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息';
