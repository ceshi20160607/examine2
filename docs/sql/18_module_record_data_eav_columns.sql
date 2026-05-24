-- 手工：旧库补齐 un_module_record_data.field_code / value_text（与 Flyway V25 相同）
-- 执行前备份；若表内有 data_json 业务数据需先迁移再 DROP data_json

SET NAMES utf8mb4;

ALTER TABLE un_module_record_data
  ADD COLUMN field_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '字段编码' AFTER record_id,
  ADD COLUMN value_text MEDIUMTEXT NULL COMMENT '字段值' AFTER field_code;

-- 列已存在则跳过上面，仅执行：
-- ALTER TABLE un_module_record_data DROP COLUMN data_json;
