-- =============================================================================
-- schema_v2.sql — 数据模型说明与增量表（与 doc/module.sql、doc/examine.sql 配套）
-- =============================================================================
--
-- 设计目标（对齐钉钉 / 飞书类产品形态）：
--
-- 1) 平台账号（plat_*）**不带 tenant_id / company_id**
--    - 用户先有一个「平台登录身份」，与是否已建低代码应用、是否加入某企业无关。
--    - 登录后即可进入控制台；未加入任何企业时仍可浏览「个人/平台级」能力。
--
-- 2) 企业 / 空间（org_company，对应现有表里的 company_id）
--    - 「你建系统」= 在某企业下创建应用；企业内自有用户、角色、部门、数据权限。
--    - 同一平台账号可加入多个企业（多行 org_member）。
--
-- 3) 企业内成员（org_member）+ 业务用户（un_module_user）
--    - 低代码模块侧继续使用 **doc/module.sql** 中的 un_module_* 全量表（字段、菜单、角色、
--      记录、字典、文件等），复杂度以该文件为准，本文件不重复定义那些表。
--    - 平台账号与 un_module_user 通过 plat_account_id（见下方扩展）或独立映射表关联。
--
-- 4) 一人多部门、多角色、多数据权限
--    - 多角色：沿用 un_module_role_user（一对多）。
--    - 多数据权限：沿用 un_module_user_data，允许同一 user_id + module_id 下多行（按 dept 区分），
--      见下方扩展表说明。
--    - 多部门：原 un_module_user.dept_id 为「主部门/展示用」；明细见 un_module_user_dept。
--
-- 5) 流程 / 审批
--    - **以 doc/examine.sql 为准**（un_examine / un_examine_node / un_examine_record / …），
--      能力比简化的 flow_* 更完整，本文件 **不再** 定义那套简化流程表。
--    - 若需要「业务变更单」与审批实例关联，可使用下方可选表 biz_change_request。
--
-- 使用方式：
--   - 新建库：先执行 doc/module.sql、doc/examine.sql（及 doc/user.sql 若仍要全局后台菜单），
--     再执行本文件中的增量表。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 一、平台层：登录身份（无租户字段）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS plat_account;
CREATE TABLE plat_account (
  id             BIGINT       NOT NULL COMMENT '平台用户主键',
  username       VARCHAR(64)  NULL COMMENT '登录名（可与手机/邮箱其一组合唯一策略）',
  password_hash  VARCHAR(255) NULL COMMENT '密码摘要，若仅第三方登录可为空',
  password_salt  VARCHAR(64)  NULL,
  mobile         VARCHAR(32)  NULL,
  email          VARCHAR(128) NULL,
  display_name   VARCHAR(128) NULL,
  avatar_url     VARCHAR(255) NULL,
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=禁用',
  last_login_time DATETIME    NULL,
  last_login_ip  VARCHAR(64)  NULL COMMENT '兼容 IPv6',
  create_time    DATETIME     NOT NULL,
  update_time    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_plat_mobile (mobile),
  KEY idx_plat_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号（与任何企业无关）';

-- 登录名全局唯一时可加：UNIQUE KEY uk_plat_username (username);
-- 若允许同一手机号在不同区号重复，建议单独做区号+手机号或 union 唯一索引策略。

DROP TABLE IF EXISTS plat_session;
CREATE TABLE plat_session (
  id             BIGINT       NOT NULL,
  plat_account_id BIGINT    NOT NULL,
  device         VARCHAR(32)  NOT NULL DEFAULT 'web' COMMENT 'web/app/h5',
  access_token   VARCHAR(128) NOT NULL,
  refresh_token  VARCHAR(128) NULL,
  issued_at      DATETIME     NOT NULL,
  expire_at      DATETIME     NOT NULL,
  revoked_flag   TINYINT      NOT NULL DEFAULT 0,
  ip             VARCHAR(64)  NULL,
  ua             VARCHAR(512) NULL,
  -- 当前会话可选上下文（也可只放 Redis，不落库）
  active_company_id BIGINT    NULL COMMENT '当前选中的企业，可为空',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_session_token (access_token),
  KEY idx_plat_session_account (plat_account_id, device)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台会话（无 tenant_id）';

-- -----------------------------------------------------------------------------
-- 二、企业 / 空间层（对应原 company_id）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS org_company;
CREATE TABLE org_company (
  id           BIGINT       NOT NULL COMMENT '与 un_module.company_id / examine.company_id 一致',
  name         VARCHAR(255) NOT NULL,
  logo_url     VARCHAR(512) NULL,
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常',
  owner_plat_id BIGINT      NULL COMMENT '创建企业的平台账号（可选）',
  create_time  DATETIME     NOT NULL,
  update_time  DATETIME     NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业/空间（租户业务域）';

DROP TABLE IF EXISTS org_member;
CREATE TABLE org_member (
  id              BIGINT       NOT NULL,
  company_id      BIGINT       NOT NULL COMMENT 'org_company.id',
  plat_account_id BIGINT       NOT NULL COMMENT 'plat_account.id',
  employee_no     VARCHAR(64)  NULL COMMENT '工号',
  realname        VARCHAR(128) NULL COMMENT '在企业内的展示名',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=在职/正常',
  join_time       DATETIME     NULL,
  create_time     DATETIME     NOT NULL,
  update_time     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_org_member_company_account (company_id, plat_account_id),
  KEY idx_org_member_plat (plat_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号在某企业下的成员身份';

-- -----------------------------------------------------------------------------
-- 三、与 module.sql 的衔接（建议增量，不替换原表）
-- -----------------------------------------------------------------------------

-- 3.1 在 un_module_user 上增加平台账号外键（二选一落地）
-- ALTER TABLE un_module_user ADD COLUMN plat_account_id BIGINT NULL COMMENT '关联 plat_account.id' AFTER id;
-- ALTER TABLE un_module_user ADD KEY idx_module_user_plat (plat_account_id);
-- 若不想改原表，可用映射表：
DROP TABLE IF EXISTS plat_module_user_link;
CREATE TABLE plat_module_user_link (
  id               BIGINT NOT NULL,
  company_id       BIGINT NOT NULL,
  plat_account_id  BIGINT NOT NULL,
  module_user_id   BIGINT NOT NULL COMMENT 'un_module_user.id',
  PRIMARY KEY (id),
  UNIQUE KEY uk_link (company_id, module_user_id),
  UNIQUE KEY uk_link_plat_module (company_id, plat_account_id, module_user_id),
  KEY idx_link_plat (plat_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号与模块业务用户映射';

-- 3.2 多部门（钉钉式：主部门仍在 un_module_user.dept_id，多部门走明细表）
DROP TABLE IF EXISTS un_module_user_dept;
CREATE TABLE un_module_user_dept (
  id           BIGINT NOT NULL,
  company_id   BIGINT NOT NULL,
  module_id    BIGINT NOT NULL,
  user_id      BIGINT NOT NULL COMMENT 'un_module_user.id',
  dept_id      BIGINT NOT NULL COMMENT 'un_module_dept.id',
  main_flag    TINYINT NOT NULL DEFAULT 0 COMMENT '1=主部门',
  sub_flag     TINYINT NOT NULL DEFAULT 0 COMMENT '数据权限是否含子部门等，与业务约定一致',
  create_time  DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_dept (company_id, module_id, user_id, dept_id),
  KEY idx_user (company_id, module_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块用户多部门';

-- 3.3 多数据权限：原 un_module_user_data 注释为「一人一个部门」；若要多条，请去掉业务层
-- 「仅一行」假设，并建议增加唯一键避免重复：
-- UNIQUE (user_id, module_id, dept_id) — 需按实际迁移脚本执行。

-- -----------------------------------------------------------------------------
-- 四、流程层：以 examine.sql 为准 + 可选业务变更单
-- -----------------------------------------------------------------------------

-- 审批定义与运行实例请使用：
--   un_examine, un_examine_group, un_examine_setting, un_examine_setting_user,
--   un_examine_node, un_examine_node_user,
--   un_examine_record, un_examine_record_node, un_examine_record_node_user
-- （见 doc/examine.sql）

-- 可选：业务侧「变更申请」与 un_examine_record.relation_id 对齐
DROP TABLE IF EXISTS biz_change_request;
CREATE TABLE biz_change_request (
  id              BIGINT       NOT NULL,
  company_id      BIGINT       NOT NULL,
  module_id       BIGINT       NOT NULL,
  action_type     VARCHAR(32)  NOT NULL COMMENT 'ADD/EDIT/DELETE/TRANSFER/TRANSFORM/...',
  record_id       BIGINT       NULL,
  examine_record_id BIGINT   NULL COMMENT 'un_examine_record.id',
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1审批中 2通过 3驳回',
  snapshot_before JSON         NULL,
  snapshot_after  JSON         NULL,
  create_user_id  BIGINT       NULL,
  create_time     DATETIME     NOT NULL,
  update_time     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_biz_cr_company_module (company_id, module_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务变更单（可选，对接 examine）';

-- -----------------------------------------------------------------------------
-- 五、审计（使用 company_id，不用 tenant_id 命名）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
  id            BIGINT       NOT NULL,
  company_id    BIGINT       NULL COMMENT '可为空表示平台级操作',
  plat_account_id BIGINT     NULL,
  module_user_id BIGINT      NULL,
  module_id     BIGINT       NULL,
  record_id     BIGINT       NULL,
  action_code   VARCHAR(64)  NOT NULL,
  request_id    VARCHAR(64)  NULL,
  ip            VARCHAR(64)  NULL,
  ua            VARCHAR(255) NULL,
  detail_json   JSON         NULL,
  create_time   DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_company_time (company_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计';

-- =============================================================================
-- 附录：module 域完整表清单（以 doc/module.sql 为唯一来源，勿在本文件重复建表）
--   un_module, un_module_menu, un_module_role, un_module_role_menu,
--   un_module_role_user, un_module_role_field, un_module_field,
--   un_module_field_user, un_module_field_api_open,
--   un_module_record, un_module_record_data,
--   un_module_dict_base, un_module_dict_group, un_module_dict,
--   un_module_user, un_module_dept, un_module_file, un_module_user_data
-- =============================================================================
