-- flow 模块的固定产品表；由当前最终结构收敛，用于空库首次安装。

CREATE TABLE `un_flow_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `context_type` varchar(32) NOT NULL COMMENT '流程所属上下文类型',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台ID',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统ID',
  `owner_tenant_id` bigint DEFAULT NULL COMMENT '配置来源租户ID',
  `code` varchar(100) NOT NULL COMMENT '稳定业务编码',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '业务说明',
  `draft_revision` int NOT NULL DEFAULT '1' COMMENT '草稿修订号',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `created_by_account_id` bigint NOT NULL COMMENT '创建人平台账号ID',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_definition_code_scope` (`context_type`,`platform_id`,(coalesce(`system_id`,0)),(coalesce(`owner_tenant_id`,0)),`code`),
  KEY `idx_flow_definition_context_id` (`context_type`,`id`),
  KEY `idx_flow_definition_context` (`system_id`,`owner_tenant_id`,`status`),
  KEY `idx_flow_definition_system_tenant_id` (`system_id`,`owner_tenant_id`,`id`),
  CONSTRAINT `ck_flow_definition_scope` CHECK (((`platform_id` is not null) and (((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`owner_tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`owner_tenant_id` is not null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程定义与草稿作用域';

CREATE TABLE `un_flow_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型',
  `name` varchar(200) NOT NULL COMMENT '显示名称',
  `position_x` decimal(12,3) NOT NULL COMMENT '设计画布横坐标',
  `position_y` decimal(12,3) NOT NULL COMMENT '设计画布纵坐标',
  `assignee_policy_json` json DEFAULT NULL COMMENT '审批人解析策略',
  `form_policy_json` json DEFAULT NULL COMMENT '节点表单与字段权限策略',
  `timeout_policy_json` json DEFAULT NULL COMMENT '节点超时处理策略',
  `exception_policy_json` json DEFAULT NULL COMMENT '节点异常兜底策略',
  `config_json` json NOT NULL COMMENT '节点或连线业务配置',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_node_key` (`flow_id`,`node_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程草稿节点';

CREATE TABLE `un_flow_edge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `edge_key` varchar(100) NOT NULL COMMENT '流程连线稳定编码',
  `source_node_key` varchar(100) NOT NULL COMMENT '连线起点节点编码',
  `target_node_key` varchar(100) NOT NULL COMMENT '连线终点节点编码',
  `condition_expression` text COMMENT '触发或连线条件表达式',
  `priority_order` int NOT NULL DEFAULT '0' COMMENT '条件或绑定优先级',
  `config_json` json NOT NULL COMMENT '节点或连线业务配置',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_edge_key` (`flow_id`,`edge_key`),
  KEY `idx_flow_edge_source` (`flow_id`,`source_node_key`,`priority_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程草稿连线';

CREATE TABLE `un_flow_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `version_number` int NOT NULL COMMENT '流程发布版本号',
  `draft_revision` int NOT NULL COMMENT '草稿修订号',
  `definition_hash` varchar(64) NOT NULL COMMENT '发布定义内容摘要',
  `snapshot_json` json NOT NULL COMMENT '发布时冻结的完整流程定义快照',
  `simulation_result_json` json NOT NULL COMMENT '发布前模拟验证结果',
  `change_summary` varchar(1000) NOT NULL COMMENT '发布变更说明',
  `published_by_account_id` bigint NOT NULL COMMENT '发布人平台账号ID',
  `publish_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发布时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_version_number` (`flow_id`,`version_number`),
  UNIQUE KEY `uk_flow_version_revision` (`flow_id`,`draft_revision`),
  KEY `idx_flow_version_flow_id` (`flow_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变流程发布版本';

CREATE TABLE `un_flow_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `current_version_id` bigint NOT NULL COMMENT '当前生效流程版本ID',
  `updated_by_account_id` bigint NOT NULL COMMENT '最后修改人平台账号ID',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_publication_flow` (`flow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程当前发布指针';

CREATE TABLE `un_flow_trigger_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `system_id` bigint NOT NULL COMMENT '所属系统ID',
  `owner_tenant_id` bigint NOT NULL COMMENT '配置来源租户ID',
  `module_id` bigint NOT NULL COMMENT '关联业务模块ID',
  `action_id` bigint DEFAULT NULL COMMENT '绑定的模块动作ID',
  `trigger_event` varchar(64) NOT NULL COMMENT '模块业务触发事件编码',
  `flow_id` bigint DEFAULT NULL COMMENT '命中的流程定义ID；直接执行业务动作时为空',
  `priority_order` int NOT NULL DEFAULT '0' COMMENT '条件或绑定优先级',
  `condition_expression` text COMMENT '触发或连线条件表达式',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_binding_active_scope` (((case when (`status` = _utf8mb4'ACTIVE') then `owner_tenant_id` else NULL end)),((case when (`status` = _utf8mb4'ACTIVE') then `module_id` else NULL end)),((case when (`status` = _utf8mb4'ACTIVE') then `trigger_event` else NULL end)),((case when (`status` = _utf8mb4'ACTIVE') then coalesce(`action_id`,0) else NULL end))),
  KEY `idx_flow_binding_flow` (`flow_id`,`status`),
  KEY `idx_flow_binding_system_module_id` (`system_id`,`module_id`,`id`),
  KEY `idx_flow_binding_id_source_tenant` (`id`,`owner_tenant_id`),
  KEY `idx_flow_binding_scope_history` (`owner_tenant_id`,`module_id`,`trigger_event`,`action_id`,`create_time`),
  CONSTRAINT `ck_flow_binding_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模块事件和动作的租户流程绑定';

CREATE TABLE `un_flow_binding_resolution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `system_id` bigint NOT NULL COMMENT '所属系统ID',
  `tenant_id` bigint NOT NULL COMMENT '实际运行租户ID',
  `module_id` bigint NOT NULL COMMENT '关联业务模块ID',
  `trigger_event` varchar(64) NOT NULL COMMENT '模块业务触发事件编码',
  `binding_id` bigint NOT NULL COMMENT '命中的流程绑定ID',
  `flow_version_id` bigint NOT NULL COMMENT '固定的流程发布版本ID',
  `resolution_reason` varchar(500) NOT NULL COMMENT '默认继承或租户覆盖原因',
  `resolve_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定解析时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_resolution_lookup` (`tenant_id`,`module_id`,`trigger_event`,`resolve_time`),
  KEY `idx_flow_resolution_binding_version` (`id`,`binding_id`,`flow_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='触发时的最终生效绑定解析';

CREATE TABLE `un_flow_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `context_type` varchar(32) NOT NULL COMMENT '流程所属上下文类型',
  `platform_id` bigint DEFAULT NULL COMMENT '所属平台ID',
  `system_id` bigint DEFAULT NULL COMMENT '所属系统ID',
  `tenant_id` bigint DEFAULT NULL COMMENT '实际运行租户ID',
  `flow_id` bigint NOT NULL COMMENT '流程定义ID',
  `flow_version_id` bigint NOT NULL COMMENT '固定的流程发布版本ID',
  `module_id` bigint DEFAULT NULL COMMENT '启动时关联的业务模块ID；平台独立流程为空',
  `module_version_id` bigint DEFAULT NULL COMMENT '启动时固定的模块发布版本ID',
  `business_record_id` bigint DEFAULT NULL COMMENT '关联的通用业务记录ID；非记录流程为空',
  `binding_resolution_id` bigint DEFAULT NULL COMMENT '本次触发绑定解析记录ID；独立发起为空',
  `binding_id` bigint DEFAULT NULL COMMENT '本次命中的流程绑定ID；独立发起为空',
  `binding_source_tenant_id` bigint DEFAULT NULL COMMENT '绑定来源租户ID，可与实例实际租户不同',
  `business_type` varchar(100) DEFAULT NULL COMMENT '关联业务对象类型',
  `business_id` varchar(100) DEFAULT NULL COMMENT '关联业务对象外部标识',
  `business_snapshot_json` json DEFAULT NULL COMMENT '启动时业务对象数据快照',
  `runtime_snapshot_json` json NOT NULL COMMENT '启动时复制的节点、连线、条件、字段权限及高级设置',
  `title` varchar(500) NOT NULL COMMENT '流程实例标题',
  `current_node_key` varchar(100) DEFAULT NULL COMMENT '当前运行节点编码',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `started_by_account_id` bigint NOT NULL COMMENT '发起人平台账号ID',
  `started_by_tenant_member_id` bigint DEFAULT NULL COMMENT '发起人在实际运行租户的成员ID',
  `start_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '流程启动时间',
  `finish_time` datetime(3) DEFAULT NULL COMMENT '流程结束时间',
  `error_code` varchar(100) DEFAULT NULL COMMENT '失败错误编码',
  `error_message` varchar(2000) DEFAULT NULL COMMENT '失败原因说明',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_instance_context` (`system_id`,`tenant_id`,`status`,`start_time`),
  KEY `idx_flow_instance_business` (`business_type`,`business_id`),
  KEY `idx_flow_instance_starter_member` (`tenant_id`,`started_by_tenant_member_id`),
  KEY `idx_flow_instance_module_record` (`module_id`,`business_record_id`),
  CONSTRAINT `ck_flow_instance_binding_source` CHECK ((((`binding_id` is null) and (`binding_source_tenant_id` is null) and (`binding_resolution_id` is null)) or ((`binding_id` is not null) and (`binding_source_tenant_id` is not null)))),
  CONSTRAINT `ck_flow_instance_module_version` CHECK ((((`module_id` is null) and (`module_version_id` is null) and (`business_record_id` is null)) or ((`module_id` is not null) and (`module_version_id` is not null)))),
  CONSTRAINT `ck_flow_instance_runtime_snapshot` CHECK ((json_type(`runtime_snapshot_json`) = _utf8mb4'OBJECT')),
  CONSTRAINT `ck_flow_instance_scope` CHECK (((`platform_id` is not null) and (((`context_type` = _utf8mb4'PLATFORM') and (`system_id` is null) and (`tenant_id` is null)) or ((`context_type` = _utf8mb4'SYSTEM') and (`system_id` is not null) and (`tenant_id` is not null)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运行流程实例及启动快照';

CREATE TABLE `un_flow_subflow_link` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '子流程关系主键',
  `parent_instance_id` bigint NOT NULL COMMENT '父流程实例ID',
  `parent_execution_id` bigint NOT NULL COMMENT '启动子流程的父节点执行轮次ID',
  `child_instance_id` bigint NOT NULL COMMENT '子流程实例ID',
  `parent_node_key` varchar(100) NOT NULL COMMENT '父流程节点编码快照',
  `status` varchar(32) NOT NULL COMMENT '子流程关系状态',
  `created_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关系创建时间',
  `completed_time` datetime(3) DEFAULT NULL COMMENT '子流程结束并处理父流程的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_subflow_parent_execution` (`parent_instance_id`,`parent_execution_id`),
  UNIQUE KEY `uk_flow_subflow_child` (`child_instance_id`),
  CONSTRAINT `ck_flow_subflow_not_self` CHECK ((`parent_instance_id` <> `child_instance_id`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='父子流程实例关系';

CREATE TABLE `un_flow_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `parent_execution_id` bigint DEFAULT NULL COMMENT '父节点执行轮次ID',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `enter_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '节点进入时间',
  `leave_time` datetime(3) DEFAULT NULL COMMENT '节点离开时间',
  `result_code` varchar(64) DEFAULT NULL COMMENT '节点执行结果编码',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_execution_active` (`instance_id`,`status`,`enter_time`),
  KEY `idx_flow_execution_instance_id` (`instance_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程节点执行轮次';

CREATE TABLE `un_flow_instance_variable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `variable_key` varchar(100) NOT NULL COMMENT '实例变量编码',
  `value_type` varchar(32) NOT NULL COMMENT '实例变量值类型',
  `value_json` json DEFAULT NULL COMMENT '实例变量JSON值',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_instance_variable` (`instance_id`,`variable_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程实例变量';

CREATE TABLE `un_flow_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `execution_id` bigint NOT NULL COMMENT '任务所属的节点执行轮次',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `task_type` varchar(32) NOT NULL COMMENT '待办任务类型',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `assignee_account_id` bigint DEFAULT NULL COMMENT '当前处理人平台账号ID',
  `assignee_tenant_member_id` bigint DEFAULT NULL COMMENT '当前处理人在实际运行租户的成员ID',
  `assignee_snapshot_json` json NOT NULL COMMENT '任务创建时处理人解析快照',
  `due_time` datetime(3) DEFAULT NULL COMMENT '任务应完成时间',
  `claim_time` datetime(3) DEFAULT NULL COMMENT '任务领取时间',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '任务完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_task_node` (`instance_id`,`node_key`,`id`),
  KEY `idx_flow_task_assignee` (`assignee_account_id`,`status`,`due_time`),
  KEY `idx_flow_task_assignee_member` (`assignee_tenant_member_id`,`status`,`due_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节点执行轮次的待办任务';

CREATE TABLE `un_flow_task_candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '所属流程待办任务ID',
  `candidate_type` varchar(32) NOT NULL COMMENT '候选主体类型',
  `candidate_id` varchar(100) NOT NULL COMMENT '候选主体标识',
  `resolution_reason` varchar(500) NOT NULL COMMENT '默认继承或租户覆盖原因',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_task_candidate` (`task_id`,`candidate_type`,`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待办任务候选处理人';

CREATE TABLE `un_flow_action` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `task_id` bigint DEFAULT NULL COMMENT '所属流程待办任务ID',
  `node_key` varchar(100) NOT NULL COMMENT '流程节点稳定编码',
  `action_code` varchar(64) NOT NULL COMMENT '流程操作编码',
  `comment_text` varchar(2000) DEFAULT NULL COMMENT '操作意见正文',
  `input_json` json DEFAULT NULL COMMENT '本次操作输入快照',
  `result_json` json NOT NULL COMMENT '本次操作结果快照',
  `idempotency_key` varchar(255) NOT NULL COMMENT '操作幂等键',
  `acted_by_account_id` bigint NOT NULL COMMENT '操作人平台账号ID',
  `acted_by_tenant_member_id` bigint DEFAULT NULL COMMENT '操作人在实际运行租户的成员ID',
  `act_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作发生时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_action_idempotency` (`instance_id`,`idempotency_key`),
  KEY `idx_flow_action_instance` (`instance_id`,`act_time`),
  KEY `idx_flow_action_actor_member` (`acted_by_tenant_member_id`,`act_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程操作及幂等记录';

CREATE TABLE `un_flow_exception` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '流程节点稳定编码',
  `exception_type` varchar(64) NOT NULL COMMENT '流程异常类型',
  `error_code` varchar(100) NOT NULL COMMENT '失败错误编码',
  `error_message` varchar(2000) NOT NULL COMMENT '失败原因说明',
  `policy_action` varchar(64) NOT NULL COMMENT '异常策略选择的处置动作',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `resolved_by_account_id` bigint DEFAULT NULL COMMENT '异常处置人平台账号ID',
  `resolved_by_tenant_member_id` bigint DEFAULT NULL COMMENT '异常处置人在运行租户的成员ID',
  `resolution_comment` varchar(2000) DEFAULT NULL COMMENT '异常处置说明',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件或异常发生时间',
  `resolve_time` datetime(3) DEFAULT NULL COMMENT '绑定解析时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_exception_instance` (`instance_id`,`status`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程异常与处置';

CREATE TABLE `un_flow_history_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `execution_id` bigint DEFAULT NULL COMMENT '关联节点执行轮次ID',
  `task_id` bigint DEFAULT NULL COMMENT '所属流程待办任务ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '流程节点稳定编码',
  `event_type` varchar(64) NOT NULL COMMENT '历史事件类型',
  `event_name` varchar(200) NOT NULL COMMENT '历史事件显示名称',
  `actor_account_id` bigint DEFAULT NULL COMMENT '事件操作者平台账号ID',
  `actor_tenant_member_id` bigint DEFAULT NULL COMMENT '事件操作者租户成员ID',
  `before_status` varchar(32) DEFAULT NULL COMMENT '事件发生前状态',
  `after_status` varchar(32) DEFAULT NULL COMMENT '事件发生后状态',
  `detail_json` json NOT NULL COMMENT '事件业务详情快照',
  `occur_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件或异常发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_flow_history_instance` (`instance_id`,`occur_time`),
  KEY `idx_flow_history_task` (`task_id`,`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变流程事件历史';

CREATE TABLE `un_flow_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` bigint NOT NULL COMMENT '所属流程实例ID',
  `execution_id` bigint DEFAULT NULL COMMENT '关联节点执行轮次ID',
  `node_key` varchar(100) DEFAULT NULL COMMENT '流程节点稳定编码',
  `job_type` varchar(64) NOT NULL COMMENT '流程任务类型',
  `status` varchar(32) NOT NULL COMMENT '当前业务状态',
  `payload_json` json NOT NULL COMMENT '流程异步任务内容',
  `idempotency_key` varchar(255) NOT NULL COMMENT '操作幂等键',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '已尝试执行次数',
  `max_attempts` int NOT NULL DEFAULT '3' COMMENT '最大允许执行次数',
  `next_run_time` datetime(3) NOT NULL COMMENT '下次可执行时间',
  `lock_time` datetime(3) DEFAULT NULL COMMENT '任务锁定时间',
  `locked_by` varchar(100) DEFAULT NULL COMMENT '当前执行节点标识',
  `last_error_code` varchar(100) DEFAULT NULL COMMENT '最近一次失败编码',
  `last_error_message` varchar(2000) DEFAULT NULL COMMENT '最近一次失败说明',
  `complete_time` datetime(3) DEFAULT NULL COMMENT '任务完成时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_job_idempotency` (`instance_id`,`idempotency_key`),
  KEY `idx_flow_job_due` (`status`,`next_run_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='流程定时与异步等待任务';
