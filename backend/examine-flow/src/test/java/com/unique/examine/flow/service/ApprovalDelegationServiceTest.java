package com.unique.examine.flow.service;

import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalDelegationRule;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalDelegationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final long TENANT_ID = 10L;

    @Test
    void rejectsUnauthorizedManagementOverlapCyclesAndChains() {
        var fixture = fixture(NOW);
        var definition = fixture.workflows.createDraft("Delegation", 11L);
        var first = fixture.workflows.createDelegation(
                TENANT_ID,
                11L,
                false,
                11L,
                12L,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3_600),
                definition.id()
        );
        assertThat(first.status()).isEqualTo(ApprovalDelegationRule.Status.ACTIVE);

        assertCode(() -> fixture.workflows.createDelegation(
                TENANT_ID,
                99L,
                false,
                11L,
                13L,
                NOW,
                NOW.plusSeconds(1_800),
                definition.id()
        ), ApprovalDomainException.Code.DELEGATION_FORBIDDEN);
        assertCode(() -> fixture.workflows.createDelegation(
                TENANT_ID,
                11L,
                false,
                11L,
                13L,
                NOW,
                NOW.plusSeconds(1_800),
                definition.id()
        ), ApprovalDomainException.Code.DELEGATION_CONFLICT);
        assertCode(() -> fixture.workflows.createDelegation(
                TENANT_ID,
                12L,
                false,
                12L,
                14L,
                NOW,
                NOW.plusSeconds(1_800),
                definition.id()
        ), ApprovalDomainException.Code.DELEGATION_CONFLICT);
        assertCode(() -> fixture.workflows.createDelegation(
                TENANT_ID,
                14L,
                false,
                14L,
                11L,
                NOW,
                NOW.plusSeconds(1_800),
                definition.id()
        ), ApprovalDomainException.Code.DELEGATION_CONFLICT);

        var managed = fixture.workflows.createDelegation(
                TENANT_ID,
                99L,
                true,
                21L,
                22L,
                NOW,
                NOW.plusSeconds(1_800),
                definition.id()
        );
        assertThat(managed.createdByMemberId()).isEqualTo(99L);
        assertThat(fixture.workflows.delegations(
                99L,
                true,
                21L,
                1,
                20
        ).items()).containsExactly(managed);
    }

    @Test
    void authorizesExactActiveRuleAndPreventsDirectOrDelegateDoubleVote() {
        var fixture = fixture(NOW);
        var definition = fixture.workflows.createDraft(
                "Concurrent delegation",
                List.of(11L, 12L),
                null,
                null,
                null,
                ApprovalMode.ALL
        );
        fixture.workflows.publish(definition.id());
        var rule = fixture.workflows.createDelegation(
                TENANT_ID,
                11L,
                false,
                11L,
                99L,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3_600),
                definition.id()
        );
        var instance = fixture.workflows.startLatest(
                definition.id(),
                "delegated-race",
                9L
        );

        var delegated = fixture.workflows.approve(
                instance.id(),
                99L,
                11L,
                "represented vote"
        );
        assertThat(delegated.approverIds()).containsExactly(11L, 12L);
        assertThat(delegated.decisions())
                .containsEntry(11L, ApprovalInstance.Decision.APPROVED);
        assertThat(delegated.history().getLast())
                .extracting(
                        ApprovalHistoryEvent::actorId,
                        ApprovalHistoryEvent::representedMemberId,
                        ApprovalHistoryEvent::delegationRuleId
                )
                .containsExactly(99L, 11L, rule.id());

        assertCode(() -> fixture.workflows.approve(
                instance.id(),
                11L,
                null,
                "direct duplicate"
        ), ApprovalDomainException.Code.APPROVER_FORBIDDEN);
        assertThat(fixture.workflows.instance(instance.id()).decisions()).hasSize(1);
        assertThat(fixture.workflows.instance(instance.id()).history()).hasSize(2);

        var completed = fixture.workflows.approve(
                instance.id(),
                12L,
                null,
                "second vote"
        );
        assertThat(completed.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
    }

    @Test
    void scheduledRevokedAndExpiredRulesDoNotAuthorizeAndTasksAreGrouped() {
        var repository = new InMemoryApprovalRepository();
        var sequence = new AtomicLong(500L);
        var ids = ids(sequence);
        var workflows = workflow(repository, ids, NOW);
        var definition = workflows.createDraft(
                "Grouped delegated tasks",
                List.of(11L, 21L),
                null,
                null,
                null,
                ApprovalMode.ALL
        );
        workflows.publish(definition.id());
        var first = workflows.createDelegation(
                TENANT_ID, 11L, false, 11L, 99L,
                NOW.minusSeconds(60), NOW.plusSeconds(3_600), definition.id()
        );
        workflows.createDelegation(
                TENANT_ID, 21L, false, 21L, 99L,
                NOW.minusSeconds(60), NOW.plusSeconds(3_600), definition.id()
        );
        var pending = workflows.startLatest(definition.id(), "grouped", 9L);

        var tasks = workflows.approvalTaskAssignments(
                99L,
                ApprovalTaskStatus.PENDING,
                1,
                20
        );
        assertThat(tasks.total()).isEqualTo(1);
        assertThat(tasks.items()).singleElement().satisfies(task -> {
            assertThat(task.instance().id()).isEqualTo(pending.id());
            assertThat(task.representedAuthorities())
                    .extracting(authority -> authority.representedMemberId())
                    .containsExactly(11L, 21L);
        });

        workflows.revokeDelegation(TENANT_ID, first.id(), 11L, false);
        assertCode(() -> workflows.approve(
                pending.id(),
                99L,
                11L,
                "revoked"
        ), ApprovalDomainException.Code.DELEGATION_INACTIVE);
        assertThat(workflows.instance(pending.id()).history()).hasSize(1);

        var scheduled = workflows.createDelegation(
                TENANT_ID, 31L, false, 31L, 98L,
                NOW.plusSeconds(600), NOW.plusSeconds(1_200), definition.id()
        );
        assertThat(scheduled.status()).isEqualTo(ApprovalDelegationRule.Status.SCHEDULED);
        assertCode(() -> workflows.approve(
                pending.id(),
                98L,
                31L,
                "scheduled"
        ), ApprovalDomainException.Code.DELEGATION_INACTIVE);

        var later = workflow(repository, ids, NOW.plusSeconds(7_200));
        assertCode(() -> later.approve(
                pending.id(),
                99L,
                21L,
                "expired"
        ), ApprovalDomainException.Code.DELEGATION_INACTIVE);
        assertThat(later.instance(pending.id()).history()).hasSize(1);
    }

    @Test
    void directAndDelegatedCandidatesRacingForOneSlotPersistOnlyOnce() throws Exception {
        var fixture = fixture(NOW);
        var definition = fixture.workflows.createDraft(
                "Delegated race",
                List.of(11L, 12L),
                null,
                null,
                null,
                ApprovalMode.ALL
        );
        fixture.workflows.publish(definition.id());
        var pending = fixture.workflows.startLatest(
                definition.id(),
                "concurrent-race",
                9L
        );
        var direct = pending.approve(
                11L,
                "direct",
                NOW.plusSeconds(1)
        );
        var delegated = pending.approve(
                99L,
                11L,
                900L,
                "delegated",
                NOW.plusSeconds(1)
        );
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> saveRacing(
                    fixture.repository,
                    direct,
                    ready,
                    release,
                    successes,
                    conflicts
            ));
            var second = executor.submit(() -> saveRacing(
                    fixture.repository,
                    delegated,
                    ready,
                    release,
                    successes,
                    conflicts
            ));
            ready.await();
            release.countDown();
            first.get();
            second.get();
        }

        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        var stored = fixture.workflows.instance(pending.id());
        assertThat(stored.decisions()).hasSize(1).containsKey(11L);
        assertThat(stored.history()).hasSize(2);
    }

    private static void saveRacing(
            InMemoryApprovalRepository repository,
            ApprovalInstance candidate,
            CountDownLatch ready,
            CountDownLatch release,
            AtomicInteger successes,
            AtomicInteger conflicts
    ) {
        ready.countDown();
        try {
            release.await();
            repository.saveInstance(candidate);
            successes.incrementAndGet();
        } catch (ApprovalDomainException exception) {
            if (exception.code() != ApprovalDomainException.Code.PERSISTENCE_CONFLICT) {
                throw exception;
            }
            conflicts.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static Fixture fixture(Instant now) {
        var repository = new InMemoryApprovalRepository();
        return new Fixture(
                repository,
                workflow(repository, ids(new AtomicLong(100L)), now)
        );
    }

    private static ApprovalWorkflowService workflow(
            InMemoryApprovalRepository repository,
            IdService ids,
            Instant now
    ) {
        return new ApprovalWorkflowService(
                repository,
                ids,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private static IdService ids(AtomicLong sequence) {
        return new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ApprovalDomainException.Code code
    ) {
        assertThatThrownBy(callable)
                .isInstanceOf(ApprovalDomainException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private record Fixture(
            InMemoryApprovalRepository repository,
            ApprovalWorkflowService workflows
    ) {
    }
}
