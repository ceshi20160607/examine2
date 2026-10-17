-- 用途：将既有平台审计事件绑定到唯一平台，供平台后台按平台主键安全查询。
-- 升级前备份数据库；升级后对照 PLATFORM 事件总数、已绑定数及仍无归属数。
-- 多平台数据库无法从旧事件反推出归属，保持无归属且不对任何平台展示，需人工依据外部证据修复。
UPDATE `un_audit_event` AS event_row
JOIN (
  SELECT MIN(`id`) AS `platform_id`
  FROM `un_plat_platform`
  HAVING COUNT(*) = 1
) AS only_platform
SET event_row.`platform_id` = only_platform.`platform_id`
WHERE event_row.`context_type` = 'PLATFORM'
  AND event_row.`platform_id` IS NULL
  AND event_row.`system_id` IS NULL
  AND event_row.`tenant_id` IS NULL;
