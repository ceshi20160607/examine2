-- 启动前一次性修复（V14 已手工执行、或存在其它 failed 记录时）
SET NAMES utf8mb4;

DELETE FROM flyway_schema_history WHERE success = 0;
DELETE FROM flyway_schema_history WHERE version = '14' AND success = 0;
