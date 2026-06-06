-- 旧库 un_flow_task 仍为 instance_id / node_id 时，对齐为 record_id / node_key（幂等）
SET NAMES utf8mb4;
SET @db = DATABASE();

SET @c_rid := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_flow_task' AND COLUMN_NAME = 'record_id'
);
SET @c_iid := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_flow_task' AND COLUMN_NAME = 'instance_id'
);
SET @sql_rid := IF(
    @c_rid = 0 AND @c_iid > 0,
    'ALTER TABLE un_flow_task CHANGE COLUMN instance_id record_id BIGINT NOT NULL COMMENT ''流程实例记录ID（un_flow_record.id）''',
    IF(@c_rid = 0,
       'ALTER TABLE un_flow_task ADD COLUMN record_id BIGINT NOT NULL DEFAULT 0 COMMENT ''流程实例记录ID（un_flow_record.id）'' AFTER tenant_id',
       'SELECT 1')
);
PREPARE stmt_rid FROM @sql_rid;
EXECUTE stmt_rid;
DEALLOCATE PREPARE stmt_rid;

SET @c_nk := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_flow_task' AND COLUMN_NAME = 'node_key'
);
SET @c_nid := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_flow_task' AND COLUMN_NAME = 'node_id'
);
SET @sql_nk := IF(
    @c_nk = 0 AND @c_nid > 0,
    'ALTER TABLE un_flow_task CHANGE COLUMN node_id node_key VARCHAR(64) NOT NULL COMMENT ''节点 node_key''',
    IF(@c_nk = 0,
       'ALTER TABLE un_flow_task ADD COLUMN node_key VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''节点 node_key'' AFTER record_id',
       'SELECT 1')
);
PREPARE stmt_nk FROM @sql_nk;
EXECUTE stmt_nk;
DEALLOCATE PREPARE stmt_nk;

-- 旧索引名 instance 相关时重建 record 索引（忽略不存在）
SET @idx_old := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_flow_task' AND INDEX_NAME = 'idx_flow_task_instance'
);
SET @sql_drop := IF(@idx_old > 0, 'ALTER TABLE un_flow_task DROP INDEX idx_flow_task_instance', 'SELECT 1');
PREPARE stmt_drop FROM @sql_drop;
EXECUTE stmt_drop;
DEALLOCATE PREPARE stmt_drop;

SET @idx_rec := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_flow_task' AND INDEX_NAME = 'idx_flow_task_record'
);
SET @sql_idx := IF(
    @idx_rec = 0,
    'ALTER TABLE un_flow_task ADD KEY idx_flow_task_record (system_id, tenant_id, record_id, status, update_time)',
    'SELECT 1'
);
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;
