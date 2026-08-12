package com.unique.examine.todo.adapter.memory;

import com.unique.examine.todo.domain.*;
import com.unique.examine.todo.port.TodoRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryTodoRepository implements TodoRepository {
    private final AtomicLong itemIds = new AtomicLong();
    private final AtomicLong actionIds = new AtomicLong();
    private final Map<Long, TodoItem> items = new HashMap<>();
    private final Map<TodoIdentity, Long> identities = new HashMap<>();
    private final Map<ActionKey, TodoActionLog> actions = new HashMap<>();

    @Override
    public long nextItemId() {
        return itemIds.incrementAndGet();
    }

    @Override
    public long nextActionLogId() {
        return actionIds.incrementAndGet();
    }

    @Override
    public synchronized Optional<TodoItem> findByIdentity(TodoIdentity identity) {
        var id = identities.get(identity);
        return id == null ? Optional.empty() : Optional.ofNullable(items.get(id));
    }

    @Override
    public synchronized Optional<TodoItem> findById(
            long systemId, long tenantId, long recipientMemberId, long itemId
    ) {
        return Optional.ofNullable(items.get(itemId))
                .filter(item -> item.identity().systemId() == systemId
                        && item.identity().tenantId() == tenantId
                        && item.identity().recipientMemberId() == recipientMemberId);
    }

    @Override
    public synchronized List<TodoItem> findOpen(
            long systemId, long tenantId, long recipientMemberId,
            TodoItem.SourceType sourceType
    ) {
        return items.values().stream()
                .filter(item -> scoped(item, systemId, tenantId, recipientMemberId))
                .filter(item -> item.identity().sourceType() == sourceType)
                .filter(item -> item.state() == TodoItem.State.OPEN)
                .sorted(order())
                .toList();
    }

    @Override
    public synchronized TodoPage findPage(
            long systemId, long tenantId, long recipientMemberId, TodoQuery query
    ) {
        var filtered = items.values().stream()
                .filter(item -> scoped(item, systemId, tenantId, recipientMemberId))
                .filter(item -> query.state() == TodoQuery.StateFilter.ALL
                        || item.state().name().equals(query.state().name()))
                .filter(item -> query.category() == TodoQuery.CategoryFilter.ALL
                        || item.category().name().equals(query.category().name()))
                .filter(item -> matchesTime(item, query))
                .sorted(order())
                .toList();
        var from = Math.min(query.offset(), filtered.size());
        var to = Math.min(from + query.size(), filtered.size());
        return new TodoPage(filtered.subList((int) from, (int) to),
                query.page(), query.size(), filtered.size());
    }

    @Override
    public synchronized TodoCounts counts(
            long systemId, long tenantId, long recipientMemberId,
            Instant todayStart, Instant tomorrowStart
    ) {
        var open = items.values().stream()
                .filter(item -> scoped(item, systemId, tenantId, recipientMemberId))
                .filter(item -> item.state() == TodoItem.State.OPEN)
                .toList();
        return new TodoCounts(
                open.size(),
                open.stream().filter(item -> item.category() == TodoItem.Category.TASK).count(),
                open.stream().filter(item -> item.category() == TodoItem.Category.APPROVAL).count(),
                open.stream().filter(item -> item.dueAt() != null
                        && !item.dueAt().isBefore(todayStart)
                        && item.dueAt().isBefore(tomorrowStart)).count(),
                open.stream().filter(item -> item.dueAt() != null
                        && item.dueAt().isBefore(todayStart)).count());
    }

    @Override
    public synchronized TodoItem save(TodoItem item) {
        var current = items.get(item.id());
        if (current == null) {
            if (item.version() != 1 || identities.containsKey(item.identity())) {
                throw conflict("TODO_VERSION_CONFLICT", "Todo item already exists");
            }
        } else {
            if (current.equals(item)) return current;
            if (item.version() != current.version() + 1) {
                throw conflict("TODO_VERSION_CONFLICT", "Todo item version is stale");
            }
            if (!current.identity().equals(item.identity())
                    || current.createdAt().equals(item.createdAt()) == false) {
                throw conflict("TODO_IDENTITY_CONFLICT", "Todo identity facts are immutable");
            }
            if (current.state() == TodoItem.State.CLOSED) {
                throw conflict("TODO_STATE_CONFLICT", "Closed Todo history is immutable");
            }
        }
        items.put(item.id(), item);
        identities.put(item.identity(), item.id());
        return item;
    }

    @Override
    public synchronized TodoActionReservation reserveAction(TodoActionLog proposed) {
        var key = key(proposed);
        var existing = actions.get(key);
        if (existing != null) return new TodoActionReservation(existing, false);
        if (proposed.version() != 1 || proposed.status() != TodoActionLog.Status.PROCESSING) {
            throw conflict("TODO_ACTION_VERSION_CONFLICT", "Todo action reservation is invalid");
        }
        actions.put(key, proposed);
        return new TodoActionReservation(proposed, true);
    }

    @Override
    public synchronized TodoActionLog completeAction(TodoActionLog completed) {
        var key = key(completed);
        var current = actions.get(key);
        if (current == null || current.status() != TodoActionLog.Status.PROCESSING
                || completed.version() != current.version() + 1
                || current.id() != completed.id()
                || current.todoItemId() != completed.todoItemId()
                || current.sourceVersion() != completed.sourceVersion()
                || current.requestedAction() != completed.requestedAction()) {
            throw conflict("TODO_ACTION_VERSION_CONFLICT", "Todo action log version is stale");
        }
        actions.put(key, completed);
        return completed;
    }

    @Override
    public synchronized Optional<TodoActionLog> findAction(
            long systemId, long tenantId, long actorMemberId,
            String callerIdempotencyKey
    ) {
        return Optional.ofNullable(actions.get(new ActionKey(
                systemId, tenantId, actorMemberId, callerIdempotencyKey)));
    }

    private static boolean scoped(TodoItem item, long systemId, long tenantId,
                                  long recipientMemberId) {
        return item.identity().systemId() == systemId
                && item.identity().tenantId() == tenantId
                && item.identity().recipientMemberId() == recipientMemberId;
    }

    private static boolean matchesTime(TodoItem item, TodoQuery query) {
        return switch (query.time()) {
            case ALL -> true;
            case TODAY -> item.dueAt() != null
                    && !item.dueAt().isBefore(query.todayStart())
                    && item.dueAt().isBefore(query.tomorrowStart());
            case OVERDUE -> item.dueAt() != null
                    && item.dueAt().isBefore(query.todayStart());
        };
    }

    private static Comparator<TodoItem> order() {
        return Comparator.comparingInt(TodoItem::priority)
                .thenComparing(TodoItem::dueAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::createdAt)
                .thenComparingLong(TodoItem::id);
    }

    private static ActionKey key(TodoActionLog log) {
        return new ActionKey(log.systemId(), log.tenantId(),
                log.actorMemberId(), log.callerIdempotencyKey());
    }

    private static TodoException conflict(String code, String message) {
        return new TodoException(code, message);
    }

    private record ActionKey(long systemId, long tenantId, long actorMemberId,
                             String callerIdempotencyKey) {
    }
}
