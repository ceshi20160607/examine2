package com.unique.examine.flow.interaction;

import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COPY_MESSAGE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COPY_TARGET_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowCopyDomainTest {
    private static final Instant NOW = Instant.parse("2026-07-27T15:00:00Z");

    @Test
    void trimsOptionalMessageAndKeepsImmutableInstanceFacts() {
        var instance = pending();
        var copy = FlowCopy.create(3, instance, 10, 30, "  Follow this outcome  ", NOW);
        var withoutMessage = FlowCopy.create(4, instance, 10, 40, null, NOW);

        assertThat(copy.instanceId()).isEqualTo(instance.id());
        assertThat(copy.actorId()).isEqualTo(10);
        assertThat(copy.recipientId()).isEqualTo(30);
        assertThat(copy.message()).isEqualTo("Follow this outcome");
        assertThat(withoutMessage.message()).isEmpty();
        assertThat(instance.history()).hasSize(1);
        assertThat(instance.status()).isEqualTo(ApprovalInstance.Status.PENDING);
    }

    @Test
    void rejectsSelfTargetAndOversizedUnicodeMessage() {
        assertThatThrownBy(() -> FlowCopy.create(3, pending(), 10, 10, "", NOW))
                .isInstanceOfSatisfying(
                        ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(COPY_TARGET_INVALID)
                );
        assertThatThrownBy(() ->
                FlowCopy.create(3, pending(), 10, 30, "😀".repeat(501), NOW))
                .isInstanceOfSatisfying(
                        ApprovalDomainException.class,
                        error -> assertThat(error.code()).isEqualTo(COPY_MESSAGE_INVALID)
                );
        assertThat(FlowCopy.create(3, pending(), 10, 30, "😀".repeat(500), NOW).message())
                .hasSize(1000);
    }

    private static ApprovalInstance pending() {
        return ApprovalInstance.start(
                2,
                new ApprovalDefinitionVersion(1, 1, "Approval", 20, 1, NOW.minusSeconds(60)),
                "expense-001",
                10,
                NOW
        );
    }
}
