package com.unique.examine.ai.plan;

import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationFieldPlanParserTest {
    private final AiConfigurationFieldPlanParser parser =
            new AiConfigurationFieldPlanParser();

    @Test
    void parsesExactScalarSettingsAndCanonicalOwnerCommand() {
        var plan = parser.parse("""
                {"operation":"CONFIG_FIELD_DRAFT","moduleCode":"orders",
                 "fieldCode":"tax_amount","fieldName":"Tax amount",
                 "fieldType":"DECIMAL","required":true,
                 "settings":{"precision":18,"scale":2,
                   "minimum":0,"maximum":999999.99},
                 "confidence":0.97,"clarification":null}
                """, policy());

        assertThat(plan.actionable()).isTrue();
        assertThat(plan.moduleCode()).isEqualTo("orders");
        assertThat(plan.fieldType()).isEqualTo(
                AiConfigurationFieldPlanParser.FieldType.DECIMAL);
        assertThat(plan.settings()).isEqualTo(
                new AiConfigurationFieldPlanParser.Settings(
                        null, 18, 2, "0", "999999.99"));
        assertThat(plan.canonicalOwnerCommandJson()).isEqualTo(
                "{\"fieldCode\":\"tax_amount\",\"fieldName\":\"Tax amount\","
                        + "\"fieldType\":\"DECIMAL\",\"moduleCode\":\"orders\","
                        + "\"required\":true,\"settings\":{\"maximum\":999999.99,"
                        + "\"minimum\":0,\"precision\":18,\"scale\":2}}");
        assertThat(plan.planHash()).hasSize(64);
    }

    @Test
    void clarificationCarriesNoExecutableFieldData() {
        var plan = parser.parse("""
                {"operation":"CONFIG_FIELD_DRAFT","moduleCode":null,
                 "fieldCode":null,"fieldName":null,"fieldType":null,
                 "required":null,"settings":null,"confidence":0.42,
                 "clarification":"Which module should contain the field?"}
                """, policy());

        assertThat(plan.actionable()).isFalse();
        assertThat(plan.clarification()).isEqualTo(
                "Which module should contain the field?");
        assertThat(plan.canonicalOwnerCommandJson()).isNull();
    }

    @Test
    void acceptsEverySupportedTypeWithOnlyItsExactSettingsShape() {
        assertThat(parse("TEXT", "{\"maxLength\":200}").settings().maxLength())
                .isEqualTo(200);
        assertThat(parse("LONG_TEXT", "{\"maxLength\":8000}").settings().maxLength())
                .isEqualTo(8000);
        assertThat(parse("INTEGER", "{\"minimum\":-3,\"maximum\":7}")
                .settings().minimum()).isEqualTo("-3");
        assertThat(parse("BOOLEAN", "{}").fieldType().name()).isEqualTo("BOOLEAN");
        assertThat(parse("DATE", "{}").fieldType().name()).isEqualTo("DATE");
        assertThat(parse("DATETIME", "{}").fieldType().name()).isEqualTo("DATETIME");
    }

    @Test
    void failsClosedForUnknownKeysSqlPublicationRecordsUnsupportedTypesAndBounds() {
        assertInvalid(actionable("TEXT", "{\"maxLength\":200}")
                .replace("\"clarification\":null", "\"clarification\":null,\"sql\":\"select 1\""));
        assertInvalid(actionable("TEXT", "{\"maxLength\":200}")
                .replace("\"clarification\":null", "\"clarification\":null,\"publish\":true"));
        assertInvalid(actionable("TEXT", "{\"maxLength\":200}")
                .replace("\"clarification\":null", "\"clarification\":null,\"values\":{}"));
        assertInvalid(actionable("SELECT", "{}"));
        assertInvalid(actionable("TEXT", "{\"maxLength\":0}"));
        assertInvalid(actionable("DECIMAL",
                "{\"precision\":5,\"scale\":6,\"minimum\":null,\"maximum\":null}"));
        assertInvalid(actionable("BOOLEAN", "{\"maximum\":1}"));
        assertInvalid(actionable("INTEGER", "{\"minimum\":8,\"maximum\":7}"));
    }

    @Test
    void rejectsDuplicateKeysUnauthorizedModuleAndUnauthorizedOperation() {
        assertInvalid(actionable("TEXT", "{\"maxLength\":10,\"maxLength\":20}"));
        assertInvalid(actionable("TEXT", "{\"maxLength\":10}")
                .replace("\"orders\"", "\"secret\""));
        var noConfig = new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write",
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v2", "b".repeat(64),
                Instant.parse("2026-08-04T00:00:00Z"), 7);
        assertThatThrownBy(() -> parser.parse(
                actionable("TEXT", "{\"maxLength\":10}"), noConfig))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_FIELD_PLAN_INVALID");
    }

    private AiConfigurationFieldPlanParser.Plan parse(
            String type, String settings) {
        return parser.parse(actionable(type, settings), policy());
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> parser.parse(value, policy()))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_FIELD_PLAN_INVALID");
    }

    private static String actionable(String type, String settings) {
        return "{\"operation\":\"CONFIG_FIELD_DRAFT\","
                + "\"moduleCode\":\"orders\",\"fieldCode\":\"new_field\","
                + "\"fieldName\":\"New field\",\"fieldType\":\"" + type + "\","
                + "\"required\":false,\"settings\":" + settings + ","
                + "\"confidence\":0.91,\"clarification\":null}";
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write",
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "CONFIG_FIELD_DRAFT"),
                10, true, AiPolicy.RedactionMode.STRICT, "v2",
                "b".repeat(64), Instant.parse("2026-08-04T00:00:00Z"), 7);
    }
}
