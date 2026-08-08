package com.unique.examine.todo.service;

import com.unique.examine.todo.domain.*;
import com.unique.examine.todo.port.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

@Transactional
public class TodoService {
    private final TodoRepository repository;
    private final Map<TodoItem.SourceType, TodoSourcePort> sources;
    private final Map<TodoItem.SourceType, TodoActionPort> actions;
    private final Clock clock;

    public TodoService(
            TodoRepository repository,
            List<TodoSourcePort> sources,
            List<TodoActionPort> actions,
            Clock clock
    ) {
        if (repository == null || sources == null || actions == null || clock == null) {
            throw new IllegalArgumentException("Todo service dependencies are required");
        }
        this.repository = repository;
        this.sources = indexSources(sources);
        this.actions = indexActions(actions);
        if (!this.sources.keySet().equals(this.actions.keySet())) {
            throw new IllegalArgumentException(
                    "Todo source and action adapters must be registered as pairs");
        }
        this.clock = clock;
    }

    public TodoRefreshResult refresh(TodoActor actor) {
        var now = Instant.now(clock);
        var discovered = 0;
        var created = 0;
        var updated = 0;
        var closed = 0;
        for (var source : sources.values()) {
            var live = source.loadOpen(actor);
            if (live == null) {
                throw new TodoException("TODO_SOURCE_INVALID",
                        "Todo source returned a null projection list");
            }
            var identities = new HashSet<TodoIdentity>();
            for (var snapshot : live) {
                requireSnapshotScope(actor, source.sourceType(), snapshot);
                discovered++;
                if (!identities.add(snapshot.identity())) {
                    throw new TodoException("TODO_SOURCE_INVALID",
                            "Todo source returned a duplicate identity");
                }
                var current = repository.findByIdentity(snapshot.identity()).orElse(null);
                if (current == null) {
                    repository.save(snapshot.create(repository.nextItemId(), now));
                    created++;
                } else {
                    var reconciled = current.reconcile(snapshot, now);
                    if (reconciled != current) {
                        repository.save(reconciled);
                        updated++;
                    }
                }
            }
            for (var current : repository.findOpen(actor.systemId(), actor.tenantId(),
                    actor.memberId(), source.sourceType())) {
                if (!identities.contains(current.identity())) {
                    var reload = source.reload(actor, reference(current));
                    requireReloadScope(actor, source.sourceType(), reload);
                    if (reload.status() == TodoSourceReload.Status.LIVE) {
                        throw new TodoException("TODO_SOURCE_INVALID",
                                "Todo source omitted a live projection");
                    }
                    closeForReload(current, reload.status(), now);
                    closed++;
                }
            }
        }
        return new TodoRefreshResult(discovered, created, updated, closed);
    }

    @Transactional(readOnly = true)
    public TodoPage page(TodoActor actor, TodoQuery query) {
        if (query == null) throw new IllegalArgumentException("Todo query is required");
        return repository.findPage(actor.systemId(), actor.tenantId(), actor.memberId(), query);
    }

