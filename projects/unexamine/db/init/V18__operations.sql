-- operations 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_ops_secret_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '受控密钥引用主键',
  `context_type` varchar(32) NOT NULL COMMENT '密钥归属层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统密钥所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '稳定归属主键，平台密钥为平台主键，系统密钥为系统主键',
  `secret_code` varchar(255) NOT NULL COMMENT '同一归属内稳定的密钥引用编码',
  `provider` varchar(64) NOT NULL COMMENT '受控密钥服务提供方',
  `reference_path` varchar(1000) NOT NULL COMMENT '受控密钥服务中的引用路径，不是密钥明文',
  `current_version` varchar(100) NOT NULL COMMENT '当前启用的密钥版本标识',
  `status` varchar(32) NOT NULL COMMENT '密钥引用的启停状态',
  `last_verified_time` datetime(3) DEFAULT NULL COMMENT '最近一次验证密钥可用性的时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '密钥引用创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '密钥引用更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '密钥引用乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_secret_ref` (`context_type`,`scope_owner_id`,`secret_code`),
  KEY `idx_ops_secret_scope_id` (`id`,`context_type`,`scope_owner_id`),
  KEY `idx_ops_secret_scope_code_id` (`id`,`context_type`,`scope_owner_id`,`secret_code`),
  CONSTRAINT `chk_ops_secret_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控密钥引用及当前版本，不保存密钥明文';

