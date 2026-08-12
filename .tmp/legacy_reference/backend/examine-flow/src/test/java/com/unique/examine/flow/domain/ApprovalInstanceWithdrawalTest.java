package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.REQUESTER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.WITHDRAW_REASON_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalInstanceWithdrawalTest {
    private static final Instant STARTED = Instant.parse("2026-07-27T08:00:00Z");
    private static final Instant WITHDRAWN = Instant.parse("2026-07-27T08:10:00Z");

    @Test
    void requesterCanWithdrawAtAnyPendingSequentialStep() {
        var instance = pendingSequential()
                .approve(20, "step one", STARTED.plusSeconds(60))
                .approve(30, "step two", STARTED.plusSeconds(120));

        var withdrawn = instance.withdraw(10, "  Submitted with the wrong amount  ", WITHDRAWN);

        assertThat(withdrawn.status()).isEqualTo(ApprovalInstance.Status.WITHDRAWN);
        assertThat(withdrawn.approverId()).isEqualTo(40);
        assertThat(withdrawn.completedAt()).isEqualTo(WITHDRAWN);
        assertThat(withdrawn.history()).extracting(ApprovalHistoryEvent::type)
                .containsExactly(
                        ApprovalHistoryEvent.Type.STARTED,
                        ApprovalHistoryEvent.Type.APPROVED,
                        ApprovalHistoryEvent.Type.APPROVED,
                        ApprovalHistoryEvent.Type.WITHDRAWN);
        var event = withdrawn.history().getLast();
        assertThat(event.actorId()).isEqualTo(10);
        assertThat(event.fromStatus()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(event.toStatus()).isEqualTo(ApprovalInstance.Status.WITHDRAWN);
        assertThat(event.comment()).isEqualTo("Submitted with the wrong amount");
    }

    @Test
    void rejectsInvalidReasonNonRequesterAndTerminalRepeat() {
        var pending = pendingSequential();
        assertThatThrownBy(() -> pending.withdraw(10, " ", WITHDRAWN))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(WITHDRAW_REASON_REQUIRED));
        assertThatThrownBy(() -> pending.withdraw(10, "x".repeat(501), WITHDRAWN))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(WITHDRAW_REASON_REQUIRED));
        assertThatThrownBy(() -> pending.withdraw(11, "wrong requester", WITHDRAWN))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(REQUESTER_FORBIDDEN));

        var withdrawn = pending.withdraw(10, "withdraw", WITHDRAWN);
        assertThatThrownBy(() -> withdrawn.withdraw(10, "repeat", WITHDRAWN.plusSeconds(1)))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID));
        assertThat(withdrawn.history()).hasSize(2);
    }

    private static ApprovalInstance pendingSequential() {
        var definition = new ApprovalDefinitionVersion(
                1,
                1,
                "Sequential",
                List.of(20L, 30L, 40L),
                1,
                STARTED.minusSeconds(60));
        return ApprovalInstance.start(2, definition, "expense-001", 10, STARTED);
    }
}
