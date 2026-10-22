-- examine2 当前空库建表结构；只在独立 unexamine_r16 库执行。
CREATE DATABASE IF NOT EXISTS unexamine_r16 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE unexamine_r16;

-- un-plat 业务模块
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

-- un-sys 业务模块
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
  action_code VARCHAR(80) NOT NULL DEFAULT 'VIEW' COMMENT '查看编辑删除等模块动作编码',
  scope_code VARCHAR(40) NOT NULL COMMENT '负责人和组织数据范围编码',
  scope_config JSON DEFAULT NULL COMMENT '指定负责人或部门的范围配置',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_data_action (role_id, module_id, action_code)
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

-- 将首装基线升级为当前结构。保留业务记录，不建立外键或触发器。
-- 辅助过程只处理固定业务表；每次执行前检查列和索引，支持重复执行。
DELIMITER $$
DROP PROCEDURE IF EXISTS un_r16_add_column$$
CREATE PROCEDURE un_r16_add_column(IN table_name_arg VARCHAR(80), IN column_name_arg VARCHAR(80), IN definition_arg TEXT)
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=table_name_arg AND column_name=column_name_arg) THEN
    SET @un_sql=CONCAT('ALTER TABLE ',table_name_arg,' ADD COLUMN ',column_name_arg,' ',definition_arg);
    PREPARE un_stmt FROM @un_sql; EXECUTE un_stmt; DEALLOCATE PREPARE un_stmt;
  END IF;
END$$
DROP PROCEDURE IF EXISTS un_r16_index$$
CREATE PROCEDURE un_r16_index(IN table_name_arg VARCHAR(80), IN index_name_arg VARCHAR(80), IN columns_arg TEXT)
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=table_name_arg AND index_name=index_name_arg) THEN
    SET @un_sql=CONCAT('ALTER TABLE ',table_name_arg,' ADD UNIQUE KEY ',index_name_arg,' (',columns_arg,')');
    PREPARE un_stmt FROM @un_sql; EXECUTE un_stmt; DEALLOCATE PREPARE un_stmt;
  END IF;
