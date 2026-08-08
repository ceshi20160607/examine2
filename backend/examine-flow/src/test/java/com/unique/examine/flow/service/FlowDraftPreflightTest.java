package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.core.runtime.RuntimeRecordMemberFieldFacade;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.PeriodicSchedule;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.domain.TriggerCondition;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowDraftPreflightTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Set<Long> activeMemberIds = ConcurrentHashMap.newKeySet();
    private InMemoryApprovalRepository repository;
    private ApprovalWorkflowService workflow;
    private FlowMutationService mutations;
    private FlowSession session;

    @BeforeEach
    void setUp() {
        var ids = new AtomicLong(100);
        repository = new InMemoryApprovalRepository();
        workflow = new ApprovalWorkflowService(
                repository,
                new IdService() {
                    @Override
                    public long nextId() {
                        return ids.incrementAndGet();
                    }
                },
                Clock.fixed(Instant.parse("2026-07-29T08:00:00Z"), ZoneOffset.UTC)
        );
        FlowRequestServiceFactory services = (systemId, tenantId) -> workflow;
        RuntimeActiveMemberFacade activeMembers = (systemId, tenantId, memberId) ->
                activeMemberIds.contains(memberId)
                        ? Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null))
                        : Optional.empty();
        mutations = new FlowMutationService(
                services,
                new NoopIdempotency(),
                json,
                activeMembers
        );
        session = new FlowSession(99, 1, 2, 10, Set.of("flow.definition.manage"));
        activeMemberIds.addAll(Set.of(10L, 20L, 30L));
    }

    @Test
    void reportsStableWarningsAndSimulatesManualRouteWithoutWritingRuntimeState() {
        var draft = workflow.createDraft("Repeated approver route", List.of(20L, 20L, 30L));
        var draftsBefore = repository.countDrafts();
        var versionsBefore = repository.countVersions(draft.id());
        var instancesBefore = repository.countInstances();

        var check = mutations.checkDraft(session, draft.id());
        var simulation = mutations.simulateDraft(session, draft.id(), null);

        assertThat(check.verdict()).isEqualTo("READY");
        assertThat(check.blockerCount()).isZero();
        assertThat(check.warningCount()).isOne();
        assertThat(check.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo("WARNING");
            assertThat(issue.code()).isEqualTo("APPROVER_REPEATED");
            assertThat(issue.path()).isEqualTo("/approverIds/1");
        });
        assertThat(simulation.startable()).isTrue();
        assertThat(simulation.reason()).isEqualTo("MANUAL_START");
        assertThat(simulation.requesterId()).isEqualTo("10");
        assertThat(simulation.businessKey()).isEqualTo("simulation:" + draft.id() + ":r1");
        assertThat(simulation.steps())
                .extracting(step -> step.approverId())
                .containsExactly("20", "20", "30");
        assertThat(simulation.steps().getFirst().initial()).isTrue();
        assertThat(simulation.trigger().configured()).isFalse();
        assertThat(repository.countDrafts()).isEqualTo(draftsBefore);
        assertThat(repository.countVersions(draft.id())).isEqualTo(versionsBefore);
        assertThat(repository.countInstances()).isEqualTo(instancesBefore);
    }

    @Test
    void blocksSimulationAndPublicationUntilEveryApproverIsCurrentlyActive() {
        var draft = workflow.createDraft("Current member check", List.of(20L, 30L));
        activeMemberIds.remove(30L);

        var check = mutations.checkDraft(session, draft.id());
        var simulation = mutations.simulateDraft(
                session,
                draft.id(),
                new FlowRequests.SimulateDefinition("10", "blocked-preview", null)
        );
        var blocked = catchThrowableOfType(
                () -> mutations.publish(session, draft.id()),
                BusinessException.class
        );

        assertThat(check.verdict()).isEqualTo("BLOCKED");
        assertThat(check.blockerCount()).isOne();
        assertThat(check.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("APPROVER_INACTIVE");
            assertThat(issue.path()).isEqualTo("/approverIds/1");
        });
        assertThat(simulation.startable()).isFalse();
        assertThat(simulation.reason()).isEqualTo("DRAFT_BLOCKED");
        assertThat(blocked.code()).isEqualTo("FLOW_DRAFT_CHECK_BLOCKED");
        assertThat(repository.countVersions(draft.id())).isZero();

        activeMemberIds.add(30L);
        assertThat(mutations.checkDraft(session, draft.id()).verdict()).isEqualTo("READY");
        assertThat(mutations.publish(session, draft.id()).version()).isOne();
    }

    @Test
    void previewsQuorumRequiredCountAndBlocksAnUnsatisfiableCount() {
        var ready = mutations.createDefinition(
                session,
                new FlowRequests.CreateDefinition(
                        "Percentage quorum",
                        null,
                        List.of("20", "30"),
                        null,
                        null,
                        null,
                        "QUORUM",
                        null,
                        null,
                        null,
                        new FlowRequests.QuorumRule("PERCENTAGE", 51)
                )
        );

        var simulation = mutations.simulateDraft(
                session,
                Long.parseLong(ready.definitionId()),
                null
        );
        assertThat(simulation.startable()).isTrue();
        assertThat(simulation.route().approvalMode()).isEqualTo("QUORUM");
        assertThat(simulation.route().requiredApprovals()).isEqualTo(2);

        var blocked = mutations.createDefinition(
                session,
                new FlowRequests.CreateDefinition(
                        "Impossible count quorum",
                        null,
                        List.of("20", "30"),
                        null,
                        null,
                        null,
                        "QUORUM",
                        null,
                        null,
                        null,
                        new FlowRequests.QuorumRule("COUNT", 3)
                )
        );
        var check = mutations.checkDraft(session, Long.parseLong(blocked.definitionId()));
        assertThat(check.verdict()).isEqualTo("BLOCKED");
        assertThat(check.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("QUORUM_RULE_UNSATISFIABLE");
            assertThat(issue.path()).isEqualTo("/quorumRule/value");
        });
    }

    @Test
    void resolvesCurrentRoleMembersAndKeepsTheStartedInstanceSnapshotImmutable() {
        var roleMembers = new AtomicReference<>(List.of(20L, 30L));
        RuntimeApproverDirectoryFacade directory = new RuntimeApproverDirectoryFacade() {
            @Override
            public Resolution resolveRoleMembers(long systemId, long tenantId, long roleId) {
                return roleId == 7
                        ? Resolution.active(roleMembers.get())
                        : Resolution.missing();
            }

            @Override
            public Resolution resolveDepartmentMembers(
                    long systemId,
                    long tenantId,
                    long departmentId
            ) {
                return Resolution.missing();
            }
        };
        var dynamicMutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                new NoopIdempotency(),
                json,
                (systemId, tenantId, memberId) -> activeMemberIds.contains(memberId)
                        ? Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null))
                        : Optional.empty(),
                directory
        );
        activeMemberIds.add(40L);
        var draft = workflow.createDraft(
                "Role sourced route",
                List.of(20L),
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                new ApprovalApproverSources(
                        ApprovalApproverSource.role(7),
                        Map.of()
                )
        );

        assertThat(dynamicMutations.checkDraft(session, draft.id()).verdict())
                .isEqualTo("READY");
        assertThat(dynamicMutations.simulateDraft(session, draft.id(), null).steps())
                .extracting(step -> step.approverId())
                .containsExactly("20", "30");
        dynamicMutations.publish(session, draft.id());

        roleMembers.set(List.of(30L, 40L));
        var started = dynamicMutations.start(
                session,
                draft.id(),
                new FlowRequests.StartInstance(null, "role-snapshot")
        );

        assertThat(started.approverIds()).containsExactly("30", "40");
        roleMembers.set(List.of());
        assertThat(workflow.instance(Long.parseLong(started.instanceId())).approverIds())
                .containsExactly(30L, 40L);
        assertThat(dynamicMutations.checkDraft(session, draft.id()).issues())
                .singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("APPROVER_SOURCE_EMPTY"));
    }

    @Test
    void resolvesOrganizationLeaderAndRequesterManagerFromTheCurrentStartContext() {
        var departmentLeader = new AtomicReference<>(30L);
        var requesterManagers = new ConcurrentHashMap<Long, Long>();
        requesterManagers.put(10L, 20L);
        requesterManagers.put(30L, 40L);
        RuntimeApproverDirectoryFacade directory = organizationDirectory(
                departmentLeader,
                requesterManagers
        );
        var dynamicMutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                new NoopIdempotency(),
                json,
                (systemId, tenantId, memberId) -> activeMemberIds.contains(memberId)
                        ? Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null))
                        : Optional.empty(),
                directory
        );
        activeMemberIds.add(40L);
        var leaderDraft = workflow.createDraft(
                "Department leader route",
                List.of(20L),
                null,
                null,
                null,
                ApprovalMode.ALL,
                null,
                null,
                new ApprovalApproverSources(
                        ApprovalApproverSource.departmentLeader(7),
                        Map.of()
                )
        );
        var managerDraft = workflow.createDraft(
                "Requester manager route",
                List.of(10L),
                null,
                null,
                null,
                ApprovalMode.ANY,
                null,
                null,
                new ApprovalApproverSources(
                        ApprovalApproverSource.requesterManager(),
                        Map.of()
                )
        );

        assertThat(dynamicMutations.checkDraft(session, leaderDraft.id()).verdict())
                .isEqualTo("READY");
        assertThat(dynamicMutations.checkDraft(session, managerDraft.id()).verdict())
                .isEqualTo("READY");
        assertThat(dynamicMutations.simulateDraft(
                session,
                managerDraft.id(),
                new FlowRequests.SimulateDefinition("30", null, null)
        ).steps()).extracting(step -> step.approverId()).containsExactly("40");
        dynamicMutations.publish(session, leaderDraft.id());
        dynamicMutations.publish(session, managerDraft.id());

        var firstLeader = dynamicMutations.start(
                session,
                leaderDraft.id(),
                new FlowRequests.StartInstance(null, "leader-first")
        );
        var firstManager = dynamicMutations.start(
                session,
                managerDraft.id(),
                new FlowRequests.StartInstance(null, "manager-first")
        );
        assertThat(firstLeader.approverIds()).containsExactly("30");
        assertThat(firstManager.approverIds()).containsExactly("20");

        departmentLeader.set(40L);
        requesterManagers.put(10L, 30L);
        var secondLeader = dynamicMutations.start(
                session,
                leaderDraft.id(),
                new FlowRequests.StartInstance(null, "leader-second")
        );
        var secondManager = dynamicMutations.start(
                session,
                managerDraft.id(),
                new FlowRequests.StartInstance(null, "manager-second")
        );

        assertThat(secondLeader.approverIds()).containsExactly("40");
        assertThat(secondManager.approverIds()).containsExactly("30");
        assertThat(workflow.instance(Long.parseLong(firstLeader.instanceId())).approverIds())
                .containsExactly(30L);
        assertThat(workflow.instance(Long.parseLong(firstManager.instanceId())).approverIds())
                .containsExactly(20L);
    }

    @Test
    void requesterManagerAvailabilityIsContextualAndPeriodicDraftsRejectIt() {
        var requesterManagers = new ConcurrentHashMap<Long, Long>();
        var dynamicMutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                new NoopIdempotency(),
                json,
                (systemId, tenantId, memberId) -> activeMemberIds.contains(memberId)
                        ? Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null))
                        : Optional.empty(),
                organizationDirectory(new AtomicReference<>(30L), requesterManagers)
        );
        var managerDraft = workflow.createDraft(
                "Contextual manager",
                List.of(10L),
                null,
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
        var periodicDraft = workflow.createDraft(
                "Invalid periodic manager",
                List.of(10L),
                TriggerBinding.periodic(new PeriodicSchedule(
                        Instant.parse("2026-07-30T08:00:00Z"),
                        60,
                        10
                )),
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

        assertThat(dynamicMutations.checkDraft(session, managerDraft.id()).verdict())
                .isEqualTo("READY");
        var simulation = dynamicMutations.simulateDraft(session, managerDraft.id(), null);
        assertThat(simulation.startable()).isFalse();
        assertThat(simulation.reason()).isEqualTo("DRAFT_BLOCKED");
        assertThat(simulation.check().issues())
                .extracting(issue -> issue.code())
                .containsExactly("REQUESTER_MANAGER_UNAVAILABLE");
        var periodicCheck = dynamicMutations.checkDraft(session, periodicDraft.id());
        assertThat(periodicCheck.verdict()).isEqualTo("BLOCKED");
        assertThat(periodicCheck.issues())
                .extracting(issue -> issue.code())
                .containsExactly("PERIODIC_REQUESTER_CONTEXT_UNAVAILABLE");

        dynamicMutations.publish(session, managerDraft.id());
        var instancesBefore = repository.countInstances();
        var failed = catchThrowableOfType(
                () -> dynamicMutations.start(
                        session,
                        managerDraft.id(),
                        new FlowRequests.StartInstance(null, "manager-missing")
                ),
                BusinessException.class
        );
        assertThat(failed.code()).isEqualTo("FLOW_APPROVER_SOURCE_EMPTY");
        assertThat(repository.countInstances()).isEqualTo(instancesBefore);
    }

    @Test
    void recordMemberFieldUsesCurrentSnapshotAndSimulationContextsBeforeSnapshotting() {
        var currentMember = new AtomicLong(20);
        var accessCalls = new AtomicLong();
        var recordMembers = recordMembers(currentMember);
        var dynamicMutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                new NoopIdempotency(),
                json,
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)),
                organizationDirectory(new AtomicReference<>(30L), Map.of()),
                recordFlows(),
                noOperationAudit(),
                recordMembers,
                request -> {
                    accessCalls.incrementAndGet();
                    return new RuntimeRecordAccessFacade.RuntimeRecordAccess(
                            Long.toString(request.recordId()), 1, false);
                }
        );
        var source = ApprovalApproverSource.recordMemberField("work_order", 42);
        var draft = workflow.createDraft(
                "Record owner route",
                List.of(10L),
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                new ApprovalApproverSources(source, Map.of())
        );

        var simulation = dynamicMutations.simulateDraft(
                session,
                draft.id(),
                new FlowRequests.SimulateDefinition(
                        "10",
                        "preview",
                        null,
                        Map.of("owner", json.valueToTree("50"))
                )
        );
        assertThat(simulation.startable()).isTrue();
        assertThat(simulation.steps())
                .extracting(step -> step.approverId())
                .containsExactly("50");

        dynamicMutations.publish(session, draft.id());
        var first = dynamicMutations.start(
                session,
                draft.id(),
                new FlowRequests.StartInstance(
                        null,
                        "manual-current-1",
                        new FlowRequests.RecordBinding("work_order", "501")
                )
        );
        currentMember.set(30);
        var second = dynamicMutations.start(
                session,
                draft.id(),
                new FlowRequests.StartInstance(
                        null,
                        "manual-current-2",
                        new FlowRequests.RecordBinding("work_order", "501"),
                        Map.of("owner", json.valueToTree("999"))
                )
        );
        var event = dynamicMutations.startTriggered(
                session,
                draft.id(),
                new FlowRequests.StartInstance(
                        null,
                        "event-snapshot",
                        new FlowRequests.RecordBinding("work_order", "501"),
                        Map.of("owner", json.valueToTree("40"))
                )
        );

        assertThat(first.approverIds()).containsExactly("20");
        assertThat(second.approverIds()).containsExactly("30");
        assertThat(event.approverIds()).containsExactly("40");
        assertThat(workflow.instance(Long.parseLong(first.instanceId())).approverIds())
                .containsExactly(20L);
        assertThat(accessCalls.get()).isEqualTo(2);
    }

    @Test
    void periodicRecordMemberFieldIsBlockedWithoutRecordContext() {
        var dynamicMutations = new FlowMutationService(
                (systemId, tenantId) -> workflow,
                new NoopIdempotency(),
                json,
                (systemId, tenantId, memberId) -> Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)),
                organizationDirectory(new AtomicReference<>(30L), Map.of()),
                recordFlows(),
                noOperationAudit(),
                recordMembers(new AtomicLong(20))
        );
        var draft = workflow.createDraft(
                "Periodic record owner",
                List.of(10L),
                TriggerBinding.periodic(new PeriodicSchedule(
                        Instant.parse("2026-07-30T08:00:00Z"), 60, 10)),
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                new ApprovalApproverSources(
                        ApprovalApproverSource.recordMemberField("work_order", 42),
                        Map.of()
                )
        );

        assertThat(dynamicMutations.checkDraft(session, draft.id()).issues())
                .extracting(issue -> issue.code())
                .contains("PERIODIC_RECORD_CONTEXT_UNAVAILABLE");
    }

    @Test
    void evaluatesTriggerConditionsAndReturnsTerminalRecordEffects() {
        var binding = new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.RECORD_ACTIVATED,
                80,
                true,
                List.of(new TriggerCondition(
                        "amount",
                        TriggerCondition.Operator.GTE,
                        "100"
                ))
        );
        var mapping = new RecordStatusMapping(
                "approval_status",
                "101",
                "102",
                "103",
                "104"
        );
        var draft = workflow.createDraft("Triggered route", List.of(20L, 30L), binding, mapping);

        var matched = mutations.simulateDraft(
                session,
                draft.id(),
                new FlowRequests.SimulateDefinition(
                        "10",
                        "PO-2026-001",
                        new FlowRequests.TriggerSample(
                                "purchase_order",
                                "RECORD_ACTIVATED",
                                Map.of("amount", json.valueToTree(150))
                        )
                )
        );
        var missed = mutations.simulateDraft(
                session,
                draft.id(),
                new FlowRequests.SimulateDefinition(
                        null,
                        null,
                        new FlowRequests.TriggerSample(
                                "purchase_order",
                                "RECORD_ACTIVATED",
                                Map.of("amount", json.valueToTree(50))
                        )
                )
        );

        assertThat(matched.startable()).isTrue();
        assertThat(matched.reason()).isEqualTo("TRIGGER_MATCHED");
        assertThat(matched.trigger().event()).isEqualTo("RECORD_ACTIVATED");
        assertThat(matched.trigger().moduleCode()).isEqualTo("purchase_order");
        assertThat(matched.statusEffects().fieldCode()).isEqualTo("approval_status");
        assertThat(matched.statusEffects().approvedValue()).isEqualTo("101");
        assertThat(missed.startable()).isFalse();
        assertThat(missed.reason()).isEqualTo("TRIGGER_CONDITIONS_NOT_MATCHED");
    }

    @Test
    void checksPeriodicRequesterAndRejectsInvalidSimulationRequester() {
        var draft = workflow.createDraft(
                "Periodic preflight",
                List.of(20L),
                TriggerBinding.periodic(new PeriodicSchedule(
                        Instant.parse("2026-07-30T08:00:00Z"),
                        60,
                        10
                ))
        );
        activeMemberIds.remove(10L);

        var check = mutations.checkDraft(session, draft.id());
        var invalid = catchThrowableOfType(
                () -> mutations.simulateDraft(
                        session,
                        draft.id(),
                        new FlowRequests.SimulateDefinition("999", null, null)
                ),
                BusinessException.class
        );

        assertThat(check.issues())
                .extracting(issue -> issue.code())
                .containsExactly("PERIODIC_REQUESTER_INACTIVE");
        assertThat(invalid.code()).isEqualTo("FLOW_SIMULATION_REQUEST_INVALID");
    }

    @Test
    void checksEveryGatewayRouteAndSimulationUsesFirstMatchWhileRuntimePinsRoute() {
        activeMemberIds.add(40L);
        var gateway = new ApprovalGateway(List.of(
                branch("urgent", "Urgent", "urgent", "true", 20L),
                branch("large", "Large", "amount", "100", 40L),
                new ApprovalGateway.Branch(
                        "default",
                        "Default",
                        true,
                        List.of(),
                        List.of(30L)
                )
        ));
        var draft = workflow.createDraft(
                "Conditional approval",
                List.of(30L),
                null,
                null,
                gateway
        );

        var simulation = mutations.simulateDraft(
                session,
                draft.id(),
                new FlowRequests.SimulateDefinition(
                        null,
                        "conditional-preview",
                        new FlowRequests.TriggerSample(
                                null,
                                null,
                                Map.of(
                                        "urgent", json.valueToTree(true),
                                        "amount", json.valueToTree(100)
                                )
                        )
                )
        );

        assertThat(simulation.route().configured()).isTrue();
        assertThat(simulation.route().branchCode()).isEqualTo("urgent");
        assertThat(simulation.route().defaultBranch()).isFalse();
        assertThat(simulation.steps())
                .extracting(step -> step.approverId())
                .containsExactly("20");

        mutations.publish(session, draft.id());
        var selected = mutations.start(
                session,
                draft.id(),
                new FlowRequests.StartInstance(
                        null,
                        "conditional-runtime",
                        null,
                        Map.of("amount", json.valueToTree(100))
                )
        );
        var fallback = mutations.start(
                session,
                draft.id(),
                new FlowRequests.StartInstance(null, "conditional-default")
        );

        assertThat(selected.approverIds()).containsExactly("40");
        assertThat(fallback.approverIds()).containsExactly("30");

        activeMemberIds.remove(40L);
        var check = mutations.checkDraft(session, draft.id());
        assertThat(check.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("APPROVER_INACTIVE");
            assertThat(issue.path()).isEqualTo("/gateway/branches/1/approverIds/0");
        });
    }

    private static ApprovalGateway.Branch branch(
            String code,
            String name,
            String field,
            String value,
            long approverId
    ) {
        return new ApprovalGateway.Branch(
                code,
                name,
                false,
                List.of(new TriggerCondition(field, TriggerCondition.Operator.EQ, value)),
                List.of(approverId)
        );
    }

    private static RuntimeApproverDirectoryFacade organizationDirectory(
            AtomicReference<Long> departmentLeader,
            Map<Long, Long> requesterManagers
    ) {
        return new RuntimeApproverDirectoryFacade() {
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
            public Resolution resolveDepartmentLeader(
                    long systemId,
                    long tenantId,
                    long departmentId
            ) {
                var leader = departmentId == 7 ? departmentLeader.get() : null;
                return departmentId == 7
                        ? Resolution.active(leader == null ? List.of() : List.of(leader))
                        : Resolution.missing();
            }

            @Override
            public Resolution resolveRequesterManager(
                    long systemId,
                    long tenantId,
                    long requesterMemberId
            ) {
                var manager = requesterManagers.get(requesterMemberId);
                return Resolution.active(manager == null ? List.of() : List.of(manager));
            }
        };
    }

    private static RuntimeRecordMemberFieldFacade recordMembers(AtomicLong currentMember) {
        return new RuntimeRecordMemberFieldFacade() {
            @Override
            public Optional<PublishedFieldCatalog> publishedEligibleFields(
                    long systemId,
                    String moduleCode
            ) {
                return Optional.of(new PublishedFieldCatalog(
                        systemId, 1, 2, 3, moduleCode,
                        List.of(new EligibleField(42, "owner", "Owner"))
                ));
            }

            @Override
            public Resolution resolveCurrent(CurrentRecordRequest request) {
                return Resolution.resolved(currentMember.get());
            }

            @Override
            public Resolution resolveSnapshot(SnapshotRequest request) {
                var raw = request.valuesJson().get("owner");
                return raw == null
                        ? Resolution.sourceEmpty()
                        : Resolution.resolved(Long.parseLong(raw.replace("\"", "")));
            }
        };
    }

    private static RuntimeRecordFlowFacade recordFlows() {
        return new RuntimeRecordFlowFacade() {
            @Override
            public RecordFlowState bind(BindRequest request) {
                return new RecordFlowState(
                        request.instanceId(), FlowStatus.PENDING, 0, request.occurredAt());
            }

            @Override
            public RecordFlowState bindAdditional(AdditionalBindRequest request) {
                return new RecordFlowState(
                        request.instanceId(), FlowStatus.PENDING, 0, request.occurredAt());
            }

            @Override
            public RecordFlowState transition(TransitionRequest request) {
                return new RecordFlowState(
                        request.instanceId(), request.status(), 1, request.occurredAt());
            }
        };
    }

    private static OperationAuditFacade noOperationAudit() {
        return new OperationAuditFacade() {
            @Override
            public void recordSuccess(OperationAudit audit) {
            }

            @Override
            public void recordDenied(OperationAudit audit) {
            }

            @Override
            public void recordFailed(OperationAudit audit) {
            }
        };
    }

    private static final class NoopIdempotency implements IdempotencyFacade {
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
            return 1;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
        }
    }
}
