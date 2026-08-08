package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.plan.AiGeneratedDraftPlanParser;
import com.unique.examine.ai.plan.AiRecordQueryPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.ai.service.AiGeneratedDraftProposalService;
import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.ai.AiRecordQueryFacade;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiGeneratedDraftAgentFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void returnsNestedGeneratedDraftAndConfirmsOnlyThroughItsOwner() {
        var repository = repository();
        var flow = new FlowOwner();
        var module = new NoModuleOwner();
        var provider = new ScriptedProvider("""
                {"operation":"FLOW_DEFINITION_DRAFT",
                 "name":"Private expense approval",
                 "approverMemberIds":["7","9"],
                 "confidence":0.98,"clarification":null}
                """);
        var service = new AiGeneratedDraftProposalService(
                repository, flow, module, new SequenceIdService(5_000), fixed());
        AiRecordQueryFacade query = request -> {
            throw new AssertionError("Record query must not execute for generated draft");
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiGeneratedDraftPlanParser(), query, service,
                new SequenceIdService(1_000), fixed(), new ObjectMapper());
        var actor = actor();
        var session = facade.createSession(
                actor, new AiAgentFacade.CreateSession("Generated drafts"));

        var turn = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Create an approval flow draft"));

        assertThat(turn.status()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(turn.generatedDraftProposal().operation())
                .isEqualTo("FLOW_DEFINITION_DRAFT");
        assertThat(turn.generatedDraftProposal().preview().flowDefinition().name())
                .isEqualTo("Private expense approval");
        assertThat(turn.generatedDraftProposal().preview().reportDefinition()).isNull();
        assertThat(turn.generatedDraftProposal().preview().printTemplate()).isNull();
        assertThat(turn.workProposal()).isNull();
        assertThat(flow.prepareCalls).isOne();
        assertThat(flow.executeCalls).isZero();
        assertThat(provider.requests).singleElement().satisfies(request ->
                assertThat(request.phase()).isEqualTo(AiProviderClient.Phase.PLAN));
        assertThat(facade.detail(actor, session.id()).turns().getFirst()
                .generatedDraftProposal().preview().flowDefinition().name())
                .isEqualTo("[name:redacted]");

        var confirmed = facade.confirmGeneratedDraftProposal(
                actor, session.id(), turn.generatedDraftProposal().id(),
                0, "agent-generated-1");
        assertThat(confirmed.state()).isEqualTo("SUCCEEDED");
        assertThat(confirmed.result().flowDefinition().definitionId())
                .isEqualTo("601");
        assertThat(confirmed.result().flowDefinition().published()).isFalse();
        assertThat(confirmed.result().reportDefinition()).isNull();
        assertThat(flow.executeCalls).isOne();

        var persisted = repository.generatedDraftProposals.values() + " "
                + repository.generatedDraftAttempts.values() + " "
                + repository.generatedDraftEvents + " "
                + repository.messageValues + " " + repository.turnValues;
        assertThat(persisted).doesNotContain("Private expense approval");
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-generated", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7));
        repository.versions.put(91L, new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-generated",
                Set.of("expense"), Map.of("expense", Set.of("amount", "owner")),
                operations(), 10, true, AiPolicy.RedactionMode.STRICT,
                "v85", "a".repeat(64), NOW, 7));
        repository.insertPolicyDraft(new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("expense"), Map.of("expense", Set.of("amount", "owner")),
                operations(), Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED,
                600, true, AiPolicy.RedactionMode.STRICT, "v85",
                "b".repeat(64), 91L, NOW, 7));
        return repository;
    }

    private static Set<String> operations() {
        return Set.of("RECORD_QUERY", "FLOW_DEFINITION_DRAFT",
                "CONFIG_REPORT_DRAFT", "CONFIG_PRINT_TEMPLATE_DRAFT");
    }

    private static AiActor actor() {
        return new AiActor(
                5, 1, 2, 7,
                Set.of("system.runtime.access", "ai.agent.use",
                        "flow.definition.manage", "system.configuration.manage"),
                41, "request-85", "trace-85");
    }

    private static Clock fixed() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static final class ScriptedProvider implements AiProviderClient {
        private final Completion completion;
        private final List<Request> requests = new ArrayList<>();

        private ScriptedProvider(String content) {
            completion = new Completion(
                    content, 5, 3, 9, AiSupport.sha256(content));
        }

        @Override
        public Completion complete(AiProvider provider, Request request) {
            requests.add(request);
            return completion;
        }
    }

    private static final class FlowOwner implements AiFlowDefinitionDraftFacade {
        private int prepareCalls;
        private int executeCalls;

        @Override
        public PreparedDraft prepare(PrepareRequest request) {
            prepareCalls++;
            return new PreparedDraft(
                    new DraftPreview(request.operation(), request.draft()),
                    new SealedCommand("sealed_flow_command", "key-v1", "c".repeat(64)),
                    NOW.plusSeconds(600));
        }

        @Override
        public DefinitionReadback execute(ExecuteRequest request) {
            executeCalls++;
            return new DefinitionReadback(
                    request.operation(), "601", "Private expense approval",
                    List.of("7", "9"), 0, NOW, false);
        }
    }

    private static final class NoModuleOwner implements AiModuleGeneratedDraftFacade {
        @Override
        public PreparedDraft prepare(PrepareRequest request) {
            throw new AssertionError("Module owner must not prepare a Flow draft");
        }

        @Override
        public DraftReadback execute(ExecuteRequest request) {
            throw new AssertionError("Module owner must not execute a Flow draft");
        }
    }
}
