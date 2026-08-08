package com.unique.examine.web.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.service.ApprovalWorkflowService;
import com.unique.examine.flow.service.FlowMutationService;
import com.unique.examine.flow.service.FlowRequestServiceFactory;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoItem;
import com.unique.examine.todo.port.TodoSourceActionCommand;
import com.unique.examine.todo.port.TodoSourceActionResult;
import com.unique.examine.todo.port.TodoSourceReference;
import com.unique.examine.todo.port.TodoSourceReload;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTodoAdapterTest {
    private static final Instant NOW =
            Instant.parse("2026-08-01T08:00:00Z");

    @Test
    void preservesRepresentedAuthorityAndNativeIdempotencyOnApproval() {
        var ids = new AtomicLong(100);
        var workflow = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                new IdService() {
                    @Override
                    public long nextId() {
                        return ids.incrementAndGet();
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC));
        var definition = workflow.createDraft("Delegated approval", 11);
        workflow.publish(definition.id());
        var delegation = workflow.createDelegation(
                20, 11, false, 11, 99,
                NOW.minusSeconds(60), NOW.plusSeconds(3_600),
                definition.id());
        var instance = workflow.startLatest(
                definition.id(), "expense-59", 7);
        FlowRequestServiceFactory factory =
                (systemId, tenantId) -> workflow;
        var idempotency = new MemoryIdempotency();
        var mutations = new FlowMutationService(
                factory, idempotency, new ObjectMapper(),
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(
                                memberId, null)));
        var adapter = new FlowTodoAdapter(factory, mutations);
        var actor = todoActor(
                FlowPermissions.INSTANCE_READ,
                FlowPermissions.INSTANCE_DECIDE);

        var snapshot = adapter.loadOpen(actor).getFirst();
        assertThat(snapshot.identity().sourceId())
                .isEqualTo(Long.toString(instance.id()));
        assertThat(snapshot.identity().actionScope()).isEqualTo("DECIDE:11");
        assertThat(snapshot.representedMemberId()).isEqualTo(11);
        assertThat(adapter.reload(
                todoActor(FlowPermissions.INSTANCE_READ),
                reference(snapshot)).status())
                .isEqualTo(TodoSourceReload.Status.DENIED);

        var result = adapter.execute(new TodoSourceActionCommand(
                10, 20, 99,
                Set.of(
                        FlowPermissions.INSTANCE_READ,
                        FlowPermissions.INSTANCE_DECIDE),
                TodoItem.SourceType.FLOW_APPROVAL,
                Long.toString(instance.id()), snapshot.sourceVersion(),
                snapshot.identity().actionScope(),
                TodoItem.ActionCode.APPROVE,
                "approved through Todo", null, 11L,
                "flow-approve-1", "request-1", "trace-1"));

        assertThat(result.code())
                .isEqualTo(TodoSourceActionResult.Code.SUCCESS);
        assertThat(workflow.instance(instance.id()).status())
                .isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(workflow.history(instance.id()).getLast())
                .extracting(
                        ApprovalHistoryEvent::actorId,
                        ApprovalHistoryEvent::representedMemberId,
                        ApprovalHistoryEvent::delegationRuleId)
                .containsExactly(99L, 11L, delegation.id());
        assertThat(idempotency.completions).hasValue(1);
        assertThat(idempotency.key).isEqualTo("flow-approve-1");
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

    private static TodoActor todoActor(String... permissions) {
        return new TodoActor(
                10, 20, 99, Set.of(permissions),
                "request-1", "trace-1");
    }

    private static final class MemoryIdempotency
            implements IdempotencyFacade {
        private IdempotencyRecord record;
        private final AtomicInteger completions = new AtomicInteger();
        private String key;

        @Override
        public Optional<IdempotencyRecord> find(
                String scopeType,
                String scopeKey,
                String key
        ) {
            return Optional.ofNullable(record);
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                Duration ttl
        ) {
            this.key = key;
            record = new IdempotencyRecord(
                    81, requestHash, "PROCESSING", null);
            return 81;
        }

        @Override
        public void complete(
                long id,
                int httpStatus,
                String responseCode,
                String responseBody
        ) {
            completions.incrementAndGet();
            record = new IdempotencyRecord(
                    id, record.requestHash(), "COMPLETED", responseBody);
        }
    }
}
