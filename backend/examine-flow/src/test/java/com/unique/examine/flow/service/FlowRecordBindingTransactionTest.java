package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.core.runtime.RuntimeRecordFlowTriggerFacade;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowRecordBindingTransactionTest {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final FlowSession REQUESTER = new FlowSession(
            100L,
            200L,
            10L,
            Set.of("flow.instance.start", "module.record.view.purchase_order")
    );

    @Test
    void failedBoundManualStartRollsBackIdempotencyInstanceAndHistory() {
        var fixture = fixture(true, false);

        assertThatThrownBy(() -> fixture.mutations.start(
                REQUESTER,
                fixture.definitionId,
                bound("bind-failure"),
                "bind-failure-key",
                "request-bind-failure",
                "trace-bind-failure"
        )).isInstanceOf(ProjectionFailure.class);

        assertThat(fixture.repository.countInstances()).isZero();
        assertThat(fixture.idempotency.find(
                "FLOW_INSTANCE",
                "100:200:10:" + fixture.definitionId + ":start",
                "bind-failure-key"
        )).isEmpty();
    }

    @Test
    void failedBoundOpenApiStartRollsBackAndTheSameBusinessKeyCanRetry() {
        var fixture = fixture(true, false);
        var scopeKey = "100:200:10:77:" + fixture.definitionId + ":openapi-start";

        assertThatThrownBy(() -> fixture.mutations.startOpenApi(
                REQUESTER,
                77L,
                fixture.definitionId,
                bound("openapi-bind-failure"),
                "openapi-bind-failure-key",
                "request-openapi-bind-failure",
                "trace-openapi-bind-failure"
        )).isInstanceOf(ProjectionFailure.class);

        assertThat(fixture.repository.countInstances()).isZero();
        assertThat(fixture.idempotency.find(
                "FLOW_INSTANCE",
                scopeKey,
                "openapi-bind-failure-key"
        )).isEmpty();

        fixture.recordFlows.failBind = false;
        var retried = fixture.mutations.startOpenApi(
                REQUESTER,
                77L,
                fixture.definitionId,
                bound("openapi-bind-failure"),
                "openapi-bind-failure-key",
                "request-openapi-bind-retry",
                "trace-openapi-bind-retry"
        );

        assertThat(retried.status()).isEqualTo("PENDING");
        assertThat(fixture.repository.countInstances()).isOne();
        assertThat(fixture.repository.findInstance(Long.parseLong(retried.instanceId())))
                .get()
                .satisfies(instance -> assertThat(instance.history()).hasSize(1));
        assertThat(fixture.idempotency.find(
                "FLOW_INSTANCE",
                scopeKey,
                "openapi-bind-failure-key"
        )).get().extracting(IdempotencyRecord::status).isEqualTo("COMPLETED");
    }

    @Test
    void failedTerminalProjectionRollsBackFlowStatusAndHistory() {
        var fixture = fixture(false, true);
        var started = fixture.mutations.start(
                REQUESTER,
                fixture.definitionId,
                bound("transition-failure")
        );
        var instanceId = Long.parseLong(started.instanceId());

        assertThatThrownBy(() -> fixture.mutations.approve(
                new FlowSession(100L, 200L, 20L, Set.of("flow.instance.decide")),
                instanceId,
                new FlowRequests.Decision("approved")
        )).isInstanceOf(ProjectionFailure.class);

        var restored = fixture.repository.findInstance(instanceId).orElseThrow();
        assertThat(restored.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(restored.completedAt()).isNull();
        assertThat(restored.history()).hasSize(1);
        assertThat(restored.recordBinding())
                .isEqualTo(new ApprovalInstance.RecordBinding("purchase_order", 901L));
    }

    @Test
    void failedTriggerDispatchCompletionRollsBackTheCompleteFanout() {
        var repository = new RollbackAwareRepository();
        var sequence = new AtomicLong(2000L);
        var workflow = new ApprovalWorkflowService(
                repository,
                new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        var draft = workflow.createDraft(
                "Triggered approval",
                List.of(20L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        200,
                        false
                )
        );
        workflow.publish(draft.id());
        var secondDraft = workflow.createDraft(
                "Triggered approval two",
                List.of(30L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        100,
                        false
                )
        );
        workflow.publish(secondDraft.id());
        FlowRequestServiceFactory services = (systemId, tenantId) -> workflow;
        var mutations = new FlowMutationService(
                services,
                new UnusedIdempotency(),
                new ObjectMapper(),
                activeMembers(),
                successfulRecordFlows()
        );
        var target = new FlowRecordTriggerAdapter(services, mutations);
        var proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                new TestTransactionManager(),
                new AnnotationTransactionAttributeSource()
        ));
        var adapter = (FlowRecordTriggerAdapter) proxyFactory.getProxy();
        repository.failDispatch = true;

        assertThatThrownBy(() -> adapter.trigger(
                new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                        100L,
                        200L,
                        10L,
                        REQUESTER.permissions(),
                        "purchase_order",
                        901L,
                        1L,
                        "PO-901",
                        RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                        "event-dispatch-failure"
                )
        )).isInstanceOf(ProjectionFailure.class);

        assertThat(repository.countInstances()).isZero();
        assertThat(repository.findTriggerDispatchForUpdate("event-dispatch-failure"))
                .isEmpty();
    }

    private static Fixture fixture(boolean failBind, boolean failTransition) {
        var repository = new RollbackAwareRepository();
        var sequence = new AtomicLong(1000L);
        var workflow = new ApprovalWorkflowService(
                repository,
                new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        var draft = workflow.createDraft("Approval", 20L);
        workflow.publish(draft.id());
        FlowRequestServiceFactory services = (systemId, tenantId) -> workflow;
        var idempotency = new RollbackAwareIdempotency();
        var recordFlows = new FailingRecordFlows(failBind, failTransition);
        var target = new FlowMutationService(
                services,
                idempotency,
                new ObjectMapper(),
                activeMembers(),
                recordFlows
        );
        var proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                new TestTransactionManager(),
                new AnnotationTransactionAttributeSource()
        ));
        return new Fixture(
                repository,
                idempotency,
                (FlowMutationService) proxyFactory.getProxy(),
                recordFlows,
                draft.id()
        );
    }

    private static RuntimeActiveMemberFacade activeMembers() {
        return (systemId, tenantId, memberId) -> Optional.of(
                new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
        );
    }

    private static RuntimeRecordFlowFacade successfulRecordFlows() {
        return new RuntimeRecordFlowFacade() {
            @Override
            public RecordFlowState bind(BindRequest request) {
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
                throw new AssertionError("Trigger rollback test does not transition instances");
            }
        };
    }

    private static FlowRequests.StartInstance bound(String businessKey) {
        return new FlowRequests.StartInstance(
                null,
                businessKey,
                new FlowRequests.RecordBinding("purchase_order", "901")
        );
    }

    private record Fixture(
            RollbackAwareRepository repository,
            RollbackAwareIdempotency idempotency,
            FlowMutationService mutations,
            FailingRecordFlows recordFlows,
            long definitionId
    ) {
    }

    private static final class FailingRecordFlows implements RuntimeRecordFlowFacade {
        private boolean failBind;
        private final boolean failTransition;

        private FailingRecordFlows(boolean failBind, boolean failTransition) {
            this.failBind = failBind;
            this.failTransition = failTransition;
        }

        @Override
        public RecordFlowState bind(BindRequest request) {
            if (failBind) {
                throw new ProjectionFailure();
            }
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt()
            );
        }

        @Override
        public RecordFlowState bindAdditional(AdditionalBindRequest request) {
            if (failBind) {
                throw new ProjectionFailure();
            }
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt()
            );
        }

        @Override
        public RecordFlowState transition(TransitionRequest request) {
            if (failTransition) {
                throw new ProjectionFailure();
            }
            return new RecordFlowState(
                    request.instanceId(),
                    request.status(),
                    1L,
                    request.occurredAt()
            );
        }
    }

    private static final class ProjectionFailure extends RuntimeException {
    }

    private static final class RollbackAwareIdempotency implements IdempotencyFacade {
        private IdempotencyRecord record;
        private String scopeType;
        private String scopeKey;
        private String key;

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return this.record != null
                    && scopeType.equals(this.scopeType)
                    && scopeKey.equals(this.scopeKey)
                    && key.equals(this.key)
                    ? Optional.of(this.record)
                    : Optional.empty();
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                Duration ttl
        ) {
            var before = record;
            registerRollback(() -> record = before);
            this.scopeType = scopeType;
            this.scopeKey = scopeKey;
            this.key = key;
            this.record = new IdempotencyRecord(81L, requestHash, "PROCESSING", null);
            return 81L;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            record = new IdempotencyRecord(id, record.requestHash(), "COMPLETED", responseBody);
        }

        private static void registerRollback(Runnable rollback) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        rollback.run();
                    }
                }
            });
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private static final class RollbackAwareRepository implements ApprovalRepository {
        private final Map<Long, ApprovalDefinitionDraft> drafts = new HashMap<>();
        private final Map<Long, TreeMap<Integer, ApprovalDefinitionVersion>> versions =
                new HashMap<>();
        private final Map<Long, ApprovalInstance> instances = new HashMap<>();
        private final Map<String, FlowTriggerDispatch> triggerDispatches = new HashMap<>();
        private boolean failDispatch;

        @Override
        public ApprovalDefinitionDraft saveDraft(ApprovalDefinitionDraft draft) {
            drafts.put(draft.id(), draft);
            return draft;
        }

        @Override
        public Optional<ApprovalDefinitionDraft> findDraft(long definitionId) {
            return Optional.ofNullable(drafts.get(definitionId));
        }

        @Override
        public List<ApprovalDefinitionDraft> findDrafts(int offset, int limit) {
            return List.of();
        }

        @Override
        public long countDrafts() {
            return drafts.size();
        }

        @Override
        public ApprovalDefinitionVersion saveVersion(ApprovalDefinitionVersion version) {
            versions.computeIfAbsent(version.definitionId(), ignored -> new TreeMap<>())
                    .put(version.version(), version);
            return version;
        }

        @Override
        public Optional<ApprovalDefinitionVersion> findVersion(long definitionId, int version) {
            return Optional.ofNullable(versions.getOrDefault(definitionId, new TreeMap<>())
                    .get(version));
        }

        @Override
        public Optional<ApprovalDefinitionVersion> findLatestVersion(long definitionId) {
            var values = versions.get(definitionId);
            return values == null || values.isEmpty()
                    ? Optional.empty()
                    : Optional.of(values.lastEntry().getValue());
        }

        @Override
        public List<ApprovalDefinitionVersion> findTriggerCandidates(
                String moduleCode,
                TriggerBinding.Event event
        ) {
            return versions.values().stream()
                    .filter(values -> !values.isEmpty())
                    .map(values -> values.lastEntry().getValue())
                    .filter(version -> version.triggerBinding() != null)
                    .filter(version -> version.triggerBinding().moduleCode().equals(moduleCode))
                    .filter(version -> version.triggerBinding().event() == event)
                    .sorted(Comparator
                            .comparingInt((ApprovalDefinitionVersion version) ->
                                    version.triggerBinding().priority())
                            .reversed()
                            .thenComparingLong(ApprovalDefinitionVersion::definitionId))
                    .toList();
        }

        @Override
        public Optional<FlowTriggerDispatch> findTriggerDispatchForUpdate(String eventKey) {
            return Optional.ofNullable(triggerDispatches.get(eventKey));
        }

        @Override
        public FlowTriggerDispatch saveTriggerDispatch(FlowTriggerDispatch dispatch) {
            if (failDispatch) {
                throw new ProjectionFailure();
            }
            var before = triggerDispatches.get(dispatch.eventKey());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
                                    return;
                                }
                                if (before == null) {
                                    triggerDispatches.remove(dispatch.eventKey());
                                } else {
                                    triggerDispatches.put(dispatch.eventKey(), before);
                                }
                            }
                        }
                );
            }
            triggerDispatches.put(dispatch.eventKey(), dispatch);
            return dispatch;
        }

        @Override
        public ApprovalInstance saveInstance(ApprovalInstance instance) {
            var before = instances.get(instance.id());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCompletion(int status) {
                                if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
                                    return;
                                }
                                if (before == null) {
                                    instances.remove(instance.id());
                                } else {
                                    instances.put(instance.id(), before);
                                }
                            }
                        }
                );
            }
            instances.put(instance.id(), instance);
            return instance;
        }

        @Override
        public Optional<ApprovalInstance> findInstance(long instanceId) {
            return Optional.ofNullable(instances.get(instanceId));
        }

        @Override
        public List<ApprovalInstance> findInstances(int offset, int limit) {
            return instances.values().stream()
                    .sorted(Comparator.comparing(ApprovalInstance::startedAt).reversed())
                    .skip(offset)
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countInstances() {
            return instances.size();
        }

        @Override
        public List<ApprovalInstance> findApprovalTasks(
                long approverId,
                ApprovalTaskStatus status,
                int offset,
                int limit
        ) {
            return List.of();
        }

        @Override
        public long countApprovalTasks(long approverId, ApprovalTaskStatus status) {
            return 0;
        }

        @Override
        public List<ApprovalInstance> findClaimableTasks(int offset, int limit) {
            return List.of();
        }

        @Override
        public long countClaimableTasks() {
            return 0;
        }
    }

    private static final class UnusedIdempotency implements IdempotencyFacade {
        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.empty();
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                Duration ttl
        ) {
            throw new AssertionError("Idempotency is not used by start/approve");
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            throw new AssertionError("Idempotency is not used by start/approve");
        }
    }
}
