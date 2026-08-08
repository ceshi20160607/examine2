package com.unique.examine.flow.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowHttpErrors;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.interaction.memory.InMemoryFlowInteractionRepository;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.ApprovalWorkflowService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowCopyIdempotencyTest {
    @Test
    void sameKeyReplaysWithoutLookupFactOrMessageAndDifferentKeyHitsUniqueConflict()
            throws Exception {
        var fixture = fixture(Set.of(30L, 40L), false);
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 99, Set.of("flow.instance.copy"));

        var first = fixture.mutations.copy(
                session,
                pending.id(),
                new FlowRequests.Copy("30", "follow"),
                "copy-key"
        );
        var replay = fixture.mutations.copy(
                session,
                pending.id(),
                new FlowRequests.Copy("30", "follow"),
                "copy-key"
        );
        var changedBody = catchThrowableOfType(
                () -> fixture.mutations.copy(
                        session,
                        pending.id(),
                        new FlowRequests.Copy("30", "different"),
                        "copy-key"
                ),
                BusinessException.class
        );
        var duplicateRecipient = catchThrowableOfType(
                () -> FlowHttpErrors.execute(() -> fixture.mutations.copy(
                        session,
                        pending.id(),
                        new FlowRequests.Copy("30", "again"),
                        "copy-key-2"
                )),
                BusinessException.class
        );

        assertThat(replay).isEqualTo(first);
        assertThat(first.recipientId()).isEqualTo("30");
        assertThat(first.message()).isEqualTo("follow");
        assertThat(fixture.interactions.copies(pending.id(), 1, 20).total()).isEqualTo(1);
        assertThat(fixture.messages.commands).hasSize(1);
        var command = fixture.messages.commands.values().iterator().next();
        assertThat(command.templateCode()).isEqualTo("FLOW_INSTANCE_COPIED");
        assertThat(command.recipientMemberId()).isEqualTo(30);
        assertThat(command.target().type()).isEqualTo("FLOW_INSTANCE");
        assertThat(command.target().id()).isEqualTo(Long.toString(pending.id()));
        assertThat(command.body()).contains(pending.businessKey(), "follow");
        assertThat(fixture.members.calls).hasValue(2);
        assertThat(changedBody.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(duplicateRecipient.code()).isEqualTo("FLOW_COPY_ALREADY_EXISTS");
        assertThat(FlowInteractionMutationService.class.getMethod(
                        "copy",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Copy.class,
                        String.class
                ).getAnnotation(Transactional.class))
                .isNotNull();

        fixture.workflows.approve(pending.id(), 20, "done");
        var terminalCopy = fixture.mutations.copy(
                session,
                pending.id(),
                new FlowRequests.Copy("40", null),
                "terminal-copy"
        );
        assertThat(terminalCopy.recipientId()).isEqualTo("40");
        assertThat(fixture.interactions.copies(pending.id(), 1, 1).items())
                .extracting(FlowCopy::recipientId)
                .containsExactly(30L);
        assertThat(fixture.interactions.copies(pending.id(), 2, 1).items())
                .extracting(FlowCopy::recipientId)
                .containsExactly(40L);
    }

    @Test
    void scopedInstanceIsCheckedBeforeTargetParsingOrActiveMemberLookup() {
        var fixture = fixture(Set.of(30L), false);
        var session = new FlowSession(1, 2, 99, Set.of("flow.instance.copy"));

        var missing = catchThrowableOfType(
                () -> FlowHttpErrors.execute(() -> fixture.mutations.copy(
                        session,
                        999,
                        new FlowRequests.Copy("not-a-member", ""),
                        "missing-copy"
                )),
                BusinessException.class
        );

        assertThat(missing.code()).isEqualTo("FLOW_INSTANCE_NOT_FOUND");
        assertThat(fixture.members.calls).hasValue(0);
    }

    @Test
    void invalidSelfInactiveAndMessageRequestsDoNotPersistOrDispatch() {
        var fixture = fixture(Set.of(30L), false);
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 99, Set.of("flow.instance.copy"));

        assertCode(
                () -> fixture.mutations.copy(
                        session,
                        pending.id(),
                        new FlowRequests.Copy("99", ""),
                        "copy-self"
                ),
                "FLOW_COPY_TARGET_INVALID"
        );
        assertThat(fixture.members.calls).hasValue(0);
        assertCode(
                () -> fixture.mutations.copy(
                        session,
                        pending.id(),
                        new FlowRequests.Copy("31", ""),
                        "copy-inactive"
                ),
                "FLOW_COPY_TARGET_INVALID"
        );
        assertCode(
                () -> fixture.mutations.copy(
                        session,
                        pending.id(),
                        new FlowRequests.Copy("30", "😀".repeat(501)),
                        "copy-message"
                ),
                "FLOW_COPY_MESSAGE_INVALID"
        );

        assertThat(fixture.interactions.copies(pending.id(), 1, 20).total()).isZero();
        assertThat(fixture.messages.commands).isEmpty();
    }

    @Test
    void messageFailurePreventsIdempotencyCompletionInsideTransactionalMutation() {
        var fixture = fixture(Set.of(30L), true);
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 99, Set.of("flow.instance.copy"));

        assertThatThrownBy(() -> fixture.mutations.copy(
                session,
                pending.id(),
                new FlowRequests.Copy("30", "follow"),
                "copy-message-failure"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("message delivery failed");
        assertThat(fixture.idempotency.completions).hasValue(0);
    }

    private static void assertCode(
            Runnable action,
            String code
    ) {
        assertThatThrownBy(() -> FlowHttpErrors.execute(() -> {
            action.run();
            return null;
        })).isInstanceOfSatisfying(
                BusinessException.class,
                error -> assertThat(error.code()).isEqualTo(code)
        );
    }

    private static Fixture fixture(Set<Long> activeMemberIds, boolean failMessages) {
        var sequence = new AtomicLong(100);
        IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var clock = Clock.fixed(Instant.parse("2026-07-27T15:30:00Z"), ZoneOffset.UTC);
        var workflows = new ApprovalWorkflowService(
                new InMemoryApprovalRepository(),
                ids,
                clock
        );
        var interactions = new FlowInteractionService(
                workflows,
                new InMemoryFlowInteractionRepository(),
                ids,
                clock
        );
        var members = new RecordingActiveMembers(activeMemberIds);
        var messages = new MemoryMessages(failMessages);
        var idempotency = new MemoryIdempotency();
        var mutations = new FlowInteractionMutationService(
                (systemId, tenantId) -> interactions,
                idempotency,
                new ObjectMapper(),
                messages,
                members
        );
        return new Fixture(
                workflows,
                interactions,
                mutations,
                messages,
                members,
                idempotency
        );
    }

    private record Fixture(
            ApprovalWorkflowService workflows,
            FlowInteractionService interactions,
            FlowInteractionMutationService mutations,
            MemoryMessages messages,
            RecordingActiveMembers members,
            MemoryIdempotency idempotency
    ) {
        private com.unique.examine.flow.domain.ApprovalInstance start() {
            var definition = workflows.createDraft("Approval", 20);
            workflows.publish(definition.id());
            return workflows.startLatest(definition.id(), "expense-001", 10);
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

    private static final class MemoryMessages implements MemberMessageFacade {
        private final Map<Long, Command> commands = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        private final boolean fail;

        private MemoryMessages(boolean fail) {
            this.fail = fail;
        }

        @Override
        public long send(Command command) {
            if (fail) {
                throw new IllegalStateException("message delivery failed");
            }
            var id = sequence.incrementAndGet();
            commands.put(id, command);
            return id;
        }
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final Map<Key, IdempotencyRecord> records = new HashMap<>();
        private final Map<Long, Key> keysById = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong(1000);
        private final AtomicInteger completions = new AtomicInteger();

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
            completions.incrementAndGet();
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
