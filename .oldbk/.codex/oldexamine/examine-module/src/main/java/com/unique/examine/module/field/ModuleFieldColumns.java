package com.unique.examine.module.field;

/**
 * un_module_field 列分工说明（固化列 vs config_json）。
 * <p>
 * 固化列：高频、稳定、可能参与查询/展示的属性。
 * config_json：各类型样式与复杂规则（displayStyle、listFields、segments、地址级联等）。
 */
public final class ModuleFieldColumns {

    private ModuleFieldColumns() {}

    /** 所有类型：field_type, field_code, field_name, tips, default_value, flags, sort_no, status */
    /** TEXT：validate_type(max_length 可列或 config.inputStyle/maxLength) */
    /** DATETIME：date_format；min/max 在 config_json */
    /** SELECT/MULTI_SELECT：dict_code；displayStyle 在 config_json */
    /** REF_MODULE：ref_model_id, ref_display_field, relation_module_label；displayStyle/listFields/subTable 在 config_json */
    /** TAG：每 model 仅一个；tags 在 config_json */
}
