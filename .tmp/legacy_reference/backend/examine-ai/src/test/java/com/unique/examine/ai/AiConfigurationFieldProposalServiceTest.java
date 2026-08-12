package com.unique.examine.ai;

import com.unique.examine.ai.domain.AiConfigurationFieldProposal;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiConfigurationFieldPlanParser;
import com.unique.examine.ai.service.AiConfigurationFieldProposalService;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationFieldProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void prepareNeverWritesAndExactReplayReturnsOneRedactedReadback() {
        var repository = repository();
        var owner = new Owner(null);
        var service = service(repository, owner, new MutableClock(NOW));

        var prepared = service.prepare(actor(5, 7), repository.turnValues.get(71L),
                policy(), actionablePlan());
        repository.insertConfigurationFieldProposal(
                prepared.storedProposal(), prepared.event());

        assertThat(prepared.liveProposal().state())
                .isEqualTo(AiConfigurationFieldProposal.State.PENDING);
        assertThat(prepared.liveProposal().preview().fieldCode())
                .isEqualTo("tax_amount");
        assertThat(owner.prepareCalls).isOne();
        assertThat(owner.executeCalls).isZero();
        assertThat(repository.configurationFieldProposals.values().toString())
                .doesNotContain("tax_amount")
                .doesNotContain("Tax amount")
                .doesNotContain("raw-command-secret")
                .contains(AiConfigurationFieldProposal.REDACTED_FIELD_CODE);

        var succeeded = service.confirm(actor(5, 7), "70",
                Long.toString(prepared.liveProposal().id()), 0, "confirm-1");
        var replayed = service.confirm(actor(5, 7), "70",
                Long.toString(prepared.liveProposal().id()), 0, "confirm-1");

        assertThat(succeeded.state())
                .isEqualTo(AiConfigurationFieldProposal.State.SUCCEEDED);
        assertThat(replayed).isEqualTo(succeeded);
        assertThat(succeeded.result().fieldCode())
                .isEqualTo(AiConfigurationFieldProposal.REDACTED_FIELD_CODE);
        assertThat(succeeded.result().draftStatus()).isEqualTo("DRAFT");
        assertThat(owner.executeCalls).isOne();
        assertThat(repository.configurationFieldAttempts).hasSize(1);
        assertThat(repository.configurationFieldEvents)
                .extracting(AiConfigurationFieldProposal.Event::eventType)
                .containsExactly("PROPOSED", "CONFIRMING", "SUCCEEDED");
        assertThat(owner.lastExecute.accountId()).isEqualTo(5);
        assertThat(owner.lastExecute.memberId()).isEqualTo(7);
        assertThat(owner.lastExecute.configRootId()).isEqualTo("301");
        assertThat(owner.lastExecute.expectedDraftRevision()).isEqualTo(8);

        assertThatThrownBy(() -> service.confirm(actor(5, 7), "70",
                Long.toString(prepared.liveProposal().id()), 1, "confirm-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_FIELD_REPLAY_CONFLICT");
    }

    @Test
    void staleOwnerFailureIsTerminalAndReplayDoesNotExecuteAgain() {
        var repository = repository();
        var owner = new Owner("MODULE_CONFIG_STALE");
        var service = service(repository, owner, new MutableClock(NOW));
        var prepared = service.prepare(actor(5, 7), repository.turnValues.get(71L),
                policy(), actionablePlan());
        repository.insertConfigurationFieldProposal(
                prepared.storedProposal(), prepared.event());

        var failed = service.confirm(actor(5, 7), "70",
                Long.toString(prepared.liveProposal().id()), 0, "stale-1");
        var replay = service.confirm(actor(5, 7), "70",
                Long.toString(prepared.liveProposal().id()), 0, "stale-1");

        assertThat(failed.state()).isEqualTo(
                AiConfigurationFieldProposal.State.FAILED);
        assertThat(failed.resultCode()).isEqualTo("MODULE_CONFIG_STALE");
        assertThat(failed.result()).isNull();
        assertThat(replay).isEqualTo(failed);
        assertThat(owner.executeCalls).isOne();
    }

    @Test
    void integerOwnerCanonicalPrecisionAndScaleAreAccepted() {
        var repository = repository();
        var owner = new Owner(null);
        var service = service(repository, owner, new MutableClock(NOW));
        var integer = new AiConfigurationFieldPlanParser.Plan(
                "orders", "quantity", "Quantity",
                AiConfigurationFieldPlanParser.FieldType.INTEGER, false,
                new AiConfigurationFieldPlanParser.Settings(
                        null, null, null, "0", "100"),
                0.98, null, "integer-command", "8".repeat(64));

        var prepared = service.prepare(actor(5, 7), repository.turnValues.get(71L),
                policy(), integer);

        assertThat(prepared.liveProposal().preview().settings().precision())
                .isEqualTo(38);
        assertThat(prepared.liveProposal().preview().settings().scale()).isZero();
        assertThat(owner.lastPrepare.field().settings().precision()).isEqualTo(38);
        assertThat(owner.lastPrepare.field().settings().scale()).isZero();
    }

    @Test
    void textOwnerSettingsKeepNullableNumericMetadata() {
        var repository = repository();
        var owner = new Owner(null);
        var service = service(repository, owner, new MutableClock(NOW));
        var text = new AiConfigurationFieldPlanParser.Plan(
                "orders", "risk_note", "Risk note",
                AiConfigurationFieldPlanParser.FieldType.TEXT, false,
                new AiConfigurationFieldPlanParser.Settings(
                        80, null, null, null, null),
                0.97, null, "text-command", "7".repeat(64));

        var prepared = service.prepare(actor(5, 7), repository.turnValues.get(71L),
                policy(), text);

        assertThat(prepared.liveProposal().preview().settings().maxLength())
                .isEqualTo(80);
        assertThat(prepared.liveProposal().preview().settings().precision()).isNull();
        assertThat(prepared.liveProposal().preview().settings().scale()).isNull();
        assertThat(owner.lastPrepare.field().settings().precision()).isNull();
        assertThat(owner.lastPrepare.field().settings().scale()).isNull();
    }

    @Test
    void clarificationRejectExpiryIdentityAndPolicyGatesNeverExecuteOwner() {
        var clarificationRepo = repository();
        var owner = new Owner(null);
        var clock = new MutableClock(NOW);
        var service = service(clarificationRepo, owner, clock);
        var clarification = service.prepare(actor(5, 7),
                clarificationRepo.turnValues.get(71L), policy(), clarificationPlan());
        clarificationRepo.insertConfigurationFieldProposal(
                clarification.storedProposal(), clarification.event());
        assertThat(clarification.liveProposal().clarification())
                .isEqualTo("Which field name should be used?");
        assertThat(clarification.storedProposal().clarification())
                .isEqualTo(AiConfigurationFieldProposal.REDACTED_CLARIFICATION);
        assertThat(owner.prepareCalls).isZero();

        var rejectRepo = repository();
        var rejectService = service(rejectRepo, owner, new MutableClock(NOW));
        var pending = rejectService.prepare(actor(5, 7),
                rejectRepo.turnValues.get(71L), policy(), actionablePlan());
        rejectRepo.insertConfigurationFieldProposal(
                pending.storedProposal(), pending.event());
        assertThat(rejectService.reject(actor(5, 7), "70",
                Long.toString(pending.liveProposal().id()), 0).state())
                .isEqualTo(AiConfigurationFieldProposal.State.REJECTED);

        assertThatThrownBy(() -> rejectService.get(actor(6, 7), "70",
                Long.toString(pending.liveProposal().id())))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_NOT_FOUND");

        var expireRepo = repository();
        var expireClock = new MutableClock(NOW);
        var expireService = service(expireRepo, owner, expireClock);
        var expiring = expireService.prepare(actor(5, 7),
                expireRepo.turnValues.get(71L), policy(), actionablePlan());
        expireRepo.insertConfigurationFieldProposal(
                expiring.storedProposal(), expiring.event());
        expireClock.advance(Duration.ofSeconds(61));
        assertThat(expireService.get(actor(5, 7), "70",
                Long.toString(expiring.liveProposal().id())).state())
                .isEqualTo(AiConfigurationFieldProposal.State.EXPIRED);

        var changedRepo = repository();
        var changedService = service(changedRepo, owner, new MutableClock(NOW));
        var changed = changedService.prepare(actor(5, 7),
                changedRepo.turnValues.get(71L), policy(), actionablePlan());
        changedRepo.insertConfigurationFieldProposal(
                changed.storedProposal(), changed.event());
        var replacement = readOnlyPolicy();
        changedRepo.versions.put(replacement.id(), replacement);
        changedRepo.drafts.put("1:2", draft(replacement.id(),
                Set.of("RECORD_QUERY"), 2));
        assertThatThrownBy(() -> changedService.confirm(actor(5, 7), "70",
                Long.toString(changed.liveProposal().id()), 0, "changed-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_CONFIG_FIELD_POLICY_CHANGED");
    }

    private static AiConfigurationFieldProposalService service(
            MemoryAiRepository repository, Owner owner, Clock clock) {
        return new AiConfigurationFieldProposalService(
                repository, owner, new SequenceIdService(1_000), clock);
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        var policy = policy();
        repository.versions.put(policy.id(), policy);
        repository.drafts.put("1:2", draft(policy.id(),
                Set.of("RECORD_QUERY", "CONFIG_FIELD_DRAFT"), 1));
        repository.insertSession(new AiConversation.Session(
                70, 1, 2, 7, policy.id(), 80, 3, "gpt-write", "v2", 41,
                AiConversation.SessionStatus.ACTIVE, "[title:redacted]", NOW, NOW));
        repository.insertTurn(new AiConversation.Turn(
                71, 1, 2, 70, policy.id(), 80, 3, 41,
                AiConversation.TurnStatus.RUNNING, "[request:redacted]",
                "a".repeat(64), null, null, null, 0,
                "AI_TURN_RUNNING", false, 0, "request-1", "trace-1", NOW, null));
        return repository;
    }

    private static AiConfigurationFieldPlanParser.Plan actionablePlan() {
        return new AiConfigurationFieldPlanParser.Plan(
                "orders", "tax_amount", "Tax amount",
                AiConfigurationFieldPlanParser.FieldType.DECIMAL, true,
                new AiConfigurationFieldPlanParser.Settings(
                        null, 18, 2, "0", "999999.99"),
                0.97, null, "raw-command-secret", "c".repeat(64));
    }

    private static AiConfigurationFieldPlanParser.Plan clarificationPlan() {
        return new AiConfigurationFieldPlanParser.Plan(
                null, null, null, null, null, null, 0.4,
                "Which field name should be used?", null, "d".repeat(64));
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "CONFIG_FIELD_DRAFT"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v2", "b".repeat(64), NOW, 7);
    }

    private static AiPolicy.Version readOnlyPolicy() {
        return new AiPolicy.Version(
                92, 1, 2, 90, 2, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")), Set.of("RECORD_QUERY"),
                10, true, AiPolicy.RedactionMode.STRICT, "v2",
                "e".repeat(64), NOW, 7);
    }

    private static AiPolicy.Draft draft(
            long activeVersion, Set<String> operations, long revision) {
        return new AiPolicy.Draft(
                90, 1, 2, revision, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                operations, Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED,
                60, true, AiPolicy.RedactionMode.STRICT, "v2",
                "f".repeat(64), activeVersion, NOW, 7);
    }

    private static AiActor actor(long accountId, long memberId) {
        return new AiActor(accountId, 1, 2, memberId,
                Set.of("system.runtime.access", "ai.agent.use",
                        "module.config.manage"), 41, "request-1", "trace-1");
    }

    private static final class Owner implements AiConfigurationFieldFacade {
        private final String failureCode;
        private int prepareCalls;
        private int executeCalls;
        private PrepareRequest lastPrepare;
        private ExecuteRequest lastExecute;

        private Owner(String failureCode) {
            this.failureCode = failureCode;
        }

        @Override
        public PreparedField prepare(PrepareRequest request) {
            prepareCalls++;
            lastPrepare = request;
            return new PreparedField(new FieldPreview(
                    "301", "401", request.moduleCode(), 8, 9, request.field()),
                    NOW.plusSeconds(60), new SealedCommand(
                    "sealed_owner_command", "key-v1", "9".repeat(64)));
        }

        @Override
        public FieldReadback execute(ExecuteRequest request) {
            executeCalls++;
            lastExecute = request;
            if (failureCode != null) throw new BusinessException(
                    failureCode, "Owner rejected configuration draft",
                    org.springframework.http.HttpStatus.CONFLICT);
            return new FieldReadback("301", "401", "orders", 9,
                    new FieldView("501", "tax_amount", "Tax amount",
                            FieldType.DECIMAL, true, new ScalarSettings(
                            null, null, null, null, "0", "999999.99",
                            18, 2, null, null), 20, 0));
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
