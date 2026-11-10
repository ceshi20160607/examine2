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
  PRIMARY KEY (id),
  -- 逻辑删除后保留历史关系，只约束当前有效关系唯一
  UNIQUE KEY uk_plat_member_role (member_id, role_id, (IF(deleted_time IS NULL, 1, NULL))),
  KEY ix_plat_member_role_role (role_id)
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
  PRIMARY KEY (id),
  -- 逻辑删除后保留历史授权，只约束当前有效菜单动作唯一
  UNIQUE KEY uk_plat_role_menu_action (role_id, menu_id, action_code, (IF(deleted_time IS NULL, 1, NULL)))
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
  PRIMARY KEY (id),
  -- 逻辑删除后保留历史范围，只约束当前有效资源配置唯一
  UNIQUE KEY uk_plat_role_data_resource (role_id, resource_code, (IF(deleted_time IS NULL, 1, NULL)))
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

CREATE TABLE IF NOT EXISTS un_plat_message_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台消息模板主键',
  template_code VARCHAR(80) NOT NULL COMMENT '平台唯一模板编码',
  title_template VARCHAR(200) NOT NULL COMMENT '消息标题模板',
  content_template TEXT NOT NULL COMMENT '消息正文模板',
  variable_keys_json JSON NOT NULL COMMENT '允许填充的变量名数组',
  channel VARCHAR(30) NOT NULL COMMENT '发送渠道，目前为站内消息 IN_APP',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '平台业务跳转类型，不允许直接跳系统详情',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿、已发布或停用状态',
  create_account_id BIGINT NOT NULL COMMENT '模板创建账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_message_template_code (template_code),
  KEY ix_plat_message_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息模板';

CREATE TABLE IF NOT EXISTS un_plat_message_delivery_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '平台消息投递记录主键',
  template_id BIGINT NOT NULL COMMENT '使用的平台消息模板主键',
  recipient_account_id BIGINT NOT NULL COMMENT '接收平台账号主键',
  dedupe_key VARCHAR(120) DEFAULT NULL COMMENT '同一接收人业务事件的去重键，空表示不去重',
  channel VARCHAR(30) NOT NULL COMMENT '实际投递渠道',
  status VARCHAR(20) NOT NULL COMMENT '投递结果状态',
  message_id BIGINT DEFAULT NULL COMMENT '成功投递的平台消息主键',
  rendered_title VARCHAR(200) NOT NULL COMMENT '发送时渲染的标题快照',
  rendered_content TEXT NOT NULL COMMENT '发送时渲染的正文快照',
  target_type VARCHAR(80) DEFAULT NULL COMMENT '发送时的平台业务跳转类型快照',
  target_id BIGINT DEFAULT NULL COMMENT '发送时的平台业务跳转主键快照',
  attempt_count INT NOT NULL DEFAULT 1 COMMENT '投递尝试次数',
  error_text VARCHAR(500) DEFAULT NULL COMMENT '失败原因摘要',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次投递时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近投递时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_message_dedupe (recipient_account_id, dedupe_key),
  KEY ix_plat_message_delivery_template (template_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台消息投递记录';

-- 运维备份的元数据和实际产物分离；产物以服务端随机键定位，不能向普通业务接口暴露路径。
CREATE TABLE IF NOT EXISTS un_plat_ops_backup (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '运维备份主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台整库或单个业务系统作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统备份所属业务系统主键，平台整库时为空',
  backup_kind VARCHAR(30) NOT NULL COMMENT '平台整库SQL或系统数据SQL备份类型',
  status VARCHAR(20) NOT NULL DEFAULT 'CREATING' COMMENT '创建中可用或失败状态',
  artifact_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '服务端随机备份文件键',
  size_bytes BIGINT DEFAULT NULL COMMENT 'SQL备份文件字节数',
  sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'SQL备份文件SHA256摘要',
  error_text VARCHAR(300) DEFAULT NULL COMMENT '失败原因摘要，不记录数据库连接串',
  create_account_id BIGINT NOT NULL COMMENT '发起备份的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份发起时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '备份状态更新时间',
  deleted_time DATETIME(3) DEFAULT NULL COMMENT '逻辑删除时间，空表示未删除',
  PRIMARY KEY (id), UNIQUE KEY uk_plat_ops_backup_artifact (artifact_key),
  KEY ix_plat_ops_backup_scope (scope_type, system_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维备份元数据';
