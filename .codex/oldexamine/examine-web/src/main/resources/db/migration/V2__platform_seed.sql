-- Baseline: platform seed (from docs/sql/02_platform_seed.sql)
SET NAMES utf8mb4;

INSERT IGNORE INTO un_plat_system (id, name, icon_url, multi_tenant_enabled, default_tenant_id, status, owner_plat_account_id, create_time, update_time)
VALUES (0, '平台', NULL, 0, 0, 1, NULL, NOW(3), NOW(3));

INSERT IGNORE INTO un_plat_tenant (id, system_id, name, status, create_time, update_time)
VALUES (0, 0, '默认租户', 1, NOW(3), NOW(3));