END$$
DELIMITER ;
CALL un_r16_add_column('un_sys_system_config','permission_revision','BIGINT NOT NULL DEFAULT 0 COMMENT ''权限配置提交版本''');
CALL un_r16_add_column('un_module_dict','dict_type','VARCHAR(30) NOT NULL DEFAULT ''LIST'' COMMENT ''字典类型：列表树形级联状态标签或字段选项''');
CALL un_r16_add_column('un_module_dict','published_options_json','JSON DEFAULT NULL COMMENT ''已发布的字典名称类型与选项快照''');
CALL un_r16_add_column('un_module_record_data','value_display_json','JSON DEFAULT NULL COMMENT ''既有显示快照保留，业务展示按字典查询''');
CALL un_r16_add_column('un_sys_menu','menu_type','VARCHAR(20) NOT NULL DEFAULT ''PAGE'' COMMENT ''菜单类型：目录页面或操作''');
CALL un_r16_add_column('un_sys_role_data','menu_id','BIGINT DEFAULT NULL COMMENT ''对应操作菜单主键''');
CALL un_r16_add_column('un_module_menu','display_position','VARCHAR(40) NOT NULL DEFAULT ''DETAIL_HEADER'' COMMENT ''模块内菜单展示位置''');
CALL un_r16_add_column('un_module_action','menu_id','BIGINT DEFAULT NULL COMMENT ''动作对应的唯一菜单主键''');
CALL un_r16_add_column('un_module_action','execution_type','VARCHAR(40) NOT NULL DEFAULT ''UPDATE_FIELDS'' COMMENT ''基础动作或字段更新执行类型''');
CALL un_r16_add_column('un_module_action','published_config_json','JSON DEFAULT NULL COMMENT ''运行态发布的动作执行配置''');
CALL un_r16_add_column('un_module_action','published_enabled_flag','TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''已发布动作是否启用''');
CALL un_r16_add_column('un_module_relation','relation_type','VARCHAR(20) NOT NULL DEFAULT ''RELATION'' COMMENT ''普通关联或模块转化关系''');
CALL un_r16_add_column('un_module_relation','target_module_id','BIGINT DEFAULT NULL COMMENT ''转化目标模块主键，普通关联为空''');
CALL un_r16_add_column('un_module_relation','action_menu_id','BIGINT DEFAULT NULL COMMENT ''产生转化关系的操作菜单主键''');

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
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_field (role_id, module_id, field_id), KEY ix_sys_role_field_module (module_id, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户角色对模块字段的控制';


UPDATE un_module_dict SET published_options_json=JSON_OBJECT('name',name,'type',dict_type,'options',options_json)
WHERE status='PUBLISHED' AND published_options_json IS NULL;
ALTER TABLE un_module_relation MODIFY COLUMN relation_config_id BIGINT DEFAULT NULL COMMENT '普通关联对应配置主键，转化关系为空';
CALL un_r16_index('un_module_relation','uk_module_conversion_target','source_record_id,target_module_id,relation_type');

-- 先建立每个既有模块的十个独立菜单；原角色尚未授权的新动作不会自动授权。
DROP TEMPORARY TABLE IF EXISTS un_r16_actions;
CREATE TEMPORARY TABLE un_r16_actions(code VARCHAR(40), label VARCHAR(100), position VARCHAR(40), kind VARCHAR(20), enabled TINYINT, ordinal_no INT);
INSERT INTO un_r16_actions VALUES
('CREATE','新建','LIST_TOOLBAR','ACTION',1,0),('EDIT','编辑','ROW','ACTION',1,1),
('LIST','列表','MODULE_ENTRY','PAGE',1,2),('DETAIL','详情','ROW','PAGE',1,3),
('IMPORT','导入','LIST_TOOLBAR','ACTION',0,4),('EXPORT','导出','LIST_TOOLBAR','ACTION',0,5),
('DELETE','删除','ROW','ACTION',1,6),('TRANSFER','转移','DETAIL_HEADER','ACTION',1,7),
('PRINT','打印','DETAIL_HEADER','ACTION',0,8),('CONVERT','转化','DETAIL_HEADER','ACTION',1,9);
INSERT INTO un_sys_menu(system_id,tenant_id,code,name,menu_type,status)
SELECT system_id,tenant_id,CONCAT('MODULE_',id),name,'DIRECTORY','ACTIVE' FROM un_module
WHERE 1=1
ON DUPLICATE KEY UPDATE code=un_sys_menu.code;
UPDATE un_sys_menu p JOIN un_module m ON p.system_id=m.system_id AND p.tenant_id=m.tenant_id AND p.code=CONCAT('MODULE_',m.id) SET p.menu_type='DIRECTORY';
INSERT INTO un_sys_menu(system_id,tenant_id,parent_id,code,name,menu_type,sort_no,status)
SELECT m.system_id,m.tenant_id,p.id,CONCAT('MODULE_',m.id,'_',a.code),a.label,a.kind,a.ordinal_no,'ACTIVE'
FROM un_module m JOIN un_sys_menu p ON p.tenant_id=m.tenant_id AND p.code=CONCAT('MODULE_',m.id) CROSS JOIN un_r16_actions a
WHERE 1=1
ON DUPLICATE KEY UPDATE code=un_sys_menu.code;

DELIMITER $$
DROP PROCEDURE IF EXISTS un_r16_permissions$$
CREATE PROCEDURE un_r16_permissions()
BEGIN
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_sys_role_menu' AND column_name='action_code') THEN
    START TRANSACTION;
    -- 原字段规则只在已有动作中取交集，避免迁为模块级字段权限时扩权。
    -- account_id=0 表示系统结构迁移，不冒用所属人或其他成员身份。
    INSERT INTO un_sys_log_opt(system_id,tenant_id,account_id,action_code,object_type,object_id,detail_json,trace_id)
    SELECT r.system_id,r.tenant_id,0,'ROLE_FIELD_MODEL_MIGRATION','ROLE',r.id,
      JSON_OBJECT('moduleId',d.module_id,'reason','旧动作字段许可按交集迁移，管理员可在角色字段页重新配置',
        'previousPolicies',JSON_ARRAYAGG(JSON_OBJECT('action',d.action_code,'configuration',d.scope_config))),
      CONCAT('role_field_migration_',r.id,'_',d.module_id)
    FROM un_sys_role_data d JOIN un_sys_role r ON r.id=d.role_id
    WHERE JSON_CONTAINS_PATH(d.scope_config,'one','$.fields')=1
      AND NOT EXISTS(SELECT 1 FROM un_sys_log_opt l WHERE l.trace_id=CONCAT('role_field_migration_',r.id,'_',d.module_id))
    GROUP BY r.id,r.system_id,r.tenant_id,d.module_id;
    INSERT IGNORE INTO un_sys_role_field(role_id,module_id,field_id,readable_flag,writable_flag,exportable_flag)
    SELECT d.role_id,d.module_id,f.id,
      MIN(COALESCE(JSON_TYPE(JSON_EXTRACT(d.scope_config,'$.fields.readable'))='NULL'
        OR JSON_EXTRACT(d.scope_config,'$.fields.readable') IS NULL
        OR JSON_CONTAINS(JSON_EXTRACT(d.scope_config,'$.fields.readable'),JSON_QUOTE(f.field_code)),0)),
      MIN(COALESCE((JSON_TYPE(JSON_EXTRACT(d.scope_config,'$.fields.readable'))='NULL'
        OR JSON_EXTRACT(d.scope_config,'$.fields.readable') IS NULL
        OR JSON_CONTAINS(JSON_EXTRACT(d.scope_config,'$.fields.readable'),JSON_QUOTE(f.field_code)))
        AND (JSON_TYPE(JSON_EXTRACT(d.scope_config,'$.fields.writable'))='NULL'
        OR JSON_EXTRACT(d.scope_config,'$.fields.writable') IS NULL
        OR JSON_CONTAINS(JSON_EXTRACT(d.scope_config,'$.fields.writable'),JSON_QUOTE(f.field_code))),0)),0
    FROM un_sys_role_data d JOIN un_module_field f ON f.module_id=d.module_id AND f.status='PUBLISHED'
    GROUP BY d.role_id,d.module_id,f.id;
    -- 旧 VIEW 同时承担列表和详情，拆成两个显式菜单授权，沿用原数据范围。
    INSERT IGNORE INTO un_sys_role_menu(role_id,menu_id,action_code)
    SELECT g.role_id,n.id,'MIGRATED'
    FROM un_sys_role_menu g JOIN un_sys_menu p ON p.id=g.menu_id
    JOIN un_module m ON p.code=CONCAT('MODULE_',m.id) AND p.system_id=m.system_id
    JOIN un_r16_actions a ON a.code=g.action_code OR (g.action_code='VIEW' AND a.code IN ('LIST','DETAIL'))
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.code);
    INSERT IGNORE INTO un_sys_role_data(role_id,module_id,action_code,scope_code,scope_config,menu_id)
    SELECT d.role_id,d.module_id,'DETAIL',d.scope_code,d.scope_config,n.id
    FROM un_sys_role_data d JOIN un_module m ON m.id=d.module_id
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_DETAIL')
    WHERE d.action_code='VIEW';
    UPDATE un_sys_role_data d JOIN un_module m ON m.id=d.module_id
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',IF(d.action_code='VIEW','LIST',d.action_code))
    SET d.menu_id=n.id,d.scope_config=JSON_REMOVE(d.scope_config,'$.fields');
    IF EXISTS(SELECT 1 FROM un_sys_role_data WHERE menu_id IS NULL) THEN
      ROLLBACK; SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='存在未解析的旧动作，迁移已回滚';
    END IF;
    -- 无执行语义的旧模块父菜单不授予子动作；保留其原导航授权，去重为角色菜单关系。
    DELETE a FROM un_sys_role_menu a JOIN un_sys_role_menu b ON a.role_id=b.role_id AND a.menu_id=b.menu_id AND a.id>b.id;
    UPDATE un_sys_system_config SET permission_revision=permission_revision+1;
    COMMIT;
    ALTER TABLE un_sys_role_menu DROP INDEX uk_sys_role_menu_action, DROP COLUMN action_code;
    ALTER TABLE un_sys_role_data DROP INDEX uk_sys_role_data_action, DROP COLUMN action_code, DROP COLUMN module_id;
  END IF;
