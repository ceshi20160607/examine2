-- Fixed platform super admin: admin / 123123aa (BCrypt)
SET NAMES utf8mb4;

SET @pwd_hash = '$2a$10$ZdoLC4pjkS5.oWOQqFmP1.zXYfo6DS4AZM8E5jicz83lWnohCOnZ.';
SET @admin_id = 9100000000000000000;
SET @bind_id = 9100000000000000001;

INSERT INTO un_plat_account (
  id, username, password_hash, status, create_time, update_time
)
SELECT @admin_id, 'admin', @pwd_hash, 1, NOW(3), NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM un_plat_account WHERE username = 'admin');

UPDATE un_plat_account
SET password_hash = @pwd_hash,
    status = 1,
    update_time = NOW(3)
WHERE username = 'admin';

DELETE ar
FROM un_plat_account_role ar
INNER JOIN un_plat_account a ON a.id = ar.plat_account_id
WHERE a.username = 'admin';

INSERT INTO un_plat_account_role (
  id, plat_account_id, role_id, create_user_id, update_user_id, create_time, update_time
)
SELECT @bind_id, a.id, 1, a.id, a.id, NOW(3), NOW(3)
FROM un_plat_account a
WHERE a.username = 'admin';
