-- =============================================================================
-- examine2 基础数据导入（合并版）
-- -----------------------------------------------------------------------------
-- 内容来源：与以下 Flyway 种子迁移一致（请勿手工与 Flyway 重复执行到“冲突”未评估的环境）
--   V2__platform_seed.sql
--   V4__upload_seed.sql
--   V6__module_seed.sql
--   V8__flow_seed.sql
--   V10__app_seed.sql
--   V12__plat_rbac_seed.sql
--   V13__plat_rbac_backfill_account_role.sql
--
-- 使用说明：
-- 1) 正常部署：启动 examine-web，Flyway 会自动执行全部 migration（含本文件中的数据），通常不需要单独执行本文件。
-- 2) 仅当：表结构已按当前版本就绪（已执行对应 DDL/迁移），需要人工补种或灾备恢复数据时，在评估后可执行本脚本。
-- 3) 全新空库请优先用 Flyway 全量，不要只执行本文件（会缺少表结构）。
-- =============================================================================

SET NAMES utf8mb4;

-- ========== V2 platform seed ==========
INSERT IGNORE INTO un_plat_system (id, name, icon_url, multi_tenant_enabled, default_tenant_id, status, owner_plat_account_id, create_time, update_time)
VALUES (0, '平台', NULL, 0, 0, 1, NULL, NOW(3), NOW(3));

INSERT IGNORE INTO un_plat_tenant (id, system_id, name, status, create_time, update_time)
VALUES (0, 0, '默认租户', 1, NOW(3), NOW(3));

-- ========== V4 upload seed ==========
INSERT IGNORE INTO un_upload_storage_config (
  id,
  system_id,
  tenant_id,
  storage_type,
  local_root_path,
  local_public_base_url,
  endpoint,
  region,
  bucket,
  base_path,
  public_base_url,
  param_json,
  status,
  remark,
  create_user_id,
  update_user_id,
  create_time,
  update_time
) VALUES (
  0,
  0,
  0,
  'local',
  'D:\\data\\uploads',
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  NULL,
  1,
  '默认本地存储配置（可按环境修改）',
  NULL,
  NULL,
  NOW(3),
  NOW(3)
);

