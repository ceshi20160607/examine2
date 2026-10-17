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
