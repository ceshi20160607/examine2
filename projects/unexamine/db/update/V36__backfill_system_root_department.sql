-- Systems created before the atomic bootstrap receive the same usable organization root.
INSERT INTO `sys_department` (`system_id`, `tenant_id`, `parent_id`, `code`, `name`, `path_code`, `sort_order`, `status`)
SELECT tenant.`system_id`, tenant.`id`, NULL, 'root', '全公司', '/root', 0, 'ACTIVE'
FROM `sys_tenant` tenant
WHERE tenant.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` department
    WHERE department.`tenant_id` = tenant.`id` AND department.`parent_id` IS NULL
  );

UPDATE `sys_tenant_member` membership
JOIN `sys_department` root_department
  ON root_department.`tenant_id` = membership.`tenant_id`
 AND root_department.`parent_id` IS NULL
SET membership.`department_id` = root_department.`id`, membership.`version` = membership.`version` + 1
WHERE membership.`status` = 'ACTIVE' AND membership.`department_id` IS NULL;
