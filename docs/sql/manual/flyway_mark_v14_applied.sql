-- V14 已手工执行：登记 flyway_schema_history，让应用从 V15 继续（勿再跑 V14 ALTER）
-- 推荐：直接启动 examine-web（FlywayStartupConfig 会自动登记）
-- 或本脚本 + 将 checksum 换成 FlywayMarkV14.java 打印值后执行

SET NAMES utf8mb4;

DELETE FROM flyway_schema_history WHERE version = '14' AND success = 0;

INSERT INTO flyway_schema_history (
    version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT
    '14', 'module field ref', 'SQL', 'V14__module_field_ref.sql', 0, 'manual-sql', NOW(), 0, 1
FROM DUAL
WHERE EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'un_module_field'
      AND COLUMN_NAME = 'ref_model_id'
)
AND NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '14' AND success = 1
);
