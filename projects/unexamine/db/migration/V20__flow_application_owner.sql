-- 外部应用配置的 Flow 记录来源应用，便于草稿读取、修改和发布时逐次校验归属。
ALTER TABLE `un_flow_definition`
  ADD COLUMN `owner_application_id` bigint DEFAULT NULL COMMENT '配置此流程的应用ID；系统后台配置为空' AFTER `owner_tenant_id`,
  ADD KEY `idx_flow_definition_owner_application` (`owner_application_id`,`id`);
