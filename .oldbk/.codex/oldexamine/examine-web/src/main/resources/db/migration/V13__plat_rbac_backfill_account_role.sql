-- Optional backfill (converted from docs/sql/14_plat_rbac_backfill_account_role.sql)
-- Rule: smallest un_plat_account.id -> super_admin(1), others -> user(2)
SET NAMES utf8mb4;

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

