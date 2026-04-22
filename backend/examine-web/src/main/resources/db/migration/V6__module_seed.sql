-- Baseline: module seed (from docs/sql/06_module_seed.sql)
SET NAMES utf8mb4;

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

