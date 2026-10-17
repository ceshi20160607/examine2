-- runtimedata 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_biz_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块主键',
  `created_config_version_id` bigint NOT NULL COMMENT '创建业务记录时固定的已发布模块版本',
  `updated_config_version_id` bigint NOT NULL COMMENT '最近修改业务记录时固定的已发布模块版本',
  `record_number` varchar(100) DEFAULT NULL COMMENT '模块内可展示的业务编号',
  `title` varchar(500) NOT NULL COMMENT '业务记录标题',
  `status` varchar(64) NOT NULL COMMENT '当前业务状态或处理状态',
  `owner_member_id` bigint NOT NULL COMMENT '当前业务记录负责人主键，缺失时由系统当前所属人补齐',
  `department_id` bigint DEFAULT NULL COMMENT '业务记录所属部门主键',
  `created_by_member_id` bigint NOT NULL COMMENT '创建业务数据的系统成员主键',
  `updated_by_member_id` bigint NOT NULL COMMENT '最近修改业务记录的系统成员主键',
  `archived` tinyint NOT NULL DEFAULT '0' COMMENT '业务记录归档标记',
  `archive_time` datetime(3) DEFAULT NULL COMMENT '业务记录归档时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  `delete_time` datetime(3) DEFAULT NULL COMMENT '逻辑删除时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_number` (`tenant_id`,`module_id`,`record_number`),
  KEY `idx_biz_record_tenant_id` (`tenant_id`,`id`),
  KEY `idx_biz_record_module_id` (`module_id`,`id`),
  KEY `idx_biz_record_list` (`tenant_id`,`module_id`,`deleted`,`update_time`),
  KEY `idx_biz_record_owner` (`tenant_id`,`module_id`,`owner_member_id`),
  KEY `idx_biz_record_department` (`tenant_id`,`module_id`,`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自定义模块的通用业务主记录';

CREATE TABLE `un_biz_record_value` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `field_id` bigint NOT NULL COMMENT '模块字段配置主键',
  `field_code` varchar(100) NOT NULL COMMENT '写入时字段的稳定业务编码',
  `value_type` varchar(32) NOT NULL COMMENT '字段值存储类型',
  `value_text` text COMMENT '文本类型原始值',
  `value_number` decimal(24,8) DEFAULT NULL COMMENT '数值类型原始值',
  `value_date` date DEFAULT NULL COMMENT '日期类型原始值',
  `value_time` datetime(3) DEFAULT NULL COMMENT '日期时间类型原始值',
  `value_boolean` tinyint DEFAULT NULL COMMENT '布尔类型原始值',
  `value_reference_id` bigint DEFAULT NULL COMMENT '关联业务记录主键',
  `value_file_id` bigint DEFAULT NULL COMMENT '附件文件主键',
  `value_json` json DEFAULT NULL COMMENT '复杂类型原始值',
  `normalized_text` varchar(1000) DEFAULT NULL COMMENT '用于检索的标准化文本',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_value_field` (`record_id`,`field_id`),
  KEY `idx_biz_record_value_text` (`field_id`,`normalized_text`(191)),
  KEY `idx_biz_record_value_number` (`field_id`,`value_number`),
  KEY `idx_biz_record_value_date` (`field_id`,`value_time`),
  KEY `idx_biz_record_value_reference` (`field_id`,`value_reference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务主记录的自定义字段值';

