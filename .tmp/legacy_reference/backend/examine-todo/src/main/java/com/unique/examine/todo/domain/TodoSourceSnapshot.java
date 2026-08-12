package com.unique.examine.todo.domain;

import java.time.Instant;
import java.util.Set;

public record TodoSourceSnapshot(
        TodoIdentity identity,
        long sourceVersion,
        TodoItem.Category category,
        int priority,
        String title,
        Instant dueAt,
        String routeHint,
        Set<TodoItem.ActionCode> availableActions,
        Long representedMemberId
) {
    public TodoSourceSnapshot {
        if (identity == null || sourceVersion <= 0 || category == null
                || priority < 0 || priority > 999 || title == null || title.isBlank()
                || title.length() > 500 || routeHint == null || routeHint.isBlank()
                || routeHint.length() > 500 || availableActions == null
                || availableActions.isEmpty()) {
            throw new IllegalArgumentException("Todo source snapshot is incomplete");
        }
        title = title.trim();
        routeHint = routeHint.trim();
        availableActions = Set.copyOf(availableActions);
        if (representedMemberId != null && representedMemberId <= 0) {
            throw new IllegalArgumentException("Represented member id must be positive");
        }
        TodoItem.requireSourceProjection(
                identity, category, availableActions, representedMemberId);
    }

    public TodoItem create(long id, Instant now) {
        return new TodoItem(id, identity, sourceVersion, category, priority,
                title, dueAt, routeHint, availableActions, representedMemberId,
                TodoItem.State.OPEN, null, now, now, null, 1);
    }
}
