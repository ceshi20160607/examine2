-- examine2 当前空库建表结构；只在独立 unexamine_r16 库执行。
CREATE DATABASE IF NOT EXISTS unexamine_r16 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE unexamine_r16;

-- examine-plat 业务模块
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
  PRIMARY KEY (id), UNIQUE KEY uk_plat_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台角色';

CREATE TABLE IF NOT EXISTS un_plat_member_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台成员角色关系主键',
  member_id BIGINT NOT NULL COMMENT '平台成员主键',
  role_id BIGINT NOT NULL COMMENT '平台角色主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
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
  PRIMARY KEY (id), UNIQUE KEY uk_plat_menu_code (code), KEY ix_plat_menu_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台菜单';

CREATE TABLE IF NOT EXISTS un_plat_role_menu (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台角色菜单授权主键',
  role_id BIGINT NOT NULL COMMENT '平台角色主键',
  menu_id BIGINT NOT NULL COMMENT '平台菜单主键',
  action_code VARCHAR(80) NOT NULL DEFAULT 'VIEW' COMMENT '菜单内操作编码',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
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
  PRIMARY KEY (id), KEY ix_plat_message_recipient (recipient_account_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息';

-- examine-sys 业务模块
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

-- examine-module 业务模块
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
