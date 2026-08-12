package com.unique.examine.ai.plan;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAiPlanParserTest {
    private final PlatformAiPlanParser parser = new PlatformAiPlanParser();

    @Test
    void acceptsExactPlatformQueryAndGuidancePlans() {
        var query = parser.parse(
                "{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}");
        var guidance = parser.parse("""
                {"operation":"SYSTEM_SWITCH_GUIDANCE",
                 "requestedSystemCode":"sales"}
                """);

        assertThat(query.operation())
                .isEqualTo(PlatformAiPlanParser.Operation.AUTHORIZED_SYSTEMS_QUERY);
        assertThat(query.canonicalJson())
                .isEqualTo("{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}");
        assertThat(guidance.operation())
                .isEqualTo(PlatformAiPlanParser.Operation.SYSTEM_SWITCH_GUIDANCE);
        assertThat(guidance.requestedSystemCode()).isEqualTo("sales");
    }

    @Test
    void acceptsActionableAndClarificationTaskDrafts() {
        var task = parser.parse("""
                {"operation":"PLATFORM_TASK_DRAFT",
                 "title":"Follow up with finance",
                 "description":"Confirm the revised budget",
                 "dueAt":"2026-08-05T09:00:00Z",
                 "priority":"HIGH","confidence":0.875,
                 "clarification":null}
                """);
        var clarification = parser.parse("""
                {"operation":"PLATFORM_TASK_DRAFT","title":null,
                 "description":null,"dueAt":null,"priority":null,
                 "confidence":0.4,
                 "clarification":"When should this task be due?"}
                """);

        assertThat(task.operation())
                .isEqualTo(PlatformAiPlanParser.Operation.PLATFORM_TASK_DRAFT);
        assertThat(task.taskDraft().actionable()).isTrue();
        assertThat(task.taskDraft().priority())
                .isEqualTo(PlatformAiPlanParser.PlatformTaskPriority.HIGH);
        assertThat(clarification.taskDraft().actionable()).isFalse();
        assertThat(clarification.taskDraft().clarification())
                .isEqualTo("When should this task be due?");
    }

    @Test
    void acceptsOnlyBoundedTypedOperationsQueriesOrClarification() {
        var quota = parser.parse("""
                {"operation":"PLATFORM_OPERATIONS_QUERY",
                 "queryKind":"AI_QUOTA","limit":1,
                 "confidence":0.95,"clarification":null}
                """);
        var clarification = parser.parse("""
                {"operation":"PLATFORM_OPERATIONS_QUERY",
                 "queryKind":null,"limit":null,"confidence":0.4,
                 "clarification":"Which platform operation should I inspect?"}
                """);

        assertThat(quota.operationsQuery().queryKind()).isEqualTo(
                PlatformAiPlanParser.OperationsQueryKind.AI_QUOTA);
        assertThat(quota.operationsQuery().limit()).isEqualTo(1);
        assertThat(clarification.operationsQuery().actionable()).isFalse();
    }

    @Test
    void rejectsRecordToolsUnknownFieldsDuplicatesAndMalformedTargets() {
        assertInvalid("""
                {"operation":"RECORD_QUERY","moduleCode":"orders"}
                """);
        assertInvalid("""
                {"operation":"AUTHORIZED_SYSTEMS_QUERY","systemId":"1"}
                """);
        assertInvalid("""
                {"operation":"AUTHORIZED_SYSTEMS_QUERY",
                 "operation":"SYSTEM_SWITCH_GUIDANCE"}
                """);
        assertInvalid("""
                {"operation":"SYSTEM_SWITCH_GUIDANCE",
                 "requestedSystemCode":"sales/records"}
                """);
        assertInvalid("""
                {"operation":"PLATFORM_TASK_DRAFT","title":"Foreign task",
                 "description":null,"dueAt":null,"priority":"NORMAL",
                 "confidence":0.9,"clarification":null,"assigneeId":"8"}
                """);
        assertInvalid("""
                {"operation":"PLATFORM_TASK_DRAFT","title":"System task",
                 "description":null,"dueAt":null,"priority":"NORMAL",
                 "confidence":0.9,"clarification":null,"systemId":"11"}
                """);
        assertInvalid("""
                {"operation":"PLATFORM_TASK_DRAFT","title":null,
                 "description":"payload","dueAt":null,"priority":null,
                 "confidence":0.4,"clarification":"Need a title"}
                """);
        assertInvalid("""
                {"operation":"PLATFORM_OPERATIONS_QUERY",
                 "queryKind":"PERSONAL_TASKS","limit":51,
                 "confidence":0.9,"clarification":null}
                """);
        assertInvalid("""
                {"operation":"PLATFORM_OPERATIONS_QUERY",
                 "queryKind":"AGENT_ACTIVITY","limit":10,
                 "confidence":0.9,"clarification":null,"accountId":"8"}
                """);
        assertInvalid("""
                {"operation":"PLATFORM_OPERATIONS_QUERY",
                 "queryKind":"SERVICE_HEALTH","limit":5,
                 "confidence":0.9,"clarification":null,
                 "sql":"select * from logs"}
                """);
        assertInvalid("""
                {"operation":"PLATFORM_OPERATIONS_QUERY",
                 "queryKind":"AI_QUOTA","limit":1,"confidence":0.5,
                 "clarification":"mixed state"}
                """);
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> parser.parse(value))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("PLATFORM_AI_PLAN_INVALID");
    }
}
