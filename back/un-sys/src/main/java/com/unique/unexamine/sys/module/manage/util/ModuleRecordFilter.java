package com.unique.unexamine.sys.module.manage.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.manage.service.ModuleSceneService;

/** 列表场景与共享范围共用的已校验字段条件，保证两处查询语义一致。 */
public final class ModuleRecordFilter {
    private ModuleRecordFilter() {}

    /** 字段标识和操作符须在调用前由业务服务按当前租户权限校验。 */
    public static void apply(QueryWrapper<UnModuleRecord> where, UnModuleField field,
                             ModuleSceneService.Condition condition, ObjectMapper json) {
        String code=field.getFieldCode();
        if ("name".equals(code)) {
            if ("EQ".equals(condition.operator())) where.eq("name",condition.value());
            else where.apply("LOCATE({0}, name) > 0",condition.value());
            return;
        }
        FieldType type=FieldType.parse(field.getFieldType());
        if ("EQ".equals(condition.operator())) {
            String index=switch (type.kind()) {
                case NUMBER -> "index_decimal";
                case TEXT, OPTION, REFERENCE, BOOLEAN, TIME -> "index_text";
                default -> null;
            };
            if (index!=null && condition.value()!=null && !(condition.value() instanceof java.util.Collection<?>))
                where.apply("EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.record_id = un_module_record.id "
                        +"AND d.field_id = {0} AND ((d."+index+" = {1}"
                        +("index_text".equals(index)?" AND HEX(d.index_text) = HEX({1})":"")+") OR "
                        +"(d."+index+" IS NULL AND JSON_CONTAINS(d.value_json, CAST({2} AS JSON)))))",
                        field.getId(),condition.value().toString(),write(json,condition.value()));
            else where.apply("EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.record_id = un_module_record.id "
                            +"AND d.field_id = {0} AND JSON_CONTAINS(d.value_json, CAST({1} AS JSON)))",
                    field.getId(),write(json,condition.value()));
        } else where.apply("EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.record_id = un_module_record.id "
                +"AND d.field_id = {0} AND LOCATE({1}, JSON_UNQUOTE(d.value_json)) > 0)",
                field.getId(),condition.value());
    }

    private static String write(ObjectMapper json,Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("筛选值无法保存",ex); }
    }
}
