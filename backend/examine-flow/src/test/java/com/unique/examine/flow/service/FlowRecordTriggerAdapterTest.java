package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.core.runtime.RuntimeRecordFlowTriggerFacade;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.domain.TriggerCondition;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
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

class FlowRecordTriggerAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-27T13:00:00Z");
    private static final Set<String> PERMISSIONS = Set.of(
            "module.record.view.purchase_order",
            "flow.instance.start"
    );

    @Test
    void selectsHighestPriorityThenLowestDefinitionIdFromLatestPublishedVersions() {
        var harness = harness();
        var shadowed = harness.createDefinition("Shadowed", 999);
        harness.workflow.reviseDraft(
                shadowed,
                "Shadowed latest manual",
                List.of(20L),
                null
        );
        harness.workflow.publish(shadowed);
        harness.createDefinition("Low", 10);
        var selectedDefinition = harness.createDefinition("High first", 200);
        harness.createDefinition("High second", 200);

        var result = harness.adapter.trigger(request("event-priority", 901L, "PO-901"));

        assertThat(result.instances()).singleElement().satisfies(triggered -> {
            assertThat(triggered.definitionId()).isEqualTo(selectedDefinition);
            assertThat(triggered.definitionVersion()).isEqualTo(1);
        });
        var triggered = result.instances().getFirst();
        var instance = harness.workflow.instance(triggered.instanceId());
        assertThat(instance.definitionId()).isEqualTo(selectedDefinition);
        assertThat(instance.definitionVersion()).isEqualTo(1);
        assertThat(instance.requesterId()).isEqualTo(10L);
        assertThat(instance.businessKey()).isEqualTo("PO-901");
        assertThat(instance.recordBinding().moduleCode()).isEqualTo("purchase_order");
        assertThat(instance.recordBinding().recordId()).isEqualTo(901L);
        assertThat(harness.recordFlows.binds).hasSize(1);
    }

    @Test
    void matchedReplayReturnsOriginalResultWithoutAnotherInstanceOrProjection() {
        var harness = harness();
        harness.createDefinition("Approval", 100);
        var request = request("event-replay", 902L, "PO-902");

        var first = harness.adapter.trigger(request);
        var replay = harness.adapter.trigger(request);

        assertThat(replay).isEqualTo(first);
        assertThat(harness.workflow.instances(1, 20).total()).isEqualTo(1);
        assertThat(harness.workflow.history(first.instances().getFirst().instanceId()))
                .hasSize(1);
        assertThat(harness.recordFlows.binds).hasSize(1);
    }

    @Test
    void startsEveryMatchingNonexclusiveDefinitionInStableOrderAndProjectsAdditionalInstances() {
        var harness = harness();
        var lowMapping = mapping("301", "302", "303", "304");
        var firstHighMapping = mapping("101", "102", "103", "104");
        var secondHighMapping = mapping("201", "202", "203", "204");
        var low = harness.createDefinition("Low", 10, false, List.of(), lowMapping);
        var firstHigh = harness.createDefinition(
                "High first",
                200,
                false,
                List.of(new TriggerCondition("amount", TriggerCondition.Operator.GTE, "100")),
                firstHighMapping
        );
        var secondHigh = harness.createDefinition(
                "High second",
                200,
                false,
                List.of(new TriggerCondition("urgent", TriggerCondition.Operator.EQ, "true")),
                secondHighMapping
        );
        harness.createDefinition(
                "Does not match",
                999,
                false,
                List.of(new TriggerCondition("amount", TriggerCondition.Operator.GT, "1000"))
        );

        var result = harness.adapter.trigger(request(
                200L,
                "event-fanout",
                906L,
                "PO-906",
                Map.of("amount", "150", "urgent", "true")
        ));

        assertThat(result.instances())
                .extracting(RuntimeRecordFlowTriggerFacade.TriggeredInstance::definitionId)
                .containsExactly(firstHigh, secondHigh, low);
        assertThat(harness.recordFlows.binds).singleElement()
                .satisfies(binding -> {
                    assertThat(binding.instanceId())
                            .isEqualTo(result.instances().getFirst().instanceId());
                    assertThat(binding.bindingSource())
                            .isEqualTo(RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
                    assertThat(binding.recordStatusMapping())
                            .isEqualTo(portMapping(firstHighMapping));
                });
        assertThat(harness.recordFlows.additionalBinds)
                .extracting(RuntimeRecordFlowFacade.AdditionalBindRequest::eventKey)
                .containsExactly("event-fanout", "event-fanout");
        assertThat(harness.recordFlows.additionalBinds)
                .extracting(RuntimeRecordFlowFacade.AdditionalBindRequest::instanceId)
                .containsExactly(
                        result.instances().get(1).instanceId(),
                        result.instances().get(2).instanceId()
                );
        assertThat(harness.recordFlows.additionalBinds)
                .extracting(RuntimeRecordFlowFacade.AdditionalBindRequest::recordStatusMapping)
                .containsExactly(
                        portMapping(secondHighMapping),
                        portMapping(lowMapping)
                );
        assertThat(harness.recordFlows.additionalBinds)
                .extracting(RuntimeRecordFlowFacade.AdditionalBindRequest::bindingSource)
                .containsOnly(RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT);
    }

    @Test
    void dispatchesNewRecordEventsThroughTheSameAutomaticBindingPath() {
        var harness = harness();
        var draft = harness.workflow.createDraft(
                "Deleted record observation",
                List.of(20L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_DELETED,
                        100,
                        true
                )
        );
        harness.workflow.publish(draft.id());
        var request = new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                100L,
                200L,
                10L,
                PERMISSIONS,
                "purchase_order",
                910L,
                3L,
                "PO-910",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_DELETED,
                "event-deleted",
                Map.of("amount", "150")
        );

        var result = harness.adapter.trigger(request);

        assertThat(result.instances()).singleElement()
                .satisfies(instance -> assertThat(instance.definitionId()).isEqualTo(draft.id()));
        assertThat(harness.recordFlows.binds).singleElement()
                .satisfies(binding -> assertThat(binding.bindingSource())
                        .isEqualTo(RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT));
    }

    @Test
    void dispatchesImportCompletedWithConditionsAndExactReplay() {
        var harness = harness();
        var draft = harness.workflow.createDraft(
                "Imported record approval",
                List.of(20L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.IMPORT_COMPLETED,
                        150,
                        true,
                        List.of(new TriggerCondition("route", TriggerCondition.Operator.EQ, "\"import\""))
                )
        );
        harness.workflow.publish(draft.id());
        var request = new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                100L, 200L, 10L, PERMISSIONS, "purchase_order", 911L, 4L, "PO-911",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.IMPORT_COMPLETED,
                "record:100:200:purchase_order:911:4:IMPORT_COMPLETED",
                Map.of("route", "\"import\"")
        );

        var first = harness.adapter.trigger(request);
        var replay = harness.adapter.trigger(request);

        assertThat(first.instances()).singleElement()
                .satisfies(instance -> assertThat(instance.definitionId()).isEqualTo(draft.id()));
        assertThat(replay).isEqualTo(first);
        assertThat(harness.workflow.instances(1, 20).total()).isOne();
        assertThat(harness.recordFlows.binds).singleElement()
                .satisfies(binding -> assertThat(binding.bindingSource())
                        .isEqualTo(RuntimeRecordFlowFacade.BindingSource.AUTOMATIC_EVENT));
    }

    @Test
    void recordAndImportTriggersResolveManagerFromThePersistedTriggeringActor() {
        var repository = new InMemoryApprovalRepository();
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
        FlowRequestServiceFactory services = (systemId, tenantId) -> workflow;
        RuntimeApproverDirectoryFacade directory = new RuntimeApproverDirectoryFacade() {
            @Override
            public Resolution resolveRoleMembers(long systemId, long tenantId, long roleId) {
                return Resolution.missing();
            }

            @Override
            public Resolution resolveDepartmentMembers(
                    long systemId,
                    long tenantId,
                    long departmentId
            ) {
                return Resolution.missing();
            }

            @Override
            public Resolution resolveRequesterManager(
                    long systemId,
                    long tenantId,
                    long requesterMemberId
            ) {
                return Resolution.active(List.of(requesterMemberId + 100));
            }
        };
        var recordFlows = new RecordingRecordFlows();
        var mutations = new FlowMutationService(
                services,
                new UnusedIdempotency(),
                new ObjectMapper(),
                activeMembers(),
                directory,
                recordFlows
        );
        var adapter = new FlowRecordTriggerAdapter(services, mutations);
        var recordDraft = workflow.createDraft(
                "Record requester manager",
                List.of(20L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        100,
                        true
                ),
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                new ApprovalApproverSources(
                        ApprovalApproverSource.requesterManager(),
                        Map.of()
                )
        );
        var importDraft = workflow.createDraft(
                "Import requester manager",
                List.of(20L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.IMPORT_COMPLETED,
                        100,
                        true
                ),
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                new ApprovalApproverSources(
                        ApprovalApproverSource.requesterManager(),
                        Map.of()
                )
        );
        workflow.publish(recordDraft.id());
        workflow.publish(importDraft.id());

        var recordResult = adapter.trigger(new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                100L,
                200L,
                17L,
                PERMISSIONS,
                "purchase_order",
                920L,
                1L,
                "PO-920",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                "event-record-manager",
                Map.of()
        ));
        var importResult = adapter.trigger(new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                100L,
                200L,
                29L,
                PERMISSIONS,
                "purchase_order",
                921L,
                1L,
                "PO-921",
                RuntimeRecordFlowTriggerFacade.TriggerEvent.IMPORT_COMPLETED,
                "event-import-manager",
                Map.of()
        ));

        var recordInstance = workflow.instance(
                recordResult.instances().getFirst().instanceId());
        var importInstance = workflow.instance(
                importResult.instances().getFirst().instanceId());
        assertThat(recordInstance.requesterId()).isEqualTo(17L);
        assertThat(recordInstance.approverIds()).containsExactly(117L);
        assertThat(importInstance.requesterId()).isEqualTo(29L);
        assertThat(importInstance.approverIds()).containsExactly(129L);
    }

    @Test
    void automaticRecordEventUsesTheExactValueSnapshotToResolveGatewayRoute() {
        var harness = harness();
        var gateway = new ApprovalGateway(List.of(
                new ApprovalGateway.Branch(
                        "large",
                        "Large amount",
                        false,
                        List.of(new TriggerCondition(
                                "amount",
                                TriggerCondition.Operator.GTE,
                                "100"
                        )),
                        List.of(30L)
                ),
                new ApprovalGateway.Branch(
                        "default",
                        "Default",
                        true,
                        List.of(),
                        List.of(20L)
                )
        ));
        var draft = harness.workflow.createDraft(
                "Conditional record approval",
                List.of(20L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        100,
                        true
                ),
                null,
                gateway
        );
        harness.workflow.publish(draft.id());

        var result = harness.adapter.trigger(request(
                200L,
                "event-gateway",
                912L,
                "PO-912",
                Map.of("amount", "150")
        ));

        var instance = harness.workflow.instance(result.instances().getFirst().instanceId());
        assertThat(instance.approverIds()).containsExactly(30L);
        assertThat(instance.approverId()).isEqualTo(30L);
    }

    @Test
    void firstMatchingExclusiveDefinitionSuppressesAllNonexclusiveMatches() {
        var harness = harness();
        harness.createDefinition("Higher nonexclusive", 500, false, List.of());
        var winner = harness.createDefinition("Exclusive winner", 200, true, List.of());
        harness.createDefinition("Exclusive later", 100, true, List.of());

        var result = harness.adapter.trigger(request("event-exclusive-winner", 907L, "PO-907"));

        assertThat(result.instances()).singleElement()
                .satisfies(instance -> assertThat(instance.definitionId()).isEqualTo(winner));
        assertThat(harness.recordFlows.binds).hasSize(1);
        assertThat(harness.recordFlows.additionalBinds).isEmpty();
    }

    @Test
    void fanoutReplayReturnsTheExactOrderedChildrenWithoutReevaluationOrProjection() {
        var harness = harness();
        var first = harness.createDefinition("First", 100, false, List.of());
        var second = harness.createDefinition("Second", 50, false, List.of());
        var request = request("event-fanout-replay", 908L, "PO-908");

        var initial = harness.adapter.trigger(request);
        harness.createDefinition("Late exclusive", 1000, true, List.of());
        var replay = harness.adapter.trigger(request);

        assertThat(initial.instances())
                .extracting(RuntimeRecordFlowTriggerFacade.TriggeredInstance::definitionId)
                .containsExactly(first, second);
        assertThat(replay).isEqualTo(initial);
        assertThat(harness.workflow.instances(1, 20).total()).isEqualTo(2);
        assertThat(harness.recordFlows.binds).hasSize(1);
        assertThat(harness.recordFlows.additionalBinds).hasSize(1);
    }

    @Test
    void explicitNoMatchReplayStaysEmptyEvenAfterAMatchingDefinitionIsPublished() {
        var harness = harness();
        var request = request("event-no-match", 903L, "PO-903");

        var first = harness.adapter.trigger(request);
        harness.createDefinition("Late definition", 500);
        var replay = harness.adapter.trigger(request);

        assertThat(first.instances()).isEmpty();
        assertThat(replay.instances()).isEmpty();
        assertThat(harness.workflow.instances(1, 20).total()).isZero();
        assertThat(harness.recordFlows.binds).isEmpty();
    }

    @Test
    void conditionalNoMatchReplayNeverReevaluatesAChangedValueSnapshot() {
        var harness = harness();
        harness.createDefinition(
                "Amount approval",
                100,
                false,
                List.of(new TriggerCondition("amount", TriggerCondition.Operator.GTE, "100"))
        );

        var first = harness.adapter.trigger(request(
                200L,
                "event-condition-no-match",
                909L,
                "PO-909",
                Map.of("amount", "50")
        ));
        var replay = harness.adapter.trigger(request(
                200L,
                "event-condition-no-match",
                909L,
                "PO-909",
                Map.of("amount", "500")
        ));

        assertThat(first.instances()).isEmpty();
        assertThat(replay.instances()).isEmpty();
        assertThat(harness.workflow.instances(1, 20).total()).isZero();
        assertThat(harness.recordFlows.binds).isEmpty();
        assertThat(harness.recordFlows.additionalBinds).isEmpty();
    }

    @Test
    void adapterPublishesARequiredSpringTransactionBoundary() throws Exception {
        assertThat(FlowRecordTriggerAdapter.class.getMethod(
                        "trigger",
                        RuntimeRecordFlowTriggerFacade.TriggerRequest.class
                ).getAnnotation(Transactional.class))
                .isNotNull();
    }

    @Test
    void theSameEventKeyIsIsolatedBySystemAndTenantScope() {
        var services = new ScopedServices();
        var firstWorkflow = services.forTenant(100L, 200L);
        var secondWorkflow = services.forTenant(100L, 201L);
        createDefinition(firstWorkflow, "Tenant one", 100);
        createDefinition(secondWorkflow, "Tenant two", 100);
        var recordFlows = new RecordingRecordFlows();
        var mutations = new FlowMutationService(
                services,
                new UnusedIdempotency(),
                new ObjectMapper(),
                activeMembers(),
                recordFlows
        );
        var adapter = new FlowRecordTriggerAdapter(services, mutations);

        var first = adapter.trigger(request(200L, "shared-event-key", 904L, "PO-904"));
        var second = adapter.trigger(request(201L, "shared-event-key", 905L, "PO-905"));

        assertThat(first.instances()).hasSize(1);
        assertThat(second.instances()).hasSize(1);
        assertThat(first.instances().getFirst().instanceId())
                .isNotEqualTo(second.instances().getFirst().instanceId());
        assertThat(recordFlows.binds)
                .extracting(RuntimeRecordFlowFacade.BindRequest::tenantId)
                .containsExactly(200L, 201L);
    }

    private static Harness harness() {
        var repository = new InMemoryApprovalRepository();
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
        FlowRequestServiceFactory services = (systemId, tenantId) -> workflow;
        var recordFlows = new RecordingRecordFlows();
        var mutations = new FlowMutationService(
                services,
                new UnusedIdempotency(),
                new ObjectMapper(),
                activeMembers(),
                recordFlows
        );
        return new Harness(
                workflow,
                new FlowRecordTriggerAdapter(services, mutations),
                recordFlows
        );
    }

    private static RuntimeRecordFlowTriggerFacade.TriggerRequest request(
            String eventKey,
            long recordId,
            String businessKey
    ) {
        return request(200L, eventKey, recordId, businessKey);
    }

    private static RuntimeRecordFlowTriggerFacade.TriggerRequest request(
            long tenantId,
            String eventKey,
            long recordId,
            String businessKey
    ) {
        return request(tenantId, eventKey, recordId, businessKey, Map.of());
    }

    private static RuntimeRecordFlowTriggerFacade.TriggerRequest request(
            long tenantId,
            String eventKey,
            long recordId,
            String businessKey,
            Map<String, String> recordValuesJson
    ) {
        return new RuntimeRecordFlowTriggerFacade.TriggerRequest(
                100L,
                tenantId,
                10L,
                PERMISSIONS,
                "purchase_order",
                recordId,
                1L,
                businessKey,
                RuntimeRecordFlowTriggerFacade.TriggerEvent.RECORD_ACTIVATED,
                eventKey,
                recordValuesJson
        );
    }

    private static RuntimeActiveMemberFacade activeMembers() {
        return (systemId, tenantId, memberId) -> Optional.of(
                new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
        );
    }

    private static long createDefinition(
            ApprovalWorkflowService workflow,
            String name,
            int priority
    ) {
        return createDefinition(workflow, name, priority, true, List.of());
    }

    private static long createDefinition(
            ApprovalWorkflowService workflow,
            String name,
            int priority,
            boolean exclusive,
            List<TriggerCondition> conditions
    ) {
        return createDefinition(workflow, name, priority, exclusive, conditions, null);
    }

    private static long createDefinition(
            ApprovalWorkflowService workflow,
            String name,
            int priority,
            boolean exclusive,
            List<TriggerCondition> conditions,
            RecordStatusMapping recordStatusMapping
    ) {
        var draft = workflow.createDraft(
                name,
                List.of(20L),
                new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        priority,
                        exclusive,
                        conditions
                ),
                recordStatusMapping
        );
        workflow.publish(draft.id());
        return draft.id();
    }

    private record Harness(
            ApprovalWorkflowService workflow,
            FlowRecordTriggerAdapter adapter,
            RecordingRecordFlows recordFlows
    ) {
        long createDefinition(String name, int priority) {
            return FlowRecordTriggerAdapterTest.createDefinition(workflow, name, priority);
        }

        long createDefinition(
                String name,
                int priority,
                boolean exclusive,
                List<TriggerCondition> conditions
        ) {
            return FlowRecordTriggerAdapterTest.createDefinition(
                    workflow,
                    name,
                    priority,
                    exclusive,
                    conditions
            );
        }

        long createDefinition(
                String name,
                int priority,
                boolean exclusive,
                List<TriggerCondition> conditions,
                RecordStatusMapping recordStatusMapping
        ) {
            return FlowRecordTriggerAdapterTest.createDefinition(
                    workflow,
                    name,
                    priority,
                    exclusive,
                    conditions,
                    recordStatusMapping
            );
        }
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

    private static final class ScopedServices implements FlowRequestServiceFactory {
        private final Map<Scope, ApprovalWorkflowService> workflows = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong(5000L);

        @Override
        public ApprovalWorkflowService forTenant(long systemId, long tenantId) {
            return workflows.computeIfAbsent(
                    new Scope(systemId, tenantId),
                    ignored -> new ApprovalWorkflowService(
                            new InMemoryApprovalRepository(),
                            new IdService() {
                                @Override
                                public long nextId() {
                                    return sequence.incrementAndGet();
                                }
                            },
                            Clock.fixed(NOW, ZoneOffset.UTC)
                    )
            );
        }
    }

    private record Scope(long systemId, long tenantId) {
    }

    private static final class RecordingRecordFlows implements RuntimeRecordFlowFacade {
        private final List<BindRequest> binds = new ArrayList<>();
        private final List<AdditionalBindRequest> additionalBinds = new ArrayList<>();

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
            additionalBinds.add(request);
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt()
            );
        }

        @Override
        public RecordFlowState transition(TransitionRequest request) {
            throw new AssertionError("Trigger tests do not transition instances");
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
            throw new AssertionError("Trigger start does not use Flow mutation idempotency");
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            throw new AssertionError("Trigger start does not use Flow mutation idempotency");
        }
    }
}
