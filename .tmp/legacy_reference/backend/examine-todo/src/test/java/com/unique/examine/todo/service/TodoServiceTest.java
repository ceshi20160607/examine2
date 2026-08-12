package com.unique.examine.todo.service;

import com.unique.examine.todo.adapter.memory.InMemoryTodoRepository;
import com.unique.examine.todo.domain.*;
import com.unique.examine.todo.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final TodoActor ACTOR = new TodoActor(
            10, 20, 30, Set.of("work.task.access"), "request-1", "trace-1");

    @Test
    void refreshIsProjectionIdempotentAndClosesMissingWithoutDeletingHistory() {
        var repository = new InMemoryTodoRepository();
        var source = new FakeSource();
        var service = service(repository, source, command -> success(command.expectedSourceVersion()));

        var first = service.refresh(ACTOR);
        var second = service.refresh(ACTOR);
        var item = repository.findByIdentity(source.snapshot.identity()).orElseThrow();
        source.open = false;
        source.reload = TodoSourceReload.of(TodoSourceReload.Status.MISSING);
        var third = service.refresh(ACTOR);

        assertThat(first.created()).isEqualTo(1);
        assertThat(second.updated()).isZero();
        assertThat(item.version()).isEqualTo(1);
        assertThat(third.closed()).isEqualTo(1);
        var closed = repository.findByIdentity(source.snapshot.identity()).orElseThrow();
        assertThat(closed.state()).isEqualTo(TodoItem.State.CLOSED);
        assertThat(closed.closeReason()).isEqualTo(TodoItem.CloseReason.SOURCE_MISSING);
    }

    @Test
    void actionReauthorizesPassesRealPermissionsAndReplaysWithoutSecondExecution() {
        var repository = new InMemoryTodoRepository();
        var source = new FakeSource();
        var calls = new AtomicInteger();
        var service = service(repository, source, command -> {
            calls.incrementAndGet();
            assertThat(command.permissions()).containsExactly("work.task.access");
            return success(command.expectedSourceVersion());
        });
        service.refresh(ACTOR);
        var item = repository.findByIdentity(source.snapshot.identity()).orElseThrow();

        var first = service.action(ACTOR, item.id(), item.version(),
                TodoItem.ActionCode.COMPLETE, null, null, "idem-1");
        var replay = service.action(ACTOR, item.id(), item.version(),
                TodoItem.ActionCode.COMPLETE, null, null, "idem-1");

        assertThat(first.code()).isEqualTo(TodoActionLog.ResultCode.SUCCESS);
        assertThat(first.item().state()).isEqualTo(TodoItem.State.CLOSED);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.code()).isEqualTo(TodoActionLog.ResultCode.SUCCESS);
        assertThat(calls).hasValue(1);
    }

    @Test
    void staleDetailClosesItemAndOldTodoVersionIsRejectedBeforeActionLog() {
        var repository = new InMemoryTodoRepository();
        var source = new FakeSource();
        var service = service(repository, source, command -> success(1));
        service.refresh(ACTOR);
        var item = repository.findByIdentity(source.snapshot.identity()).orElseThrow();

        assertThatThrownBy(() -> service.action(ACTOR, item.id(), 99,
                TodoItem.ActionCode.COMPLETE, null, null, "idem-stale"))
                .isInstanceOf(TodoException.class)
                .extracting("code").isEqualTo("TODO_VERSION_CONFLICT");
        assertThat(repository.findAction(10, 20, 30, "idem-stale")).isEmpty();
        source.reload = TodoSourceReload.of(TodoSourceReload.Status.COMPLETED);
        var detail = service.detail(ACTOR, item.id());
        assertThat(detail.code()).isEqualTo(TodoDetailResult.Code.STALE);
        assertThat(detail.item().closeReason())
                .isEqualTo(TodoItem.CloseReason.SOURCE_COMPLETED);
    }

    @Test
    void unexpectedNativeFailureIsSanitizedAndLogged() {
        var repository = new InMemoryTodoRepository();
        var source = new FakeSource();
        var service = service(repository, source, command -> {
            throw new IllegalStateException("database-password=secret");
        });
        service.refresh(ACTOR);
        var item = repository.findByIdentity(source.snapshot.identity()).orElseThrow();

        var outcome = service.action(ACTOR, item.id(), item.version(),
                TodoItem.ActionCode.COMPLETE, null, null, "idem-failure");

        assertThat(outcome.code()).isEqualTo(TodoActionLog.ResultCode.FAILED);
        assertThat(outcome.log().resultMessage()).isEqualTo("Todo source action failed");
        assertThat(outcome.log().resultMessage()).doesNotContain("secret");
        assertThat(outcome.item().state()).isEqualTo(TodoItem.State.OPEN);
    }

    @Test
    void concurrentReservationCannotReplayAnotherTodoForTheSameKey() {
        var delegate = new InMemoryTodoRepository();
        var source = new FakeSource();
        var repository = new RacingRepository(delegate);
        var calls = new AtomicInteger();
        var service = service(repository, source, command -> {
            calls.incrementAndGet();
            return success(1);
        });
        service.refresh(ACTOR);
        var item = delegate.findByIdentity(source.snapshot.identity()).orElseThrow();
        repository.racingLog = new TodoActionLog(999, 10, 20, item.id() + 1,
                30, 30, "racing-key", TodoItem.SourceType.WORK_TASK, "other", 1,
                TodoItem.ActionCode.COMPLETE, TodoActionLog.Status.PROCESSING,
                null, null, "request-1", "trace-1", NOW, null, 1);

        assertThatThrownBy(() -> service.action(ACTOR, item.id(), item.version(),
                TodoItem.ActionCode.COMPLETE, null, null, "racing-key"))
                .isInstanceOf(TodoException.class)
                .extracting("code").isEqualTo("TODO_ACTION_CONFLICT");
        assertThat(calls).hasValue(0);
    }

    @Test
    void serviceHasRealSpringTransactionBoundaryAndIsProxyable() {
        assertThat(TodoService.class.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(java.lang.reflect.Modifier.isFinal(TodoService.class.getModifiers())).isFalse();
    }

    @Test
    void eventReminderIsDiscoveredAndMarkReadClosesItExactlyOnce() {
        var repository = new InMemoryTodoRepository();
        var source = new FakeEventSource(TodoItem.Category.REMINDER, "701");
        var calls = new AtomicInteger();
        TodoActionPort action = new TodoActionPort() {
            @Override public TodoItem.SourceType sourceType() {
                return TodoItem.SourceType.EVENT_MESSAGE;
            }
            @Override public TodoSourceActionResult execute(TodoSourceActionCommand command) {
                calls.incrementAndGet();
                assertThat(command.sourceType()).isEqualTo(TodoItem.SourceType.EVENT_MESSAGE);
                assertThat(command.sourceId()).isEqualTo("701");
                assertThat(command.actionScope()).isEqualTo("MARK_READ");
                assertThat(command.action()).isEqualTo(TodoItem.ActionCode.MARK_READ);
                assertThat(command.representedMemberId()).isNull();
                return success(command.expectedSourceVersion());
            }
        };
        var service = service(repository, source, action);

        var refresh = service.refresh(ACTOR);
        var item = repository.findByIdentity(source.snapshot.identity()).orElseThrow();
        var outcome = service.action(ACTOR, item.id(), item.version(),
                TodoItem.ActionCode.MARK_READ, null, null, "event-read-701");
        var replay = service.action(ACTOR, item.id(), item.version(),
                TodoItem.ActionCode.MARK_READ, null, null, "event-read-701");

        assertThat(refresh.created()).isOne();
        assertThat(item.category()).isEqualTo(TodoItem.Category.REMINDER);
        assertThat(outcome.code()).isEqualTo(TodoActionLog.ResultCode.SUCCESS);
        assertThat(outcome.item().closeReason())
                .isEqualTo(TodoItem.CloseReason.ACTION_COMPLETED);
        assertThat(replay.replayed()).isTrue();
        assertThat(calls).hasValue(1);
    }

    @Test
    void externallyReadEventReloadsAndClosesAsSourceCompleted() {
        var repository = new InMemoryTodoRepository();
        var source = new FakeEventSource(TodoItem.Category.CC, "702");
        var service = service(repository, source, eventAction());
        service.refresh(ACTOR);
        source.open = false;
        source.reload = TodoSourceReload.of(TodoSourceReload.Status.COMPLETED);

        var refresh = service.refresh(ACTOR);
        var closed = repository.findByIdentity(source.snapshot.identity()).orElseThrow();

        assertThat(refresh.closed()).isOne();
        assertThat(closed.category()).isEqualTo(TodoItem.Category.CC);
        assertThat(closed.closeReason()).isEqualTo(TodoItem.CloseReason.SOURCE_COMPLETED);
    }

    @Test
    void sourceAndActionAdaptersMustBeDiscoveredAsPairs() {
        assertThatThrownBy(() -> new TodoService(
                new InMemoryTodoRepository(), List.of(new FakeEventSource(
                TodoItem.Category.REMINDER, "703")), List.of(),
                Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registered as pairs");
    }

    private static TodoService service(TodoRepository repository,
                                       FakeSource source,
                                       java.util.function.Function<TodoSourceActionCommand,
                                               TodoSourceActionResult> action) {
        TodoActionPort port = new TodoActionPort() {
            @Override public TodoItem.SourceType sourceType() {
                return TodoItem.SourceType.WORK_TASK;
            }
            @Override public TodoSourceActionResult execute(TodoSourceActionCommand command) {
                return action.apply(command);
            }
        };
        return new TodoService(repository, List.of(source), List.of(port),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TodoService service(
            TodoRepository repository,
            TodoSourcePort source,
            TodoActionPort action
    ) {
        return new TodoService(repository, List.of(source), List.of(action),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TodoActionPort eventAction() {
        return new TodoActionPort() {
            @Override public TodoItem.SourceType sourceType() {
                return TodoItem.SourceType.EVENT_MESSAGE;
            }
            @Override public TodoSourceActionResult execute(TodoSourceActionCommand command) {
                return success(command.expectedSourceVersion());
            }
        };
    }

    private static TodoSourceActionResult success(long version) {
        return new TodoSourceActionResult(TodoSourceActionResult.Code.SUCCESS,
                version + 1, "Completed");
    }

    private static final class FakeSource implements TodoSourcePort {
        private final TodoSourceSnapshot snapshot = new TodoSourceSnapshot(
                new TodoIdentity(10, 20, 30, TodoItem.SourceType.WORK_TASK,
                        "100", "task-100"),
                1, TodoItem.Category.TASK, 10, "Complete work",
                NOW.plusSeconds(3600), "/work/tasks/100",
                Set.of(TodoItem.ActionCode.COMPLETE), null);
        private boolean open = true;
        private TodoSourceReload reload = TodoSourceReload.live(snapshot);

        @Override public TodoItem.SourceType sourceType() {
            return TodoItem.SourceType.WORK_TASK;
        }
        @Override public List<TodoSourceSnapshot> loadOpen(TodoActor actor) {
            return open ? List.of(snapshot) : List.of();
        }
        @Override public TodoSourceReload reload(TodoActor actor, TodoSourceReference reference) {
            return reload;
        }
    }

    private static final class FakeEventSource implements TodoSourcePort {
        private final TodoSourceSnapshot snapshot;
        private boolean open = true;
        private TodoSourceReload reload;

        private FakeEventSource(TodoItem.Category category, String sourceId) {
            snapshot = new TodoSourceSnapshot(new TodoIdentity(
                    10, 20, 30, TodoItem.SourceType.EVENT_MESSAGE,
                    sourceId, "MARK_READ"), 1, category, 75,
                    "Event " + sourceId, null,
                    "/systems/10/messages?messageId=" + sourceId,
                    Set.of(TodoItem.ActionCode.MARK_READ), null);
            reload = TodoSourceReload.live(snapshot);
        }

        @Override public TodoItem.SourceType sourceType() {
            return TodoItem.SourceType.EVENT_MESSAGE;
        }
        @Override public List<TodoSourceSnapshot> loadOpen(TodoActor actor) {
            return open ? List.of(snapshot) : List.of();
        }
        @Override public TodoSourceReload reload(TodoActor actor, TodoSourceReference reference) {
            return reload;
        }
    }

    private static final class RacingRepository implements TodoRepository {
        private final TodoRepository delegate;
        private TodoActionLog racingLog;

        private RacingRepository(TodoRepository delegate) { this.delegate = delegate; }
        @Override public long nextItemId() { return delegate.nextItemId(); }
        @Override public long nextActionLogId() { return delegate.nextActionLogId(); }
        @Override public Optional<TodoItem> findByIdentity(TodoIdentity identity) {
            return delegate.findByIdentity(identity);
        }
        @Override public Optional<TodoItem> findById(long systemId, long tenantId,
                                                     long recipientMemberId, long itemId) {
            return delegate.findById(systemId, tenantId, recipientMemberId, itemId);
        }
        @Override public List<TodoItem> findOpen(long systemId, long tenantId,
                                                 long recipientMemberId,
                                                 TodoItem.SourceType sourceType) {
            return delegate.findOpen(systemId, tenantId, recipientMemberId, sourceType);
        }
        @Override public TodoPage findPage(long systemId, long tenantId,
                                           long recipientMemberId, TodoQuery query) {
            return delegate.findPage(systemId, tenantId, recipientMemberId, query);
        }
        @Override public TodoCounts counts(long systemId, long tenantId,
                                           long recipientMemberId, Instant todayStart,
                                           Instant tomorrowStart) {
            return delegate.counts(systemId, tenantId, recipientMemberId,
                    todayStart, tomorrowStart);
        }
        @Override public TodoItem save(TodoItem item) { return delegate.save(item); }
        @Override public TodoActionReservation reserveAction(TodoActionLog proposed) {
            return racingLog == null ? delegate.reserveAction(proposed)
                    : new TodoActionReservation(racingLog, false);
        }
        @Override public TodoActionLog completeAction(TodoActionLog completed) {
            return delegate.completeAction(completed);
        }
        @Override public Optional<TodoActionLog> findAction(long systemId, long tenantId,
                                                            long actorMemberId, String key) {
            return Optional.empty();
        }
    }
}
