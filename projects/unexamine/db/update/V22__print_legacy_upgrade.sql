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
