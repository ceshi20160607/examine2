-- 用途：保存模块 Excel 导入的预检查、确认和错误行历史；不改写现有业务记录或字段结构。
-- 旧记录无需回填；导入仅从已发布字段模板进入，确认阶段再由原业务服务按权限写入。
CREATE TABLE `un_exchange_import_job` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '模块数据导入任务主键',
  `system_id` BIGINT NOT NULL COMMENT '导入任务所属业务系统主键',
  `tenant_id` BIGINT NOT NULL COMMENT '导入任务所属租户主键',
  `module_id` BIGINT NOT NULL COMMENT '目标业务模块主键',
  `module_version_id` BIGINT NOT NULL COMMENT '预检查时的模块发布版本主键',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '提交导入的平台账号主键',
  `mode` VARCHAR(20) NOT NULL COMMENT '只新增或按记录编号匹配更新的导入模式',
  `source_key` VARCHAR(80) NOT NULL COMMENT '本地存储中随机生成的源 Excel 文件名',
  `source_name` VARCHAR(255) NOT NULL COMMENT '用户上传的原始 Excel 文件名',
  `source_sha256` CHAR(64) NOT NULL COMMENT '预检查文件内容摘要',
  `preview_json` JSON NOT NULL COMMENT '逐行预检查结果和错误原因快照',
  `status` VARCHAR(20) NOT NULL COMMENT '已预检查、含错误、已导入或失败状态',
  `total_rows` INT NOT NULL DEFAULT 0 COMMENT 'Excel 中待导入的数据行数',
  `valid_rows` INT NOT NULL DEFAULT 0 COMMENT '预检查通过的数据行数',
  `imported_rows` INT NOT NULL DEFAULT 0 COMMENT '确认后成功落库的数据行数',
  `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '确认失败时显示给提交人的原因',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文件上传和预检查时间',
  `finish_time` DATETIME(3) DEFAULT NULL COMMENT '确认导入或失败时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_import_source` (`source_key`),
  KEY `idx_exchange_import_owner` (`tenant_id`,`requested_by_account_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块 Excel 导入的预检查、确认和错误行历史';
