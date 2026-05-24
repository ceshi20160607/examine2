SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version IN ('14','21','22','23') OR success = 0 ORDER BY installed_rank;
SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'un_module_record_data' AND COLUMN_NAME IN ('value_num','value_dt');
SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'un_module_role' AND COLUMN_NAME = 'data_scope';
