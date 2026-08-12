package com.unique.examine.web.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.WorkTaskCreationFacade;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.port.TodoSourceActionCommand;
import com.unique.examine.todo.port.TodoSourceActionResult;
import com.unique.examine.todo.port.TodoSourceReference;
import com.unique.examine.todo.port.TodoSourceReload;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.adapter.WorkTaskCreationAdapter;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.service.WorkTaskService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class WorkTodoAdapterTest {
    @Test
    void reloadsAuthorizationAndDelegatesCompletionToTheNativeService() {
        var repository = new InMemoryWorkTaskRepository();
        var service = new WorkTaskService(
                repository,
                (systemId, tenantId, memberId) -> true,
                Clock.fixed(
                        Instant.parse("2026-08-01T08:00:00Z"),
                        ZoneOffset.UTC));
        var creation = new WorkTaskCreationAdapter(
                service, new MemoryIdempotency(), new NoopAudits(),
                event -> 1, new ObjectMapper().findAndRegisterModules());
        var created = creation.create(new WorkTaskCreationFacade.Command(
                900, 10, 20, 100, Set.of(WorkTaskService.CREATE),
                101, "Complete through Todo", "Created by a Flow task node",
                null, null, new AggregateRef("FLOW_INSTANCE", "800"),
                "flow-node:800:v2:task:work-task", "request-1", "trace-1"));
        var adapter = new WorkTodoAdapter(service);
        var actor = todoActor(101, WorkTaskService.ACCESS);

        var snapshot = adapter.loadOpen(actor).getFirst();
        assertThat(snapshot.identity().sourceId())
                .isEqualTo(Long.toString(created.taskId()));
        assertThat(snapshot.identity().actionScope()).isEqualTo("COMPLETE");
        assertThat(adapter.reload(
                todoActor(101), reference(snapshot)).status())
                .isEqualTo(TodoSourceReload.Status.DENIED);

        var result = adapter.execute(new TodoSourceActionCommand(
                10, 20, 101, Set.of(WorkTaskService.ACCESS),
                TodoItem.SourceType.WORK_TASK,
                Long.toString(created.taskId()), created.version(),
                "COMPLETE", TodoItem.ActionCode.COMPLETE,
                null, null, null, "work-complete-1",
                "request-1", "trace-1"));

        assertThat(result.code())
                .isEqualTo(TodoSourceActionResult.Code.SUCCESS);
        assertThat(service.get(
                new WorkActor(10, 20, 101, Set.of()), created.taskId()).status())
                .isEqualTo(WorkTask.Status.COMPLETED);
        assertThat(adapter.reload(actor, reference(snapshot)).status())
                .isEqualTo(TodoSourceReload.Status.COMPLETED);
    }

    private static final class NoopAudits implements OperationAuditFacade {
        @Override
        public void recordSuccess(OperationAudit audit) {
        }

        @Override
        public void recordDenied(OperationAudit audit) {
        }

        @Override
        public void recordFailed(OperationAudit audit) {
        }
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();
        private final Map<Long, String> keys = new HashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(scopeType + "|" + scopeKey + "|" + key));
        }

        @Override
        public long begin(
                String scopeType, String scopeKey, String key,
                String requestHash, Duration ttl) {
            var id = ids.incrementAndGet();
            var compound = scopeType + "|" + scopeKey + "|" + key;
            records.put(compound,
                    new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            keys.put(id, compound);
            return id;
        }

        @Override
        public void complete(
                long id, int httpStatus, String responseCode, String responseBody) {
            var key = keys.get(id);
            var current = records.get(key);
            records.put(key, new IdempotencyRecord(
                    id, current.requestHash(), "COMPLETED", responseBody));
        }
    }

    private static TodoSourceReference reference(
            com.unique.examine.todo.domain.TodoSourceSnapshot snapshot
    ) {
        return new TodoSourceReference(
                snapshot.identity().sourceType(),
                snapshot.identity().sourceId(), snapshot.sourceVersion(),
                snapshot.identity().actionScope(),
                snapshot.representedMemberId());
    }

    private static TodoActor todoActor(long memberId, String... permissions) {
        return new TodoActor(
                10, 20, memberId, Set.of(permissions),
                "request-1", "trace-1");
    }
}
