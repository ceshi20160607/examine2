-- 公共文件、公告和 AI 配置；所属作用域由业务 Service 校验，不设置数据库外键。
CREATE TABLE IF NOT EXISTS un_common_file (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件元数据主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统作用域的业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统作用域的租户主键',
  uploader_account_id BIGINT NOT NULL COMMENT '实际上传平台账号主键',
  original_name VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
  content_type VARCHAR(120) NOT NULL COMMENT '文件内容类型',
  size_bytes BIGINT NOT NULL COMMENT '文件字节数',
  sha256 CHAR(64) DEFAULT NULL COMMENT '文件内容摘要',
  storage_type VARCHAR(30) NOT NULL COMMENT '本地或对象存储类型',
  storage_key VARCHAR(500) NOT NULL COMMENT '存储对象键，不向普通接口直接暴露',
  preview_key VARCHAR(500) DEFAULT NULL COMMENT '预览或缩略图对象键',
  upload_json JSON DEFAULT NULL COMMENT '分片上传会话与合并校验信息',
  status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT '上传中可用或待清理状态',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件登记时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近状态更新时间',
  PRIMARY KEY (id), KEY ix_common_file_scope (scope_type, system_id, tenant_id, status),
  KEY ix_common_file_hash (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共文件元数据';

CREATE TABLE IF NOT EXISTS un_common_file_ref (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件业务引用主键',
  file_id BIGINT NOT NULL COMMENT '被引用公共文件元数据主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统引用作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统业务引用所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统业务引用所属租户主键',
  object_type VARCHAR(50) NOT NULL COMMENT '模块记录任务工作日志等对象类型',
  object_id BIGINT NOT NULL COMMENT '业务对象主键',
  field_id BIGINT DEFAULT NULL COMMENT '模块字段引用时的字段主键',
  create_account_id BIGINT NOT NULL COMMENT '建立引用的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '建立引用时间',
  PRIMARY KEY (id), KEY ix_common_file_ref_file (file_id),
  KEY ix_common_file_ref_object (scope_type, system_id, tenant_id, object_type, object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件与多个业务对象的引用关系';

CREATE TABLE IF NOT EXISTS un_common_notice (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统公告作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统公告所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户专属公告的租户主键',
  title VARCHAR(200) NOT NULL COMMENT '公告标题',
  content TEXT NOT NULL COMMENT '公告正文',
  audience_json JSON DEFAULT NULL COMMENT '角色部门或成员可见范围',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  publish_time DATETIME(3) DEFAULT NULL COMMENT '正式发布时间',
  expire_time DATETIME(3) DEFAULT NULL COMMENT '公告失效时间',
  create_account_id BIGINT NOT NULL COMMENT '创建公告的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_common_notice_scope (scope_type, system_id, tenant_id, status, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共公告';

CREATE TABLE IF NOT EXISTS un_common_ai (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '智能能力配置主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台或系统作用域',
  system_id BIGINT DEFAULT NULL COMMENT '系统智能配置所属系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '租户专属智能配置主键',
  code VARCHAR(80) NOT NULL COMMENT '作用域内智能能力编码',
  name VARCHAR(100) NOT NULL COMMENT '智能能力名称',
  provider_code VARCHAR(80) NOT NULL COMMENT '模型提供方编码',
  secret_ref VARCHAR(255) DEFAULT NULL COMMENT '模型凭据安全引用',
  config_json JSON DEFAULT NULL COMMENT '模型参数能力范围与人工确认策略',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布配置版本',
  published_config_json JSON DEFAULT NULL COMMENT '运行态智能能力配置快照',
  create_account_id BIGINT NOT NULL COMMENT '创建配置的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), KEY ix_common_ai_scope (scope_type, system_id, tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共智能能力配置';
