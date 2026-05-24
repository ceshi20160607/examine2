-- 已执行 docs/sql/17_module_record_data_typed_alter.sql 时：登记 V23 成功，避免重复 ADD COLUMN
SET NAMES utf8mb4;

DELETE FROM flyway_schema_history WHERE version = '23' AND success = 0;

INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history f),
       '23', 'module record data typed', 'SQL', 'V23__module_record_data_typed.sql', 1997050609, 'manual', NOW(), 0, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '23' AND success = 1
);
