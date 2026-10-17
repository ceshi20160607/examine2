-- 用途：为已有主租户补齐默认普通成员角色；新系统由创建事务直接初始化此角色。
-- 仅插入缺失角色，不改写既有授权、成员关系或业务数据；依赖 (tenant_id, code) 唯一约束保持幂等。
INSERT INTO `un_sys_role` (`system_id`, `tenant_id`, `code`, `name`, `status`)
SELECT tenant.`system_id`, tenant.`id`, 'member', '普通成员', 'ACTIVE'
FROM `un_sys_tenant` AS tenant
LEFT JOIN `un_sys_role` AS role
  ON role.`tenant_id` = tenant.`id` AND role.`code` = 'member'
WHERE tenant.`main_flag` = 1 AND role.`id` IS NULL;
