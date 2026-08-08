package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowHttpErrors;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowTerminationIdempotencyTest {
    @Test
    void sameKeySameBodyReplaysWithoutHistoryAndDifferentBodyConflicts() throws Exception {
        var workflow = workflow();
        var pending = start(workflow);
        var idempotency = new MemoryIdempotency();
        var mutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                idempotency,
                new ObjectMapper(),
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
                ));
        var session = new FlowSession(1, 2, 99, Set.of("flow.instance.terminate"));

        var first = mutations.terminate(
                session, pending.id(), new FlowRequests.Termination("duplicate"), "terminate-key");
        var replay = mutations.terminate(
                session, pending.id(), new FlowRequests.Termination("duplicate"), "terminate-key");
        var conflict = catchThrowableOfType(
                () -> mutations.terminate(
                        session,
                        pending.id(),
                        new FlowRequests.Termination("another reason"),
                        "terminate-key"),
                BusinessException.class);

        assertThat(first.status()).isEqualTo("TERMINATED");
        assertThat(replay).isEqualTo(first);
        assertThat(workflow.instance(pending.id()).status()).isEqualTo(ApprovalInstance.Status.TERMINATED);
        assertThat(workflow.history(pending.id())).hasSize(2);
        assertThat(idempotency.completions).hasValue(1);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(FlowMutationService.class.getMethod(
                        "terminate",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Termination.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void newKeyAgainstTerminatedInstanceReturnsStateConflict() {
        var workflow = workflow();
        var pending = start(workflow);
        var mutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                new MemoryIdempotency(),
                new ObjectMapper(),
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
                ));
        var session = new FlowSession(1, 2, 99, Set.of("flow.instance.terminate"));
        mutations.terminate(
                session, pending.id(), new FlowRequests.Termination("duplicate"), "terminate-key-1");

        var conflict = catchThrowableOfType(
                () -> FlowHttpErrors.execute(() -> mutations.terminate(
                        session,
                        pending.id(),
                        new FlowRequests.Termination("duplicate"),
                        "terminate-key-2")),
                BusinessException.class);

        assertThat(conflict.code()).isEqualTo("FLOW_INSTANCE_STATE_INVALID");
        assertThat(workflow.history(pending.id())).hasSize(2);
    }

    private static ApprovalWorkflowService workflow() {
        var sequence = new AtomicLong(100);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        return new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                ids,
                Clock.fixed(Instant.parse("2026-07-27T09:00:00Z"), ZoneOffset.UTC));
    }

    private static ApprovalInstance start(ApprovalWorkflowService workflow) {
        var definition = workflow.createDraft("Approval", 20);
        workflow.publish(definition.id());
        return workflow.startLatest(definition.id(), "expense-001", 10);
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final Map<Key, IdempotencyRecord> records = new HashMap<>();
        private final Map<Long, Key> keysById = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong(80);
        private final AtomicInteger completions = new AtomicInteger();

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(new Key(scopeType, scopeKey, key)));
        }

        @Override
        public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) {
            var id = sequence.incrementAndGet();
            var compoundKey = new Key(scopeType, scopeKey, key);
            records.put(compoundKey, new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            keysById.put(id, compoundKey);
            return id;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            completions.incrementAndGet();
            var key = keysById.get(id);
            var record = records.get(key);
            records.put(key, new IdempotencyRecord(id, record.requestHash(), "COMPLETED", responseBody));
        }
    }

    private record Key(String scopeType, String scopeKey, String key) {
    }
}
