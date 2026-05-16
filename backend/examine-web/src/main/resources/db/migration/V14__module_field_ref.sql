-- 关联字段：指向同 app 下其它 model 的 recordId
ALTER TABLE un_module_field
    ADD COLUMN ref_model_id BIGINT NULL COMMENT '关联目标 un_module_model.id（field_type=ref/relation 时）' AFTER dict_code,
    ADD COLUMN ref_display_field VARCHAR(64) NULL COMMENT '关联展示用 fieldCode（目标 model 的字段）' AFTER ref_model_id;
