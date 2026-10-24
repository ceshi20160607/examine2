package com.unique.unexamine.core.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 公共字段定义解析只解释标准结构，不把模块租户业务规则混入 core。 */
class FieldDefinitionParserTest {
    @Test
    void parsesStandardConfiguration() {
        Map<String, Object> configured = new LinkedHashMap<>();
        configured.put("fieldName", "客户名称");
        configured.put("fieldType", 1);
        configured.put("required", true);
        configured.put("config", Map.of("maxLength", 80));

        FieldDefinition definition = FieldDefinitionParser.parse("customer_name", configured);

        assertEquals("customer_name", definition.code());
        assertEquals("客户名称", definition.label());
        assertEquals(FieldType.TEXT, definition.type());
        assertEquals(80, definition.rules().get("maxLength"));
    }

    @Test
    void rejectsNonObjectRulesAndNonBooleanRequired() {
        assertThrows(IllegalArgumentException.class, () -> FieldDefinitionParser.parse("name",
                Map.of("fieldType", 1, "required", "true", "config", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> FieldDefinitionParser.parse("name",
                Map.of("fieldType", 1, "config", "not-an-object")));
    }
}
