package com.unique.unexamine.sys.module.manage.util;

import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.field.FieldValueCodec;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;

/** 列表场景与共享范围共用的已校验字段条件，保证两处查询语义一致。 */
public final class ModuleRecordFilter {
    private ModuleRecordFilter() {}

    /** 字段标识和操作符须在调用前由业务服务按当前租户权限校验。 */
    public static void apply(QueryWrapper<UnModuleRecord> where, UnModuleField field, FieldDefinition definition,
                             ModuleSceneDto.Condition condition, ObjectMapper json) {
        String code=field.getFieldKey();
        if ("name".equals(code)) {
            if ("EQ".equals(condition.operator())) where.eq("name",condition.value());
            else where.apply("LOCATE({0}, name) > 0",condition.value());
            return;
        }
        if (ModuleRecordStorage.isReservedKey(code)) {
            if ("EQ".equals(condition.operator())) {
                where.eq(code,mainValue(definition,condition.value(),json));
                applyActiveRelationTarget(where, definition, code);
            }
            else where.apply("LOCATE({0}, "+ModuleRecordStorage.column(code)+") > 0",condition.value());
            return;
        }
        if ("EQ".equals(condition.operator())) {
            var cells=FieldValueCodec.encode(definition,condition.value(),json);
            String base="EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id AND d.field_id = {0} AND ";
            if (cells.number()!=null) {
                String relation = activeRelationPredicate(definition, "d.value_number");
                if (relation.isEmpty()) where.apply(base+"d.value_number = {1})", field.getId(), cells.number());
                else where.apply(base+"d.value_number = {1}"+relation+")",
                        field.getId(), cells.number(), relationModuleId(definition));
            }
            else if (cells.time()!=null) where.apply(base+"d.value_time = {1})",field.getId(),cells.time());
            else if (cells.varchar()!=null)
                where.apply(base+"d.value_varchar = {1})",field.getId(),cells.varchar());
            else if (cells.text()!=null) where.apply(base+"HEX(d.value_text) = HEX({1}))",field.getId(),cells.text());
            else if (cells.json()!=null) {
                // 多选关联值是目标记录主键数组；命中前先确认数组中的目标仍然有效，
                // 避免已逻辑删除或跨模块的目标记录继续参与普通列表筛选。
                String targetGuard = activeMultiRelationPredicate(definition);
                if (targetGuard.isEmpty()) where.apply(base
                        +"JSON_CONTAINS(d.value_json, CAST({1} AS JSON)) "
                        +"AND JSON_CONTAINS(CAST({1} AS JSON), d.value_json))",field.getId(),cells.json());
                else where.apply(base
                        +"JSON_CONTAINS(d.value_json, CAST({1} AS JSON)) "
                        +"AND JSON_CONTAINS(CAST({1} AS JSON), d.value_json)" + targetGuard + ")",
                        field.getId(), cells.json(), relationModuleId(definition));
            }
            else where.apply("(NOT EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id AND d.field_id = {0}) "
                    +"OR EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id "
                    +"AND d.field_id = {0} AND d.value_varchar IS NULL AND d.value_text IS NULL AND d.value_number IS NULL "
                    +"AND d.value_time IS NULL AND d.value_json IS NULL))",field.getId());
        } else {
            String column = FieldValueCodec.valueColumn(definition).sqlName();
            if (column == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"该字段类型暂不支持筛选");
            where.apply("EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = un_module_record.id "
                    +"AND d.field_id = {0} AND LOCATE({1}, d."+column+") > 0)",
                    field.getId(),condition.value());
        }
    }

    private static Object mainValue(FieldDefinition definition,Object value,ObjectMapper json) {
        FieldType type=definition.type();
        if (type==FieldType.SELECT || type==FieldType.TEXT || type==FieldType.RICH_TEXT)
            return value==null?null:value.toString();
        var cells=FieldValueCodec.encode(definition,value,json);
        if (cells.number()!=null) return cells.number();
        if (cells.time()!=null) return cells.time();
        return value;
    }

    /** 关联字段的单值筛选必须排除已逻辑删除的目标记录，避免列表继续命中失效引用。 */
    private static void applyActiveRelationTarget(QueryWrapper<UnModuleRecord> where,
                                                  FieldDefinition definition, String sourceColumn) {
        if (definition.type() != FieldType.RELATION || Boolean.TRUE.equals(definition.rules().get("multiple"))) return;
        where.apply("EXISTS (SELECT 1 FROM un_module_record target WHERE target.id = "+sourceColumn
                +" AND target.deleted_time IS NULL AND target.module_id = {0}"
                +" AND target.system_id = un_module_record.system_id)", relationModuleId(definition));
    }

    /** 扩展值仍可能是单值关联；在动态值列上复用同一目标记录过滤条件。 */
    private static String activeRelationPredicate(FieldDefinition definition, String valueColumn) {
        if (definition.type() != FieldType.RELATION || Boolean.TRUE.equals(definition.rules().get("multiple"))) return "";
        return " AND EXISTS (SELECT 1 FROM un_module_record target WHERE target.id = "+valueColumn
                +" AND target.deleted_time IS NULL AND target.module_id = {2}"
                +" AND target.system_id = un_module_record.system_id)";
    }

    /** 多选关联值使用 JSON 数组；数组内任一目标失效时不让来源记录继续命中筛选。 */
    private static String activeMultiRelationPredicate(FieldDefinition definition) {
        if (definition.type() != FieldType.RELATION || !Boolean.TRUE.equals(definition.rules().get("multiple"))) return "";
        return " AND NOT EXISTS (SELECT 1 FROM JSON_TABLE(COALESCE(d.value_json, '[]'), '$[*]' "
                +"COLUMNS (target_id BIGINT PATH '$')) relation_ids "
                +"WHERE NOT EXISTS (SELECT 1 FROM un_module_record target "
                +"WHERE target.id = relation_ids.target_id AND target.deleted_time IS NULL "
                +"AND target.module_id = {2} AND target.system_id = un_module_record.system_id))";
    }

    private static Object relationModuleId(FieldDefinition definition) {
        Object moduleId = definition.rules().get("relationModuleId");
        if (!(moduleId instanceof Number number) || number.longValue() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "关联字段目标模块配置无效");
        return number.longValue();
    }

}
