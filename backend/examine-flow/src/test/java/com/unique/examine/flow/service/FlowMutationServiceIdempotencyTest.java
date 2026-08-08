package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowMutationServiceIdempotencyTest {
    @Test
    void manualStartFreezesLatestVersionReplaysExactlyAndAuditsOnce() throws Exception {
        var sequence = new AtomicLong(200);
        var workflow = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                },
                Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC));
        var definition = workflow.createDraft("Approval v1", 20);
        workflow.publish(definition.id());
        FlowRequestServiceFactory factory = (systemId, tenantId) -> workflow;
        var idempotency = new MemoryIdempotency();
        var audit = new MemoryOperationAudit();
        var mutations = new FlowMutationService(
                factory,
                idempotency,
                new ObjectMapper(),
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
                ),
                noRecordFlows(),
                audit
        );
        var session = new FlowSession(
                99, 1, 2, 10, Set.of("flow.instance.start"));
        var request = new FlowRequests.StartInstance(null, "expense-start-001");

        var first = mutations.start(
                session, definition.id(), request, "start-key", "request-1", "trace-1");
        workflow.reviseDraft(definition.id(), "Approval v2", 20);
        workflow.publish(definition.id());
        var replay = mutations.start(
                session, definition.id(), request, "start-key", "request-2", "trace-2");
        var conflict = catchThrowableOfType(
                () -> mutations.start(
                        session,
                        definition.id(),
                        new FlowRequests.StartInstance(null, "expense-start-changed"),
                        "start-key",
                        "request-3",
                        "trace-3"),
                BusinessException.class
        );
        idempotency.record = new IdempotencyRecord(
                idempotency.record.id(),
                idempotency.record.requestHash(),
                "PROCESSING",
                null
        );
        var inProgress = catchThrowableOfType(
                () -> mutations.start(
                        session, definition.id(), request, "start-key", "request-4", "trace-4"),
                BusinessException.class
        );

        assertThat(first.definitionVersion()).isEqualTo(1);
        assertThat(replay).isEqualTo(first);
        assertThat(workflow.startableDefinitions(1, 20).items().getFirst().version()).isEqualTo(2);
        assertThat(workflow.instances(1, 20).items()).hasSize(1);
        assertThat(workflow.history(Long.parseLong(first.instanceId()))).hasSize(1);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(inProgress.code()).isEqualTo("REQUEST_IN_PROGRESS");
        assertThat(idempotency.scopeType).isEqualTo("FLOW_INSTANCE");
        assertThat(idempotency.scopeKey)
                .isEqualTo("1:2:10:" + definition.id() + ":start");
        assertThat(idempotency.httpStatus).isEqualTo(201);
        assertThat(idempotency.responseCode).isEqualTo("OK");
        assertThat(idempotency.completions).hasValue(1);
        assertThat(audit.successes).singleElement().satisfies(operation -> {
            assertThat(operation.action()).isEqualTo("FLOW_INSTANCE_STARTED");
            assertThat(operation.actor().accountId()).isEqualTo(99L);
            assertThat(operation.actor().sourceType()).isEqualTo("WEB");
            assertThat(operation.context().systemId()).isEqualTo(1L);
            assertThat(operation.context().tenantId()).isEqualTo(2L);
            assertThat(operation.aggregate().type()).isEqualTo("FLOW_INSTANCE");
            assertThat(operation.aggregate().id()).isEqualTo(first.instanceId());
            assertThat(operation.requestId()).isEqualTo("request-1");
            assertThat(operation.traceId()).isEqualTo("trace-1");
            assertThat(operation.before()).isNull();
            assertThat(operation.after()).isNotNull();
        });
        assertThat(FlowMutationService.class.getMethod(
                        "start",
                        FlowSession.class,
                        long.class,
                        FlowRequests.StartInstance.class,
                        String.class,
                        String.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void sameKeySameBodyReplaysWithoutHistoryAndDifferentBodyConflicts() throws Exception {
        var sequence = new AtomicLong(100);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var workflow = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                ids,
                Clock.fixed(Instant.parse("2026-07-27T08:00:00Z"), ZoneOffset.UTC));
        var definition = workflow.createDraft("Approval", 20);
        workflow.publish(definition.id());
        var pending = workflow.startLatest(definition.id(), "expense-001", 10);
        FlowRequestServiceFactory factory = (systemId, tenantId) -> workflow;
        var idempotency = new MemoryIdempotency();
        var mutations = new FlowMutationService(
                factory,
                idempotency,
                new ObjectMapper(),
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
                )
        );
        var session = new FlowSession(1, 2, 10, Set.of("flow.instance.withdraw"));

        var first = mutations.withdraw(
                session, pending.id(), new FlowRequests.Withdrawal("wrong amount"), "withdraw-key");
        var replay = mutations.withdraw(
                session, pending.id(), new FlowRequests.Withdrawal("wrong amount"), "withdraw-key");
        var conflict = catchThrowableOfType(
                () -> mutations.withdraw(
                        session,
                        pending.id(),
                        new FlowRequests.Withdrawal("different reason"),
                        "withdraw-key"),
                BusinessException.class);

        assertThat(first.status()).isEqualTo("WITHDRAWN");
        assertThat(replay).isEqualTo(first);
        assertThat(workflow.instance(pending.id()).status()).isEqualTo(ApprovalInstance.Status.WITHDRAWN);
        assertThat(workflow.history(pending.id())).hasSize(2);
        assertThat(idempotency.completions).hasValue(1);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(FlowMutationService.class.getMethod(
                        "withdraw",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Withdrawal.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private IdempotencyRecord record;
        private final AtomicInteger completions = new AtomicInteger();
        private String scopeType;
        private String scopeKey;
        private int httpStatus;
        private String responseCode;

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(record);
        }

        @Override
        public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) {
            this.scopeType = scopeType;
            this.scopeKey = scopeKey;
            record = new IdempotencyRecord(81, requestHash, "PROCESSING", null);
            return 81;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            this.httpStatus = httpStatus;
            this.responseCode = responseCode;
            completions.incrementAndGet();
            record = new IdempotencyRecord(id, record.requestHash(), "COMPLETED", responseBody);
        }
    }

    private static RuntimeRecordFlowFacade noRecordFlows() {
        return new RuntimeRecordFlowFacade() {
            @Override
            public RecordFlowState bind(BindRequest request) {
                throw new AssertionError("Unbound manual start must not bind a record");
            }

            @Override
            public RecordFlowState bindAdditional(AdditionalBindRequest request) {
                throw new AssertionError("Unbound manual start must not bind an additional record");
            }

            @Override
            public RecordFlowState transition(TransitionRequest request) {
                throw new AssertionError("Manual start must not project a terminal status");
            }
        };
    }

    private static final class MemoryOperationAudit implements OperationAuditFacade {
        private final ArrayList<OperationAudit> successes = new ArrayList<>();

        @Override
        public void recordSuccess(OperationAudit audit) {
            successes.add(audit);
        }

        @Override
        public void recordDenied(OperationAudit audit) {
            throw new AssertionError("Manual start service must not write denied audit");
        }

        @Override
        public void recordFailed(OperationAudit audit) {
            throw new AssertionError("Manual start service must not write failed audit");
        }
    }
}
