package com.unique.examine.todo.domain;

import java.time.Instant;
import java.util.Set;

public record TodoItem(
        long id,
        TodoIdentity identity,
        long sourceVersion,
        Category category,
        int priority,
        String title,
        Instant dueAt,
        String routeHint,
        Set<ActionCode> availableActions,
        Long representedMemberId,
        State state,
        CloseReason closeReason,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        long version
) {
    public enum SourceType { WORK_TASK, FLOW_APPROVAL, EVENT_MESSAGE }
    public enum Category { TASK, APPROVAL, REMINDER, CC }
    public enum ActionCode { COMPLETE, APPROVE, REJECT, MARK_READ }
    public enum State { OPEN, CLOSED }
    public enum CloseReason {
        SOURCE_STALE, SOURCE_COMPLETED, SOURCE_MISSING,
        RECIPIENT_INELIGIBLE, ACTION_COMPLETED
    }

    public TodoItem {
        if (id <= 0 || identity == null || sourceVersion <= 0 || category == null
                || priority < 0 || priority > 999 || state == null
                || createdAt == null || updatedAt == null || version <= 0
                || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Todo item state is incomplete");
        }
        if (title == null || title.isBlank() || title.length() > 500) {
            throw new IllegalArgumentException("Todo title must contain 1 to 500 characters");
        }
        title = title.trim();
        if (routeHint == null || routeHint.isBlank() || routeHint.length() > 500) {
            throw new IllegalArgumentException("Todo route hint must contain 1 to 500 characters");
        }
        routeHint = routeHint.trim();
        availableActions = availableActions == null ? Set.of() : Set.copyOf(availableActions);
        if (availableActions.isEmpty()) {
            throw new IllegalArgumentException("Todo requires at least one available action");
        }
        if (representedMemberId != null && representedMemberId <= 0) {
            throw new IllegalArgumentException("Represented member id must be positive");
        }
        requireSourceProjection(identity, category, availableActions, representedMemberId);
        if (state == State.OPEN && (closeReason != null || closedAt != null)
                || state == State.CLOSED && (closeReason == null || closedAt == null)) {
            throw new IllegalArgumentException("Todo close facts are inconsistent");
        }
        if (closedAt != null && (closedAt.isBefore(createdAt) || closedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("Todo close timestamp is inconsistent");
        }
    }

    public TodoItem reconcile(TodoSourceSnapshot source, Instant now) {
        if (!identity.equals(source.identity())) {
            throw invalid("TODO_SOURCE_INVALID", "Todo source identity does not match");
        }
        if (state == State.CLOSED || source.sourceVersion() < sourceVersion) {
            return this;
        }
        if (source.sourceVersion() == sourceVersion
                && category == source.category()
                && priority == source.priority()
                && title.equals(source.title())
                && java.util.Objects.equals(dueAt, source.dueAt())
                && routeHint.equals(source.routeHint())
                && availableActions.equals(source.availableActions())
                && java.util.Objects.equals(representedMemberId,
                source.representedMemberId())) {
            return this;
        }
        return new TodoItem(id, identity, source.sourceVersion(), source.category(),
                source.priority(), source.title(), source.dueAt(), source.routeHint(),
                source.availableActions(), source.representedMemberId(), State.OPEN, null, createdAt, now,
                null, version + 1);
    }

    public TodoItem close(CloseReason reason, Instant now) {
        if (state == State.CLOSED) return this;
        if (reason == null || now == null || now.isBefore(updatedAt)) {
            throw invalid("TODO_STATE_INVALID", "Todo close facts are invalid");
        }
        return new TodoItem(id, identity, sourceVersion, category, priority, title,
                dueAt, routeHint, availableActions, representedMemberId,
                State.CLOSED, reason,
                createdAt, now, now, version + 1);
    }

    private static TodoException invalid(String code, String message) {
        return new TodoException(code, message);
    }

    static void requireSourceProjection(
            TodoIdentity identity,
            Category category,
            Set<ActionCode> availableActions,
            Long representedMemberId
    ) {
        var consistent = switch (identity.sourceType()) {
            case WORK_TASK -> category == Category.TASK
                    && availableActions.equals(Set.of(ActionCode.COMPLETE))
                    && representedMemberId == null;
            case FLOW_APPROVAL -> category == Category.APPROVAL
                    && availableActions.equals(Set.of(ActionCode.APPROVE, ActionCode.REJECT))
                    && representedMemberId != null;
            case EVENT_MESSAGE -> Set.of(Category.REMINDER, Category.CC).contains(category)
                    && availableActions.equals(Set.of(ActionCode.MARK_READ))
                    && representedMemberId == null
                    && "MARK_READ".equals(identity.actionScope());
        };
        if (!consistent) {
            throw new IllegalArgumentException("Todo source projection is inconsistent");
        }
    }

    public static boolean supportsAction(SourceType sourceType, ActionCode action) {
        if (sourceType == null || action == null) return false;
        return switch (sourceType) {
            case WORK_TASK -> action == ActionCode.COMPLETE;
            case FLOW_APPROVAL -> Set.of(ActionCode.APPROVE, ActionCode.REJECT).contains(action);
            case EVENT_MESSAGE -> action == ActionCode.MARK_READ;
        };
    }
}
