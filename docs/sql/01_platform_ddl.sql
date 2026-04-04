-- 平台域表结构（与 docs/platform-database-tables.md 一致；会话走 Redis，无 plat_session）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS plat_login_log;
DROP TABLE IF EXISTS plat_oper_log;
DROP TABLE IF EXISTS plat_msg;
DROP TABLE IF EXISTS plat_config;
DROP TABLE IF EXISTS plat_account;

CREATE TABLE plat_account (
  id              BIGINT       NOT NULL COMMENT '平台账号主键；逻辑 platId',
  username        VARCHAR(64)  NOT NULL COMMENT '登录名',
  password_hash   VARCHAR(255) NOT NULL,
  password_salt   VARCHAR(64)  NULL,
  mobile          VARCHAR(32)  NULL,
  email           VARCHAR(128) NULL,
  display_name    VARCHAR(128) NULL,
  avatar_url      VARCHAR(512) NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=禁用',
  last_login_time DATETIME(3)  NULL,
  last_login_ip   VARCHAR(64)  NULL,
  create_time     DATETIME(3)  NOT NULL,
  update_time     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_username (username),
  KEY idx_plat_mobile (mobile),
  KEY idx_plat_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台账号';

CREATE TABLE plat_config (
  id             BIGINT        NOT NULL,
  config_key     VARCHAR(128)  NOT NULL,
  config_value   MEDIUMTEXT    NULL,
  value_type     VARCHAR(32)   NOT NULL DEFAULT 'string',
  group_code     VARCHAR(64)   NULL,
  description    VARCHAR(512)  NULL,
  create_time    DATETIME(3)   NOT NULL,
  update_time    DATETIME(3)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_plat_config_key (config_key),
  KEY idx_plat_config_group (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台基础配置';

CREATE TABLE plat_login_log (
  id               BIGINT       NOT NULL,
  plat_account_id  BIGINT       NULL,
  username_attempt VARCHAR(64)  NULL,
  success_flag     TINYINT      NOT NULL COMMENT '1=成功 0=失败',
  fail_reason      VARCHAR(255) NULL,
  ip               VARCHAR(64)  NULL,
  ua               VARCHAR(512) NULL,
  device           VARCHAR(32)  NULL,
  login_time       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_plat_login_time (login_time),
  KEY idx_plat_login_account (plat_account_id, login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台登录日志';

CREATE TABLE plat_oper_log (
  id               BIGINT       NOT NULL,
  plat_account_id  BIGINT       NOT NULL,
  oper_time        DATETIME(3)  NOT NULL,
  module_code      VARCHAR(64)  NULL,
  action_code      VARCHAR(64)  NOT NULL,
  resource_type    VARCHAR(64)  NULL,
  resource_id      VARCHAR(64)  NULL,
  detail_json      JSON         NULL,
  ip               VARCHAR(64)  NULL,
  request_id       VARCHAR(64)  NULL,
  PRIMARY KEY (id),
  KEY idx_plat_oper_time (oper_time),
  KEY idx_plat_oper_account (plat_account_id, oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台操作日志';

CREATE TABLE plat_msg (
  id             BIGINT        NOT NULL,
  msg_type       VARCHAR(32)   NOT NULL,
  title          VARCHAR(255)  NOT NULL,
  content        MEDIUMTEXT    NULL,
  payload_json   JSON          NULL,
  source_type    TINYINT       NOT NULL DEFAULT 1 COMMENT '1=配置 2=运营 3=系统',
  priority       TINYINT       NOT NULL DEFAULT 0,
  publish_time   DATETIME(3)   NULL,
  expire_time    DATETIME(3)   NULL,
  status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1=发布 0=草稿 2=下线',
  create_time    DATETIME(3)   NOT NULL,
  update_time    DATETIME(3)   NOT NULL,
  PRIMARY KEY (id),
  KEY idx_plat_msg_query (msg_type, status, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台级消息';

DROP TABLE IF EXISTS plat_tenant;
DROP TABLE IF EXISTS plat_system;

CREATE TABLE plat_system (
  id                    BIGINT       NOT NULL COMMENT 'systemId；0=平台占位',
  name                  VARCHAR(255) NOT NULL,
  icon_url              VARCHAR(512) NULL,
  multi_tenant_enabled  TINYINT      NOT NULL DEFAULT 0,
  default_tenant_id     BIGINT       NOT NULL DEFAULT 0,
  status                TINYINT      NOT NULL DEFAULT 1,
  owner_plat_account_id BIGINT       NULL,
  create_time           DATETIME(3)  NOT NULL,
  update_time           DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_plat_system_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自建系统/应用';

CREATE TABLE plat_tenant (
  id          BIGINT       NOT NULL COMMENT '租户ID；0=占位',
  system_id   BIGINT       NOT NULL,
  name        VARCHAR(255) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 1,
  create_time DATETIME(3)  NOT NULL,
  update_time DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_plat_tenant_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户';

SET FOREIGN_KEY_CHECKS = 1;
