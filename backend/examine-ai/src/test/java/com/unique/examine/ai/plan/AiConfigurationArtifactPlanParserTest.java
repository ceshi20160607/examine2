package com.unique.examine.ai.plan;

import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactPlanParserTest {
    private final AiConfigurationArtifactPlanParser parser =
            new AiConfigurationArtifactPlanParser();

    @Test
    void parsesSelectionWithDeterministicOptions() {
        var plan = parser.parse(selection(), policy());

        assertThat(plan.operation()).isEqualTo(
                AiConfigurationArtifactPlanParser.Operation
                        .CONFIG_SELECTION_FIELD_DRAFT);
        assertThat(plan.selection().fieldType()).isEqualTo(
                AiConfigurationArtifactPlanParser.SelectionType.RADIO);
        assertThat(plan.selection().options())
                .extracting(AiConfigurationArtifactPlanParser.Option::sortOrder)
                .containsExactly(0, 10, 20);
        assertThat(plan.selection().options().getLast().color())
                .isEqualTo("#FF0000");
        assertThat(plan.planHash()).hasSize(64);
        assertThat(plan.actionable()).isTrue();
    }

    @Test
    void parsesPageAndCanonicalDefaults() {
        var plan = parser.parse(page(), policy());

        assertThat(plan.page().pageType()).isEqualTo(
                AiConfigurationArtifactPlanParser.PageType.FORM);
        assertThat(plan.page().layout().columns()).isOne();
        assertThat(plan.page().layout().gap()).isEqualTo(16);
        assertThat(plan.page().layout().density()).isEqualTo(
                AiConfigurationArtifactPlanParser.Density.DEFAULT);
        assertThat(plan.page().layout().stickyActions()).isTrue();
        assertThat(plan.page().layout().sections())
                .extracting(AiConfigurationArtifactPlanParser.Section::sortOrder)
                .containsExactly(0, 10);
    }

    @Test
    void clarificationHasNoExecutablePayload() {
        var value = """
                {"operation":"CONFIG_PAGE_LAYOUT_DRAFT","moduleCode":null,
                 "pageCode":null,"pageType":null,"layout":null,
                 "confidence":0.5,"clarification":"Which page should change?"}
                """;
        var plan = parser.parse(value, policy());
        assertThat(plan.actionable()).isFalse();
        assertThat(plan.page()).isNull();
        assertThat(plan.clarification()).isEqualTo("Which page should change?");
    }

    @Test
    void parsesOneCanonicalFilterScenarioSuggestion() {
        var plan = parser.parse(filterScenario(), policy());

        assertThat(plan.operation()).isEqualTo(
                AiConfigurationArtifactPlanParser.Operation
                        .CONFIG_FILTER_SCENARIO_DRAFT);
        assertThat(plan.filterScenario().scenarioCode()).isEqualTo("open_items");
        assertThat(plan.filterScenario().scenarioName()).isEqualTo("Open items");
        assertThat(plan.filterScenario().filter().path("kind").asText())
                .isEqualTo("PREDICATE");
        assertThat(plan.filterScenario().sort()).hasSize(1);
        assertThat(plan.filterScenario().makeDefault()).isTrue();
        assertThat(plan.actionable()).isTrue();
    }

    @Test
    void parsesBoundedFieldPermissionStagingWithoutProviderCodesOrModes() {
        var plan = parser.parse(fieldPermissionStage(), policy());

        assertThat(plan.operation()).isEqualTo(
                AiConfigurationArtifactPlanParser.Operation
                        .CONFIG_FIELD_PERMISSION_STAGE_DRAFT);
        assertThat(plan.fieldPermissionStage().fieldCode()).isEqualTo("status");
        assertThat(plan.fieldPermissionStage().stageRead()).isTrue();
        assertThat(plan.fieldPermissionStage().stageWrite()).isFalse();
        assertInvalid(fieldPermissionStage().replace(
                "\"clarification\":null", "\"clarification\":null,"
                        + "\"permissionCode\":\"module.orders.field.status.read\""));
        assertInvalid(fieldPermissionStage().replace(
                "\"stageRead\":true", "\"stageRead\":false"));
    }

    @Test
    void newArtifactClarificationsCannotCarryTargetsOrPayloads() {
        var filterClarification = """
                {"operation":"CONFIG_FILTER_SCENARIO_DRAFT","moduleCode":null,
                 "pageCode":null,"scenario":null,"makeDefault":null,
                 "confidence":0.4,"clarification":"Which list page?"}
                """;
        var permissionClarification = """
                {"operation":"CONFIG_FIELD_PERMISSION_STAGE_DRAFT","moduleCode":null,
                 "fieldCode":null,"stageRead":null,"stageWrite":null,
                 "confidence":0.4,"clarification":"Which direction?"}
                """;

        assertThat(parser.parse(filterClarification, policy()).actionable()).isFalse();
        assertThat(parser.parse(permissionClarification, policy()).actionable()).isFalse();
        assertInvalid(permissionClarification.replace(
                "\"stageRead\":null", "\"stageRead\":true"));
        assertInvalid(filterClarification.replace(
                "\"pageCode\":null", "\"pageCode\":\"order_list\""));
    }

    @Test
    void failsClosedForMalformedFilterScenarioShapeAndBounds() {
        assertInvalid(filterScenario().replace(
                "\"makeDefault\":true", "\"makeDefault\":true,\"publish\":true"));
        assertInvalid(filterScenario().replace(
                "\"code\":\"open_items\"", "\"code\":\"Open_Items\""));
        assertInvalid(filterScenario().replace(
                "\"operator\":\"EQ\"", "\"operator\":\"eq\""));
        assertInvalid(filterScenario().replace(
                "\"direction\":\"DESC\"", "\"direction\":\"SIDEWAYS\""));
        assertInvalid(filterScenario().replace(
                "\"nulls\":\"LAST\"", "\"nulls\":\"LAST\",\"nulls\":\"FIRST\""));
    }

    @Test
    void failsClosedForSelectionShapeBoundsAndPolicy() {
        assertInvalid(selection().replace(
                "\"clarification\":null", "\"clarification\":null,\"sql\":\"x\""));
        assertInvalid(selection().replace(
                "{\"code\":\"high\"", "{\"code\":\"medium\""));
        assertInvalid(selection().replace(
                "\"default\":false}", "\"default\":true}"));
        assertInvalid(selection().replace("\"RADIO\"", "\"SELECT\""));
        assertInvalid(selection().replace("\"maxSelections\":null",
                "\"maxSelections\":2"));
        assertInvalid(selection().replace("\"orders\"", "\"secret\""));
        assertThatThrownBy(() -> parser.parse(selection(), readOnlyPolicy()))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_ARTIFACT_PLAN_INVALID");
    }

    @Test
    void failsClosedForPageUnknownFieldsDuplicateSectionsAndRepeatedFields() {
        assertInvalid(page().replace(
                "\"clarification\":null", "\"clarification\":null,\"publish\":true"));
        assertInvalid(page().replace("\"details\"", "\"main\""));
        assertInvalid(page().replace("\"amount\",\"status\"",
                "\"customer\",\"status\""));
        assertInvalid(page().replace("\"FORM\"", "\"CREATE\""));
        assertInvalid(page().replace("\"pageSize\":null",
                "\"pageSize\":20"));
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> parser.parse(value, policy()))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_ARTIFACT_PLAN_INVALID");
    }

    private static String selection() {
        return """
                {"operation":"CONFIG_SELECTION_FIELD_DRAFT","moduleCode":"orders",
                 "fieldCode":"priority","fieldName":"Priority","fieldType":"RADIO",
                 "required":true,"dictionaryCode":"order_priority",
                 "dictionaryName":"Order priority","options":[
                   {"code":"low","label":"Low","semanticKey":"LOW","color":null,"default":false},
                   {"code":"medium","label":"Medium","semanticKey":"MEDIUM","color":null,"default":true},
                   {"code":"high","label":"High","semanticKey":"HIGH","color":"#ff0000","default":false}],
                 "maxSelections":null,"confidence":0.98,"clarification":null}
                """;
    }

    private static String page() {
        return """
                {"operation":"CONFIG_PAGE_LAYOUT_DRAFT","moduleCode":"orders",
                 "pageCode":"order_form","pageType":"FORM","layout":{
                   "columns":null,"gap":null,"labelPosition":null,"density":null,
                   "stickyActions":null,"pageSize":null,"searchEnabled":null,
                   "filterEnabled":null,"sections":[
                     {"code":"main","title":"Main","fieldCodes":["customer"]},
                     {"code":"details","title":"Details","fieldCodes":["amount","status"]}]},
                 "confidence":0.96,"clarification":null}
                """;
    }

    private static String filterScenario() {
        return """
                {"operation":"CONFIG_FILTER_SCENARIO_DRAFT","moduleCode":"orders",
                 "pageCode":"list","scenario":{"code":"open_items","name":"Open items",
                 "filter":{"kind":"PREDICATE","fieldCode":"status","operator":"EQ","value":"OPEN"},
                 "sort":[{"fieldCode":"created_at","direction":"DESC","nulls":"LAST"}]},
                 "makeDefault":true,"confidence":0.97,"clarification":null}
                """;
    }

    private static String fieldPermissionStage() {
        return """
                {"operation":"CONFIG_FIELD_PERMISSION_STAGE_DRAFT","moduleCode":"orders",
                 "fieldCode":"status","stageRead":true,"stageWrite":false,
                 "confidence":0.99,"clarification":null}
                """;
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")), Set.of(
                "RECORD_QUERY", "CONFIG_SELECTION_FIELD_DRAFT",
                "CONFIG_PAGE_LAYOUT_DRAFT", "CONFIG_FILTER_SCENARIO_DRAFT",
                "CONFIG_FIELD_PERMISSION_STAGE_DRAFT"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v3", "b".repeat(64),
                Instant.parse("2026-08-04T00:00:00Z"), 7);
    }

    private static AiPolicy.Version readOnlyPolicy() {
        return new AiPolicy.Version(
                92, 1, 2, 90, 2, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")), Set.of("RECORD_QUERY"),
                10, true, AiPolicy.RedactionMode.STRICT, "v3",
                "c".repeat(64), Instant.parse("2026-08-04T00:00:00Z"), 7);
    }
}
