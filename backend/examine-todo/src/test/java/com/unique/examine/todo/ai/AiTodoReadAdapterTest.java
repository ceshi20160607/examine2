package com.unique.examine.todo.ai;

import com.unique.examine.core.ai.AiTodoReadFacade;
import com.unique.examine.todo.adapter.memory.InMemoryTodoRepository;
import com.unique.examine.todo.domain.TodoActionLog;
import com.unique.examine.todo.domain.TodoActionReservation;
import com.unique.examine.todo.domain.TodoCounts;
import com.unique.examine.todo.domain.TodoIdentity;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.domain.TodoPage;
import com.unique.examine.todo.domain.TodoQuery;
import com.unique.examine.todo.domain.TodoSourceSnapshot;
import com.unique.examine.todo.port.TodoRepository;
import com.unique.examine.todo.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiTodoReadAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    private InMemoryTodoRepository repository;
    private WriteGuardRepository guarded;
    private AiTodoReadAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTodoRepository();
        guarded = new WriteGuardRepository(repository);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var service = new TodoService(
                guarded, List.of(), List.of(), clock);
        adapter = new AiTodoReadAdapter(service, clock);

        repository.save(task(
                10, 20, 30, "current-today", 10,
                NOW.plusSeconds(3_600)));
        repository.save(task(
                10, 20, 30, "current-overdue", 20,
                NOW.minusSeconds(86_400)));
        repository.save(approval(
                10, 20, 30, "current-approval", 30,
                NOW.plusSeconds(7_200)));
        repository.save(task(
                10, 21, 30, "other-tenant", 1,
                NOW.plusSeconds(3_600)));
        repository.save(task(
                10, 20, 31, "other-member", 1,
                NOW.plusSeconds(3_600)));
        guarded.rejectWrites = true;
    }

    @Test
    void queryUsesPageOneExactFiltersAndProjectsTypedCurrentMemberFacts()
            throws Exception {
        var result = adapter.query(new AiTodoReadFacade.Request(
                10, 20, 30,
                Set.of("todo.access"),
                AiTodoReadFacade.Category.TASK,
                AiTodoReadFacade.State.OPEN,
                AiTodoReadFacade.Time.TODAY,
                1));

        assertThat(result.counts()).isEqualTo(
                new AiTodoReadFacade.Counts(3, 2, 1, 2, 1));
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceId()).isEqualTo("current-today");
            assertThat(item.category()).isEqualTo(
                    AiTodoReadFacade.Category.TASK);
            assertThat(item.sourceType()).isEqualTo(
                    AiTodoReadFacade.SourceType.WORK_TASK);
            assertThat(item.title()).isEqualTo("Task current-today");
            assertThat(item.priority()).isEqualTo(10);
            assertThat(item.dueAt()).isEqualTo(NOW.plusSeconds(3_600));
            assertThat(item.routeHint()).isEqualTo(
                    "/systems/10/work/tasks/current-today");
            assertThat(item.actions()).containsExactly(
                    AiTodoReadFacade.Action.COMPLETE);
            assertThat(item.state()).isEqualTo(AiTodoReadFacade.State.OPEN);
            assertThat(item.version()).isOne();
        });
        assertThat(AiTodoReadAdapter.class.getMethod(
                        "query", AiTodoReadFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void queryIsTenantMemberScopedBoundedAndDoesNotWrite() {
        var before = repository.findPage(
                10, 20, 30,
                new com.unique.examine.todo.domain.TodoQuery(
                        com.unique.examine.todo.domain.TodoQuery.CategoryFilter.ALL,
                        com.unique.examine.todo.domain.TodoQuery.StateFilter.ALL,
                        com.unique.examine.todo.domain.TodoQuery.TimeFilter.ALL,
                        1, 100, null, null));

        var current = adapter.query(request(10, 20, 30, 2));
        var otherTenant = adapter.query(request(10, 21, 30, 20));
        var otherMember = adapter.query(request(10, 20, 31, 20));

        assertThat(current.items()).hasSize(2);
        assertThat(current.items()).extracting(AiTodoReadFacade.Item::sourceId)
                .doesNotContain("other-tenant", "other-member");
        assertThat(otherTenant.items()).extracting(AiTodoReadFacade.Item::sourceId)
                .containsExactly("other-tenant");
        assertThat(otherMember.items()).extracting(AiTodoReadFacade.Item::sourceId)
                .containsExactly("other-member");
        assertThat(repository.findPage(
                10, 20, 30,
                new com.unique.examine.todo.domain.TodoQuery(
                        com.unique.examine.todo.domain.TodoQuery.CategoryFilter.ALL,
                        com.unique.examine.todo.domain.TodoQuery.StateFilter.ALL,
                        com.unique.examine.todo.domain.TodoQuery.TimeFilter.ALL,
                        1, 100, null, null))).isEqualTo(before);
        assertThat(guarded.writeAttempts).isZero();
    }

    private static AiTodoReadFacade.Request request(
            long systemId, long tenantId, long memberId, int limit) {
        return new AiTodoReadFacade.Request(
                systemId, tenantId, memberId,
                Set.of(),
                AiTodoReadFacade.Category.ALL,
                AiTodoReadFacade.State.ALL,
                AiTodoReadFacade.Time.ALL,
                limit);
    }

    private TodoItem task(
            long systemId, long tenantId, long memberId,
            String sourceId, int priority, Instant dueAt) {
        var identity = new TodoIdentity(
                systemId, tenantId, memberId,
                TodoItem.SourceType.WORK_TASK, sourceId, "COMPLETE");
        return new TodoSourceSnapshot(
                identity, 1, TodoItem.Category.TASK, priority,
                "Task " + sourceId, dueAt,
                "/systems/" + systemId + "/work/tasks/" + sourceId,
                Set.of(TodoItem.ActionCode.COMPLETE), null)
                .create(repository.nextItemId(), NOW.minusSeconds(100));
    }

    private TodoItem approval(
            long systemId, long tenantId, long memberId,
            String sourceId, int priority, Instant dueAt) {
        var identity = new TodoIdentity(
                systemId, tenantId, memberId,
                TodoItem.SourceType.FLOW_APPROVAL, sourceId, "DECIDE:40");
        return new TodoSourceSnapshot(
                identity, 2, TodoItem.Category.APPROVAL, priority,
                "Approval " + sourceId, dueAt,
                "/systems/" + systemId + "/flows?instanceId=" + sourceId,
                Set.of(
                        TodoItem.ActionCode.APPROVE,
                        TodoItem.ActionCode.REJECT),
                40L).create(repository.nextItemId(), NOW.minusSeconds(100));
    }

    private static final class WriteGuardRepository implements TodoRepository {
        private final TodoRepository delegate;
        private boolean rejectWrites;
        private int writeAttempts;

        private WriteGuardRepository(TodoRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public long nextItemId() {
            write();
            return delegate.nextItemId();
        }

        @Override
        public long nextActionLogId() {
            write();
            return delegate.nextActionLogId();
        }

        @Override
        public Optional<TodoItem> findByIdentity(TodoIdentity identity) {
            return delegate.findByIdentity(identity);
        }

        @Override
        public Optional<TodoItem> findById(
                long systemId, long tenantId, long recipientMemberId,
                long itemId) {
            return delegate.findById(
                    systemId, tenantId, recipientMemberId, itemId);
        }

        @Override
        public List<TodoItem> findOpen(
                long systemId, long tenantId, long recipientMemberId,
                TodoItem.SourceType sourceType) {
            return delegate.findOpen(
                    systemId, tenantId, recipientMemberId, sourceType);
        }

        @Override
        public TodoPage findPage(
                long systemId, long tenantId, long recipientMemberId,
                TodoQuery query) {
            return delegate.findPage(
                    systemId, tenantId, recipientMemberId, query);
        }

        @Override
        public TodoCounts counts(
                long systemId, long tenantId, long recipientMemberId,
                Instant todayStart, Instant tomorrowStart) {
            return delegate.counts(
                    systemId, tenantId, recipientMemberId,
                    todayStart, tomorrowStart);
        }

        @Override
        public TodoItem save(TodoItem item) {
            write();
            return delegate.save(item);
        }

        @Override
        public TodoActionReservation reserveAction(TodoActionLog proposed) {
            write();
            return delegate.reserveAction(proposed);
        }

        @Override
        public TodoActionLog completeAction(TodoActionLog completed) {
            write();
            return delegate.completeAction(completed);
        }

        @Override
        public Optional<TodoActionLog> findAction(
                long systemId, long tenantId, long actorMemberId,
                String callerIdempotencyKey) {
            return delegate.findAction(
                    systemId, tenantId, actorMemberId, callerIdempotencyKey);
        }

        private void write() {
            if (!rejectWrites) {
                return;
            }
            writeAttempts++;
            throw new AssertionError("AI Todo read attempted a write");
        }
    }
}
