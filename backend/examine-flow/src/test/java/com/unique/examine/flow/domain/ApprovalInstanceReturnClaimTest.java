package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.CANCEL_CLAIM_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.CLAIM_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.RETURN_REQUEST_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalInstanceReturnClaimTest {
    private static final Instant STARTED = Instant.parse("2026-07-27T12:00:00Z");

    @Test
    void returnMovesCursorBackAndRequiresEveryLaterStepToApproveAgain() {
        var atLastStep = pending(List.of(20L, 30L, 40L))
                .approve(20, "first", at(10))
                .approve(30, "second", at(20));

        var returned = atLastStep.returnToPrevious(
                40,
                "  Correct the supporting data  ",
                at(30)
        );
        var previousReapproved = returned.approve(30, "corrected", at(40));
        var completed = previousReapproved.approve(40, "final", at(50));

        assertThat(returned.currentStepIndex()).isEqualTo(1);
        assertThat(returned.approverId()).isEqualTo(30);
        assertThat(returned.approverIds()).containsExactly(20L, 30L, 40L);
        assertThat(returned.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.RETURNED);
        assertThat(returned.history().getLast().targetMemberId()).isEqualTo(30);
        assertThat(returned.history().getLast().comment()).isEqualTo("Correct the supporting data");
        assertThat(previousReapproved.currentStepIndex()).isEqualTo(2);
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(completed.currentStepIndex()).isEqualTo(2);
        assertThat(completed.history())
                .filteredOn(event -> event.type() == ApprovalHistoryEvent.Type.APPROVED)
                .hasSize(4);
    }

    @Test
    void returnValidatesActorReasonAndPreviousStep() {
        var first = pending(List.of(20L, 30L));
        assertCode(
                () -> first.returnToPrevious(21, "reason", at(10)),
                APPROVER_FORBIDDEN
        );
        assertCode(
                () -> first.returnToPrevious(20, "reason", at(10)),
                RETURN_REQUEST_INVALID
        );
        var second = first.approve(20, "done", at(10));
        assertCode(
                () -> second.returnToPrevious(30, " ", at(20)),
                RETURN_REQUEST_INVALID
        );
    }

    @Test
    void cancelClaimOpensWithoutChangingCursorOwnerOrSequenceAndBlocksHandling() {
        var current = pending(List.of(20L, 30L));
        var open = current.cancelClaim(20, "  candidate pool  ", at(10));

        assertThat(open.claimState()).isEqualTo(ApprovalInstance.ClaimState.OPEN);
        assertThat(open.currentStepIndex()).isZero();
        assertThat(open.approverId()).isEqualTo(20);
        assertThat(open.approverIds()).containsExactly(20L, 30L);
        assertThat(open.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.CLAIM_CANCELLED);
        assertThat(open.history().getLast().targetMemberId()).isNull();
        assertThat(open.history().getLast().comment()).isEqualTo("candidate pool");

        assertCode(() -> open.approve(20, "", at(20)), INSTANCE_STATE_INVALID);
        assertCode(() -> open.reject(20, "no", at(20)), INSTANCE_STATE_INVALID);
        assertCode(() -> open.transfer(20, 40, "move", at(20)), INSTANCE_STATE_INVALID);
        assertCode(() -> open.addSign(
                20,
                40,
                ApprovalHistoryEvent.AssignmentPosition.AFTER,
                "add",
                at(20)
        ), INSTANCE_STATE_INVALID);
        assertCode(() -> open.returnToPrevious(20, "back", at(20)), INSTANCE_STATE_INVALID);
    }

    @Test
    void claimReplacesOnlyCursorMemberAndFormerOwnerMayReclaim() {
        var atSecond = pending(List.of(20L, 30L, 40L))
                .approve(20, "first", at(10));
        var open = atSecond.cancelClaim(30, "release", at(20));
        var claimed = open.claim(50, "  I will handle it  ", at(30));

        assertThat(claimed.claimState()).isEqualTo(ApprovalInstance.ClaimState.CLAIMED);
        assertThat(claimed.currentStepIndex()).isEqualTo(1);
        assertThat(claimed.approverId()).isEqualTo(50);
        assertThat(claimed.approverIds()).containsExactly(20L, 50L, 40L);
        assertThat(claimed.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.CLAIMED);
        assertThat(claimed.history().getLast().targetMemberId()).isEqualTo(50);
        assertThat(claimed.history().getLast().comment()).isEqualTo("I will handle it");

        var reopened = claimed.cancelClaim(50, "release again", at(40));
        var reclaimed = reopened.claim(50, null, at(50));
        assertThat(reclaimed.approverIds()).containsExactly(20L, 50L, 40L);
        assertThat(reclaimed.history().getLast().comment()).isEmpty();
    }

    @Test
    void claimRejectsAnotherSequenceOccurrenceLongCommentAndClaimedState() {
        var open = pending(List.of(20L, 30L))
                .cancelClaim(20, "release", at(10));

        assertCode(() -> open.claim(30, "", at(20)), CLAIM_REQUEST_INVALID);
        assertCode(() -> open.claim(40, "x".repeat(501), at(20)), CLAIM_REQUEST_INVALID);
        assertCode(() -> pending(List.of(20L)).claim(40, "", at(20)), INSTANCE_STATE_INVALID);
        assertCode(
                () -> pending(List.of(20L)).cancelClaim(20, " ", at(20)),
                CANCEL_CLAIM_REQUEST_INVALID
        );
    }

    @Test
    void oldConstructorRebuildsCursorFromForwardAndReturnHistory() {
        var returned = pending(List.of(20L, 30L, 40L))
                .approve(20, "first", at(10))
                .approve(30, "second", at(20))
                .returnToPrevious(40, "back", at(30));

        var restored = new ApprovalInstance(
                returned.id(),
                returned.definitionId(),
                returned.definitionVersion(),
                returned.businessKey(),
                returned.requesterId(),
                returned.approverId(),
                returned.approverIds(),
                returned.status(),
                returned.startedAt(),
                returned.completedAt(),
                returned.history()
        );

        assertThat(restored.currentStepIndex()).isEqualTo(1);
        assertThat(restored.claimState()).isEqualTo(ApprovalInstance.ClaimState.CLAIMED);
    }

    private static ApprovalInstance pending(List<Long> approvers) {
        return ApprovalInstance.start(
                2,
                new ApprovalDefinitionVersion(
                        1,
                        1,
                        "Sequential",
                        approvers,
                        1,
                        STARTED.minusSeconds(60)
                ),
                "expense-001",
                10,
                STARTED
        );
    }

    private static Instant at(long seconds) {
        return STARTED.plusSeconds(seconds);
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            ApprovalDomainException.Code code
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(code)
                );
    }
}
