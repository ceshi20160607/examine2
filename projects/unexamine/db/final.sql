-- source: init/V1__platform.sql
-- 用途：首条业务旅程的统一平台账号；系统成员身份另存在系统模块，不凭账号直接进入业务系统。
CREATE TABLE un_plat_account (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '平台账号标识',
  username VARCHAR(64) NOT NULL COMMENT '全平台唯一登录名',
  password_hash VARCHAR(255) NOT NULL COMMENT '带盐的单向口令哈希',
  display_name VARCHAR(100) NOT NULL COMMENT '用户显示名称',
  email VARCHAR(255) NULL COMMENT '电子邮箱',
  mobile VARCHAR(32) NULL COMMENT '手机号码',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1启用，0停用',
  last_login_time DATETIME(3) NULL COMMENT '最近成功登录时间',
  create_time DATETIME(3) NOT NULL COMMENT '账号创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '账号修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_account_username (username),
  KEY idx_plat_account_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一平台账号';

-- source: init/V2__system.sql
-- 用途：系统、主租户、显式成员映射与基础角色授权；跨表归属、状态和权限由 manage 事务校验。
CREATE TABLE un_sys_system (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '业务系统标识',
  system_code VARCHAR(64) NOT NULL COMMENT '全平台唯一系统编码',
  system_name VARCHAR(120) NOT NULL COMMENT '业务系统名称',
  owner_account_id BIGINT UNSIGNED NOT NULL COMMENT '当前系统所属平台账号标识',
  primary_tenant_id BIGINT UNSIGNED NULL COMMENT '系统创建时同步生成的主租户标识',
  multi_tenant_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '多租户开关：0关闭，1开启',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '系统状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '系统创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '系统修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_system_code (system_code),
  KEY idx_sys_system_owner (owner_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台内业务系统';

CREATE TABLE un_sys_tenant (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统租户标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_code VARCHAR(64) NOT NULL COMMENT '系统内唯一租户编码',
  tenant_name VARCHAR(120) NOT NULL COMMENT '租户名称',
  is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主租户：1是，0否',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '租户状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '租户创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '租户修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_tenant_code (system_id, tenant_code),
  KEY idx_sys_tenant_primary (system_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统内租户';

CREATE TABLE un_sys_member (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统成员标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  account_id BIGINT UNSIGNED NOT NULL COMMENT '关联平台账号标识',
  member_name VARCHAR(100) NOT NULL COMMENT '系统内成员显示名称',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '成员状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '加入系统时间',
  update_time DATETIME(3) NOT NULL COMMENT '成员修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_member_account (system_id, account_id),
  KEY idx_sys_member_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号与系统的显式成员映射';

CREATE TABLE un_sys_tenant_member (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户成员关系标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '所属租户标识',
  member_id BIGINT UNSIGNED NOT NULL COMMENT '系统成员标识',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '租户成员状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '加入租户时间',
  update_time DATETIME(3) NOT NULL COMMENT '租户成员修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_tenant_member (tenant_id, member_id),
  KEY idx_sys_tenant_member_scope (system_id, member_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统成员与租户的显式关系';

CREATE TABLE un_sys_role (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户角色标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '所属租户标识',
  role_code VARCHAR(64) NOT NULL COMMENT '租户内稳定角色编码',
  role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '角色状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '角色创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '角色修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (tenant_id, role_code),
  KEY idx_sys_role_scope (system_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统租户角色';

CREATE TABLE un_sys_member_role (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '成员角色关系标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '所属租户标识',
  member_id BIGINT UNSIGNED NOT NULL COMMENT '系统成员标识',
  role_id BIGINT UNSIGNED NOT NULL COMMENT '租户角色标识',
  create_time DATETIME(3) NOT NULL COMMENT '角色授予时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_member_role (tenant_id, member_id, role_id),
  KEY idx_sys_member_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户成员的角色授予关系';

CREATE TABLE un_sys_role_menu (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色菜单动作授权标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '所属租户标识',
  role_id BIGINT UNSIGNED NOT NULL COMMENT '受权角色标识',
  menu_id BIGINT UNSIGNED NOT NULL COMMENT '已发布模块菜单标识',
  action_code VARCHAR(64) NOT NULL COMMENT '菜单下允许的动作编码',
  create_time DATETIME(3) NOT NULL COMMENT '授权创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_menu_action (role_id, menu_id, action_code),
  KEY idx_sys_role_menu_scope (system_id, tenant_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色对模块菜单及动作的正向授权';

-- source: init/V3__module_config.sql
-- 用途：模块组、模块、字段、菜单、动作的可编辑草稿及不可变发布版本；运行态只读取发布快照。
CREATE TABLE un_cfg_module_group (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块组标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '配置来源租户标识，首批为主租户',
  group_code VARCHAR(64) NOT NULL COMMENT '系统内稳定模块组编码',
  group_name VARCHAR(100) NOT NULL COMMENT '运行态顶栏分组名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '顶栏展示顺序',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '模块组状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '模块组创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '模块组修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cfg_module_group_code (system_id, group_code),
  KEY idx_cfg_module_group_scope (system_id, tenant_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块运行态导航分组';

CREATE TABLE un_cfg_module (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '业务模块标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '配置来源租户标识，首批为主租户',
  group_id BIGINT UNSIGNED NOT NULL COMMENT '所属模块组标识',
  module_code VARCHAR(64) NOT NULL COMMENT '系统内稳定模块编码',
  module_name VARCHAR(120) NOT NULL COMMENT '模块名称',
  current_version_no INT NOT NULL DEFAULT 0 COMMENT '当前已发布版本号，0表示尚未发布',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '模块状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '模块创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '模块修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cfg_module_code (system_id, module_code),
  KEY idx_cfg_module_group (system_id, group_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可配置业务模块';

CREATE TABLE un_cfg_module_field (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块字段标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '字段配置来源租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务模块标识',
  field_code VARCHAR(64) NOT NULL COMMENT '模块内稳定字段编码',
  field_name VARCHAR(120) NOT NULL COMMENT '字段显示名称',
  field_type VARCHAR(40) NOT NULL COMMENT '已支持的字段类型编码',
  required_flag TINYINT NOT NULL DEFAULT 0 COMMENT '表单是否必填：1是，0否',
  searchable_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许列表筛选：1是，0否',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '默认表单和列表排序',
  config_json TEXT NULL COMMENT '字段类型相关约束与显示配置的 JSON 文本',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '字段草稿状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '字段创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '字段修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cfg_module_field_code (module_id, field_code),
  KEY idx_cfg_module_field_scope (system_id, tenant_id, module_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块字段草稿配置';

CREATE TABLE un_cfg_module_menu (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块菜单标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '菜单配置来源租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务模块标识',
  menu_code VARCHAR(64) NOT NULL COMMENT '模块内稳定菜单编码',
  menu_name VARCHAR(100) NOT NULL COMMENT '运行态左侧菜单名称',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '左侧菜单排序',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '菜单草稿状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '菜单创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '菜单修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cfg_module_menu_code (module_id, menu_code),
  KEY idx_cfg_module_menu_scope (system_id, tenant_id, module_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块运行菜单草稿配置';

CREATE TABLE un_cfg_module_action (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块动作标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '动作配置来源租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务模块标识',
  action_code VARCHAR(64) NOT NULL COMMENT '模块内稳定动作编码',
  action_name VARCHAR(100) NOT NULL COMMENT '动作显示名称',
  display_location VARCHAR(40) NOT NULL COMMENT '模块入口、工具栏、行或详情中的展示位置',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同位置动作排序',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '动作草稿状态：1启用，0停用',
  create_time DATETIME(3) NOT NULL COMMENT '动作创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '动作修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cfg_module_action_code (module_id, action_code),
  KEY idx_cfg_module_action_scope (system_id, tenant_id, module_id, display_location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块动作草稿配置';

CREATE TABLE un_cfg_module_version (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '模块发布版本标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '发布来源租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '发布的业务模块标识',
  version_no INT NOT NULL COMMENT '模块内递增发布版本号',
  snapshot_json LONGTEXT NOT NULL COMMENT '字段、菜单、动作和页面配置的不可变完整快照',
  published_by_member_id BIGINT UNSIGNED NOT NULL COMMENT '发布操作系统成员标识',
  publish_time DATETIME(3) NOT NULL COMMENT '版本发布时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cfg_module_version (module_id, version_no),
  KEY idx_cfg_module_version_scope (system_id, tenant_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务模块不可变发布版本';

-- source: init/V4__runtime_data.sql
-- 用途：所有用户自定义模块共用主记录、字段原值和类型化查询索引；不按模块动态生成物理表。
CREATE TABLE un_biz_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通用业务记录标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '记录原始所属租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务模块标识',
  module_version_no INT NOT NULL COMMENT '创建或最近验证时使用的模块发布版本号',
  record_no VARCHAR(80) NOT NULL COMMENT '模块租户内唯一业务编号',
  title VARCHAR(255) NOT NULL COMMENT '业务记录标题',
  business_status VARCHAR(64) NOT NULL DEFAULT 'draft' COMMENT '业务状态编码',
  owner_member_id BIGINT UNSIGNED NOT NULL COMMENT '当前负责人系统成员标识',
  owner_department_id BIGINT UNSIGNED NULL COMMENT '当前负责部门标识，组织启用后使用',
  related_member_ids_json TEXT NULL COMMENT '相关人员系统成员标识的 JSON 文本',
  created_by_member_id BIGINT UNSIGNED NOT NULL COMMENT '创建人系统成员标识',
  updated_by_member_id BIGINT UNSIGNED NULL COMMENT '最近修改人系统成员标识',
  archived_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否归档：1是，0否',
  deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否逻辑删除：1是，0否',
  version_no BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time DATETIME(3) NOT NULL COMMENT '业务记录创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '业务记录修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_record_no (tenant_id, module_id, record_no),
  KEY idx_biz_record_list (system_id, tenant_id, module_id, deleted_flag, update_time),
  KEY idx_biz_record_owner (system_id, tenant_id, module_id, owner_member_id, deleted_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自定义模块通用业务主记录';

CREATE TABLE un_biz_record_data (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录字段原值标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '记录原始所属租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务模块标识',
  record_id BIGINT UNSIGNED NOT NULL COMMENT '关联通用业务主记录标识',
  field_id BIGINT UNSIGNED NOT NULL COMMENT '发布字段标识',
  field_code VARCHAR(64) NOT NULL COMMENT '写入时字段稳定编码',
  value_json LONGTEXT NULL COMMENT '字段原值的 JSON 文本，按发布类型校验',
  update_time DATETIME(3) NOT NULL COMMENT '字段值最近修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_record_data_field (record_id, field_id),
  KEY idx_biz_record_data_scope (system_id, tenant_id, module_id, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用业务记录的动态字段原值';

CREATE TABLE un_biz_record_index (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录查询索引标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '记录原始所属租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务模块标识',
  record_id BIGINT UNSIGNED NOT NULL COMMENT '关联通用业务主记录标识',
  field_id BIGINT UNSIGNED NOT NULL COMMENT '索引对应的发布字段标识',
  value_text VARCHAR(512) NULL COMMENT '文本型筛选和排序值',
  value_number DECIMAL(24, 6) NULL COMMENT '数值型筛选和排序值',
  value_time DATETIME(3) NULL COMMENT '日期时间型筛选和排序值',
  value_boolean TINYINT NULL COMMENT '布尔型筛选值',
  update_time DATETIME(3) NOT NULL COMMENT '索引值最近重建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_record_index_field (record_id, field_id),
  KEY idx_biz_record_index_text (system_id, tenant_id, module_id, field_id, value_text(100)),
  KEY idx_biz_record_index_number (system_id, tenant_id, module_id, field_id, value_number),
  KEY idx_biz_record_index_time (system_id, tenant_id, module_id, field_id, value_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可由字段原值重建的类型化查询索引';

-- No post-baseline database update exists yet.
