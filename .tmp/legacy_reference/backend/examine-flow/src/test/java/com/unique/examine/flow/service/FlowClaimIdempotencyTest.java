package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowClaimIdempotencyTest {
    @Test
    void returnCancelAndClaimReplayWithoutDuplicatingHistory() throws Exception {
        var fixture = fixture();
        var started = fixture.start();
        fixture.workflows.approve(started.id(), 20, "first");

        var returnSession = new FlowSession(1, 2, 30, Set.of("flow.instance.return"));
        var returned = fixture.mutations.returnToPrevious(
                returnSession,
                started.id(),
                new FlowRequests.Return("correct"),
                "return-key"
        );
        var returnReplay = fixture.mutations.returnToPrevious(
                returnSession,
                started.id(),
                new FlowRequests.Return("correct"),
                "return-key"
        );
        assertThat(returnReplay).isEqualTo(returned);
        assertDifferentBodyConflicts(() -> fixture.mutations.returnToPrevious(
                returnSession,
                started.id(),
                new FlowRequests.Return("different"),
                "return-key"
        ));

        var ownerSession = new FlowSession(
                1,
                2,
                20,
                Set.of("flow.instance.cancel-claim")
        );
        var open = fixture.mutations.cancelClaim(
                ownerSession,
                started.id(),
                new FlowRequests.CancelClaim("release"),
                "cancel-key"
        );
        var cancelReplay = fixture.mutations.cancelClaim(
                ownerSession,
                started.id(),
                new FlowRequests.CancelClaim("release"),
                "cancel-key"
        );
        assertThat(cancelReplay).isEqualTo(open);

        var claimant = new FlowSession(1, 2, 40, Set.of("flow.instance.claim"));
        var claimed = fixture.mutations.claim(
                claimant,
                started.id(),
                new FlowRequests.Claim("mine"),
                "claim-key"
        );
        var claimReplay = fixture.mutations.claim(
                claimant,
                started.id(),
                new FlowRequests.Claim("mine"),
                "claim-key"
        );
        assertThat(claimReplay).isEqualTo(claimed);
        assertDifferentBodyConflicts(() -> fixture.mutations.claim(
                claimant,
                started.id(),
                new FlowRequests.Claim("different"),
                "claim-key"
        ));

        assertThat(fixture.workflows.history(started.id()))
                .extracting(ApprovalHistoryEvent::type)
                .containsExactly(
                        ApprovalHistoryEvent.Type.STARTED,
                        ApprovalHistoryEvent.Type.APPROVED,
                        ApprovalHistoryEvent.Type.RETURNED,
                        ApprovalHistoryEvent.Type.CLAIM_CANCELLED,
                        ApprovalHistoryEvent.Type.CLAIMED
                );
        assertTransactional("returnToPrevious", FlowRequests.Return.class);
        assertTransactional("cancelClaim", FlowRequests.CancelClaim.class);
        assertTransactional("claim", FlowRequests.Claim.class);
    }

    private static void assertDifferentBodyConflicts(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        com.unique.examine.core.error.BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("IDEMPOTENCY_CONFLICT")
                );
    }

    private static void assertTransactional(String method, Class<?> requestType) throws Exception {
        assertThat(FlowMutationService.class.getMethod(
                        method,
                        FlowSession.class,
                        long.class,
                        requestType,
                        String.class
                ).getAnnotation(Transactional.class))
                .isNotNull();
    }

    private static Fixture fixture() {
        var sequence = new AtomicLong(100);
        var workflows = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                },
                Clock.fixed(Instant.parse("2026-07-27T13:00:00Z"), ZoneOffset.UTC)
        );
        var mutations = new FlowMutationService(
                (systemId, tenantId) -> workflows,
                new MemoryIdempotency(),
                new ObjectMapper(),
                (systemId, tenantId, memberId) -> Optional.empty()
        );
        return new Fixture(workflows, mutations);
    }

    private record Fixture(
            ApprovalWorkflowService workflows,
            FlowMutationService mutations
    ) {
        private com.unique.examine.flow.domain.ApprovalInstance start() {
            var draft = workflows.createDraft("Sequential", java.util.List.of(20L, 30L));
            workflows.publish(draft.id());
            return workflows.startLatest(draft.id(), "expense-001", 10);
        }
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final Map<Key, IdempotencyRecord> records = new HashMap<>();
        private final Map<Long, Key> keysById = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong(1000);

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(new Key(scopeType, scopeKey, key)));
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                Duration ttl
        ) {
            var id = sequence.incrementAndGet();
            var compound = new Key(scopeType, scopeKey, key);
            records.put(compound, new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            keysById.put(id, compound);
            return id;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            var key = keysById.get(id);
            var record = records.get(key);
            records.put(key, new IdempotencyRecord(
                    id,
                    record.requestHash(),
                    "COMPLETED",
                    responseBody
            ));
        }
    }

    private record Key(String scopeType, String scopeKey, String key) {
    }
}
