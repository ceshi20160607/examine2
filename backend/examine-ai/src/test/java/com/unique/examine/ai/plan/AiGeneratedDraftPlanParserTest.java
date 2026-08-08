package com.unique.examine.ai.plan;

import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGeneratedDraftPlanParserTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private final AiGeneratedDraftPlanParser parser =
            new AiGeneratedDraftPlanParser();

    @Test
    void parsesAllThreeExactGeneratedDrafts() {
        var flow = (AiGeneratedDraftPlanParser.FlowPlan) parser.parse("""
                {"operation":"FLOW_DEFINITION_DRAFT","name":"Expense approval",
                 "approverMemberIds":["7","9"],"confidence":0.96,
                 "clarification":null}
                """, policy());
        assertThat(flow.name()).isEqualTo("Expense approval");
        assertThat(flow.approverMemberIds()).containsExactly("7", "9");

        var report = (AiGeneratedDraftPlanParser.ReportPlan) parser.parse("""
                {"operation":"CONFIG_REPORT_DRAFT","code":"expense_summary",
                 "name":"Expense summary","description":"Private report notes",
                 "dataSourceId":"301","outputFieldCodes":["amount","owner"],
                 "confidence":0.91,"clarification":null}
                """, policy());
        assertThat(report.dataSourceId()).isEqualTo("301");
        assertThat(report.outputFieldCodes()).containsExactly("amount", "owner");

        var print = (AiGeneratedDraftPlanParser.PrintPlan) parser.parse("""
                {"operation":"CONFIG_PRINT_TEMPLATE_DRAFT","moduleCode":"expense",
                 "code":"expense_receipt","name":"Expense receipt","paperSize":"A4",
                 "orientation":"PORTRAIT","title":"Private receipt title",
                 "fieldCodes":["amount","owner"],"footer":null,
                 "confidence":1,"clarification":null}
                """, policy());
        assertThat(print.paperSize()).isEqualTo("A4");
        assertThat(print.title()).isEqualTo("Private receipt title");
        assertThat(print.planHash()).hasSize(64);
    }

    @Test
    void clarificationRequiresEveryOperationPayloadToBeNull() {
        var value = parser.parse("""
                {"operation":"CONFIG_REPORT_DRAFT","code":null,"name":null,
                 "description":null,"dataSourceId":null,"outputFieldCodes":null,
                 "confidence":0.4,"clarification":"Which data source?"}
                """, policy());
        assertThat(value.actionable()).isFalse();

        rejects("""
                {"operation":"FLOW_DEFINITION_DRAFT","name":"Expense approval",
                 "approverMemberIds":null,"confidence":0.4,
                 "clarification":"Which approvers?"}
                """);
    }

    @Test
    void rejectsDuplicateUnknownUnsafeAndInvalidBoundedMaterial() {
        rejects("""
                {"operation":"FLOW_DEFINITION_DRAFT","name":"Expense approval",
                 "name":"Other","approverMemberIds":["7"],"confidence":0.9,
                 "clarification":null}
                """);
        rejects("""
                {"operation":"FLOW_DEFINITION_DRAFT","name":"Expense approval",
                 "approverMemberIds":["7"],"confidence":0.9,
                 "clarification":null,"published":true}
                """);
        rejects("""
                {"operation":"CONFIG_REPORT_DRAFT","code":"expense-summary",
                 "name":"Expense summary","description":null,"dataSourceId":"301",
                 "outputFieldCodes":["amount"],"confidence":0.9,"clarification":null}
                """);
        rejects("""
                {"operation":"CONFIG_PRINT_TEMPLATE_DRAFT","moduleCode":"Expense",
                 "code":"receipt","name":"Receipt","paperSize":"A3",
                 "orientation":"PORTRAIT","title":"https://secret.test",
                 "fieldCodes":["amount","amount"],"footer":null,
                 "confidence":0.9,"clarification":null}
                """);
    }

    @Test
    void rejectsOperationNotPublishedByPolicy() {
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"FLOW_DEFINITION_DRAFT","name":"Expense approval",
                 "approverMemberIds":["7"],"confidence":0.9,
                 "clarification":null}
                """, policy(Set.of("RECORD_QUERY"))))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
    }

    private void rejects(String value) {
        assertThatThrownBy(() -> parser.parse(value, policy()))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
    }

    private static AiPolicy.Version policy() {
        return policy(Set.of("RECORD_QUERY", "FLOW_DEFINITION_DRAFT",
                "CONFIG_REPORT_DRAFT", "CONFIG_PRINT_TEMPLATE_DRAFT"));
    }

    private static AiPolicy.Version policy(Set<String> operations) {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-generated",
                Set.of("expense"), Map.of("expense", Set.of("amount", "owner")),
                operations, 10, true, AiPolicy.RedactionMode.STRICT,
                "v85", "a".repeat(64), NOW, 7);
    }
}
