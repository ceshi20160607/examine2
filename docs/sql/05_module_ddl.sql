-- module（无代码/自建应用）模块表结构
-- 说明：
-- - 所有表统一使用 un_ 前缀
-- - 所有表包含：create_user_id / update_user_id / create_time / update_time
-- - 作用域隔离：system_id / tenant_id（无多租户时 tenant_id 固定 0）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS un_module_role_perm;
DROP TABLE IF EXISTS un_module_member;
DROP TABLE IF EXISTS un_module_role;

DROP TABLE IF EXISTS un_module_record_history;
DROP TABLE IF EXISTS un_module_record_data;
DROP TABLE IF EXISTS un_module_record;

DROP TABLE IF EXISTS un_module_relation;
DROP TABLE IF EXISTS un_module_index;
DROP TABLE IF EXISTS un_module_field;
DROP TABLE IF EXISTS un_module_model;

DROP TABLE IF EXISTS un_module_page_block;
DROP TABLE IF EXISTS un_module_page;
DROP TABLE IF EXISTS un_module_menu;

DROP TABLE IF EXISTS un_module_app_version;
DROP TABLE IF EXISTS un_module_app;

-- -----------------------------------------------------------------------------
-- 1) 应用/模块
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_app (
  id             BIGINT       NOT NULL COMMENT '应用ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId；无多租户时固定 0',
  app_code       VARCHAR(64)  NOT NULL COMMENT '应用编码（同 system 内唯一）',
  app_name       VARCHAR(255) NOT NULL COMMENT '应用名称',
  icon_url       VARCHAR(512) NULL COMMENT '图标 URL',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  published_flag TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已发布：0=否 1=是（MVP 可选）',
  remark         VARCHAR(512) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_app_code (system_id, tenant_id, app_code),
  KEY idx_module_app_status (system_id, tenant_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='无代码应用';

CREATE TABLE un_module_app_version (
  id             BIGINT       NOT NULL COMMENT '版本ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  version_no     INT          NOT NULL COMMENT '版本号（递增）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布 3=已废弃',
  snapshot_json  JSON         NULL COMMENT '发布快照（页面/模型等聚合 JSON；MVP 可为空）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_app_ver (app_id, version_no),
  KEY idx_module_app_ver_query (system_id, tenant_id, app_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用版本/发布记录';

-- -----------------------------------------------------------------------------
-- 2) 菜单/页面（搭建态）
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_menu (
  id             BIGINT       NOT NULL COMMENT '菜单ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  parent_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID；0=根',
  menu_name      VARCHAR(255) NOT NULL COMMENT '菜单名称',
  page_id        BIGINT       NULL COMMENT '绑定页面ID（un_module_page.id，可为空）',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  visible_flag   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可见：1=可见 0=隐藏',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_menu_tree (app_id, parent_id, sort_no),
  KEY idx_module_menu_page (app_id, page_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用菜单';

CREATE TABLE un_module_page (
  id             BIGINT        NOT NULL COMMENT '页面ID',
  system_id      BIGINT        NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT        NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT        NOT NULL COMMENT 'un_module_app.id',
  page_code      VARCHAR(64)   NOT NULL COMMENT '页面编码（同 app 内唯一）',
  page_name      VARCHAR(255)  NOT NULL COMMENT '页面名称',
  page_type      VARCHAR(32)   NOT NULL DEFAULT 'list' COMMENT '页面类型：list|form|detail|custom',
  route_path     VARCHAR(255)  NULL COMMENT '路由路径（前端使用，可选）',
  config_json    JSON          NULL COMMENT '页面配置 JSON（布局/数据源/交互等）',
  status         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT        NULL COMMENT '创建人 platId',
  update_user_id BIGINT        NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_page_code (app_id, page_code),
  KEY idx_module_page_query (app_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用页面';

CREATE TABLE un_module_page_block (
  id             BIGINT       NOT NULL COMMENT '区块ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  page_id        BIGINT       NOT NULL COMMENT 'un_module_page.id',
  block_type     VARCHAR(32)  NOT NULL COMMENT '区块类型：form|table|chart|text|custom',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  config_json    JSON         NULL COMMENT '区块配置 JSON',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_block_page (page_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面区块';

-- -----------------------------------------------------------------------------
-- 3) 数据模型（自定义字段核心）
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_model (
  id             BIGINT       NOT NULL COMMENT '模型ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_code     VARCHAR(64)  NOT NULL COMMENT '模型编码（同 app 内唯一）',
  model_name     VARCHAR(255) NOT NULL COMMENT '模型名称',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  remark         VARCHAR(512) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_model_code (app_id, model_code),
  KEY idx_module_model_query (app_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务模型（元数据）';

CREATE TABLE un_module_field (
  id             BIGINT       NOT NULL COMMENT '字段ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  field_code     VARCHAR(64)  NOT NULL COMMENT '字段编码（同 model 内唯一）',
  field_name     VARCHAR(255) NOT NULL COMMENT '字段名称',
  field_type     VARCHAR(32)  NOT NULL COMMENT '字段类型：string|number|date|datetime|bool|enum|ref|json 等',
  required_flag  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填：1=必填 0=可空',
  unique_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否唯一：1=唯一 0=否',
  default_value  VARCHAR(512) NULL COMMENT '默认值（字符串表示，可选）',
  options_json   JSON         NULL COMMENT '枚举/字典/校验等扩展配置 JSON',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_field_code (model_id, field_code),
  KEY idx_module_field_query (model_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型字段定义';

CREATE TABLE un_module_index (
  id             BIGINT       NOT NULL COMMENT '索引/约束ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  index_type     VARCHAR(32)  NOT NULL COMMENT '索引类型：unique|index',
  fields_json    JSON         NOT NULL COMMENT '字段列表（JSON 数组：field_code）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_index_model (model_id, index_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型索引/唯一约束（元数据）';

CREATE TABLE un_module_relation (
  id             BIGINT       NOT NULL COMMENT '关系ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  src_model_id   BIGINT       NOT NULL COMMENT '源模型ID',
  dst_model_id   BIGINT       NOT NULL COMMENT '目标模型ID',
  rel_type       VARCHAR(16)  NOT NULL COMMENT '关系类型：1-1|1-n|n-n',
  config_json    JSON         NULL COMMENT '关系配置（字段映射/约束/级联等）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_rel_src (src_model_id),
  KEY idx_module_rel_dst (dst_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型关系（元数据）';

-- -----------------------------------------------------------------------------
-- 4) 数据存储（实例数据）
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_record (
  id             BIGINT       NOT NULL COMMENT '记录ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=正常 2=删除/禁用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_record_query (model_id, status, update_time),
  KEY idx_module_record_scope (system_id, tenant_id, app_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型记录主表';

CREATE TABLE un_module_record_data (
  id             BIGINT       NOT NULL COMMENT '数据ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  record_id      BIGINT       NOT NULL COMMENT 'un_module_record.id',
  data_json      JSON         NOT NULL COMMENT '记录数据（动态字段 JSON）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_record_data (record_id),
  KEY idx_module_record_data_model (model_id, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型记录数据（*_data）';

CREATE TABLE un_module_record_history (
  id             BIGINT       NOT NULL COMMENT '历史ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  record_id      BIGINT       NOT NULL COMMENT 'un_module_record.id',
  action         VARCHAR(32)  NOT NULL COMMENT '动作：create|update|delete',
  data_json      JSON         NULL COMMENT '变更后数据快照（可选）',
  diff_json      JSON         NULL COMMENT '差异（可选）',
  create_user_id BIGINT       NULL COMMENT '操作人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId（一般为空）',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_record_hist (record_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录变更历史';

-- -----------------------------------------------------------------------------
-- 5) 权限与协作
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_role (
  id             BIGINT       NOT NULL COMMENT '角色ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  role_code      VARCHAR(64)  NOT NULL COMMENT '角色编码（同 app 内唯一）',
  role_name      VARCHAR(255) NOT NULL COMMENT '角色名称',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_role_code (app_id, role_code),
  KEY idx_module_role_query (app_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用角色';

CREATE TABLE un_module_member (
  id             BIGINT       NOT NULL COMMENT '成员ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  plat_id        BIGINT       NOT NULL COMMENT '平台账号 platId',
  role_id        BIGINT       NULL COMMENT '角色ID（un_module_role.id，可为空）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=正常 2=禁用/移除',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_member (app_id, plat_id),
  KEY idx_module_member_role (app_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用成员';

CREATE TABLE un_module_role_perm (
  id             BIGINT       NOT NULL COMMENT '权限ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  role_id        BIGINT       NOT NULL COMMENT 'un_module_role.id',
  perm_type      VARCHAR(32)  NOT NULL COMMENT '权限类型：page|menu|action|field',
  perm_key       VARCHAR(255) NOT NULL COMMENT '权限键（如 page_code / action_code / field_code）',
  perm_level     TINYINT      NOT NULL DEFAULT 1 COMMENT '权限级别：1=允许 0=禁止（或可扩展）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_perm_role (role_id, perm_type),
  KEY idx_module_perm_key (app_id, perm_type, perm_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限';

SET FOREIGN_KEY_CHECKS = 1;

