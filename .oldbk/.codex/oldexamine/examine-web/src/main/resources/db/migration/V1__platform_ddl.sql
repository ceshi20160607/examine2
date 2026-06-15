-- Baseline: platform DDL (converted from docs/sql/01_platform_ddl.sql)
-- Note: migration should be safe; avoid DROP statements.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS un_plat_account (
  id              BIGINT       NOT NULL COMMENT '平台账号主键；逻辑 platId',
  username        VARCHAR(64)  NOT NULL COMMENT '登录名',
  password_hash   VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt 等）',
  password_salt   VARCHAR(64)  NULL COMMENT '盐（如算法自带盐可不使用）',
  mobile          VARCHAR(32)  NULL COMMENT '手机号',
  email           VARCHAR(128) NULL COMMENT '邮箱',
  display_name    VARCHAR(128) NULL COMMENT '展示名/昵称',
  avatar_url      VARCHAR(512) NULL COMMENT '头像地址',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=禁用',
  last_login_time DATETIME(3)  NULL COMMENT '最后登录时间',
  last_login_ip   VARCHAR(64)  NULL COMMENT '最后登录 IP',
  create_user_id  BIGINT       NULL COMMENT '创建人 platId',
  update_user_id  BIGINT       NULL COMMENT '更新人 platId',
  create_time     DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time     DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_username (username),
  KEY idx_plat_mobile (mobile),
  KEY idx_plat_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号';

CREATE TABLE IF NOT EXISTS un_plat_config (
  id             BIGINT        NOT NULL COMMENT '配置ID',
  config_key     VARCHAR(128)  NOT NULL COMMENT '配置键（唯一）',
  config_value   MEDIUMTEXT    NULL COMMENT '配置值（按 value_type 解析）',
  value_type     VARCHAR(32)   NOT NULL DEFAULT 'string' COMMENT '值类型：string|number|bool|json 等',
  group_code     VARCHAR(64)   NULL COMMENT '分组编码',
  description    VARCHAR(512)  NULL COMMENT '说明/备注',
  create_user_id BIGINT        NULL COMMENT '创建人 platId',
  update_user_id BIGINT        NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_config_key (config_key),
  KEY idx_plat_config_group (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台基础配置';

CREATE TABLE IF NOT EXISTS un_plat_login_log (
  id               BIGINT       NOT NULL COMMENT '日志ID',
  plat_account_id  BIGINT       NULL COMMENT 'platId；成功时有值',
  username_attempt VARCHAR(64)  NULL COMMENT '尝试登录的用户名（失败也记录）',
  success_flag     TINYINT      NOT NULL COMMENT '1=成功 0=失败',
  fail_reason      VARCHAR(255) NULL COMMENT '失败原因',
  ip               VARCHAR(64)  NULL COMMENT '登录 IP',
  ua               VARCHAR(512) NULL COMMENT 'User-Agent',
  device           VARCHAR(32)  NULL COMMENT '设备类型（可选）',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId（一般同 plat_account_id；失败可为空）',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId（一般为空）',
  login_time       DATETIME(3)  NOT NULL COMMENT '登录时间',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_plat_login_time (login_time),
  KEY idx_plat_login_account (plat_account_id, login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台登录日志';

CREATE TABLE IF NOT EXISTS un_plat_oper_log (
  id               BIGINT       NOT NULL COMMENT '日志ID',
  plat_account_id  BIGINT       NOT NULL COMMENT '操作人 platId',
  oper_time        DATETIME(3)  NOT NULL COMMENT '操作时间',
  module_code      VARCHAR(64)  NULL COMMENT '模块编码（如 platform/upload/module/flow）',
  action_code      VARCHAR(64)  NOT NULL COMMENT '动作编码（建议 method+path 或业务动作码）',
  resource_type    VARCHAR(64)  NULL COMMENT '资源类型（可选）',
  resource_id      VARCHAR(64)  NULL COMMENT '资源标识（可选）',
  detail_json      JSON         NULL COMMENT '操作详情（可选，JSON）',
  ip               VARCHAR(64)  NULL COMMENT '客户端 IP',
  request_id       VARCHAR(64)  NULL COMMENT '请求追踪ID（requestId）',
  create_user_id   BIGINT       NULL COMMENT '创建人 platId（一般同 plat_account_id）',
  update_user_id   BIGINT       NULL COMMENT '更新人 platId（一般为空）',
  create_time      DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_plat_oper_time (oper_time),
  KEY idx_plat_oper_account (plat_account_id, oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台操作日志';

CREATE TABLE IF NOT EXISTS un_plat_msg (
  id             BIGINT        NOT NULL COMMENT '消息ID',
  msg_type       VARCHAR(32)   NOT NULL COMMENT '消息类型（如 notice/tip/warn/...）',
  title          VARCHAR(255)  NOT NULL COMMENT '标题',
  content        MEDIUMTEXT    NULL COMMENT '正文内容',
  payload_json   JSON          NULL COMMENT '扩展负载（JSON，可选）',
  source_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '1=配置 2=运营 3=系统',
  priority       TINYINT       NOT NULL DEFAULT 0 COMMENT '优先级（数值越大越高）',
  publish_time   DATETIME(3)   NULL COMMENT '发布时间',
  expire_time    DATETIME(3)   NULL COMMENT '过期时间',
  status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1=发布 0=草稿 2=下线',
  create_user_id BIGINT        NULL COMMENT '创建人 platId',
  update_user_id BIGINT        NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)   NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_plat_msg_query (msg_type, status, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台级消息';

CREATE TABLE IF NOT EXISTS un_plat_system (
  id                    BIGINT       NOT NULL COMMENT 'systemId；0=平台占位',
  name                  VARCHAR(255) NOT NULL COMMENT '系统名称',
  icon_url              VARCHAR(512) NULL COMMENT '图标 URL',
  multi_tenant_enabled  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用多租户：0=否 1=是',
  default_tenant_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '默认 tenantId',
  status                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  owner_plat_account_id BIGINT       NULL COMMENT '创建/所有者 platId',
  create_user_id        BIGINT       NULL COMMENT '创建人 platId',
  update_user_id        BIGINT       NULL COMMENT '更新人 platId',
  create_time           DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time           DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_plat_system_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自建系统/应用';

CREATE TABLE IF NOT EXISTS un_plat_tenant (
  id          BIGINT       NOT NULL COMMENT '租户ID；0=占位',
  system_id   BIGINT       NOT NULL COMMENT '所属 systemId',
  name        VARCHAR(255) NOT NULL COMMENT '租户名称',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT    NULL COMMENT '创建人 platId',
  update_user_id BIGINT    NULL COMMENT '更新人 platId',
  create_time DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_plat_tenant_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户';

SET FOREIGN_KEY_CHECKS = 1;

