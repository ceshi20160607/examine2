package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicies;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicy;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalDecisionEvidenceFile;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalQuorumRules;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.ApprovalStageExecution;
import com.unique.examine.flow.domain.ApprovalStartContext;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.domain.PeriodicSchedule;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.VERSION_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalWorkflowServiceTest {
    private ApprovalWorkflowService service;

    @BeforeEach
    void setUp() {
        var sequence = new AtomicLong(100);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        service = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                ids,
                Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void publishesImmutableVersionsAndStartsFromTheSelectedSnapshot() {
        var draft = service.createDraft("Expense approval", 20);
        var versionOne = service.publish(draft.id());

        service.reviseDraft(draft.id(), "Expense approval v2", 30);
        var versionTwo = service.publish(draft.id());

        assertThat(versionOne.version()).isEqualTo(1);
        assertThat(versionOne.name()).isEqualTo("Expense approval");
        assertThat(versionOne.approverId()).isEqualTo(20);
        assertThat(versionTwo.version()).isEqualTo(2);
        assertThat(versionTwo.name()).isEqualTo("Expense approval v2");
        assertThat(versionTwo.approverId()).isEqualTo(30);

        var oldInstance = service.start(draft.id(), 1, "expense-001", 10);
        var latestInstance = service.startLatest(draft.id(), "expense-002", 11);

        assertThat(oldInstance.approverId()).isEqualTo(20);
        assertThat(latestInstance.approverId()).isEqualTo(30);
        assertThat(latestInstance.definitionVersion()).isEqualTo(2);
        assertThat(latestInstance.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(latestInstance.history())
                .extracting(ApprovalHistoryEvent::type)
                .containsExactly(ApprovalHistoryEvent.Type.STARTED);
    }

    @Test
    void pagesImmutableHistoryAndRestoresACompleteSnapshotWithoutActivatingIt() {
        var activation = new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.RECORD_ACTIVATED,
                50,
                true
        );
        var mapping = new RecordStatusMapping(
                "approval_status",
                "101",
                "102",
                "103",
                "104"
        );
        var draft = service.createDraft(
                "Purchase approval v1",
                List.of(20L, 21L),
                activation,
                mapping
        );
        service.publish(draft.id());
        service.reviseDraft(draft.id(), "Purchase approval v2", List.of(30L));
        service.publish(draft.id());
        var versionTwoInstance = service.startLatest(
                draft.id(),
                "purchase-v2-before-restore",
                10
        );

        assertThat(service.definitionVersions(draft.id(), 1, 1).items())
                .extracting(value -> value.version())
                .containsExactly(2);
        assertThat(service.definitionVersions(draft.id(), 2, 1).items())
                .extracting(value -> value.version())
                .containsExactly(1);
        assertThat(service.definitionVersions(draft.id(), 1, 1).total()).isEqualTo(2);

        var restored = service.restoreVersion(draft.id(), 1, 99L);
        assertThat(restored.revision()).isEqualTo(3);
        assertThat(restored.name()).isEqualTo("Purchase approval v1");
        assertThat(restored.approverIds()).containsExactly(20L, 21L);
        assertThat(restored.triggerBinding()).isEqualTo(activation);
        assertThat(restored.recordStatusMapping()).isEqualTo(mapping);

        var stillVersionTwo = service.startLatest(
                draft.id(),
                "purchase-v2-after-restore",
                10
        );
        assertThat(stillVersionTwo.definitionVersion()).isEqualTo(2);
        assertThat(stillVersionTwo.approverId()).isEqualTo(30L);

        var restoredPublication = service.publish(draft.id());
        assertThat(restoredPublication.version()).isEqualTo(3);
        assertThat(restoredPublication.sourceRevision()).isEqualTo(3);
        assertThat(restoredPublication.name()).isEqualTo("Purchase approval v1");
        assertThat(service.startLatest(draft.id(), "purchase-restored", 10)
                .definitionVersion()).isEqualTo(3);
        assertThat(versionTwoInstance.definitionVersion()).isEqualTo(2);
        assertThat(service.definitionVersion(draft.id(), 1).name())
                .isEqualTo("Purchase approval v1");
    }

    @Test
    void restoringPeriodicHistoryFreezesTheRestoringMemberAsRequester() {
        var draft = service.createDraft(
                "Periodic v1",
                List.of(20L),
                TriggerBinding.periodic(new PeriodicSchedule(
                        Instant.parse("2026-08-01T00:00:00Z"),
                        60,
                        88L
                ))
        );
        service.publish(draft.id());
        service.reviseDraft(
                draft.id(),
                "Periodic v2",
                List.of(30L),
                TriggerBinding.periodic(new PeriodicSchedule(
                        Instant.parse("2026-08-02T00:00:00Z"),
                        30,
                        89L
                ))
        );
        service.publish(draft.id());

        var restored = service.restoreVersion(draft.id(), 1, 99L);

        assertThat(restored.triggerBinding().periodicSchedule().startAt())
                .isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(restored.triggerBinding().periodicSchedule().intervalMinutes())
                .isEqualTo(60);
        assertThat(restored.triggerBinding().periodicSchedule().requesterMemberId())
                .isEqualTo(99L);
        assertThat(service.definitionVersion(draft.id(), 1)
                .triggerBinding().periodicSchedule().requesterMemberId()).isEqualTo(88L);
        assertThat(service.definitionVersion(draft.id(), 2)
                .triggerBinding().periodicSchedule().requesterMemberId()).isEqualTo(89L);
    }

    @Test
    void assignedApproverCanApproveAndHistoryIsAppendOnly() {
        var instance = pendingInstance(20);

        var approved = service.approve(instance.id(), 20, "Looks good");

        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.completedAt()).isNotNull();
        assertThat(service.history(instance.id()))
                .extracting(ApprovalHistoryEvent::type)
                .containsExactly(ApprovalHistoryEvent.Type.STARTED, ApprovalHistoryEvent.Type.APPROVED);
        assertThat(service.history(instance.id()).get(1).comment()).isEqualTo("Looks good");
    }

    @Test
    void assignedApproverCanRejectWithAReason() {
        var instance = pendingInstance(20);

        var rejected = service.reject(instance.id(), 20, "Missing receipt");

        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.history().get(1).type()).isEqualTo(ApprovalHistoryEvent.Type.REJECTED);
        assertThat(rejected.history().get(1).comment()).isEqualTo("Missing receipt");
    }

    @Test
    void deniesNonApproversAndRepeatedTerminalDecisionsWithoutChangingHistory() {
        var instance = pendingInstance(20);

        assertThatThrownBy(() -> service.approve(instance.id(), 21, "not mine"))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(APPROVER_FORBIDDEN));
        assertThat(service.instance(instance.id()).status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(service.history(instance.id())).hasSize(1);

        service.approve(instance.id(), 20, "approved");
        assertThatThrownBy(() -> service.reject(instance.id(), 20, "changed my mind"))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID));
        assertThat(service.instance(instance.id()).status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(service.history(instance.id())).hasSize(2);
    }

    @Test
    void cannotStartAnUnpublishedDefinition() {
        var draft = service.createDraft("Unpublished", 20);

        assertThatThrownBy(() -> service.startLatest(draft.id(), "expense-003", 10))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(VERSION_NOT_FOUND));
    }

    @Test
    void returnsStableBoundedPagesForRefreshReads() {
        var first = service.createDraft("First", 20);
        var second = service.createDraft("Second", 21);
        var third = service.createDraft("Third", 22);
        service.publish(first.id());

        var firstInstance = service.startLatest(first.id(), "request-001", 10);
        var secondInstance = service.startLatest(first.id(), "request-002", 11);
        var thirdInstance = service.startLatest(first.id(), "request-003", 12);

        var definitionPageOne = service.definitions(1, 2);
        var definitionPageTwo = service.definitions(2, 2);
        assertThat(definitionPageOne.items())
                .extracting(value -> value.id())
                .containsExactly(third.id(), second.id());
        assertThat(definitionPageTwo.items())
                .extracting(value -> value.id())
                .containsExactly(first.id());
        assertThat(definitionPageOne.total()).isEqualTo(3);

        var instancePageOne = service.instances(1, 2);
        var instancePageTwo = service.instances(2, 2);
        assertThat(instancePageOne.items())
                .extracting(value -> value.id())
                .containsExactly(thirdInstance.id(), secondInstance.id());
        assertThat(instancePageTwo.items())
                .extracting(value -> value.id())
                .containsExactly(firstInstance.id());
        assertThat(instancePageOne.total()).isEqualTo(3);
    }

    @Test
    void filtersInstancesByStatusAndInclusiveExclusiveCompletionRange() {
        var draft = service.createDraft("Filtered approvals", 20);
        service.publish(draft.id());
        var approved = service.startLatest(draft.id(), "approved", 10);
        var rejected = service.startLatest(draft.id(), "rejected", 11);
        var pending = service.startLatest(draft.id(), "pending", 12);
        service.approve(approved.id(), 20, "approved");
        service.reject(rejected.id(), 20, "rejected");
        var completedAt = Instant.parse("2026-07-25T08:00:00Z");

        var included = service.instances(
                ApprovalInstance.Status.APPROVED,
                completedAt,
                completedAt.plusSeconds(1),
                1,
                20);
        var excludedAtUpperBound = service.instances(
                ApprovalInstance.Status.APPROVED,
                completedAt.minusSeconds(1),
                completedAt,
                1,
                20);
        var pendingWithoutCompletionRange = service.instances(
                ApprovalInstance.Status.PENDING,
                null,
                null,
                1,
                20);

        assertThat(included.items())
                .extracting(ApprovalInstance::id)
                .containsExactly(approved.id());
        assertThat(included.total()).isOne();
        assertThat(excludedAtUpperBound.items()).isEmpty();
        assertThat(excludedAtUpperBound.total()).isZero();
        assertThat(pendingWithoutCompletionRange.items())
                .extracting(ApprovalInstance::id)
                .containsExactly(pending.id());
        assertThatThrownBy(() -> service.instances(
                null, completedAt, completedAt, 1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before to");
    }

    @Test
    void returnsOnlyTheApproversTasksWithStatusFiltersAndStablePages() {
        var ownDraft = service.createDraft("Own approvals", 20);
        service.publish(ownDraft.id());
        var first = service.startLatest(ownDraft.id(), "own-001", 10);
        var second = service.startLatest(ownDraft.id(), "own-002", 11);
        var third = service.startLatest(ownDraft.id(), "own-003", 12);

        var otherDraft = service.createDraft("Other approvals", 21);
        service.publish(otherDraft.id());
        service.startLatest(otherDraft.id(), "other-001", 13);

        service.approve(first.id(), 20, "approved");
        service.reject(second.id(), 20, "rejected");

        var pending = service.approvalTasks(20, ApprovalTaskStatus.PENDING, 1, 20);
        assertThat(pending.items()).extracting(ApprovalInstance::id).containsExactly(third.id());
        assertThat(pending.total()).isOne();

        var completedPageOne = service.approvalTasks(20, ApprovalTaskStatus.COMPLETED, 1, 1);
        var completedPageTwo = service.approvalTasks(20, ApprovalTaskStatus.COMPLETED, 2, 1);
        assertThat(completedPageOne.items()).extracting(ApprovalInstance::id).containsExactly(second.id());
        assertThat(completedPageTwo.items()).extracting(ApprovalInstance::id).containsExactly(first.id());
        assertThat(completedPageOne.total()).isEqualTo(2);

        var all = service.approvalTasks(20, ApprovalTaskStatus.ALL, 1, 20);
        assertThat(all.items()).extracting(ApprovalInstance::id)
                .containsExactly(third.id(), second.id(), first.id());
        assertThat(all.total()).isEqualTo(3);
    }

    @Test
    void advancesASequentialSnapshotAndHandsOffThePendingTask() {
        var draft = service.createDraft("Sequential approval", List.of(20L, 30L, 40L));
        var published = service.publish(draft.id());
        service.reviseDraft(draft.id(), "Future sequence", List.of(50L));

        assertThat(published.approverIds()).containsExactly(20L, 30L, 40L);
        var instance = service.start(draft.id(), published.version(), "sequential-001", 10);
        assertThat(instance.approverId()).isEqualTo(20);
        assertThat(instance.approverIds()).containsExactly(20L, 30L, 40L);

        var afterFirst = service.approve(instance.id(), 20, "step one");
        assertThat(afterFirst.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(afterFirst.approverId()).isEqualTo(30);
        assertThat(afterFirst.completedAt()).isNull();
        assertThat(afterFirst.history().get(1).toStatus())
                .isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(service.approvalTasks(20, ApprovalTaskStatus.PENDING, 1, 20).items())
                .isEmpty();
        assertThat(service.approvalTasks(30, ApprovalTaskStatus.PENDING, 1, 20).items())
                .extracting(ApprovalInstance::id)
                .containsExactly(instance.id());

        var afterSecond = service.approve(instance.id(), 30, "step two");
        assertThat(afterSecond.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(afterSecond.approverId()).isEqualTo(40);

        var completed = service.approve(instance.id(), 40, "step three");
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(completed.approverId()).isEqualTo(40);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.history()).hasSize(4);

        var rejectedInstance = service.start(
                draft.id(), published.version(), "sequential-002", 11);
        service.approve(rejectedInstance.id(), 20, "step one");
        var rejected = service.reject(rejectedInstance.id(), 30, "stop here");
        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.completedAt()).isNotNull();
        assertThat(rejected.history()).hasSize(3);
    }

    @Test
    void advancesOrderedStagesAndUsesTheActualProxyActorForPreviousHandler() {
        var stageZero = stage("manager", "Manager", List.of(20L), ApprovalApproverSource.fixed());
        var stageOne = stage("finance", "Finance", List.of(30L), ApprovalApproverSource.fixed());
        var stageTwo = stage(
                "confirmation",
                "Confirmation",
                List.of(),
                ApprovalApproverSource.previousHandler()
        );
        var draft = service.createDraft(
                "Three stages",
                stageZero.approverIds(),
                null,
                null,
                null,
                stageZero.approvalMode(),
                null,
                null,
                new ApprovalApproverSources(stageZero.approverSource(), java.util.Map.of()),
                new ApprovalQuorumRules(stageZero.quorumRule(), java.util.Map.of()),
                new ApprovalDeadlinePolicies(stageZero.deadlinePolicy(), java.util.Map.of()),
                new ApprovalDecisionCommentPolicies(
                        stageZero.decisionCommentPolicy(), java.util.Map.of()),
                List.of(stageZero, stageOne, stageTwo)
        );
        var version = service.publish(draft.id());
        service.reviseDraft(draft.id(), "Temporary legacy route", List.of(99L));
        var restored = service.restoreVersion(draft.id(), version.version(), 10L);
        assertThat(restored.approvalStages())
                .extracting(ApprovalStage::code)
                .containsExactly("manager", "finance", "confirmation");
        assertThat(restored.approverIds()).containsExactly(20L);
        var instance = service.start(
                draft.id(), version.version(), "stages-001", 10L);

        assertThat(instance.currentStageIndex()).isZero();
        assertThat(instance.stages())
                .extracting(ApprovalStageExecution::status)
                .containsExactly(
                        ApprovalStageExecution.Status.ACTIVE,
                        ApprovalStageExecution.Status.WAITING,
                        ApprovalStageExecution.Status.WAITING
                );

        var finance = service.approve(instance.id(), 20L, "manager approved");
        assertThat(finance.id()).isEqualTo(instance.id());
        assertThat(finance.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(finance.currentStageIndex()).isEqualTo(1);
        assertThat(finance.approverIds()).containsExactly(30L);
        assertThat(finance.stages().get(0).actualHandlerIds()).containsExactly(20L);

        var now = Instant.parse("2026-07-25T08:00:00Z");
        service.createDelegation(
                1L,
                30L,
                false,
                30L,
                40L,
                now.minusSeconds(60),
                now.plusSeconds(3600),
                draft.id()
        );
        var confirmation = service.approve(
                instance.id(), 40L, 30L, "proxy approved");

        assertThat(confirmation.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(confirmation.currentStageIndex()).isEqualTo(2);
        assertThat(confirmation.approverIds()).containsExactly(40L);
        assertThat(confirmation.stages().get(1).actualHandlerIds())
                .containsExactly(40L);
        assertThat(confirmation.stages().get(2).approverIds())
                .containsExactly(40L);

        var completed = service.approve(
                instance.id(), 40L, "confirmed");
        assertThat(completed.id()).isEqualTo(instance.id());
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(completed.currentStageIndex()).isEqualTo(2);
        assertThat(completed.stages())
                .extracting(ApprovalStageExecution::status)
                .containsExactly(
                        ApprovalStageExecution.Status.APPROVED,
                        ApprovalStageExecution.Status.APPROVED,
                        ApprovalStageExecution.Status.APPROVED
                );
    }

    @Test
    void doesNotPersistTheCompletingDecisionWhenStageActivationFails() {
        var stageZero = stage("first", "First", List.of(20L), ApprovalApproverSource.fixed());
        var stageOne = stage("second", "Second", List.of(30L), ApprovalApproverSource.fixed());
        var draft = service.createDraft(
                "Activation rollback",
                stageZero.approverIds(),
                null,
                null,
                null,
                stageZero.approvalMode(),
                null,
                null,
                new ApprovalApproverSources(stageZero.approverSource(), java.util.Map.of()),
                ApprovalQuorumRules.none(),
                ApprovalDeadlinePolicies.none(),
                ApprovalDecisionCommentPolicies.none(),
                List.of(stageZero, stageOne)
        );
        service.publish(draft.id());
        var instance = service.startLatest(draft.id(), "stages-rollback", 10L);

        assertThatThrownBy(() -> service.approve(
                instance.id(),
                20L,
                null,
                "complete stage",
                (nextStage, completedStage) -> {
                    throw new IllegalStateException("member became inactive");
                }
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("member became inactive");

        var unchanged = service.instance(instance.id());
        assertThat(unchanged.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(unchanged.currentStageIndex()).isZero();
        assertThat(unchanged.history()).hasSize(1);
        assertThat(unchanged.stages().get(0).status())
                .isEqualTo(ApprovalStageExecution.Status.ACTIVE);
        assertThat(unchanged.stages().get(1).status())
                .isEqualTo(ApprovalStageExecution.Status.WAITING);
    }

    @Test
    void deadlineAutoApprovalActivatesTheNextFixedStage() {
        var autoApprove = new ApprovalDeadlinePolicy(
                1,
                null,
                ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE
        );
        var stageZero = new ApprovalStage(
                "timed",
                "Timed",
                List.of(20L),
                ApprovalMode.SEQUENTIAL,
                ApprovalApproverSource.fixed(),
                null,
                autoApprove,
                null
        );
        var stageOne = stage(
                "manual", "Manual", List.of(30L), ApprovalApproverSource.fixed());
        var draft = service.createDraft(
                "Timed stages",
                stageZero.approverIds(),
                null,
                null,
                null,
                stageZero.approvalMode(),
                null,
                null,
                new ApprovalApproverSources(stageZero.approverSource(), java.util.Map.of()),
                ApprovalQuorumRules.none(),
                new ApprovalDeadlinePolicies(autoApprove, java.util.Map.of()),
                ApprovalDecisionCommentPolicies.none(),
                List.of(stageZero, stageOne)
        );
        service.publish(draft.id());
        var started = service.startLatest(draft.id(), "timed-stages", 10L);

        var advanced = service.processDeadline(
                started.id(),
                null,
                started.requesterId(),
                started.deadline().dueAt(),
                (nextStage, completedStage) -> nextStage.approverIds()
        );

        assertThat(advanced.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(advanced.currentStageIndex()).isEqualTo(1);
        assertThat(advanced.approverIds()).containsExactly(30L);
        assertThat(advanced.stages().get(0).status())
                .isEqualTo(ApprovalStageExecution.Status.APPROVED);
        assertThat(advanced.stages().get(0).actualHandlerIds()).isEmpty();
        assertThat(advanced.stages().get(1).status())
                .isEqualTo(ApprovalStageExecution.Status.ACTIVE);
    }

    @Test
    void rejectsInvalidApproverSequences() {
        assertThatThrownBy(() -> service.createDraft("Empty", List.of()))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID));
        assertThatThrownBy(() -> service.createDraft("Non-positive", List.of(20L, 0L)))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID));
        assertThatThrownBy(() -> service.createDraft(
                "Oversized",
                java.util.stream.LongStream.rangeClosed(1, 11).boxed().toList()))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID));
    }

    @Test
    void advancesOnlyTheApprovedBranchAndPassesItsImmutableStartContext() {
        var review = stage(
                "review", "Review", List.of(20L),
                ApprovalApproverSource.fixed());
        var confirm = stage(
                "confirm", "Confirm", List.of(),
                ApprovalApproverSource.previousHandler());
        var parallel = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance", "Finance", List.of(20L),
                        ApprovalMode.SEQUENTIAL, List.of(review, confirm)),
                new ApprovalParallelGateway.Branch(
                        "owner", "Owner", List.of(30L),
                        ApprovalMode.SEQUENTIAL)
        ));
        var sources = new ApprovalApproverSources(
                ApprovalApproverSource.fixed(),
                Map.of(
                        "finance", ApprovalApproverSource.fixed(),
                        "owner", ApprovalApproverSource.fixed()
                )
        );
        var draft = service.createDraft(
                "Branch stages", List.of(20L), null, null, null,
                ApprovalMode.SEQUENTIAL, parallel, null, sources,
                ApprovalQuorumRules.none(), ApprovalDeadlinePolicies.none(),
                ApprovalDecisionCommentPolicies.none(), null);
        var published = service.publish(draft.id());
        var context = new ApprovalStartContext(
                10L, null, null, Map.of("amount", "100"));
        var started = service.startBranches(
                draft.id(), published.version(),
                published.parallelGateway().branches(),
                "branch-stages", 10L, null, context);

        var advanced = service.approveBranch(
                started.id(), "finance", 20L, null, null,
                (branchCode, nextStage, completedStage, startContext) -> {
                    assertThat(branchCode).isEqualTo("finance");
                    assertThat(startContext).isEqualTo(context);
                    return completedStage.actualHandlerIds();
                });

        assertThat(advanced.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(advanced.parallelBranch("finance").currentStageIndex())
                .isEqualTo(1);
        assertThat(advanced.parallelBranch("finance").status().name())
                .isEqualTo("PENDING");
        assertThat(advanced.parallelBranch("owner").currentStageIndex())
                .isZero();
        assertThat(advanced.parallelBranch("owner").activeApproverIds())
                .containsExactly(30L);
    }

    @Test
    void rejectsUnboundedOrInvalidRefreshPages() {
        assertThatThrownBy(() -> service.definitions(0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be at least 1");
        assertThatThrownBy(() -> service.instances(1, ApprovalWorkflowService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and " + ApprovalWorkflowService.MAX_PAGE_SIZE);
        assertThatThrownBy(() -> service.approvalTasks(
                20, ApprovalTaskStatus.PENDING, 1, ApprovalWorkflowService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must be between 1 and " + ApprovalWorkflowService.MAX_PAGE_SIZE);
    }

    @Test
    void managesTemplatesAndPersistsOneImmutableEvidenceSnapshot() {
        var template = service.createDecisionCommentTemplate(
                "Approved", "Approved from template", 20L);
        var revised = service.reviseDecisionCommentTemplate(
                template.id(), "Approved v2", "Approved with evidence", 20L);
        assertThat(revised.currentVersion()).isEqualTo(2);
        assertThat(service.decisionCommentTemplates(false, 1, 20).items())
                .containsExactly(revised);

        var inactive = service.deactivateDecisionCommentTemplate(
                template.id(), 20L);
        assertThat(inactive.status())
                .isEqualTo(ApprovalDecisionCommentTemplate.Status.INACTIVE);
        assertThat(service.decisionCommentTemplates(true, 1, 20).items())
                .isEmpty();
        service.activateDecisionCommentTemplate(template.id(), 20L);

        var policy = new ApprovalDecisionEvidencePolicy(
                1,
                2,
                Set.of(ApprovalDecisionEvidencePolicy.MimeFamily.PDF),
                ApprovalDecisionEvidencePolicy.SignatureMode.REQUIRED
        );
        var draft = service.createDraft(
                "Evidence approval",
                List.of(20L),
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                ApprovalApproverSources.fixedFor(null, null, null),
                ApprovalQuorumRules.none(),
                ApprovalDeadlinePolicies.none(),
                ApprovalDecisionCommentPolicies.none(),
                null,
                new ApprovalDecisionEvidencePolicies(policy, Map.of())
        );
        service.publish(draft.id());
        var started = service.startLatest(
                draft.id(), "evidence-001", 10L);
        var file = new ApprovalDecisionEvidenceFile(
                900L,
                "approval.pdf",
                "application/pdf",
                123L,
                "a".repeat(64)
        );

        var result = service.approveWithEvidence(
                started.id(),
                20L,
                null,
                null,
                new ApprovalWorkflowService.DecisionEvidenceInput(
                        List.of(file),
                        ApprovalDecisionEvidence.Signature.typed(
                                "Jane Approver"),
                        template.id()
                ),
                (next, completed, context) -> next.approverIds()
        );

        assertThat(result.instance().status())
                .isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(result.evidence()).isNotNull();
        assertThat(result.evidence().template())
                .extracting(
                        ApprovalDecisionEvidence.TemplateSelection::version,
                        ApprovalDecisionEvidence.TemplateSelection::name)
                .containsExactly(2, "Approved v2");
        assertThat(result.instance().history().getLast().comment())
                .isEqualTo("Approved with evidence");
        assertThat(result.instance().history().getLast().evidence())
                .isEqualTo(result.evidence());
        assertThat(service.history(started.id()).getLast().evidence())
                .isEqualTo(result.evidence());
    }

    private ApprovalInstance pendingInstance(long approverId) {
        var draft = service.createDraft("Single approver", approverId);
        service.publish(draft.id());
        return service.startLatest(draft.id(), "request-001", 10);
    }

    private static ApprovalStage stage(
            String code,
            String name,
            List<Long> memberIds,
            ApprovalApproverSource source
    ) {
        return new ApprovalStage(
                code,
                name,
                memberIds,
                ApprovalMode.SEQUENTIAL,
                source,
                null,
                null,
                null
        );
    }
}
