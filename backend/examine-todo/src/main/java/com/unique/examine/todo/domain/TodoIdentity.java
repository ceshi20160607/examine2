package com.unique.examine.todo.domain;

public record TodoIdentity(
        long systemId,
        long tenantId,
        long recipientMemberId,
        TodoItem.SourceType sourceType,
        String sourceId,
        String actionScope
) {
    public TodoIdentity {
        if (systemId <= 0 || tenantId <= 0 || recipientMemberId <= 0 || sourceType == null) {
            throw new IllegalArgumentException("Todo identity scope is incomplete");
        }
        sourceId = normalize(sourceId, "sourceId", 200);
        actionScope = normalize(actionScope, "actionScope", 200);
        if (sourceType == TodoItem.SourceType.EVENT_MESSAGE) {
            if (!"MARK_READ".equals(actionScope) || !positiveLong(sourceId)) {
                throw new IllegalArgumentException("Event message Todo identity is invalid");
            }
        }
    }

    private static String normalize(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " must contain 1 to " + max + " characters");
        }
        return value.trim();
    }

    private static boolean positiveLong(String value) {
        try {
            return value.equals(Long.toString(Long.parseLong(value)))
                    && Long.parseLong(value) > 0;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
