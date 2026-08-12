package com.unique.examine.flow.repository;

import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowCompensationRepositoryContractTest {
    private static final Instant NOW = Instant.parse("2026-07-31T12:30:00Z");
    private static final String TOKEN = "a".repeat(64);

    @Test
    void storesOneReversePlanAndAppliesOptimisticTransitions() {
        var repository = new InMemoryApprovalRepository();
        var available = List.of(available(301L, 0), available(302L, 1));
        repository.materializeCompletionExecutions(available);
        var claimed = available.stream().map(value -> value.claim(
                "worker", TOKEN, NOW.plusSeconds(30), NOW)).toList();
        repository.saveCompletionExecutions(claimed);
        var originals = claimed.stream().map(value -> value.complete(
                TOKEN, "{}", NOW.plusSeconds(1))).toList();
        repository.saveCompletionExecutions(originals);
        var ids = new AtomicLong(400L);
        var plan = ApprovalCompletionCompensation.materializePlan(
                originals, ids::incrementAndGet, NOW.plusSeconds(2));

        assertThat(repository.materializeCompensationPlan(plan))
                .containsExactlyElementsOf(plan);
        assertThat(repository.materializeCompensationPlan(plan))
                .containsExactlyElementsOf(plan);
        assertThat(repository.findCompensationsForUpdate(201L))
                .extracting(ApprovalCompletionCompensation::originalOrdinal)
                .containsExactly(1, 0);

        var compensationClaimed = plan.getFirst().claim(
                "worker", TOKEN, NOW.plusSeconds(60), NOW.plusSeconds(3));
        repository.saveCompensation(compensationClaimed);
        assertThat(repository.findAvailableCompensationExternalTasks(
                null, NOW.plusSeconds(61), 0, 10))
                .containsExactly(compensationClaimed);
        assertThatThrownBy(() -> repository.saveCompensation(compensationClaimed))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("concurrently");
    }

    private static ApprovalCompletionExecution available(long id, int ordinal) {
        return ApprovalCompletionExecution.materialize(
                id, 201L, 101L, 1, ordinal, step("step_" + id), "{}", NOW,
                true);
    }

    private static ApprovalCompletionStep step(String code) {
        return ApprovalCompletionStep.externalTask(
                code, code,
                new ApprovalCompletionStep.ExternalTask(
                        "topic." + code, 60, 3, 1024))
                .withParallelGroup("publish")
                .withCompensation(
                        ApprovalCompletionStep.Compensation.externalTask(
                                new ApprovalCompletionStep.ExternalTask(
                                        "undo." + code, 60, 3, 1024)));
    }
}
