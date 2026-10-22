-- 应用是平台或系统能力的受控调用方；授权范围随发布快照生效。
CREATE TABLE IF NOT EXISTS un_app (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '受控应用主键',
  scope_type VARCHAR(20) NOT NULL COMMENT '平台应用或系统应用',
  system_id BIGINT DEFAULT NULL COMMENT '系统应用所属业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '系统应用所属租户主键',
  app_id VARCHAR(100) NOT NULL COMMENT '对外稳定应用标识',
  name VARCHAR(120) NOT NULL COMMENT '应用名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '应用业务说明',
  icon VARCHAR(255) DEFAULT NULL COMMENT '应用展示图标',
  access_mode VARCHAR(30) NOT NULL COMMENT '内部外部或同时允许的访问模式',
  secret_ref VARCHAR(255) NOT NULL COMMENT '当前密钥安全引用',
  secret_version INT NOT NULL DEFAULT 1 COMMENT '当前密钥版本',
  previous_secret_ref VARCHAR(255) DEFAULT NULL COMMENT '轮换过渡期旧密钥安全引用',
  previous_secret_expire_time DATETIME(3) DEFAULT NULL COMMENT '轮换过渡期旧密钥失效时间',
  callback_url VARCHAR(500) DEFAULT NULL COMMENT '应用回调地址',
  config_json JSON NOT NULL COMMENT '草稿资源动作字段数据范围及访问控制配置',
  published_config_json JSON DEFAULT NULL COMMENT '已发布的应用授权和访问控制快照',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_version INT NOT NULL DEFAULT 0 COMMENT '已发布应用配置版本',
  create_account_id BIGINT NOT NULL COMMENT '创建应用的平台账号主键',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_app_identifier (app_id),
  KEY ix_app_scope (scope_type, system_id, tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台或系统受控访问应用';

CREATE TABLE IF NOT EXISTS un_app_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用事件配置主键',
  application_id BIGINT NOT NULL COMMENT '所属受控应用主键',
  event_code VARCHAR(100) NOT NULL COMMENT '稳定业务事件编码',
  resource_type VARCHAR(40) NOT NULL COMMENT '事件所属模块或流程资源类型',
  resource_id BIGINT DEFAULT NULL COMMENT '被订阅的具体资源主键',
  callback_url VARCHAR(500) DEFAULT NULL COMMENT '该事件覆盖应用默认回调的地址',
  config_json JSON DEFAULT NULL COMMENT '签名重试过滤和推送配置',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿发布或停用状态',
  published_config_json JSON DEFAULT NULL COMMENT '已发布事件执行配置快照',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id), UNIQUE KEY uk_app_event (application_id, event_code, resource_type, resource_id),
  KEY ix_app_event_resource (resource_type, resource_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受控应用事件配置';

-- 调用请求标识在应用内唯一，可同时用作重试幂等记录与可查询审计。
CREATE TABLE IF NOT EXISTS un_app_call (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用调用审计主键',
  application_id BIGINT NOT NULL COMMENT '实际调用的受控应用主键',
  credential_version INT NOT NULL COMMENT '通过验证的应用密钥版本',
  system_id BIGINT DEFAULT NULL COMMENT '目标业务系统主键',
  tenant_id BIGINT DEFAULT NULL COMMENT '目标业务租户主键',
  resource_type VARCHAR(40) NOT NULL COMMENT '调用模块流程或文件资源类型',
  resource_id BIGINT DEFAULT NULL COMMENT '调用资源主键',
  action_code VARCHAR(80) NOT NULL COMMENT '本次执行的独立授权动作',
  request_id VARCHAR(100) NOT NULL COMMENT '调用方提交的稳定幂等请求标识',
  request_hash CHAR(64) NOT NULL COMMENT '请求内容摘要，用于拒绝同键异参',
  result_code VARCHAR(50) NOT NULL COMMENT '成功拒绝或失败结果编码',
  response_json JSON DEFAULT NULL COMMENT '幂等重试需要的非敏感结果摘要',
  permission_json JSON DEFAULT NULL COMMENT '实际生效资源字段及数据范围快照',
  duration_ms INT DEFAULT NULL COMMENT '接口处理耗时毫秒',
  caller_ip VARCHAR(45) DEFAULT NULL COMMENT '调用方网络地址',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用发生时间',
  PRIMARY KEY (id), UNIQUE KEY uk_app_call_request (application_id, request_id),
  KEY ix_app_call_scope (system_id, tenant_id, create_time),
  KEY ix_app_call_result (application_id, result_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用调用幂等与审计记录';
