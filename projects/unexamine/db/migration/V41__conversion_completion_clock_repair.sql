-- 修复已确认的转化时间混用：旧容器以 UTC 写 finished_at，创建和成功审计由 +08:00 数据库连接写入。
-- 只处理完成早于创建、加八小时不早于创建，且与同一转化成功审计相差不超过两秒的记录。
-- 不修改来源/目标正文、关系状态、版本、映射快照及原成功审计。未满足证据条件的数据保持原样。
-- 先追加含原值、修复值的永久审计，供追溯和按记录恢复；禁止对所有历史时间统一加八小时。
INSERT INTO audit_event
    (trace_id, request_id, context_type, system_id, tenant_id, event_category,
     event_code, object_type, object_id, result_code, detail_json)
SELECT 'migration-v41-conversion-clock', 'migration-v41-conversion-clock', 'SYSTEM',
       c.system_id, c.tenant_id, 'DATA_REPAIR', 'CONVERSION_CLOCK_REPAIRED',
       'BUSINESS_RECORD_CONVERSION', CAST(c.id AS CHAR), 'SUCCESS',
       JSON_OBJECT('beforeTime', DATE_FORMAT(c.finished_at, '%Y-%m-%d %H:%i:%s.%f'),
                   'afterTime', DATE_FORMAT(DATE_ADD(c.finished_at, INTERVAL 8 HOUR), '%Y-%m-%d %H:%i:%s.%f'),
                   'reason', '转化完成时间与数据库业务时区对齐，原值保留用于恢复')
FROM biz_record_conversion c
WHERE c.status IN ('SUCCEEDED', 'UNLINKED')
  AND c.finished_at < c.created_at
  AND DATE_ADD(c.finished_at, INTERVAL 8 HOUR) >= c.created_at
  AND EXISTS (
      SELECT 1 FROM audit_event a
      WHERE a.system_id = c.system_id AND a.tenant_id = c.tenant_id
        AND a.event_code = 'BUSINESS_RECORD_CONVERTED' AND a.result_code = 'SUCCESS'
        AND a.object_type = 'BUSINESS_RECORD' AND a.object_id = CAST(c.source_record_id AS CHAR)
        AND JSON_EXTRACT(a.detail_json, '$.conversionId') = c.id
        AND ABS(TIMESTAMPDIFF(MICROSECOND, DATE_ADD(c.finished_at, INTERVAL 8 HOUR), a.occurred_at)) <= 2000000)
  AND NOT EXISTS (
      SELECT 1 FROM audit_event r
      WHERE r.event_code = 'CONVERSION_CLOCK_REPAIRED' AND r.object_type = 'BUSINESS_RECORD_CONVERSION'
        AND r.object_id = CAST(c.id AS CHAR) AND r.request_id = 'migration-v41-conversion-clock');

-- 仅应用刚才审计中的明确修复；原值校验保证重跑不会重复平移或覆盖其他修复。
UPDATE biz_record_conversion c
JOIN audit_event r ON r.object_id = CAST(c.id AS CHAR)
  AND r.system_id = c.system_id AND r.tenant_id = c.tenant_id
  AND r.event_code = 'CONVERSION_CLOCK_REPAIRED' AND r.object_type = 'BUSINESS_RECORD_CONVERSION'
  AND r.request_id = 'migration-v41-conversion-clock'
SET c.finished_at = CAST(JSON_UNQUOTE(JSON_EXTRACT(r.detail_json, '$.afterTime')) AS DATETIME(3))
WHERE c.finished_at = CAST(JSON_UNQUOTE(JSON_EXTRACT(r.detail_json, '$.beforeTime')) AS DATETIME(3));

-- 恢复方案：从修复审计取 beforeTime，仅在当前值仍等于 afterTime 时按 conversion id 恢复；
-- 原成功审计及修复审计均保留，并为恢复另记审计。不能重写已被后续人工修复的时间。
