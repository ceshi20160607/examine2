package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApprovalInstanceRecordBindingTest {
    private static final Instant STARTED = Instant.parse("2026-07-27T12:00:00Z");

    @Test
    void bindingIsAnImmutableSnapshotAcrossPendingAndTerminalTransitions() {
        var binding = new ApprovalInstance.RecordBinding("purchase_order", 901L);
        var instance = ApprovalInstance.start(
                301L,
                definition(List.of(20L, 30L)),
                "PO-2026-0008",
                10L,
                STARTED,
                binding
        );

        var handedOff = instance.approve(20L, "finance", STARTED.plusSeconds(10));
        var completed = handedOff.approve(30L, "done", STARTED.plusSeconds(20));

        assertThat(instance.recordBinding()).isEqualTo(binding);
        assertThat(handedOff.recordBinding()).isSameAs(binding);
        assertThat(handedOff.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(completed.recordBinding()).isSameAs(binding);
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
    }

    @Test
    void bindingRejectsNonCanonicalModuleCodesAndNonPositiveRecordIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ApprovalInstance.RecordBinding("bad-code", 901L));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ApprovalInstance.RecordBinding("purchase_order", 0L));
    }

    private static ApprovalDefinitionVersion definition(List<Long> approvers) {
        return new ApprovalDefinitionVersion(
                101L,
                1,
                "Approval",
                approvers,
                1,
                STARTED.minusSeconds(1)
        );
    }
}
