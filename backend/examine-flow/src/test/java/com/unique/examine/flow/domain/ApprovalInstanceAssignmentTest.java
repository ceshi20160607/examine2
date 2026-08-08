package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.ASSIGNMENT_REQUEST_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalInstanceAssignmentTest {
    private static final Instant STARTED = Instant.parse("2026-07-27T11:00:00Z");

    @Test
    void transferReplacesCurrentRuntimeStepAndAssignmentHistoryDoesNotAdvanceApproval() {
        var current = pending(List.of(20L, 30L, 40L))
                .approve(20, "first", STARTED.plusSeconds(10));

        var transferred = current.transfer(30, 99, "  Owning reviewer  ", STARTED.plusSeconds(20));
        var approved = transferred.approve(99, "done", STARTED.plusSeconds(30));

        assertThat(transferred.approverIds()).containsExactly(20L, 99L, 40L);
        assertThat(transferred.approverId()).isEqualTo(99);
        assertThat(transferred.history().getLast().type())
                .isEqualTo(ApprovalHistoryEvent.Type.TRANSFERRED);
        assertThat(transferred.history().getLast().targetMemberId()).isEqualTo(99);
        assertThat(transferred.history().getLast().assignmentPosition()).isNull();
        assertThat(transferred.history().getLast().comment()).isEqualTo("Owning reviewer");
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(approved.approverId()).isEqualTo(40);
    }

    @Test
    void addSignBeforeImmediatelyAssignsTargetThenReturnsToOriginalApprover() {
        var added = pending(List.of(20L, 40L)).addSign(
                20,
                30,
                ApprovalHistoryEvent.AssignmentPosition.BEFORE,
                "security first",
                STARTED.plusSeconds(10)
        );
        var targetApproved = added.approve(30, "security done", STARTED.plusSeconds(20));

        assertThat(added.approverIds()).containsExactly(30L, 20L, 40L);
        assertThat(added.approverId()).isEqualTo(30);
        assertThat(targetApproved.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(targetApproved.approverId()).isEqualTo(20);
        assertThat(added.history().getLast().assignmentPosition())
                .isEqualTo(ApprovalHistoryEvent.AssignmentPosition.BEFORE);
    }

    @Test
    void addSignAfterLeavesCurrentTaskThenHandsOffToTarget() {
        var added = pending(List.of(20L, 40L)).addSign(
                20,
                30,
                ApprovalHistoryEvent.AssignmentPosition.AFTER,
                "security next",
                STARTED.plusSeconds(10)
        );
        var currentApproved = added.approve(20, "owner done", STARTED.plusSeconds(20));

        assertThat(added.approverIds()).containsExactly(20L, 30L, 40L);
        assertThat(added.approverId()).isEqualTo(20);
        assertThat(currentApproved.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(currentApproved.approverId()).isEqualTo(30);
        assertThat(added.history().getLast().assignmentPosition())
                .isEqualTo(ApprovalHistoryEvent.AssignmentPosition.AFTER);
    }

    @Test
    void assignmentValidatesActorTargetReasonPositionAndMaximumSequence() {
        var pending = pending(List.of(20L, 30L));
        assertCode(
                () -> pending.transfer(21, 40, "reason", STARTED),
                APPROVER_FORBIDDEN
        );
        assertCode(
                () -> pending.transfer(20, 20, "reason", STARTED),
                ASSIGNMENT_REQUEST_INVALID
        );
        assertCode(
                () -> pending.transfer(20, 30, "reason", STARTED),
                ASSIGNMENT_REQUEST_INVALID
        );
        assertCode(
                () -> pending.transfer(20, 40, " ", STARTED),
                ASSIGNMENT_REQUEST_INVALID
        );
        assertCode(
                () -> pending.addSign(20, 40, null, "reason", STARTED),
                ASSIGNMENT_REQUEST_INVALID
        );
        var tenSteps = pending(LongStream.rangeClosed(20, 29).boxed().toList());
        assertCode(
                () -> tenSteps.addSign(
                        20,
                        40,
                        ApprovalHistoryEvent.AssignmentPosition.AFTER,
                        "reason",
                        STARTED
                ),
                ASSIGNMENT_REQUEST_INVALID
        );
    }

    @Test
    void terminalInstanceCannotBeAssigned() {
        var approved = pending(List.of(20L)).approve(20, "done", STARTED.plusSeconds(10));
        assertCode(
                () -> approved.transfer(20, 30, "late", STARTED.plusSeconds(20)),
                INSTANCE_STATE_INVALID
        );
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
