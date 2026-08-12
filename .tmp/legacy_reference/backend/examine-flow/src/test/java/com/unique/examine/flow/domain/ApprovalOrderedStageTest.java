package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalOrderedStageTest {
    private static final Instant PUBLISHED =
            Instant.parse("2026-07-31T01:00:00Z");
    private static final Instant STARTED = PUBLISHED.plusSeconds(60);

    @Test
    void validatesFrozenStageShapeAndPreservesLegacyAbsence() {
        var legacy = new ApprovalDefinitionVersion(
                101L, 1, "Legacy", List.of(11L), 1, PUBLISHED);
        assertThat(legacy.approvalStages()).isNull();

        assertThatThrownBy(() -> definition(List.of(fixed("only", 11L))))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID);

        assertThatThrownBy(() -> definition(List.of(
                previous("first"),
                fixed("second", 12L)
        )))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID);

        assertThat(definition(List.of(
                fixed("first", 11L),
                new ApprovalStage(
                        "dynamic", "Dynamic", List.of(), ApprovalMode.SEQUENTIAL,
                        ApprovalApproverSource.role(7L), null, null, null)
        )).approvalStages().get(1).approverSource().kind())
                .isEqualTo(ApprovalApproverSource.Kind.ROLE);

        var autoApprove = new ApprovalDeadlinePolicy(
                60, null, ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE);
        assertThatThrownBy(() -> definition(List.of(
                new ApprovalStage(
                        "first", "First", List.of(11L),
                        ApprovalMode.SEQUENTIAL, ApprovalApproverSource.fixed(),
                        null, autoApprove, null),
                previous("second")
        )))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID);

        var gateway = new ApprovalGateway(List.of(
                new ApprovalGateway.Branch(
                        "urgent", "Urgent", false,
                        List.of(new TriggerCondition(
                                "urgent", TriggerCondition.Operator.NOT_EMPTY, null)),
                        List.of(12L)),
                new ApprovalGateway.Branch(
                        "default", "Default", true, List.of(), List.of(11L))
        ));
        assertThatThrownBy(() -> new ApprovalDefinitionVersion(
                101L, 1, "Gateway", List.of(11L), 1, PUBLISHED,
                null, null, gateway, ApprovalMode.SEQUENTIAL,
                null, null, null, null, null, null,
                List.of(fixed("first", 11L), fixed("second", 12L))
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(
                        ApprovalDomainException.Code.APPROVAL_STAGES_GATEWAY_UNSUPPORTED);
    }

    @Test
    void proxyActorFeedsPreviousHandlerWithoutPollutingTheNewActiveStage() {
        var plan = List.of(
                fixed("review", 11L),
                previous("confirm"),
                fixed("archive", 22L)
        );
        var started = ApprovalInstance.start(
                201L,
                definition(plan),
                List.of(11L),
                "ordered-proxy",
                9L,
                STARTED,
                null
        );

        var reviewApproved = started.approve(
                99L, 11L, 701L, "reviewed", STARTED.plusSeconds(1));
        assertThat(reviewApproved.currentStage().status())
                .isEqualTo(ApprovalStageExecution.Status.APPROVED);
        assertThat(reviewApproved.currentStage().actualHandlerIds())
                .containsExactly(99L);
        assertThat(reviewApproved.currentStage().handlerActorsByParticipantId())
                .containsExactly(Map.entry(11L, 99L));

        var confirmActive = reviewApproved.activateNextStage(
                plan.get(1), List.of(99L), STARTED.plusSeconds(2));
        assertThat(confirmActive.id()).isEqualTo(started.id());
        assertThat(confirmActive.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(confirmActive.currentStageIndex()).isEqualTo(1);
        assertThat(confirmActive.currentStage().status())
                .isEqualTo(ApprovalStageExecution.Status.ACTIVE);
        assertThat(confirmActive.currentStage().approverIds()).containsExactly(99L);
        assertThat(confirmActive.currentStage().actualHandlerIds()).isEmpty();
        assertThat(confirmActive.currentStage().handlerActorsByParticipantId())
                .isEmpty();
        assertThat(confirmActive.history()).hasSize(2);
        assertThat(confirmActive.history().getLast().toStatus())
                .isEqualTo(ApprovalInstance.Status.PENDING);

        var confirmApproved = confirmActive.approve(
                99L, "confirmed", STARTED.plusSeconds(3));
        assertThat(confirmApproved.currentStage().actualHandlerIds())
                .containsExactly(99L);
        assertThat(confirmApproved.stages().getFirst().actualHandlerIds())
                .containsExactly(99L);

        var archiveActive = confirmApproved.activateNextStage(
                plan.get(2), List.of(22L), STARTED.plusSeconds(4));
        assertThat(archiveActive.currentStageIndex()).isEqualTo(2);
        assertThat(archiveActive.currentStage().actualHandlerIds()).isEmpty();
        assertThat(archiveActive.stages())
                .extracting(ApprovalStageExecution::status)
                .containsExactly(
                        ApprovalStageExecution.Status.APPROVED,
                        ApprovalStageExecution.Status.APPROVED,
                        ApprovalStageExecution.Status.ACTIVE
                );
    }

    @Test
    void allModeCountsRepresentedSlotsButDeduplicatesActualActors() {
        var all = new ApprovalStage(
                "joint", "Joint", List.of(11L, 12L), ApprovalMode.ALL,
                ApprovalApproverSource.fixed(), null, null, null);
        var tail = fixed("tail", 22L);
        var started = ApprovalInstance.start(
                202L,
                definition(List.of(all, tail)),
                List.of(11L, 12L),
                ApprovalMode.ALL,
                "all-proxy",
                9L,
                STARTED,
                null
        );

        var first = started.approve(
                99L, 11L, 702L, "first slot", STARTED.plusSeconds(1));
        assertThat(first.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(first.currentStage().actualHandlerIds()).containsExactly(99L);

        var completed = first.approve(
                99L, 12L, 703L, "second slot", STARTED.plusSeconds(2));
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(completed.currentStage().actualHandlerIds()).containsExactly(99L);
        assertThat(completed.currentStage().handlerActorsByParticipantId())
                .containsExactlyInAnyOrderEntriesOf(Map.of(11L, 99L, 12L, 99L));
    }

    @Test
    void anyAndQuorumFreezeOnlyActorsPresentAtTheirCompletionThreshold() {
        var any = new ApprovalStage(
                "any_review", "Any review", List.of(11L, 12L),
                ApprovalMode.ANY, ApprovalApproverSource.fixed(),
                null, null, null);
        var anyCompleted = ApprovalInstance.start(
                203L, definition(List.of(any, fixed("any_tail", 22L))),
                any.approverIds(), ApprovalMode.ANY, "any", 9L, STARTED, null
        ).approve(12L, "winner", STARTED.plusSeconds(1));
        assertThat(anyCompleted.currentStage().actualHandlerIds())
                .containsExactly(12L);

        var quorum = new ApprovalStage(
                "quorum_review", "Quorum review", List.of(11L, 12L, 13L),
                ApprovalMode.QUORUM, ApprovalApproverSource.fixed(),
                new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 2),
                null, null);
        var quorumStarted = ApprovalInstance.start(
                204L, definition(List.of(quorum, fixed("quorum_tail", 22L))),
                quorum.approverIds(), ApprovalMode.QUORUM,
                "quorum", 9L, STARTED, null
        );
        var oneVote = quorumStarted.approve(
                91L, 11L, 704L, "first", STARTED.plusSeconds(1));
        var quorumCompleted = oneVote.approve(
                92L, 12L, 705L, "second", STARTED.plusSeconds(2));

        assertThat(quorumCompleted.status())
                .isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(quorumCompleted.currentStage().actualHandlerIds())
                .containsExactly(91L, 92L);
        assertThat(quorumCompleted.currentStage().actualHandlerIds())
                .doesNotContain(13L);
    }

    @Test
    void approvedExecutionMayHaveNoActorForAutomaticApproval() {
        var approved = new ApprovalStageExecution(
                0, "auto", "Automatic", ApprovalStageExecution.Status.APPROVED,
                List.of(11L), ApprovalMode.SEQUENTIAL, 1, List.of(),
                STARTED, STARTED.plusSeconds(60), null,
                ApprovalDecisionCommentPolicy.defaults(), Map.of()
        );

        assertThat(approved.actualHandlerIds()).isEmpty();
    }

    @Test
    void rejectedStageRetainsEarlierHumanApprovalsForAudit() {
        var quorum = new ApprovalStage(
                "strict", "Strict", List.of(11L, 12L, 13L),
                ApprovalMode.QUORUM, ApprovalApproverSource.fixed(),
                new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 3),
                null, null);
        var started = ApprovalInstance.start(
                205L, definition(List.of(quorum, fixed("tail", 22L))),
                quorum.approverIds(), ApprovalMode.QUORUM,
                "rejected-audit", 9L, STARTED, null
        );

        var approvedOnce = started.approve(
                11L, "approved", STARTED.plusSeconds(1));
        var rejected = approvedOnce.reject(
                12L, "rejected", STARTED.plusSeconds(2));

        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.currentStage().status())
                .isEqualTo(ApprovalStageExecution.Status.REJECTED);
        assertThat(rejected.currentStage().actualHandlerIds())
                .containsExactly(11L);
    }

    private static ApprovalDefinitionVersion definition(List<ApprovalStage> plan) {
        var first = plan.getFirst();
        var route = first.approverIds().isEmpty()
                ? List.of(11L)
                : first.approverIds();
        return new ApprovalDefinitionVersion(
                101L, 1, "Ordered", route, 1, PUBLISHED,
                null, null, null, first.approvalMode(),
                null, null, null,
                new ApprovalQuorumRules(first.quorumRule(), Map.of()),
                null, null, plan
        );
    }

    private static ApprovalStage fixed(String code, long approverId) {
        return new ApprovalStage(
                code, code, List.of(approverId), ApprovalMode.SEQUENTIAL,
                ApprovalApproverSource.fixed(), null, null, null);
    }

    private static ApprovalStage previous(String code) {
        return new ApprovalStage(
                code, code, List.of(), ApprovalMode.SEQUENTIAL,
                ApprovalApproverSource.previousHandler(), null, null, null);
    }
}
