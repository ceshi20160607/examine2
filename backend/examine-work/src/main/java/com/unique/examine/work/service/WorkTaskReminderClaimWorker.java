package com.unique.examine.work.service;

import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskReminder;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.port.WorkTaskReminderNotification;
import com.unique.examine.work.port.WorkTaskReminderNotifier;
import com.unique.examine.work.port.WorkTaskReminderRepository;
import com.unique.examine.work.port.WorkTaskRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Claims and delivers a bounded batch without ever advancing task versions. */
public final class WorkTaskReminderClaimWorker {
    public static final String DEFAULT_OWNER = "work-task-reminder-worker";
    public static final Duration DEFAULT_LEASE = Duration.ofMinutes(1);

    private final WorkTaskReminderRepository reminders;
    private final WorkTaskRepository tasks;
    private final WorkMemberDirectory members;
    private final WorkTaskReminderNotifier notifier;
    private final Clock clock;
    private final String owner;
    private final Duration leaseDuration;

    public WorkTaskReminderClaimWorker(
            WorkTaskReminderRepository reminders,
            WorkTaskRepository tasks,
            WorkMemberDirectory members,
            WorkTaskReminderNotifier notifier,
            Clock clock
    ) {
        this(reminders, tasks, members, notifier, clock,
                DEFAULT_OWNER, DEFAULT_LEASE);
    }

    public WorkTaskReminderClaimWorker(
            WorkTaskReminderRepository reminders,
            WorkTaskRepository tasks,
            WorkMemberDirectory members,
            WorkTaskReminderNotifier notifier,
            Clock clock,
            String owner,
            Duration leaseDuration
    ) {
        if (reminders == null || tasks == null || members == null
                || notifier == null || clock == null) {
            throw new IllegalArgumentException("Reminder worker dependencies are required");
        }
        if (owner == null || owner.isBlank() || owner.length() > 160) {
            throw new IllegalArgumentException("Reminder worker owner is invalid");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Reminder worker lease duration must be positive");
        }
        this.reminders = reminders;
        this.tasks = tasks;
        this.members = members;
        this.notifier = notifier;
        this.clock = clock;
        this.owner = owner.trim();
        this.leaseDuration = leaseDuration;
    }

    public int runBatch(int limit) {
        var now = Instant.now(clock);
        var tokenHash = sha256(UUID.randomUUID().toString());
        var claimed = reminders.claimDue(owner, tokenHash, now,
                now.plus(leaseDuration), limit);
        claimed.forEach(reminder -> process(reminder, tokenHash, Instant.now(clock)));
        return claimed.size();
    }

    private void process(
            WorkTaskReminder reminder,
            String tokenHash,
            Instant now
    ) {
        var task = tasks.findById(
                reminder.systemId(), reminder.tenantId(), reminder.taskId())
                .orElse(null);
        if (task == null) {
            fail(reminder, tokenHash, "TASK_UNAVAILABLE",
                    "Task is unavailable", now);
            return;
        }
        if (!isCurrentOpenSchedule(task, reminder)) {
            saveIfCurrent(reminder.cancelLive(now));
            return;
        }
        if (!stillOwnsLatestGeneration(reminder, tokenHash, now)) {
            return;
        }
        if (!members.isActiveMember(
                reminder.systemId(), reminder.tenantId(), task.assigneeMemberId())) {
            fail(reminder, tokenHash, "ASSIGNEE_UNAVAILABLE",
                    "Task assignee is unavailable", now);
            return;
        }

        try {
            notifier.send(notification(reminder, task));
        } catch (RuntimeException deliveryFailure) {
            fail(reminder, tokenHash, "DELIVERY_FAILED",
                    "Reminder delivery failed", Instant.now(clock));
            return;
        }
        saveIfCurrent(reminder.sent(tokenHash, Instant.now(clock)));
    }

    private void fail(
            WorkTaskReminder reminder,
            String tokenHash,
            String code,
            String message,
            Instant now
    ) {
        saveIfCurrent(reminder.fail(tokenHash, code, message, now));
    }

    private void saveIfCurrent(WorkTaskReminder next) {
        try {
            reminders.save(next);
        } catch (WorkDomainException conflict) {
            if (!"WORK_TASK_REMINDER_VERSION_CONFLICT".equals(conflict.code())) {
                throw conflict;
            }
            // A concurrent cancel/reschedule won the CAS; never restore the old generation.
        }
    }

    private static boolean isCurrentOpenSchedule(
            WorkTask task,
            WorkTaskReminder reminder
    ) {
        return task.status() == WorkTask.Status.OPEN
                && task.reminderAt() != null
                && task.reminderAt().equals(reminder.scheduledAt());
    }

    private boolean stillOwnsLatestGeneration(
            WorkTaskReminder claimed,
            String tokenHash,
            Instant now
    ) {
        return reminders.findLatest(
                        claimed.systemId(), claimed.tenantId(), claimed.taskId())
                .filter(current -> current.generation() == claimed.generation())
                .filter(current -> current.version() == claimed.version())
                .filter(current -> current.status()
                        == WorkTaskReminder.Status.PROCESSING)
                .filter(current -> current.lease() != null
                        && current.lease().tokenHash().equals(tokenHash)
                        && current.lease().expiresAt().isAfter(now))
                .isPresent();
    }

    private static WorkTaskReminderNotification notification(
            WorkTaskReminder reminder,
            WorkTask task
    ) {
        return new WorkTaskReminderNotification(
                reminder.deliveryKey(), reminder.systemId(), reminder.tenantId(),
                task.assigneeMemberId(), task.id(), task.title(), task.dueAt(),
                WorkTaskReminderNotification.SOURCE_TYPE,
                "/work/tasks/" + task.id());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
