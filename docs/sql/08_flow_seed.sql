-- flow（流程/审批）模块默认数据（MVP）
SET NAMES utf8mb4;

-- 约定：system_id=0 tenant_id=0 为默认/演示作用域
-- 说明：此处使用固定 ID 便于本地初始化与联调；生产环境建议使用雪花 ID 生成。

INSERT IGNORE INTO un_flow_definition
(id, system_id, tenant_id, def_code, def_name, category_code, status, latest_version_no, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 'demo_approve', '演示审批流程', 'demo', 1, 1, 'flow 模块默认演示流程', 0, 0, NOW(3), NOW(3));

-- 一个最小流程图快照：start -> approve1 -> end
INSERT IGNORE INTO un_flow_definition_version
(id, system_id, tenant_id, def_id, version_no, publish_status, graph_json, form_json, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 1, 2,
 JSON_OBJECT(
   'nodes', JSON_ARRAY(
     JSON_OBJECT('id','start','type','start','name','开始'),
     JSON_OBJECT('id','approve1','type','approve','name','审批'),
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

