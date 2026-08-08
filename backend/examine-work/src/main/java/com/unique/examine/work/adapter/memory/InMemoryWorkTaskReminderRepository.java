package com.unique.examine.work.adapter.memory;

import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTaskReminder;
import com.unique.examine.work.port.WorkTaskReminderRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryWorkTaskReminderRepository
        implements WorkTaskReminderRepository {
    private final Map<Key, WorkTaskReminder> reminders = new HashMap<>();

    @Override
    public synchronized WorkTaskReminder schedule(
            long systemId,
            long tenantId,
            long taskId,
            Instant scheduledAt,
            Instant now
    ) {
        var latest = findLatestInternal(systemId, tenantId, taskId).orElse(null);
        if (latest != null && latest.isLive()) {
            reminders.put(key(latest), latest.cancelLive(now));
        }
        var nextGeneration = latest == null ? 1 : Math.addExact(latest.generation(), 1);
        var scheduled = WorkTaskReminder.pending(systemId, tenantId, taskId,
                nextGeneration, scheduledAt, now);
        reminders.put(key(scheduled), scheduled);
        return scheduled;
    }

    @Override
    public synchronized Optional<WorkTaskReminder> find(
            long systemId,
            long tenantId,
            long taskId,
            int generation
    ) {
        return Optional.ofNullable(reminders.get(
                new Key(systemId, tenantId, taskId, generation)));
    }

    @Override
    public synchronized Optional<WorkTaskReminder> findLatest(
            long systemId,
            long tenantId,
            long taskId
    ) {
        return findLatestInternal(systemId, tenantId, taskId);
    }

    @Override
    public synchronized Optional<WorkTaskReminder> cancelLatestLive(
            long systemId,
            long tenantId,
            long taskId,
            Instant now
    ) {
        var latest = findLatestInternal(systemId, tenantId, taskId).orElse(null);
        if (latest == null) {
            return Optional.empty();
        }
        if (latest.isLive()) {
            latest = latest.cancelLive(now);
            reminders.put(key(latest), latest);
        }
        return Optional.of(latest);
    }

    @Override
    public synchronized WorkTaskReminder save(WorkTaskReminder reminder) {
        var reminderKey = key(reminder);
        var current = reminders.get(reminderKey);
        if (current == null) {
            if (reminder.version() != 1) {
                throw conflict();
            }
        } else {
            if (current.equals(reminder)) {
                return current;
            }
            if (reminder.version() != current.version() + 1) {
                throw conflict();
            }
            requireImmutable(current, reminder);
        }
        reminders.put(reminderKey, reminder);
        return reminder;
    }

    @Override
    public synchronized List<WorkTaskReminder> claimDue(
            String owner,
            String tokenHash,
            Instant now,
            Instant leaseUntil,
            int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Reminder claim limit must be within 1..100");
        }
        reminders.replaceAll((key, reminder) ->
                reminder.status() == WorkTaskReminder.Status.PROCESSING
                        && reminder.isDueAt(now)
                        ? reminder.recoverExpiredLease(now) : reminder);
        var due = reminders.values().stream()
                .filter(reminder -> reminder.status() == WorkTaskReminder.Status.PENDING)
                .filter(reminder -> reminder.isDueAt(now))
                .sorted(dueOrder())
                .limit(limit)
                .toList();
        return due.stream().map(reminder -> {
            var claimed = reminder.claim(owner, tokenHash, leaseUntil, now);
            reminders.put(key(claimed), claimed);
            return claimed;
        }).toList();
    }

    private Optional<WorkTaskReminder> findLatestInternal(
            long systemId,
            long tenantId,
            long taskId
    ) {
        return reminders.values().stream()
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId
                        && value.taskId() == taskId)
                .max(Comparator.comparingInt(WorkTaskReminder::generation));
    }

    private static Comparator<WorkTaskReminder> dueOrder() {
        return Comparator.comparing(WorkTaskReminder::scheduledAt)
                .thenComparingLong(WorkTaskReminder::taskId)
                .thenComparingInt(WorkTaskReminder::generation)
                .thenComparingLong(WorkTaskReminder::systemId)
                .thenComparingLong(WorkTaskReminder::tenantId);
    }

    private static Key key(WorkTaskReminder reminder) {
        return new Key(reminder.systemId(), reminder.tenantId(),
                reminder.taskId(), reminder.generation());
    }

    private static void requireImmutable(
            WorkTaskReminder current,
            WorkTaskReminder next
    ) {
        if ((current.status() == WorkTaskReminder.Status.SENT
                || current.status() == WorkTaskReminder.Status.CANCELLED)
                && !current.equals(next)) {
            throw new WorkDomainException("WORK_TASK_REMINDER_STATE_INVALID",
                    "Terminal reminder generations are immutable");
        }
        if (current.systemId() != next.systemId()
                || current.tenantId() != next.tenantId()
                || current.taskId() != next.taskId()
                || current.generation() != next.generation()
                || !current.scheduledAt().equals(next.scheduledAt())
                || !current.createdAt().equals(next.createdAt())) {
            throw new WorkDomainException("WORK_TASK_REMINDER_IMMUTABLE",
                    "Reminder generation facts are immutable");
        }
    }

    private static WorkDomainException conflict() {
        return new WorkDomainException("WORK_TASK_REMINDER_VERSION_CONFLICT",
                "Reminder version is stale");
    }

    private record Key(long systemId, long tenantId, long taskId, int generation) {
    }
}
