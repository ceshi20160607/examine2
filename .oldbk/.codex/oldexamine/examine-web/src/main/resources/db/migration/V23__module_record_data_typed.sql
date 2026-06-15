-- EAV typed 列：数值/日期时间（幂等：列/索引已存在则跳过）
SET NAMES utf8mb4;

SET @db = DATABASE();

SET @c_num := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_record_data' AND COLUMN_NAME = 'value_num'
);
SET @sql_num := IF(
    @c_num = 0,
    'ALTER TABLE un_module_record_data ADD COLUMN value_num DECIMAL(38,10) NULL COMMENT ''数值/金额/百分比/布尔 typed 列'' AFTER value_text',
    'SELECT 1'
);
PREPARE stmt_num FROM @sql_num;
EXECUTE stmt_num;
DEALLOCATE PREPARE stmt_num;

SET @c_dt := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_record_data' AND COLUMN_NAME = 'value_dt'
);
SET @sql_dt := IF(
    @c_dt = 0,
    'ALTER TABLE un_module_record_data ADD COLUMN value_dt DATETIME(3) NULL COMMENT ''日期时间 typed 列'' AFTER value_num',
    'SELECT 1'
);
PREPARE stmt_dt FROM @sql_dt;
EXECUTE stmt_dt;
DEALLOCATE PREPARE stmt_dt;

SET @i_num := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_record_data' AND INDEX_NAME = 'idx_module_record_data_num'
);
SET @sql_in := IF(
    @i_num = 0,
    'CREATE INDEX idx_module_record_data_num ON un_module_record_data (model_id, field_code, value_num)',
    'SELECT 1'
);
PREPARE stmt_in FROM @sql_in;
EXECUTE stmt_in;
DEALLOCATE PREPARE stmt_in;

SET @i_dt := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_record_data' AND INDEX_NAME = 'idx_module_record_data_dt'
);
SET @sql_id := IF(
    @i_dt = 0,
    'CREATE INDEX idx_module_record_data_dt ON un_module_record_data (model_id, field_code, value_dt)',
    'SELECT 1'
);
PREPARE stmt_id FROM @sql_id;
EXECUTE stmt_id;
DEALLOCATE PREPARE stmt_id;
