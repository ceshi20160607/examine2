package com.unique.examine.work.domain;

import java.time.Instant;

public record WorkTask(
        long id,
        long systemId,
        long tenantId,
        long creatorMemberId,
        long assigneeMemberId,
        String title,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        long version,
        Long projectId,
        String description,
        Instant dueAt,
        Instant reminderAt
) {
    public static final int MAX_DESCRIPTION_CHARACTERS = 2_000;

    public WorkTask(
            long id,
            long systemId,
            long tenantId,
            long creatorMemberId,
            long assigneeMemberId,
            String title,
            Status status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this(id, systemId, tenantId, creatorMemberId, assigneeMemberId,
                title, status, createdAt, updatedAt, version,
                null, null, null, null);
    }

    public WorkTask(
            long id,
            long systemId,
            long tenantId,
            long creatorMemberId,
            long assigneeMemberId,
            String title,
            Status status,
            Instant createdAt,
            Instant updatedAt,
            long version,
            Long projectId,
            String description,
            Instant dueAt
    ) {
        this(id, systemId, tenantId, creatorMemberId, assigneeMemberId,
                title, status, createdAt, updatedAt, version,
                projectId, description, dueAt, null);
    }

    public enum Status {
        OPEN,
        COMPLETED
    }

    public WorkTask {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || creatorMemberId <= 0 || assigneeMemberId <= 0) {
            throw new IllegalArgumentException("Task identity and scope values must be positive");
        }
        if (title == null || title.isBlank() || title.length() > 500) {
            throw new IllegalArgumentException("Task title must contain 1 to 500 characters");
        }
        if (status == null || createdAt == null || updatedAt == null || version <= 0) {
            throw new IllegalArgumentException("Task state is incomplete");
        }
        title = title.trim();
        if (projectId != null && projectId <= 0) {
            throw invalid("WORK_TASK_PROJECT_INVALID",
                    "Task project id must be positive");
        }
        description = normalizeDescription(description);
        if (reminderAt != null && dueAt != null && reminderAt.isAfter(dueAt)) {
            throw invalid("WORK_TASK_REMINDER_INVALID",
                    "Task reminder cannot be after its due time");
        }
    }

    public WorkTask assign(long memberId, Instant now) {
        return new WorkTask(id, systemId, tenantId, creatorMemberId, memberId, title,
                status, createdAt, now, version + 1,
                projectId, description, dueAt, reminderAt);
    }

    public WorkTask complete(Instant now) {
        return new WorkTask(id, systemId, tenantId, creatorMemberId, assigneeMemberId, title,
                Status.COMPLETED, createdAt, now, version + 1,
                projectId, description, dueAt, null);
    }

    public WorkTask reopen(Instant now) {
        return new WorkTask(id, systemId, tenantId, creatorMemberId, assigneeMemberId, title,
                Status.OPEN, createdAt, now, version + 1,
                projectId, description, dueAt, reminderAt);
    }

    public WorkTask reviseMetadata(
            String nextTitle,
            String nextDescription,
            Long nextProjectId,
            Instant nextDueAt,
            Instant now
    ) {
        return reviseMetadata(nextTitle, nextDescription, nextProjectId,
                nextDueAt, null, now);
    }

    public WorkTask reviseMetadata(
            String nextTitle,
            String nextDescription,
            Long nextProjectId,
            Instant nextDueAt,
            Instant nextReminderAt,
            Instant now
    ) {
        if (nextReminderAt != null) {
            requireReminderSchedule(nextReminderAt, nextDueAt, now);
        }
        return new WorkTask(
                id, systemId, tenantId, creatorMemberId, assigneeMemberId,
                nextTitle, status, createdAt, now, version + 1,
                nextProjectId, nextDescription, nextDueAt, nextReminderAt);
    }

    public static void requireReminderSchedule(
            Instant reminderAt,
            Instant dueAt,
            Instant now
    ) {
        if (reminderAt == null || now == null) {
            throw invalid("WORK_TASK_REMINDER_INVALID",
                    "Task reminder and current time are required");
        }
        if (!reminderAt.isAfter(now)) {
            throw invalid("WORK_TASK_REMINDER_INVALID",
                    "Task reminder must be in the future");
        }
        if (dueAt != null && reminderAt.isAfter(dueAt)) {
            throw invalid("WORK_TASK_REMINDER_INVALID",
                    "Task reminder cannot be after its due time");
        }
    }

    private static String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        value = value.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length())
                > MAX_DESCRIPTION_CHARACTERS) {
            throw invalid("WORK_TASK_DESCRIPTION_INVALID",
                    "Task description cannot exceed "
                            + MAX_DESCRIPTION_CHARACTERS + " characters");
        }
        return value;
    }

    private static WorkDomainException invalid(String code, String message) {
        return new WorkDomainException(code, message);
    }
}
