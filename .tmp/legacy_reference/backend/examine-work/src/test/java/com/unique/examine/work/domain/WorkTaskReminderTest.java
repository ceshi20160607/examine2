package com.unique.examine.work.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkTaskReminderTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final String TOKEN = "a".repeat(64);

    @Test
    void generationScheduleAndDeliveryKeyAreImmutable() {
        var reminder = pending();

        assertThat(reminder.generation()).isEqualTo(1);
        assertThat(reminder.scheduledAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(reminder.deliveryKey())
                .isEqualTo("work-task-reminder:10:20:30:1");
        assertThat(reminder.version()).isEqualTo(1);
    }

    @Test
    void claimRenewAndSendAdvanceOnlyReminderState() {
        var due = new WorkTaskReminder(
                10, 20, 30, 1, NOW, WorkTaskReminder.Status.PENDING,
                0, null, NOW.minusSeconds(60), NOW.minusSeconds(60),
                null, null, null, null, null, 1);

        var claimed = due.claim("worker", TOKEN, NOW.plusSeconds(30), NOW);
        var renewed = claimed.renew(TOKEN, NOW.plusSeconds(60), NOW.plusSeconds(1));
        var sent = renewed.sent(TOKEN, NOW.plusSeconds(2));

        assertThat(claimed.attemptCount()).isEqualTo(1);
        assertThat(renewed.lease().expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(sent.status()).isEqualTo(WorkTaskReminder.Status.SENT);
        assertThat(sent.sentAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(sent.version()).isEqualTo(4);
        assertThat(sent.scheduledAt()).isEqualTo(NOW);
    }

    @Test
    void expiredLeaseCanBeRecoveredAndReclaimed() {
        var due = new WorkTaskReminder(
                10, 20, 30, 1, NOW, WorkTaskReminder.Status.PENDING,
                0, null, NOW.minusSeconds(60), NOW.minusSeconds(60),
                null, null, null, null, null, 1);
        var claimed = due.claim("worker-a", TOKEN, NOW.plusSeconds(5), NOW);

        var recovered = claimed.recoverExpiredLease(NOW.plusSeconds(5));
        var reclaimed = recovered.claim(
                "worker-b", "b".repeat(64), NOW.plusSeconds(40), NOW.plusSeconds(6));

        assertThat(reclaimed.attemptCount()).isEqualTo(2);
        assertThat(reclaimed.lease().owner()).isEqualTo("worker-b");
        assertThatThrownBy(() -> claimed.sent(TOKEN, NOW.plusSeconds(6)))
                .isInstanceOf(WorkDomainException.class)
                .extracting("code")
                .isEqualTo("WORK_TASK_REMINDER_LEASE_EXPIRED");
    }

    @Test
    void retryKeepsFailedGenerationAndSchedule() {
        var due = new WorkTaskReminder(
                10, 20, 30, 7, NOW, WorkTaskReminder.Status.PENDING,
                0, null, NOW.minusSeconds(60), NOW.minusSeconds(60),
                null, null, null, null, null, 1);
        var failed = due.claim("worker", TOKEN, NOW.plusSeconds(30), NOW)
                .fail(TOKEN, "DELIVERY_FAILED", "Delivery failed", NOW.plusSeconds(1));

        var retry = failed.retry(NOW.plusSeconds(2));

        assertThat(retry.status()).isEqualTo(WorkTaskReminder.Status.PENDING);
        assertThat(retry.generation()).isEqualTo(7);
        assertThat(retry.scheduledAt()).isEqualTo(NOW);
        assertThat(retry.attemptCount()).isEqualTo(1);
        assertThat(retry.failureCode()).isNull();
    }

    @Test
    void taskReminderMustBeFutureAndNotAfterDue() {
        assertThatThrownBy(() -> WorkTask.requireReminderSchedule(
                NOW, NOW.plusSeconds(60), NOW))
                .isInstanceOf(WorkDomainException.class)
                .extracting("code")
                .isEqualTo("WORK_TASK_REMINDER_INVALID");
        assertThatThrownBy(() -> WorkTask.requireReminderSchedule(
                NOW.plusSeconds(120), NOW.plusSeconds(60), NOW))
                .isInstanceOf(WorkDomainException.class)
                .extracting("code")
                .isEqualTo("WORK_TASK_REMINDER_INVALID");
    }

    private static WorkTaskReminder pending() {
        return WorkTaskReminder.pending(
                10, 20, 30, 1, NOW.plusSeconds(60), NOW);
    }
}
