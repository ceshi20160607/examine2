-- 用途：给已有模块草稿补齐已实现的导入与发起审批动作，管理员可在下一次发布时配置。
-- 历史发布版本的 JSON 快照保持不变；运行时代码对旧快照按原有行为兼容，重新发布后按显式配置执行。
INSERT INTO `un_module_action` (`module_id`,`code`,`name`,`action_type`,`enabled_flag`,`sort_order`)
SELECT m.`id`, a.`code`, a.`name`, a.`action_type`, 1, a.`sort_order`
FROM `un_module` m
CROSS JOIN (
  SELECT 'import' AS `code`, '导入' AS `name`, 'IMPORT' AS `action_type`, 4 AS `sort_order`
  UNION ALL SELECT 'submit_approval', '发起审批', 'SUBMIT_APPROVAL', 5
) a
WHERE NOT EXISTS (
  SELECT 1 FROM `un_module_action` existing
  WHERE existing.`module_id` = m.`id` AND existing.`code` = a.`code`
);
