-- app（对外开放 API）模块默认数据（MVP）
SET NAMES utf8mb4;

-- 约定：system_id=0 tenant_id=0 为默认/演示作用域
-- 说明：此处仅提供演示 client，并默认设置为“停用”，避免误用。
-- 如需启用，请自行修改 status=1，并替换 access_key / secret_hash。

INSERT IGNORE INTO un_app_client
(id, system_id, tenant_id, client_code, client_name, contact_name, contact_mobile, contact_email, status, remark, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 'demo', '演示 Client（默认停用）', NULL, NULL, NULL, 2, 'app 模块默认演示 client', 0, 0, NOW(3), NOW(3));

-- secret_hash 仅示意：请替换为 BCrypt(secret) 或其它强哈希结果
INSERT IGNORE INTO un_app_client_credential
(id, system_id, tenant_id, client_id, access_key, secret_hash, status, expired_time, last_used_time, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 'demo-ak', '{bcrypt}$2a$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 2, NULL, NULL, 0, 0, NOW(3), NOW(3));

INSERT IGNORE INTO un_app_client_scope
(id, system_id, tenant_id, client_id, scope_code, status, create_user_id, update_user_id, create_time, update_time)
VALUES
(0, 0, 0, 0, 'api:*', 2, 0, 0, NOW(3), NOW(3));

