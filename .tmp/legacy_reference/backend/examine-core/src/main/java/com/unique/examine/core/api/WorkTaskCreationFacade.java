package com.unique.examine.core.api;

import java.time.Instant;
import java.util.Set;

/** Work-owned boundary for creating one actionable tenant task. */
public interface WorkTaskCreationFacade {
    CreatedTask create(Command command);

    TaskState state(StateQuery query);

    record Command(
            long accountId,
            long systemId,
            long tenantId,
            long creatorMemberId,
            Set<String> effectivePermissions,
            long assigneeMemberId,
            String title,
            String description,
            Long projectId,
            Instant dueAt,
            AggregateRef source,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public Command {
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(creatorMemberId, "creatorMemberId");
            positive(assigneeMemberId, "assigneeMemberId");
            effectivePermissions = Set.copyOf(requiredPermissions(effectivePermissions));
            title = text(title, "title", 500, false);
            description = text(description, "description", 2_000, true);
            if (projectId != null) positive(projectId, "projectId");
            if (source == null) throw new IllegalArgumentException("source is required");
            idempotencyKey = token(idempotencyKey, "idempotencyKey", 128);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record CreatedTask(
            long taskId,
            long version,
            long systemId,
            long tenantId,
            long creatorMemberId,
            long assigneeMemberId,
            String title,
            String description,
            Long projectId,
            Instant dueAt,
            String status,
            Instant createdAt,
            boolean replay
    ) {
        public CreatedTask {
            positive(taskId, "taskId");
            positive(version, "version");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(creatorMemberId, "creatorMemberId");
            positive(assigneeMemberId, "assigneeMemberId");
            title = text(title, "title", 500, false);
            description = text(description, "description", 2_000, true);
            if (projectId != null) positive(projectId, "projectId");
            if (!"OPEN".equals(status) || createdAt == null) {
                throw new IllegalArgumentException("created task is incomplete");
            }
        }

        public CreatedTask asReplay() {
            return replay ? this : new CreatedTask(
                    taskId, version, systemId, tenantId, creatorMemberId,
                    assigneeMemberId, title, description, projectId, dueAt,
                    status, createdAt, true);
        }
    }

    record StateQuery(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            long taskId,
            String requestId,
            String traceId
    ) {
        public StateQuery {
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(taskId, "taskId");
            effectivePermissions = Set.copyOf(requiredPermissions(effectivePermissions));
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record TaskState(long taskId, long version, String status) {
        public TaskState {
            positive(taskId, "taskId");
            positive(version, "version");
            if (!Set.of("OPEN", "COMPLETED").contains(status)) {
                throw new IllegalArgumentException("task status is invalid");
            }
        }
    }

    private static Set<String> requiredPermissions(Set<String> value) {
        if (value == null || value.stream().anyMatch(
                item -> item == null || item.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return value;
    }

    private static String text(
            String value, String name, int maximum, boolean nullable) {
        if (nullable && value == null) return null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        var normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return normalized;
    }

    private static String token(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }
}
