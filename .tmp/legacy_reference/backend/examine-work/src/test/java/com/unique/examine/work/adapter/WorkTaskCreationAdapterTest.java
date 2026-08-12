package com.unique.examine.work.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.api.WorkTaskCreationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.service.WorkTaskService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class WorkTaskCreationAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");

    @Test
    void createsOneOwnerTaskWithPermissionMembershipIdempotencyAuditAndEvent() {
        var repository = new InMemoryWorkTaskRepository();
        var service = new WorkTaskService(
                repository,
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20 && Set.of(100L, 101L).contains(memberId),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var idempotency = new MemoryIdempotency();
        var audits = new RecordingAudits();
        var outbox = new RecordingOutbox();
        var adapter = new WorkTaskCreationAdapter(
                service, idempotency, audits, outbox,
                new ObjectMapper().findAndRegisterModules());
        var command = command("Review Flow outcome", 101, "flow-task-1",
                Set.of(WorkTaskService.CREATE));

        var first = adapter.create(command);
        var replay = adapter.create(command);

        assertThat(first.replay()).isFalse();
        assertThat(replay.replay()).isTrue();
        assertThat(replay.taskId()).isEqualTo(first.taskId());
        assertThat(repository.findAll(10, 20)).singleElement()
                .satisfies(task -> {
                    assertThat(task.id()).isEqualTo(first.taskId());
                    assertThat(task.assigneeMemberId()).isEqualTo(101);
                    assertThat(task.status().name()).isEqualTo("OPEN");
                });
        assertThat(outbox.events).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("WORK_TASK_CREATED");
            assertThat(event.aggregate().type()).isEqualTo("WORK_TASK");
            assertThat(event.aggregate().id()).isEqualTo(Long.toString(first.taskId()));
        });
        assertThat(audits.successes).hasSize(2);
        assertThat(audits.failures).isEmpty();

        var changed = catchThrowableOfType(BusinessException.class,
                () -> adapter.create(command("Changed title", 101,
                        "flow-task-1", Set.of(WorkTaskService.CREATE))));
        assertThat(changed.code()).isEqualTo("WORK_TASK_IDEMPOTENCY_CONFLICT");
        var denied = catchThrowableOfType(BusinessException.class,
                () -> adapter.create(command("Denied", 101,
                        "flow-task-2", Set.of())));
        assertThat(denied.code()).isEqualTo("WORK_TASK_FORBIDDEN");
        var inactive = catchThrowableOfType(BusinessException.class,
                () -> adapter.create(command("Inactive", 999,
                        "flow-task-3", Set.of(WorkTaskService.CREATE))));
        assertThat(inactive.code()).isEqualTo("WORK_ASSIGNEE_INVALID");
        assertThat(repository.findAll(10, 20)).hasSize(1);
        assertThat(outbox.events).hasSize(1);
        assertThat(audits.failures).hasSize(3);

        assertThat(adapter.state(state(first.taskId())).status()).isEqualTo("OPEN");
        service.complete(new WorkActor(10, 20, 101, Set.of()), first.taskId());
        assertThat(adapter.state(state(first.taskId())).status())
                .isEqualTo("COMPLETED");
    }

    private static WorkTaskCreationFacade.Command command(
            String title, long assignee, String key, Set<String> permissions) {
        return new WorkTaskCreationFacade.Command(
                900, 10, 20, 100, permissions, assignee,
                title, "Flow-created task", null, null,
                new AggregateRef("FLOW_INSTANCE", "800"), key,
                "request-1", "trace-1");
    }

    private static WorkTaskCreationFacade.StateQuery state(long taskId) {
        return new WorkTaskCreationFacade.StateQuery(
                900, 10, 20, 100, Set.of(WorkTaskService.CREATE),
                taskId, "request-2", "trace-2");
    }

    private static final class RecordingAudits implements OperationAuditFacade {
        private final List<OperationAudit> successes = new ArrayList<>();
        private final List<OperationAudit> failures = new ArrayList<>();

        @Override
        public void recordSuccess(OperationAudit audit) {
            successes.add(audit);
        }

        @Override
        public void recordDenied(OperationAudit audit) {
            failures.add(audit);
        }

        @Override
        public void recordFailed(OperationAudit audit) {
            failures.add(audit);
        }
    }

    private static final class RecordingOutbox implements OutboxFacade {
        private final List<OutboxEvent> events = new ArrayList<>();

        @Override
        public long enqueue(OutboxEvent event) {
            events.add(event);
            return events.size();
        }
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final Map<String, IdempotencyRecord> records = new HashMap<>();
        private final Map<Long, String> keys = new HashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(compound(scopeType, scopeKey, key)));
        }

        @Override
        public long begin(
                String scopeType, String scopeKey, String key,
                String requestHash, Duration ttl) {
            var id = ids.incrementAndGet();
            var compound = compound(scopeType, scopeKey, key);
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

        private static String compound(String scopeType, String scopeKey, String key) {
            return scopeType + "|" + scopeKey + "|" + key;
        }
    }
}
