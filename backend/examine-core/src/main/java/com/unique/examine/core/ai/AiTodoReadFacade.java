package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Todo-owner boundary for one bounded, current-member, read-only projection. */
public interface AiTodoReadFacade {
    int MAX_LIMIT = 20;

    Result query(Request request);

    enum Category { ALL, TASK, APPROVAL, REMINDER, CC }

    enum State { ALL, OPEN, CLOSED }

    enum Time { ALL, TODAY, OVERDUE }

    enum SourceType { WORK_TASK, FLOW_APPROVAL, EVENT_MESSAGE }

    enum Action { COMPLETE, APPROVE, REJECT, MARK_READ }

    record Request(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            Category category,
            State state,
            Time time,
            int limit
    ) {
        public Request {
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            effectivePermissions = permissions(effectivePermissions);
            category = Objects.requireNonNull(category, "category");
            state = Objects.requireNonNull(state, "state");
            time = Objects.requireNonNull(time, "time");
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException(
                        "limit must be within 1..20");
            }
        }
    }

    record Result(
            Category category,
            State state,
            Time time,
            long total,
            Counts counts,
            List<Item> items
    ) {
        public Result {
            category = Objects.requireNonNull(category, "category");
            state = Objects.requireNonNull(state, "state");
            time = Objects.requireNonNull(time, "time");
            if (total < 0) {
                throw new IllegalArgumentException("total is invalid");
            }
            counts = Objects.requireNonNull(counts, "counts");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (items.size() > MAX_LIMIT) {
                throw new IllegalArgumentException(
                        "items must contain at most 20 entries");
            }
            if (items.size() > total) {
                throw new IllegalArgumentException(
                        "items cannot exceed total");
            }
        }
    }

    record Counts(
            long open,
            long task,
            long approval,
            long today,
            long overdue
    ) {
        public Counts {
            if (open < 0 || task < 0 || approval < 0
                    || today < 0 || overdue < 0 || task + approval > open
                    || today > open || overdue > open) {
                throw new IllegalArgumentException(
                        "Todo counts are invalid");
            }
        }
    }

    record Item(
            String id,
            Category category,
            SourceType sourceType,
            String sourceId,
            String title,
            int priority,
            Instant dueAt,
            String routeHint,
            List<Action> actions,
            State state,
            long version
    ) {
        public Item {
            id = positiveDecimal(id, "id");
            if (category == null || category == Category.ALL) {
                throw new IllegalArgumentException("category is invalid");
            }
            sourceType = Objects.requireNonNull(sourceType, "sourceType");
            sourceId = requiredText(sourceId, "sourceId", 200);
            title = requiredText(title, "title", 500);
            if (priority < 0 || priority > 999) {
                throw new IllegalArgumentException("priority is invalid");
            }
            routeHint = requiredText(routeHint, "routeHint", 500);
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            if (actions.isEmpty()
                    || actions.stream().distinct().count() != actions.size()) {
                throw new IllegalArgumentException("actions are invalid");
            }
            var expectedSource = switch (category) {
                case TASK -> SourceType.WORK_TASK;
                case APPROVAL -> SourceType.FLOW_APPROVAL;
                case REMINDER, CC -> SourceType.EVENT_MESSAGE;
                case ALL -> throw new IllegalArgumentException(
                        "category is invalid");
            };
            var expectedActions = switch (category) {
                case TASK -> List.of(Action.COMPLETE);
                case APPROVAL -> List.of(Action.APPROVE, Action.REJECT);
                case REMINDER, CC -> List.of(Action.MARK_READ);
                case ALL -> throw new IllegalArgumentException(
                        "category is invalid");
            };
            if (sourceType != expectedSource || !actions.equals(expectedActions)) {
                throw new IllegalArgumentException(
                        "Todo category, source and actions are inconsistent");
            }
            if (state == null || state == State.ALL) {
                throw new IllegalArgumentException("state is invalid");
            }
            positive(version, "version");
        }
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static String requiredText(
            String value, String name, int maximum) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.strip();
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
