package com.unique.examine.flow.interaction;

import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COMMENT_BODY_REQUIRED;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.REQUESTER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.URGE_MESSAGE_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowInteractionDomainTest {
    private static final Instant STARTED = Instant.parse("2026-07-27T10:00:00Z");

    @Test
    void requesterUrgeTargetsCurrentSequentialApproverWithoutChangingInstance() {
        var pending = pendingSequential().approve(20, "first", STARTED.plusSeconds(10));

        var urge = FlowUrge.create(
                100,
                pending,
                10,
                "  Please review before noon  ",
                STARTED.plusSeconds(20)
        );

        assertThat(urge.actorId()).isEqualTo(10);
        assertThat(urge.recipientId()).isEqualTo(30);
        assertThat(urge.message()).isEqualTo("Please review before noon");
        assertThat(pending.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(pending.approverId()).isEqualTo(30);
        assertThat(pending.completedAt()).isNull();
        assertThat(pending.history()).hasSize(2);
    }

    @Test
    void urgeRejectsNonRequesterTerminalStateAndOversizedMessage() {
        var pending = pendingSequential();
        assertThatThrownBy(() -> FlowUrge.create(100, pending, 11, "", STARTED))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(REQUESTER_FORBIDDEN));
        assertThatThrownBy(() -> FlowUrge.create(100, pending, 10, "x".repeat(501), STARTED))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(URGE_MESSAGE_INVALID));

        var approved = pending.approve(20, "done", STARTED.plusSeconds(10))
                .approve(30, "done", STARTED.plusSeconds(20));
        assertThatThrownBy(() -> FlowUrge.create(100, approved, 10, "late", STARTED.plusSeconds(30)))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID));
    }

    @Test
    void requesterCannotUrgeAnOpenTask() {
        var open = pendingSequential().cancelClaim(
                20,
                "candidate pool",
                STARTED.plusSeconds(10)
        );

        assertThatThrownBy(() ->
                FlowUrge.create(100, open, 10, "please", STARTED.plusSeconds(20)))
                .isInstanceOfSatisfying(
                        ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(INSTANCE_STATE_INVALID)
                );
    }

    @Test
    void commentTrimsBodyAndValidatesUnicodeCodePointLength() {
        var comment = new FlowComment(1, 2, 3, "  checked  ", STARTED);
        assertThat(comment.body()).isEqualTo("checked");
        assertThat(new FlowComment(2, 2, 3, "😀".repeat(2000), STARTED).body())
                .hasSize(4000);
        assertThatThrownBy(() -> new FlowComment(3, 2, 3, " ", STARTED))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(COMMENT_BODY_REQUIRED));
        assertThatThrownBy(() -> new FlowComment(4, 2, 3, "😀".repeat(2001), STARTED))
                .isInstanceOfSatisfying(ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(COMMENT_BODY_REQUIRED));
    }

    private static ApprovalInstance pendingSequential() {
        return ApprovalInstance.start(
                2,
                new ApprovalDefinitionVersion(
                        1,
                        1,
                        "Sequential",
                        List.of(20L, 30L),
                        1,
                        STARTED.minusSeconds(60)
                ),
                "expense-001",
                10,
                STARTED
        );
    }
}
