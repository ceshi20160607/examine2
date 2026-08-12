package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.ConfigTypes.FieldType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredPropertyValidatorP4C4Test {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StructuredPropertyValidator validator = new StructuredPropertyValidator(objectMapper);

    @Test
    void acceptsExactTypedDerivedDeclarations() throws Exception {
        assertThat(validate(FieldType.FORMULA, """
                {"resultSchema":"DECIMAL","astVersion":1,"expressionAst":{"op":"MULTIPLY","args":[
                  {"fieldId":"101"},{"fieldId":"102"}]}}
                """)).contains("expressionAst");
        assertThat(validate(FieldType.CALCULATED, """
                {"resultSchema":"DATE","astVersion":1,"expressionAst":{"op":"ADD_DAYS","args":[
                  {"fieldId":"103"},{"literalType":"INTEGER","value":5}]}}
                """)).contains("ADD_DAYS");
        assertThat(validate(FieldType.SUMMARY,
                "{\"resultSchema\":\"DECIMAL\",\"relationFieldId\":\"201\","
                        + "\"targetFieldId\":\"202\",\"reduction\":\"SUM\"}"))
                .contains("relationFieldId");
        assertThat(validate(FieldType.LOOKUP,
                "{\"resultSchema\":\"STRING\",\"relationFieldId\":\"201\","
                        + "\"targetFieldId\":\"203\",\"distinct\":true}"))
                .contains("distinct");
        assertThat(validate(FieldType.AGGREGATE,
                "{\"resultSchema\":\"DECIMAL\",\"subtableFieldId\":\"301\","
                        + "\"aggregateId\":\"lineTotal\"}"))
                .contains("lineTotal");
    }

    @Test
    void rejectsFreeFormCodeDefaultsAndUnboundedOrMalformedAst() {
        assertInvalid(FieldType.FORMULA,
                "{\"resultSchema\":\"DECIMAL\",\"formula\":\"price * quantity\"}");
        assertInvalid(FieldType.FORMULA,
                "{\"resultSchema\":\"DECIMAL\",\"astVersion\":1,\"defaultMode\":\"FIXED\","
                        + "\"expressionAst\":{\"fieldId\":\"101\"}}");
        assertInvalid(FieldType.FORMULA,
                "{\"resultSchema\":\"DECIMAL\",\"astVersion\":1,"
                        + "\"expressionAst\":{\"op\":\"EXEC\",\"args\":[{\"fieldId\":\"101\"}]}}");
        assertInvalid(FieldType.CALCULATED,
                "{\"resultSchema\":\"DATE\",\"astVersion\":1,"
                        + "\"expressionAst\":{\"literalType\":\"INTEGER\",\"value\":\"5\"}}");
        assertInvalid(FieldType.SUMMARY,
                "{\"resultSchema\":\"INTEGER\",\"relationFieldId\":\"201\","
                        + "\"targetFieldId\":\"202\",\"reduction\":\"COUNT\"}");
        assertInvalid(FieldType.AGGREGATE,
                "{\"resultSchema\":\"STRING\",\"subtableFieldId\":\"301\"," 
                        + "\"aggregateId\":\"lineTotal\"}");
    }

    @Test
    void freezesBoundedAiFillContractAndDefaultConfidence() throws Exception {
        var canonical = validate(FieldType.AI_FILL, """
                {"resultSchema":"STRING","sourceFieldIds":["101","102"],
                 "promptTemplate":"Summarize the source fields","modelPolicy":"SYSTEM_DEFAULT",
                 "overwriteMode":"CONFIRM"}
                """);

        assertThat(objectMapper.readTree(canonical).path("minConfidence").decimalValue())
                .isEqualByComparingTo("0.80");
        for (var schema : new String[]{"STRING", "DECIMAL", "INTEGER", "DATE", "DATETIME", "BOOLEAN"}) {
            assertThat(validate(FieldType.AI_FILL, """
                    {"resultSchema":"%s","sourceFieldIds":["101"],"promptTemplate":"p",
                     "modelPolicy":"SYSTEM_DEFAULT","minConfidence":0.50,"overwriteMode":"NEVER"}
                    """.formatted(schema))).contains(schema);
        }
    }

    @Test
    void rejectsUnsafeOrUnboundedAiFillConfiguration() {
        assertInvalid(FieldType.AI_FILL, ai("[\"101\",\"101\"]", "p", "SYSTEM_DEFAULT", "0.80", "NEVER"));
        assertInvalid(FieldType.AI_FILL, ai("[]", "p", "SYSTEM_DEFAULT", "0.80", "NEVER"));
        assertInvalid(FieldType.AI_FILL, ai("[\"101\"]", " ", "SYSTEM_DEFAULT", "0.80", "NEVER"));
        assertInvalid(FieldType.AI_FILL, ai("[\"101\"]", "p", "ANY", "0.80", "NEVER"));
        assertInvalid(FieldType.AI_FILL, ai("[\"101\"]", "p", "SYSTEM_DEFAULT", "0.49", "NEVER"));
        assertInvalid(FieldType.AI_FILL, ai("[\"101\"]", "p", "SYSTEM_DEFAULT", "0.80", "ALWAYS"));
    }

    private static String ai(String sources, String prompt, String model, String confidence, String overwrite) {
        return "{\"resultSchema\":\"STRING\",\"sourceFieldIds\":" + sources
                + ",\"promptTemplate\":\"" + prompt + "\",\"modelPolicy\":\"" + model
                + "\",\"minConfidence\":" + confidence + ",\"overwriteMode\":\"" + overwrite + "\"}";
    }

    private String validate(FieldType type, String json) throws Exception {
        return validator.field(type, objectMapper.readTree(json));
    }

    private void assertInvalid(FieldType type, String json) {
        assertThatThrownBy(() -> validate(type, json)).isInstanceOf(BusinessException.class);
    }
}