CREATE TABLE `un_ops_secret_rotation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '密钥轮换任务主键',
  `secret_ref_id` bigint NOT NULL COMMENT '被轮换的受控密钥引用主键',
  `from_version` varchar(100) NOT NULL COMMENT '轮换前密钥版本',
  `to_version` varchar(100) NOT NULL COMMENT '待切换到的密钥版本',
  `status` varchar(32) NOT NULL COMMENT '新版本验证、切换、旧版停用或回滚状态',
  `verification_json` json DEFAULT NULL COMMENT '双版本验证与失败回滚的审计结果',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起轮换的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '轮换开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '轮换结束或回滚完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '轮换申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '轮换任务乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_secret_rotation` (`secret_ref_id`,`create_time`),
  CONSTRAINT `chk_ops_secret_rotation_versions` CHECK ((`from_version` <> `to_version`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控密钥版本轮换与验证审计';

CREATE TABLE `un_ops_health_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '健康检查运行主键',
  `context_type` varchar(32) NOT NULL COMMENT '检查层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统级检查所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `run_type` varchar(32) NOT NULL COMMENT '健康检查类型',
  `release_version` varchar(100) DEFAULT NULL COMMENT '检查时运行的产品版本',
  `status` varchar(32) NOT NULL COMMENT '健康检查结果状态',
  `summary_json` json DEFAULT NULL COMMENT '检查汇总和处置建议',
  `started_by_account_id` bigint NOT NULL COMMENT '发起检查的平台账号主键',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '健康检查开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '健康检查结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_health_context` (`context_type`,`scope_owner_id`,`status`,`start_time`),
  CONSTRAINT `chk_ops_health_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或系统健康检查运行及结果汇总';

CREATE TABLE `un_ops_health_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '健康检查项目主键',
  `health_run_id` bigint NOT NULL COMMENT '所属健康检查运行主键',
  `check_code` varchar(100) NOT NULL COMMENT '检查项稳定编码',
  `check_name` varchar(200) NOT NULL COMMENT '检查项显示名称',
  `category` varchar(64) NOT NULL COMMENT '检查项业务类别',
  `status` varchar(32) NOT NULL COMMENT '检查项结果状态',
  `message` varchar(2000) DEFAULT NULL COMMENT '结果说明和修复建议',
  `metric_json` json DEFAULT NULL COMMENT '检查项数值与证据',
  `check_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '检查项完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_health_item` (`health_run_id`,`check_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康检查逐项结果';

CREATE TABLE `un_ops_release` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品发布版本主键',
  `version_name` varchar(100) NOT NULL COMMENT '全局唯一的产品发布版本号',
  `artifact_hash` varchar(64) NOT NULL COMMENT '部署产物校验摘要',
  `database_version` varchar(100) NOT NULL COMMENT '数据库结构版本',
  `config_version` varchar(100) NOT NULL COMMENT '配置结构版本',
  `compatibility_json` json NOT NULL COMMENT '升级兼容性及环境要求',
  `release_notes` text NOT NULL COMMENT '发布内容和升级说明',
  `status` varchar(32) NOT NULL COMMENT '发布版本状态',
  `created_by_account_id` bigint NOT NULL COMMENT '登记版本的平台账号主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布版本登记时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_release_version` (`version_name`),
  UNIQUE KEY `uk_ops_release_hash` (`artifact_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局部署产物及兼容性版本目录';

CREATE TABLE `un_ops_deployment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '环境部署记录主键',
  `release_id` bigint NOT NULL COMMENT '目标产品发布版本主键',
  `environment_code` varchar(100) NOT NULL COMMENT '开发、测试或生产等环境编码',
  `deployment_type` varchar(32) NOT NULL COMMENT '部署或回滚操作类型',
  `from_release_id` bigint DEFAULT NULL COMMENT '部署前产品发布版本主键',
  `status` varchar(32) NOT NULL COMMENT '部署执行结果状态',
  `step_state_json` json NOT NULL COMMENT '部署步骤及校验结果',
  `rollback_point_json` json DEFAULT NULL COMMENT '可恢复的回滚点信息',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起部署的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '部署开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '部署结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '部署申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '部署记录乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_deployment_env` (`environment_code`,`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多环境部署、验证及回滚证据';

CREATE TABLE `un_ops_backup` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '备份任务主键',
  `context_type` varchar(32) NOT NULL COMMENT '备份层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统备份所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `backup_type` varchar(32) NOT NULL COMMENT '数据库、文件、配置或密钥策略等备份类型',
  `status` varchar(32) NOT NULL COMMENT '备份执行结果状态',
  `manifest_hash` varchar(64) DEFAULT NULL COMMENT '备份清单校验摘要',
  `encryption_secret_ref_id` bigint NOT NULL COMMENT '同范围备份加密密钥引用主键，不保存密钥明文',
  `retention_until_time` datetime(3) NOT NULL COMMENT '备份保留截止时间',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起备份的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '备份开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '备份结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '备份记录乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_backup_scope_id` (`id`,`context_type`,`scope_owner_id`),
  KEY `idx_ops_backup_context` (`context_type`,`scope_owner_id`,`status`,`create_time`),
  CONSTRAINT `chk_ops_backup_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分范围备份任务及加密密钥引用';

CREATE TABLE `un_ops_backup_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '备份内容项主键',
  `backup_id` bigint NOT NULL COMMENT '所属备份任务主键',
  `item_type` varchar(32) NOT NULL COMMENT '数据库、文件、配置或模板等内容类别',
  `storage_uri` varchar(2000) NOT NULL COMMENT '备份内容的受控存储位置',
  `size_bytes` bigint NOT NULL COMMENT '备份内容字节大小',
  `sha256` varchar(64) NOT NULL COMMENT '备份内容 SHA-256 校验摘要',
  `status` varchar(32) NOT NULL COMMENT '备份内容校验状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份内容登记时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_backup_item` (`backup_id`,`item_type`,`storage_uri`(191)),
  CONSTRAINT `chk_ops_backup_item_size` CHECK ((`size_bytes` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备份清单内容及完整性校验';

CREATE TABLE `un_ops_restore_drill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '恢复演练主键',
  `backup_id` bigint NOT NULL COMMENT '被验证备份任务主键',
  `environment_code` varchar(100) NOT NULL COMMENT '隔离恢复环境编码',
  `status` varchar(32) NOT NULL COMMENT '恢复演练执行结果状态',
  `verification_json` json DEFAULT NULL COMMENT '恢复完整性及业务验收结果',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起演练的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '恢复演练开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '恢复演练结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '恢复演练申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '恢复演练乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_restore_backup` (`backup_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备份恢复演练及可恢复性证据';

CREATE TABLE `un_ops_upgrade` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品升级任务主键',
  `release_id` bigint NOT NULL COMMENT '升级目标发布版本主键',
  `backup_id` bigint NOT NULL COMMENT '升级前同范围备份任务主键',
  `context_type` varchar(32) NOT NULL COMMENT '升级层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统级升级所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `impact_report_json` json NOT NULL COMMENT '升级前模块、权限、数据和兼容性影响报告',
  `status` varchar(32) NOT NULL COMMENT '升级执行结果状态',
  `rollback_point_json` json NOT NULL COMMENT '失败时可恢复的回滚点及处置方案',
  `requested_by_account_id` bigint NOT NULL COMMENT '发起升级的平台账号主键',
  `start_time` datetime(3) DEFAULT NULL COMMENT '升级开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '升级结束时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '升级申请时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '升级任务乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_ops_upgrade_status` (`context_type`,`scope_owner_id`,`status`,`create_time`),
  CONSTRAINT `chk_ops_upgrade_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有备份和回滚点的产品升级任务';

CREATE TABLE `un_ops_upgrade_step` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '升级步骤主键',
  `upgrade_id` bigint NOT NULL COMMENT '所属升级任务主键',
  `step_number` int NOT NULL COMMENT '升级任务内执行顺序号',
  `step_type` varchar(32) NOT NULL COMMENT '数据库、配置、页面、流程或权限升级步骤类型',
  `source_version` varchar(100) NOT NULL COMMENT '步骤执行前版本',
  `target_version` varchar(100) NOT NULL COMMENT '步骤目标版本',
  `mapping_json` json DEFAULT NULL COMMENT '配置或数据升级映射及校验依据',
  `status` varchar(32) NOT NULL COMMENT '步骤执行结果状态',
  `start_time` datetime(3) DEFAULT NULL COMMENT '升级步骤开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '升级步骤结束时间',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '步骤失败原因与处理提示',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_upgrade_step` (`upgrade_id`,`step_number`),
  CONSTRAINT `chk_ops_upgrade_step_number` CHECK ((`step_number` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='升级步骤及失败处置证据';

CREATE TABLE `un_ops_verification_run` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品验收运行主键',
  `verification_type` varchar(32) NOT NULL COMMENT '验收类型，如部署、升级或业务场景',
  `context_type` varchar(32) NOT NULL COMMENT '验收层级，PLATFORM 或 SYSTEM',
  `platform_id` bigint NOT NULL COMMENT '所属平台主键',
  `system_id` bigint DEFAULT NULL COMMENT '系统验收所属系统主键',
  `scope_owner_id` bigint NOT NULL COMMENT '平台或系统的稳定归属主键',
  `release_id` bigint DEFAULT NULL COMMENT '验收对应的产品发布版本主键',
  `scenario_code` varchar(100) NOT NULL COMMENT '可复现的验收场景编码',
  `status` varchar(32) NOT NULL COMMENT '验收结果状态',
  `input_json` json NOT NULL COMMENT '验收输入、前置条件和步骤参数',
  `result_json` json DEFAULT NULL COMMENT '验收读回结果及业务证据',
  `threshold_json` json NOT NULL COMMENT '验收通过条件和限制',
  `started_by_account_id` bigint NOT NULL COMMENT '发起验收的平台账号主键',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '验收开始时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '验收结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_verification_type` (`context_type`,`scope_owner_id`,`verification_type`,`status`,`start_time`),
  CONSTRAINT `chk_ops_verification_scope` CHECK ((((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`scope_owner_id` = `platform_id`)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`scope_owner_id` = `system_id`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可复现的产品功能与部署验收记录';

CREATE TABLE `un_ops_verification_finding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '验收发现问题主键',
  `verification_run_id` bigint NOT NULL COMMENT '所属验收运行主键',
  `severity` varchar(32) NOT NULL COMMENT '问题严重程度',
  `finding_code` varchar(100) NOT NULL COMMENT '问题稳定编码',
  `title` varchar(500) NOT NULL COMMENT '问题标题',
  `detail_text` text NOT NULL COMMENT '复现步骤、预期与实际结果',
  `evidence_json` json DEFAULT NULL COMMENT '日志、截图或读回数据证据引用',
  `status` varchar(32) NOT NULL COMMENT '问题处理状态',
  `resolved_by_account_id` bigint DEFAULT NULL COMMENT '解决问题的平台账号主键',
  `resolve_time` datetime(3) DEFAULT NULL COMMENT '问题解决时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '问题登记时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '问题记录乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ops_finding_code` (`verification_run_id`,`finding_code`),
  KEY `idx_ops_finding_run` (`verification_run_id`,`severity`,`status`),
  CONSTRAINT `chk_ops_finding_resolution` CHECK (((`resolve_time` is null) or (`resolved_by_account_id` is not null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='验收发现的问题及整改证据';

CREATE TABLE `un_ops_request_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '请求观测记录主键',
  `request_id` varchar(64) NOT NULL COMMENT '对外请求标识，用于定位一次调用',
  `trace_id` varchar(64) NOT NULL COMMENT '跨服务调用链追踪标识',
  `system_id` bigint DEFAULT NULL COMMENT '系统上下文主键，平台请求为空',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户上下文主键，平台或系统公共请求为空',
  `account_id` bigint DEFAULT NULL COMMENT '已识别的平台账号主键，匿名请求为空',
  `client_source` varchar(32) NOT NULL COMMENT '发起请求的客户端渠道',
  `http_method` varchar(16) NOT NULL COMMENT 'HTTP 请求方法',
  `request_path` varchar(500) NOT NULL COMMENT '不含查询串的请求路径',
  `status_code` int NOT NULL COMMENT 'HTTP 响应状态码',
  `result_code` varchar(64) NOT NULL COMMENT '应用层结果或错误编码',
  `duration_millis` bigint NOT NULL COMMENT '请求处理耗时，单位毫秒',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '请求发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_ops_request_id` (`request_id`,`occur_time`),
  KEY `idx_ops_request_context` (`system_id`,`tenant_id`,`occur_time`),
  KEY `idx_ops_request_result` (`status_code`,`result_code`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不含敏感请求内容的调用观测与追踪记录';