-- ========== V6 module seed ==========
INSERT IGNORE INTO un_module_app
(id, system_id, tenant_id, app_code, app_name, icon_url, status, published_flag, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 'demo', '演示应用', NULL, 1, 0, 'module 模块默认演示应用', 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_module_app_version
(id, system_id, tenant_id, app_id, version_no, status, snapshot_json, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 1, 1, NULL, 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_module_page
(id, system_id, tenant_id, app_id, page_code, page_name, page_type, route_path, config_json, form_fields_json, status, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 'home', '首页', 'custom', '/demo/home', JSON_OBJECT('title','演示首页'), NULL, 1, 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_module_menu
(id, system_id, tenant_id, app_id, parent_id, menu_name, page_id, sort_no, visible_flag, perm_key, api_pattern, module_fields_json, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 0, '首页', 0, 0, 1, NULL, NULL, NULL, 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_module_model
(id, system_id, tenant_id, app_id, model_code, model_name, status, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 'customer', '客户', 1, '演示模型', 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_module_field
(id, system_id, tenant_id, app_id, model_id, field_code, field_name, field_type, required_flag, unique_flag, hidden_flag, tips, max_length, min_length, validate_type, date_format, dict_code, multi_flag, default_value, sort_no, status, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 0, 'name', '姓名', 'string', 1, 0, 0, '请输入姓名', 50, NULL, NULL, NULL, NULL, 0, NULL, 10, 1, 0, 0, NOW(3), NOW(3)),
(1, 0, 0, 0, 0, 'mobile', '手机号', 'string', 0, 0, 0, '请输入手机号', 11, NULL, 'phone', NULL, NULL, 0, NULL, 20, 1, 0, 0, NOW(3), NOW(3)),
(2, 0, 0, 0, 0, 'createdAt', '创建时间', 'datetime', 0, 0, 0, NULL, NULL, NULL, NULL, 'yyyymmdd', NULL, 0, NULL, 30, 1, 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_module_role
(id, system_id, tenant_id, app_id, role_code, role_name, status, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 'admin', '管理员', 1, 0, 0, NOW(3), NOW(3));

-- ========== V8 flow seed ==========
INSERT IGNORE INTO un_flow_temp
(id, system_id, tenant_id, temp_code, temp_name, category_code, status, latest_ver_no, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 'demo_approve', '演示审批流程', 'demo', 1, 1, 'flow 模块默认演示流程', 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_flow_temp_ver
(id, system_id, tenant_id, temp_id, ver_no, publish_status, graph_json, form_json, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 1, 2,
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','start','type','start','name','开始'),
     JSON_OBJECT('id','approve1','type','approve','name','审批'),
     JSON_OBJECT('id','approve2','type','approve','name','二次审批'),
     JSON_OBJECT('id','end','type','end','name','结束')
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','start','to','approve1'),
     JSON_OBJECT('from','approve1','to','end','cond','eq(amount, 100)','priority',1),
     JSON_OBJECT('from','approve1','to','approve2','priority',2),
     JSON_OBJECT('from','approve2','to','end')
   ),
   'config', JSON_OBJECT(
     'mvp', true,
     'exception_policy', JSON_OBJECT('mode','fallback_admin','admin_plat_id',0)
   )
 ),
 NULL,
 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_flow_temp
(id, system_id, tenant_id, temp_code, temp_name, category_code, status, latest_ver_no, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(1, 0, 0, 'demo_subflow_child', '演示子流程（单审批）', 'demo', 1, 1, '供 demo_parent_subflow 的 subflow 节点引用', 0, 0, NOW(3), NOW(3)),
(2, 0, 0, 'demo_parent_subflow', '演示父流程（含 subflow）', 'demo', 1, 1, '一审通过后进入子流程，子结束后回到二审', 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_flow_temp_ver
(id, system_id, tenant_id, temp_id, ver_no, publish_status, graph_json, form_json, create_user_id, update_user_id, create_time, update_time)
VALUES
(1, 0, 0, 1, 1, 2,
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','start','type','start','name','开始'),
     JSON_OBJECT('id','approve_c','type','approve','name','子流程内审批'),
     JSON_OBJECT('id','end','type','end','name','结束')
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','start','to','approve_c'),
     JSON_OBJECT('from','approve_c','to','end')
   ),
   'config', JSON_OBJECT('mvp', true)
 ),
 NULL,
 0, 0, NOW(3), NOW(3)),
(2, 0, 0, 2, 1, 2,
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','start','type','start','name','开始'),
     JSON_OBJECT('id','approve1','type','approve','name','父-一审'),
     JSON_OBJECT(
       'id','sf1',
       'type','subflow',
       'name','子流程',
       'config', JSON_OBJECT('sub_temp_code','demo_subflow_child','copy_vars', TRUE)
     ),
     JSON_OBJECT('id','approve2','type','approve','name','父-二审'),
     JSON_OBJECT('id','end','type','end','name','结束')
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','start','to','approve1'),
     JSON_OBJECT('from','approve1','to','sf1'),
     JSON_OBJECT('from','sf1','to','approve2'),
     JSON_OBJECT('from','approve2','to','end')
   ),
   'config', JSON_OBJECT('mvp', true)
 ),
 NULL,
 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_flow_temp
(id, system_id, tenant_id, temp_code, temp_name, category_code, status, latest_ver_no, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(3, 0, 0, 'demo_cc', '演示抄送节点', 'demo', 1, 1, '一审后抄送再结束；plat_ids 空则抄送给发起人', 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_flow_temp_ver
(id, system_id, tenant_id, temp_id, ver_no, publish_status, graph_json, form_json, create_user_id, update_user_id, create_time, update_time)
VALUES
(3, 0, 0, 3, 1, 2,
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','start','type','start','name','开始'),
     JSON_OBJECT('id','approve1','type','approve','name','审批'),
     JSON_OBJECT(
       'id','cc1',
       'type','cc',
       'name','抄送',
       'config', JSON_OBJECT('plat_ids', JSON_ARRAY())
     ),
     JSON_OBJECT('id','end','type','end','name','结束')
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','start','to','approve1'),
     JSON_OBJECT('from','approve1','to','cc1'),
     JSON_OBJECT('from','cc1','to','end')
   ),
   'config', JSON_OBJECT('mvp', true)
 ),
 NULL,
 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_flow_temp
(id, system_id, tenant_id, temp_code, temp_name, category_code, status, latest_ver_no, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(4, 0, 0, 'demo_countersign_all', '演示会签（全员同意）', 'demo', 1, 1, 'sign_mode=all；plat_ids 示例 1、2', 0, 0, NOW(3), NOW(3)),
(5, 0, 0, 'demo_any_sign', '演示或签（一人同意即可）', 'demo', 1, 1, 'sign_mode=any；plat_ids 示例 1、2', 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_flow_temp_ver
(id, system_id, tenant_id, temp_id, ver_no, publish_status, graph_json, form_json, create_user_id, update_user_id, create_time, update_time)
VALUES
(4, 0, 0, 4, 1, 2,
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','start','type','start','name','开始'),
     JSON_OBJECT(
       'id','approve1',
       'type','approve',
       'name','会签审批',
       'config', JSON_OBJECT('sign_mode','all','plat_ids', JSON_ARRAY(1, 2))
     ),
     JSON_OBJECT('id','end','type','end','name','结束')
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','start','to','approve1'),
     JSON_OBJECT('from','approve1','to','end')
   ),
   'config', JSON_OBJECT('mvp', true)
 ),
 NULL,
 0, 0, NOW(3), NOW(3)),
(5, 0, 0, 5, 1, 2,
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','start','type','start','name','开始'),
     JSON_OBJECT(
       'id','approve1',
       'type','approve',
       'name','或签审批',
       'config', JSON_OBJECT('sign_mode','any','plat_ids', JSON_ARRAY(1, 2))
     ),
     JSON_OBJECT('id','end','type','end','name','结束')
   ),
   'edges', JSON_ARRAY(
     JSON_OBJECT('from','start','to','approve1'),
     JSON_OBJECT('from','approve1','to','end')
   ),
   'config', JSON_OBJECT('mvp', true)
 ),
 NULL,
 0, 0, NOW(3), NOW(3));

-- ========== V10 app seed ==========
INSERT IGNORE INTO un_app_client
(id, system_id, tenant_id, client_code, client_name, contact_name, contact_mobile, contact_email, status, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 'demo', '演示 Client（默认停用）', NULL, NULL, NULL, 2, 'app 模块默认演示 client', 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_app_client_credential
(id, system_id, tenant_id, client_id, access_key, secret_hash, status, expired_time, last_used_time, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 'demo-ak', '{bcrypt}$2a$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 2, NULL, NULL, 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_app_client_scope
(id, system_id, tenant_id, client_id, scope_code, status, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 'api:*', 2, 0, 0, NOW(3), NOW(3));

-- ========== V12 plat_rbac seed ==========
INSERT INTO un_plat_role (id, role_code, role_name, remark, status, create_user_id, update_user_id, create_time, update_time)
VALUES
(1, 'plat_super_admin', '平台超级管理员', '首账号默认；可分配菜单与角色扩展', 1, 0, 0, NOW(3), NOW(3)),
(2, 'plat_user', '平台普通用户', '仅控制台入口', 1, 0, 0, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

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

INSERT INTO un_plat_role_menu (id, role_id, menu_id, create_user_id, update_user_id, create_time, update_time)
VALUES
(10, 2, 1, 0, 0, NOW(3), NOW(3));

-- ========== V13 plat_rbac backfill ==========
INSERT INTO un_plat_account_role (id, plat_account_id, role_id, create_user_id, update_user_id, create_time, update_time)
SELECT
  (UNIX_TIMESTAMP(NOW(3)) * 1000000 + a.id) AS id,
  a.id AS plat_account_id,
  CASE WHEN a.id = (SELECT MIN(id) FROM un_plat_account) THEN 1 ELSE 2 END AS role_id,
  a.id,
  a.id,
  NOW(3),
  NOW(3)
FROM un_plat_account a
WHERE NOT EXISTS (
  SELECT 1 FROM un_plat_account_role ar WHERE ar.plat_account_id = a.id
);
