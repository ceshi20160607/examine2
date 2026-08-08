package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiFillProposal;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.plan.AiFillResultParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.core.ai.AiFieldFillFacade;
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

class AiFillFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void exactProposalAndConfirmReplaysNeverDuplicateProviderOrOwnerWrites() {
        var repository = repository();
        var owner = new Owner();
        var provider = new Provider("""
                {"value":"AI candidate","confidence":0.95,"clarification":null}
                """);
        var facade = facade(repository, owner, provider, new MutableClock(NOW));

        var proposal = facade.propose(
                actor(), "orders", "501", "ai_summary", 4, "propose-1");
        var proposalReplay = facade.propose(
                actor(), "orders", "501", "ai_summary", 4, "propose-1");

        assertThat(proposal.state()).isEqualTo("PENDING");
        assertThat(proposalReplay).isEqualTo(proposal);
        assertThat(provider.calls).isOne();
        assertThat(owner.snapshotCalls).isOne();
        assertThat(owner.prepareCalls).isOne();
        assertThat(owner.executeCalls).isZero();
        assertThat(proposal.sources().getFirst().displayValue())
                .doesNotContain("hidden-source-raw");
        assertThat(repository.fillProposals.toString())
                .doesNotContain("hidden-source-raw")
                .contains("afterDisplayValue=AI candidate");

        var succeeded = facade.confirm(
                actor(), "orders", "501", "ai_summary", proposal.id(),
                0, "confirm-1");
        var confirmReplay = facade.confirm(
                actor(), "orders", "501", "ai_summary", proposal.id(),
                0, "confirm-1");

        assertThat(succeeded.state()).isEqualTo("SUCCEEDED");
        assertThat(confirmReplay).isEqualTo(succeeded);
        assertThat(owner.executeCalls).isOne();
        assertThat(repository.fillEvents)
                .extracting(AiFillProposal.Event::eventType)
                .containsExactly("PROPOSED", "CONFIRMING", "SUCCEEDED");
        assertThatThrownBy(() -> facade.confirm(
                actor(), "orders", "501", "ai_summary", proposal.id(),
                1, "confirm-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_FILL_REPLAY_CONFLICT");
    }