END$$
CALL un_r16_permissions()$$
DROP PROCEDURE un_r16_permissions$$

DROP PROCEDURE IF EXISTS un_r16_module_menus$$
CREATE PROCEDURE un_r16_module_menus()
BEGIN
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_module_menu' AND column_name='label') THEN
    -- 旧模块菜单已有关联时将展示资料归并到统一菜单，未关联的不删除，直接阻断要求修正。
    IF EXISTS(SELECT 1 FROM un_module_menu WHERE menu_id IS NULL) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='旧模块菜单尚未关联统一菜单，禁止丢弃原配置';
    END IF;
    UPDATE un_sys_menu m JOIN un_module_menu b ON b.menu_id=m.id SET m.name=b.label,m.route=COALESCE(b.route,m.route);
    ALTER TABLE un_module_menu DROP COLUMN label, DROP COLUMN route;
  END IF;
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='un_module_action' AND column_name='action_code') THEN
    UPDATE un_module_action a JOIN un_module m ON m.id=a.module_id
    JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.action_code)
    SET a.menu_id=n.id,a.execution_type=a.action_code;
    IF EXISTS(SELECT 1 FROM un_module_action WHERE menu_id IS NULL) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='旧模块动作尚未匹配菜单，禁止丢弃原配置';
    END IF;
    ALTER TABLE un_module_action DROP INDEX uk_module_action_code, DROP COLUMN action_code;
  END IF;
