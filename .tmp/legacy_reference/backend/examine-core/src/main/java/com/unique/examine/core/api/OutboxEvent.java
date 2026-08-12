package com.unique.examine.core.api;

import java.util.Objects;

public record OutboxEvent(
        String eventType,
        int eventVersion,
        AggregateRef aggregate,
        Context context,
        String idempotencyKey,
        Object payload,
        String traceId
) {
    public OutboxEvent {
        eventType = required(eventType, "eventType", 96);
        if (eventVersion <= 0) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        aggregate = Objects.requireNonNull(aggregate, "aggregate is required");
        if (aggregate.id() == null) {
            throw new IllegalArgumentException("outbox aggregate id is required");
        }
        context = Objects.requireNonNull(context, "context is required");
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 160);
        payload = Objects.requireNonNull(payload, "payload is required");
        traceId = required(traceId, "traceId", 64);
    }

    public record Context(Long systemId, Long tenantId) {
        public Context {
            positive(systemId, "context systemId");
            positive(tenantId, "context tenantId");
            if (tenantId != null && systemId == null) {
                throw new IllegalArgumentException("tenant context requires systemId");
            }
        }
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    private static void positive(Long value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
