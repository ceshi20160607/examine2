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
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowOpenApiStartTest {
    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");

    @Test
    void exactReplayIsApplicationIsolatedAndChangedPayloadConflicts() throws Exception {
        var fixture = fixture();
        var request = new FlowRequests.StartInstance(
                null,
                "external-expense-001",
                new FlowRequests.RecordBinding("purchase_order", "901"));

        var first = fixture.mutations.startOpenApi(
                fixture.session,
                71L,
                fixture.definitionId,
                request,
                "external-start-key",
                "request-1",
                "trace-1");
        var replay = fixture.mutations.startOpenApi(
                fixture.session,
                71L,
                fixture.definitionId,
                request,
                "external-start-key",
                "request-2",
                "trace-2");
        var otherApplication = fixture.mutations.startOpenApi(
                fixture.session,
                72L,
                fixture.definitionId,
                request,
                "external-start-key",
                "request-3",
                "trace-3");
        var conflict = catchThrowableOfType(
                () -> fixture.mutations.startOpenApi(
                        fixture.session,
                        71L,
                        fixture.definitionId,
                        new FlowRequests.StartInstance(
                                null,
                                "external-expense-changed",
                                request.recordBinding()),
                        "external-start-key",
                        "request-4",
                        "trace-4"),
                BusinessException.class);

        assertThat(replay).isEqualTo(first);
        assertThat(otherApplication.instanceId()).isNotEqualTo(first.instanceId());
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(conflict.status().value()).isEqualTo(409);
        assertThat(fixture.workflow.instances(1, 20).items()).hasSize(2);
        assertThat(fixture.workflow.history(Long.parseLong(first.instanceId()))).hasSize(1);
        assertThat(fixture.workflow.history(Long.parseLong(otherApplication.instanceId()))).hasSize(1);
        assertThat(fixture.idempotency.scopeKeys()).containsExactlyInAnyOrder(
                "1:2:10:71:" + fixture.definitionId + ":openapi-start",
                "1:2:10:72:" + fixture.definitionId + ":openapi-start");
        assertThat(fixture.idempotency.completions).isEqualTo(2);
        assertThat(fixture.recordFlows.binds).hasSize(2)
                .allSatisfy(binding -> {
                    assertThat(binding.bindingSource())
                            .isEqualTo(RuntimeRecordFlowFacade.BindingSource.OPENAPI);
                    assertThat(binding.systemId()).isEqualTo(1L);
                    assertThat(binding.tenantId()).isEqualTo(2L);
                    assertThat(binding.memberId()).isEqualTo(10L);
                    assertThat(binding.effectivePermissions()).isEqualTo(fixture.session.permissions());
                });
        assertThat(fixture.audit.successes).hasSize(2)
                .allSatisfy(operation -> {
                    assertThat(operation.action()).isEqualTo("FLOW_INSTANCE_STARTED");
                    assertThat(operation.actor().accountId()).isEqualTo(99L);
                    assertThat(operation.actor().sourceType()).isEqualTo("OPENAPI");
                    assertThat(operation.context().systemId()).isEqualTo(1L);
                    assertThat(operation.context().tenantId()).isEqualTo(2L);
                    assertThat(operation.before()).isNull();
                    assertThat(operation.after()).isInstanceOf(Map.class);
                });
        assertThat(fixture.audit.successes.getFirst().requestId()).isEqualTo("request-1");
        assertThat(fixture.audit.successes.getFirst().traceId()).isEqualTo("trace-1");
        assertThat(FlowMutationService.class.getMethod(
                        "startOpenApi",
                        FlowSession.class,
                        long.class,
                        long.class,
                        FlowRequests.StartInstance.class,
                        String.class,
                        String.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void rejectsNonPositiveApplicationBeforeAnyMutation() {
        var fixture = fixture();

        assertThatThrownBy(() -> fixture.mutations.startOpenApi(
                fixture.session,
                0L,
                fixture.definitionId,
                new FlowRequests.StartInstance(null, "invalid-application"),
                "external-start-key",
                "request-invalid",
                "trace-invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application ID");

        assertThat(fixture.workflow.instances(1, 20).items()).isEmpty();
        assertThat(fixture.idempotency.scopeKeys()).isEmpty();
        assertThat(fixture.audit.successes).isEmpty();
    }

    private static Fixture fixture() {
        var sequence = new AtomicLong(200L);
        var workflow = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC));
        var definition = workflow.createDraft("External approval", 20L);
        workflow.publish(definition.id());
        var idempotency = new ScopedMemoryIdempotency();
        var recordFlows = new RecordingRecordFlows();
        var audit = new RecordingOperationAudit();
        var mutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                idempotency,
                new ObjectMapper(),
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)),
                recordFlows,
                audit);
        var session = new FlowSession(
                99L,
                1L,
                2L,
                10L,
                Set.of(
                        "flow.instance.start",
                        "system.runtime.access",
                        "module.purchase_order.view"));
        return new Fixture(
                workflow,
                mutations,
                idempotency,
                recordFlows,
                audit,
                session,
                definition.id());
    }

    private record Fixture(
            ApprovalWorkflowService workflow,
            FlowMutationService mutations,
            ScopedMemoryIdempotency idempotency,
            RecordingRecordFlows recordFlows,
            RecordingOperationAudit audit,
            FlowSession session,
            long definitionId
    ) {
    }

    private static final class ScopedMemoryIdempotency implements IdempotencyFacade {
        private final AtomicLong sequence = new AtomicLong(80L);
        private final Map<Key, IdempotencyRecord> records = new HashMap<>();
        private int completions;

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(new Key(scopeType, scopeKey, key)));
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                Duration ttl
        ) {
            var id = sequence.incrementAndGet();
            records.put(
                    new Key(scopeType, scopeKey, key),
                    new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            return id;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            var entry = records.entrySet().stream()
                    .filter(candidate -> candidate.getValue().id() == id)
                    .findFirst()
                    .orElseThrow();
            var record = entry.getValue();
            entry.setValue(new IdempotencyRecord(
                    id,
                    record.requestHash(),
                    "COMPLETED",
                    responseBody));
            completions++;
            assertThat(httpStatus).isEqualTo(201);
            assertThat(responseCode).isEqualTo("OK");
        }

        private Set<String> scopeKeys() {
            return records.keySet().stream().map(Key::scopeKey).collect(
                    java.util.stream.Collectors.toSet());
        }

        private record Key(String scopeType, String scopeKey, String key) {
        }
    }

    private static final class RecordingRecordFlows implements RuntimeRecordFlowFacade {
        private final ArrayList<BindRequest> binds = new ArrayList<>();

        @Override
        public RecordFlowState bind(BindRequest request) {
            binds.add(request);
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt());
        }

        @Override
        public RecordFlowState bindAdditional(AdditionalBindRequest request) {
            throw new AssertionError("OpenAPI start does not create an additional binding");
        }

        @Override
        public RecordFlowState transition(TransitionRequest request) {
            throw new AssertionError("OpenAPI start does not project a terminal state");
        }
    }

    private static final class RecordingOperationAudit implements OperationAuditFacade {
        private final ArrayList<OperationAudit> successes = new ArrayList<>();

        @Override
        public void recordSuccess(OperationAudit audit) {
            successes.add(audit);
        }

        @Override
        public void recordDenied(OperationAudit audit) {
            throw new AssertionError("OpenAPI start service does not record denied audit");
        }

        @Override
        public void recordFailed(OperationAudit audit) {
            throw new AssertionError("OpenAPI start service does not record failed audit");
        }
    }
}
