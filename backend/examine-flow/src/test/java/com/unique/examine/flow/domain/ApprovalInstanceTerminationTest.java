package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.TERMINATE_REASON_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalInstanceTerminationTest {
    private static final Instant STARTED = Instant.parse("2026-07-27T09:00:00Z");
    private static final Instant TERMINATED = Instant.parse("2026-07-27T09:10:00Z");

    @Test
    void anyOperatorCanTerminateAtAnyPendingSequentialStep() {
        var instance = pendingSequential()
                .approve(20, "step one", STARTED.plusSeconds(60))
                .approve(30, "step two", STARTED.plusSeconds(120));

        var terminated = instance.terminate(99, "  Duplicate request  ", TERMINATED);

        assertThat(terminated.status()).isEqualTo(ApprovalInstance.Status.TERMINATED);
        assertThat(terminated.approverId()).isEqualTo(40);
        assertThat(terminated.completedAt()).isEqualTo(TERMINATED);
        assertThat(terminated.history()).extracting(ApprovalHistoryEvent::type)
                .containsExactly(
                        ApprovalHistoryEvent.Type.STARTED,
                        ApprovalHistoryEvent.Type.APPROVED,
                        ApprovalHistoryEvent.Type.APPROVED,
                        ApprovalHistoryEvent.Type.TERMINATED);
        var event = terminated.history().getLast();
        assertThat(event.actorId()).isEqualTo(99);
        assertThat(event.fromStatus()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(event.toStatus()).isEqualTo(ApprovalInstance.Status.TERMINATED);
        assertThat(event.comment()).isEqualTo("Duplicate request");
    }

    @Test
    void rejectsInvalidReasonAndEveryMutationAfterTermination() {
        var pending = pendingSequential();
        assertThatThrownBy(() -> pending.terminate(99, " ", TERMINATED))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(TERMINATE_REASON_REQUIRED));
        assertThatThrownBy(() -> pending.terminate(99, "x".repeat(501), TERMINATED))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(TERMINATE_REASON_REQUIRED));

        var terminated = pending.terminate(99, "duplicate", TERMINATED);
        assertThatThrownBy(() -> terminated.approve(20, "late", TERMINATED.plusSeconds(1)))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID));
        assertThatThrownBy(() -> terminated.reject(20, "late", TERMINATED.plusSeconds(1)))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID));
        assertThatThrownBy(() -> terminated.withdraw(10, "late", TERMINATED.plusSeconds(1)))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID));
        assertThatThrownBy(() -> terminated.terminate(88, "late", TERMINATED.plusSeconds(1)))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID));
        assertThat(terminated.history()).hasSize(2);
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
