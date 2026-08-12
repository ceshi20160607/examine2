package com.unique.examine.todo.adapter.memory;

import com.unique.examine.todo.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTodoRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void stableIdentityCasAndRecipientTenantIsolationAreEnforced() {
        var repository = new InMemoryTodoRepository();
        var item = task(10, 20, 30, "1", 5, NOW.plusSeconds(10));
        repository.save(item);

        assertThat(repository.findById(10, 20, 30, item.id())).contains(item);
        assertThat(repository.findById(10, 21, 30, item.id())).isEmpty();
        assertThat(repository.findById(10, 20, 31, item.id())).isEmpty();
        var duplicateIdentity = source(item.identity(), 1, 5,
                NOW.plusSeconds(10)).create(item.id() + 100, NOW);
        assertThatThrownBy(() -> repository.save(duplicateIdentity))
                .isInstanceOf(TodoException.class);
        var closed = item.close(TodoItem.CloseReason.SOURCE_COMPLETED, NOW.plusSeconds(1));
        repository.save(closed);
        assertThatThrownBy(() -> repository.save(item.reconcile(
                source(item.identity(), 2, 4, NOW.plusSeconds(20)), NOW.plusSeconds(2))))
                .isInstanceOf(TodoException.class);
    }

    @Test
    void pageAndCountsUseFrozenStableOrderingAndTimeFilters() {
        var repository = new InMemoryTodoRepository();
        repository.save(task(10, 20, 30, "late", 5, NOW.plusSeconds(200)));
        repository.save(task(10, 20, 30, "first", 1, NOW.plusSeconds(100)));
        repository.save(task(10, 20, 30, "overdue", 1, NOW.minusSeconds(1)));

        var page = repository.findPage(10, 20, 30,
                new TodoQuery(TodoQuery.CategoryFilter.ALL, TodoQuery.StateFilter.ALL,
                        TodoQuery.TimeFilter.ALL, 1, 10, null, null));
        var counts = repository.counts(10, 20, 30, NOW, NOW.plusSeconds(300));

        assertThat(page.items()).extracting(item -> item.identity().sourceId())
                .containsExactly("overdue", "first", "late");
        assertThat(counts.open()).isEqualTo(3);
        assertThat(counts.today()).isEqualTo(2);
        assertThat(counts.overdue()).isEqualTo(1);
    }

    @Test
    void actionReservationIsIdempotentAndCompletionUsesCas() {
        var repository = new InMemoryTodoRepository();
        var log = log(repository.nextActionLogId());

        assertThat(repository.reserveAction(log).acquired()).isTrue();
        assertThat(repository.reserveAction(log).acquired()).isFalse();
        var completed = repository.completeAction(log.complete(
                TodoActionLog.ResultCode.SUCCESS, "Completed", NOW.plusSeconds(1)));

        assertThat(repository.findAction(10, 20, 30, "key-1")).contains(completed);
        assertThatThrownBy(() -> repository.completeAction(completed))
                .isInstanceOf(TodoException.class);
    }

    @Test
    void eventCategoriesAreFilterableWithoutChangingLegacyCounts() {
        var repository = new InMemoryTodoRepository();
        repository.save(event(10, 20, 30, "701", TodoItem.Category.REMINDER));
        repository.save(event(10, 20, 30, "702", TodoItem.Category.CC));

        var reminders = repository.findPage(10, 20, 30,
                new TodoQuery(TodoQuery.CategoryFilter.REMINDER,
                        TodoQuery.StateFilter.OPEN, TodoQuery.TimeFilter.ALL,
                        1, 10, null, null));
        var cc = repository.findPage(10, 20, 30,
                new TodoQuery(TodoQuery.CategoryFilter.CC,
                        TodoQuery.StateFilter.OPEN, TodoQuery.TimeFilter.ALL,
                        1, 10, null, null));
        var counts = repository.counts(10, 20, 30, NOW, NOW.plusSeconds(300));

        assertThat(reminders.items()).extracting(TodoItem::category)
                .containsExactly(TodoItem.Category.REMINDER);
        assertThat(cc.items()).extracting(TodoItem::category)
                .containsExactly(TodoItem.Category.CC);
        assertThat(counts.open()).isEqualTo(2);
        assertThat(counts.task()).isZero();
        assertThat(counts.approval()).isZero();
    }

    private static TodoItem task(long system, long tenant, long recipient,
                                 String sourceId, int priority, Instant dueAt) {
        var repositoryId = Math.abs(sourceId.hashCode()) + 1L;
        var identity = new TodoIdentity(system, tenant, recipient,
                TodoItem.SourceType.WORK_TASK, sourceId, "scope-" + sourceId);
        return source(identity, 1, priority, dueAt).create(repositoryId, NOW);
    }

    private static TodoSourceSnapshot source(TodoIdentity identity, long version,
                                             int priority, Instant dueAt) {
        return new TodoSourceSnapshot(identity, version, TodoItem.Category.TASK,
                priority, "Task " + identity.sourceId(), dueAt,
                "/work/tasks/" + identity.sourceId(),
                Set.of(TodoItem.ActionCode.COMPLETE), null);
    }

    private static TodoActionLog log(long id) {
        return new TodoActionLog(id, 10, 20, 100, 30, 30, "key-1",
                TodoItem.SourceType.WORK_TASK, "1", 1,
                TodoItem.ActionCode.COMPLETE, TodoActionLog.Status.PROCESSING,
                null, null, "request-1", "trace-1", NOW, null, 1);
    }

    private static TodoItem event(
            long system, long tenant, long recipient,
            String sourceId, TodoItem.Category category) {
        return new TodoSourceSnapshot(new TodoIdentity(
                system, tenant, recipient, TodoItem.SourceType.EVENT_MESSAGE,
                sourceId, "MARK_READ"), 1, category, 75,
                "Message " + sourceId, null, "/messages/" + sourceId,
                Set.of(TodoItem.ActionCode.MARK_READ), null)
                .create(Long.parseLong(sourceId), NOW);
    }
}
