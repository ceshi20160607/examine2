-- 已废弃：勿删除 V14 历史。V14 列已存在时请用 flyway_mark_v14_applied.sql 或直接启动应用（自动登记）。
SET NAMES utf8mb4;
DELETE FROM flyway_schema_history WHERE success = 0;
