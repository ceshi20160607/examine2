package com.unique.examine.todo.port;

import com.unique.examine.todo.domain.TodoItem;

import java.util.Set;

public record TodoSourceActionCommand(
        long systemId,
        long tenantId,
        long actorAccountId,
        long actorMemberId,
        Set<String> permissions,
        TodoItem.SourceType sourceType,
        String sourceId,
        long expectedSourceVersion,
        String actionScope,
        TodoItem.ActionCode action,
        String comment,
        String reason,
        Long representedMemberId,
        String callerIdempotencyKey,
        String requestId,
        String traceId
) {
    public TodoSourceActionCommand {
        if (systemId <= 0 || tenantId <= 0 || actorAccountId <= 0 || actorMemberId <= 0
                || sourceType == null || sourceId == null || sourceId.isBlank()
                || expectedSourceVersion <= 0 || actionScope == null || actionScope.isBlank()
                || action == null || callerIdempotencyKey == null
                || callerIdempotencyKey.isBlank() || callerIdempotencyKey.length() > 200
                || requestId == null || requestId.isBlank() || requestId.length() > 128
                || traceId == null || traceId.isBlank() || traceId.length() > 128
                || representedMemberId != null && representedMemberId <= 0) {
            throw new IllegalArgumentException("Todo source action command is invalid");
        }
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        sourceId = sourceId.trim();
        actionScope = actionScope.trim();
        callerIdempotencyKey = callerIdempotencyKey.trim();
        requestId = requestId.trim();
        traceId = traceId.trim();
        comment = normalizeOptional(comment, 2000);
        reason = normalizeOptional(reason, 2000);
        if (!TodoItem.supportsAction(sourceType, action)) {
            throw new IllegalArgumentException("Todo source action command is inconsistent");
        }
        if (sourceType == TodoItem.SourceType.EVENT_MESSAGE
                && (!"MARK_READ".equals(actionScope)
                || representedMemberId != null || !positiveLong(sourceId))) {
            throw new IllegalArgumentException("Event message Todo action command is invalid");
        }
    }

    public TodoSourceActionCommand(
            long systemId, long tenantId, long actorMemberId,
            Set<String> permissions, TodoItem.SourceType sourceType,
            String sourceId, long expectedSourceVersion, String actionScope,
            TodoItem.ActionCode action, String comment, String reason,
            Long representedMemberId, String callerIdempotencyKey,
            String requestId, String traceId
    ) {
        this(systemId, tenantId, actorMemberId, actorMemberId, permissions,
                sourceType, sourceId, expectedSourceVersion, actionScope,
                action, comment, reason, representedMemberId,
                callerIdempotencyKey, requestId, traceId);
    }

    private static String normalizeOptional(String value, int max) {
        if (value == null) return null;
        value = value.strip();
        if (value.isEmpty()) return null;
        if (value.codePointCount(0, value.length()) > max) {
            throw new IllegalArgumentException("Todo action text is too long");
        }
        return value;
    }

    private static boolean positiveLong(String value) {
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 && value.equals(Long.toString(parsed));
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