END$$
CALL un_r16_module_menus()$$
DROP PROCEDURE un_r16_module_menus$$
DELIMITER ;
ALTER TABLE un_sys_role_data MODIFY COLUMN menu_id BIGINT NOT NULL COMMENT '对应操作菜单主键', MODIFY COLUMN scope_config JSON DEFAULT NULL COMMENT '指定负责人和部门范围';
ALTER TABLE un_module_menu MODIFY COLUMN menu_id BIGINT NOT NULL COMMENT '统一系统菜单主键';
ALTER TABLE un_module_action MODIFY COLUMN menu_id BIGINT NOT NULL COMMENT '动作对应的唯一菜单主键';
ALTER TABLE un_module_action MODIFY COLUMN execution_type VARCHAR(40) NOT NULL COMMENT '基础动作或字段更新执行类型';
ALTER TABLE un_module_record_data MODIFY COLUMN value_display_json JSON DEFAULT NULL COMMENT '既有显示快照保留，业务展示按字典查询';
ALTER TABLE un_module_menu COMMENT='模块与统一菜单的关联和展示配置';
ALTER TABLE un_module_action COMMENT='菜单操作的模块动作执行配置';
CALL un_r16_index('un_sys_role_menu','uk_sys_role_menu','role_id,menu_id');
CALL un_r16_index('un_sys_role_data','uk_sys_role_data_menu','role_id,menu_id');
CALL un_r16_index('un_module_menu','uk_module_menu_binding','module_id,menu_id');
CALL un_r16_index('un_module_action','uk_module_action_menu','menu_id');

INSERT INTO un_module_menu(system_id,tenant_id,module_id,menu_id,display_position,sort_no,status)
SELECT m.system_id,m.tenant_id,m.id,n.id,a.position,a.ordinal_no,IF(m.status='PUBLISHED','PUBLISHED','DRAFT')
FROM un_module m CROSS JOIN un_r16_actions a
JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.code)
WHERE 1=1
ON DUPLICATE KEY UPDATE menu_id=un_module_menu.menu_id;
INSERT INTO un_module_action(module_id,tenant_id,menu_id,execution_type,enabled_flag,config_json,published_config_json,published_enabled_flag,status,published_version)
SELECT m.id,m.tenant_id,n.id,a.code,a.enabled,
 JSON_OBJECT('name',a.label,'type',a.code,'position',a.position,'fieldIds',JSON_ARRAY(),'conditions',JSON_ARRAY(),'mappings',JSON_ARRAY()),
 IF(m.status='PUBLISHED',JSON_OBJECT('name',a.label,'type',a.code,'position',a.position,'fieldIds',JSON_ARRAY(),'conditions',JSON_ARRAY(),'mappings',JSON_ARRAY()),NULL),
 IF(m.status='PUBLISHED',a.enabled,0),IF(m.status='PUBLISHED','PUBLISHED','DRAFT'),IF(m.status='PUBLISHED',1,0)
FROM un_module m CROSS JOIN un_r16_actions a
JOIN un_sys_menu n ON n.tenant_id=m.tenant_id AND n.code=CONCAT('MODULE_',m.id,'_',a.code)
WHERE 1=1
ON DUPLICATE KEY UPDATE menu_id=un_module_action.menu_id;
DROP TEMPORARY TABLE un_r16_actions;
DROP PROCEDURE un_r16_add_column;
DROP PROCEDURE un_r16_index;
