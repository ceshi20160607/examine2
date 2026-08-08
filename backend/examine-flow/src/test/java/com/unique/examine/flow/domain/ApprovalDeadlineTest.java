package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalDeadlineTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-30T00:00:00Z");
    private static final Instant STARTED_AT = PUBLISHED_AT.plusSeconds(60);

    @Test
    void validatesPolicyBoundsAndComputesImmutableAbsoluteTimes() {
        assertThatThrownBy(() -> new ApprovalDeadlinePolicy(
                0, null, ApprovalDeadlinePolicy.TimeoutAction.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalDeadlinePolicy(
                30, 30, ApprovalDeadlinePolicy.TimeoutAction.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalDeadlinePolicy(
                30, 5, null))
                .isInstanceOf(IllegalArgumentException.class);

        var policy = new ApprovalDeadlinePolicy(
                30, 5, ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE);
        var state = ApprovalDeadlineState.start(policy, STARTED_AT);

        assertThat(state.remindAt()).isEqualTo(STARTED_AT.plusSeconds(25 * 60L));
        assertThat(state.dueAt()).isEqualTo(STARTED_AT.plusSeconds(30 * 60L));
        assertThat(state.remindedAt()).isNull();
        assertThat(state.processedAt()).isNull();
    }

    @Test
    void remindsOnceAndAutomaticallyApprovesTheWholeRouteAtItsSnapshotDeadline() {
        var policy = new ApprovalDeadlinePolicy(
                30, 5, ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE);
        var pending = start(ordinaryDefinition(policy), 101L);

        var reminded = pending.markDeadlineReminded(
                null,
                pending.requesterId(),
                pending.deadline().remindAt()
        );
        assertThat(reminded.deadline().remindedAt())
                .isEqualTo(pending.deadline().remindAt());
        assertThat(reminded.markDeadlineReminded(
                null,
                pending.requesterId(),
                pending.deadline().remindAt().plusSeconds(1)
        )).isSameAs(reminded);

        var approved = reminded.processDeadline(
                null,
                pending.requesterId(),
                pending.deadline().dueAt()
        );
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.activeApproverIds()).isEmpty();
        assertThat(approved.deadline().processedAt()).isEqualTo(pending.deadline().dueAt());
        assertThat(approved.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.DEADLINE_AUTO_APPROVED);
        assertThat(approved.processDeadline(
                null,
                pending.requesterId(),
                pending.deadline().dueAt().plusSeconds(1)
        )).isSameAs(approved);
    }

    @Test
    void automaticRejectAndOverdueOnlyPoliciesKeepTheirExactSemantics() {
        var rejectedPolicy = new ApprovalDeadlinePolicy(
                10, null, ApprovalDeadlinePolicy.TimeoutAction.AUTO_REJECT);
        var rejected = start(ordinaryDefinition(rejectedPolicy), 102L)
                .processDeadline(
                        null,
                        9L,
                        STARTED_AT.plusSeconds(10 * 60L)
                );
        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.DEADLINE_AUTO_REJECTED);

        var nonePolicy = new ApprovalDeadlinePolicy(
                10, null, ApprovalDeadlinePolicy.TimeoutAction.NONE);
        var overdue = start(ordinaryDefinition(nonePolicy), 103L)
                .processDeadline(
                        null,
                        9L,
                        STARTED_AT.plusSeconds(10 * 60L)
                );
        assertThat(overdue.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(overdue.deadline().processedAt())
                .isEqualTo(STARTED_AT.plusSeconds(10 * 60L));
        assertThat(overdue.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.DEADLINE_OVERDUE);
        assertThat(overdue.approve(
                11L,
                "processed after deadline",
                STARTED_AT.plusSeconds(10 * 60L + 1)
        ).status()).isEqualTo(ApprovalInstance.Status.PENDING);
    }

    @Test
    void parallelBranchesOwnIndependentDeadlinesAndFailFastParentJoin() {
        var financePolicy = new ApprovalDeadlinePolicy(
                5, 1, ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE);
        var ownerPolicy = new ApprovalDeadlinePolicy(
                10, null, ApprovalDeadlinePolicy.TimeoutAction.AUTO_REJECT);
        var pending = start(parallelDefinition(financePolicy, ownerPolicy), 104L);

        assertThat(pending.parallelBranch("finance").deadline().dueAt())
                .isEqualTo(STARTED_AT.plusSeconds(5 * 60L));
        assertThat(pending.parallelBranch("owner").deadline().dueAt())
                .isEqualTo(STARTED_AT.plusSeconds(10 * 60L));

        var financeApproved = pending.processDeadline(
                "finance",
                9L,
                STARTED_AT.plusSeconds(5 * 60L)
        );
        assertThat(financeApproved.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(financeApproved.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.APPROVED);
        assertThat(financeApproved.parallelBranch("owner").status())
                .isEqualTo(ApprovalBranchExecution.Status.PENDING);

        var ownerRejected = financeApproved.processDeadline(
                "owner",
                9L,
                STARTED_AT.plusSeconds(10 * 60L)
        );
        assertThat(ownerRejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(ownerRejected.parallelBranch("owner").status())
                .isEqualTo(ApprovalBranchExecution.Status.REJECTED);
        assertThat(ownerRejected.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.DEADLINE_AUTO_REJECTED);
    }

    @Test
    void rejectsMismatchedGatewayPolicyShape() {
        var gateway = parallelGateway();
        var finance = new ApprovalDeadlinePolicy(
                5, null, ApprovalDeadlinePolicy.TimeoutAction.NONE);

        assertThatThrownBy(() -> new ApprovalDeadlinePolicies(
                finance,
                Map.of("owner", finance)
        ).requireShape(null, gateway, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mirror");
    }

    private static ApprovalInstance start(
            ApprovalDefinitionVersion definition,
            long instanceId
    ) {
        return ApprovalInstance.start(
                instanceId,
                definition,
                "deadline-" + instanceId,
                9L,
                STARTED_AT
        );
    }

    private static ApprovalDefinitionVersion ordinaryDefinition(
            ApprovalDeadlinePolicy policy
    ) {
        return new ApprovalDefinitionVersion(
                1L,
                1,
                "Deadline approval",
                List.of(11L, 12L),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                ApprovalApproverSources.fixedFor(null, null, null),
                ApprovalQuorumRules.none(),
                new ApprovalDeadlinePolicies(policy, Map.of())
        );
    }

    private static ApprovalDefinitionVersion parallelDefinition(
            ApprovalDeadlinePolicy financePolicy,
            ApprovalDeadlinePolicy ownerPolicy
    ) {
        var gateway = parallelGateway();
        return new ApprovalDefinitionVersion(
                2L,
                1,
                "Parallel deadline approval",
                gateway.branches().getFirst().approverIds(),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                gateway.branches().getFirst().approvalMode(),
                gateway,
                null,
                ApprovalApproverSources.fixedFor(null, gateway, null),
                ApprovalQuorumRules.none(),
                new ApprovalDeadlinePolicies(
                        financePolicy,
                        Map.of(
                                "finance", financePolicy,
                                "owner", ownerPolicy
                        )
                )
        );
    }

    private static ApprovalParallelGateway parallelGateway() {
        return new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance", "Finance", List.of(11L), ApprovalMode.SEQUENTIAL),
                new ApprovalParallelGateway.Branch(
                        "owner", "Owner", List.of(21L), ApprovalMode.SEQUENTIAL)
        ));
    }
}
