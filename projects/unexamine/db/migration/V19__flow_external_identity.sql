-- 外部 Flow 沿用现有实例、任务和操作表；应用 ID 是身份命名空间，外部标识不映射成平台账号。
ALTER TABLE `un_flow_instance`
  ADD COLUMN `source_application_id` bigint DEFAULT NULL COMMENT '外部发起来源应用ID；内部发起为空' AFTER `started_by_tenant_member_id`,
  ADD COLUMN `source_external_principal_id` varchar(100) DEFAULT NULL COMMENT '应用命名空间内的外部发起人标识' AFTER `source_application_id`,
  ADD KEY `idx_flow_instance_source_app` (`source_application_id`,`source_external_principal_id`,`start_time`);

ALTER TABLE `un_flow_task`
  ADD COLUMN `assignee_application_id` bigint DEFAULT NULL COMMENT '外部审批人所属应用ID；内部任务为空' AFTER `assignee_tenant_member_id`,
  ADD COLUMN `assignee_external_principal_id` varchar(100) DEFAULT NULL COMMENT '应用命名空间内的外部审批人标识；候选池任务为空' AFTER `assignee_application_id`,
  ADD KEY `idx_flow_task_external_assignee` (`assignee_application_id`,`assignee_external_principal_id`,`status`);

ALTER TABLE `un_flow_action`
  ADD COLUMN `actor_application_id` bigint DEFAULT NULL COMMENT '外部操作来源应用ID；内部操作为空' AFTER `acted_by_tenant_member_id`,
  ADD COLUMN `actor_external_principal_id` varchar(100) DEFAULT NULL COMMENT '应用命名空间内的外部操作人标识' AFTER `actor_application_id`,
  ADD KEY `idx_flow_action_external_actor` (`actor_application_id`,`actor_external_principal_id`,`act_time`);
