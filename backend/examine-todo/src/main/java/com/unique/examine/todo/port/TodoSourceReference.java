package com.unique.examine.todo.port;

import com.unique.examine.todo.domain.TodoItem;

public record TodoSourceReference(
        TodoItem.SourceType sourceType,
        String sourceId,
        long sourceVersion,
        String actionScope,
        Long representedMemberId
) {
    public TodoSourceReference {
        if (sourceType == null || sourceId == null || sourceId.isBlank()
                || sourceId.length() > 200 || sourceVersion <= 0
                || actionScope == null || actionScope.isBlank()
                || actionScope.length() > 200
                || representedMemberId != null && representedMemberId <= 0) {
            throw new IllegalArgumentException("Todo source reference is invalid");
        }
        sourceId = sourceId.trim();
        actionScope = actionScope.trim();
        if (sourceType == TodoItem.SourceType.EVENT_MESSAGE
                && (!"MARK_READ".equals(actionScope)
                || representedMemberId != null
                || !positiveLong(sourceId))) {
            throw new IllegalArgumentException("Event message Todo source reference is invalid");
        }
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
