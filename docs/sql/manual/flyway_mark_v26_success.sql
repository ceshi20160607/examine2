-- 已执行 docs/sql/19_flow_task_column_align.sql 时：登记 V26 成功，避免重复 ALTER
SET NAMES utf8mb4;

DELETE FROM flyway_schema_history WHERE version = '26' AND success = 0;

INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history f),
       '26', 'flow task column align', 'SQL', 'V26__flow_task_column_align.sql', NULL, 'manual', NOW(), 0, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '26' AND success = 1
);
