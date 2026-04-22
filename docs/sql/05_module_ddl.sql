-- module（无代码/自建应用）模块表结构
-- 说明：
-- - 所有表统一使用 un_ 前缀
-- - 所有表包含：create_user_id / update_user_id / create_time / update_time
-- - 作用域隔离：system_id / tenant_id（无多租户时 tenant_id 固定 0）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS un_module_dict_item;
DROP TABLE IF EXISTS un_module_dict;
DROP TABLE IF EXISTS un_module_field_option;

DROP TABLE IF EXISTS un_module_integration_mapping;
DROP TABLE IF EXISTS un_module_integration_event;
DROP TABLE IF EXISTS un_module_integration;

DROP TABLE IF EXISTS un_module_export_tpl_field;
DROP TABLE IF EXISTS un_module_export_tpl;
DROP TABLE IF EXISTS un_module_export_job;

DROP TABLE IF EXISTS un_module_list_filter_field;
DROP TABLE IF EXISTS un_module_list_filter_tpl;
DROP TABLE IF EXISTS un_module_list_view_col;
DROP TABLE IF EXISTS un_module_list_view;

DROP TABLE IF EXISTS un_module_model_action;
DROP TABLE IF EXISTS un_module_action;

DROP TABLE IF EXISTS un_module_role_field_perm;
DROP TABLE IF EXISTS un_module_role_action_perm;
DROP TABLE IF EXISTS un_module_role_menu_perm;
DROP TABLE IF EXISTS un_module_role_page_perm;
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_app_ver (app_id, version_no),
  KEY idx_module_app_ver_query (system_id, tenant_id, app_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用版本/发布记录';

-- -----------------------------------------------------------------------------
-- 2) 菜单/页面（搭建态）
-- -----------------------------------------------------------------------------
-- 字段自定义（产品分层）：
-- （1）模块/列表数据域：成员「能看/能改哪些模型字段」以 un_module_role_perm perm_type=field、perm_key（建议 modelCode.fieldCode）为主；
--     菜单可带 module_fields_json 作为本入口下字段展示/校验等默认模板，与角色 field 权限叠加规则由产品约定。
-- （2）页面内表单：un_module_page.form_fields_json 存「新建页面里表单字段」级自定义；整页布局/区块仍可用 config_json、un_module_page_block.config_json。
CREATE TABLE un_module_menu (
  id                  BIGINT       NOT NULL COMMENT '菜单ID',
  system_id           BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id           BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id              BIGINT       NOT NULL COMMENT 'un_module_app.id',
  parent_id           BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID；0=根',
  menu_name           VARCHAR(255) NOT NULL COMMENT '菜单名称',
  page_id             BIGINT       NULL COMMENT '绑定页面ID（un_module_page.id，可为空）',
  sort_no             INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  visible_flag        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可见：1=可见 0=隐藏（接口门菜单可置 0）',
  perm_key            VARCHAR(255) NULL COMMENT '与 un_module_role_perm.perm_key 一致，菜单级功能权限',
  api_pattern         VARCHAR(255) NULL COMMENT '后端 Ant 路径；与 perm_key 均非空时参与 HTTP 鉴权',
  module_fields_json  JSON         NULL COMMENT '本菜单（模块入口）下模型字段展示/校验等自定义（可选）',
  create_user_id      BIGINT       NULL COMMENT '创建人 platId',
  update_user_id      BIGINT       NULL COMMENT '更新人 platId',
  create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_module_menu_tree (app_id, parent_id, sort_no),
  KEY idx_module_menu_page (app_id, page_id),
  KEY idx_module_menu_perm (app_id, perm_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用菜单（权限与接口门统一挂菜单）';

CREATE TABLE un_module_page (
  id             BIGINT        NOT NULL COMMENT '页面ID',
  system_id      BIGINT        NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT        NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT        NOT NULL COMMENT 'un_module_app.id',
  page_code      VARCHAR(64)   NOT NULL COMMENT '页面编码（同 app 内唯一）',
  page_name      VARCHAR(255)  NOT NULL COMMENT '页面名称',
  page_type         VARCHAR(32)   NOT NULL DEFAULT 'list' COMMENT '页面类型：list|form|detail|custom',
  route_path        VARCHAR(255)  NULL COMMENT '路由路径（前端使用，可选）',
  config_json       JSON          NULL COMMENT '页面配置 JSON（布局/数据源/交互等）',
  form_fields_json  JSON          NULL COMMENT '页面内表单字段级自定义（控件/校验/联动等），与 config_json 分工见文件头说明',
  status            TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT        NULL COMMENT '创建人 platId',
  update_user_id BIGINT        NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  hidden_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否隐藏（字段级配置默认）：1=隐藏 0=显示',
  tips           VARCHAR(255) NULL COMMENT '输入提示/placeholder（可选）',
  max_length     INT          NULL COMMENT '最大长度（string 等，可选）',
  min_length     INT          NULL COMMENT '最小长度（string 等，可选）',
  validate_type  VARCHAR(32)  NULL COMMENT '校验类型（phone|email|idCard|url 等，可选）',
  date_format    VARCHAR(32)  NULL COMMENT '日期格式（如 yyyymmdd / yyyy-mm-dd / yyyy-mm-dd HH:mm:ss）',
  dict_code      VARCHAR(64)  NULL COMMENT '数据字典编码（引用 un_module_dict.dict_code，可选）',
  multi_flag     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否多选：1=多选 0=单选（enum/dict 等）',
  default_value  VARCHAR(512) NULL COMMENT '默认值（字符串表示，可选）',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_field_code (model_id, field_code),
  KEY idx_module_field_query (model_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型字段定义';

CREATE TABLE un_module_dict (
  id             BIGINT       NOT NULL COMMENT '字典ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  dict_code      VARCHAR(64)  NOT NULL COMMENT '字典编码（同 app 内唯一）',
  dict_name      VARCHAR(255) NOT NULL COMMENT '字典名称',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  remark         VARCHAR(512) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_dict_code (app_id, dict_code),
  KEY idx_module_dict_query (system_id, tenant_id, app_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典（下拉选项来源）';

CREATE TABLE un_module_dict_item (
  id             BIGINT       NOT NULL COMMENT '字典项ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  dict_id        BIGINT       NOT NULL COMMENT 'un_module_dict.id',
  item_value     VARCHAR(128) NOT NULL COMMENT '选项值',
  item_label     VARCHAR(255) NOT NULL COMMENT '选项名',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_dict_item (dict_id, item_value),
  KEY idx_module_dict_item_query (dict_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据字典项';

CREATE TABLE un_module_field_option (
  id             BIGINT       NOT NULL COMMENT '字段选项ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  field_id       BIGINT       NOT NULL COMMENT 'un_module_field.id',
  option_value   VARCHAR(128) NOT NULL COMMENT '选项值',
  option_label   VARCHAR(255) NOT NULL COMMENT '选项名',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_field_option (field_id, option_value),
  KEY idx_module_field_option_query (field_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段下拉选项（不走字典时使用）';
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  field_code     VARCHAR(64)  NOT NULL COMMENT '字段编码，与 un_module_field.field_code 对齐；EAV 一行一字段',
  value_text     MEDIUMTEXT   NULL COMMENT '字段值（字符串存储；数值/时间可存字面量，后续可扩 typed 列）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_record_data_field (record_id, field_code),
  KEY idx_module_record_data_model (model_id, record_id),
  KEY idx_module_record_data_lookup (model_id, field_code, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型记录数据（EAV：一行一字段）';

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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_member (app_id, plat_id),
  KEY idx_module_member_role (app_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用成员';

CREATE TABLE un_module_role_menu_perm (
  id             BIGINT       NOT NULL COMMENT '菜单权限ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  role_id        BIGINT       NOT NULL COMMENT 'un_module_role.id',
  menu_id        BIGINT       NOT NULL COMMENT 'un_module_menu.id',
  perm_level     TINYINT      NOT NULL DEFAULT 1 COMMENT '权限级别：1=允许 0=禁止',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_role_menu_perm (role_id, menu_id),
  KEY idx_module_role_menu_perm (app_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单权限';

CREATE TABLE un_module_role_action_perm (
  id             BIGINT       NOT NULL COMMENT '动作权限ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  role_id        BIGINT       NOT NULL COMMENT 'un_module_role.id',
  action_code    VARCHAR(64)  NOT NULL COMMENT '动作编码（关联 un_module_action.action_code）',
  perm_level     TINYINT      NOT NULL DEFAULT 1 COMMENT '权限级别：1=允许 0=禁止',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_role_action_perm (role_id, model_id, action_code),
  KEY idx_module_role_action_perm (app_id, role_id, model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色动作权限（模块功能点）';

CREATE TABLE un_module_role_page_perm (
  id             BIGINT       NOT NULL COMMENT '页面权限ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  role_id        BIGINT       NOT NULL COMMENT 'un_module_role.id',
  page_id        BIGINT       NOT NULL COMMENT 'un_module_page.id',
  perm_level     TINYINT      NOT NULL DEFAULT 1 COMMENT '权限级别：1=允许 0=禁止',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_role_page_perm (role_id, page_id),
  KEY idx_module_role_page_perm (app_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色页面权限';

CREATE TABLE un_module_role_field_perm (
  id             BIGINT       NOT NULL COMMENT '字段权限ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  role_id        BIGINT       NOT NULL COMMENT 'un_module_role.id',
  field_id       BIGINT       NOT NULL COMMENT 'un_module_field.id',
  visible_flag   TINYINT      NOT NULL DEFAULT 1 COMMENT '可见：1=可见 0=不可见',
  editable_flag  TINYINT      NOT NULL DEFAULT 0 COMMENT '可编辑：1=可编辑 0=只读/不可编辑',
  mask_type      VARCHAR(16)  NOT NULL DEFAULT 'none' COMMENT '脱敏：none|partial|full',
  remark         VARCHAR(255) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_role_field_perm (role_id, field_id),
  KEY idx_module_role_field_scope (system_id, tenant_id, app_id, model_id, role_id, visible_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色字段权限（可见/可编辑/脱敏）';

-- -----------------------------------------------------------------------------
-- 6) 视图/筛选/导出（可配置项，建议拆表）
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_list_view (
  id             BIGINT       NOT NULL COMMENT '视图ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  plat_id        BIGINT       NULL COMMENT '平台账号 platId；为空表示系统/角色默认视图',
  view_code      VARCHAR(64)  NOT NULL COMMENT '视图编码（同 model+plat 唯一）',
  view_name      VARCHAR(255) NOT NULL COMMENT '视图名称',
  default_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认：1=默认 0=否',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_list_view (model_id, plat_id, view_code),
  KEY idx_module_list_view_query (system_id, tenant_id, app_id, model_id, plat_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表视图/表头方案（支持用户个性化）';

CREATE TABLE un_module_list_view_col (
  id             BIGINT       NOT NULL COMMENT '列配置ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  view_id        BIGINT       NOT NULL COMMENT 'un_module_list_view.id',
  field_id       BIGINT       NOT NULL COMMENT 'un_module_field.id',
  col_title      VARCHAR(255) NULL COMMENT '列标题（可覆盖 field_name）',
  width          INT          NULL COMMENT '列宽（px，可选）',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  visible_flag   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示：1=显示 0=隐藏',
  fixed_type     VARCHAR(16)  NULL COMMENT '固定列：left|right（可选）',
  format_json    JSON         NULL COMMENT '格式化/展示配置（可选）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_list_view_col (view_id, field_id),
  KEY idx_module_list_view_col (view_id, sort_no, visible_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表视图列配置（表头/列顺序/宽度）';

CREATE TABLE un_module_list_filter_tpl (
  id             BIGINT       NOT NULL COMMENT '筛选模板ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  menu_id        BIGINT       NULL COMMENT 'un_module_menu.id；为空表示模型级默认模板',
  tpl_code       VARCHAR(64)  NOT NULL COMMENT '模板编码（同 scope 内唯一）',
  tpl_name       VARCHAR(255) NOT NULL COMMENT '模板名称',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_list_filter_tpl (model_id, menu_id, tpl_code),
  KEY idx_module_list_filter_tpl (system_id, tenant_id, app_id, model_id, menu_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表筛选模板（可按菜单覆盖）';

CREATE TABLE un_module_list_filter_field (
  id             BIGINT       NOT NULL COMMENT '筛选项ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  tpl_id         BIGINT       NOT NULL COMMENT 'un_module_list_filter_tpl.id',
  field_id       BIGINT       NOT NULL COMMENT 'un_module_field.id',
  op_code        VARCHAR(32)  NOT NULL COMMENT '操作符：eq|ne|like|in|between|gt|lt 等',
  default_value  VARCHAR(512) NULL COMMENT '默认值（可选）',
  required_flag  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填：1=必填 0=否',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_list_filter_field (tpl_id, field_id),
  KEY idx_module_list_filter_field (tpl_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列表筛选项配置（可筛字段/默认值/顺序）';

CREATE TABLE un_module_export_tpl (
  id             BIGINT       NOT NULL COMMENT '导出模板ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  menu_id        BIGINT       NULL COMMENT 'un_module_menu.id；为空表示模型级模板',
  tpl_code       VARCHAR(64)  NOT NULL COMMENT '模板编码（同 scope 内唯一）',
  tpl_name       VARCHAR(255) NOT NULL COMMENT '模板名称',
  file_type      VARCHAR(16)  NOT NULL DEFAULT 'xlsx' COMMENT '文件类型：xlsx|csv',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_export_tpl (model_id, menu_id, tpl_code),
  KEY idx_module_export_tpl (system_id, tenant_id, app_id, model_id, menu_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出模板（字段集合与格式）';

CREATE TABLE un_module_export_tpl_field (
  id             BIGINT       NOT NULL COMMENT '导出字段ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  tpl_id         BIGINT       NOT NULL COMMENT 'un_module_export_tpl.id',
  field_id       BIGINT       NOT NULL COMMENT 'un_module_field.id',
  col_title      VARCHAR(255) NULL COMMENT '列标题（可覆盖 field_name）',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  format_json    JSON         NULL COMMENT '格式/脱敏/字典转换等（可选）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_export_tpl_field (tpl_id, field_id),
  KEY idx_module_export_tpl_field (tpl_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出模板字段配置（导出列）';

CREATE TABLE un_module_export_job (
  id             BIGINT       NOT NULL COMMENT '任务ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'appId',
  model_id       BIGINT       NOT NULL COMMENT 'modelId',
  tpl_id         BIGINT       NOT NULL COMMENT '导出模板ID',
  file_type      VARCHAR(16)  NOT NULL DEFAULT 'csv' COMMENT 'csv|xlsx',
  status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0=pending 1=running 2=success 3=failed',
  query_json     JSON         NULL COMMENT '导出查询 DSL（可选）',
  result_file_id BIGINT       NULL COMMENT '结果文件ID（un_upload_file.id）',
  error_msg      VARCHAR(512) NULL COMMENT '失败原因（可选）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_export_job_scope (system_id, tenant_id, app_id, model_id, status, id),
  KEY idx_export_job_creator (create_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块导出任务';

-- -----------------------------------------------------------------------------
-- 7) 模块内置功能（action）与开关
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_action (
  id             BIGINT       NOT NULL COMMENT '动作ID',
  action_code    VARCHAR(64)  NOT NULL COMMENT '动作编码（系统内置，如 create|update|delete|import|export|transfer|statusChange）',
  action_name    VARCHAR(255) NOT NULL COMMENT '动作名称',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  remark         VARCHAR(512) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_action_code (action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统内置动作字典（模块功能点）';

CREATE TABLE un_module_model_action (
  id             BIGINT       NOT NULL COMMENT '主键',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  action_code    VARCHAR(64)  NOT NULL COMMENT '动作编码（关联 un_module_action.action_code）',
  enabled_flag   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
  config_json    JSON         NULL COMMENT '动作配置（如转移规则、状态变更字典等）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_model_action (model_id, action_code),
  KEY idx_module_model_action_scope (system_id, tenant_id, app_id, model_id, enabled_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型启用的动作（模块功能开关）';

-- -----------------------------------------------------------------------------
-- 8) 第三方集成与字段映射（推送/对接）
-- -----------------------------------------------------------------------------
CREATE TABLE un_module_integration (
  id             BIGINT       NOT NULL COMMENT '集成ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  integration_code VARCHAR(64) NOT NULL COMMENT '集成编码（同 app 内唯一）',
  integration_name VARCHAR(255) NOT NULL COMMENT '集成名称',
  endpoint_url   VARCHAR(512) NULL COMMENT '接口地址（可选）',
  auth_json      JSON         NULL COMMENT '鉴权配置（token/aksk 等）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  remark         VARCHAR(512) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_integration_code (app_id, integration_code),
  KEY idx_module_integration_scope (system_id, tenant_id, app_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方集成配置';

CREATE TABLE un_module_integration_event (
  id             BIGINT       NOT NULL COMMENT '事件ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  integration_id BIGINT       NOT NULL COMMENT 'un_module_integration.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  event_code     VARCHAR(64)  NOT NULL COMMENT '触发事件：create|update|delete|statusChange|schedule 等',
  enabled_flag   TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1=启用 0=禁用',
  config_json    JSON         NULL COMMENT '事件配置（触发条件、过滤、重试等）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_integration_event (integration_id, model_id, event_code),
  KEY idx_module_integration_event_scope (system_id, tenant_id, app_id, integration_id, enabled_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成触发事件（按模型）';

CREATE TABLE un_module_integration_mapping (
  id             BIGINT       NOT NULL COMMENT '映射ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  app_id         BIGINT       NOT NULL COMMENT 'un_module_app.id',
  integration_id BIGINT       NOT NULL COMMENT 'un_module_integration.id',
  model_id       BIGINT       NOT NULL COMMENT 'un_module_model.id',
  direction      VARCHAR(16)  NOT NULL COMMENT '方向：push|pull',
  external_param VARCHAR(128) NOT NULL COMMENT '外部参数名/字段名',
  field_id       BIGINT       NOT NULL COMMENT 'un_module_field.id',
  required_flag  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填：1=必填 0=否',
  default_value  VARCHAR(512) NULL COMMENT '默认值（可选）',
  transform_json JSON         NULL COMMENT '转换规则（映射、表达式、字典等）',
  sort_no        INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_module_integration_mapping (integration_id, model_id, direction, external_param),
  KEY idx_module_integration_mapping_scope (system_id, tenant_id, app_id, integration_id, model_id, direction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成字段映射（外参 ↔ 模型字段）';

SET FOREIGN_KEY_CHECKS = 1;

