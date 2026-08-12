package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalParallelInstanceTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-29T16:00:00Z");
    private static final Instant STARTED_AT = PUBLISHED_AT.plusSeconds(60);

    @Test
    void startsEveryBranchAndApprovesOnlyAfterEveryBranchApproves() {
        var pending = start();

        assertThat(pending.isParallel()).isTrue();
        assertThat(pending.parallelBranches())
                .extracting(ApprovalBranchExecution::code)
                .containsExactly("finance", "security", "owner");
        assertThat(pending.activeApproverIds())
                .containsExactly(11L, 21L, 22L, 31L, 32L);

        var financeStepOne = pending.approveBranch(
                "finance",
                11L,
                "finance one",
                STARTED_AT.plusSeconds(1)
        );
        assertThat(financeStepOne.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(financeStepOne.parallelBranch("finance").activeApproverIds())
                .containsExactly(12L);
        assertThat(financeStepOne.parallelBranch("security").activeApproverIds())
                .containsExactly(21L, 22L);

        var financeDone = financeStepOne.approveBranch(
                "finance",
                12L,
                "finance done",
                STARTED_AT.plusSeconds(2)
        );
        var securityDone = financeDone.approveBranch(
                "security",
                22L,
                "any security member",
                STARTED_AT.plusSeconds(3)
        );
        var ownerHalf = securityDone.approveBranch(
                "owner",
                31L,
                "first owner",
                STARTED_AT.plusSeconds(4)
        );
        assertThat(ownerHalf.status()).isEqualTo(ApprovalInstance.Status.PENDING);

        var approved = ownerHalf.approveBranch(
                "owner",
                32L,
                "second owner",
                STARTED_AT.plusSeconds(5)
        );
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.parallelBranches())
                .extracting(ApprovalBranchExecution::status)
                .containsOnly(ApprovalBranchExecution.Status.APPROVED);
        assertThat(approved.activeApproverIds()).isEmpty();
    }

    @Test
    void rejectsParentImmediatelyAndCancelsEveryOtherPendingBranch() {
        var rejected = start().rejectBranch(
                "owner",
                31L,
                "risk rejected",
                STARTED_AT.plusSeconds(1)
        );

        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.parallelBranch("owner").status())
                .isEqualTo(ApprovalBranchExecution.Status.REJECTED);
        assertThat(rejected.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.CANCELLED);
        assertThat(rejected.parallelBranch("security").status())
                .isEqualTo(ApprovalBranchExecution.Status.CANCELLED);
        assertThatThrownBy(() -> rejected.approveBranch(
                "finance",
                11L,
                "late",
                STARTED_AT.plusSeconds(2)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.INSTANCE_STATE_INVALID);
    }

    @Test
    void closesActiveBranchesAndKeepsPublishedSnapshotImmutable() {
        var definition = definition();
        var pending = ApprovalInstance.start(
                201L,
                definition,
                "parallel-1",
                9L,
                STARTED_AT
        );
        var source = new ArrayList<>(definition.parallelGateway().branches());
        source.clear();

        assertThat(pending.parallelBranches()).hasSize(3);
        assertThatThrownBy(() -> pending.approve(11L, "", STARTED_AT.plusSeconds(1)))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.INSTANCE_STATE_INVALID);
        assertThatThrownBy(() ->
                pending.transfer(11L, 99L, "delegate", STARTED_AT.plusSeconds(1)))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.ASSIGNMENT_REQUEST_INVALID);

        var withdrawn = pending.withdraw(9L, "request withdrawn", STARTED_AT.plusSeconds(2));
        assertThat(withdrawn.status()).isEqualTo(ApprovalInstance.Status.WITHDRAWN);
        assertThat(withdrawn.parallelBranches())
                .extracting(ApprovalBranchExecution::status)
                .containsOnly(ApprovalBranchExecution.Status.WITHDRAWN);
    }

    @Test
    void quorumBranchApprovesAtItsSnapshotAndRejectsWhenItBecomesImpossible() {
        var definition = quorumDefinition();
        var pending = ApprovalInstance.start(
                301L,
                definition,
                "parallel-quorum",
                9L,
                STARTED_AT
        );
        assertThat(pending.parallelBranch("finance").requiredApprovals()).isEqualTo(2);

        var oneApproval = pending.approveBranch(
                "finance",
                11L,
                "first finance vote",
                STARTED_AT.plusSeconds(1)
        );
        assertThat(oneApproval.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.PENDING);
        var quorumReached = oneApproval.approveBranch(
                "finance",
                12L,
                "second finance vote",
                STARTED_AT.plusSeconds(2)
        );
        assertThat(quorumReached.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.APPROVED);
        assertThat(quorumReached.parallelBranch("finance").activeApproverIds()).isEmpty();
        assertThat(quorumReached.approveBranch(
                "owner",
                21L,
                "owner accepted",
                STARTED_AT.plusSeconds(3)
        ).status()).isEqualTo(ApprovalInstance.Status.APPROVED);

        var oneRejection = ApprovalInstance.start(
                302L,
                definition,
                "parallel-quorum-reject",
                9L,
                STARTED_AT
        ).rejectBranch(
                "finance",
                11L,
                "first rejection",
                STARTED_AT.plusSeconds(4)
        );
        assertThat(oneRejection.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        var impossible = oneRejection.rejectBranch(
                "finance",
                12L,
                "threshold impossible",
                STARTED_AT.plusSeconds(5)
        );
        assertThat(impossible.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(impossible.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.REJECTED);
        assertThat(impossible.parallelBranch("owner").status())
                .isEqualTo(ApprovalBranchExecution.Status.CANCELLED);
    }

    private static ApprovalInstance start() {
        return ApprovalInstance.start(
                201L,
                definition(),
                "parallel-1",
                9L,
                STARTED_AT
        );
    }

    private static ApprovalDefinitionVersion definition() {
        var gateway = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance",
                        "Finance",
                        List.of(11L, 12L),
                        ApprovalMode.SEQUENTIAL
                ),
                new ApprovalParallelGateway.Branch(
                        "security",
                        "Security",
                        List.of(21L, 22L),
                        ApprovalMode.ANY
                ),
                new ApprovalParallelGateway.Branch(
                        "owner",
                        "Owner",
                        List.of(31L, 32L),
                        ApprovalMode.ALL
                )
        ));
        return new ApprovalDefinitionVersion(
                101L,
                1,
                "Parallel approval",
                gateway.branches().getFirst().approverIds(),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                gateway.branches().getFirst().approvalMode(),
                gateway
        );
    }

    private static ApprovalDefinitionVersion quorumDefinition() {
        var rule = new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 2);
        var gateway = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance",
                        "Finance",
                        List.of(11L, 12L, 13L),
                        ApprovalMode.QUORUM
                ),
                new ApprovalParallelGateway.Branch(
                        "owner",
                        "Owner",
                        List.of(21L),
                        ApprovalMode.SEQUENTIAL
                )
        ));
        return new ApprovalDefinitionVersion(
                201L,
                1,
                "Parallel quorum approval",
                gateway.branches().getFirst().approverIds(),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                ApprovalMode.QUORUM,
                gateway,
                null,
                null,
                new ApprovalQuorumRules(rule, Map.of("finance", rule))
        );
    }
}
