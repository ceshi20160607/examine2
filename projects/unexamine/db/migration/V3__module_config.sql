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
