-- V14（ref_model_id / ref_display_field）已手工执行过时，只需修 Flyway 历史，勿再 ALTER。
-- 执行后重启 examine-web（请使用含幂等 V14 的最新 JAR）。

SET NAMES utf8mb4;

-- 1) 删除 V14 全部历史（列已手工加过时推荐；下次启动幂等脚本会重新登记 success=1）
DELETE FROM flyway_schema_history WHERE version = '14';
DELETE FROM flyway_schema_history WHERE success = 0;

-- 2) 可选：去掉所有失败记录（与其它版本失败一并清理）
-- DELETE FROM flyway_schema_history WHERE success = 0;

-- 3) 自检：两列应已存在（不存在才需要补跑 V14 正文）
SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'un_module_field'
  AND COLUMN_NAME IN ('ref_model_id', 'ref_display_field');

-- 说明：若上表返回 2 行，下次启动时 Flyway 会重跑 V14 脚本（仓库内已幂等，仅 SELECT 1），
--       并在 flyway_schema_history 写入 success=1，然后继续 V15～V23（baseline 22 时仅 V23 等更高版本）。
