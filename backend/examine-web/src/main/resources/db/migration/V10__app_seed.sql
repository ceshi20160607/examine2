-- Baseline: app seed (from docs/sql/10_app_seed.sql)
SET NAMES utf8mb4;

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

