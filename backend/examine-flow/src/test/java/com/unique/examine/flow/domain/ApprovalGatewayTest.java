package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalGatewayTest {
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void publishSnapshotsOrderedBranchesAndDefaultRoute() {
        var gateway = gateway();
        var draft = new ApprovalDefinitionDraft(
                101,
                "Expense approval",
                List.of(30L),
                1,
                NOW,
                null,
                null,
                gateway
        );

        var version = ApprovalDefinitionVersion.publish(draft, 1, NOW.plusSeconds(1));

        assertThat(version.gateway()).isEqualTo(gateway);
        assertThat(version.gateway().conditionalBranches())
                .extracting(ApprovalGateway.Branch::code)
                .containsExactly("urgent");
        assertThat(version.gateway().defaultBranch().approverIds()).containsExactly(30L);
    }

    @Test
    void requiresOneToFiveConditionsAndOneFinalDefault() {
        assertThatThrownBy(() -> new ApprovalGateway(List.of(
                new ApprovalGateway.Branch(
                        "default",
                        "Default",
                        true,
                        List.of(),
                        List.of(30L)
                ),
                new ApprovalGateway.Branch(
                        "urgent",
                        "Urgent",
                        false,
                        List.of(condition()),
                        List.of(20L)
                )
        )))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("ordered last");

        assertThatThrownBy(() -> new ApprovalGateway.Branch(
                "missing_condition",
                "Missing condition",
                false,
                List.of(),
                List.of(20L)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("requires at least one");
    }

    private static ApprovalGateway gateway() {
        return new ApprovalGateway(List.of(
                new ApprovalGateway.Branch(
                        "urgent",
                        "Urgent",
                        false,
                        List.of(condition()),
                        List.of(20L)
                ),
                new ApprovalGateway.Branch(
                        "default",
                        "Default",
                        true,
                        List.of(),
                        List.of(30L)
                )
        ));
    }

    private static TriggerCondition condition() {
        return new TriggerCondition("urgent", TriggerCondition.Operator.EQ, "true");
    }
}
