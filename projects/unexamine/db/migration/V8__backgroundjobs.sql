-- backgroundjobs 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_job_background` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '后台作业业务申请主键',
  `context_type` varchar(32) NOT NULL COMMENT '申请层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统层申请所属系统主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '系统层申请所属租户主键，可为空表示系统公共作业',
  `job_type` varchar(100) NOT NULL COMMENT '作业业务类型，如导入、导出、打印或文件处理',
  `source_type` varchar(100) NOT NULL COMMENT '发起作业的业务对象类型',
  `source_id` varchar(100) DEFAULT NULL COMMENT '发起作业的业务对象标识',
  `parameter_json` json NOT NULL COMMENT '提交时固定的业务参数，不包含运行队列状态',
  `authorization_snapshot_json` json NOT NULL COMMENT '申请时固定的权限与数据范围快照',
  `final_status` varchar(32) DEFAULT NULL COMMENT '完成后写入的最终审计状态，成功、带警告成功、失败或取消',
  `result_summary_json` json DEFAULT NULL COMMENT '最终结果、结果文件引用及统计摘要',
  `error_code` varchar(100) DEFAULT NULL COMMENT '最终失败错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '最终失败原因摘要',
  `created_by_account_id` bigint NOT NULL COMMENT '发起申请的平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务申请时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '最终结果归档时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '业务审计记录更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '业务审计记录乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_job_background_context` (`platform_id`,`system_id`,`tenant_id`,`job_type`,`create_time`),
  KEY `idx_job_background_final` (`final_status`,`finish_time`),
  CONSTRAINT `chk_job_background_final` CHECK ((((`final_status` is null) and (`finish_time` is null) and (`result_summary_json` is null) and (`error_code` is null) and (`error_message` is null)) or ((`final_status` is not null) and (`final_status` in (_utf8mb4'SUCCEEDED',_utf8mb4'SUCCEEDED_WITH_WARNINGS',_utf8mb4'FAILED',_utf8mb4'CANCELLED')) and (`finish_time` is not null)))),
  CONSTRAINT `chk_job_background_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台作业业务申请与最终审计；运行状态和重试事实保存在 Redis';

CREATE TABLE `un_job_item_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '作业逐项结果主键',
  `job_id` bigint NOT NULL COMMENT '所属后台作业申请主键',
  `item_key` varchar(255) NOT NULL COMMENT '作业内稳定的业务项标识，用于幂等写入',
  `row_number` bigint DEFAULT NULL COMMENT '导入导出等行处理场景的原始行号',
  `status` varchar(32) NOT NULL COMMENT '该业务项的最终处理状态',
  `result_json` json DEFAULT NULL COMMENT '该业务项的结果数据或文件引用',
  `error_code` varchar(100) DEFAULT NULL COMMENT '该业务项的失败错误码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '该业务项的失败原因摘要',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '逐项结果首次记录时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '逐项结果更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_item_result` (`job_id`,`item_key`),
  KEY `idx_job_item_status` (`job_id`,`status`,`row_number`),
  CONSTRAINT `chk_job_item_result_status` CHECK ((`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'FAILED',_utf8mb4'SKIPPED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台作业逐项业务结果，不保存运行尝试记录';
