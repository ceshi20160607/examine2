package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.TriggerCondition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalRouteResolverTest {
    private final ApprovalRouteResolver resolver = new ApprovalRouteResolver(
            new TriggerConditionMatcher(new ObjectMapper())
    );

    @Test
    void resolvesFirstMatchingBranchOtherwiseDefault() {
        var gateway = new ApprovalGateway(List.of(
                branch("urgent", "Urgent", "urgent", "true", 20),
                branch("large", "Large amount", "amount", "100", 30),
                new ApprovalGateway.Branch(
                        "default",
                        "Default",
                        true,
                        List.of(),
                        List.of(40L)
                )
        ));

        var first = resolver.resolve(
                gateway,
                List.of(40L),
                Map.of("urgent", "true", "amount", "100")
        );
        var second = resolver.resolve(gateway, List.of(40L), Map.of("amount", "100"));
        var fallback = resolver.resolve(gateway, List.of(40L), Map.of());

        assertThat(first.branchCode()).isEqualTo("urgent");
        assertThat(first.approverIds()).containsExactly(20L);
        assertThat(second.branchCode()).isEqualTo("large");
        assertThat(second.approverIds()).containsExactly(30L);
        assertThat(fallback.branchCode()).isEqualTo("default");
        assertThat(fallback.defaultBranch()).isTrue();
        assertThat(fallback.approverIds()).containsExactly(40L);
    }

    private static ApprovalGateway.Branch branch(
            String code,
            String name,
            String field,
            String value,
            long approverId
    ) {
        return new ApprovalGateway.Branch(
                code,
                name,
                false,
                List.of(new TriggerCondition(field, TriggerCondition.Operator.EQ, value)),
                List.of(approverId)
        );
    }
}
