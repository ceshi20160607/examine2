package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.plan.AiRecordQueryPlanParser;
import com.unique.examine.ai.plan.AiConfigurationFieldPlanParser;
import com.unique.examine.ai.plan.AiConfigurationArtifactPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.ai.service.AiConfigurationFieldProposalService;
import com.unique.examine.ai.service.AiConfigurationArtifactProposalService;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.ai.AiConfigurationArtifactFacade;
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

class AiAgentFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void executesReadOnlySliceWithExplicitActorAndPersistsRedactedAuditOnly() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(
                completion("""
                        {"operation":"RECORD_QUERY","moduleCode":"orders","filter":null,
                         "sort":[],"outputFields":["status","amount"],"limit":5}
                        """, 10, 3),
                completion("There are two authorized orders.", 8, 4)));
        var seen = new ArrayList<AiRecordQueryFacade.Request>();
        AiRecordQueryFacade query = request -> {
            seen.add(request);
            return new AiRecordQueryFacade.Result(2, List.of(
                    new AiRecordQueryFacade.Record(
                            "501", "ORD-501", 4, "ACTIVE", "Allowed title",
                            List.of(
                                    new AiRecordQueryFacade.DisplayValue("status", "OPEN"),
                                    new AiRecordQueryFacade.DisplayValue("amount", null)))));
        };
        var facade = facade(repository, provider, query, 1000);
        var actor = actor();
        var session = facade.createSession(actor, new AiAgentFacade.CreateSession("Finance questions"));

        var turn = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show customer salary secret-raw-value"));

        assertThat(turn.status()).isEqualTo("SUCCEEDED");
        assertThat(turn.answer()).isEqualTo("There are two authorized orders.");
        assertThat(turn.tool().returnedRows()).isEqualTo(1);
        assertThat(turn.tool().rows().getFirst()).doesNotContainKey("amount");
        assertThat(seen).singleElement().satisfies(request -> {
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.effectivePermissions()).isEqualTo(actor.effectivePermissions());
            assertThat(request.moduleCode()).isEqualTo("orders");
            assertThat(request.outboundFieldCodes()).containsExactly("amount", "status");
            assertThat(request.maxRows()).isEqualTo(5);
            assertThat(request.canonicalQueryJson()).contains("\"recordScope\":\"active\"");
        });
        assertThat(repository.toolValues).singleElement().satisfies(tool -> {
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.SUCCEEDED);
            assertThat(tool.resultCount()).isEqualTo(1);
            assertThat(tool.requestHash()).hasSize(64);
            assertThat(tool.responseHash()).hasSize(64);
        });
        assertThat(repository.usageValues).singleElement().satisfies(usage -> {
            assertThat(usage.providerCalls()).isEqualTo(2);
            assertThat(usage.totalTokens()).isEqualTo(25);
        });
        var stored = repository.messageValues + " " + repository.turnValues
                + " " + repository.toolValues + " " + repository.usageValues;
        assertThat(stored)
                .doesNotContain("secret-raw-value")
                .doesNotContain("There are two authorized orders")
                .doesNotContain("Allowed title")
                .doesNotContain("OPEN");
    }

    @Test
    void providerFailureProducesNoSyntheticAnswerAndLeavesAuditableRetryableTurn() {
        var repository = repository();
        AiProviderClient provider = (configuration, request) -> {
            throw new AiProviderClient.ProviderFailure("AI_PROVIDER_TIMEOUT", true);
        };
        var facade = facade(repository, provider, request -> {
            throw new AssertionError("query must not run after planning failure");
        }, 2000);
        var session = facade.createSession(
                actor(), new AiAgentFacade.CreateSession("Failure test"));

        var turn = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("raw-failure-question"));

        assertThat(turn.status()).isEqualTo("RETRYABLE");
        assertThat(turn.answer()).isNull();
        assertThat(turn.errorCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(turn.retryable()).isTrue();
        assertThat(repository.turnValues.values()).singleElement().satisfies(stored -> {
            assertThat(stored.status()).isEqualTo(AiConversation.TurnStatus.RETRYABLE);
            assertThat(stored.responseSummary()).isNull();
            assertThat(stored.resultCode()).isEqualTo("AI_PROVIDER_TIMEOUT");
            assertThat(stored.requestSummary()).doesNotContain("raw-failure-question");
        });
        assertThat(repository.messageValues)
                .extracting(AiConversation.Message::role)
                .containsExactly(AiConversation.Role.USER);
        assertThat(repository.usageValues).singleElement().satisfies(usage ->
                assertThat(usage.providerCalls()).isZero());
    }

    @Test
    void configurationPlanProducesTurnBoundProposalAndNestedConfirmation() {
        var repository = configurationRepository();
        var provider = new ScriptedProvider(List.of(completion("""
                {"operation":"CONFIG_FIELD_DRAFT","moduleCode":"orders",
                 "fieldCode":"tax_amount","fieldName":"Tax amount",
                 "fieldType":"DECIMAL","required":true,
                 "settings":{"precision":18,"scale":2,
                   "minimum":0,"maximum":999999.99},
                 "confidence":0.97,"clarification":null}
                """, 14, 6)));
        var owner = new ConfigurationOwner();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var service = new AiConfigurationFieldProposalService(
                repository, owner, new SequenceIdService(4_000), clock);
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiConfigurationFieldPlanParser(), request -> {
                    throw new AssertionError("record query must not run");
                }, service, new SequenceIdService(3_000), clock,
                new ObjectMapper());
        var session = facade.createSession(actor(),
                new AiAgentFacade.CreateSession("Configuration"));

        var turn = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("Add tax amount"));

        assertThat(turn.status()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(turn.configurationProposal().turnId()).isEqualTo(turn.id());
        assertThat(turn.configurationProposal().preview().fieldCode())
                .isEqualTo("tax_amount");
        assertThat(owner.executeCalls).isZero();
        assertThat(repository.configurationFieldProposals.values().toString())
                .doesNotContain("tax_amount")
                .doesNotContain("Tax amount")
                .contains("[field-code:redacted]");
        var storedTurn = facade.detail(actor(), session.id()).turns().getFirst();
        assertThat(storedTurn.configurationProposal().preview().fieldCode())
                .isEqualTo("[field-code:redacted]");

        var result = facade.confirmConfigurationField(
                actor(), session.id(), turn.configurationProposal().id(),
                0, "field-confirm-1");
        var replay = facade.confirmConfigurationField(
                actor(), session.id(), turn.configurationProposal().id(),
                0, "field-confirm-1");
        assertThat(result.state()).isEqualTo("SUCCEEDED");
        assertThat(result.result().fieldId()).isEqualTo("501");
        assertThat(result.result().fieldCode()).isEqualTo("[field-code:redacted]");
        assertThat(replay).isEqualTo(result);
        assertThat(owner.executeCalls).isOne();
    }

    @Test
    void artifactPlanProducesTurnBoundRedactedDetailAndNestedConfirmation() {
        var repository = artifactRepository();
        var provider = new ScriptedProvider(List.of(completion("""
                {"operation":"CONFIG_SELECTION_FIELD_DRAFT","moduleCode":"orders",
                 "fieldCode":"priority","fieldName":"Priority","fieldType":"RADIO",
                 "required":true,"dictionaryCode":"order_priority",
                 "dictionaryName":"Order priority","options":[
                  {"code":"low","label":"Low","semanticKey":"LOW","color":null,"default":false},
                  {"code":"high","label":"High","semanticKey":"HIGH","color":null,"default":true}],
                 "maxSelections":null,"confidence":0.98,"clarification":null}
                """, 15, 7)));
        var owner = new ArtifactOwner();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var service = new AiConfigurationArtifactProposalService(
                repository, owner, new SequenceIdService(6_000), clock);
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiConfigurationArtifactPlanParser(), request -> {
                    throw new AssertionError("record query must not run");
                }, service, new SequenceIdService(5_500), clock,
                new ObjectMapper());
        var session = facade.createSession(actor(),
                new AiAgentFacade.CreateSession("Artifacts"));

        var turn = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("Add priority choices"));

        assertThat(turn.status()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(turn.artifactProposal().turnId()).isEqualTo(turn.id());
        assertThat(turn.artifactProposal().artifactKind())
                .isEqualTo("SELECTION_FIELD");
        assertThat(turn.artifactProposal().preview().selectionField().fieldName())
                .isEqualTo("Priority");
        assertThat(provider.requests.getFirst().systemPrompt())
                 .contains("CONFIG_SELECTION_FIELD_DRAFT")
                 .contains("CONFIG_PAGE_LAYOUT_DRAFT")
                 .contains("CONFIG_FILTER_SCENARIO_DRAFT")
                 .contains("CONFIG_FIELD_PERMISSION_STAGE_DRAFT")
                 .contains("code,label,semanticKey,color,default")
                 .contains("columns,gap,labelPosition,density,stickyActions")
                 .contains("Never reuse a dictionary")
                 .contains("Never emit a permission code")
                 .contains("publish/activation");
        assertThat(owner.executeCalls).isZero();
        assertThat(facade.detail(actor(), session.id()).turns().getFirst()
                .artifactProposal().preview().selectionField().fieldName())
                .isEqualTo("[name:redacted]");

        var result = facade.confirmArtifact(actor(), session.id(),
                turn.artifactProposal().id(), 0, "artifact-confirm-1");
        var replay = facade.confirmArtifact(actor(), session.id(),
                turn.artifactProposal().id(), 0, "artifact-confirm-1");
        assertThat(result.state()).isEqualTo("SUCCEEDED");
        assertThat(result.result().selectionField().dictionary().dictionaryId())
                .isEqualTo("601");
        assertThat(replay).isEqualTo(result);
        assertThat(owner.executeCalls).isOne();
    }

    private static AiAgentFacade facade(
            MemoryAiRepository repository,
            AiProviderClient provider,
            AiRecordQueryFacade query,
            long firstId) {
        return new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(), query,
                new SequenceIdService(firstId), Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        var provider = new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-read", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7);
        var version = new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-read", Set.of("orders"),
                Map.of("orders", Set.of("status", "amount")),
                Set.of("RECORD_QUERY"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v1", "b".repeat(64), NOW, 7);
        var draft = new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status", "amount")),
                10, true, AiPolicy.RedactionMode.STRICT, "v1", "a".repeat(64),
                91L, NOW, 7);
        repository.insertProvider(provider);
        repository.insertPolicyDraft(draft);
        repository.versions.put(version.id(), version);
        return repository;
    }

    private static MemoryAiRepository configurationRepository() {
        var repository = new MemoryAiRepository();
        var provider = new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-write", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7);
        var version = new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "CONFIG_FIELD_DRAFT"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v2", "b".repeat(64), NOW, 7);
        var draft = new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "CONFIG_FIELD_DRAFT"), Map.of(),
                10, AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v2", "a".repeat(64),
                91L, NOW, 7);
        repository.insertProvider(provider);
        repository.insertPolicyDraft(draft);
        repository.versions.put(version.id(), version);
        return repository;
    }

    private static MemoryAiRepository artifactRepository() {
        var repository = new MemoryAiRepository();
        var provider = new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-write", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7);
        var version = new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write", Set.of("orders"),
                Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "CONFIG_SELECTION_FIELD_DRAFT",
                        "CONFIG_PAGE_LAYOUT_DRAFT"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v3", "b".repeat(64), NOW, 7);
        var draft = new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "CONFIG_SELECTION_FIELD_DRAFT",
                        "CONFIG_PAGE_LAYOUT_DRAFT"), Map.of(), 10,
                AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v3", "a".repeat(64),
                91L, NOW, 7);
        repository.insertProvider(provider);
        repository.insertPolicyDraft(draft);
        repository.versions.put(version.id(), version);
        return repository;
    }

    private static AiActor actor() {
        return new AiActor(5,
                1, 2, 7, Set.of("system.runtime.access", "ai.agent.use", "module.orders.view"),
                41, "request-1", "trace-1");
    }

    private static AiProviderClient.Completion completion(
            String content, int prompt, int response) {
        return new AiProviderClient.Completion(
                content, prompt, response, 9, AiSupport.sha256(content));
    }

    private static final class ScriptedProvider implements AiProviderClient {
        private final List<Completion> values;
        private final List<Request> requests = new ArrayList<>();
        private int index;

        private ScriptedProvider(List<Completion> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public Completion complete(AiProvider provider, Request request) {
            requests.add(request);
            return values.get(index++);
        }
    }

    private static final class ConfigurationOwner
            implements AiConfigurationFieldFacade {
        private int executeCalls;

        @Override
        public PreparedField prepare(PrepareRequest request) {
            return new PreparedField(new FieldPreview(
                    "301", "401", request.moduleCode(), 8, 9, request.field()),
                    NOW.plusSeconds(60), new SealedCommand(
                    "sealed_owner_command", "key-v1", "9".repeat(64)));
        }

        @Override
        public FieldReadback execute(ExecuteRequest request) {
            executeCalls++;
            return new FieldReadback("301", "401", "orders", 9,
                    new FieldView("501", "tax_amount", "Tax amount",
                            FieldType.DECIMAL, true, new ScalarSettings(
                            null, null, null, null, "0", "999999.99",
                            18, 2, null, null), 20, 0));
        }
    }

    private static final class ArtifactOwner
            implements AiConfigurationArtifactFacade {
        private int executeCalls;

        @Override
        public PreparedArtifact prepare(PrepareRequest request) {
            return new PreparedArtifact(new ArtifactPreview(
                    request.operation(), "301", "401", request.moduleCode(),
                    8, 9, new SelectionFieldPreview(30,
                    request.selectionField()), null), NOW.plusSeconds(60),
                    new SealedCommand("sealed_artifact_command", "key-v1",
                            "8".repeat(64)));
        }

        @Override
        public ArtifactReadback execute(ExecuteRequest request) {
            executeCalls++;
            return new ArtifactReadback(Operation.CONFIG_SELECTION_FIELD_DRAFT,
                    "301", "401", "orders", 9,
                    new SelectionFieldReadback(
                            new DictionaryView("601", "order_priority",
                                    "Order priority", 0),
                            List.of(
                                    new OptionView("701", "low", "Low", "LOW",
                                            null, false, 0, 0),
                                    new OptionView("702", "high", "High", "HIGH",
                                            null, true, 10, 0)),
                            new SelectionFieldView("801", "priority", "Priority",
                                    SelectionType.RADIO, true, "601", 30,
                                    null, 0)), null);
        }
    }
}
