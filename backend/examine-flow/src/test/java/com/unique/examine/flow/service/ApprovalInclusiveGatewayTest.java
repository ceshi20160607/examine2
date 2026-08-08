package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalInclusiveGateway;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalBranchExecution;
import com.unique.examine.flow.domain.ApprovalQuorumRule;
import com.unique.examine.flow.domain.ApprovalQuorumRules;
import com.unique.examine.flow.domain.TriggerCondition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalInclusiveGatewayTest {
    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-29T17:00:00Z");
    private static final Instant STARTED_AT = PUBLISHED_AT.plusSeconds(60);
    private final ApprovalInclusiveRouteResolver resolver =
            new ApprovalInclusiveRouteResolver(
                    new TriggerConditionMatcher(new ObjectMapper())
            );

    @Test
    void selectsEveryMatchAndUsesDefaultOnlyWhenNoneMatch() {
        var gateway = gateway(true);

        assertThat(resolver.resolve(
                gateway,
                Map.of("urgent", "true", "amount", "5000")
        ))
                .extracting(ApprovalInclusiveGateway.Branch::code)
                .containsExactly("urgent", "large");
        assertThat(resolver.resolve(gateway, Map.of("urgent", "true")))
                .extracting(ApprovalInclusiveGateway.Branch::code)
                .containsExactly("urgent");
        assertThat(resolver.resolve(gateway, Map.of()))
                .extracting(ApprovalInclusiveGateway.Branch::code)
                .containsExactly("default");
        assertThat(resolver.resolve(gateway(false), Map.of())).isEmpty();
    }

    @Test
    void joinsOnlySelectedBranchesAndRejectsFailFast() {
        var definition = definition();
        var selected = resolver.resolve(
                definition.inclusiveGateway(),
                Map.of("urgent", "true", "amount", "5000")
        );
        var pending = ApprovalInstance.startBranches(
                201L,
                definition,
                selected,
                "inclusive-1",
                9L,
                STARTED_AT,
                null
        );

        assertThat(pending.parallelBranches())
                .extracting(ApprovalBranchExecution::code)
                .containsExactly("urgent", "large");
        assertThat(pending.activeApproverIds()).containsExactly(11L, 21L, 22L);

        var urgentApproved = pending.approveBranch(
                "urgent",
                11L,
                "urgent accepted",
                STARTED_AT.plusSeconds(1)
        );
        assertThat(urgentApproved.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        var joined = urgentApproved.approveBranch(
                "large",
                21L,
                "one large reviewer accepted",
                STARTED_AT.plusSeconds(2)
        );
        assertThat(joined.status()).isEqualTo(ApprovalInstance.Status.APPROVED);

        var largePartiallyRejected = ApprovalInstance.startBranches(
                202L,
                definition,
                selected,
                "inclusive-2",
                9L,
                STARTED_AT,
                null
        ).rejectBranch(
                "large",
                21L,
                "large route declined",
                STARTED_AT.plusSeconds(3)
        );
        assertThat(largePartiallyRejected.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        var rejected = largePartiallyRejected.rejectBranch(
                "large",
                22L,
                "all large reviewers declined",
                STARTED_AT.plusSeconds(4)
        );
        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.parallelBranch("urgent").status())
                .isEqualTo(ApprovalBranchExecution.Status.CANCELLED);
        assertThat(rejected.parallelBranch("large").status())
                .isEqualTo(ApprovalBranchExecution.Status.REJECTED);
    }

    @Test
    void supportsOneSelectedBranchWithoutInventingUnselectedExecutions() {
        var definition = definition();
        var selected = resolver.resolve(
                definition.inclusiveGateway(),
                Map.of("urgent", "true")
        );
        var pending = ApprovalInstance.startBranches(
                203L,
                definition,
                selected,
                "inclusive-single",
                9L,
                STARTED_AT,
                null
        );

        assertThat(pending.parallelBranches()).hasSize(1);
        assertThat(pending.approveBranch(
                "urgent",
                11L,
                "single selected route",
                STARTED_AT.plusSeconds(1)
        ).status()).isEqualTo(ApprovalInstance.Status.APPROVED);
    }

    @Test
    void selectedQuorumBranchUsesEarlyApprovalAndImpossibleRejection() {
        var definition = quorumDefinition();
        var selected = resolver.resolve(
                definition.inclusiveGateway(),
                Map.of("urgent", "true", "amount", "5000")
        );
        var pending = ApprovalInstance.startBranches(
                301L,
                definition,
                selected,
                "inclusive-quorum",
                9L,
                STARTED_AT,
                null
        );
        assertThat(pending.parallelBranch("large").requiredApprovals()).isEqualTo(2);

        var urgentDone = pending.approveBranch(
                "urgent",
                11L,
                "urgent accepted",
                STARTED_AT.plusSeconds(1)
        );
        var oneVote = urgentDone.approveBranch(
                "large",
                21L,
                "first large vote",
                STARTED_AT.plusSeconds(2)
        );
        assertThat(oneVote.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(oneVote.approveBranch(
                "large",
                22L,
                "large quorum reached",
                STARTED_AT.plusSeconds(3)
        ).status()).isEqualTo(ApprovalInstance.Status.APPROVED);

        var oneRejection = ApprovalInstance.startBranches(
                302L,
                definition,
                selected,
                "inclusive-quorum-reject",
                9L,
                STARTED_AT,
                null
        ).rejectBranch(
                "large",
                21L,
                "first large rejection",
                STARTED_AT.plusSeconds(4)
        );
        assertThat(oneRejection.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        var impossible = oneRejection.rejectBranch(
                "large",
                22L,
                "large quorum impossible",
                STARTED_AT.plusSeconds(5)
        );
        assertThat(impossible.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(impossible.parallelBranch("urgent").status())
                .isEqualTo(ApprovalBranchExecution.Status.CANCELLED);
    }

    private static ApprovalDefinitionVersion definition() {
        var gateway = gateway(true);
        return new ApprovalDefinitionVersion(
                101L,
                1,
                "Inclusive approval",
                gateway.branches().getFirst().approverIds(),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                gateway.branches().getFirst().approvalMode(),
                null,
                gateway
        );
    }

    private static ApprovalInclusiveGateway gateway(boolean withDefault) {
        var urgent = new ApprovalInclusiveGateway.Branch(
                "urgent",
                "Urgent",
                false,
                List.of(new TriggerCondition(
                        "urgent",
                        TriggerCondition.Operator.EQ,
                        "true"
                )),
                List.of(11L),
                ApprovalMode.SEQUENTIAL
        );
        var large = new ApprovalInclusiveGateway.Branch(
                "large",
                "Large amount",
                false,
                List.of(new TriggerCondition(
                        "amount",
                        TriggerCondition.Operator.GTE,
                        "1000"
                )),
                List.of(21L, 22L),
                ApprovalMode.ANY
        );
        if (!withDefault) {
            return new ApprovalInclusiveGateway(List.of(urgent, large));
        }
        return new ApprovalInclusiveGateway(List.of(
                urgent,
                large,
                new ApprovalInclusiveGateway.Branch(
                        "default",
                        "Default",
                        true,
                        List.of(),
                        List.of(31L),
                        ApprovalMode.SEQUENTIAL
                )
        ));
    }

    private static ApprovalDefinitionVersion quorumDefinition() {
        var rule = new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 2);
        var gateway = new ApprovalInclusiveGateway(List.of(
                new ApprovalInclusiveGateway.Branch(
                        "urgent",
                        "Urgent",
                        false,
                        List.of(new TriggerCondition(
                                "urgent",
                                TriggerCondition.Operator.EQ,
                                "true"
                        )),
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL
                ),
                new ApprovalInclusiveGateway.Branch(
                        "large",
                        "Large amount",
                        false,
                        List.of(new TriggerCondition(
                                "amount",
                                TriggerCondition.Operator.GTE,
                                "1000"
                        )),
                        List.of(21L, 22L, 23L),
                        ApprovalMode.QUORUM
                ),
                new ApprovalInclusiveGateway.Branch(
                        "default",
                        "Default",
                        true,
                        List.of(),
                        List.of(31L),
                        ApprovalMode.SEQUENTIAL
                )
        ));
        return new ApprovalDefinitionVersion(
                201L,
                1,
                "Inclusive quorum approval",
                gateway.branches().getFirst().approverIds(),
                1,
                PUBLISHED_AT,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                gateway,
                null,
                new ApprovalQuorumRules(null, Map.of("large", rule))
        );
    }
}
