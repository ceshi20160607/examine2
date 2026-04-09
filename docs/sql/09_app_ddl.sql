-- app（对外开放 API）模块表结构
-- 说明：
-- - 所有表统一使用 un_ 前缀
-- - 所有表包含：create_user_id / update_user_id / create_time / update_time
-- - 作用域隔离：system_id / tenant_id（无多租户时 tenant_id 固定 0）
-- - 不存明文 secret：仅存 secret_hash（建议 BCrypt），并支持凭证轮换

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS un_app_access_log;
DROP TABLE IF EXISTS un_app_ip_whitelist;
DROP TABLE IF EXISTS un_app_client_scope;
DROP TABLE IF EXISTS un_app_client_credential;
DROP TABLE IF EXISTS un_app_client;

-- -----------------------------------------------------------------------------
-- 1) 第三方应用（Client）
-- -----------------------------------------------------------------------------
CREATE TABLE un_app_client (
  id             BIGINT       NOT NULL COMMENT 'clientId',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId；无多租户时固定 0',
  client_code    VARCHAR(64)  NOT NULL COMMENT 'client 编码（同 system 内唯一）',
  client_name    VARCHAR(255) NOT NULL COMMENT 'client 名称',
  contact_name   VARCHAR(64)  NULL COMMENT '联系人（可选）',
  contact_mobile VARCHAR(32)  NULL COMMENT '联系人手机号（可选）',
  contact_email  VARCHAR(128) NULL COMMENT '联系人邮箱（可选）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  remark         VARCHAR(512) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_client_code (system_id, tenant_id, client_code),
  KEY idx_app_client_status (system_id, tenant_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对外 client';

-- -----------------------------------------------------------------------------
-- 2) Client 凭证（支持轮换；不存明文 secret）
-- -----------------------------------------------------------------------------
CREATE TABLE un_app_client_credential (
  id             BIGINT       NOT NULL COMMENT '凭证ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  client_id      BIGINT       NOT NULL COMMENT 'un_app_client.id',
  access_key     VARCHAR(64)  NOT NULL COMMENT 'accessKey（明文，可用于标识 client）',
  secret_hash    VARCHAR(255) NOT NULL COMMENT 'secret 哈希（建议 BCrypt，不存明文）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  expired_time   DATETIME(3)  NULL COMMENT '过期时间（可选）',
  last_used_time DATETIME(3)  NULL COMMENT '最近使用时间（可选）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_ak (system_id, tenant_id, access_key),
  KEY idx_app_cred_client (client_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='client 凭证（AK/SK）';

-- -----------------------------------------------------------------------------
-- 3) Client 授权范围（Scope）
-- -----------------------------------------------------------------------------
CREATE TABLE un_app_client_scope (
  id             BIGINT       NOT NULL COMMENT 'scopeId',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  client_id      BIGINT       NOT NULL COMMENT 'un_app_client.id',
  scope_code     VARCHAR(128) NOT NULL COMMENT '授权范围编码（如 api:module:* / api:upload:read）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_scope (client_id, scope_code),
  KEY idx_app_scope_query (system_id, tenant_id, client_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='client 授权范围';

-- -----------------------------------------------------------------------------
-- 4) IP 白名单（可选）
-- -----------------------------------------------------------------------------
CREATE TABLE un_app_ip_whitelist (
  id             BIGINT       NOT NULL COMMENT '白名单ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  client_id      BIGINT       NOT NULL COMMENT 'un_app_client.id',
  ip_cidr        VARCHAR(64)  NOT NULL COMMENT 'IP/CIDR（如 1.2.3.4 或 1.2.3.0/24）',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用',
  remark         VARCHAR(512) NULL COMMENT '备注',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_ip (client_id, ip_cidr),
  KEY idx_app_ip_query (system_id, tenant_id, client_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='client IP 白名单';

-- -----------------------------------------------------------------------------
-- 5) 对外访问日志（审计/排障）
-- -----------------------------------------------------------------------------
CREATE TABLE un_app_access_log (
  id             BIGINT       NOT NULL COMMENT '访问日志ID',
  system_id      BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId',
  client_id      BIGINT       NULL COMMENT 'clientId（鉴权失败可为空）',
  access_key     VARCHAR(64)  NULL COMMENT 'accessKey（鉴权失败也可记录）',
  request_id     VARCHAR(64)  NULL COMMENT '请求追踪ID（requestId）',
  method         VARCHAR(16)  NOT NULL COMMENT 'HTTP method',
  path           VARCHAR(512) NOT NULL COMMENT '请求路径（不含域名）',
  query_string   VARCHAR(1024) NULL COMMENT 'queryString（可选，截断）',
  ip             VARCHAR(64)  NULL COMMENT '客户端 IP',
  ua             VARCHAR(512) NULL COMMENT 'User-Agent（可选）',
  status_code    INT          NOT NULL COMMENT 'HTTP 状态码',
  biz_code       INT          NULL COMMENT '业务码（ApiResult.code，可选）',
  cost_ms        BIGINT       NULL COMMENT '耗时 ms（可选）',
  error_msg      VARCHAR(1024) NULL COMMENT '错误信息（可选，截断）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId（一般为空）',
  update_user_id BIGINT       NULL COMMENT '更新人 platId（一般为空）',
  create_time    DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time    DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_app_access_time (system_id, tenant_id, create_time),
  KEY idx_app_access_client (client_id, create_time),
  KEY idx_app_access_ak (access_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对外 API 访问日志';

SET FOREIGN_KEY_CHECKS = 1;

