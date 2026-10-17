-- source: update/V19__flow_external_identity.sql
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

-- source: update/V20__flow_application_owner.sql
-- 外部应用配置的 Flow 记录来源应用，便于草稿读取、修改和发布时逐次校验归属。
ALTER TABLE `un_flow_definition`
  ADD COLUMN `owner_application_id` bigint DEFAULT NULL COMMENT '配置此流程的应用ID；系统后台配置为空' AFTER `owner_tenant_id`,
  ADD KEY `idx_flow_definition_owner_application` (`owner_application_id`,`id`);

-- source: update/V21__operations_release_artifact_index.sql
-- 一个制品可配合多个配置版本发布；版本号仍唯一，摘要只用于查询和校验。
ALTER TABLE `un_ops_release`
  DROP INDEX `uk_ops_release_hash`,
  ADD KEY `idx_ops_release_hash` (`artifact_hash`);

-- source: update/V22__print_legacy_upgrade.sql
-- 升级时固定旧打印模板的兼容范围；新装库和升级后新建的模板不会进入本表。
-- 管理员发布 PRINT 动作后填写退出时间，避免停用或回滚时重新放开旧兼容入口。
CREATE TABLE `un_print_legacy_template` (
  `module_id` bigint NOT NULL COMMENT '升级时已有打印模板的业务模块主键',
  `template_id` bigint NOT NULL COMMENT '升级时已发布的打印模板主键',
  `system_id` bigint NOT NULL COMMENT '业务模块所属系统主键',
  `owner_tenant_id` bigint NOT NULL COMMENT '业务模块配置所属租户主键',
  `source_module_version_id` bigint NOT NULL COMMENT '升级时固定的模块发布版本主键',
  `retired_time` datetime(3) DEFAULT NULL COMMENT '模块正式发布打印动作后的兼容退出时间',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '升级兼容记录创建时间',
  PRIMARY KEY (`module_id`,`template_id`),
  KEY `idx_print_legacy_scope` (`system_id`,`owner_tenant_id`,`module_id`,`retired_time`),
  KEY `idx_print_legacy_template` (`template_id`,`retired_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='升级前已发布打印模板的限定兼容清单';

-- 只从当前活跃模块、当前已发布模块快照和已发布打印模板取样，避免草稿或新装库获得兼容权。
INSERT INTO `un_print_legacy_template`
  (`module_id`,`template_id`,`system_id`,`owner_tenant_id`,`source_module_version_id`,`retired_time`,`create_time`)
SELECT DISTINCT m.`id`, t.`id`, m.`system_id`, m.`owner_tenant_id`, mp.`current_version_id`, NULL, CURRENT_TIMESTAMP(3)
FROM `un_cfg_module` m
JOIN `un_cfg_module_publication` mp ON mp.`module_id` = m.`id`
JOIN `un_cfg_module_version` mv ON mv.`id` = mp.`current_version_id`
JOIN `un_print_template` t ON t.`module_id` = m.`id`
  AND t.`system_id` = m.`system_id` AND t.`owner_tenant_id` = m.`owner_tenant_id`
JOIN `un_print_template_publication` tp ON tp.`template_id` = t.`id`
WHERE m.`status` = 'ACTIVE'
  AND JSON_SEARCH(mv.`snapshot_json`, 'one', 'PRINT', NULL, '$.actions[*].code') IS NULL;

-- source: update/V23__module_dashboard_legacy.sql
-- 只固定升级前已经发布的模块首页；新装库、新首页和未发布草稿不会获得旧入口兼容。
-- 模块与仪表盘的旧发布快照均保持不变；运行时仍按当前系统、租户、模块和成员 LIST 权限重查。
CREATE TABLE `un_ana_legacy_module_dashboard` (
  `dashboard_id` bigint NOT NULL COMMENT '升级前已发布的模块首页主键',
  `module_id` bigint NOT NULL COMMENT '首页绑定的当前业务模块主键',
  `system_id` bigint NOT NULL COMMENT '模块与首页共同所属系统主键',
  `tenant_id` bigint NOT NULL COMMENT '首页归属租户主键，不向其他租户回退',
  `source_module_version_id` bigint NOT NULL COMMENT '升级时模块当前发布版本主键，用于核对来源',
  `source_dashboard_version_id` bigint NOT NULL COMMENT '升级时首页当前发布版本主键，用于核对来源',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '兼容清单建立时间',
  PRIMARY KEY (`dashboard_id`),
  KEY `idx_ana_legacy_dashboard_scope` (`system_id`,`tenant_id`,`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='升级前已发布模块首页的限定兼容清单';

-- 同时核对模块和首页的有效发布指针，只保存缺少 DASHBOARD 动作的旧模块首页。
INSERT INTO `un_ana_legacy_module_dashboard`
  (`dashboard_id`,`module_id`,`system_id`,`tenant_id`,`source_module_version_id`,
   `source_dashboard_version_id`,`create_time`)
SELECT d.`id`, m.`id`, m.`system_id`, d.`owner_tenant_id`, mp.`current_version_id`,
       dp.`current_version_id`, CURRENT_TIMESTAMP(3)
FROM `un_ana_dashboard` d
JOIN `un_ana_dashboard_publication` dp ON dp.`dashboard_id` = d.`id`
JOIN `un_ana_dashboard_version` dv ON dv.`id` = dp.`current_version_id`
  AND dv.`dashboard_id` = d.`id` AND dv.`scope_type` = d.`scope_type`
  AND dv.`scope_owner_id` = d.`scope_owner_id`
JOIN `un_cfg_module` m ON m.`id` = d.`module_id` AND m.`system_id` = d.`system_id`
JOIN `un_cfg_module_publication` mp ON mp.`module_id` = m.`id`
  AND mp.`system_id` = m.`system_id` AND mp.`owner_tenant_id` = m.`owner_tenant_id`
JOIN `un_cfg_module_version` mv ON mv.`id` = mp.`current_version_id`
  AND mv.`module_id` = m.`id` AND mv.`system_id` = m.`system_id`
  AND mv.`owner_tenant_id` = m.`owner_tenant_id`
WHERE d.`context_type` = 'SYSTEM' AND d.`scope_type` = 'TENANT'
  AND d.`dashboard_kind` = 'MODULE_HOME' AND d.`owner_tenant_id` IS NOT NULL
  AND m.`status` = 'ACTIVE'
  AND JSON_SEARCH(mv.`snapshot_json`, 'one', 'DASHBOARD', NULL, '$.actions[*].code') IS NULL;

-- source: update/V24__audit_platform_scope.sql
-- 用途：将既有平台审计事件绑定到唯一平台，供平台后台按平台主键安全查询。
-- 升级前备份数据库；升级后对照 PLATFORM 事件总数、已绑定数及仍无归属数。
-- 多平台数据库无法从旧事件反推出归属，保持无归属且不对任何平台展示，需人工依据外部证据修复。
UPDATE `un_audit_event` AS event_row
JOIN (
  SELECT MIN(`id`) AS `platform_id`
  FROM `un_plat_platform`
  HAVING COUNT(*) = 1
) AS only_platform
SET event_row.`platform_id` = only_platform.`platform_id`
WHERE event_row.`context_type` = 'PLATFORM'
  AND event_row.`platform_id` IS NULL
  AND event_row.`system_id` IS NULL
  AND event_row.`tenant_id` IS NULL;
