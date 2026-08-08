package com.unique.examine.work.service;

import com.unique.examine.work.adapter.memory.InMemoryWorkTaskReminderRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskReminder;
import com.unique.examine.work.port.WorkTaskReminderNotification;
import com.unique.examine.work.port.WorkTaskReminderRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkTaskReminderClaimWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void sendsToCurrentAssigneeWithDeterministicKeyWithoutAdvancingTask() {
        var tasks = taskRepository();
        var reminders = reminderRepository();
        var sent = new ArrayList<WorkTaskReminderNotification>();
        var worker = worker(reminders, tasks, true, sent);

        assertThat(worker.runBatch(10)).isEqualTo(1);

        var reminder = reminders.findLatest(10, 20, 30).orElseThrow();
        assertThat(reminder.status()).isEqualTo(WorkTaskReminder.Status.SENT);
        assertThat(sent).singleElement().satisfies(notification -> {
            assertThat(notification.deliveryKey())
                    .isEqualTo("work-task-reminder:10:20:30:1");
            assertThat(notification.recipientMemberId()).isEqualTo(200);
            assertThat(notification.sourceType()).isEqualTo("WORK_TASK_REMINDER");
            assertThat(notification.referencePath()).isEqualTo("/work/tasks/30");
        });
        assertThat(tasks.findById(10, 20, 30).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void inactiveAssigneeFailsTerminallyWithSanitizedFacts() {
        var tasks = taskRepository();
        var reminders = reminderRepository();
        var sent = new ArrayList<WorkTaskReminderNotification>();

        worker(reminders, tasks, false, sent).runBatch(10);

        var reminder = reminders.findLatest(10, 20, 30).orElseThrow();
        assertThat(reminder.status()).isEqualTo(WorkTaskReminder.Status.FAILED);
        assertThat(reminder.failureCode()).isEqualTo("ASSIGNEE_UNAVAILABLE");
        assertThat(reminder.failureMessage()).isEqualTo("Task assignee is unavailable");
        assertThat(sent).isEmpty();
    }

    @Test
    void generationFencePreventsOldClaimDeliveryAfterReschedule() {
        var tasks = taskRepository();
        var delegate = reminderRepository();
        var reminders = new ReschedulingOnClaimRepository(delegate);
        var sent = new ArrayList<WorkTaskReminderNotification>();

        worker(reminders, tasks, true, sent).runBatch(10);

        assertThat(sent).isEmpty();
        assertThat(reminders.findLatest(10, 20, 30).orElseThrow().generation())
                .isEqualTo(2);
        assertThat(delegate.find(10, 20, 30, 1).orElseThrow().status())
                .isEqualTo(WorkTaskReminder.Status.CANCELLED);
    }

    private static InMemoryWorkTaskRepository taskRepository() {
        var tasks = new InMemoryWorkTaskRepository();
        tasks.save(new WorkTask(
                30, 10, 20, 100, 200, "Prepare release",
                WorkTask.Status.OPEN, NOW.minusSeconds(60), NOW.minusSeconds(60), 1,
                null, null, NOW.plusSeconds(60), NOW));
        return tasks;
    }

    private static InMemoryWorkTaskReminderRepository reminderRepository() {
        var reminders = new InMemoryWorkTaskReminderRepository();
        reminders.schedule(10, 20, 30, NOW, NOW.minusSeconds(60));
        return reminders;
    }

    private static WorkTaskReminderClaimWorker worker(
            WorkTaskReminderRepository reminders,
            InMemoryWorkTaskRepository tasks,
            boolean active,
            List<WorkTaskReminderNotification> sent
    ) {
        return new WorkTaskReminderClaimWorker(
                reminders, tasks, (system, tenant, member) -> active,
                sent::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class ReschedulingOnClaimRepository
            implements WorkTaskReminderRepository {
        private final WorkTaskReminderRepository delegate;

        private ReschedulingOnClaimRepository(WorkTaskReminderRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public WorkTaskReminder schedule(long systemId, long tenantId, long taskId,
                                         Instant scheduledAt, Instant now) {
            return delegate.schedule(systemId, tenantId, taskId, scheduledAt, now);
        }

        @Override
        public Optional<WorkTaskReminder> find(long systemId, long tenantId,
                                               long taskId, int generation) {
            return delegate.find(systemId, tenantId, taskId, generation);
        }

        @Override
        public Optional<WorkTaskReminder> findLatest(long systemId, long tenantId,
                                                     long taskId) {
            return delegate.findLatest(systemId, tenantId, taskId);
        }

        @Override
        public Optional<WorkTaskReminder> cancelLatestLive(long systemId, long tenantId,
                                                           long taskId, Instant now) {
            return delegate.cancelLatestLive(systemId, tenantId, taskId, now);
        }

        @Override
        public WorkTaskReminder save(WorkTaskReminder reminder) {
            return delegate.save(reminder);
        }

        @Override
        public List<WorkTaskReminder> claimDue(String owner, String tokenHash,
                                               Instant now, Instant leaseUntil, int limit) {
            var claimed = delegate.claimDue(owner, tokenHash, now, leaseUntil, limit);
            delegate.schedule(10, 20, 30, NOW.plusSeconds(20), NOW);
            return claimed;
        }
    }
}
