-- 补齐创建租户时遗漏的默认组织，只处理有效系统的有效租户；不修改手工根部门或已有部门数据。
-- 没有任何顶级部门且 root 编码未被占用才补根；停用根、多根、编码冲突均留待人工核对，不重启用或任意选根。
INSERT INTO `sys_department` (`system_id`, `tenant_id`, `parent_id`, `code`, `name`, `path_code`, `sort_order`, `status`)
SELECT tenant.`system_id`, tenant.`id`, NULL, 'root', '全公司', '/root', 0, 'ACTIVE'
FROM `sys_tenant` tenant
JOIN `sys_system` system_definition ON system_definition.`id` = tenant.`system_id`
WHERE tenant.`status` = 'ACTIVE' AND system_definition.`status` = 'ACTIVE' AND system_definition.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` department
    WHERE department.`tenant_id` = tenant.`id`
      AND (department.`parent_id` IS NULL OR department.`code` = 'root')
  );

-- 有效成员未配置部门时归入本租户唯一的有效顶级部门；保留已有归属并递增版本，防止旧页面覆盖修复结果。
-- 只补真正活动的账号/系统成员，不把禁用历史成员重新纳入组织；多根或停用根不会被任意作为默认部门。
UPDATE `sys_tenant_member` membership
JOIN `sys_tenant` tenant ON tenant.`id` = membership.`tenant_id` AND tenant.`system_id` = membership.`system_id`
JOIN `sys_system` system_definition ON system_definition.`id` = tenant.`system_id`
JOIN `sys_member` member ON member.`id` = membership.`system_member_id` AND member.`system_id` = membership.`system_id`
JOIN `plat_account` account ON account.`id` = member.`account_id`
JOIN `sys_department` root_department ON root_department.`tenant_id` = membership.`tenant_id`
  AND root_department.`system_id` = membership.`system_id` AND root_department.`parent_id` IS NULL
SET membership.`department_id` = root_department.`id`, membership.`version` = membership.`version` + 1
WHERE membership.`status` = 'ACTIVE' AND membership.`department_id` IS NULL
  AND tenant.`status` = 'ACTIVE' AND system_definition.`status` = 'ACTIVE' AND system_definition.`deleted` = 0
  AND member.`status` = 'ACTIVE' AND account.`status` = 'ACTIVE' AND root_department.`status` = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_department` other_root
    WHERE other_root.`tenant_id` = tenant.`id` AND other_root.`parent_id` IS NULL
      AND other_root.`id` <> root_department.`id`
  );
