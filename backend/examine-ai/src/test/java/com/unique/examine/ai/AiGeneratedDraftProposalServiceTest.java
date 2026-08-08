package com.unique.examine.ai;

import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiGeneratedDraftProposal;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.plan.AiGeneratedDraftPlanParser;
import com.unique.examine.ai.service.AiGeneratedDraftProposalService;
import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiGeneratedDraftProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void flowPrepareIsZeroWriteAndConfirmIsSingularReplaySafeAndRedacted() {
        var repository = repository();
        var flow = new FlowOwner();
        var module = new ModuleOwner();
        var service = service(repository, flow, module, fixed());
        var prepared = service.prepare(
                actor(), repository.turnValues.get(71L), policy(), flowPlan());
        repository.insertGeneratedDraftProposal(
                prepared.storedProposal(), prepared.event());

        assertThat(prepared.liveProposal().preview().flowDefinition().name())
                .isEqualTo("Private expense approval");
        assertThat(flow.prepareCalls).isOne();
        assertThat(flow.executeCalls).isZero();
        assertThat(repository.generatedDraftProposals.values() + " "
                + repository.generatedDraftEvents)
                .doesNotContain("Private expense approval")
                .contains(AiGeneratedDraftProposal.REDACTED_NAME);

        var succeeded = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "generated-flow-1");
        var replay = service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 0, "generated-flow-1");

        assertThat(succeeded.state()).isEqualTo(
                AiGeneratedDraftProposal.State.SUCCEEDED);
        assertThat(succeeded.result().flowDefinition().definitionId())
                .isEqualTo("601");
        assertThat(succeeded.result().flowDefinition().name())
                .isEqualTo("Private expense approval");
        assertThat(replay.result().flowDefinition().name())
                .isEqualTo(AiGeneratedDraftProposal.REDACTED_NAME);
        assertThat(flow.executeCalls).isOne();
        assertThat(repository.generatedDraftProposals.values() + " "
                + repository.generatedDraftAttempts.values() + " "
                + repository.generatedDraftEvents)
                .doesNotContain("Private expense approval");
        assertThat(repository.generatedDraftEvents)
                .extracting(AiGeneratedDraftProposal.Event::eventType)
                .containsExactly("PROPOSED", "CONFIRMING", "SUCCEEDED");
        assertThatThrownBy(() -> service.confirm(actor(), "70",
                Long.toString(prepared.liveProposal().id()), 1, "generated-flow-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_GENERATED_DRAFT_REPLAY_CONFLICT");
    }

    @Test
    void reportAndPrintUseModuleOwnerAndPersistNoProtectedNarrative() {
        var repository = repository();
        var flow = new FlowOwner();
        var module = new ModuleOwner();
        var service = service(repository, flow, module, fixed());

        var report = service.prepare(
                actor(), repository.turnValues.get(71L), policy(), reportPlan());
        repository.insertGeneratedDraftProposal(report.storedProposal(), report.event());
        var reportResult = service.confirm(actor(), "70",
                Long.toString(report.liveProposal().id()), 0, "generated-report-1");
        assertThat(reportResult.result().report().reportId()).isEqualTo("701");
        assertThat(reportResult.result().report().published()).isFalse();

        repository.insertTurn(turn(72));
        var print = service.prepare(
                actor(), repository.turnValues.get(72L), policy(), printPlan());
        repository.insertGeneratedDraftProposal(print.storedProposal(), print.event());
        var printResult = service.confirm(actor(), "70",
                Long.toString(print.liveProposal().id()), 0, "generated-print-1");
        assertThat(printResult.result().printTemplate().templateId()).isEqualTo("801");
        assertThat(printResult.result().printTemplate().status()).isEqualTo("DISABLED");
        assertThat(printResult.result().printTemplate().published()).isFalse();

        assertThat(module.prepareCalls).isEqualTo(2);
        assertThat(module.executeCalls).isEqualTo(2);
        var persisted = repository.generatedDraftProposals.values() + " "
                + repository.generatedDraftAttempts.values() + " "
                + repository.generatedDraftEvents;
        assertThat(persisted)
                .doesNotContain("Private report name")
                .doesNotContain("Private report description")
                .doesNotContain("Private print name")
                .doesNotContain("Private print title")
                .doesNotContain("Private print footer");
    }

    @Test
    void staleAndLivePermissionDenialAreStableTerminalReplays() {
        var staleRepository = repository();
        var staleModule = new ModuleOwner();
        staleModule.failureCode = "CONFIG_REPORT_CODE_EXISTS";
        var staleService = service(
                staleRepository, new FlowOwner(), staleModule, fixed());
        var report = staleService.prepare(actor(), staleRepository.turnValues.get(71L),
                policy(), reportPlan());
        staleRepository.insertGeneratedDraftProposal(
                report.storedProposal(), report.event());
        var stale = staleService.confirm(actor(), "70",
                Long.toString(report.liveProposal().id()), 0, "stale-report");
        var staleReplay = staleService.confirm(actor(), "70",
                Long.toString(report.liveProposal().id()), 0, "stale-report");
        assertThat(stale.state()).isEqualTo(AiGeneratedDraftProposal.State.STALE);
        assertThat(staleReplay).isEqualTo(stale);
        assertThat(staleModule.executeCalls).isOne();

        var deniedRepository = repository();
        var deniedFlow = new FlowOwner();
        deniedFlow.failureCode = "FLOW_DEFINITION_PERMISSION_DENIED";
        var deniedService = service(
                deniedRepository, deniedFlow, new ModuleOwner(), fixed());
        var flow = deniedService.prepare(actor(), deniedRepository.turnValues.get(71L),
                policy(), flowPlan());
        deniedRepository.insertGeneratedDraftProposal(flow.storedProposal(), flow.event());
        assertThat(deniedService.confirm(actor(), "70",
                Long.toString(flow.liveProposal().id()), 0, "denied-flow").state())
                .isEqualTo(AiGeneratedDraftProposal.State.PERMISSION_DENIED);
    }

    @Test
    void clarificationRejectAndExpiryNeverExecuteOwners() {
        var flowOwner = new FlowOwner();
        var moduleOwner = new ModuleOwner();
        var clarificationRepository = repository();
        var clarificationService = service(
                clarificationRepository, flowOwner, moduleOwner, fixed());
        var clarification = clarificationService.prepare(
                actor(), clarificationRepository.turnValues.get(71L), policy(),
                new AiGeneratedDraftPlanParser.FlowPlan(
                        null, null, 0.3, "Which approvers?", "d".repeat(64)));
        clarificationRepository.insertGeneratedDraftProposal(
                clarification.storedProposal(), clarification.event());
        assertThat(clarification.liveProposal().state()).isEqualTo(
                AiGeneratedDraftProposal.State.CLARIFICATION_REQUIRED);
        assertThat(clarification.storedProposal().clarification())
                .isEqualTo(AiGeneratedDraftProposal.REDACTED_CLARIFICATION);

        var rejectRepository = repository();
        var rejectService = service(rejectRepository, flowOwner, moduleOwner, fixed());
        var pending = rejectService.prepare(
                actor(), rejectRepository.turnValues.get(71L), policy(), flowPlan());
        rejectRepository.insertGeneratedDraftProposal(
                pending.storedProposal(), pending.event());
        assertThat(rejectService.reject(actor(), "70",
                Long.toString(pending.liveProposal().id()), 0).state())
                .isEqualTo(AiGeneratedDraftProposal.State.REJECTED);

        var expiryRepository = repository();
        var mutable = new MutableClock(NOW);
        var expiryService = service(expiryRepository, flowOwner, moduleOwner, mutable);
        var expiring = expiryService.prepare(
                actor(), expiryRepository.turnValues.get(71L), policy(), flowPlan());
        expiryRepository.insertGeneratedDraftProposal(
                expiring.storedProposal(), expiring.event());
        mutable.now = NOW.plusSeconds(601);
        assertThat(expiryService.get(actor(), "70",
                Long.toString(expiring.liveProposal().id())).state())
                .isEqualTo(AiGeneratedDraftProposal.State.EXPIRED);
        assertThat(flowOwner.executeCalls).isZero();
        assertThat(moduleOwner.executeCalls).isZero();
    }

    private static AiGeneratedDraftProposalService service(
            MemoryAiRepository repository,
            FlowOwner flow,
            ModuleOwner module,
            Clock clock) {
        return new AiGeneratedDraftProposalService(
                repository, flow, module, new SequenceIdService(5_000), clock);
    }

    private static AiGeneratedDraftPlanParser.FlowPlan flowPlan() {
        return new AiGeneratedDraftPlanParser.FlowPlan(
                "Private expense approval", List.of("7", "9"), 0.97,
                null, "a".repeat(64));
    }

    private static AiGeneratedDraftPlanParser.ReportPlan reportPlan() {
        return new AiGeneratedDraftPlanParser.ReportPlan(
                "expense_summary", "Private report name",
                "Private report description", "301",
                List.of("amount", "owner"), 0.96, null, "b".repeat(64));
    }

    private static AiGeneratedDraftPlanParser.PrintPlan printPlan() {
        return new AiGeneratedDraftPlanParser.PrintPlan(
                "expense", "expense_receipt", "Private print name", "A4",
                "PORTRAIT", "Private print title", List.of("amount", "owner"),
                "Private print footer", 0.95, null, "c".repeat(64));
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        var policy = policy();
        repository.versions.put(policy.id(), policy);
        repository.drafts.put("1:2", new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("expense"), Map.of("expense", Set.of("amount", "owner")),
                operations(), Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED,
                600, true, AiPolicy.RedactionMode.STRICT, "v85",
                "e".repeat(64), policy.id(), NOW, 7));
        repository.insertSession(new AiConversation.Session(
                70, 1, 2, 7, policy.id(), 80, 3, "gpt-generated", "v85", 41,
                AiConversation.SessionStatus.ACTIVE, "[title:redacted]", NOW, NOW));
        repository.insertTurn(turn(71));
        return repository;
    }

    private static AiConversation.Turn turn(long id) {
        return new AiConversation.Turn(
                id, 1, 2, 70, 91, 80, 3, 41,
                AiConversation.TurnStatus.RUNNING, "[request:redacted]",
                "f".repeat(64), null, null, null, 0, "AI_TURN_RUNNING", false,
                0, "request-85", "trace-85", NOW, null);
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-generated",
                Set.of("expense"), Map.of("expense", Set.of("amount", "owner")),
                operations(), 10, true, AiPolicy.RedactionMode.STRICT,
                "v85", "a".repeat(64), NOW, 7);
    }

    private static Set<String> operations() {
        return Set.of("RECORD_QUERY", "FLOW_DEFINITION_DRAFT",
                "CONFIG_REPORT_DRAFT", "CONFIG_PRINT_TEMPLATE_DRAFT");
    }

    private static AiActor actor() {
        return new AiActor(5, 1, 2, 7,
                Set.of("system.runtime.access", "ai.agent.use",
                        "flow.definition.manage", "system.configuration.manage"),
                41, "request-85", "trace-85");
    }

    private static Clock fixed() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static final class FlowOwner implements AiFlowDefinitionDraftFacade {
        private int prepareCalls;
        private int executeCalls;
        private String failureCode;

        @Override
        public PreparedDraft prepare(PrepareRequest request) {
            prepareCalls++;
            return new PreparedDraft(
                    new DraftPreview(request.operation(), request.draft()),
                    new SealedCommand("sealed_flow_command", "key-v1", "1".repeat(64)),
                    NOW.plusSeconds(900));
        }

        @Override
        public DefinitionReadback execute(ExecuteRequest request) {
            executeCalls++;
            fail(failureCode);
            return new DefinitionReadback(
                    request.operation(), "601", "Private expense approval",
                    List.of("7", "9"), 0, NOW, false);
        }
    }

    private static final class ModuleOwner implements AiModuleGeneratedDraftFacade {
        private int prepareCalls;
        private int executeCalls;
        private String failureCode;

        @Override
        public PreparedDraft prepare(PrepareRequest request) {
            prepareCalls++;
            return new PreparedDraft(
                    new DraftPreview(
                            request.operation(), request.report(), request.printTemplate()),
                    new SealedCommand("sealed_module_command", "key-v1", "2".repeat(64)),
                    NOW.plusSeconds(900));
        }

        @Override
        public DraftReadback execute(ExecuteRequest request) {
            executeCalls++;
            fail(failureCode);
            if (request.operation() == Operation.CONFIG_REPORT_DRAFT) {
                return new DraftReadback(request.operation(), new ReportReadback(
                        "701", "expense_summary", "Private report name",
                        "Private report description", "301",
                        List.of("amount", "owner"), 1, 1, NOW, NOW, false), null);
            }
            return new DraftReadback(request.operation(), null,
                    new PrintTemplateReadback(
                            "801", "901", "expense", "expense_receipt",
                            "Private print name", "A4", "PORTRAIT",
                            "Private print title", List.of("amount", "owner"),
                            "Private print footer", "DISABLED", 0, NOW, false));
        }
    }

    private static void fail(String code) {
        if (code != null) {
            throw new BusinessException(code, "Owner rejected generated draft",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return now; }
    }
}
