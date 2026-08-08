package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalBranchOrderedStageTest {
    private static final Instant PUBLISHED =
            Instant.parse("2026-07-31T02:00:00Z");
    private static final Instant STARTED = PUBLISHED.plusSeconds(60);

    @Test
    void advancesOnlyTheCompletedBranchAndJoinsAfterEveryFinalStage() {
        var definition = definition();
        var financePlan = definition.parallelGateway().branches()
                .getFirst().approvalStages();
        var ownerPlan = definition.parallelGateway().branches()
                .getLast().approvalStages();
        var started = ApprovalInstance.start(
                201L, definition, "branch-stages", 9L, STARTED);

        var financeStageZero = started.approveBranch(
                "finance", 99L, 11L, 701L, "reviewed",
                STARTED.plusSeconds(1));
        assertThat(financeStageZero.status())
                .isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(financeStageZero.parallelBranch("finance").currentStage())
                .satisfies(stage -> {
                    assertThat(stage.status())
                            .isEqualTo(ApprovalStageExecution.Status.APPROVED);
                    assertThat(stage.actualHandlerIds()).containsExactly(99L);
                });
        assertThat(financeStageZero.parallelBranch("owner").currentStageIndex())
                .isZero();

        assertThatThrownBy(() -> financeStageZero.activateNextBranchStage(
                "finance", financePlan.get(1), List.of(21L),
                STARTED.plusSeconds(2)))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.INSTANCE_STATE_INVALID);

        var financeStageOne = financeStageZero.activateNextBranchStage(
                "finance", financePlan.get(1), List.of(99L),
                STARTED.plusSeconds(2));
        assertThat(financeStageOne.parallelBranch("finance").currentStageIndex())
                .isEqualTo(1);
        assertThat(financeStageOne.parallelBranch("finance").currentStage()
                .approverIds()).containsExactly(99L);
        assertThat(financeStageOne.parallelBranch("finance").currentStage()
                .actualHandlerIds()).isEmpty();
        assertThat(financeStageOne.parallelBranch("owner").currentStageIndex())
                .isZero();

        var ownerStageZero = financeStageOne.approveBranch(
                "owner", 21L, "owner reviewed", STARTED.plusSeconds(3));
        var ownerStageOne = ownerStageZero.activateNextBranchStage(
                "owner", ownerPlan.get(1), List.of(22L),
                STARTED.plusSeconds(4));
        assertThat(ownerStageOne.parallelBranch("finance").currentStageIndex())
                .isEqualTo(1);
        assertThat(ownerStageOne.parallelBranch("owner").currentStageIndex())
                .isEqualTo(1);

        var financeDone = ownerStageOne.approveBranch(
                "finance", 99L, "confirmed", STARTED.plusSeconds(5));
        assertThat(financeDone.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(financeDone.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.APPROVED);

        var allDone = financeDone.approveBranch(
                "owner", 22L, "archived", STARTED.plusSeconds(6));
        assertThat(allDone.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(allDone.parallelBranches())
                .extracting(ApprovalBranchExecution::currentStageIndex)
                .containsExactly(1, 1);
        assertThat(allDone.parallelBranches())
                .flatExtracting(ApprovalBranchExecution::stages)
                .extracting(ApprovalStageExecution::status)
                .containsOnly(ApprovalStageExecution.Status.APPROVED);
    }

    @Test
    void failFastRejectionPreservesCompletedSnapshotsAndCancelsSiblingWork() {
        var definition = definition();
        var financePlan = definition.parallelGateway().branches()
                .getFirst().approvalStages();
        var ownerPlan = definition.parallelGateway().branches()
                .getLast().approvalStages();
        var activeSecondStages = ApprovalInstance.start(
                202L, definition, "branch-reject", 9L, STARTED)
                .approveBranch(
                        "finance", 11L, "finance approved",
                        STARTED.plusSeconds(1))
                .activateNextBranchStage(
                        "finance", financePlan.get(1), List.of(11L),
                        STARTED.plusSeconds(2))
                .approveBranch(
                        "owner", 21L, "owner approved",
                        STARTED.plusSeconds(3))
                .activateNextBranchStage(
                        "owner", ownerPlan.get(1), List.of(22L),
                        STARTED.plusSeconds(4));

        var rejected = activeSecondStages.rejectBranch(
                "finance", 11L, "confirmation rejected",
                STARTED.plusSeconds(5));

        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.REJECTED);
        assertThat(rejected.parallelBranch("owner").status())
                .isEqualTo(ApprovalBranchExecution.Status.CANCELLED);
        assertThat(rejected.parallelBranches())
                .allSatisfy(branch -> {
                    assertThat(branch.stages().getFirst().status())
                            .isEqualTo(ApprovalStageExecution.Status.APPROVED);
                    assertThat(branch.stages().getLast().status())
                            .isEqualTo(ApprovalStageExecution.Status.REJECTED);
                });
    }

    @Test
    void resolvedBranchRetainsPublishedPlanAndLegacyAbsence() {
        var branch = definition().parallelGateway().branches().getFirst();
        var resolved = ResolvedApprovalBranchRoute.from(branch, List.of(11L));
        assertThat(resolved.approvalStages()).isEqualTo(branch.approvalStages());

        var legacy = new ApprovalParallelGateway.Branch(
                "legacy", "Legacy", List.of(31L), ApprovalMode.SEQUENTIAL);
        assertThat(ResolvedApprovalBranchRoute.from(
                legacy, List.of(31L)).approvalStages()).isNull();
    }

    private static ApprovalDefinitionVersion definition() {
        var financePlan = List.of(
                fixed("finance_review", 11L),
                previous("finance_confirm")
        );
        var ownerPlan = List.of(
                fixed("owner_review", 21L),
                fixed("owner_archive", 22L)
        );
        var gateway = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance", "Finance", List.of(11L),
                        ApprovalMode.SEQUENTIAL, financePlan),
                new ApprovalParallelGateway.Branch(
                        "owner", "Owner", List.of(21L),
                        ApprovalMode.SEQUENTIAL, ownerPlan)
        ));
        return new ApprovalDefinitionVersion(
                101L, 1, "Branch stages", List.of(11L), 1, PUBLISHED,
                null, null, null, ApprovalMode.SEQUENTIAL,
                gateway, null, null, null, null, null, null
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
