package com.unique.examine.flow.repository;

import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStage;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowParallelCompletionRepositoryContractTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T11:30:00Z");

    @Test
    void materializesAndLocksTheWholeStageInOrdinalOrder() {
        var repository = new InMemoryApprovalRepository();
        var initial = ApprovalCompletionStage.activate(plan(), 0, NOW);

        repository.materializeCompletionExecutions(initial);

        assertThat(repository.findCompletionStageForUpdate(201L, 0))
                .extracting(ApprovalCompletionExecution::id)
                .containsExactly(301L, 302L);
        assertThat(repository.findCompletionExecutionsByInstance(201L))
                .extracting(execution -> execution.step().parallelGroup())
                .containsExactly("publish", "publish", null);
    }

    @Test
    void appliesAGroupTransitionAtomicallyAndRejectsStaleReplay() {
        var repository = new InMemoryApprovalRepository();
        var initial = ApprovalCompletionStage.activate(plan(), 0, NOW);
        repository.materializeCompletionExecutions(initial);
        var claimed = List.of(
                initial.get(0).claim(
                        "worker-a", "a".repeat(64),
                        NOW.plusSeconds(60), NOW),
                initial.get(1).claim(
                        "worker-b", "b".repeat(64),
                        NOW.plusSeconds(60), NOW));

        repository.saveCompletionExecutions(claimed);

        assertThat(repository.findCompletionStageForUpdate(201L, 0))
                .extracting(ApprovalCompletionExecution::status)
                .containsOnly(ApprovalCompletionExecution.Status.LEASED);
        assertThatThrownBy(() ->
                repository.saveCompletionExecutions(claimed))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("concurrently");
    }

    private static List<ApprovalCompletionExecution> plan() {
        return List.of(execution(301L, 0, "first", "publish"),
                execution(302L, 1, "second", "publish"),
                execution(303L, 2, "after", null));
    }

    private static ApprovalCompletionExecution execution(
            long id, int ordinal, String code, String group
    ) {
        var step = ApprovalCompletionStep.externalTask(
                code, code,
                new ApprovalCompletionStep.ExternalTask(
                        "topic." + code, 60, 3, 1024))
                .withParallelGroup(group);
        return ApprovalCompletionExecution.materialize(
                id, 201L, 101L, 1, ordinal, step, "{}", NOW);
    }
}
