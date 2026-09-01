-- C44: every module exposes permission-scoped export as an explicit, publishable action.

INSERT INTO `cfg_module_action` (
  `system_id`, `owner_tenant_id`, `module_id`, `code`, `name`, `action_type`,
  `location`, `sort_order`, `status`, `config_json`, `version`
)
SELECT
  m.`system_id`, m.`owner_tenant_id`, m.`id`, 'EXPORT', '导出', 'BUILTIN',
  'LIST_TOOLBAR', 110, 'ACTIVE', JSON_OBJECT(), 0
FROM `cfg_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'EXPORT'
);

UPDATE `cfg_module` m
SET m.`draft_revision` = m.`draft_revision` + 1,
    m.`updated_at` = CURRENT_TIMESTAMP(3)
WHERE EXISTS (
  SELECT 1 FROM `cfg_module_action` a
  WHERE a.`module_id` = m.`id` AND a.`code` = 'EXPORT'
)
AND EXISTS (
  SELECT 1 FROM `cfg_module_publication` p
  WHERE p.`module_id` = m.`id`
);
