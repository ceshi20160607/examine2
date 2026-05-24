-- V14 已手工执行：在 flyway_schema_history 登记 success=1，Flyway 从 V15 继续
SET NAMES utf8mb4;

DELETE FROM flyway_schema_history WHERE version = '14' AND success = 0;

INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history f),
       '14', 'module field ref', 'SQL', 'V14__module_field_ref.sql', 716151889, 'manual', NOW(), 0, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '14' AND success = 1
);