CREATE TABLE `un_biz_record_child_row` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通用子表行主键',
  `system_id` bigint NOT NULL COMMENT '主记录所属系统',
  `tenant_id` bigint NOT NULL COMMENT '主记录的数据归属租户',
  `module_id` bigint NOT NULL COMMENT '主记录所属模块',
  `record_id` bigint NOT NULL COMMENT '主业务记录主键',
  `child_field_id` bigint NOT NULL COMMENT '定义此子表的主模块字段主键',
  `row_key` varchar(100) NOT NULL COMMENT '同一记录和子表内稳定的行标识',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '子表行显示顺序',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '行逻辑删除标记',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '行创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '行最近修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '行乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_child_row_key` (`record_id`,`child_field_id`,`row_key`),
  KEY `idx_biz_child_row_field_id` (`module_id`,`child_field_id`,`id`),
  KEY `idx_biz_child_row_list` (`record_id`,`child_field_id`,`deleted`,`sort_order`),
  CONSTRAINT `ck_biz_child_row_deleted` CHECK ((`deleted` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用业务记录的子表明细行';

CREATE TABLE `un_biz_record_child_value` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '子表行字段值主键',
  `module_id` bigint NOT NULL COMMENT '子表所属模块',
  `child_field_id` bigint NOT NULL COMMENT '子表定义字段主键',
  `child_row_id` bigint NOT NULL COMMENT '所属子表行主键',
  `field_id` bigint NOT NULL COMMENT '子表列字段主键',
  `value_type` varchar(32) NOT NULL COMMENT '字段值存储类型',
  `value_text` text COMMENT '文本类型原始值',
  `value_number` decimal(24,8) DEFAULT NULL COMMENT '数值类型原始值',
  `value_date` date DEFAULT NULL COMMENT '日期类型原始值',
  `value_time` datetime(3) DEFAULT NULL COMMENT '日期时间类型原始值',
  `value_boolean` tinyint DEFAULT NULL COMMENT '布尔类型原始值',
  `value_reference_id` bigint DEFAULT NULL COMMENT '关联记录主键，读取时继续校验目标模块和权限',
  `value_file_id` bigint DEFAULT NULL COMMENT '附件文件主键，读取时继续校验访问权限',
  `value_json` json DEFAULT NULL COMMENT '复杂类型原始值',
  `normalized_text` varchar(1000) DEFAULT NULL COMMENT '用于检索的标准化文本',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '值创建时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '值最近修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_child_value_field` (`child_row_id`,`field_id`),
  KEY `idx_biz_child_value_search` (`module_id`,`field_id`,`normalized_text`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用子表明细行的独立列字段值';

CREATE TABLE `un_biz_record_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '业务评论主键',
  `system_id` bigint NOT NULL COMMENT '业务记录所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务记录的数据归属租户',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块',
  `record_id` bigint NOT NULL COMMENT '被评论的业务记录主键',
  `parent_comment_id` bigint DEFAULT NULL COMMENT '同一记录内被回复的评论主键',
  `body` text NOT NULL COMMENT '评论正文',
  `created_by_member_id` bigint NOT NULL COMMENT '发表评论的系统成员主键',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评论发表时间',
  `updated_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '评论最近修改时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '评论逻辑删除标记',
  `deleted_time` datetime(3) DEFAULT NULL COMMENT '评论逻辑删除时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '评论乐观锁版本',
  PRIMARY KEY (`id`),
  KEY `idx_biz_comment_record_id` (`record_id`,`id`),
  KEY `idx_biz_comment_record_time` (`record_id`,`deleted`,`created_time`),
  CONSTRAINT `ck_biz_comment_deleted` CHECK ((((`deleted` = 0) and (`deleted_time` is null)) or ((`deleted` = 1) and (`deleted_time` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录评论与同记录回复';

CREATE TABLE `un_biz_record_index` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `module_id` bigint NOT NULL COMMENT '业务记录所属模块主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `query_index_id` bigint NOT NULL COMMENT '本模块查询索引定义主键',
  `index_key_hash` varchar(64) NOT NULL COMMENT '复合查询键的哈希摘要',
  `unique_key_hash` varchar(64) DEFAULT NULL COMMENT '唯一索引的规范化键摘要；非唯一索引为空',
  `index_key_text` varchar(1500) NOT NULL COMMENT '可诊断的复合查询键原文',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_index_record` (`record_id`,`query_index_id`),
  UNIQUE KEY `uk_biz_record_index_unique_value` (`tenant_id`,`module_id`,`query_index_id`,`unique_key_hash`),
  KEY `idx_biz_record_index_lookup` (`tenant_id`,`module_id`,`query_index_id`,`index_key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录按发布索引配置生成的查询键';

CREATE TABLE `un_biz_record_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `source_module_id` bigint NOT NULL COMMENT '来源业务记录所属模块',
  `source_record_id` bigint NOT NULL COMMENT '来源业务记录主键',
  `field_id` bigint NOT NULL COMMENT '来源模块中的关联字段配置主键',
  `target_module_id` bigint NOT NULL COMMENT '目标业务记录所属模块',
  `target_record_id` bigint NOT NULL COMMENT '目标业务记录主键',
  `relation_order` int NOT NULL DEFAULT '0' COMMENT '多值关联的显示顺序',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_relation` (`source_record_id`,`field_id`,`target_record_id`),
  KEY `idx_biz_relation_target` (`tenant_id`,`target_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录之间由关联字段定义的独立关系';

CREATE TABLE `un_biz_record_participant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `system_member_id` bigint NOT NULL COMMENT '参与记录的系统成员主键',
  `participant_type` varchar(32) NOT NULL DEFAULT 'PARTICIPANT' COMMENT '参与关系类型，例如协作人或关注人',
  `created_by_member_id` bigint NOT NULL COMMENT '创建业务数据的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_record_participant` (`record_id`,`system_member_id`,`participant_type`),
  KEY `idx_biz_record_participant_member` (`tenant_id`,`system_member_id`,`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录的负责人以外的团队参与成员';

CREATE TABLE `un_biz_record_state_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `from_status` varchar(64) DEFAULT NULL COMMENT '变更前业务状态',
  `to_status` varchar(64) NOT NULL COMMENT '变更后的业务状态',
  `reason` varchar(1000) DEFAULT NULL COMMENT '业务变更原因',
  `source_type` varchar(64) NOT NULL COMMENT '发起状态变更的来源类型',
  `source_id` varchar(100) DEFAULT NULL COMMENT '发起状态变更的来源对象标识',
  `changed_by_member_id` bigint NOT NULL COMMENT '执行变更的系统成员主键',
  `change_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务变更发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_state_record` (`record_id`,`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录状态变更历史';

CREATE TABLE `un_biz_record_owner_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `record_id` bigint NOT NULL COMMENT '业务主记录主键',
  `from_owner_member_id` bigint DEFAULT NULL COMMENT '变更前负责人主键',
  `to_owner_member_id` bigint DEFAULT NULL COMMENT '变更后负责人主键',
  `from_department_id` bigint DEFAULT NULL COMMENT '变更前负责部门主键',
  `to_department_id` bigint DEFAULT NULL COMMENT '变更后负责部门主键',
  `reason` varchar(1000) NOT NULL COMMENT '业务变更原因',
  `changed_by_member_id` bigint NOT NULL COMMENT '执行变更的系统成员主键',
  `change_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '业务变更发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_owner_record` (`record_id`,`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录负责人和部门变更历史';

CREATE TABLE `un_biz_record_conversion` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `system_id` bigint NOT NULL COMMENT '业务数据所属系统',
  `tenant_id` bigint NOT NULL COMMENT '业务数据归属租户',
  `source_module_id` bigint NOT NULL COMMENT '来源记录及转化动作共同所属模块',
  `source_record_id` bigint NOT NULL COMMENT '来源业务记录主键',
  `action_id` bigint NOT NULL COMMENT '来源模块的转化动作配置主键',
  `target_module_id` bigint NOT NULL COMMENT '成功转化永久唯一键中的目标模块',
  `status` varchar(32) NOT NULL COMMENT '转化状态：处理中、失败、成功或已解除关系',
  `mapping_snapshot_json` json NOT NULL COMMENT '执行转化时固定的字段映射快照',
  `idempotency_key` varchar(255) NOT NULL COMMENT '同租户转化请求的幂等键',
  `created_by_member_id` bigint NOT NULL COMMENT '创建业务数据的系统成员主键',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '转化执行结束时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_conversion_idempotency` (`tenant_id`,`idempotency_key`),
  UNIQUE KEY `uk_biz_conversion_permanent_success` (((case when (`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'UNLINKED')) then `source_record_id` else NULL end)),((case when (`status` in (_utf8mb4'SUCCEEDED',_utf8mb4'UNLINKED')) then `target_module_id` else NULL end))),
  KEY `idx_biz_conversion_result_scope` (`tenant_id`,`id`,`target_module_id`),
  KEY `idx_biz_conversion_source` (`source_record_id`,`create_time`),
  CONSTRAINT `ck_biz_conversion_status` CHECK ((`status` in (_utf8mb4'PROCESSING',_utf8mb4'FAILED',_utf8mb4'SUCCEEDED',_utf8mb4'UNLINKED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务记录转化执行及永久成功身份';

CREATE TABLE `un_biz_record_conversion_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `conversion_id` bigint NOT NULL COMMENT '所属转化执行记录主键',
  `tenant_id` bigint NOT NULL COMMENT '来源和目标记录共同所属的数据租户',
  `target_module_id` bigint NOT NULL COMMENT '目标业务记录所属模块',
  `target_record_id` bigint NOT NULL COMMENT '目标业务记录主键',
  `result_type` varchar(32) NOT NULL COMMENT '目标记录在转化结果中的类型',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_conversion_result` (`conversion_id`,`target_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一次转化创建的目标记录';

CREATE TABLE `un_biz_tenant_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '共享授权主键，历史使用记录保持引用',
  `system_id` bigint NOT NULL COMMENT '共享双方所属系统',
  `source_tenant_id` bigint NOT NULL COMMENT '拥有业务数据的来源租户',
  `target_tenant_id` bigint NOT NULL COMMENT '获得访问关系的目标租户',
  `module_id` bigint NOT NULL COMMENT '共享数据所属模块',
  `record_id` bigint DEFAULT NULL COMMENT '指定记录授权的原记录主键，动态范围授权为空',
  `scope_type` varchar(32) NOT NULL DEFAULT 'RECORD' COMMENT '授权范围类型：RECORD指定记录，DATA_RANGE动态数据范围',
  `scope_json` json DEFAULT NULL COMMENT '范围条件、字段类型绑定及授予时来源共享权限约束快照',
  `permission_json` json NOT NULL COMMENT '明确允许的业务动作，仍与目标用户角色权限取交集',
  `status` varchar(32) NOT NULL COMMENT '授权存储状态：ACTIVE有效，REVOKED已撤销',
  `start_time` datetime(3) DEFAULT NULL COMMENT '授权开始生效时间，为空表示授予后立即生效',
  `expire_time` datetime(3) DEFAULT NULL COMMENT '授权失效时间，旧接口和字段保持兼容',
  `granted_by_member_id` bigint NOT NULL COMMENT '来源租户授予授权的系统成员',
  `grant_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近授予时间，旧字段保持兼容',
  `revoke_time` datetime(3) DEFAULT NULL COMMENT '授权撤销时间，历史授权不删除',
  `version` int NOT NULL DEFAULT '0' COMMENT '授权修改的乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_tenant_share` (`source_tenant_id`,`target_tenant_id`,`record_id`),
  KEY `idx_biz_share_target` (`target_tenant_id`,`status`,`expire_time`),
  KEY `idx_biz_share_module_scope` (`system_id`,`target_tenant_id`,`module_id`,`scope_type`,`status`),
  CONSTRAINT `ck_biz_share_scope` CHECK ((((`scope_type` = _utf8mb4'RECORD') and (`record_id` is not null) and (`scope_json` is null)) or ((`scope_type` = _utf8mb4'DATA_RANGE') and (`record_id` is null) and (json_type(`scope_json`) = _utf8mb4'OBJECT') and (`scope_json` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同系统跨租户记录及动态模块范围共享授权，不改变数据所有权';

CREATE TABLE `un_biz_tenant_share_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '本表主键',
  `share_id` bigint NOT NULL COMMENT '共享授权主键',
  `target_tenant_member_id` bigint NOT NULL COMMENT '执行共享访问的目标租户成员',
  `action_code` varchar(64) NOT NULL COMMENT '通过共享授权执行的业务动作编码',
  `result_code` varchar(64) NOT NULL COMMENT '共享访问结果编码',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '共享访问发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_biz_share_usage` (`share_id`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目标租户通过共享授权访问业务数据的历史';
