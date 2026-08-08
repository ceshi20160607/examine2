package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalCompletionCompensationTest {
    private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
    private static final String TOKEN = "a".repeat(64);

    @Test
    void validatesPolicyAndMaterializesOnlySucceededOriginalsInReverseOrder() {
        var first = step("first").withParallelGroup("publish");
        var second = step("second").withParallelGroup("publish");
        var third = step("third").withParallelGroup("publish");

        assertThat(CompletionFailurePolicy.require(
                CompletionFailurePolicy.COMPENSATE,
                List.of(first, second, third)))
                .isEqualTo(CompletionFailurePolicy.COMPENSATE);
        assertThatThrownBy(() -> CompletionFailurePolicy.require(
                CompletionFailurePolicy.MANUAL_RETRY, List.of(first)))
                .isInstanceOf(ApprovalDomainException.class);

        var one = succeed(execution(301L, 0, first));
        var two = succeed(execution(302L, 1, second));
        var failed = execution(303L, 2, third)
                .claim("worker", TOKEN, NOW.plusSeconds(30), NOW)
                .fail(TOKEN, "BROKEN", "broken", false, NOW.plusSeconds(1));
        var ids = new AtomicLong(400L);

        var plan = ApprovalCompletionCompensation.materializePlan(
                List.of(one, two, failed), ids::incrementAndGet,
                NOW.plusSeconds(2));

        assertThat(plan).extracting(
                        ApprovalCompletionCompensation::originalExecutionId)
                .containsExactly(302L, 301L);
        assertThat(plan).extracting(
                        ApprovalCompletionCompensation::reverseOrdinal)
                .containsExactly(0, 1);
        assertThat(plan).extracting(ApprovalCompletionCompensation::status)
                .containsExactly(
                        ApprovalCompletionExecution.Status.AVAILABLE,
                        ApprovalCompletionExecution.Status.WAITING);
    }

    @Test
    void parentEntersCompensatingAndTerminatesWithOneHistoryFact() {
        var steps = List.of(
                step("first").withParallelGroup("publish"),
                step("second").withParallelGroup("publish"));
        var draft = new ApprovalDefinitionDraft(
                101L, "Expense", List.of(20L), 1, NOW.minusSeconds(10),
                null, null, null, ApprovalMode.SEQUENTIAL,
                null, null, null, null, null, null, null,
                null, steps, CompletionFailurePolicy.COMPENSATE);
        var definition = ApprovalDefinitionVersion.publish(
                draft, 1, NOW.minusSeconds(9));
        var approved = ApprovalInstance.start(
                201L, definition, "expense-201", 10L,
                NOW.minusSeconds(5)).approve(20L, "", NOW);
        var one = execution(301L, 0, steps.get(0));
        var two = execution(302L, 1, steps.get(1));
        var running = approved.beginCompletion(List.of(one, two));
        var closed = List.of(
                succeed(one),
                two.claim("worker", TOKEN, NOW.plusSeconds(30), NOW)
                        .fail(TOKEN, "BROKEN", "broken", false,
                                NOW.plusSeconds(1)));

        var compensating = running.beginCompensation(closed);
        var completed = compensating.completeCompensation(
                closed, 10L, NOW.plusSeconds(3));

        assertThat(compensating.completionFailurePolicy())
                .isEqualTo(CompletionFailurePolicy.COMPENSATE);
        assertThat(compensating.completionPhase())
                .isEqualTo(ApprovalInstance.CompletionPhase.COMPENSATING);
        assertThat(completed.status())
                .isEqualTo(ApprovalInstance.Status.TERMINATED);
        assertThat(completed.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.COMPLETION_COMPENSATED);
    }

    private static ApprovalCompletionExecution execution(
            long id, int ordinal, ApprovalCompletionStep step
    ) {
        return ApprovalCompletionExecution.materialize(
                id, 201L, 101L, 1, ordinal, step, "{}", NOW, true);
    }

    private static ApprovalCompletionExecution succeed(
            ApprovalCompletionExecution execution
    ) {
        return execution.claim(
                        "worker", TOKEN, NOW.plusSeconds(30), NOW)
                .complete(TOKEN, "{}", NOW.plusSeconds(1));
    }

    private static ApprovalCompletionStep step(String code) {
        return ApprovalCompletionStep.externalTask(
                code, code,
                new ApprovalCompletionStep.ExternalTask(
                        "topic." + code, 60, 3, 1024))
                .withCompensation(
                        ApprovalCompletionStep.Compensation.externalTask(
                                new ApprovalCompletionStep.ExternalTask(
                                        "undo." + code, 60, 3, 1024)));
    }
}
