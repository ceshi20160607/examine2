-- 可选：已有库在上线 RBAC 后，为「尚无 un_plat_account_role 行」的账号补角色。
-- 规则：plat_account.id 最小的账号绑 plat_super_admin(1)，其余绑 plat_user(2)。
-- 执行前请先执行 12_plat_rbac_ddl.sql 与 13_plat_rbac_seed.sql。

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
