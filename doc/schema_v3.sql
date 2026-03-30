-- =============================================================================
-- schema_v3.sql — examine2 物理表结构（对齐 README.md ADR / 术语）
-- =============================================================================
--
-- 与旧脚本关系（迁移时对照，非逐字兼容）：
--   doc/user.sql      → un_admin_* 管理端用户/部门/角色（可继续沿用或逐步收编到平台成员）
--   doc/module.sql    → un_module_* 低代码模块/字段/主数据/EAV（槽位 + record_data）
--   doc/examine.sql   → un_examine_* 旧审批；本文件用 flow_* / record_* 逻辑名重写
--
-- 上下文（D1）：platform_account_id + system_id + tenant_id
--   - 旧库 company_id 建议迁移映射为 system_id；tenant_id 关多租户时固定 0（或约定 defaultTenantId）
--
-- 主键（D10）：业务主键建议雪花 BIGINT，由应用分配；本 DDL 不强制 AUTO_INCREMENT，
--   若开发期使用自增，可改为 AUTO_INCREMENT（须与 ID 策略一致）。
--
-- 使用：新建库可直接执行本文件；已有 un_* 表时需按「迁移说明」增量 ALTER + 数据迁移。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 一、平台账号（无租户；参考 schema_v2 plat_*）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS plat_session;
DROP TABLE IF EXISTS plat_account;

