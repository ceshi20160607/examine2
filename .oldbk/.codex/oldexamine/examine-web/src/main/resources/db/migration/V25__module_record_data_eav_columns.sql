-- 旧库 un_module_record_data 仍为 data_json 结构时：补齐 EAV 列 field_code / value_text（幂等）
SET NAMES utf8mb4;
SET @db = DATABASE();

SET @c_fc := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_record_data' AND COLUMN_NAME = 'field_code'
);
SET @sql_fc := IF(
    @c_fc = 0,
    'ALTER TABLE un_module_record_data ADD COLUMN field_code VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''字段编码'' AFTER record_id',
    'SELECT 1'
);
PREPARE stmt_fc FROM @sql_fc;
EXECUTE stmt_fc;
DEALLOCATE PREPARE stmt_fc;

SET @c_vt := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_record_data' AND COLUMN_NAME = 'value_text'
);
SET @sql_vt := IF(
    @c_vt = 0,
    'ALTER TABLE un_module_record_data ADD COLUMN value_text MEDIUMTEXT NULL COMMENT ''字段值'' AFTER field_code',
    'SELECT 1'
);
PREPARE stmt_vt FROM @sql_vt;
EXECUTE stmt_vt;
DEALLOCATE PREPARE stmt_vt;

-- 旧列 data_json 存在时删除（旧单行 JSON 模式，与 EAV 不兼容；有数据请先备份并手工迁移）
SET @c_dj := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_record_data' AND COLUMN_NAME = 'data_json'
);
SET @sql_dj := IF(
    @c_dj > 0,
    'ALTER TABLE un_module_record_data DROP COLUMN data_json',
    'SELECT 1'
);
PREPARE stmt_dj FROM @sql_dj;
EXECUTE stmt_dj;
DEALLOCATE PREPARE stmt_dj;
