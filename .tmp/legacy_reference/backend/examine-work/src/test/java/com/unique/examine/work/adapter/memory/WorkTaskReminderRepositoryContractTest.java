package com.unique.examine.work.adapter.memory;

import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTaskReminder;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkTaskReminderRepositoryContractTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void rescheduleCancelsOldGenerationAndNeverMutatesSentHistory() {
        var repository = new InMemoryWorkTaskReminderRepository();
        var first = repository.schedule(10, 20, 30, NOW.plusSeconds(60), NOW);
        var second = repository.schedule(
                10, 20, 30, NOW.plusSeconds(120), NOW.plusSeconds(1));

        assertThat(repository.find(10, 20, 30, 1).orElseThrow().status())
                .isEqualTo(WorkTaskReminder.Status.CANCELLED);
        assertThat(second.generation()).isEqualTo(2);
        assertThat(second.scheduledAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(first.scheduledAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void expiredLeaseIsRecoveredAndClaimScanIsStableAndBounded() {
        var repository = new InMemoryWorkTaskReminderRepository();
        repository.schedule(10, 20, 40, NOW.plusSeconds(10), NOW);
        repository.schedule(10, 20, 30, NOW.plusSeconds(10), NOW);
        repository.schedule(10, 20, 20, NOW.plusSeconds(5), NOW);
        var dueAt = NOW.plusSeconds(11);

        var firstBatch = repository.claimDue(
                "worker-a", "a".repeat(64), dueAt, dueAt.plusSeconds(10), 2);
        var secondBatch = repository.claimDue(
                "worker-b", "b".repeat(64), dueAt.plusSeconds(10),
                dueAt.plusSeconds(30), 2);

        assertThat(firstBatch).extracting(WorkTaskReminder::taskId)
                .containsExactly(20L, 30L);
        assertThat(secondBatch).extracting(WorkTaskReminder::taskId)
                .containsExactly(20L, 30L);
        assertThat(secondBatch).extracting(WorkTaskReminder::attemptCount)
                .containsExactly(2, 2);
    }

    @Test
    void tenantScopeAndCasPreventCrossTenantOrStaleMutation() {
        var repository = new InMemoryWorkTaskReminderRepository();
        var tenantA = repository.schedule(10, 20, 30, NOW.plusSeconds(10), NOW);
        repository.schedule(10, 21, 30, NOW.plusSeconds(20), NOW);

        assertThat(repository.find(10, 21, 30, 1).orElseThrow().scheduledAt())
                .isEqualTo(NOW.plusSeconds(20));
        assertThat(repository.find(10, 22, 30, 1)).isEmpty();
        repository.save(tenantA.cancel(NOW.plusSeconds(1)));
        assertThatThrownBy(() -> repository.save(
                tenantA.cancel(NOW.plusSeconds(2))))
                .isInstanceOf(WorkDomainException.class)
                .extracting("code")
                .isEqualTo("WORK_TASK_REMINDER_VERSION_CONFLICT");
    }

    @Test
    void sentGenerationCannotBeRestoredToAWriteableState() {
        var repository = new InMemoryWorkTaskReminderRepository();
        var pending = repository.schedule(10, 20, 30, NOW.plusSeconds(10), NOW);
        var claimed = pending.claim("worker", "a".repeat(64),
                NOW.plusSeconds(30), NOW.plusSeconds(10));
        repository.save(claimed);
        var sent = claimed.sent("a".repeat(64), NOW.plusSeconds(11));
        repository.save(sent);
        var forgedPending = new WorkTaskReminder(
                sent.systemId(), sent.tenantId(), sent.taskId(), sent.generation(),
                sent.scheduledAt(), WorkTaskReminder.Status.PENDING,
                sent.attemptCount(), null, sent.createdAt(), NOW.plusSeconds(12),
                null, null, null, null, null, sent.version() + 1);

        assertThatThrownBy(() -> repository.save(forgedPending))
                .isInstanceOf(WorkDomainException.class)
                .extracting("code")
                .isEqualTo("WORK_TASK_REMINDER_STATE_INVALID");
    }
}
