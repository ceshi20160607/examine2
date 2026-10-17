-- 用途：把模块导出作为可授权页面动作，避免仅凭查看权限取走批量业务数据。
-- 现有角色默认不获得导出权限；已有模块草稿补导出动作，管理员重新发布模块后才对运行态生效。
ALTER TABLE `un_sys_role_menu`
  ADD COLUMN `can_export` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许导出当前有权查看的模块记录' AFTER `can_delete`;

-- 迁移已有模块草稿：只补缺失的导出动作，不修改已有发布快照或授权。
INSERT INTO `un_module_action` (`module_id`, `code`, `name`, `action_type`, `enabled_flag`, `sort_order`)
SELECT m.`id`, 'export', '导出', 'EXPORT', 1, 3
FROM `un_module` m
WHERE NOT EXISTS (
  SELECT 1 FROM `un_module_action` a WHERE a.`module_id` = m.`id` AND a.`code` = 'export'
);
