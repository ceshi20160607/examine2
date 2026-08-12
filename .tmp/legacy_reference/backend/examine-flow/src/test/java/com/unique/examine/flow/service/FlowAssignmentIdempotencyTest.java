package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowHttpErrors;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowAssignmentIdempotencyTest {
    @Test
    void transferReplayDoesNotDuplicateHistoryOrMemberLookupAndDifferentBodyConflicts() throws Exception {
        var fixture = fixture(Set.of(30L));
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 20, Set.of("flow.instance.transfer"));

        var first = fixture.mutations.transfer(
                session,
                pending.id(),
                new FlowRequests.Transfer("30", "owning reviewer"),
                "transfer-key"
        );
        var replay = fixture.mutations.transfer(
                session,
                pending.id(),
                new FlowRequests.Transfer("30", "owning reviewer"),
                "transfer-key"
        );
        var conflict = catchThrowableOfType(
                () -> fixture.mutations.transfer(
                        session,
                        pending.id(),
                        new FlowRequests.Transfer("30", "different"),
                        "transfer-key"
                ),
                BusinessException.class
        );

        assertThat(replay).isEqualTo(first);
        assertThat(first.approverIds()).containsExactly("30", "40");
        assertThat(fixture.workflows.history(pending.id())).hasSize(2);
        assertThat(fixture.members.calls).hasValue(1);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(FlowMutationService.class.getMethod(
                        "transfer",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Transfer.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();

        var afterTarget = fixture.workflows.approve(pending.id(), 30, "done");
        assertThat(afterTarget.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(afterTarget.approverId()).isEqualTo(40);
    }

    @Test
    void nonCurrentActorIsRejectedBeforeActiveMemberLookup() {
        var fixture = fixture(Set.of(30L));
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 21, Set.of("flow.instance.transfer"));

        var forbidden = catchThrowableOfType(
                () -> FlowHttpErrors.execute(() -> fixture.mutations.transfer(
                        session,
                        pending.id(),
                        new FlowRequests.Transfer("30", "not mine"),
                        "transfer-forbidden"
                )),
                BusinessException.class
        );

        assertThat(forbidden.code()).isEqualTo("FLOW_APPROVER_FORBIDDEN");
        assertThat(fixture.members.calls).hasValue(0);
        assertThat(fixture.workflows.history(pending.id())).hasSize(1);
    }

    @Test
    void addSignRequiresLockedActiveTenantMemberAndReplaysWithoutDuplication() throws Exception {
        var fixture = fixture(Set.of(30L));
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 20, Set.of("flow.instance.add-sign"));

        var inactive = catchThrowableOfType(
                () -> FlowHttpErrors.execute(() -> fixture.mutations.addSign(
                        session,
                        pending.id(),
                        new FlowRequests.AddSign("99", "BEFORE", "security"),
                        "add-inactive"
                )),
                BusinessException.class
        );
        assertThat(inactive.code()).isEqualTo("FLOW_ASSIGNMENT_REQUEST_INVALID");

        var first = fixture.mutations.addSign(
                session,
                pending.id(),
                new FlowRequests.AddSign("30", "BEFORE", "security"),
                "add-key"
        );
        var replay = fixture.mutations.addSign(
                session,
                pending.id(),
                new FlowRequests.AddSign("30", "BEFORE", "security"),
                "add-key"
        );

        assertThat(replay).isEqualTo(first);
        assertThat(first.approverIds()).containsExactly("30", "20", "40");
        assertThat(first.approverId()).isEqualTo("30");
        assertThat(fixture.workflows.history(pending.id())).hasSize(2);
        assertThat(fixture.members.calls).hasValue(2);
        assertThat(FlowMutationService.class.getMethod(
                        "addSign",
                        FlowSession.class,
                        long.class,
                        FlowRequests.AddSign.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void reduceSignReplayDoesNotRemoveASecondStepAndChangedBodyConflicts() throws Exception {
        var fixture = fixture(Set.of());
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 20, Set.of("flow.instance.reduce-sign"));

        var first = fixture.mutations.reduceSign(
                session,
                pending.id(),
                new FlowRequests.ReduceSign(1, "not required"),
                "reduce-key"
        );
        var replay = fixture.mutations.reduceSign(
                session,
                pending.id(),
                new FlowRequests.ReduceSign(1, "not required"),
                "reduce-key"
        );
        var conflict = catchThrowableOfType(
                () -> fixture.mutations.reduceSign(
                        session,
                        pending.id(),
                        new FlowRequests.ReduceSign(1, "different"),
                        "reduce-key"
                ),
                BusinessException.class
        );

        assertThat(replay).isEqualTo(first);
        assertThat(first.approverIds()).containsExactly("20");
        assertThat(fixture.workflows.history(pending.id())).hasSize(2);
        assertThat(fixture.workflows.history(pending.id()).getLast().targetStepIndex())
                .isEqualTo(1);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(fixture.workflows.approve(pending.id(), 20, "done").status())
                .isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(FlowMutationService.class.getMethod(
                        "reduceSign",
                        FlowSession.class,
                        long.class,
                        FlowRequests.ReduceSign.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private static Fixture fixture(Set<Long> activeMemberIds) {
        var sequence = new AtomicLong(100);
        var ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var workflows = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                ids,
                Clock.fixed(Instant.parse("2026-07-27T11:00:00Z"), ZoneOffset.UTC)
        );
        var members = new RecordingActiveMembers(activeMemberIds);
        var mutations = new FlowMutationService(
                (systemId, tenantId) -> workflows,
                new MemoryIdempotency(),
                new ObjectMapper(),
                members
        );
        return new Fixture(workflows, mutations, members);
    }

    private record Fixture(
            ApprovalWorkflowService workflows,
            FlowMutationService mutations,
            RecordingActiveMembers members
    ) {
        private ApprovalInstance start() {
            var draft = workflows.createDraft("Sequential", java.util.List.of(20L, 40L));
            workflows.publish(draft.id());
            return workflows.startLatest(draft.id(), "expense-001", 10);
        }
    }

    private static final class RecordingActiveMembers implements RuntimeActiveMemberFacade {
        private final Set<Long> activeMemberIds;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingActiveMembers(Set<Long> activeMemberIds) {
            this.activeMemberIds = new HashSet<>(activeMemberIds);
        }

        @Override
        public Optional<ActiveMember> lockActiveMember(
                long systemId,
                long tenantId,
                long memberId
        ) {
            calls.incrementAndGet();
            return activeMemberIds.contains(memberId)
                    ? Optional.of(new ActiveMember(memberId, null))
                    : Optional.empty();
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
        public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) {
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
            records.put(key, new IdempotencyRecord(id, record.requestHash(), "COMPLETED", responseBody));
        }
    }

    private record Key(String scopeType, String scopeKey, String key) {
    }
}
