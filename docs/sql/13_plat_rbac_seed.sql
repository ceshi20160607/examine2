-- 平台 RBAC 种子：固定 ID 便于本地与迁移脚本对齐
SET NAMES utf8mb4;

-- 角色：1=平台超级管理员 2=平台普通用户
INSERT INTO un_plat_role (id, role_code, role_name, remark, status, create_user_id, update_user_id, create_time, update_time)
VALUES
(1, 'plat_super_admin', '平台超级管理员', '首账号默认；可分配菜单与角色扩展', 1, 0, 0, NOW(3), NOW(3)),
(2, 'plat_user', '平台普通用户', '仅控制台入口', 1, 0, 0, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 菜单树：目录无 perm_code；叶子带 perm_code 与 PlatPermCodes 一致
INSERT INTO un_plat_menu (id, parent_id, menu_name, menu_type, path, perm_code, icon, sort_no, visible_flag, status, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(1, 0, '控制台', 2, '/platform', 'CONSOLE', NULL, 0, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(2, 0, '系统管理', 1, '/platform/system', NULL, NULL, 10, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(3, 2, '创建系统', 2, NULL, 'SYSTEM_CREATE', NULL, 1, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(4, 2, '启停系统', 2, NULL, 'SYSTEM_STATUS', NULL, 2, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(5, 2, '删除系统', 2, NULL, 'SYSTEM_DELETE', NULL, 3, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(6, 0, '应用管理', 1, '/platform/app', NULL, NULL, 20, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(7, 6, '新建应用', 2, NULL, 'APP_CREATE', NULL, 1, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(8, 6, '删除应用', 2, NULL, 'APP_DELETE', NULL, 2, 1, 1, NULL, 0, 0, NOW(3), NOW(3)),
(9, 6, '启停应用', 2, NULL, 'APP_STATUS', NULL, 3, 1, 1, NULL, 0, 0, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

DELETE FROM un_plat_role_menu WHERE role_id IN (1, 2);

-- 超级管理员：全部菜单 1-9
INSERT INTO un_plat_role_menu (id, role_id, menu_id, create_user_id, update_user_id, create_time, update_time)
VALUES
(1, 1, 1, 0, 0, NOW(3), NOW(3)),
(2, 1, 2, 0, 0, NOW(3), NOW(3)),
(3, 1, 3, 0, 0, NOW(3), NOW(3)),
(4, 1, 4, 0, 0, NOW(3), NOW(3)),
(5, 1, 5, 0, 0, NOW(3), NOW(3)),
(6, 1, 6, 0, 0, NOW(3), NOW(3)),
(7, 1, 7, 0, 0, NOW(3), NOW(3)),
(8, 1, 8, 0, 0, NOW(3), NOW(3)),
(9, 1, 9, 0, 0, NOW(3), NOW(3));

-- 普通用户：仅控制台
INSERT INTO un_plat_role_menu (id, role_id, menu_id, create_user_id, update_user_id, create_time, update_time)
VALUES
(10, 2, 1, 0, 0, NOW(3), NOW(3));

-- 注意：账号与角色的绑定在注册时由程序写入 un_plat_account_role，此处不插账号数据