CREATE TABLE plat_account (
  id              BIGINT       NOT NULL COMMENT '平台账号主键（雪花）',
  username        VARCHAR(64)  NULL COMMENT '登录名',
  password_hash   VARCHAR(255) NULL,
  password_salt   VARCHAR(64)  NULL,
  mobile          VARCHAR(32)  NULL,
  email           VARCHAR(128) NULL,
  display_name    VARCHAR(128) NULL,
  avatar_url      VARCHAR(512) NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=禁用',
  last_login_time DATETIME     NULL,
  last_login_ip   VARCHAR(64)  NULL,
  create_time     DATETIME     NOT NULL,
  update_time     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_plat_mobile (mobile),
  KEY idx_plat_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号';

CREATE TABLE plat_session (
  id               BIGINT       NOT NULL,
  plat_account_id  BIGINT       NOT NULL,
  device           VARCHAR(32)  NOT NULL DEFAULT 'web',
  access_token     VARCHAR(128) NOT NULL,
  refresh_token    VARCHAR(128) NULL,
  issued_at        DATETIME     NOT NULL,
  expire_at        DATETIME     NOT NULL,
  revoked_flag     TINYINT      NOT NULL DEFAULT 0,
  ip               VARCHAR(64)  NULL,
  ua               VARCHAR(512) NULL,
  active_system_id BIGINT       NULL COMMENT '当前选中的系统（低代码应用）',
  active_tenant_id BIGINT       NULL COMMENT '当前租户，可为空',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_session_token (access_token),
  KEY idx_plat_session_account (plat_account_id, device)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台会话';

-- -----------------------------------------------------------------------------
-- 二、系统（低代码「应用」）与租户、成员
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS sys_member;
DROP TABLE IF EXISTS sys_tenant;
DROP TABLE IF EXISTS sys_system;

CREATE TABLE sys_system (
  id                   BIGINT       NOT NULL COMMENT '系统ID（对应 README systemId；旧 company_id 可映射至此）',
  name                 VARCHAR(255) NOT NULL,
  icon_url             VARCHAR(512) NULL,
  multi_tenant_enabled TINYINT      NOT NULL DEFAULT 0 COMMENT '0=单租户 tenant_id 固定 1=多租户',
  default_tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '关多租户时业务表统一使用该值',
  status               TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常',
  owner_plat_account_id BIGINT      NULL,
  create_time          DATETIME     NOT NULL,
  update_time          DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sys_system_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='低代码系统（应用）';

CREATE TABLE sys_tenant (
  id          BIGINT       NOT NULL,
  system_id   BIGINT       NOT NULL,
  name        VARCHAR(255) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,
  create_time DATETIME     NOT NULL,
  update_time DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tenant_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户（multi_tenant_enabled=1 时使用）';

CREATE TABLE sys_member (
  id               BIGINT       NOT NULL,
  system_id        BIGINT       NOT NULL,
  tenant_id        BIGINT       NOT NULL DEFAULT 0 COMMENT 'D1：关多租户=sys_system.default_tenant_id',
  plat_account_id  BIGINT       NOT NULL,
  module_user_id   BIGINT       NULL COMMENT '可选：绑定 un_module_user.id',
  employee_no      VARCHAR(64)  NULL,
  realname         VARCHAR(128) NULL,
  status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常',
  join_time        DATETIME     NULL,
  create_time      DATETIME     NOT NULL,
  update_time      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_member_scope (system_id, tenant_id, plat_account_id),
  KEY idx_member_plat (plat_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统内成员（平台账号在某系统/租户下身份）';

-- -----------------------------------------------------------------------------
-- 三、流程定义（模板）— 对应旧 un_examine / un_examine_node
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS flow_node_assignee;
DROP TABLE IF EXISTS flow_definition_setting;
DROP TABLE IF EXISTS flow_node;
DROP TABLE IF EXISTS flow_definition;

CREATE TABLE flow_definition (
  id          BIGINT       NOT NULL COMMENT '流程定义ID（改模板=新ID，D6）',
  system_id   BIGINT       NOT NULL,
  tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '元数据 MVP 可共享；仍带列便于后续租户级定制',
  scope_type  TINYINT      NOT NULL DEFAULT 2 COMMENT '1=平台级对外 2=系统内模块级',
  module_id   BIGINT       NULL COMMENT 'scope_type=2 时绑定 un_module.id',
  group_id    BIGINT       NULL COMMENT '分组，对应旧 un_examine_group',
  name        VARCHAR(128) NOT NULL,
  icon        VARCHAR(64)  NULL,
  sort_num    INT          NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=停用 3=删除',
  remarks     VARCHAR(512) NULL,
  create_user_id BIGINT    NULL,
  update_user_id BIGINT    NULL,
  create_time DATETIME     NOT NULL,
  update_time DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_flow_def_system (system_id, tenant_id, status),
  KEY idx_flow_def_module (system_id, module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义 flow_definition';

CREATE TABLE flow_definition_setting (
  id                BIGINT NOT NULL,
  flow_definition_id BIGINT NOT NULL,
  system_id         BIGINT NOT NULL,
  tenant_id         BIGINT NOT NULL DEFAULT 0,
  rule_type         TINYINT NULL COMMENT '0撤回规则 1通过规则（沿用旧 examine_setting 语义）',
  recheck_type      TINYINT NULL COMMENT '撤回后重审：1从头 2从拒绝层',
  pass_type         TINYINT NULL COMMENT '超时自动通过等',
  pass_rule         TINYINT NULL,
  limit_time_type   TINYINT NULL DEFAULT 0,
  limit_time_num    INT    NULL,
  limit_time_unit   TINYINT NULL,
  apply_scope_type  TINYINT NULL DEFAULT 0 COMMENT '0全系统 1指定用户 2指定部门',
  create_user_id    BIGINT NULL,
  update_user_id    BIGINT NULL,
  create_time       DATETIME NOT NULL,
  update_time       DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_setting_def (flow_definition_id),
  KEY idx_flow_setting_sys (system_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义级高级设置';

CREATE TABLE flow_node (
  id                         BIGINT       NOT NULL,
  flow_definition_id         BIGINT       NOT NULL,
  system_id                  BIGINT       NOT NULL,
  tenant_id                  BIGINT       NOT NULL DEFAULT 0,
  module_id                  BIGINT       NULL,
  node_before_id             BIGINT       NULL COMMENT '前驱节点 flow_node.id，0=开始',
  node_after_id              BIGINT       NULL COMMENT '后继节点',
  node_type                  TINYINT      NOT NULL DEFAULT 1 COMMENT '0动态 1审批 2条件 3抄送 4转交等',
  node_sort                  INT          NOT NULL DEFAULT 0,
  node_depth                 VARCHAR(1024) NULL COMMENT '层级路径，逗号分隔',
  examine_type               TINYINT      NULL COMMENT '审批人来源：固定人/上级/角色/自选等',
  examine_flag               TINYINT      NOT NULL DEFAULT 0 COMMENT '多人策略：顺序/无序/或签等',
  examine_end_user_id        BIGINT       NULL,
  condition_expr             JSON         NULL COMMENT '条件节点表达式（原 condition_module_field_search）',
  copy_targets               JSON         NULL COMMENT '抄送配置',
  transfer_flag              TINYINT      NOT NULL DEFAULT 0,
  transfer_user_id           BIGINT       NULL,
  transfer_status            TINYINT      NULL,
  edit_policy                JSON         NULL COMMENT '节点可编辑字段策略（README）',
  connector_config           JSON         NULL COMMENT '出站 Connector：URL、映射、密钥引用等',
  sync_main_data_on_change   TINYINT      NOT NULL DEFAULT 0 COMMENT 'D20：1=字段变更立即同步主数据',
  remarks                    VARCHAR(512) NULL,
  create_user_id             BIGINT       NULL,
  update_user_id             BIGINT       NULL,
  create_time                DATETIME     NOT NULL,
  update_time                DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_flow_node_def (flow_definition_id, node_sort),
  KEY idx_flow_node_system (system_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点模板 flow_node';

CREATE TABLE flow_node_assignee (
  id                 BIGINT NOT NULL,
  flow_node_id       BIGINT NOT NULL,
  flow_definition_id BIGINT NOT NULL,
  system_id          BIGINT NOT NULL,
  tenant_id          BIGINT NOT NULL DEFAULT 0,
  apply_type         TINYINT NOT NULL COMMENT '0用户 1部门 2角色 4邮箱',
  user_id            BIGINT NULL,
  dept_id            BIGINT NULL,
  role_id            BIGINT NULL,
  email              VARCHAR(255) NULL,
  sorting            INT NULL,
  create_time        DATETIME NOT NULL,
  update_time        DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_fna_node (flow_node_id),
  KEY idx_fna_def (flow_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模板节点审批人/候选人（对应 un_examine_node_user）';

-- -----------------------------------------------------------------------------
-- 四、流程运行时 — flow_record / record_node / record_node_user / record_node_after
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS record_node_after;
DROP TABLE IF EXISTS record_node_user;
DROP TABLE IF EXISTS record_node;
DROP TABLE IF EXISTS flow_record;

CREATE TABLE flow_record (
  id                      BIGINT       NOT NULL COMMENT '审批整单ID',
  flow_definition_id      BIGINT       NOT NULL COMMENT '发起时固化 D6',
  system_id               BIGINT       NOT NULL,
  tenant_id               BIGINT       NOT NULL DEFAULT 0,
  module_id               BIGINT       NOT NULL,
  relation_id             BIGINT       NOT NULL COMMENT '业务主表 un_module_record.id',
  record_status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0审批中 1暂停 2通过 3拒绝 4终止 5作废',
  terminal_reason         VARCHAR(64)  NULL COMMENT '终态细分原因',
  sync_main_data_flag     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否开启过程同步主数据（来自定义或节点）',
  remarks                 VARCHAR(512) NULL,
  create_user_id          BIGINT       NULL,
  update_user_id          BIGINT       NULL,
  create_time             DATETIME     NOT NULL,
  update_time             DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_fr_system_tenant (system_id, tenant_id),
  KEY idx_fr_relation (system_id, tenant_id, module_id, relation_id),
  KEY idx_fr_def (flow_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批整单 flow_record';

CREATE TABLE record_node (
  id                       BIGINT       NOT NULL COMMENT '运行时节点实例',
  flow_record_id           BIGINT       NOT NULL,
  flow_definition_id       BIGINT       NOT NULL,
  source_flow_node_id      BIGINT       NULL COMMENT '复制来源 flow_node.id',
  system_id                BIGINT       NOT NULL,
  tenant_id                BIGINT       NOT NULL DEFAULT 0,
  module_id                BIGINT       NULL,
  node_before_id           BIGINT       NULL,
  node_after_id            BIGINT       NULL,
  node_type                TINYINT      NOT NULL,
  node_sort                INT          NOT NULL DEFAULT 0,
  node_depth               VARCHAR(1024) NULL,
  examine_type             TINYINT      NULL,
  examine_flag             TINYINT      NOT NULL DEFAULT 0,
  examine_end_user_id      BIGINT       NULL,
  condition_expr           JSON         NULL,
  copy_targets             JSON         NULL,
  transfer_flag            TINYINT      NOT NULL DEFAULT 0,
  transfer_user_id         BIGINT       NULL,
  transfer_status          TINYINT      NULL,
  edit_policy              JSON         NULL,
  connector_config         JSON         NULL,
  sync_main_data_on_change TINYINT      NOT NULL DEFAULT 0,
  node_status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1已通过 2已驳回 3转交等',
  inserted_flag            TINYINT      NOT NULL DEFAULT 0 COMMENT '1=D17 运行中加签插入',
  remarks                  VARCHAR(512) NULL,
  create_user_id           BIGINT       NULL,
  update_user_id           BIGINT       NULL,
  create_time              DATETIME     NOT NULL,
  update_time              DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rn_record (flow_record_id, node_sort),
  KEY idx_rn_todo (system_id, tenant_id, node_status),
  KEY idx_rn_def (flow_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运行时节点 record_node';

CREATE TABLE record_node_user (
  id                 BIGINT       NOT NULL,
  flow_record_id     BIGINT       NOT NULL,
  record_node_id     BIGINT       NOT NULL,
  flow_definition_id BIGINT       NOT NULL,
  system_id          BIGINT       NOT NULL,
  tenant_id          BIGINT       NOT NULL DEFAULT 0,
  parent_id          BIGINT       NULL,
  depts              VARCHAR(2048) NULL,
  transfer_flag      TINYINT      NOT NULL DEFAULT 0,
  apply_type         TINYINT      NOT NULL,
  user_id            BIGINT       NULL COMMENT 'un_module_user.id',
  dept_id            BIGINT       NULL,
  role_id            BIGINT       NULL,
  email              VARCHAR(255) NULL,
  approval_status    TINYINT      NULL COMMENT '0待处理 1同意 2拒绝等',
  remark             VARCHAR(512) NULL,
  sorting            INT          NULL,
  create_user_id     BIGINT       NULL,
  update_user_id     BIGINT       NULL,
  create_time        DATETIME     NOT NULL,
  update_time        DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_rnu_node (record_node_id),
  KEY idx_rnu_record (flow_record_id),
  KEY idx_rnu_user_todo (system_id, tenant_id, user_id, approval_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点审批人待办 record_node_user';

CREATE TABLE record_node_after (
  id               BIGINT       NOT NULL,
  flow_record_id   BIGINT       NOT NULL,
  record_node_id   BIGINT       NOT NULL,
  system_id        BIGINT       NOT NULL,
  tenant_id        BIGINT       NOT NULL DEFAULT 0,
  submit_user_id   BIGINT       NOT NULL COMMENT '提交人 un_module_user.id',
  payload_json     JSON         NOT NULL COMMENT 'after 快照（字段键值）',
  submit_time      DATETIME     NOT NULL,
  create_time      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_after_node_time (record_node_id, submit_time),
  KEY idx_after_record (flow_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节点 after 快照（D13/D21；或签多条取最后一条时间最大）';

-- -----------------------------------------------------------------------------
-- 五、Outbox（D8）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS outbox_event;

CREATE TABLE outbox_event (
  id            BIGINT       NOT NULL COMMENT 'event_id 幂等消费',
  system_id     BIGINT       NOT NULL,
  tenant_id     BIGINT       NOT NULL DEFAULT 0,
  event_type    VARCHAR(64)  NOT NULL,
  aggregate_type VARCHAR(64) NULL,
  aggregate_id  BIGINT       NULL,
  payload_json  JSON         NOT NULL,
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待投递 1处理中 2成功 3死信',
  retry_count   INT          NOT NULL DEFAULT 0,
  next_retry_at DATETIME     NULL,
  last_error    VARCHAR(1024) NULL,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_outbox_poll (status, next_retry_at, create_time),
  KEY idx_outbox_sys (system_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox 轻量版';

-- -----------------------------------------------------------------------------
-- 六、幂等记录（D11）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS idempotency_record;

CREATE TABLE idempotency_record (
  id                BIGINT       NOT NULL,
  system_id         BIGINT       NOT NULL,
  tenant_id         BIGINT       NOT NULL DEFAULT 0,
  principal         VARCHAR(128) NOT NULL COMMENT '平台用户ID或 open_app.app_id',
  idempotency_key   VARCHAR(128) NOT NULL,
  request_hash      VARCHAR(64)  NULL,
  response_status   INT          NULL COMMENT 'HTTP 状态',
  response_body_json JSON        NULL COMMENT '成功响应缓存（幂等重放）',
  expire_at         DATETIME     NOT NULL,
  create_time       DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotency (system_id, tenant_id, principal, idempotency_key),
  KEY idx_idem_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='写接口幂等（24h TTL 可清）';

-- -----------------------------------------------------------------------------
-- 七、对外开放应用（appId / appKey / appSecret）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS open_app;

CREATE TABLE open_app (
  id               BIGINT       NOT NULL,
  app_id           VARCHAR(64)  NOT NULL COMMENT '对外 appId',
  app_key          VARCHAR(128) NOT NULL,
  app_secret_hash  VARCHAR(255) NOT NULL COMMENT 'secret 摘要存储',
  system_id        BIGINT       NOT NULL,
  tenant_id        BIGINT       NOT NULL DEFAULT 0,
  name             VARCHAR(128) NULL,
  status           TINYINT      NOT NULL DEFAULT 1,
  scope_json       JSON         NULL COMMENT '授权模块/Action 等',
  service_member_id BIGINT      NULL COMMENT '映射 service principal 对应 sys_member.id',
  create_time      DATETIME     NOT NULL,
  update_time      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_open_app_id (app_id),
  KEY idx_open_app_sys (system_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方开放应用凭证';

-- -----------------------------------------------------------------------------
-- 八、消息（站内信；待办由 record_node / record_node_user 查询）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS sys_message;

CREATE TABLE sys_message (
  id              BIGINT       NOT NULL,
  system_id       BIGINT       NOT NULL,
  tenant_id       BIGINT       NOT NULL DEFAULT 0,
  receiver_user_id BIGINT      NOT NULL COMMENT 'un_module_user.id',
  title           VARCHAR(255) NOT NULL,
  content         TEXT         NULL,
  biz_type        VARCHAR(64)  NULL,
  biz_id          BIGINT       NULL,
  read_flag       TINYINT      NOT NULL DEFAULT 0,
  create_time     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_msg_user (system_id, tenant_id, receiver_user_id, read_flag, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户可见消息列表';

-- -----------------------------------------------------------------------------
-- 九、审计（D14 三类分离）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS audit_tech_log;
DROP TABLE IF EXISTS audit_flow_trace;
DROP TABLE IF EXISTS audit_biz_log;

CREATE TABLE audit_biz_log (
  id               BIGINT       NOT NULL,
  system_id        BIGINT       NOT NULL,
  tenant_id        BIGINT       NOT NULL DEFAULT 0,
  plat_account_id  BIGINT       NULL,
  module_user_id   BIGINT       NULL,
  module_id        BIGINT       NULL,
  record_id        BIGINT       NULL,
  action_code      VARCHAR(64)  NOT NULL,
  detail_json      JSON         NULL,
  request_id       VARCHAR(64)  NULL,
  ip               VARCHAR(64)  NULL,
  create_time      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_abiz_record (system_id, tenant_id, module_id, record_id),
  KEY idx_abiz_time (system_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务操作审计';

CREATE TABLE audit_flow_trace (
  id               BIGINT       NOT NULL,
  system_id        BIGINT       NOT NULL,
  tenant_id        BIGINT       NOT NULL DEFAULT 0,
  flow_record_id   BIGINT       NOT NULL,
  record_node_id   BIGINT       NULL,
  actor_user_id    BIGINT       NULL,
  action_type      VARCHAR(64)  NOT NULL COMMENT '提交/转交/驳回/评论等',
  content          TEXT         NULL,
  detail_json      JSON         NULL,
  create_time      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_aflow_fr (flow_record_id),
  KEY idx_aflow_sys (system_id, tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程轨迹';

CREATE TABLE audit_tech_log (
  id               BIGINT       NOT NULL,
  system_id        BIGINT       NULL,
  tenant_id        BIGINT       NULL,
  source           VARCHAR(32)  NOT NULL COMMENT 'outbox/callback/connector/sql',
  level            VARCHAR(16)  NOT NULL DEFAULT 'INFO',
  message          TEXT         NOT NULL,
  detail_json      JSON         NULL,
  request_id       VARCHAR(64)  NULL,
  create_time      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_atech_time (create_time),
  KEY idx_atech_sys (system_id, source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技术排障日志';

-- -----------------------------------------------------------------------------
-- 十、连接器密钥（按系统/租户隔离；配置体在 flow_node.connector_config 引用本表 id）
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS connector_secret;

CREATE TABLE connector_secret (
  id            BIGINT       NOT NULL,
  system_id     BIGINT       NOT NULL,
  tenant_id     BIGINT       NOT NULL DEFAULT 0,
  name          VARCHAR(128) NOT NULL,
  secret_enc    VARBINARY(2048) NOT NULL COMMENT '加密后的密钥',
  key_version   INT          NOT NULL DEFAULT 1,
  status        TINYINT      NOT NULL DEFAULT 1,
  create_time   DATETIME     NOT NULL,
  update_time   DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_cs_sys (system_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出站 Connector 密钥存储';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 附录 A：低代码模块域（doc/module.sql）与 README 对齐说明
-- =============================================================================
-- 1) un_module / un_module_field / un_module_record / un_module_record_data 等
--    仍建议以 doc/module.sql 为基准导入，再执行下列「增量」：
--
--    ALTER TABLE un_module ADD COLUMN system_id BIGINT NULL COMMENT '映射 sys_system.id' AFTER id;
--    -- 将原 company_id 数据刷入 system_id 后，可弃用 company_id 或保留兼容期双写。
--
--    ALTER TABLE un_module_record ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER system_id;
--
-- 2) 槽位列名保持 fieldnum1..5、fielddecimal1..5、fieldtext1..9 等与 module.sql 一致（D3）。
--
-- 3) un_module_record.examine_record_id 建议改为关联 flow_record.id（新表）。
--
-- 附录 B：旧 examine 表与 v3 对应关系
-- =============================================================================
--   un_examine              → flow_definition (+ flow_definition_setting)
--   un_examine_node         → flow_node
--   un_examine_node_user    → flow_node_assignee
--   un_examine_record       → flow_record
--   un_examine_record_node  → record_node
--   un_examine_record_node_user → record_node_user
--   （无独立 after 表）      → record_node_after
-- =============================================================================