    @Test
    void lowConfidenceCreatesNonExecutableClarificationWithoutPrepare() {
        var repository = repository();
        var owner = new Owner();
        var clock = new MutableClock(NOW);
        var facade = facade(repository, owner, new Provider("""
                {"value":"uncertain","confidence":0.40,"clarification":null}
                """), clock);

        var proposal = facade.propose(
                actor(), "orders", "501", "ai_summary", 4, "low-1");

        assertThat(proposal.state()).isEqualTo("CLARIFICATION_REQUIRED");
        assertThat(proposal.confidence()).isEqualTo(0.40);
        assertThat(owner.prepareCalls).isZero();
        assertThat(owner.executeCalls).isZero();
        assertThatThrownBy(() -> facade.confirm(
                actor(), "orders", "501", "ai_summary", proposal.id(),
                0, "low-confirm"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_FILL_STATE_CONFLICT");
        clock.advance(Duration.ofSeconds(61));
        assertThat(facade.proposal(
                actor(), "orders", "501", "ai_summary", proposal.id()).state())
                .isEqualTo("EXPIRED");
        assertThat(owner.prepareCalls).isZero();
        assertThat(owner.executeCalls).isZero();
    }

    @Test
    void rejectExpiryActorAndCurrentPolicyChecksPreventMaterialization() {
        var repository = repository();
        var owner = new Owner();
        var clock = new MutableClock(NOW);
        var facade = facade(repository, owner, new Provider(valid()), clock);
        var proposal = facade.propose(
                actor(), "orders", "501", "ai_summary", 4, "reject-proposal");

        assertThatThrownBy(() -> facade.proposal(
                actor(8), "orders", "501", "ai_summary", proposal.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_NOT_FOUND");
        var rejected = facade.reject(actor(), "orders", "501", "ai_summary",
                proposal.id(), 0, "reject-1");
        var rejectReplay = facade.reject(actor(), "orders", "501", "ai_summary",
                proposal.id(), 0, "reject-1");
        assertThat(rejected.state()).isEqualTo("REJECTED");
        assertThat(rejectReplay).isEqualTo(rejected);
        assertThat(owner.rejectCalls).isOne();
        assertThat(owner.executeCalls).isZero();

        var expiringRepository = repository();
        var expiring = facade(
                expiringRepository, owner, new Provider(valid()), clock);
        var expiringProposal = expiring.propose(
                actor(), "orders", "501", "ai_summary", 4, "expires");
        clock.advance(Duration.ofSeconds(61));
        assertThat(expiring.proposal(actor(), "orders", "501", "ai_summary",
                expiringProposal.id()).state()).isEqualTo("EXPIRED");
        assertThat(owner.executeCalls).isZero();

        var policyRepository = repository();
        var denied = facade(policyRepository, owner, new Provider(valid()),
                new MutableClock(NOW));
        var deniedProposal = denied.propose(
                actor(), "orders", "501", "ai_summary", 4, "policy");
        policyRepository.versions.put(92L, readOnlyPolicy());
        policyRepository.drafts.put("1:2", readOnlyDraft());
        assertThatThrownBy(() -> denied.confirm(
                actor(), "orders", "501", "ai_summary", deniedProposal.id(),
                0, "policy-confirm"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_POLICY_CHANGED");
        assertThat(owner.executeCalls).isZero();
    }

    private static AiFillFacade facade(
            MemoryAiRepository repository, Owner owner,
            Provider provider, Clock clock) {
        return new AiFillFacade(
                repository, owner, provider, new AiFillResultParser(),
                new SequenceIdService(1_000), clock, new ObjectMapper());
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-fill", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7));
        repository.versions.put(91L, fillPolicy());
        repository.drafts.put("1:2", fillDraft());
        return repository;
    }

    private static AiPolicy.Version fillPolicy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-fill", Set.of("orders"),
                Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "AI_FILL"), Map.of(),
                Map.of("orders", Set.of("ai_summary")), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v3", "b".repeat(64), NOW, 7);
    }

    private static AiPolicy.Draft fillDraft() {
        return new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "AI_FILL"), Map.of(),
                Map.of("orders", Set.of("ai_summary")), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v3", "a".repeat(64),
                91L, NOW, 7);
    }

    private static AiPolicy.Version readOnlyPolicy() {
        return new AiPolicy.Version(
                92, 1, 2, 90, 2, 80, 3, "gpt-fill", Set.of("orders"),
                Map.of("orders", Set.of("status")), Set.of("RECORD_QUERY"),
                Map.of(), Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED, 60,
                true, AiPolicy.RedactionMode.STRICT, "v3", "c".repeat(64), NOW, 7);
    }

    private static AiPolicy.Draft readOnlyDraft() {
        return new AiPolicy.Draft(
                90, 1, 2, 2, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY"), Map.of(), Map.of(), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v3", "d".repeat(64),
                92L, NOW, 7);
    }

    private static AiActor actor() { return actor(7); }

    private static AiActor actor(long memberId) {
        return new AiActor(5,
                1, 2, memberId, Set.of(
                "system.runtime.access", "ai.agent.use", "module.orders.view",
                "module.orders.update"), 41, "request-1", "trace-1");
    }

    private static String valid() {
        return "{\"value\":\"AI candidate\",\"confidence\":0.95,"
                + "\"clarification\":null}";
    }

    private static final class Provider implements AiProviderClient {
        private final String response;
        private int calls;

        private Provider(String response) { this.response = response; }

        @Override
        public Completion complete(AiProvider provider, Request request) {
            calls++;
            return new Completion(response, 12, 5, 7, AiSupport.sha256(response));
        }
    }

    private static final class Owner implements AiFieldFillFacade {
        private int snapshotCalls;
        private int prepareCalls;
        private int executeCalls;
        private int rejectCalls;

        @Override
        public SourceSnapshot sourceSnapshot(SourceRequest request) {
            snapshotCalls++;
            return new SourceSnapshot(
                    request.moduleCode(), request.recordId(), 4, "301",
                    new FieldContract(
                            "701", request.fieldCode(), "AI summary",
                            ResultSchema.STRING, List.of("702"),
                            "Summarize the source", "SYSTEM_DEFAULT", 0.80,
                            OverwriteMode.CONFIRM),
                    "e".repeat(64), List.of(new SourceValue(
                    "702", "description", "Description", "TEXT",
                    "hidden-source-raw")), null);
        }

        @Override
        public PreparedFill prepare(PrepareRequest request) {
            prepareCalls++;
            return new PreparedFill(new FillPreview(
                    request.moduleCode(), request.recordId(),
                    request.expectedRecordVersion(), request.expectedSchemaVersionId(),
                    "701", request.fieldCode(), ResultSchema.STRING,
                    request.expectedSourceVersionHash(), null,
                    "AI candidate", 0.95, false),
                    new SealedCommand("sealed-fill", "key-v1", "f".repeat(64)));
        }

        @Override
        public FillReadback execute(ExecuteRequest request) {
            executeCalls++;
            return new FillReadback(
                    "9001", request.moduleCode(), request.recordId(), 5,
                    "301", "701", request.fieldCode(), ResultSchema.STRING,
                    "AI candidate", 0.95, 1, "CREATED",
                    new Provenance("80", 3, "gpt-fill", "v3", "91"));
        }

        @Override
        public RejectionReadback reject(RejectRequest request) {
            rejectCalls++;
            return new RejectionReadback(
                    "9002", request.proposalId(), request.recordId(),
                    request.fieldCode(), "REJECTED");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        private void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
