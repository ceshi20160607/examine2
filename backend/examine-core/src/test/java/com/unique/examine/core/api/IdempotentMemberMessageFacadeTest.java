package com.unique.examine.core.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentMemberMessageFacadeTest {
    @Test
    void commandFreezesDeliveryIdentityAndSystemLocalTarget() throws Exception {
        var method = IdempotentMemberMessageFacade.class.getMethod(
                "deliver", IdempotentMemberMessageFacade.Command.class);
        assertThat(method.getReturnType()).isEqualTo(long.class);
        assertThat(IdempotentMemberMessageFacade.Command.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly(
                        "deliveryKey", "systemId", "tenantId", "recipientMemberId",
                        "sourceType", "title", "body", "target", "targetPath");

        var command = command(
                " kpi-reminder:calculation:42:member:101 ",
                10, 20, 101, " KPI_TARGET_MISSED ", " KPI missed ", " Review target ",
                target(), " /systems/10/kpis?periodType=MONTH&periodStart=2026-08-01 ");

        assertThat(command.deliveryKey()).isEqualTo("kpi-reminder:calculation:42:member:101");
        assertThat(command.sourceType()).isEqualTo("KPI_TARGET_MISSED");
        assertThat(command.title()).isEqualTo("KPI missed");
        assertThat(command.body()).isEqualTo("Review target");
        assertThat(command.targetPath())
                .isEqualTo("/systems/10/kpis?periodType=MONTH&periodStart=2026-08-01");
    }

    @Test
    void commandRejectsInvalidScopePayloadTargetAndCrossSystemPath() {
        assertInvalid(() -> command("key", 0, 20, 101, "TYPE", "Title", "Body", target(), path()));
        assertInvalid(() -> command("key", 10, 0, 101, "TYPE", "Title", "Body", target(), path()));
        assertInvalid(() -> command("key", 10, 20, 0, "TYPE", "Title", "Body", target(), path()));
        assertInvalid(() -> command(" ", 10, 20, 101, "TYPE", "Title", "Body", target(), path()));
        assertInvalid(() -> command("key", 10, 20, 101, " ", "Title", "Body", target(), path()));
        assertInvalid(() -> command("key", 10, 20, 101, "TYPE", null, "Body", target(), path()));
        assertInvalid(() -> command("key", 10, 20, 101, "TYPE", "Title", "\t", target(), path()));
        assertInvalid(() -> command("key", 10, 20, 101, "TYPE", "Title", "Body", null, path()));
        assertInvalid(() -> command("key", 10, 20, 101, "TYPE", "Title", "Body", target(),
                "/systems/11/kpis"));
    }

    private static IdempotentMemberMessageFacade.Command command(
            String deliveryKey,
            long systemId,
            long tenantId,
            long recipientMemberId,
            String sourceType,
            String title,
            String body,
            AggregateRef target,
            String targetPath
    ) {
        return new IdempotentMemberMessageFacade.Command(
                deliveryKey, systemId, tenantId, recipientMemberId,
                sourceType, title, body, target, targetPath);
    }

    private static AggregateRef target() {
        return new AggregateRef("KPI_CALCULATION", "42");
    }

    private static String path() {
        return "/systems/10/kpis?periodType=MONTH&periodStart=2026-08-01";
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IllegalArgumentException.class);
    }
}
