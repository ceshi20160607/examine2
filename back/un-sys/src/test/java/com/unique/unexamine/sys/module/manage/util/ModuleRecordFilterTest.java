package com.unique.unexamine.sys.module.manage.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 主表公式字段筛选必须沿用完整字段定义，不能丢失公式结果类型配置。 */
class ModuleRecordFilterTest {
    @Test
    void filtersMainFormulaUsingConfiguredResultType() {
        UnModuleField field = new UnModuleField();
        field.setFieldKey("item_number01");
        field.setFieldType((short) FieldType.FORMULA.code());
        FieldDefinition definition = new FieldDefinition("item_number01", "金额合计", FieldType.FORMULA, false,
                Map.of("formulaConfig", Map.of("resultType", "NUMBER")));
        QueryWrapper<com.unique.unexamine.sys.module.base.entity.UnModuleRecord> where = new QueryWrapper<>();
        ModuleRecordFilter.apply(where, field, definition,
                new ModuleSceneDto.Condition(1L, "EQ", 12.5), new ObjectMapper());
        assertTrue(where.getSqlSegment().contains("item_number01"));
    }

    @Test
    void filtersSingleRelationOnlyWhenTargetRecordIsActive() {
        UnModuleField field = new UnModuleField();
        field.setId(12L);
        field.setFieldKey("item_module01");
        field.setFieldType((short) FieldType.RELATION.code());
        FieldDefinition definition = new FieldDefinition("item_module01", "客户", FieldType.RELATION, false,
                Map.of("relationModuleId", 88L));
        QueryWrapper<com.unique.unexamine.sys.module.base.entity.UnModuleRecord> where = new QueryWrapper<>();

        ModuleRecordFilter.apply(where, field, definition,
                new ModuleSceneDto.Condition(12L, "EQ", 901L), new ObjectMapper());

        assertTrue(where.getSqlSegment().contains("target.deleted_time IS NULL"));
        assertTrue(where.getSqlSegment().contains("target.module_id"));
    }

    @Test
    void filtersExtendedSingleRelationUsingTargetRecordGuard() {
        UnModuleField field = new UnModuleField();
        field.setId(13L);
        field.setFieldKey("field_customer");
        field.setFieldType((short) FieldType.RELATION.code());
        FieldDefinition definition = new FieldDefinition("field_customer", "客户", FieldType.RELATION, false,
                Map.of("relationModuleId", 88L));
        QueryWrapper<com.unique.unexamine.sys.module.base.entity.UnModuleRecord> where = new QueryWrapper<>();

        ModuleRecordFilter.apply(where, field, definition,
                new ModuleSceneDto.Condition(13L, "EQ", 902L), new ObjectMapper());

        assertTrue(where.getSqlSegment().contains("d.value_number"));
        assertTrue(where.getSqlSegment().contains("target.deleted_time IS NULL"));
    }

    @Test
    void filtersMultiRelationArrayUsingActiveTargetGuard() {
        UnModuleField field = new UnModuleField();
        field.setId(14L);
        field.setFieldKey("field_customers");
        field.setFieldType((short) FieldType.RELATION.code());
        FieldDefinition definition = new FieldDefinition("field_customers", "客户", FieldType.RELATION, false,
                Map.of("multiple", true, "relationModuleId", 88L));
        QueryWrapper<com.unique.unexamine.sys.module.base.entity.UnModuleRecord> where = new QueryWrapper<>();

        ModuleRecordFilter.apply(where, field, definition,
                new ModuleSceneDto.Condition(14L, "EQ", List.of(901L, 902L)), new ObjectMapper());

        assertTrue(where.getSqlSegment().contains("JSON_TABLE"));
        assertTrue(where.getSqlSegment().contains("target.deleted_time IS NULL"));
        assertTrue(where.getSqlSegment().contains("target.module_id"));
    }
}
