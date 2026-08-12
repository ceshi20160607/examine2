package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalParallelCompletionStageTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T11:00:00Z");
    private static final String TOKEN_A = "a".repeat(64);
    private static final String TOKEN_B = "b".repeat(64);

    @Test
    void validatesAdjacentGroupsAndPreservesLegacySteps() {
        var first = step("first").withParallelGroup("publish");
        var second = step("second").withParallelGroup("publish");
        var legacy = step("legacy");

        assertThat(ApprovalCompletionStage.stages(
                List.of(first, second, legacy)))
                .containsExactly(
                        new ApprovalCompletionStage(0, 2, "publish"),
                        new ApprovalCompletionStage(2, 3, null));
        assertThat(ApprovalCompletionStep.requireSteps(List.of(legacy)))
                .containsExactly(legacy);
        assertThatThrownBy(() -> ApprovalCompletionStep.requireSteps(
                List.of(first)))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("2..8 adjacent");
        assertThatThrownBy(() -> ApprovalCompletionStep.requireSteps(List.of(
                first, second, step("middle"),
                step("later_a").withParallelGroup("publish"),
                step("later_b").withParallelGroup("publish"))))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("reuse");
        assertThatThrownBy(() -> step("invalid").withParallelGroup("Bad Group"))
                .isInstanceOf(ApprovalDomainException.class);
    }

    @Test
    void activatesAWholeGroupAndJoinsOnlyAfterEveryMemberSucceeds() {
        var plan = initialPlan();
        var activated = ApprovalCompletionStage.activate(plan, 0, NOW);

        assertThat(activated.subList(0, 2))
                .extracting(ApprovalCompletionExecution::status)
                .containsExactly(
                        ApprovalCompletionExecution.Status.AVAILABLE,
                        ApprovalCompletionExecution.Status.AVAILABLE);
        assertThat(activated.subList(0, 2))
                .extracting(ApprovalCompletionExecution::stateVersion)
                .containsExactly(0, 0);
        assertThat(activated.get(2).status())
                .isEqualTo(ApprovalCompletionExecution.Status.WAITING);

        var firstSucceeded = activated.get(0)
                .claim("worker-a", TOKEN_A, NOW.plusSeconds(60), NOW)
                .complete(TOKEN_A, "{}", NOW.plusSeconds(1));
        var secondFailed = activated.get(1)
                .claim("worker-b", TOKEN_B, NOW.plusSeconds(60), NOW)
                .fail(TOKEN_B, "FAILED", "Failed", false,
                        NOW.plusSeconds(1));
        var blocked = List.of(firstSucceeded, secondFailed, activated.get(2));

        assertThat(ApprovalCompletionStage.joined(blocked, 0)).isFalse();
        var secondSucceeded = secondFailed.retry(NOW.plusSeconds(2))
                .claim("worker-b", TOKEN_B, NOW.plusSeconds(62),
                        NOW.plusSeconds(2))
                .complete(TOKEN_B, "{}", NOW.plusSeconds(3));
        var joined = List.of(firstSucceeded, secondSucceeded, activated.get(2));
        assertThat(ApprovalCompletionStage.joined(joined, 0)).isTrue();

        var next = ApprovalCompletionStage.activate(
                joined, 2, NOW.plusSeconds(4));
        assertThat(next.get(2).status())
                .isEqualTo(ApprovalCompletionExecution.Status.AVAILABLE);
        assertThat(next.subList(0, 2))
                .extracting(ApprovalCompletionExecution::status)
                .containsOnly(ApprovalCompletionExecution.Status.SUCCEEDED);
    }

    @Test
    void keepsTheParentCursorAtTheGroupStartDuringOutOfOrderCompletion() {
        var definition = new ApprovalDefinitionVersion(
                101L, 1, "Parent", 20L, 1, NOW.minusSeconds(10));
        var approved = ApprovalInstance.start(
                201L, definition, "parent-201", 10L,
                NOW.minusSeconds(5)).approve(20L, "", NOW);
        var activated = ApprovalCompletionStage.activate(initialPlan(), 0, NOW);
        var firstSucceeded = activated.get(0)
                .claim("worker-a", TOKEN_A, NOW.plusSeconds(60), NOW)
                .complete(TOKEN_A, "{}", NOW.plusSeconds(1));

        var parent = approved.beginCompletion(activated).advanceCompletion(
                List.of(firstSucceeded, activated.get(1), activated.get(2)));

        assertThat(parent.activeCompletionOrdinal()).isZero();
        assertThat(parent.activeCompletionOrdinals()).containsExactly(0, 1);
        assertThat(parent.activeCompletionExecution()).isEqualTo(firstSucceeded);
    }

    private static List<ApprovalCompletionExecution> initialPlan() {
        return List.of(
                ApprovalCompletionExecution.materialize(
                        301L, 201L, 101L, 1, 0,
                        step("first").withParallelGroup("publish"), "{}", NOW),
                ApprovalCompletionExecution.materialize(
                        302L, 201L, 101L, 1, 1,
                        step("second").withParallelGroup("publish"), "{}", NOW),
                ApprovalCompletionExecution.materialize(
                        303L, 201L, 101L, 1, 2,
                        step("after"), "{}", NOW));
    }

    private static ApprovalCompletionStep step(String code) {
        return ApprovalCompletionStep.externalTask(
                code, code,
                new ApprovalCompletionStep.ExternalTask(
                        "topic." + code, 60, 3, 1024));
    }
}
