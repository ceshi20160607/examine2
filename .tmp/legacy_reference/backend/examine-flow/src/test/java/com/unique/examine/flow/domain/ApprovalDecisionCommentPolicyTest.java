package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalDecisionCommentPolicyTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-30T10:00:00Z");
    private static final Instant STARTED_AT = PUBLISHED_AT.plusSeconds(60);

    @Test
    void defaultsKeepApprovalOptionalAndRejectionRequired() {
        var approved = start(
                1L,
                ordinaryDefinition(
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL,
                        ApprovalDecisionCommentPolicy.defaults(),
                        null
                )
        ).approve(11L, "   ", STARTED_AT.plusSeconds(1));

        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.history().getLast().comment()).isEmpty();

        var pending = start(
                2L,
                ordinaryDefinition(
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL,
                        ApprovalDecisionCommentPolicy.defaults(),
                        null
                )
        );
        assertThatThrownBy(() -> pending.reject(
                11L,
                "  ",
                STARTED_AT.plusSeconds(1)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.REJECTION_REASON_REQUIRED);
        assertThat(pending.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(pending.history()).hasSize(1);
    }

    @Test
    void requiredApprovalUsesTrimmedUnicodeCodePointsAndAllowsRetry() {
        var policy = new ApprovalDecisionCommentPolicy(true, true, 2);
        var pending = start(
                3L,
                ordinaryDefinition(
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL,
                        policy,
                        null
                )
        );

        assertThatThrownBy(() -> pending.approve(
                11L,
                " 😀 ",
                STARTED_AT.plusSeconds(1)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVAL_COMMENT_REQUIRED);
        assertThat(pending.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(pending.history()).hasSize(1);

        var approved = pending.approve(
                11L,
                " 😀好 ",
                STARTED_AT.plusSeconds(2)
        );
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.history().getLast().comment()).isEqualTo("😀好");
        assertThat(approved.decisionCommentPolicy()).isEqualTo(policy);
    }

    @Test
    void concurrentModesEnforceRequiredRejectionAndFiveHundredCodePointLimit() {
        var optional = new ApprovalDecisionCommentPolicy(false, true, 3);
        var pending = start(
                4L,
                ordinaryDefinition(
                        List.of(11L, 12L),
                        ApprovalMode.ALL,
                        optional,
                        null
                )
        );
        var fiveHundred = "😀".repeat(500);
        var oneApproved = pending.approve(
                11L,
                fiveHundred,
                STARTED_AT.plusSeconds(1)
        );
        assertThat(oneApproved.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(oneApproved.decisions())
                .containsEntry(11L, ApprovalInstance.Decision.APPROVED);

        assertThatThrownBy(() -> pending.approve(
                11L,
                "😀".repeat(501),
                STARTED_AT.plusSeconds(2)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVAL_COMMENT_REQUIRED);
        assertThat(pending.decisions()).isEmpty();

        assertThatThrownBy(() -> pending.reject(
                12L,
                "不",
                STARTED_AT.plusSeconds(3)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.REJECTION_REASON_REQUIRED);
        assertThat(pending.decisions()).isEmpty();
    }

    @Test
    void anyAllAndQuorumValidateBeforeRecordingMemberDecision() {
        var required = new ApprovalDecisionCommentPolicy(true, true, 2);
        var modes = List.of(ApprovalMode.ANY, ApprovalMode.ALL, ApprovalMode.QUORUM);
        for (var index = 0; index < modes.size(); index++) {
            var offset = index;
            var mode = modes.get(index);
            var pending = start(
                    20L + index,
                    ordinaryDefinition(
                            List.of(11L, 12L, 13L),
                            mode,
                            required,
                            null
                    )
            );

            assertThatThrownBy(() -> pending.approve(
                    11L,
                    "短",
                    STARTED_AT.plusSeconds(10 + offset)
            ))
                    .isInstanceOf(ApprovalDomainException.class)
                    .extracting("code")
                    .isEqualTo(ApprovalDomainException.Code.APPROVAL_COMMENT_REQUIRED);
            assertThatThrownBy(() -> pending.reject(
                    11L,
                    "短",
                    STARTED_AT.plusSeconds(20 + offset)
            ))
                    .isInstanceOf(ApprovalDomainException.class)
                    .extracting("code")
                    .isEqualTo(ApprovalDomainException.Code.REJECTION_REASON_REQUIRED);
            assertThat(pending.decisions()).isEmpty();

            var decided = pending.approve(
                    11L,
                    "足够",
                    STARTED_AT.plusSeconds(30 + offset)
            );
            assertThat(decided.decisions())
                    .containsEntry(11L, ApprovalInstance.Decision.APPROVED);
            assertThat(decided.decisionCommentPolicy()).isEqualTo(required);
        }
    }

    @Test
    void parallelBranchesKeepIndependentPoliciesAndFailedDecisionIsSideEffectFree() {
        var financePolicy = new ApprovalDecisionCommentPolicy(true, true, 2);
        var ownerPolicy = new ApprovalDecisionCommentPolicy(false, false, 5);
        var gateway = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance",
                        "Finance",
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL
                ),
                new ApprovalParallelGateway.Branch(
                        "owner",
                        "Owner",
                        List.of(21L),
                        ApprovalMode.SEQUENTIAL
                )
        ));
        var policies = new ApprovalDecisionCommentPolicies(
                financePolicy,
                Map.of(
                        "finance", financePolicy,
                        "owner", ownerPolicy
                )
        );
        var definition = ApprovalDefinitionVersion.publish(
                new ApprovalDefinitionDraft(
                        101L,
                        "Parallel decision comments",
                        gateway.branches().getFirst().approverIds(),
                        1,
                        PUBLISHED_AT,
                        null,
                        null,
                        null,
                        gateway.branches().getFirst().approvalMode(),
                        gateway,
                        null,
                        null,
                        null,
                        null,
                        policies
                ),
                1,
                PUBLISHED_AT
        );
        var pending = start(5L, definition);

        assertThat(pending.parallelBranch("finance").decisionCommentPolicy())
                .isEqualTo(financePolicy);
        assertThat(pending.parallelBranch("owner").decisionCommentPolicy())
                .isEqualTo(ownerPolicy);
        assertThatThrownBy(() -> pending.approveBranch(
                "finance",
                11L,
                "短",
                STARTED_AT.plusSeconds(1)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVAL_COMMENT_REQUIRED);
        assertThat(pending.history()).hasSize(1);
        assertThat(pending.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.PENDING);
        assertThat(pending.parallelBranch("owner").status())
                .isEqualTo(ApprovalBranchExecution.Status.PENDING);

        var financeApproved = pending.approveBranch(
                "finance",
                11L,
                "可以",
                STARTED_AT.plusSeconds(2)
        );
        var completed = financeApproved.approveBranch(
                "owner",
                21L,
                "",
                STARTED_AT.plusSeconds(3)
        );
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(completed.parallelBranch("owner").decisionCommentPolicy())
                .isEqualTo(ownerPolicy);
    }

    @Test
    void automaticDeadlineDecisionBypassesHumanCommentPolicy() {
        var strict = new ApprovalDecisionCommentPolicy(true, true, 500);
        var deadline = new ApprovalDeadlinePolicy(
                1,
                null,
                ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE
        );
        var pending = start(
                6L,
                ordinaryDefinition(
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL,
                        strict,
                        deadline
                )
        );

        var approved = pending.processDeadline(
                null,
                999L,
                STARTED_AT.plusSeconds(60)
        );
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.DEADLINE_AUTO_APPROVED);
        assertThat(approved.decisionCommentPolicy()).isEqualTo(strict);
    }

    @Test
    void policyContainersRejectOutOfRangeAndMismatchedBranchShape() {
        assertThatThrownBy(() -> new ApprovalDecisionCommentPolicy(false, true, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalDecisionCommentPolicy(false, true, 501))
                .isInstanceOf(IllegalArgumentException.class);

        var gateway = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "first",
                        "First",
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL
                ),
                new ApprovalParallelGateway.Branch(
                        "second",
                        "Second",
                        List.of(12L),
                        ApprovalMode.SEQUENTIAL
                )
        ));
        var required = new ApprovalDecisionCommentPolicy(true, true, 2);
        assertThatThrownBy(() -> new ApprovalDecisionCommentPolicies(
                required,
                Map.of("other", required)
        ).requireShape(null, gateway, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalDecisionCommentPolicies(
                required,
                Map.of()
        ).requireShape(null, gateway, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApprovalInstance start(
            long instanceId,
            ApprovalDefinitionVersion definition
    ) {
        return ApprovalInstance.start(
                instanceId,
                definition,
                "decision-comment-" + instanceId,
                9L,
                STARTED_AT
        );
    }

    private static ApprovalDefinitionVersion ordinaryDefinition(
            List<Long> approverIds,
            ApprovalMode approvalMode,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            ApprovalDeadlinePolicy deadlinePolicy
    ) {
        var quorumRules = approvalMode == ApprovalMode.QUORUM
                ? new ApprovalQuorumRules(
                        new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 2),
                        Map.of()
                )
                : null;
        return ApprovalDefinitionVersion.publish(
                new ApprovalDefinitionDraft(
                        100L,
                        "Decision comments",
                        approverIds,
                        1,
                        PUBLISHED_AT,
                        null,
                        null,
                        null,
                        approvalMode,
                        null,
                        null,
                        null,
                        quorumRules,
                        deadlinePolicy == null
                                ? null
                                : new ApprovalDeadlinePolicies(
                                        deadlinePolicy,
                                        Map.of()
                                ),
                        new ApprovalDecisionCommentPolicies(
                                decisionCommentPolicy,
                                Map.of()
                        )
                ),
                1,
                PUBLISHED_AT
        );
    }
}
