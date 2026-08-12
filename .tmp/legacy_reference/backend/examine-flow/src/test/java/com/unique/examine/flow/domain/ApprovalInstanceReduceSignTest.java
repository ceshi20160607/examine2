package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.REDUCE_SIGN_REQUEST_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalInstanceReduceSignTest {
    private static final Instant STARTED = Instant.parse("2026-07-27T14:00:00Z");

    @Test
    void removesExactlyOneFutureStepAndApprovalFollowsShortenedSnapshot() {
        var reduced = pending().reduceSign(
                20,
                2,
                "  Extra finance review is not required  ",
                STARTED.plusSeconds(10)
        );
        var next = reduced.approve(20, "first", STARTED.plusSeconds(20));
        var completed = next.approve(30, "final", STARTED.plusSeconds(30));

        assertThat(reduced.approverIds()).containsExactly(20L, 30L);
        assertThat(reduced.currentStepIndex()).isZero();
        assertThat(reduced.approverId()).isEqualTo(20);
        assertThat(reduced.claimState()).isEqualTo(ApprovalInstance.ClaimState.CLAIMED);
        assertThat(reduced.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(reduced.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.SIGN_REMOVED);
        assertThat(reduced.history().getLast().targetMemberId()).isEqualTo(40);
        assertThat(reduced.history().getLast().targetStepIndex()).isEqualTo(2);
        assertThat(reduced.history().getLast().assignmentPosition()).isNull();
        assertThat(reduced.history().getLast().comment())
                .isEqualTo("Extra finance review is not required");
        assertThat(next.approverId()).isEqualTo(30);
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
    }

    @Test
    void removingOnlyFutureStepCanMakeCurrentApprovalTerminal() {
        var atSecond = pending().approve(20, "first", STARTED.plusSeconds(10));
        var reduced = atSecond.reduceSign(30, 2, "skip last", STARTED.plusSeconds(20));
        var completed = reduced.approve(30, "done", STARTED.plusSeconds(30));

        assertThat(reduced.currentStepIndex()).isEqualTo(1);
        assertThat(reduced.approverIds()).containsExactly(20L, 30L);
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(completed.currentStepIndex()).isEqualTo(1);
    }

    @Test
    void validatesActorClaimStateFutureIndexAndReason() {
        var pending = pending();
        assertCode(
                () -> pending.reduceSign(21, 2, "reason", STARTED),
                APPROVER_FORBIDDEN
        );
        assertCode(
                () -> pending.reduceSign(20, null, "reason", STARTED),
                REDUCE_SIGN_REQUEST_INVALID
        );
        assertCode(
                () -> pending.reduceSign(20, 0, "reason", STARTED),
                REDUCE_SIGN_REQUEST_INVALID
        );
        assertCode(
                () -> pending.reduceSign(20, 3, "reason", STARTED),
                REDUCE_SIGN_REQUEST_INVALID
        );
        assertCode(
                () -> pending.reduceSign(20, 2, " ", STARTED),
                REDUCE_SIGN_REQUEST_INVALID
        );

        var open = pending.cancelClaim(20, "release", STARTED.plusSeconds(10));
        assertCode(
                () -> open.reduceSign(20, 2, "reason", STARTED.plusSeconds(20)),
                INSTANCE_STATE_INVALID
        );
        var terminal = pending.approve(20, "first", STARTED.plusSeconds(10))
                .approve(30, "second", STARTED.plusSeconds(20))
                .approve(40, "third", STARTED.plusSeconds(30));
        assertCode(
                () -> terminal.reduceSign(40, 2, "late", STARTED.plusSeconds(40)),
                INSTANCE_STATE_INVALID
        );
    }

    private static ApprovalInstance pending() {
        return ApprovalInstance.start(
                2,
                new ApprovalDefinitionVersion(
                        1,
                        1,
                        "Sequential",
                        List.of(20L, 30L, 40L),
                        1,
                        STARTED.minusSeconds(60)
                ),
                "expense-001",
                10,
                STARTED
        );
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
