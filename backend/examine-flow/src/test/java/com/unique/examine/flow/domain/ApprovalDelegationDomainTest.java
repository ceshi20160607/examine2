package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalDelegationDomainTest {
    private static final Instant CREATED = Instant.parse("2026-07-30T10:00:00Z");
    private static final Instant STARTED = CREATED.plusSeconds(60);

    @Test
    void validatesBoundsAndMaintainsImmutableTemporalAndRevokeAudit() {
        assertThatThrownBy(() -> ApprovalDelegationRule.create(
                1L, 10L, 11L, 11L,
                CREATED, CREATED.plusSeconds(1), null, 11L, CREATED
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.DELEGATION_RULE_INVALID);
        assertThatThrownBy(() -> ApprovalDelegationRule.create(
                1L, 10L, 11L, 12L,
                CREATED,
                CREATED.plus(ApprovalDelegationRule.MAX_DURATION).plusSeconds(1),
                null,
                11L,
                CREATED
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.DELEGATION_RULE_INVALID);

        var scheduled = ApprovalDelegationRule.create(
                1L,
                10L,
                11L,
                12L,
                CREATED.plusSeconds(60),
                CREATED.plus(Duration.ofDays(1)),
                101L,
                11L,
                CREATED
        );
        assertThat(scheduled.status()).isEqualTo(ApprovalDelegationRule.Status.SCHEDULED);
        assertThat(scheduled.at(CREATED.plusSeconds(120)).status())
                .isEqualTo(ApprovalDelegationRule.Status.ACTIVE);
        assertThat(scheduled.at(CREATED.plus(Duration.ofDays(2))).status())
                .isEqualTo(ApprovalDelegationRule.Status.EXPIRED);

        var revoked = scheduled.revoke(11L, CREATED.plusSeconds(30));
        assertThat(revoked.status()).isEqualTo(ApprovalDelegationRule.Status.REVOKED);
        assertThat(revoked.revokedByMemberId()).isEqualTo(11L);
        assertThat(revoked.revokedAt()).isEqualTo(CREATED.plusSeconds(30));
        assertThat(revoked.at(CREATED.plusSeconds(120))).isSameAs(revoked);
    }

    @Test
    void delegatedSequentialDecisionPreservesParticipantAndAuditsActualActor() {
        var pending = ApprovalInstance.start(
                201L,
                definition(List.of(11L), ApprovalMode.SEQUENTIAL),
                "delegated-sequential",
                9L,
                STARTED
        );

        var approved = pending.approve(
                99L,
                11L,
                700L,
                "on behalf",
                STARTED.plusSeconds(1)
        );
        assertThat(approved.approverIds()).containsExactly(11L);
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.history().getLast())
                .extracting(
                        ApprovalHistoryEvent::actorId,
                        ApprovalHistoryEvent::representedMemberId,
                        ApprovalHistoryEvent::delegationRuleId
                )
                .containsExactly(99L, 11L, 700L);
    }

    @Test
    void concurrentVotesAreKeyedByRepresentedMemberAcrossDelegatesAndDirectRetry() {
        for (var mode : List.of(
                ApprovalMode.ALL,
                ApprovalMode.QUORUM
        )) {
            var pending = ApprovalInstance.start(
                    mode == ApprovalMode.ALL ? 202L : 203L,
                    definition(List.of(11L, 12L, 13L), mode),
                    "delegated-" + mode,
                    9L,
                    STARTED
            );
            var represented = pending.approve(
                    99L,
                    11L,
                    701L,
                    "delegate vote",
                    STARTED.plusSeconds(1)
            );
            assertThat(represented.decisions())
                    .containsOnly(Map.entry(11L, ApprovalInstance.Decision.APPROVED));

            assertThatThrownBy(() -> represented.approve(
                    11L,
                    "direct duplicate",
                    STARTED.plusSeconds(2)
            ))
                    .isInstanceOf(ApprovalDomainException.class)
                    .extracting("code")
                    .isEqualTo(ApprovalDomainException.Code.APPROVER_FORBIDDEN);
            assertThatThrownBy(() -> represented.reject(
                    98L,
                    11L,
                    702L,
                    "other delegate duplicate",
                    STARTED.plusSeconds(3)
            ))
                    .isInstanceOf(ApprovalDomainException.class)
                    .extracting("code")
                    .isEqualTo(ApprovalDomainException.Code.APPROVER_FORBIDDEN);
            assertThat(represented.decisions()).hasSize(1);
            assertThat(represented.history()).hasSize(2);
        }

        var any = ApprovalInstance.start(
                204L,
                definition(List.of(11L, 12L), ApprovalMode.ANY),
                "delegated-any",
                9L,
                STARTED
        ).approve(99L, 11L, 703L, "any", STARTED.plusSeconds(1));
        assertThat(any.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(any.approvedApproverIds()).containsExactly(11L);
    }

    @Test
    void branchAuthorityCannotCrossParallelOrInclusiveBranchBoundaries() {
        var gateway = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance",
                        "Finance",
                        List.of(11L),
                        ApprovalMode.SEQUENTIAL
                ),
                new ApprovalParallelGateway.Branch(
                        "owner",
                        "Owner",
                        List.of(21L),
                        ApprovalMode.SEQUENTIAL
                )
        ));
        var definition = new ApprovalDefinitionVersion(
                102L,
                1,
                "Delegated parallel",
                gateway.branches().getFirst().approverIds(),
                1,
                CREATED,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                gateway
        );
        var pending = ApprovalInstance.start(
                205L,
                definition,
                "delegated-parallel",
                9L,
                STARTED
        );

        assertThatThrownBy(() -> pending.approveBranch(
                "finance",
                99L,
                21L,
                704L,
                "wrong branch",
                STARTED.plusSeconds(1)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVER_FORBIDDEN);
        assertThat(pending.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.PENDING);
        assertThat(pending.parallelBranch("owner").status())
                .isEqualTo(ApprovalBranchExecution.Status.PENDING);
        assertThat(pending.history()).hasSize(1);

        var finance = pending.approveBranch(
                "finance",
                99L,
                11L,
                705L,
                "finance delegated",
                STARTED.plusSeconds(2)
        );
        assertThat(finance.parallelBranch("finance").status())
                .isEqualTo(ApprovalBranchExecution.Status.APPROVED);
        assertThat(finance.history().getLast().representedMemberId()).isEqualTo(11L);
        assertThat(finance.history().getLast().delegationRuleId()).isEqualTo(705L);

        var inclusiveGateway = new ApprovalInclusiveGateway(List.of(
                new ApprovalInclusiveGateway.Branch(
                        "risk",
                        "Risk",
                        false,
                        List.of(new TriggerCondition(
                                "amount",
                                TriggerCondition.Operator.GT,
                                "100"
                        )),
                        List.of(31L),
                        ApprovalMode.SEQUENTIAL
                ),
                new ApprovalInclusiveGateway.Branch(
                        "fallback",
                        "Fallback",
                        true,
                        List.of(),
                        List.of(41L),
                        ApprovalMode.SEQUENTIAL
                )
        ));
        var inclusiveDefinition = new ApprovalDefinitionVersion(
                103L,
                1,
                "Delegated inclusive",
                inclusiveGateway.branches().getFirst().approverIds(),
                1,
                CREATED,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                inclusiveGateway
        );
        var inclusive = ApprovalInstance.startBranches(
                206L,
                inclusiveDefinition,
                inclusiveGateway.branches(),
                "delegated-inclusive",
                9L,
                STARTED,
                null
        );
        assertThatThrownBy(() -> inclusive.approveBranch(
                "risk",
                98L,
                41L,
                706L,
                "wrong inclusive branch",
                STARTED.plusSeconds(3)
        ))
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(ApprovalDomainException.Code.APPROVER_FORBIDDEN);
        assertThat(inclusive.parallelBranches())
                .extracting(ApprovalBranchExecution::status)
                .containsOnly(ApprovalBranchExecution.Status.PENDING);
    }

    private static ApprovalDefinitionVersion definition(
            List<Long> approverIds,
            ApprovalMode mode
    ) {
        var quorum = mode == ApprovalMode.QUORUM
                ? new ApprovalQuorumRules(
                        new ApprovalQuorumRule(ApprovalQuorumRule.Type.COUNT, 2),
                        Map.of()
                )
                : null;
        return ApprovalDefinitionVersion.publish(
                new ApprovalDefinitionDraft(
                        101L,
                        "Delegated route",
                        approverIds,
                        1,
                        CREATED,
                        null,
                        null,
                        null,
                        mode,
                        null,
                        null,
                        null,
                        quorum,
                        null,
                        null
                ),
                1,
                CREATED
        );
    }
}
