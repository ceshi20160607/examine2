package com.unique.examine.ai.plan;

import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkDraftPlanParserTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private final AiWorkDraftPlanParser parser = new AiWorkDraftPlanParser(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void parsesExactTaskAndReportDrafts() {
        var task = (AiWorkDraftPlanParser.TaskPlan) parser.parse("""
                {"operation":"WORK_TASK_DRAFT","title":"Close quarter",
                 "description":"Reconcile the ledger","assigneeMemberId":"7",
                 "projectId":"701","dueAt":"2026-08-08T09:00:00Z",
                 "confidence":0.95,"clarification":null}
                """, policy());
        assertThat(task.actionable()).isTrue();
        assertThat(task.title()).isEqualTo("Close quarter");
        assertThat(task.assigneeMemberId()).isEqualTo("7");
        assertThat(task.planHash()).hasSize(64);

        var report = (AiWorkDraftPlanParser.DailyReportPlan) parser.parse("""
                {"operation":"WORK_DAILY_REPORT_DRAFT","workDate":"2026-08-04",
                 "completedWork":"Finished reconciliation",
                 "plannedWork":"Review exceptions","blockers":null,
                 "confidence":1,"clarification":null}
                """, policy());
        assertThat(report.workDate()).isEqualTo(LocalDate.parse("2026-08-04"));
        assertThat(report.blockers()).isNull();
    }

    @Test
    void acceptsOnlyNullPayloadForClarification() {
        var plan = parser.parse("""
                {"operation":"WORK_TASK_DRAFT","title":null,"description":null,
                 "assigneeMemberId":null,"projectId":null,"dueAt":null,
                 "confidence":0.4,"clarification":"Which assignee?"}
                """, policy());
        assertThat(plan.actionable()).isFalse();

        rejects("""
                {"operation":"WORK_TASK_DRAFT","title":"Do work","description":null,
                 "assigneeMemberId":null,"projectId":null,"dueAt":null,
                 "confidence":0.4,"clarification":"Which assignee?"}
                """);
    }

    @Test
    void rejectsDuplicateUnknownUnauthorizedInvalidAndFutureMaterial() {
        rejects("""
                {"operation":"WORK_TASK_DRAFT","title":"Do work","description":null,
                 "assigneeMemberId":"7","projectId":null,"dueAt":null,
                 "confidence":0.9,"clarification":null,"status":"OPEN"}
                """);
        rejects("""
                {"operation":"WORK_TASK_DRAFT","title":"Do work","title":"Other",
                 "description":null,"assigneeMemberId":"7","projectId":null,
                 "dueAt":null,"confidence":0.9,"clarification":null}
                """);
        rejects("""
                {"operation":"WORK_TASK_DRAFT","title":"<script>alert(1)</script>",
                 "description":null,"assigneeMemberId":"7","projectId":null,
                 "dueAt":null,"confidence":0.9,"clarification":null}
                """);
        rejects("""
                {"operation":"WORK_DAILY_REPORT_DRAFT","workDate":"2026-08-05",
                 "completedWork":"Done","plannedWork":"Next","blockers":null,
                 "confidence":0.9,"clarification":null}
                """);
        var readOnly = policy(Set.of("RECORD_QUERY"));
        assertThatThrownBy(() -> parser.parse("""
                {"operation":"WORK_TASK_DRAFT","title":"Do work","description":null,
                 "assigneeMemberId":"7","projectId":null,"dueAt":null,
                 "confidence":0.9,"clarification":null}
                """, readOnly)).isInstanceOf(BusinessException.class)
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
        return policy(Set.of(
                "RECORD_QUERY", "WORK_TASK_DRAFT", "WORK_DAILY_REPORT_DRAFT"));
    }

    private static AiPolicy.Version policy(Set<String> operations) {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-work",
                Set.of("orders"), Map.of("orders", Set.of("status")),
                operations, 10, true, AiPolicy.RedactionMode.STRICT,
                "v84", "a".repeat(64), NOW, 7);
    }
}
