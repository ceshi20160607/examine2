package com.unique.unexamine.core.module;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.field.FieldValidationService;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 触发条件必须在保存绑定时按模块字段类型校验，避免运行时把错误配置当作未命中。 */
class TriggerConditionMatcherTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FieldValidationService validation = new FieldValidationService();

    @Test
    void rejectsTextValueForNumberCondition() {
        FieldDefinition amount = new FieldDefinition("amount", "金额", FieldType.NUMBER, false, Map.of());

        assertThrows(IllegalArgumentException.class, () -> TriggerConditionMatcher.validateFieldValues(json,
                "{\"fieldKey\":\"amount\",\"operator\":\"EQ\",\"value\":\"12\"}",
                Map.of("amount", amount), validation));
    }

    @Test
    void acceptsShortTextContainsCondition() {
        FieldDefinition name = new FieldDefinition("name", "名称", FieldType.TEXT, false, Map.of());

        assertDoesNotThrow(() -> TriggerConditionMatcher.validateFieldValues(json,
                "{\"fieldKey\":\"name\",\"operator\":\"CONTAINS\",\"value\":\"商机\"}",
                Map.of("name", name), validation));
    }

    @Test
    void allowsConditionOnComputedTextValue() {
        FieldDefinition serial = new FieldDefinition("serial", "编号", FieldType.AUTO_NUMBER, false,
                Map.of("autoNumberConfig", Map.of("prefix", "NO-", "padding", 4, "start", 1)));

        assertDoesNotThrow(() -> TriggerConditionMatcher.validateFieldValues(json,
                "{\"fieldKey\":\"serial\",\"operator\":\"EQ\",\"value\":\"NO-0001\"}",
                Map.of("serial", serial), validation));
    }

    @Test
    void validatesAndMatchesDateRangeCondition() {
        FieldDefinition date = new FieldDefinition("signedTime", "签订时间", FieldType.DATE_TIME, false,
                Map.of("displayStyle", "DATE"));
        String condition = "{\"fieldKey\":\"signedTime\",\"operator\":\"GE\",\"value\":\"2026-11-01\"}";

        assertDoesNotThrow(() -> TriggerConditionMatcher.validate(json, condition));
        assertDoesNotThrow(() -> TriggerConditionMatcher.validateFieldValues(json, condition,
                Map.of("signedTime", date), validation));
        assertDoesNotThrow(() -> TriggerConditionMatcher.matches(json, condition,
                Map.of("signedTime", "2026-11-02")));
        assertThrows(IllegalArgumentException.class, () -> TriggerConditionMatcher.validateFieldValues(json,
                "{\"fieldKey\":\"signedTime\",\"operator\":\"GE\",\"value\":\"not-date\"}",
                Map.of("signedTime", date), validation));
    }
}
