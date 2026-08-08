package com.unique.examine.core.api;

import com.unique.examine.core.context.ContextType;

import java.util.Objects;

public record OperationAudit(
        Actor actor,
        Context context,
        AggregateRef aggregate,
        String action,
        Object before,
        Object after,
        Result result,
        Failure failure,
        String requestId,
        String traceId
) {
    public OperationAudit {
        actor = Objects.requireNonNull(actor, "actor is required");
        context = Objects.requireNonNull(context, "context is required");
        aggregate = Objects.requireNonNull(aggregate, "aggregate is required");
        action = required(action, "action", 64);
        result = Objects.requireNonNull(result, "result is required");
        requestId = required(requestId, "requestId", 64);
        traceId = required(traceId, "traceId", 64);
        if (result == Result.SUCCESS && failure != null) {
            throw new IllegalArgumentException("success audit must not contain failure");
        }
        if (result != Result.SUCCESS && failure == null) {
            throw new IllegalArgumentException("denied or failed audit requires failure");
        }
    }

    public static OperationAudit success(
            Actor actor,
            Context context,
            AggregateRef aggregate,
            String action,
            Object before,
            Object after,
            String requestId,
            String traceId
    ) {
        return new OperationAudit(
                actor, context, aggregate, action, before, after,
                Result.SUCCESS, null, requestId, traceId
        );
    }

    public static OperationAudit denied(
            Actor actor,
            Context context,
            AggregateRef aggregate,
            String action,
            Object before,
            Object after,
            Failure failure,
            String requestId,
            String traceId
    ) {
        return new OperationAudit(
                actor, context, aggregate, action, before, after,
                Result.DENIED, failure, requestId, traceId
        );
    }

    public static OperationAudit failed(
            Actor actor,
            Context context,
            AggregateRef aggregate,
            String action,
            Object before,
            Object after,
            Failure failure,
            String requestId,
            String traceId
    ) {
        return new OperationAudit(
                actor, context, aggregate, action, before, after,
                Result.FAILED, failure, requestId, traceId
        );
    }

    public record Actor(Long accountId, String sourceType) {
        public Actor {
            if (accountId != null && accountId <= 0) {
                throw new IllegalArgumentException("actor accountId must be positive");
            }
            sourceType = required(sourceType, "actor sourceType", 24);
        }
    }

    public record Context(ContextType type, Long systemId, Long tenantId) {
        public Context {
            type = Objects.requireNonNull(type, "context type is required");
            positive(systemId, "context systemId");
            positive(tenantId, "context tenantId");
            if (type == ContextType.PLATFORM && (systemId != null || tenantId != null)) {
                throw new IllegalArgumentException("platform context must not contain systemId or tenantId");
            }
            if (type == ContextType.SYSTEM && systemId == null) {
                throw new IllegalArgumentException("system context requires systemId");
            }
            if (tenantId != null && systemId == null) {
                throw new IllegalArgumentException("tenant context requires systemId");
            }
        }
    }

    public record Failure(String code) {
        public Failure {
            code = required(code, "failure code", 64);
        }
    }

    public enum Result {
        SUCCESS,
        DENIED,
        FAILED
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
