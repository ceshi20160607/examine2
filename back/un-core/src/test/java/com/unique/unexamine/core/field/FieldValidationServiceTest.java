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
    @Test void moneyUsesConfiguredRangeAndDecimalPlaces() {
        var field=field(FieldType.MONEY,true,Map.of("min",0,"max",100,"precision",2));
        service.validateDefinition(field);
        assertDoesNotThrow(()->service.validateValue(field,new BigDecimal("12.30")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,"12.30"));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,new BigDecimal("12.301")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field,101));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.NUMBER,false,Map.of("min",5,"max",1))));
    }
    @Test void zeroAndFalseSatisfyRequiredButEmptyCollectionsDoNot() {
        assertDoesNotThrow(()->service.validateValue(field(FieldType.NUMBER,true,Map.of()),0));
        assertDoesNotThrow(()->service.validateValue(field(FieldType.BOOLEAN,true,Map.of()),false));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.MULTI_SELECT,true,Map.of()),List.of()));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.TEXT,true,Map.of())," "));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.NUMBER,false,Map.of()),""));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.BOOLEAN,false,Map.of()),Map.of()));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.DATE,false,Map.of()),List.of()));
        assertDoesNotThrow(()->service.validateValue(field(FieldType.MULTI_SELECT,false,Map.of()),List.of()));
    }
    @Test void optionValidationRejectsDisabledUnknownDuplicateAndBrokenCascade() {
        var options=List.of(Map.of("id","a","label","甲"),Map.of("id","b","label","乙","parentId","a"),Map.of("id","x","label","禁用","enabled",false));
        var multi=field(FieldType.MULTI_SELECT,false,Map.of("options",options));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(multi,List.of("a","a")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(multi,List.of("x")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(multi,List.of("missing")));
        var cascade=field(FieldType.CASCADE,true,Map.of("options",options));
        assertDoesNotThrow(()->service.validateValue(cascade,List.of("a","b")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(cascade,List.of("b")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(cascade,List.of("b","a")));
    }
    @Test void invalidRulesAndDateRangesCannotBePublishedOrStored() {
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("unsupportedRule",true))));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("pattern","["))));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("maxLength",1.5))));
        assertThrows(IllegalArgumentException.class,()->service.validateDefinition(field(FieldType.TEXT,false,Map.of("defaultValue",123))));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.DATE_RANGE,false,Map.of()),List.of("2026-10-22","2026-10-21")));
        assertThrows(IllegalArgumentException.class,()->service.validateValue(field(FieldType.FORMULA,false,Map.of()),10));
        assertThrows(IllegalArgumentException.class,()->FieldType.parse("arbitrary"));
    }
}
