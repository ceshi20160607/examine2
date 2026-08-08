package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiConfirmation;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiRecordMutationPlanParser;
import com.unique.examine.ai.service.AiConfirmationService;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfirmationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void proposalNeverExecutesAndExactConfirmationReplayIsSingular() {
        var repository = repository();
        var clock = new MutableClock(NOW);
        var owner = new Owner(false);
        var service = service(repository, owner, clock);

        var proposed = service.propose(
                actor(7), repository.sessionValues.get(70L),
                repository.turnValues.get(71L), writePolicy(), plan());

        assertThat(proposed.state()).isEqualTo(AiConfirmation.State.PENDING);
        assertThat(owner.prepareCalls).isOne();
        assertThat(owner.executeCalls).isZero();
        assertThat(repository.confirmationAttempts).isEmpty();
        var persisted = repository.confirmationValues.toString()
                + repository.confirmationEvents;
        assertThat(persisted).doesNotContain("raw-command-secret");
        assertThat(proposed.sealedCommand().ciphertext()).isEqualTo("sealed-owner-command");

        var succeeded = service.confirm(
                actor(7), Long.toString(proposed.id()), 0, "confirm-1");
        var replayed = service.confirm(
                actor(7), Long.toString(proposed.id()), 0, "confirm-1");

        assertThat(succeeded.state()).as(succeeded.resultCode())
                .isEqualTo(AiConfirmation.State.SUCCEEDED);
        assertThat(replayed).isEqualTo(succeeded);
        assertThat(owner.executeCalls).isOne();
        assertThat(repository.confirmationAttempts).hasSize(1);
        assertThat(repository.confirmationEvents)
                .extracting(AiConfirmation.Event::eventType)
                .containsExactly("PROPOSED", "CONFIRMING", "SUCCEEDED");
        assertThatThrownBy(() -> service.confirm(
                actor(7), Long.toString(proposed.id()), 1, "confirm-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIRMATION_REPLAY_CONFLICT");
    }

    @Test
    void expiryRejectAndMemberBindingPreventOwnerExecution() {
        var repository = repository();
        var clock = new MutableClock(NOW);
        var owner = new Owner(false);
        var service = service(repository, owner, clock);
        var proposed = service.propose(
                actor(7), repository.sessionValues.get(70L),
                repository.turnValues.get(71L), writePolicy(), plan());

        assertThatThrownBy(() -> service.get(
                actor(8), Long.toString(proposed.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_NOT_FOUND");
        clock.advance(Duration.ofSeconds(61));
        assertThat(service.get(actor(7), Long.toString(proposed.id())).state())
                .isEqualTo(AiConfirmation.State.EXPIRED);
        assertThat(owner.executeCalls).isZero();

        var freshRepository = repository();
        var fresh = service(freshRepository, owner, new MutableClock(NOW));
        var second = fresh.propose(
                actor(7), freshRepository.sessionValues.get(70L),
                freshRepository.turnValues.get(71L), writePolicy(), plan());
        var rejected = fresh.reject(actor(7), Long.toString(second.id()), 0);
        assertThat(rejected.state()).isEqualTo(AiConfirmation.State.REJECTED);
        assertThatThrownBy(() -> fresh.confirm(
                actor(7), Long.toString(second.id()), 1, "after-reject"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIRMATION_STATE_CONFLICT");
        assertThat(owner.executeCalls).isZero();
    }

    @Test
    void currentPolicyIsRecheckedAndOwnerFailureIsAuditedWithoutSuccess() {
        var repository = repository();
        var owner = new Owner(false);
        var service = service(repository, owner, new MutableClock(NOW));
        var proposed = service.propose(
                actor(7), repository.sessionValues.get(70L),
                repository.turnValues.get(71L), writePolicy(), plan());

        var readOnly = readOnlyPolicy();
        repository.versions.put(readOnly.id(), readOnly);
        repository.drafts.put("1:2", readOnlyDraft(readOnly.id()));
        assertThatThrownBy(() -> service.confirm(
                actor(7), Long.toString(proposed.id()), 0, "policy-changed"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_POLICY_CHANGED");
        assertThat(owner.executeCalls).isZero();

        var failingRepository = repository();
        var failingOwner = new Owner(true);
        var failing = service(
                failingRepository, failingOwner, new MutableClock(NOW));
        var failureProposal = failing.propose(
                actor(7), failingRepository.sessionValues.get(70L),
                failingRepository.turnValues.get(71L), writePolicy(), plan());
        var failed = failing.confirm(
                actor(7), Long.toString(failureProposal.id()), 0, "owner-fails");
        var replay = failing.confirm(
                actor(7), Long.toString(failureProposal.id()), 0, "owner-fails");
        assertThat(failed.state()).isEqualTo(AiConfirmation.State.FAILED);
        assertThat(failed.result()).isNull();
        assertThat(failed.resultCode()).isEqualTo("OWNER_DENIED");
        assertThat(replay).isEqualTo(failed);
        assertThat(failingOwner.executeCalls).isOne();
    }

    private static AiConfirmationService service(
            MemoryAiRepository repository, Owner owner, Clock clock) {
        return new AiConfirmationService(
                repository, owner, new SequenceIdService(1_000),
                clock, new ObjectMapper().findAndRegisterModules());
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        var policy = writePolicy();
        repository.versions.put(policy.id(), policy);
        repository.drafts.put("1:2", writeDraft(policy.id()));
        repository.insertSession(new AiConversation.Session(
                70, 1, 2, 7, policy.id(), 80, 3, "gpt-write", "v2", 41,
                AiConversation.SessionStatus.ACTIVE, "[title:redacted]", NOW, NOW));
        repository.insertTurn(new AiConversation.Turn(
                71, 1, 2, 70, policy.id(), 80, 3, 41,
                AiConversation.TurnStatus.RUNNING, "[request:redacted]",
                "a".repeat(64), null, null, null, 0,
                "AI_TURN_RUNNING", false, 0, "request-1", "trace-1",
                NOW, null));
        return repository;
    }

    private static AiRecordMutationPlanParser.Plan plan() {
        return new AiRecordMutationPlanParser.Plan(
                AiRecordMutationFacade.Operation.RECORD_CREATE, "orders",
                null, null, "Order A", List.of("status"),
                List.of(new AiRecordMutationPlanParser.FieldConfidence(
                        "status", 0.99)), List.of(), true,
                "{\"schemaVersionId\":\"501\",\"recordId\":null,"
                        + "\"expectedVersion\":null,\"title\":\"raw-command-secret\","
                        + "\"values\":{\"status\":\"OPEN\"},"
                        + "\"relations\":[],\"subtables\":[]}",
                "c".repeat(64));
    }

    private static AiPolicy.Version writePolicy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "RECORD_CREATE", "RECORD_UPDATE"),
                Map.of("orders", Set.of("status")), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v2", "b".repeat(64), NOW, 7);
    }

    private static AiPolicy.Version readOnlyPolicy() {
        return new AiPolicy.Version(
                92, 1, 2, 90, 2, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")), Set.of("RECORD_QUERY"),
                Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v2", "d".repeat(64), NOW, 7);
    }

    private static AiPolicy.Draft writeDraft(long activeVersion) {
        return new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "RECORD_CREATE", "RECORD_UPDATE"),
                Map.of("orders", Set.of("status")), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v2", "e".repeat(64),
                activeVersion, NOW, 7);
    }

    private static AiPolicy.Draft readOnlyDraft(long activeVersion) {
        return new AiPolicy.Draft(
                90, 1, 2, 2, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY"), Map.of(), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v2", "f".repeat(64),
                activeVersion, NOW, 7);
    }

    private static AiActor actor(long memberId) {
        return new AiActor(5,
                1, 2, memberId, Set.of(
                "system.runtime.access", "ai.agent.use", "module.orders.view",
                "module.orders.create", "module.orders.update"),
                41, "request-1", "trace-1");
    }

    private static final class Owner implements AiRecordMutationFacade {
        private final boolean fail;
        private int prepareCalls;
        private int executeCalls;

        private Owner(boolean fail) {
            this.fail = fail;
        }

        @Override
        public PreparedMutation prepare(PrepareRequest request) {
            prepareCalls++;
            return new PreparedMutation(
                    new MutationPreview(
                            request.operation(), request.moduleCode(), "501",
                            null, null, null, "Order A",
                            List.of(new FieldChange(
                                    "status", "Status", "SELECT",
                                    null, "OPEN", false))),
                    new SealedCommand(
                            "sealed-owner-command", "key-v1", "9".repeat(64)));
        }

        @Override
        public RecordView execute(ExecuteRequest request) {
            executeCalls++;
            if (fail) throw new BusinessException(
                    "OWNER_DENIED", "Owner denied mutation",
                    org.springframework.http.HttpStatus.CONFLICT);
            return new RecordView(
                    "5010", "ORD-5010", 0, "ACTIVE", "Order A", "501",
                    List.of(new DisplayValue(
                            "status", "Status", "SELECT", "OPEN", false)));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