    @Transactional(readOnly = true)
    public TodoCounts counts(TodoActor actor) {
        var day = LocalDate.now(clock);
        var start = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        return repository.counts(actor.systemId(), actor.tenantId(), actor.memberId(),
                start, day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    public TodoDetailResult detail(TodoActor actor, long itemId) {
        var item = scopedItem(actor, itemId);
        if (item.state() == TodoItem.State.CLOSED) {
            return new TodoDetailResult(TodoDetailResult.Code.STALE, item);
        }
        var reload = source(item.identity().sourceType()).reload(actor, reference(item));
        requireReloadScope(actor, item.identity().sourceType(), reload);
        if (reload.status() == TodoSourceReload.Status.DENIED) {
            return new TodoDetailResult(TodoDetailResult.Code.DENIED, item);
        }
        if (!sameLiveProjection(item, reload)) {
            var closed = closeForReload(item, reload.status(), Instant.now(clock));
            return new TodoDetailResult(TodoDetailResult.Code.STALE, closed);
        }
        return new TodoDetailResult(TodoDetailResult.Code.LIVE, item);
    }

    public TodoActionOutcome action(
            TodoActor actor,
            long itemId,
            long expectedTodoVersion,
            TodoItem.ActionCode requestedAction,
            String comment,
            String reason,
            String callerIdempotencyKey
    ) {
        var item = scopedItem(actor, itemId);
        if (requestedAction == null || callerIdempotencyKey == null
                || callerIdempotencyKey.isBlank()) {
            throw new TodoException("TODO_ACTION_INVALID", "Todo action and idempotency key are required");
        }
        var replay = repository.findAction(actor.systemId(), actor.tenantId(),
                actor.memberId(), callerIdempotencyKey);
        if (replay.isPresent()) {
            var existing = replay.orElseThrow();
            if (existing.todoItemId() != item.id()
                    || existing.requestedAction() != requestedAction) {
                throw new TodoException("TODO_ACTION_CONFLICT",
                        "Idempotency key belongs to another Todo action");
            }
            var replayCode = existing.status() == TodoActionLog.Status.COMPLETED
                    ? existing.resultCode() : TodoActionLog.ResultCode.IN_PROGRESS;
            return new TodoActionOutcome(replayCode, existing, item, true);
        }
        if (expectedTodoVersion <= 0 || item.version() != expectedTodoVersion) {
            throw new TodoException("TODO_VERSION_CONFLICT", "Todo item version is stale");
        }
        if (!item.availableActions().contains(requestedAction)) {
            throw new TodoException("TODO_ACTION_INVALID", "Todo action is unavailable");
        }
        var now = Instant.now(clock);
        var proposed = new TodoActionLog(repository.nextActionLogId(), actor.systemId(),
                actor.tenantId(), item.id(), actor.memberId(), actor.memberId(),
                callerIdempotencyKey, item.identity().sourceType(),
                item.identity().sourceId(), item.sourceVersion(), requestedAction,
                TodoActionLog.Status.PROCESSING, null, null,
                actor.requestId(), actor.traceId(), now, null, 1);
        var reservation = repository.reserveAction(proposed);
        if (!reservation.acquired()) {
            var existing = reservation.log();
            requireSameIdempotentRequest(existing, item, requestedAction);
            var result = existing.status() == TodoActionLog.Status.COMPLETED
                    ? existing.resultCode() : TodoActionLog.ResultCode.IN_PROGRESS;
            return new TodoActionOutcome(result, existing, item, true);
        }
        if (item.state() == TodoItem.State.CLOSED) {
            return completeOutcome(item, proposed, TodoActionLog.ResultCode.STALE,
                    "Todo is closed", false);
        }
        var reload = source(item.identity().sourceType()).reload(actor, reference(item));
        requireReloadScope(actor, item.identity().sourceType(), reload);
        if (reload.status() == TodoSourceReload.Status.DENIED) {
            return completeOutcome(item, proposed, TodoActionLog.ResultCode.DENIED,
                    "Todo source authorization was denied", false);
        }
        if (!sameLiveProjection(item, reload)) {
            var closed = closeForReload(item, reload.status(), Instant.now(clock));
            return completeOutcome(closed, proposed, TodoActionLog.ResultCode.STALE,
                    "Todo source is stale", false);
        }
        if (!reload.snapshot().availableActions().contains(requestedAction)) {
            return completeOutcome(item, proposed, TodoActionLog.ResultCode.DENIED,
                    "Todo action is unavailable", false);
        }
        final TodoSourceActionResult nativeResult;
        try {
            nativeResult = actionPort(item.identity().sourceType()).execute(
                    new TodoSourceActionCommand(actor.systemId(), actor.tenantId(),
                        actor.accountId(), actor.memberId(), actor.permissions(),
                        item.identity().sourceType(),
                        item.identity().sourceId(), item.sourceVersion(),
                        item.identity().actionScope(), requestedAction, comment, reason,
                        item.representedMemberId(), callerIdempotencyKey,
                        actor.requestId(), actor.traceId()));
        } catch (RuntimeException unexpected) {
            return completeOutcome(item, proposed, TodoActionLog.ResultCode.FAILED,
                    "Todo source action failed", false);
        }
        var code = map(nativeResult.code());
        if (code == TodoActionLog.ResultCode.SUCCESS || code == TodoActionLog.ResultCode.STALE) {
            item = repository.save(item.close(code == TodoActionLog.ResultCode.SUCCESS
                    ? TodoItem.CloseReason.ACTION_COMPLETED
                    : TodoItem.CloseReason.SOURCE_STALE, Instant.now(clock)));
        }
        return completeOutcome(item, proposed, code, nativeResult.message(), false);
    }

    private TodoActionOutcome completeOutcome(TodoItem item, TodoActionLog log,
                                              TodoActionLog.ResultCode code,
                                              String message, boolean replayed) {
        var completed = repository.completeAction(log.complete(code, message, Instant.now(clock)));
        return new TodoActionOutcome(code, completed, item, replayed);
    }

    private static void requireSameIdempotentRequest(
            TodoActionLog existing,
            TodoItem item,
            TodoItem.ActionCode requestedAction
    ) {
        if (existing.todoItemId() != item.id()
                || existing.requestedAction() != requestedAction) {
            throw new TodoException("TODO_ACTION_CONFLICT",
                    "Idempotency key belongs to another Todo action");
        }
    }

    private TodoItem closeForReload(TodoItem item, TodoSourceReload.Status status, Instant now) {
        var reason = switch (status) {
            case COMPLETED -> TodoItem.CloseReason.SOURCE_COMPLETED;
            case MISSING -> TodoItem.CloseReason.SOURCE_MISSING;
            case INELIGIBLE -> TodoItem.CloseReason.RECIPIENT_INELIGIBLE;
            case DENIED -> TodoItem.CloseReason.RECIPIENT_INELIGIBLE;
            default -> TodoItem.CloseReason.SOURCE_STALE;
        };
        return repository.save(item.close(reason, now));
    }

    private TodoItem scopedItem(TodoActor actor, long itemId) {
        return repository.findById(actor.systemId(), actor.tenantId(), actor.memberId(), itemId)
                .orElseThrow(() -> new TodoException("TODO_NOT_FOUND", "Todo was not found"));
    }

    private static boolean sameLiveProjection(TodoItem item, TodoSourceReload reload) {
        return reload.status() == TodoSourceReload.Status.LIVE
                && reload.snapshot().identity().equals(item.identity())
                && reload.snapshot().sourceVersion() == item.sourceVersion()
                && reload.snapshot().category() == item.category()
                && reload.snapshot().priority() == item.priority()
                && reload.snapshot().title().equals(item.title())
                && java.util.Objects.equals(reload.snapshot().dueAt(), item.dueAt())
                && reload.snapshot().routeHint().equals(item.routeHint())
                && reload.snapshot().availableActions().equals(item.availableActions())
                && java.util.Objects.equals(reload.snapshot().representedMemberId(),
                item.representedMemberId());
    }

    private static void requireReloadScope(
            TodoActor actor,
            TodoItem.SourceType sourceType,
            TodoSourceReload reload
    ) {
        if (reload == null) {
            throw new TodoException("TODO_SOURCE_INVALID",
                    "Todo source returned a null reload result");
        }
        if (reload.status() == TodoSourceReload.Status.LIVE) {
            requireSnapshotScope(actor, sourceType, reload.snapshot());
        }
    }

    private static TodoSourceReference reference(TodoItem item) {
        return new TodoSourceReference(item.identity().sourceType(),
                item.identity().sourceId(), item.sourceVersion(),
                item.identity().actionScope(), item.representedMemberId());
    }

    private static void requireSnapshotScope(TodoActor actor, TodoItem.SourceType sourceType,
                                             TodoSourceSnapshot snapshot) {
        var identity = snapshot.identity();
        if (identity.systemId() != actor.systemId() || identity.tenantId() != actor.tenantId()
                || identity.recipientMemberId() != actor.memberId()
                || identity.sourceType() != sourceType) {
            throw new TodoException("TODO_SOURCE_INVALID", "Todo source escaped actor scope");
        }
    }

    private TodoSourcePort source(TodoItem.SourceType type) {
        var source = sources.get(type);
        if (source == null) throw new IllegalStateException("Missing Todo source: " + type);
        return source;
    }

    private TodoActionPort actionPort(TodoItem.SourceType type) {
        var action = actions.get(type);
        if (action == null) throw new IllegalStateException("Missing Todo action port: " + type);
        return action;
    }

    private static TodoActionLog.ResultCode map(TodoSourceActionResult.Code code) {
        return switch (code) {
            case SUCCESS -> TodoActionLog.ResultCode.SUCCESS;
            case STALE -> TodoActionLog.ResultCode.STALE;
            case DENIED -> TodoActionLog.ResultCode.DENIED;
            case CONFLICT -> TodoActionLog.ResultCode.CONFLICT;
            case FAILED -> TodoActionLog.ResultCode.FAILED;
        };
    }

    private static Map<TodoItem.SourceType, TodoSourcePort> indexSources(List<TodoSourcePort> values) {
        var result = new EnumMap<TodoItem.SourceType, TodoSourcePort>(TodoItem.SourceType.class);
        values.forEach(value -> {
            if (value == null || value.sourceType() == null) {
                throw new IllegalArgumentException("Todo source adapter is invalid");
            }
            if (result.put(value.sourceType(), value) != null) {
                throw new IllegalArgumentException("Duplicate Todo source: " + value.sourceType());
            }
        });
        return Map.copyOf(result);
    }

    private static Map<TodoItem.SourceType, TodoActionPort> indexActions(List<TodoActionPort> values) {
        var result = new EnumMap<TodoItem.SourceType, TodoActionPort>(TodoItem.SourceType.class);
        values.forEach(value -> {
            if (value == null || value.sourceType() == null) {
                throw new IllegalArgumentException("Todo action adapter is invalid");
            }
            if (result.put(value.sourceType(), value) != null) {
                throw new IllegalArgumentException("Duplicate Todo action: " + value.sourceType());
            }
        });
        return Map.copyOf(result);
    }
}
