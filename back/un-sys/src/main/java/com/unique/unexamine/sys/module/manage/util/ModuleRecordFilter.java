package com.unique.unexamine.sys.module.manage.util;

import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.field.FieldValueCodec;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.manage.service.ModuleSceneService;

/** 列表场景与共享范围共用的已校验字段条件，保证两处查询语义一致。 */
public final class ModuleRecordFilter {
    private ModuleRecordFilter() {}

    /** 字段标识和操作符须在调用前由业务服务按当前租户权限校验。 */
    public static void apply(QueryWrapper<UnModuleRecord> where, UnModuleField field,
                             ModuleSceneDto.Condition condition, ObjectMapper json) {
        String code=field.getFieldKey();
        if ("name".equals(code)) {
            if ("EQ".equals(condition.operator())) where.eq("name",condition.value());
            else where.apply("LOCATE({0}, name) > 0",condition.value());
            return;
        }
        if (ModuleRecordStorage.isReservedKey(code)) {
            if ("EQ".equals(condition.operator())) where.eq(code,mainValue(type(field),condition.value(),json));
            else where.apply("LOCATE({0}, "+ModuleRecordStorage.column(code)+") > 0",condition.value());
            return;
        }
        FieldType type=FieldType.parse(field.getFieldType());
        if ("EQ".equals(condition.operator())) {
            var cells=FieldValueCodec.encode(type,condition.value(),json);
            String base="EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id AND d.field_id = {0} AND ";
            if (cells.number()!=null) where.apply(base+"d.value_number = {1})",field.getId(),cells.number());
            else if (cells.time()!=null) where.apply(base+"d.value_time = {1})",field.getId(),cells.time());
            else if (cells.varchar()!=null)
                where.apply(base+"d.value_varchar = {1})",field.getId(),cells.varchar());
            else if (cells.text()!=null) where.apply(base+"HEX(d.value_text) = HEX({1}))",field.getId(),cells.text());
            else if (cells.json()!=null) where.apply(base
                    +"JSON_CONTAINS(d.value_json, CAST({1} AS JSON)) "
                    +"AND JSON_CONTAINS(CAST({1} AS JSON), d.value_json))",field.getId(),cells.json());
            else where.apply("(NOT EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id AND d.field_id = {0}) "
                    +"OR EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id "
                    +"AND d.field_id = {0} AND d.value_varchar IS NULL AND d.value_text IS NULL AND d.value_number IS NULL "
                    +"AND d.value_time IS NULL AND d.value_json IS NULL))",field.getId());
        } else where.apply("EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id "
                +"AND d.field_id = {0} AND LOCATE({1}, d."+type.valueColumn().sqlName()+") > 0)",
                field.getId(),condition.value());
    }

    private static FieldType type(UnModuleField field) {
        return FieldType.parse(field.getFieldType());
    }

    private static Object mainValue(FieldType type,Object value,ObjectMapper json) {
        if (type==FieldType.SELECT || type==FieldType.TEXT || type==FieldType.RICH_TEXT
                || type==FieldType.ATTACHMENT) return value==null?null:value.toString();
        var cells=FieldValueCodec.encode(type,value,json);
        if (cells.number()!=null) return cells.number();
        if (cells.time()!=null) return cells.time();
        return value;
    }

}
