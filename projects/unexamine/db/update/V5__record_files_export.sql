-- 用途：为通用业务记录增加本地文件元数据/引用，并为模块数据导出保存异步任务状态。
-- 已有记录与审批数据不变；新表从空表开始，不删除或重建现有业务表。
CREATE TABLE `un_file_object` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件对象主键',
  `system_id` BIGINT NOT NULL COMMENT '文件所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '文件所属租户主键',
  `storage_key` VARCHAR(80) NOT NULL COMMENT '本地存储中随机生成的文件名',
  `original_name` VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名',
  `content_type` VARCHAR(120) NOT NULL COMMENT '服务端识别的安全文件类型',
  `size_bytes` BIGINT NOT NULL COMMENT '文件实际字节数',
  `sha256_hex` CHAR(64) NOT NULL COMMENT '文件内容的 SHA-256 摘要',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '文件可用或已移除状态',
  `created_by_account_id` BIGINT NOT NULL COMMENT '上传文件的平台账号主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件上传时间',
  `delete_time` DATETIME(3) DEFAULT NULL COMMENT '文件移除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`storage_key`),
  KEY `idx_file_object_scope` (`tenant_id`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地文件内容的元数据与存储定位';

CREATE TABLE `un_file_reference` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件引用主键',
  `file_id` BIGINT NOT NULL COMMENT '被引用的文件对象主键',
  `record_id` BIGINT NOT NULL COMMENT '引用文件的业务记录主键',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '附件加入业务记录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_reference_file` (`file_id`),
  KEY `idx_file_reference_record` (`record_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记录与附件的引用关系';

CREATE TABLE `un_exchange_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块数据导出任务主键',
  `system_id` BIGINT NOT NULL COMMENT '任务所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '任务所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '任务导出的业务模块主键',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '提交任务的平台账号主键',
  `filter_json` JSON NOT NULL COMMENT '提交时的标题和字段筛选条件快照',
  `columns_json` JSON NOT NULL COMMENT '提交时选择的模块字段编码快照',
  `status` VARCHAR(20) NOT NULL DEFAULT 'QUEUED' COMMENT '排队、运行、成功或失败状态',
  `result_key` VARCHAR(80) DEFAULT NULL COMMENT '成功结果在本地存储中的随机文件名',
  `row_count` INT NOT NULL DEFAULT 0 COMMENT '导出成功的数据行数',
  `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '失败时给请求者展示的原因',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务提交时间',
  `start_time` DATETIME(3) DEFAULT NULL COMMENT '任务开始处理时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '任务结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_exchange_job_owner` (`tenant_id`,`requested_by_account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块筛选数据的异步导出任务';
