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
