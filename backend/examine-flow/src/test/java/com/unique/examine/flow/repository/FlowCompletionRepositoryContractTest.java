package com.unique.examine.flow.repository;

import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowCompletionRepositoryContractTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T06:30:00Z");

    @Test
    void materializesOrderedSnapshotsAndFiltersAnOptionalTopic() {
        var repository = new InMemoryApprovalRepository();
        var alpha = execution(
                301L, 201L, "topic.alpha", 0, NOW);
        var waiting = ApprovalCompletionExecution.materialize(
                302L, 201L, 101L, 1, 1,
                externalStep("finish", "topic.beta"), "{}", NOW);
        var beta = execution(
                303L, 202L, "topic.beta", 0, NOW);

        repository.materializeCompletionExecutions(List.of(alpha, waiting));
        repository.materializeCompletionExecutions(List.of(beta));

        assertThat(repository.findCompletionExecutionsByInstance(201L))
                .containsExactly(alpha, waiting);
        assertThat(repository.findAvailableExternalTasks(
                null, NOW, 0, 20)).containsExactly(alpha, beta);
        assertThat(repository.findAvailableExternalTasks(
                "  ", NOW, 0, 20)).containsExactly(alpha, beta);
        assertThat(repository.findAvailableExternalTasks(
                "topic.beta", NOW, 0, 20)).containsExactly(beta);
        assertThat(repository.countAvailableExternalTasks(null, NOW))
                .isEqualTo(2);
    }

    @Test
    void appliesOptimisticTransitionsAndAppendOnlyIdempotencyFacts() {
        var repository = new InMemoryApprovalRepository();
        var available = execution(
                301L, 201L, "topic.alpha", 0, NOW);
        repository.materializeCompletionExecutions(List.of(available));
        var claimed = available.claim(
                "worker-1", "a".repeat(64),
                NOW.plusSeconds(60), NOW);

        repository.saveCompletionExecution(claimed);
        assertThat(repository.findCompletionExecution(301L))
                .contains(claimed);
        assertThatThrownBy(() ->
                repository.saveCompletionExecution(claimed))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("concurrently");

        var first = new ApprovalCompletionAttempt(
                401L, 301L, 1, 1,
                ApprovalCompletionAttempt.Event.CLAIMED,
                null, "worker-1", "c".repeat(64),
                null, null, null, NOW);
        repository.appendCompletionAttempt(first);
        assertThat(repository.findCompletionAttempts(301L))
                .containsExactly(first);

        var duplicateKey = new ApprovalCompletionAttempt(
                402L, 301L, 1, 2,
                ApprovalCompletionAttempt.Event.CLAIMED,
                null, "worker-1", "c".repeat(64),
                null, null, null, NOW.plusSeconds(1));
        assertThatThrownBy(() ->
                repository.appendCompletionAttempt(duplicateKey))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void exposesExpiredLeasesForRecoveryWithoutIncrementingAttempt() {
        var repository = new InMemoryApprovalRepository();
        var available = execution(
                301L, 201L, "topic.alpha", 0, NOW);
        repository.materializeCompletionExecutions(List.of(available));
        var claimed = available.claim(
                "worker-1", "a".repeat(64),
                NOW.plusSeconds(30), NOW);
        repository.saveCompletionExecution(claimed);

        assertThat(repository.findAvailableExternalTasks(
                "topic.alpha", NOW.plusSeconds(31), 0, 20))
                .containsExactly(claimed);
        var recovered = claimed.recoverExpiredLease(NOW.plusSeconds(31));

        assertThat(recovered.status())
                .isEqualTo(ApprovalCompletionExecution.Status.AVAILABLE);
        assertThat(recovered.attemptCount()).isEqualTo(1);
    }

    private static ApprovalCompletionExecution execution(
            long id,
            long instanceId,
            String topic,
            int ordinal,
            Instant createdAt
    ) {
        return ApprovalCompletionExecution.materialize(
                id, instanceId, 101L, 1, ordinal,
                externalStep("step_" + id, topic), "{}", createdAt);
    }

    private static ApprovalCompletionStep externalStep(
            String code,
            String topic
    ) {
        return ApprovalCompletionStep.externalTask(
                code, code,
                new ApprovalCompletionStep.ExternalTask(
                        topic, 60, 3, 2048));
    }
}
