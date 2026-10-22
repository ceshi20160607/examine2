-- examine2 当前空库建表结构；只在独立 unexamine_r16 库执行。
CREATE DATABASE IF NOT EXISTS unexamine_r16 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE unexamine_r16;

-- un-core 业务模块
-- 公共文件、公告和 AI 配置；所属作用域由业务 Service 校验，不设置数据库外键。
CREATE TABLE IF NOT EXISTS un_common_file (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件元数据主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统作用域的业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统作用域的租户主键',
  uploader_account_id BIGINT NOT NULL COMMENT '实际上传平台账号主键',
  original_name VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
  content_type VARCHAR(120) NOT NULL COMMENT '文件内容类型',
  size_bytes BIGINT NOT NULL COMMENT '文件字节数',
  sha256 CHAR(64) DEFAULT NULL COMMENT '文件内容摘要',
  storage_type VARCHAR(30) NOT NULL COMMENT '本地或对象存储类型',
  storage_key VARCHAR(500) NOT NULL COMMENT '存储对象键，不向普通接口直接暴露',
  preview_key VARCHAR(500) DEFAULT NULL COMMENT '预览或缩略图对象键',
  upload_json JSON DEFAULT NULL COMMENT '分片上传会话与合并校验信息',
  status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT '上传中可用或待清理状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件登记时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近状态更新时间',
  PRIMARY KEY (id), KEY ix_common_file_scope (scope_type, system_id, tenant_id, status),
  KEY ix_common_file_hash (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共文件元数据';

CREATE TABLE IF NOT EXISTS un_common_file_ref (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件业务引用主键',
  file_id BIGINT NOT NULL COMMENT '被引用公共文件元数据主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统引用作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统业务引用所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统业务引用所属租户主键',
  object_type VARCHAR(50) NOT NULL COMMENT '模块记录任务工作日志等对象类型',
  object_id BIGINT NOT NULL COMMENT '业务对象主键',
  field_id BIGINT DEFAULT NULL COMMENT '模块字段引用时的字段主键',
  create_account_id BIGINT NOT NULL COMMENT '建立引用的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '建立引用时间',
  PRIMARY KEY (id), KEY ix_common_file_ref_file (file_id),
  KEY ix_common_file_ref_object (scope_type, system_id, tenant_id, object_type, object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件与多个业务对象的引用关系';

CREATE TABLE IF NOT EXISTS un_common_notice (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统公告作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统公告所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户专属公告的租户主键',
  title VARCHAR(200) NOT NULL COMMENT '公告标题',
  content TEXT NOT NULL COMMENT '公告正文',
  audience_json JSON DEFAULT NULL COMMENT '角色部门或成员可见范围',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  publish_time DATETIME(3) DEFAULT NULL COMMENT '正式发布时间',
  expire_time DATETIME(3) DEFAULT NULL COMMENT '公告失效时间',
  create_account_id BIGINT NOT NULL COMMENT '创建公告的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_common_notice_scope (scope_type, system_id, tenant_id, status, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共公告';

CREATE TABLE IF NOT EXISTS un_common_ai (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '智能能力配置主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统智能配置所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户专属智能配置主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内智能能力编码',
  name VARCHAR(100) NOT NULL COMMENT '智能能力名称',
  provider_code VARCHAR(80) NOT NULL COMMENT '模型提供方编码',
  secret_ref VARCHAR(255) DEFAULT NULL COMMENT '模型凭据安全引用',
  config_json JSON DEFAULT NULL COMMENT '模型参数能力范围与人工确认策略',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布配置版本',
  published_config_json JSON DEFAULT NULL COMMENT '运行态智能能力配置快照',
  create_account_id BIGINT NOT NULL COMMENT '创建配置的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_common_ai_scope (scope_type, system_id, tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共智能能力配置';

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
  permission_revision BIGINT NOT NULL DEFAULT 0 COMMENT '权限配置版本，变更后隔离旧缓存',
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
  menu_type VARCHAR(20) NOT NULL DEFAULT 'PAGE' COMMENT '菜单类型：目录页面或操作',
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
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统租户角色菜单授权';

CREATE TABLE IF NOT EXISTS un_sys_role_data (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统角色数据权限主键',
  role_id BIGINT NOT NULL COMMENT '租户角色主键',
  menu_id BIGINT NOT NULL COMMENT '对应操作菜单主键',
  scope_code VARCHAR(40) NOT NULL COMMENT '负责人和组织数据范围编码',
  scope_config JSON DEFAULT NULL COMMENT '指定负责人和部门范围',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_data_menu (role_id, menu_id)
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
  PRIMARY KEY (id), UNIQUE KEY uk_sys_role_field (role_id, module_id, field_id), KEY ix_sys_role_field_module (module_id, field_id)
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
  PRIMARY KEY (id), UNIQUE KEY uk_module_dict_value (system_id, tenant_id, dict_code, dict_value),
  KEY ix_module_dict_scope (system_id, tenant_id, dict_code, parent_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='逐条保存值与名称的字典基础项';

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
  config_json JSON NOT NULL COMMENT '字段类型校验默认值和字典项引用配置',
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
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块菜单关系主键',
  system_id BIGINT NOT NULL COMMENT '所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '定义菜单的租户主键',
  module_id BIGINT NOT NULL COMMENT '所属业务模块主键',
  menu_id BIGINT NOT NULL COMMENT '统一系统菜单主键',
  display_position VARCHAR(40) NOT NULL DEFAULT 'DETAIL_HEADER' COMMENT '模块内菜单展示位置',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '同位置展示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '模块菜单发布状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  published_config_json JSON DEFAULT NULL COMMENT '运行态发布的动作执行配置',
  published_enabled_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '已发布动作是否启用',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '动作发布状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '动作发布版本号',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_action_menu (menu_id), KEY ix_module_action_module (module_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单操作的模块动作执行配置';

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
  field_code VARCHAR(80) NOT NULL COMMENT '保存时的字段编码快照',
  value_json JSON DEFAULT NULL COMMENT '按字段类型保存的业务值',
  index_text VARCHAR(255) DEFAULT NULL COMMENT '允许筛选排序的文本值索引',
  index_decimal DECIMAL(30,8) DEFAULT NULL COMMENT '允许筛选排序的数值索引',
  index_time DATETIME(3) DEFAULT NULL COMMENT '允许筛选排序的时间值索引',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_record_field (record_id, field_id),
  KEY ix_module_record_data_field (field_id, record_id),
  KEY ix_module_record_data_text (field_id, index_text, record_id),
  KEY ix_module_record_data_decimal (field_id, index_decimal, record_id),
  KEY ix_module_record_data_time (field_id, index_time, record_id)
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
  relation_config_id BIGINT DEFAULT NULL COMMENT '普通关联对应配置主键，转化关系为空',
  relation_type VARCHAR(20) NOT NULL DEFAULT 'RELATION' COMMENT '普通关联或模块转化关系',
  target_module_id BIGINT DEFAULT NULL COMMENT '转化目标模块主键，普通关联为空',
  action_menu_id BIGINT DEFAULT NULL COMMENT '产生转化关系的操作菜单主键',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '子表明细或关联展示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '关联状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_module_relation (source_record_id, target_record_id, relation_config_id),
  UNIQUE KEY uk_module_conversion_target (source_record_id, target_module_id, relation_type), KEY ix_module_relation_target (target_record_id)
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
  PRIMARY KEY (id), UNIQUE KEY uk_print_version (module_id, tenant_id, template_code, version_no),
  KEY ix_print_module (module_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块打印模板及历史发布版本';

-- un-examine 业务模块
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
  PRIMARY KEY (id), UNIQUE KEY uk_examine_config_rule (examine_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批撤回超时等高级配置';

CREATE TABLE IF NOT EXISTS un_examine_config_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批规则适用对象主键',
  config_id BIGINT NOT NULL COMMENT '所属审批高级配置主键',
  subject_type VARCHAR(20) NOT NULL COMMENT '适用人员部门或角色类型',
  subject_id BIGINT NOT NULL COMMENT '适用对象主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '配置时间',
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
  PRIMARY KEY (id), KEY ix_examine_exception_open (status, create_time),
  KEY ix_examine_exception_example (example_id, example_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批运行异常与处置';

-- un-work 业务模块
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
  PRIMARY KEY (id), KEY ix_work_project_scope (scope_type, system_id, tenant_id, status),
  KEY ix_work_project_owner (owner_account_id, due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作项目';

CREATE TABLE IF NOT EXISTS un_work_project_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目参与人关系主键',
  project_id BIGINT NOT NULL COMMENT '所属工作项目主键',
  account_id BIGINT NOT NULL COMMENT '参与平台账号主键',
  member_type VARCHAR(30) NOT NULL COMMENT '负责人协作或观察成员身份',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '成员关系状态',
  join_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入项目时间',
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
  field_code VARCHAR(80) NOT NULL COMMENT '任务类型内稳定字段编码',
  field_name VARCHAR(100) NOT NULL COMMENT '任务字段展示名称',
  field_type VARCHAR(40) NOT NULL COMMENT '复用公共固定字段类型',
  config_json JSON NOT NULL COMMENT '草稿校验字典卡片和看板配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布字段运行配置快照',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布任务字段版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_work_task_field_scope (scope_type, system_id, tenant_id, task_kind, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务字段与看板取数配置';

CREATE TABLE IF NOT EXISTS un_work_task_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务参与人关系主键',
  task_id BIGINT NOT NULL COMMENT '所属工作任务主键',
  account_id BIGINT NOT NULL COMMENT '参与平台账号主键',
  member_type VARCHAR(20) NOT NULL COMMENT '协作关注或验收成员身份',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '参与关系状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入任务时间',
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
  log_date DATE NOT NULL COMMENT '用户选择的工作日期',
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
  PRIMARY KEY (id), KEY ix_work_daily_owner (scope_type, system_id, tenant_id, account_id, log_date),
  KEY ix_work_daily_root (root_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户手动创建的每日工作日志及版本';

CREATE TABLE IF NOT EXISTS un_work_daily_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工作日志字段配置主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统字段配置作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统字段所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统字段所属租户主键',
  field_code VARCHAR(80) NOT NULL COMMENT '作用域内稳定字段编码',
  field_name VARCHAR(100) NOT NULL COMMENT '工作日志字段展示名称',
  field_type VARCHAR(40) NOT NULL COMMENT '复用公共固定字段类型',
  config_json JSON NOT NULL COMMENT '草稿校验权限布局和字典配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布字段运行配置快照',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '字段显示顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布工作日志字段版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  PRIMARY KEY (id), KEY ix_work_daily_comment_root (daily_root_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日志评论与管理反馈';

-- un-dashboard 业务模块
-- 仪表盘按平台、系统及租户归属查询；数据源只引用已发布模块与模块关系。
CREATE TABLE IF NOT EXISTS un_dashboard (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表盘主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台系统或模块仪表盘作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统仪表盘所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统仪表盘所属租户主键',
  module_id BIGINT DEFAULT NULL COMMENT '模块首页关联模块主键',
  owner_account_id BIGINT DEFAULT NULL COMMENT '个人仪表盘所属平台账号主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内仪表盘编码',
  name VARCHAR(120) NOT NULL COMMENT '仪表盘名称',
  layout_json JSON NOT NULL COMMENT '草稿组件位置尺寸和页面布局',
  published_layout_json JSON DEFAULT NULL COMMENT '已发布页面布局快照',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布仪表盘版本',
  create_account_id BIGINT NOT NULL COMMENT '创建仪表盘的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_dashboard_scope (scope_type, system_id, tenant_id, owner_account_id, status),
  KEY ix_dashboard_module (module_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义仪表盘';

CREATE TABLE IF NOT EXISTS un_dashboard_component (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表盘组件主键',
  dashboard_id BIGINT NOT NULL COMMENT '所属仪表盘主键',
  component_code VARCHAR(80) NOT NULL COMMENT '页面内稳定组件编码',
  component_type VARCHAR(40) NOT NULL COMMENT '图表指标列表待办或快捷入口类型',
  data_source_id BIGINT DEFAULT NULL COMMENT '已发布自定义数据源主键',
  module_id BIGINT DEFAULT NULL COMMENT '直接查询的已发布模块主键',
  config_json JSON NOT NULL COMMENT '草稿统计筛选样式刷新和跳转配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布组件运行配置快照',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '组件顺序',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '组件草稿发布或停用状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_dashboard_component (dashboard_id, component_code),
  KEY ix_dashboard_component_source (data_source_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘配置组件';

CREATE TABLE IF NOT EXISTS un_dashboard_component_field (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表组件字段主键',
  component_id BIGINT NOT NULL COMMENT '所属仪表组件主键',
  source_field_id BIGINT NOT NULL COMMENT '数据源输出字段或模块字段稳定主键',
  field_role VARCHAR(30) NOT NULL COMMENT '指标分组时间或排序字段用途',
  aggregation VARCHAR(30) DEFAULT NULL COMMENT '计数求和平均最大或最小计算方式',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '组件内字段顺序',
  config_json JSON DEFAULT NULL COMMENT '展示格式及字段级补充配置',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_component_field (component_id, source_field_id, field_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表组件字段配置';

CREATE TABLE IF NOT EXISTS un_dashboard_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仪表盘可见范围主键',
  dashboard_id BIGINT NOT NULL COMMENT '所属仪表盘主键',
  subject_type VARCHAR(20) NOT NULL COMMENT '授权成员角色或部门类型',
  subject_id BIGINT NOT NULL COMMENT '成员角色或部门主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
  PRIMARY KEY (id), UNIQUE KEY uk_dashboard_subject (dashboard_id, subject_type, subject_id),
  KEY ix_dashboard_user_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仪表盘人员与组织访问范围';

CREATE TABLE IF NOT EXISTS un_data_source (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自定义数据源主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统数据源作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统数据源所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '定义数据源的租户主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内稳定数据源编码',
  name VARCHAR(120) NOT NULL COMMENT '数据源名称',
  root_module_id BIGINT DEFAULT NULL COMMENT '模块型数据源起始的已发布模块主键',
  description VARCHAR(500) DEFAULT NULL COMMENT '数据源业务口径说明',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布数据源版本',
  create_account_id BIGINT NOT NULL COMMENT '创建数据源的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_data_source_scope (scope_type, system_id, tenant_id, status),
  KEY ix_data_source_root (root_module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按模块及关联配置的自定义数据源';

CREATE TABLE IF NOT EXISTS un_data_source_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据源查询配置主键',
  data_source_id BIGINT NOT NULL COMMENT '所属数据源主键',
  relation_json JSON DEFAULT NULL COMMENT '已配置模块关联路径与连接方式',
  output_json JSON NOT NULL COMMENT '允许输出的模块字段稳定标识',
  filter_json JSON DEFAULT NULL COMMENT '结构化固定筛选条件',
  sort_json JSON DEFAULT NULL COMMENT '默认排序和时间字段配置',
  published_json JSON DEFAULT NULL COMMENT '已发布查询配置整体快照',
  published_version INT NOT NULL DEFAULT 0 COMMENT '查询配置发布版本',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_data_source_config (data_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义数据源的关联输出筛选配置';

CREATE TABLE IF NOT EXISTS un_kpi (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关键指标主键',
  system_id BIGINT NOT NULL COMMENT '指标所属业务系统主键',
  tenant_id BIGINT NOT NULL COMMENT '指标所属租户主键',
  code VARCHAR(80) NOT NULL COMMENT '租户内指标编码',
  name VARCHAR(120) NOT NULL COMMENT '指标名称',
  data_source_id BIGINT DEFAULT NULL COMMENT '计算指标的已发布数据源主键',
  module_id BIGINT DEFAULT NULL COMMENT '直接计算指标的业务模块主键',
  measure_field_id BIGINT DEFAULT NULL COMMENT '参与指标计算的模块字段主键',
  aggregation VARCHAR(30) NOT NULL COMMENT '指标数量求和比率等统计口径',
  period_type VARCHAR(20) NOT NULL COMMENT '按月季度或年份考核',
  config_json JSON DEFAULT NULL COMMENT '草稿筛选预警和计算规则',
  published_config_json JSON DEFAULT NULL COMMENT '已发布指标计算规则快照',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布指标版本',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_kpi_code (tenant_id, code),
  KEY ix_kpi_source (data_source_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键绩效指标配置';

CREATE TABLE IF NOT EXISTS un_kpi_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '指标目标与计算结果主键',
  kpi_id BIGINT NOT NULL COMMENT '所属关键指标主键',
  target_type VARCHAR(20) NOT NULL COMMENT '人员部门或角色目标类型',
  target_id BIGINT NOT NULL COMMENT '指标目标对象主键',
  period_key VARCHAR(20) NOT NULL COMMENT '月份季度或年度周期编码',
  target_value DECIMAL(20,4) NOT NULL COMMENT '该周期设定目标值',
  actual_value DECIMAL(20,4) DEFAULT NULL COMMENT '最近一次已计算实际值',
  calculated_time DATETIME(3) DEFAULT NULL COMMENT '实际值最近计算时间',
  calculation_json JSON DEFAULT NULL COMMENT '计算口径来源和结果解释快照',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '目标启用或停用状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_kpi_target_period (kpi_id, target_type, target_id, period_key),
  KEY ix_kpi_user_target (target_type, target_id, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标人员组织目标与计算结果';

CREATE TABLE IF NOT EXISTS un_kpi_result (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '指标计算历史主键',
  kpi_user_id BIGINT NOT NULL COMMENT '所属人员或组织周期目标主键',
  kpi_version INT NOT NULL COMMENT '计算所用的已发布指标版本',
  actual_value DECIMAL(20,4) NOT NULL COMMENT '本次计算的实际指标值',
  source_json JSON NOT NULL COMMENT '来源模块数据源筛选口径与权限摘要',
  calculation_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '指标实际计算时间',
  PRIMARY KEY (id), KEY ix_kpi_result_target (kpi_user_id, calculation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可解释的指标计算历史';

-- un-app 业务模块
-- 应用是平台或系统能力的受控调用方；授权范围随发布快照生效。
CREATE TABLE IF NOT EXISTS un_app (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '受控应用主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台应用或系统应用',
  system_id BIGINT DEFAULT NULL COMMENT '系统应用所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统应用所属租户主键',
  app_id VARCHAR(100) NOT NULL COMMENT '对外稳定应用标识',
  name VARCHAR(120) NOT NULL COMMENT '应用名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '应用业务说明',
  icon VARCHAR(255) DEFAULT NULL COMMENT '应用展示图标',
  access_mode VARCHAR(30) NOT NULL COMMENT '内部外部或同时允许的访问模式',
  secret_ref VARCHAR(255) NOT NULL COMMENT '当前密钥安全引用',
  secret_version INT NOT NULL DEFAULT 1 COMMENT '当前密钥版本',
  previous_secret_ref VARCHAR(255) DEFAULT NULL COMMENT '轮换过渡期旧密钥安全引用',
  previous_secret_expire_time DATETIME(3) DEFAULT NULL COMMENT '轮换过渡期旧密钥失效时间',
  callback_url VARCHAR(500) DEFAULT NULL COMMENT '应用回调地址',
  config_json JSON NOT NULL COMMENT '草稿资源动作字段数据范围及访问控制配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布的应用授权和访问控制快照',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布应用配置版本',
  create_account_id BIGINT NOT NULL COMMENT '创建应用的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_app_identifier (app_id),
  KEY ix_app_scope (scope_type, system_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台或系统受控访问应用';

CREATE TABLE IF NOT EXISTS un_app_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用事件配置主键',
  application_id BIGINT NOT NULL COMMENT '所属受控应用主键',
  event_code VARCHAR(100) NOT NULL COMMENT '稳定业务事件编码',
  resource_type VARCHAR(40) NOT NULL COMMENT '事件所属模块或流程资源类型',
  resource_id BIGINT DEFAULT NULL COMMENT '被订阅的具体资源主键',
  callback_url VARCHAR(500) DEFAULT NULL COMMENT '该事件覆盖应用默认回调的地址',
  config_json JSON DEFAULT NULL COMMENT '签名重试过滤和推送配置',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_config_json JSON DEFAULT NULL COMMENT '已发布事件执行配置快照',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_app_event (application_id, event_code, resource_type, resource_id),
  KEY ix_app_event_resource (resource_type, resource_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控应用事件配置';

-- 调用请求标识在应用内唯一，可同时用作重试幂等记录与可查询审计。
CREATE TABLE IF NOT EXISTS un_app_call (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用调用审计主键',
  application_id BIGINT NOT NULL COMMENT '实际调用的受控应用主键',
  credential_version INT NOT NULL COMMENT '通过验证的应用密钥版本',
  system_id BIGINT DEFAULT NULL COMMENT '目标业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '目标业务租户主键',
  resource_type VARCHAR(40) NOT NULL COMMENT '调用模块流程或文件资源类型',
  resource_id BIGINT DEFAULT NULL COMMENT '调用资源主键',
  action_code VARCHAR(80) NOT NULL COMMENT '本次执行的独立授权动作',
  request_id VARCHAR(100) NOT NULL COMMENT '调用方提交的稳定幂等请求标识',
  request_hash CHAR(64) NOT NULL COMMENT '请求内容摘要，用于拒绝同键异参',
  result_code VARCHAR(50) NOT NULL COMMENT '成功拒绝或失败结果编码',
  response_json JSON DEFAULT NULL COMMENT '幂等重试需要的非敏感结果摘要',
  permission_json JSON DEFAULT NULL COMMENT '实际生效资源字段及数据范围快照',
  duration_ms INT DEFAULT NULL COMMENT '接口处理耗时毫秒',
  caller_ip VARCHAR(45) DEFAULT NULL COMMENT '调用方网络地址',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用发生时间',
  PRIMARY KEY (id), UNIQUE KEY uk_app_call_request (application_id, request_id),
  KEY ix_app_call_scope (system_id, tenant_id, create_time),
  KEY ix_app_call_result (application_id, result_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用调用幂等与审计记录';
