package com.unique.examine.flow.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowHttpErrors;
import com.unique.examine.flow.api.FlowRequests;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.interaction.memory.InMemoryFlowInteractionRepository;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.ApprovalWorkflowService;
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
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FlowInteractionIdempotencyTest {
    @Test
    void urgeReplayDoesNotDuplicateFactOrMessageAndDifferentBodyConflicts() throws Exception {
        var fixture = fixture();
        var pending = fixture.start();
        var session = new FlowSession(1, 2, 10, Set.of("flow.instance.urge"));

        var first = fixture.mutations.urge(
                session, pending.id(), new FlowRequests.Urge("review"), "urge-key");
        var replay = fixture.mutations.urge(
                session, pending.id(), new FlowRequests.Urge("review"), "urge-key");
        var conflict = catchThrowableOfType(
                () -> fixture.mutations.urge(
                        session,
                        pending.id(),
                        new FlowRequests.Urge("different"),
                        "urge-key"),
                BusinessException.class);

        assertThat(replay).isEqualTo(first);
        assertThat(fixture.interactions.urges(pending.id(), 1, 20).total()).isEqualTo(1);
        assertThat(fixture.messages.commands).hasSize(1);
        var command = fixture.messages.commands.values().iterator().next();
        assertThat(command.templateCode()).isEqualTo("FLOW_INSTANCE_URGED");
        assertThat(command.title()).isEqualTo("流程催办");
        assertThat(command.body()).contains(pending.businessKey(), "review");
        assertThat(command.recipientMemberId()).isEqualTo(20);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(FlowInteractionMutationService.class.getMethod(
                        "urge",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Urge.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();

        fixture.workflows.approve(pending.id(), 20, "done");
        var terminal = catchThrowableOfType(
                () -> FlowHttpErrors.execute(() -> fixture.mutations.urge(
                        session,
                        pending.id(),
                        new FlowRequests.Urge("late"),
                        "urge-new-key")),
                BusinessException.class);
        assertThat(terminal.code()).isEqualTo("FLOW_INSTANCE_STATE_INVALID");
    }

    @Test
    void commentReplayWorksOnTerminalInstanceWithoutDuplicate() throws Exception {
        var fixture = fixture();
        var pending = fixture.start();
        fixture.workflows.approve(pending.id(), 20, "done");
        var session = new FlowSession(1, 2, 99, Set.of("flow.instance.comment"));

        var first = fixture.mutations.comment(
                session, pending.id(), new FlowRequests.Comment("checked"), "comment-key");
        var replay = fixture.mutations.comment(
                session, pending.id(), new FlowRequests.Comment("checked"), "comment-key");
        var conflict = catchThrowableOfType(
                () -> fixture.mutations.comment(
                        session,
                        pending.id(),
                        new FlowRequests.Comment("different"),
                        "comment-key"),
                BusinessException.class);

        assertThat(replay).isEqualTo(first);
        assertThat(fixture.interactions.comments(pending.id(), 1, 20).total()).isEqualTo(1);
        assertThat(fixture.messages.commands).isEmpty();
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(FlowInteractionMutationService.class.getMethod(
                        "comment",
                        FlowSession.class,
                        long.class,
                        FlowRequests.Comment.class,
                        String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    private static Fixture fixture() {
        var sequence = new AtomicLong(100);
        IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        var clock = Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);
        var workflows = new ApprovalWorkflowService(new InMemoryApprovalRepository(), ids, clock);
        var interactions = new FlowInteractionService(
                workflows,
                new InMemoryFlowInteractionRepository(),
                ids,
                clock
        );
        FlowInteractionServiceFactory services = (systemId, tenantId) -> interactions;
        var messages = new MemoryMessages();
        var mutations = new FlowInteractionMutationService(
                services,
                new MemoryIdempotency(),
                new ObjectMapper(),
                messages,
                (systemId, tenantId, memberId) -> Optional.of(
                        new com.unique.examine.core.api.RuntimeActiveMemberFacade.ActiveMember(
                                memberId,
                                null
                        )
                )
        );
        return new Fixture(workflows, interactions, mutations, messages);
    }

    private record Fixture(
            ApprovalWorkflowService workflows,
            FlowInteractionService interactions,
            FlowInteractionMutationService mutations,
            MemoryMessages messages
    ) {
        private ApprovalInstance start() {
            var definition = workflows.createDraft("Approval", 20);
            workflows.publish(definition.id());
            return workflows.startLatest(definition.id(), "expense-001", 10);
        }
    }

    private static final class MemoryMessages implements MemberMessageFacade {
        private final Map<Long, Command> commands = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public long send(Command command) {
            var id = sequence.incrementAndGet();
            commands.put(id, command);
            return id;
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
