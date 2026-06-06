-- Baseline: flow seed (from docs/sql/08_flow_seed.sql)
SET NAMES utf8mb4;

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

