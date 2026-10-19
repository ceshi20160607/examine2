-- 用途：所有用户自定义模块共用主记录、字段原值和类型化查询索引；不按模块动态生成物理表。
CREATE TABLE un_biz_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通用业务记录标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '记录原始所属租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务模块标识',
  module_version_no INT NOT NULL COMMENT '创建或最近验证时使用的模块发布版本号',
  record_no VARCHAR(80) NOT NULL COMMENT '模块租户内唯一业务编号',
  title VARCHAR(255) NOT NULL COMMENT '业务记录标题',
  business_status VARCHAR(64) NOT NULL DEFAULT 'draft' COMMENT '业务状态编码',
  owner_member_id BIGINT UNSIGNED NOT NULL COMMENT '当前负责人系统成员标识',
  owner_department_id BIGINT UNSIGNED NULL COMMENT '当前负责部门标识，组织启用后使用',
  related_member_ids_json TEXT NULL COMMENT '相关人员系统成员标识的 JSON 文本',
  created_by_member_id BIGINT UNSIGNED NOT NULL COMMENT '创建人系统成员标识',
  updated_by_member_id BIGINT UNSIGNED NULL COMMENT '最近修改人系统成员标识',
  archived_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否归档：1是，0否',
  deleted_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否逻辑删除：1是，0否',
  version_no BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time DATETIME(3) NOT NULL COMMENT '业务记录创建时间',
  update_time DATETIME(3) NOT NULL COMMENT '业务记录修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_record_no (tenant_id, module_id, record_no),
  KEY idx_biz_record_list (system_id, tenant_id, module_id, deleted_flag, update_time),
  KEY idx_biz_record_owner (system_id, tenant_id, module_id, owner_member_id, deleted_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自定义模块通用业务主记录';

CREATE TABLE un_biz_record_data (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录字段原值标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '记录原始所属租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务模块标识',
  record_id BIGINT UNSIGNED NOT NULL COMMENT '关联通用业务主记录标识',
  field_id BIGINT UNSIGNED NOT NULL COMMENT '发布字段标识',
  field_code VARCHAR(64) NOT NULL COMMENT '写入时字段稳定编码',
  value_json LONGTEXT NULL COMMENT '字段原值的 JSON 文本，按发布类型校验',
  update_time DATETIME(3) NOT NULL COMMENT '字段值最近修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_record_data_field (record_id, field_id),
  KEY idx_biz_record_data_scope (system_id, tenant_id, module_id, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用业务记录的动态字段原值';

CREATE TABLE un_biz_record_index (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录查询索引标识',
  system_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务系统标识',
  tenant_id BIGINT UNSIGNED NOT NULL COMMENT '记录原始所属租户标识',
  module_id BIGINT UNSIGNED NOT NULL COMMENT '记录所属业务模块标识',
  record_id BIGINT UNSIGNED NOT NULL COMMENT '关联通用业务主记录标识',
  field_id BIGINT UNSIGNED NOT NULL COMMENT '索引对应的发布字段标识',
  value_text VARCHAR(512) NULL COMMENT '文本型筛选和排序值',
  value_number DECIMAL(24, 6) NULL COMMENT '数值型筛选和排序值',
  value_time DATETIME(3) NULL COMMENT '日期时间型筛选和排序值',
  value_boolean TINYINT NULL COMMENT '布尔型筛选值',
  update_time DATETIME(3) NOT NULL COMMENT '索引值最近重建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_record_index_field (record_id, field_id),
  KEY idx_biz_record_index_text (system_id, tenant_id, module_id, field_id, value_text(100)),
  KEY idx_biz_record_index_number (system_id, tenant_id, module_id, field_id, value_number),
  KEY idx_biz_record_index_time (system_id, tenant_id, module_id, field_id, value_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可由字段原值重建的类型化查询索引';
