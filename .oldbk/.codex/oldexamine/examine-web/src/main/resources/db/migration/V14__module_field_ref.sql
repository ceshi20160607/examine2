-- 关联字段：指向同 app 下其它 model 的 recordId（幂等：列已存在则跳过）
SET @db = DATABASE();
SET @c1 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_field' AND COLUMN_NAME = 'ref_model_id'
);
SET @sql1 := IF(
    @c1 = 0,
    'ALTER TABLE un_module_field ADD COLUMN ref_model_id BIGINT NULL COMMENT ''关联目标 un_module_model.id（field_type=ref/relation 时）'' AFTER dict_code',
    'SELECT 1'
);
PREPARE stmt1 FROM @sql1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @c2 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'un_module_field' AND COLUMN_NAME = 'ref_display_field'
);
SET @sql2 := IF(
    @c2 = 0,
    'ALTER TABLE un_module_field ADD COLUMN ref_display_field VARCHAR(64) NULL COMMENT ''关联展示用 fieldCode（目标 model 的字段）'' AFTER ref_model_id',
    'SELECT 1'
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
