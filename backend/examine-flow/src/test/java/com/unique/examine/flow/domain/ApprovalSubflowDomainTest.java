package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalSubflowDomainTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void freezesExactTargetAndRunsOneChildAttemptAtATime() {
        var step = ApprovalCompletionStep.subflow(
                "finance_review", "Finance review",
                new ApprovalCompletionStep.Subflow(102L, 3));
        var available = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 7, 0, step, "{}", NOW);

        var running = available.startSubflow(NOW.plusSeconds(1));
        var failed = running.failSubflow(
                "CHILD_REJECTED", "Child approval was rejected",
                NOW.plusSeconds(2));
        var retried = failed.retry(NOW.plusSeconds(3));
        var secondRun = retried.startSubflow(NOW.plusSeconds(4));
        var succeeded = secondRun.completeSubflow(
                "{\"childInstanceId\":502}", NOW.plusSeconds(5));

        assertThat(running.status())
                .isEqualTo(ApprovalCompletionExecution.Status.RUNNING);
        assertThat(running.attemptCount()).isEqualTo(1);
        assertThat(secondRun.attemptCount()).isEqualTo(2);
        assertThat(succeeded.status())
                .isEqualTo(ApprovalCompletionExecution.Status.SUCCEEDED);
        assertThat(succeeded.step().subflow())
                .isEqualTo(new ApprovalCompletionStep.Subflow(102L, 3));
        assertThatThrownBy(() -> new ApprovalCompletionStep.Subflow(0, 0))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting(error -> ((ApprovalDomainException) error).code())
                .isEqualTo(ApprovalDomainException.Code.COMPLETION_STEP_INVALID);
    }

    @Test
    void carriesImmutableRootAndRejectsDepthBeyondEight() {
        var root = new ApprovalStartContext(
                10L, "purchase_order", 901L, Map.of("amount", "100"))
                .root(201L);
        var current = root;
        for (var depth = 1; depth <= 8; depth++) {
            current = current.child(201L, depth);
        }

        assertThat(current.requesterMemberId()).isEqualTo(10L);
        assertThat(current.moduleCode()).isEqualTo("purchase_order");
        assertThat(current.recordId()).isEqualTo(901L);
        assertThat(current.rootInstanceId()).isEqualTo(201L);
        assertThat(current.subflowDepth()).isEqualTo(8);
        var childDefinition = new ApprovalDefinitionVersion(
                102L, 2, "Child", 20L, 1, NOW.minusSeconds(1));
        var child = ApprovalInstance.start(
                501L, childDefinition, "subflow-301-1", 10L, NOW,
                null, root.child(201L, 1));
        assertThat(child.recordBinding()).isNull();
        assertThat(child.startContext().hasRecord()).isTrue();
        assertThat(child.startContext().rootInstanceId()).isEqualTo(201L);
        var maximum = current;
        assertThatThrownBy(() -> maximum.child(201L, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth");
    }

    @Test
    void rejectsMissingCrossScopeAndCyclicExactVersionTargets() {
        var child = ApprovalCompletionStep.subflow(
                "child", "Child",
                new ApprovalCompletionStep.Subflow(102L, 2));
        var backToParent = ApprovalCompletionStep.subflow(
                "parent", "Parent",
                new ApprovalCompletionStep.Subflow(101L, 1));

        assertThatThrownBy(() -> ApprovalSubflowGraph.validateCompletionSteps(
                101L, 1, 1L, 2L, List.of(child),
                (definitionId, version) -> null))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting(error -> ((ApprovalDomainException) error).code())
                .isEqualTo(ApprovalDomainException.Code.SUBFLOW_TARGET_INVALID);

        assertThatThrownBy(() -> ApprovalSubflowGraph.validateCompletionSteps(
                101L, 1, 1L, 2L, List.of(child),
                (definitionId, version) -> new ApprovalSubflowGraph.Target(
                        1L, 99L, definitionId, version,
                        true, true, List.of())))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting(error -> ((ApprovalDomainException) error).code())
                .isEqualTo(ApprovalDomainException.Code.SUBFLOW_TARGET_INVALID);

        assertThatThrownBy(() -> ApprovalSubflowGraph.validateCompletionSteps(
                101L, 1, 1L, 2L, List.of(child),
                (definitionId, version) -> definitionId == 102L
                        ? new ApprovalSubflowGraph.Target(
                        1L, 2L, 102L, 2, true, true,
                        List.of(backToParent))
                        : new ApprovalSubflowGraph.Target(
                        1L, 2L, 101L, 1, true, true,
                        List.of(child))))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("101@1 -> 102@2 -> 101@1")
                .extracting(error -> ((ApprovalDomainException) error).code())
                .isEqualTo(ApprovalDomainException.Code.SUBFLOW_CYCLE);
    }

    @Test
    void rejectsAnExactVersionChainDeeperThanEight() {
        var first = ApprovalCompletionStep.subflow(
                "child_102", "Child 102",
                new ApprovalCompletionStep.Subflow(102L, 1));

        assertThatThrownBy(() -> ApprovalSubflowGraph.validateCompletionSteps(
                101L, 1, 1L, 2L, List.of(first),
                (definitionId, version) -> {
                    var next = definitionId < 110L
                            ? List.of(ApprovalCompletionStep.subflow(
                            "child_" + (definitionId + 1),
                            "Child " + (definitionId + 1),
                            new ApprovalCompletionStep.Subflow(
                                    definitionId + 1, 1)))
                            : List.<ApprovalCompletionStep>of();
                    return new ApprovalSubflowGraph.Target(
                            1L, 2L, definitionId, version,
                            true, true, next);
                }))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting(error -> ((ApprovalDomainException) error).code())
                .isEqualTo(ApprovalDomainException.Code.SUBFLOW_DEPTH_EXCEEDED);
    }

    @Test
    void rejectsDirectSelfReferenceEvenToAnotherPublishedVersion() {
        var self = ApprovalCompletionStep.subflow(
                "older_self", "Older self",
                new ApprovalCompletionStep.Subflow(101L, 1));

        assertThatThrownBy(() -> ApprovalSubflowGraph.validateCompletionSteps(
                101L, 2, 1L, 2L, List.of(self),
                (definitionId, version) -> new ApprovalSubflowGraph.Target(
                        1L, 2L, definitionId, version,
                        true, true, List.of())))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("101@2 -> 101@1")
                .extracting(error -> ((ApprovalDomainException) error).code())
                .isEqualTo(ApprovalDomainException.Code.SUBFLOW_CYCLE);
    }

    @Test
    void storesReplaySafeTerminalAndAppliedResultFacts() {
        var target = new ApprovalCompletionStep.Subflow(102L, 2);
        var launched = ApprovalSubflowRun.launch(
                401L, 301L, 1, "a".repeat(64), 501L,
                target, 201L, 1, NOW);
        var terminal = launched.observeTerminal(
                ApprovalSubflowRun.Status.APPROVED_COMPLETED,
                NOW.plusSeconds(1));
        var applied = terminal.markResultApplied(NOW.plusSeconds(2));

        assertThat(terminal.pendingResult()).isTrue();
        assertThat(terminal.resultCode()).isEqualTo("APPROVED_COMPLETED");
        assertThat(terminal.observeTerminal(
                ApprovalSubflowRun.Status.APPROVED_COMPLETED,
                NOW.plusSeconds(3))).isSameAs(terminal);
        assertThat(applied.pendingResult()).isFalse();
        assertThat(applied.markResultApplied(NOW.plusSeconds(3)))
                .isSameAs(applied);
        assertThatThrownBy(() -> terminal.observeTerminal(
                ApprovalSubflowRun.Status.REJECTED, NOW.plusSeconds(3)))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting(error -> ((ApprovalDomainException) error).code())
                .isEqualTo(ApprovalDomainException.Code.SUBFLOW_RUN_CONFLICT);
    }

    @Test
    void acceptsRunningSubflowAsTheActiveParentCursor() {
        var definition = new ApprovalDefinitionVersion(
                101L, 1, "Parent", 20L, 1, NOW.minusSeconds(10));
        var approved = ApprovalInstance.start(
                201L, definition, "parent-201", 10L,
                NOW.minusSeconds(5)).approve(20L, "", NOW);
        var available = ApprovalCompletionExecution.materialize(
                301L, 201L, 101L, 1, 0,
                ApprovalCompletionStep.subflow(
                        "child", "Child",
                        new ApprovalCompletionStep.Subflow(102L, 2)),
                "{}", NOW);
        var parent = approved.beginCompletion(List.of(available));
        var running = available.startSubflow(NOW.plusSeconds(1));

        var reloaded = parent.advanceCompletion(List.of(running));

        assertThat(reloaded.activeCompletionExecution()).isEqualTo(running);
        assertThat(reloaded.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(reloaded.activeApproverIds()).isEmpty();
    }
}
