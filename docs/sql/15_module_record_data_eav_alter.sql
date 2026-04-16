-- 从「单 record 一行 + data_json」迁移为 EAV（record_id + field_code 唯一）
-- 仅用于已按旧版 05_module_ddl 建库的库；全新安装直接用新版 05 即可，无需执行本脚本。
-- 执行前请备份；旧 data_json 中的数据需自行迁移为多行（本脚本不自动拆 JSON）。

SET NAMES utf8mb4;

-- 若存在旧唯一索引 (record_id)
-- ALTER TABLE un_module_record_data DROP INDEX uk_module_record_data;

-- 若表仍为旧结构，按需执行（列已存在会失败，请按实际裁剪）：
-- ALTER TABLE un_module_record_data
--   ADD COLUMN field_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '字段编码' AFTER record_id,
--   ADD COLUMN value_text MEDIUMTEXT NULL COMMENT '字段值' AFTER field_code;
-- ALTER TABLE un_module_record_data DROP COLUMN data_json;
-- ALTER TABLE un_module_record_data ADD UNIQUE KEY uk_module_record_data_field (record_id, field_code);
-- ALTER TABLE un_module_record_data ADD KEY idx_module_record_data_lookup (model_id, field_code, record_id);
