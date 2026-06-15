-- Baseline: upload DDL (converted from docs/sql/03_upload_ddl.sql)
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS un_upload_file (
  id               BIGINT        NOT NULL COMMENT '文件ID（雪花/分布式ID）',
  system_id        BIGINT        NOT NULL COMMENT 'systemId',
  tenant_id        BIGINT        NOT NULL DEFAULT 0 COMMENT 'tenantId；无多租户时固定 0',
  uploader_plat_id BIGINT        NULL COMMENT '上传人 platId（可选）',
  create_user_id   BIGINT        NULL COMMENT '创建人 platId（一般同 uploader_plat_id）',
  update_user_id   BIGINT        NULL COMMENT '更新人 platId',

  original_name    VARCHAR(255)  NOT NULL COMMENT '原始文件名',
  file_ext         VARCHAR(32)   NULL COMMENT '扩展名（不含点）',
  content_type     VARCHAR(128)  NULL COMMENT 'MIME',
  file_size        BIGINT        NOT NULL COMMENT '字节数',

  sha256           CHAR(64)      NULL COMMENT '内容摘要（可选，用于秒传/去重/完整性校验）',

  storage_type     VARCHAR(16)   NOT NULL COMMENT 'local|minio|oss（可扩展）',
  storage_config_id BIGINT       NULL COMMENT '存储配置ID（un_upload_storage_config.id，可为空表示默认配置）',
  bucket           VARCHAR(128)  NULL COMMENT 'bucket（对象存储）',
  object_key       VARCHAR(512)  NULL COMMENT '对象Key/路径（对象存储）',
  local_abs_path   VARCHAR(1024) NULL COMMENT '本地物理路径（storage_type=local 时使用）',
  public_url       VARCHAR(1024) NULL COMMENT '外网可访问地址（可为空，按配置/签名动态生成）',
  internal_url     VARCHAR(1024) NULL COMMENT '内网访问地址（可选）',

  status           TINYINT       NOT NULL DEFAULT 1 COMMENT '1=可用 2=删除/禁用 3=上传中',
  create_time      DATETIME(3)   NOT NULL COMMENT '创建时间',
  update_time      DATETIME(3)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_upload_scope_time (system_id, tenant_id, create_time),
  KEY idx_upload_sha256 (sha256),
  KEY idx_upload_uploader (uploader_plat_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传文件主表';

CREATE TABLE IF NOT EXISTS un_upload_file_part (
  id              BIGINT       NOT NULL COMMENT '分片ID',
  system_id       BIGINT       NOT NULL COMMENT 'systemId',
  tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT 'tenantId；无多租户时固定 0',
  upload_file_id  BIGINT       NOT NULL COMMENT 'un_upload_file.id',
  create_user_id  BIGINT       NULL COMMENT '创建人 platId',
  update_user_id  BIGINT       NULL COMMENT '更新人 platId',

  upload_session  VARCHAR(64)  NOT NULL COMMENT '一次分片上传会话ID',
  part_no         INT          NOT NULL COMMENT '分片序号，从 1 开始',
  part_size       BIGINT       NOT NULL COMMENT '分片大小（字节）',
  etag            VARCHAR(128) NULL COMMENT '对象存储返回的 etag（可选）',
  sha256          CHAR(64)     NULL COMMENT '分片摘要（可选）',

  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已上传 2=待上传 3=已合并/归档',
  create_time     DATETIME(3)  NOT NULL COMMENT '创建时间',
  update_time     DATETIME(3)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_part (upload_file_id, upload_session, part_no),
  KEY idx_part_scope (system_id, tenant_id, upload_file_id),
  KEY idx_part_session (upload_session)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传分片表';

CREATE TABLE IF NOT EXISTS un_upload_storage_config (
  id            BIGINT        NOT NULL COMMENT '存储配置ID',
  system_id     BIGINT        NOT NULL COMMENT 'systemId',
  tenant_id     BIGINT        NOT NULL DEFAULT 0 COMMENT 'tenantId；无多租户时固定 0',
  storage_type  VARCHAR(16)   NOT NULL COMMENT 'local|minio|oss（可扩展）',
  create_user_id BIGINT       NULL COMMENT '创建人 platId',
  update_user_id BIGINT       NULL COMMENT '更新人 platId',

  local_root_path VARCHAR(512) NULL COMMENT '本地存储根目录（storage_type=local）',
  local_public_base_url VARCHAR(512) NULL COMMENT '本地文件对外访问域名/前缀（可选）',

  endpoint      VARCHAR(255)  NULL COMMENT 'S3/OSS endpoint（local 可为空）',
  region        VARCHAR(64)   NULL COMMENT 'region（可选）',
  bucket        VARCHAR(128)  NULL COMMENT '默认 bucket（对象存储）',
  base_path     VARCHAR(512)  NULL COMMENT '基础路径前缀（对象存储 key 前缀）',
  public_base_url VARCHAR(512) NULL COMMENT '公开访问域名（可选）',
  param_json    JSON          NULL COMMENT '扩展参数（如 ACL、是否私有、签名有效期秒数等）',

  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 2=停用',
  remark        VARCHAR(512)  NULL COMMENT '备注',
  create_time   DATETIME(3)   NOT NULL COMMENT '创建时间',
  update_time   DATETIME(3)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_storage_scope (system_id, tenant_id, storage_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传存储配置（不含密钥）';

SET FOREIGN_KEY_CHECKS = 1;

