package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformTaskFacade;

import java.time.Instant;
import java.util.Objects;

/** Immutable owner row for a self-assigned platform follow-up task. */
public record PlatformTask(
        long id,
        long accountId,
        String title,
        String description,
        Instant dueAt,
        PlatformTaskFacade.Priority priority,
        PlatformTaskFacade.Status status,
        PlatformTaskFacade.Source source,
        long authorizationEpoch,
        String payloadHash,
        String idempotencyKey,
        String requestId,
        String traceId,
        Instant createdAt,
        long createdBy,
        Instant updatedAt,
        Instant completedAt,
        Instant cancelledAt,
        long version
) {
    /** Batch 62 creation compatibility; lifecycle columns start at their defaults. */
    public PlatformTask(
            long id,
            long accountId,
            String title,
            String description,
            Instant dueAt,
            PlatformTaskFacade.Priority priority,
            PlatformTaskFacade.Status status,
            PlatformTaskFacade.Source source,
            long authorizationEpoch,
            String payloadHash,
            String idempotencyKey,
            String requestId,
            String traceId,
            Instant createdAt,
            long createdBy
    ) {
        this(id, accountId, title, description, dueAt, priority, status, source,
                authorizationEpoch, payloadHash, idempotencyKey, requestId,
                traceId, createdAt, createdBy, createdAt, null, null, 0);
    }

    public PlatformTask {
        positive(id, "id");
        positive(accountId, "accountId");
        title = text(title, "title", 200, false);
        description = text(description, "description", 2_000, true);
        priority = Objects.requireNonNull(priority, "priority");
        status = Objects.requireNonNull(status, "status");
        source = Objects.requireNonNull(source, "source");
        positive(authorizationEpoch, "authorizationEpoch");
        if (payloadHash == null || !payloadHash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("payloadHash is invalid");
        }
        idempotencyKey = token(idempotencyKey, "idempotencyKey", 128);
        requestId = token(requestId, "requestId", 128);
        traceId = token(traceId, "traceId", 128);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        positive(createdBy, "createdBy");
        if (createdBy != accountId) {
            throw new IllegalArgumentException("Platform task must be self-assigned");
        }
        if (dueAt != null && !dueAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("dueAt must be after createdAt");
        }
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt) || version < 0) {
            throw new IllegalArgumentException("Platform task lifecycle is invalid");
        }
        if (completedAt != null && (completedAt.isBefore(createdAt)
                || completedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("completedAt is invalid");
        }
        if (cancelledAt != null && (cancelledAt.isBefore(createdAt)
                || cancelledAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("cancelledAt is invalid");
        }
        var lifecycleValid = switch (status) {
            case OPEN -> completedAt == null && cancelledAt == null;
            case COMPLETED -> completedAt != null && cancelledAt == null;
            case CANCELLED -> completedAt == null && cancelledAt != null;
        };
        if (!lifecycleValid) {
            throw new IllegalArgumentException("Platform task status facts are invalid");
        }
    }

    private static String text(
            String value, String name, int maximum, boolean nullable) {
        if (nullable && value == null) return null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
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
        if (value <= 0) throw new IllegalArgumentException(name + " is invalid");
    }
}
