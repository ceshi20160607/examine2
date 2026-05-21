-- 已有库增量：un_module_record_data 增加 typed 列（可重复执行时请自行判断列是否已存在）
SET NAMES utf8mb4;

ALTER TABLE un_module_record_data
  ADD COLUMN value_num DECIMAL(38,10) NULL COMMENT '数值/金额/百分比/布尔 typed 列' AFTER value_text,
  ADD COLUMN value_dt DATETIME(3) NULL COMMENT '日期时间 typed 列' AFTER value_num;

CREATE INDEX idx_module_record_data_num ON un_module_record_data (model_id, field_code, value_num);
CREATE INDEX idx_module_record_data_dt ON un_module_record_data (model_id, field_code, value_dt);
