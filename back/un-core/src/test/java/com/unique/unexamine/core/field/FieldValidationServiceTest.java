package com.unique.unexamine.core.field;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 从配置到业务值的边界，避免保存入口之间产生不同规则。 */
class FieldValidationServiceTest {
    private final FieldValidationService service=new FieldValidationService();
    private FieldDefinition field(FieldType type,boolean required,Map<String,Object> rules) {
        return new FieldDefinition("test","测试字段",type,required,rules);
    }
    @Test void numberUsesConfiguredRangeTotalDigitsAndDecimalPlaces() {
        var field=field(FieldType.NUMBER,true,Map.of("min",0,"max",100,"precision",5,"scale",2));
        service.validateDefinition(field);
        assertDoesNotThrow(()->service.validateValue(field,new BigDecimal("12.30")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,"12.30"));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,new BigDecimal("12.301")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,101));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,100000));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.NUMBER,false,Map.of("precision",5)),100000));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.NUMBER,false,Map.of("precision",2,"scale",3))));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.NUMBER,false,Map.of("min",5,"max",1))));
    }
    @Test void oneNumberTypeAcceptsIntegersAndDecimals() {
        assertDoesNotThrow(()->service.validateValue(field(FieldType.NUMBER,false,Map.of()),2));
        assertDoesNotThrow(()->service.validateValue(field(FieldType.NUMBER,false,Map.of()),new BigDecimal("1.5")));
    }
    @Test void zeroSatisfiesRequiredButEmptyCollectionsDoNot() {
        assertDoesNotThrow(()->service.validateValue(field(FieldType.NUMBER,true,Map.of()),0));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.SELECT,true,Map.of("multiple",true)),List.of()));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.TEXT,true,Map.of())," "));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.NUMBER,false,Map.of()),""));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.SELECT,false,Map.of()),false));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.DATE_TIME,false,Map.of()),List.of()));
        assertDoesNotThrow(()->service.validateValue(field(FieldType.SELECT,false,Map.of("multiple",true)),List.of()));
    }
    @Test void optionValidationRejectsDisabledUnknownDuplicateAndBrokenCascade() {
        var options=List.of(Map.of("id",1L,"value","a","label","甲"),Map.of("id",2L,"value","b","label","乙","parentId",1L),
                Map.of("id",3L,"value","c","label","禁用","enabled",false));
        var multi=field(FieldType.SELECT,false,Map.of("options",options,"multiple",true));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(multi,List.of("a","a")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(multi,List.of("c")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(multi,List.of("unknown")));
        var cascade=field(FieldType.CASCADE,true,Map.of("options",options));
        assertDoesNotThrow(()->service.validateValue(cascade,Map.of("path",List.of(1L,2L))));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(cascade,List.of(1L,2L)));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(cascade,Map.of("path",List.of(2L))));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(cascade,Map.of("path",List.of(2L,1L))));
    }
    @Test void invalidRulesAndOldTypeCodesCannotBeStored() {
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("unsupportedRule",true))));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("pattern","["))));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("maxLength",1.5))));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("defaultValue",123))));
        assertDoesNotThrow(()->service.validateValue(field(FieldType.DATE_TIME,false,Map.of("displayStyle","DATE")),"2026-10-22"));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.DATE_TIME,false,Map.of("displayStyle","DATE")),"2026-10-22T12:00"));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.DATE_TIME,false,Map.of("displayStyle","DATE_TIME")),"2026-10-22"));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.FORMULA,false,Map.of()),10));
        assertThrows(IllegalArgumentException.class,()->FieldType.parse("arbitrary"));
        assertEquals(FieldType.TEXT,FieldType.parse(1));
        assertEquals(FieldType.RICH_TEXT,FieldType.parse(2));
        assertEquals(FieldType.NUMBER,FieldType.parse(3));
        assertThrows(IllegalArgumentException.class,()->FieldType.parse("TEXT"));
    }

    @Test void attachmentUsesIndependentUuidBatchRatherThanFileId() {
        var field=field(FieldType.ATTACHMENT,false,Map.of());
        assertDoesNotThrow(()->service.validateValue(field,FileBatchId.create()));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,123L));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,"not-a-uuid"));
    }
}
