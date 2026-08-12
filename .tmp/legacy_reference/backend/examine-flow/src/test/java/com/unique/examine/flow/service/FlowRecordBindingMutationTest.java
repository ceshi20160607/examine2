package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowRecordBindingMutationTest {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final Set<String> START_PERMISSIONS = Set.of(
            "flow.instance.start",
            "module.record.view.purchase_order"
    );

    @Test
    void boundStartPassesCanonicalAuthorizationContextAndReturnsTheSnapshot() {
        var harness = harness(List.of(20L));
        var session = session(10L, START_PERMISSIONS);

        var result = harness.mutations.start(
                session,
                harness.definitionId,
                new FlowRequests.StartInstance(
                        null,
                        "PO-2026-0008",
                        new FlowRequests.RecordBinding("purchase_order", "901")
                )
        );

        assertThat(result.recordBinding())
                .isEqualTo(new com.unique.examine.flow.api.FlowViews.RecordBinding(
                        "purchase_order",
                        "901"
                ));
        assertThat(harness.recordFlows.binds).singleElement().satisfies(request -> {
            assertThat(request.systemId()).isEqualTo(100L);
            assertThat(request.tenantId()).isEqualTo(200L);
            assertThat(request.memberId()).isEqualTo(10L);
            assertThat(request.effectivePermissions()).isEqualTo(START_PERMISSIONS);
            assertThat(request.moduleCode()).isEqualTo("purchase_order");
            assertThat(request.recordId()).isEqualTo(901L);
            assertThat(request.instanceId()).isEqualTo(Long.parseLong(result.instanceId()));
            assertThat(request.occurredAt()).isEqualTo(NOW);
            assertThat(request.recordStatusMapping()).isNull();
            assertThat(request.bindingSource())
                    .isEqualTo(RuntimeRecordFlowFacade.BindingSource.MANUAL);
        });
        assertThat(harness.workflow.instance(Long.parseLong(result.instanceId())).recordBinding())
                .isEqualTo(new ApprovalInstance.RecordBinding("purchase_order", 901L));
    }

    @Test
    void boundStartUsesTheExactPublishedDefinitionVersionStatusMapping() {
        var firstMapping = mapping("101", "102", "103", "104");
        var secondMapping = mapping("201", "202", "203", "204");
        var harness = harness(List.of(20L), firstMapping);
        harness.workflow.reviseDraft(
                harness.definitionId,
                "Approval v2",
                List.of(20L),
                null,
                secondMapping
        );
        harness.workflow.publish(harness.definitionId);

        harness.mutations.start(
                session(10L, START_PERMISSIONS),
                harness.definitionId,
                new FlowRequests.StartInstance(1, "version-one", bound("unused").recordBinding())
        );
        harness.mutations.start(
                session(10L, START_PERMISSIONS),
                harness.definitionId,
                bound("latest")
        );

        assertThat(harness.recordFlows.binds)
                .extracting(RuntimeRecordFlowFacade.BindRequest::recordStatusMapping)
                .containsExactly(
                        portMapping(firstMapping),
                        portMapping(secondMapping)
                );
    }

    @Test
    void onlyTerminalDecisionsProjectAndEveryTerminalStatusMapsExactlyOnce() {
        var sequential = harness(List.of(20L, 30L));
        var started = sequential.mutations.start(
                session(10L, START_PERMISSIONS),
                sequential.definitionId,
                bound("sequential")
        );
        var instanceId = Long.parseLong(started.instanceId());

        var pending = sequential.mutations.approve(
                session(20L, Set.of("flow.instance.decide")),
                instanceId,
                new FlowRequests.Decision("first")
        );
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(sequential.recordFlows.transitions).isEmpty();

        sequential.mutations.approve(
                session(30L, Set.of("flow.instance.decide")),
                instanceId,
                new FlowRequests.Decision("final")
        );
        assertThat(sequential.recordFlows.transitions).singleElement()
                .extracting(RuntimeRecordFlowFacade.TransitionRequest::status)
                .isEqualTo(RuntimeRecordFlowFacade.FlowStatus.APPROVED);

        assertTerminalProjection(
                RuntimeRecordFlowFacade.FlowStatus.REJECTED,
                harness -> harness.mutations.reject(
                        session(20L, Set.of("flow.instance.decide")),
                        harness.boundInstance("reject"),
                        new FlowRequests.Rejection("no")
                )
        );
        assertTerminalProjection(
                RuntimeRecordFlowFacade.FlowStatus.WITHDRAWN,
                harness -> harness.mutations.withdraw(
                        session(10L, Set.of("flow.instance.withdraw")),
                        harness.boundInstance("withdraw"),
                        new FlowRequests.Withdrawal("changed"),
                        "withdraw-bound"
                )
        );
        assertTerminalProjection(
                RuntimeRecordFlowFacade.FlowStatus.TERMINATED,
                harness -> harness.mutations.terminate(
                        session(99L, Set.of("flow.instance.terminate")),
                        harness.boundInstance("terminate"),
                        new FlowRequests.Termination("obsolete"),
                        "terminate-bound"
                )
        );
    }

    @Test
    void unboundAndInvalidStartsDoNotCallTheRecordPort() {
        var harness = harness(List.of(20L));
        var unbound = harness.mutations.start(
                session(10L, Set.of("flow.instance.start")),
                harness.definitionId,
                new FlowRequests.StartInstance(null, "unbound")
        );
        harness.mutations.approve(
                session(20L, Set.of("flow.instance.decide")),
                Long.parseLong(unbound.instanceId()),
                new FlowRequests.Decision("done")
        );

        var invalid = catchThrowableOfType(
                () -> harness.mutations.start(
                        session(10L, START_PERMISSIONS),
                        harness.definitionId,
                        new FlowRequests.StartInstance(
                                null,
                                "invalid",
                                new FlowRequests.RecordBinding("purchase_order", null)
                        )
                ),
                BusinessException.class
        );

        assertThat(invalid.code()).isEqualTo("FLOW_RECORD_BINDING_INVALID");
        assertThat(invalid.status().value()).isEqualTo(422);
        assertThat(harness.recordFlows.binds).isEmpty();
        assertThat(harness.recordFlows.transitions).isEmpty();
        assertThat(harness.workflow.instances(1, 20).total()).isEqualTo(1);
    }

    @Test
    void idempotentTerminalReplayDoesNotProjectTheRecordTwice() {
        var harness = harness(List.of(20L));
        var instanceId = harness.boundInstance("withdraw-replay");
        var requester = session(10L, Set.of("flow.instance.withdraw"));
        var request = new FlowRequests.Withdrawal("changed");

        var first = harness.mutations.withdraw(
                requester,
                instanceId,
                request,
                "withdraw-replay-key"
        );
        var replay = harness.mutations.withdraw(
                requester,
                instanceId,
                request,
                "withdraw-replay-key"
        );

        assertThat(replay).isEqualTo(first);
        assertThat(harness.recordFlows.transitions).hasSize(1);
        assertThat(harness.workflow.history(instanceId)).hasSize(2);
    }

    @Test
    void startApproveRejectWithdrawAndTerminateExposeSpringTransactionBoundaries() throws Exception {
        assertTransactional("start", long.class, FlowRequests.StartInstance.class);
        assertTransactional("approve", long.class, FlowRequests.Decision.class);
        assertTransactional("reject", long.class, FlowRequests.Rejection.class);
        assertTransactional("withdraw", long.class, FlowRequests.Withdrawal.class, String.class);
        assertTransactional("terminate", long.class, FlowRequests.Termination.class, String.class);
    }

    private static void assertTerminalProjection(
            RuntimeRecordFlowFacade.FlowStatus expected,
            java.util.function.Consumer<Harness> mutation
    ) {
        var harness = harness(List.of(20L));
        mutation.accept(harness);
        assertThat(harness.recordFlows.transitions).singleElement().satisfies(request -> {
            assertThat(request.status()).isEqualTo(expected);
            assertThat(request.instanceId()).isPositive();
            assertThat(request.occurredAt()).isEqualTo(NOW);
        });
    }

    private static void assertTransactional(
            String method,
            Class<?>... trailingParameters
    ) throws Exception {
        var parameters = new ArrayList<Class<?>>();
        parameters.add(FlowSession.class);
        for (var parameter : trailingParameters) {
            parameters.add(parameter);
        }
        assertThat(FlowMutationService.class.getMethod(
                        method,
                        parameters.toArray(Class<?>[]::new))
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private static FlowRequests.StartInstance bound(String businessKey) {
        return new FlowRequests.StartInstance(
                null,
                businessKey,
                new FlowRequests.RecordBinding("purchase_order", "901")
        );
    }

    private static Harness harness(List<Long> approvers) {
        return harness(approvers, null);
    }

    private static Harness harness(
            List<Long> approvers,
            RecordStatusMapping recordStatusMapping
    ) {
        var repository = new InMemoryApprovalRepository();
        var sequence = new AtomicLong(1000L);
        IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var workflow = new ApprovalWorkflowService(
                repository,
                ids,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        var definition = workflow.createDraft(
                "Approval",
                approvers,
                null,
                recordStatusMapping
        );
        workflow.publish(definition.id());
        FlowRequestServiceFactory services = (systemId, tenantId) -> workflow;
        var recordFlows = new RecordingRecordFlows();
        var mutations = new FlowMutationService(
                services,
                new MemoryIdempotency(),
                new ObjectMapper(),
                activeMembers(),
                recordFlows
        );
        return new Harness(workflow, mutations, recordFlows, definition.id());
    }

    private static RecordStatusMapping mapping(
            String approved,
            String rejected,
            String withdrawn,
            String terminated
    ) {
        return new RecordStatusMapping(
                "approval_status",
                approved,
                rejected,
                withdrawn,
                terminated
        );
    }

    private static RuntimeRecordFlowFacade.RecordStatusMapping portMapping(
            RecordStatusMapping value
    ) {
        return new RuntimeRecordFlowFacade.RecordStatusMapping(
                value.fieldCode(),
                value.approvedValue(),
                value.rejectedValue(),
                value.withdrawnValue(),
                value.terminatedValue()
        );
    }

    private static RuntimeActiveMemberFacade activeMembers() {
        return (systemId, tenantId, memberId) -> Optional.of(
                new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
        );
    }

    private static FlowSession session(long memberId, Set<String> permissions) {
        return new FlowSession(100L, 200L, memberId, permissions);
    }

    private record Harness(
            ApprovalWorkflowService workflow,
            FlowMutationService mutations,
            RecordingRecordFlows recordFlows,
            long definitionId
    ) {
        long boundInstance(String businessKey) {
            return Long.parseLong(mutations.start(
                    session(10L, START_PERMISSIONS),
                    definitionId,
                    bound(businessKey)
            ).instanceId());
        }
    }

    private static final class RecordingRecordFlows implements RuntimeRecordFlowFacade {
        private final List<BindRequest> binds = new ArrayList<>();
        private final List<TransitionRequest> transitions = new ArrayList<>();

        @Override
        public RecordFlowState bind(BindRequest request) {
            binds.add(request);
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt()
            );
        }

        @Override
        public RecordFlowState bindAdditional(AdditionalBindRequest request) {
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt()
            );
        }

        @Override
        public RecordFlowState transition(TransitionRequest request) {
            transitions.add(request);
            return new RecordFlowState(
                    request.instanceId(),
                    request.status(),
                    1L,
                    request.occurredAt()
            );
        }
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Key> keys = new ConcurrentHashMap<>();
        private final Map<Key, IdempotencyRecord> records = new ConcurrentHashMap<>();

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
            var lookup = new Key(scopeType, scopeKey, key);
            keys.put(id, lookup);
            records.put(lookup, new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            return id;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            var key = keys.get(id);
            var current = records.get(key);
            records.put(
                    key,
                    new IdempotencyRecord(id, current.requestHash(), "COMPLETED", responseBody)
            );
        }
    }

    private record Key(String scopeType, String scopeKey, String key) {
    }
}
