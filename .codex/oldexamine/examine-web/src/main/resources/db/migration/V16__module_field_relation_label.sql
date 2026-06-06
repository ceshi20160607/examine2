-- 关联模块字段：固化关联模块在表单/子表上的展示名称
ALTER TABLE un_module_field
    ADD COLUMN relation_module_label VARCHAR(128) NULL
        COMMENT '关联模块展示名（field_type=REF_MODULE 时）'
        AFTER ref_display_field;
