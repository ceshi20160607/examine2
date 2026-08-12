package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalInstanceModeTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-29T10:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-07-29T10:01:00Z");

    @Test
    void anyModeApprovesOnFirstApprovalAndRejectsOnlyAfterEveryMemberRejects() {
        var pending = start(ApprovalMode.ANY);

        assertThat(pending.activeApproverIds()).containsExactly(11L, 12L, 13L);

        var oneRejected = pending.reject(12L, "not mine", STARTED_AT.plusSeconds(1));
        assertThat(oneRejected.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(oneRejected.rejectedApproverIds()).containsExactly(12L);
        assertThat(oneRejected.activeApproverIds()).containsExactly(11L, 13L);

        assertThatThrownBy(() ->
                oneRejected.reject(12L, "again", STARTED_AT.plusSeconds(2)))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVER_FORBIDDEN);

        var approved = oneRejected.approve(13L, "accepted", STARTED_AT.plusSeconds(3));
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.approvedApproverIds()).containsExactly(13L);
        assertThat(approved.activeApproverIds()).isEmpty();

        var allRejected = start(ApprovalMode.ANY)
                .reject(11L, "no", STARTED_AT.plusSeconds(1))
                .reject(12L, "no", STARTED_AT.plusSeconds(2))
                .reject(13L, "no", STARTED_AT.plusSeconds(3));
        assertThat(allRejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(allRejected.rejectedApproverIds()).containsExactly(11L, 12L, 13L);
    }

    @Test
    void allModeRequiresEveryApprovalAndRejectsOnFirstRejection() {
        var first = start(ApprovalMode.ALL)
                .approve(12L, "ok", STARTED_AT.plusSeconds(1));
        assertThat(first.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(first.approvedApproverIds()).containsExactly(12L);
        assertThat(first.activeApproverIds()).containsExactly(11L, 13L);

        var approved = first
                .approve(11L, "ok", STARTED_AT.plusSeconds(2))
                .approve(13L, "ok", STARTED_AT.plusSeconds(3));
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.approvedApproverIds()).containsExactly(11L, 12L, 13L);

        var rejected = start(ApprovalMode.ALL)
                .reject(13L, "blocked", STARTED_AT.plusSeconds(1));
        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.rejectedApproverIds()).containsExactly(13L);
    }

    @Test
    void concurrentModesRejectSequentialOnlyMutationsAndSnapshotMode() {
        var instance = start(ApprovalMode.ANY);

        assertThat(instance.approvalMode()).isEqualTo(ApprovalMode.ANY);
        assertThatThrownBy(() ->
                instance.transfer(11L, 99L, "delegate", STARTED_AT.plusSeconds(1)))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.ASSIGNMENT_REQUEST_INVALID);
        assertThatThrownBy(() ->
                instance.claim(99L, "", STARTED_AT.plusSeconds(1)))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.ASSIGNMENT_REQUEST_INVALID);
    }

    @Test
    void quorumApprovesAtCountAndRejectsWhenTheThresholdBecomesImpossible() {
        var pending = start(new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 2));

        assertThat(pending.approvalMode()).isEqualTo(ApprovalMode.QUORUM);
        assertThat(pending.requiredApprovals()).isEqualTo(2);
        var oneApproval = pending.approve(11L, "yes", STARTED_AT.plusSeconds(1));
        assertThat(oneApproval.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        var approved = oneApproval.approve(13L, "yes", STARTED_AT.plusSeconds(2));
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.activeApproverIds()).isEmpty();

        var oneRejection = start(new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 2))
                .reject(11L, "no", STARTED_AT.plusSeconds(1));
        assertThat(oneRejection.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        var rejected = oneRejection.reject(12L, "no", STARTED_AT.plusSeconds(2));
        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.activeApproverIds()).isEmpty();
    }

    @Test
    void percentageQuorumRoundsUpAndSnapshotsTheComputedCount() {
        var twoOfThree = start(
                new ApprovalQuorumRule(ApprovalQuorumRule.Type.PERCENTAGE, 34));
        var allThree = start(
                new ApprovalQuorumRule(ApprovalQuorumRule.Type.PERCENTAGE, 67));

        assertThat(twoOfThree.requiredApprovals()).isEqualTo(2);
        assertThat(allThree.requiredApprovals()).isEqualTo(3);
        assertThatThrownBy(() ->
                new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 4)
                        .requiredApprovals(3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed");
    }

    private static ApprovalInstance start(ApprovalMode mode) {
        var definition = new ApprovalDefinitionVersion(
                101L,
                1,
                "Mode test",
                List.of(11L, 12L, 13L),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                mode
        );
        return ApprovalInstance.start(
                201L,
                definition,
                "mode-1",
                9L,
                STARTED_AT
        );
    }

    private static ApprovalInstance start(ApprovalQuorumRule rule) {
        var definition = new ApprovalDefinitionVersion(
                101L,
                1,
                "Quorum test",
                List.of(11L, 12L, 13L),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                ApprovalMode.QUORUM,
                null,
                null,
                ApprovalApproverSources.fixedFor(null, null, null),
                new ApprovalQuorumRules(rule, Map.of())
        );
        return ApprovalInstance.start(
                201L,
                definition,
                "quorum-1",
                9L,
                STARTED_AT
        );
    }
}
