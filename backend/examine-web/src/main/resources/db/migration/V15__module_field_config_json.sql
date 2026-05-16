-- 字段类型扩展配置（各类型专属属性 JSON）
ALTER TABLE un_module_field
    ADD COLUMN config_json JSON NULL COMMENT '字段类型扩展配置 JSON' AFTER ref_display_field;
