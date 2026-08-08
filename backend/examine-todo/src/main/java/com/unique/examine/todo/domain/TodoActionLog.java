package com.unique.examine.todo.domain;

import java.time.Instant;

public record TodoActionLog(
        long id,
        long systemId,
        long tenantId,
        long todoItemId,
        long recipientMemberId,
        long actorMemberId,
        String callerIdempotencyKey,
        TodoItem.SourceType sourceType,
        String sourceId,
        long sourceVersion,
        TodoItem.ActionCode requestedAction,
        Status status,
        ResultCode resultCode,
        String resultMessage,
        String requestId,
        String traceId,
        Instant createdAt,
        Instant completedAt,
        long version
) {
    public enum Status { PROCESSING, COMPLETED }
    public enum ResultCode { SUCCESS, STALE, DENIED, CONFLICT, FAILED, IN_PROGRESS }

    public TodoActionLog {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || todoItemId <= 0
                || recipientMemberId <= 0 || actorMemberId <= 0 || sourceVersion <= 0
                || sourceType == null || requestedAction == null || status == null
                || createdAt == null || version <= 0) {
            throw new IllegalArgumentException("Todo action log state is incomplete");
        }
        if (actorMemberId != recipientMemberId) {
            throw new IllegalArgumentException("Todo action actor must be its recipient");
        }
        if (!TodoItem.supportsAction(sourceType, requestedAction)) {
            throw new IllegalArgumentException("Todo source action is inconsistent");
        }
        callerIdempotencyKey = text(callerIdempotencyKey, "idempotency key", 200);
        sourceId = text(sourceId, "source id", 200);
        requestId = text(requestId, "request id", 128);
        traceId = text(traceId, "trace id", 128);
        if (status == Status.PROCESSING && (resultCode != null || resultMessage != null || completedAt != null)
                || status == Status.COMPLETED && (resultCode == null
                || resultCode == ResultCode.IN_PROGRESS || completedAt == null)) {
            throw new IllegalArgumentException("Todo action result facts are inconsistent");
        }
        if (resultMessage != null) {
            resultMessage = text(resultMessage, "result message", 500);
        }
    }

    public TodoActionLog complete(ResultCode code, String message, Instant now) {
        if (status != Status.PROCESSING || code == null || now == null || now.isBefore(createdAt)) {
            throw new TodoException("TODO_ACTION_STATE_INVALID", "Todo action log cannot be completed");
        }
        return new TodoActionLog(id, systemId, tenantId, todoItemId,
                recipientMemberId, actorMemberId, callerIdempotencyKey,
                sourceType, sourceId, sourceVersion, requestedAction,
                Status.COMPLETED, code, message, requestId, traceId,
                createdAt, now, version + 1);
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " must contain 1 to " + max + " characters");
        }
        return value.trim();
    }
}
