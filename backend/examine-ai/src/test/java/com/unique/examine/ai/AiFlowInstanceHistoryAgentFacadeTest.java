package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.plan.AiContextReadPlanParser;
import com.unique.examine.ai.plan.AiRecordQueryPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.core.ai.AiFlowInstanceHistoryReadFacade;
import com.unique.examine.core.ai.AiRecordCommentReadFacade;
import com.unique.examine.core.ai.AiRecordFileReadFacade;
import com.unique.examine.core.ai.AiRecordHistoryReadFacade;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiFlowInstanceHistoryAgentFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");
    private static final String ROUTE = "/systems/1/flows";

    @Test
    void routesTypedFlowHistoryAndPersistsOnlyHashCountEvidence() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(
                completion("""
                        {"operation":"FLOW_INSTANCE_HISTORY_QUERY",
                         "instanceId":"801","limit":7}
                        """),
                completion("Authorized Flow history.")));
        var ownerRequests = new ArrayList<
                AiFlowInstanceHistoryReadFacade.Request>();
        AiFlowInstanceHistoryReadFacade owner = request -> {
            ownerRequests.add(request);
            return new AiFlowInstanceHistoryReadFacade.Result(
                    "801", "APPROVED", 2, ROUTE, List.of(
                    new AiFlowInstanceHistoryReadFacade.Event(
                            1, "APPROVED", "PENDING", "APPROVED", "7",
                            "FLOW_HISTORY_COMMENT_SECRET_100", NOW),
                    new AiFlowInstanceHistoryReadFacade.Event(
                            2, "COMPLETION_EXECUTION", null, "SUCCEEDED", null,
                            null, NOW.plusSeconds(1))));
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), request -> {
                    throw new AssertionError("RECORD_QUERY must not execute");
                }, unusedComments(), unusedHistory(), unusedFiles(),
                new SequenceIdService(5000),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        facade.configureFlowInstanceHistoryReads(owner);
        var actor = actor();
        var session = facade.createSession(
                actor, new AiAgentFacade.CreateSession("Flow history"));

        var result = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show Flow 801 history"));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.contextResult().operation())
                .isEqualTo("FLOW_INSTANCE_HISTORY_QUERY");
        assertThat(result.contextResult().flowHistory()).satisfies(value -> {
            assertThat(value.instanceId()).isEqualTo("801");
            assertThat(value.status()).isEqualTo("APPROVED");
            assertThat(value.total()).isEqualTo(2);
            assertThat(value.route()).isEqualTo(ROUTE);
            assertThat(value.events())
                    .extracting(AiAgentFacade.ContextFlowHistoryEvent::eventType)
                    .containsExactly("APPROVED", "COMPLETION_EXECUTION");
            assertThat(value.events().getFirst().comment())
                    .isEqualTo("FLOW_HISTORY_COMMENT_SECRET_100");
            assertThat(value.events().getLast().actorMemberId()).isNull();
        });
        assertThat(result.contextResult().record()).isNull();
        assertThat(result.contextResult().tasks()).isEmpty();
        assertThat(result.contextResult().reports()).isEmpty();
        assertThat(result.contextResult().recordComments()).isNull();
        assertThat(result.contextResult().recordHistory()).isNull();
        assertThat(result.contextResult().recordFiles()).isNull();
        assertThat(result.contextResult().runtimeStatistics()).isNull();
        assertThat(result.contextResult().runtimeReport()).isNull();

        assertThat(ownerRequests).singleElement().satisfies(request -> {
            assertThat(request.accountId()).isEqualTo(actor.accountId());
            assertThat(request.systemId()).isEqualTo(actor.systemId());
            assertThat(request.tenantId()).isEqualTo(actor.tenantId());
            assertThat(request.memberId()).isEqualTo(actor.memberId());
            assertThat(request.effectivePermissions())
                    .isEqualTo(actor.effectivePermissions());
            assertThat(request.instanceId()).isEqualTo("801");
            assertThat(request.limit()).isEqualTo(7);
        });
        assertThat(repository.toolValues).singleElement().satisfies(tool -> {
            assertThat(tool.toolName())
                    .isEqualTo("FLOW_INSTANCE_HISTORY_QUERY");
            assertThat(tool.status()).isEqualTo(
                    AiConversation.TurnStatus.SUCCEEDED);
            assertThat(tool.resultCount()).isEqualTo(2);
            assertThat(tool.requestHash()).hasSize(64);
            assertThat(tool.responseHash()).hasSize(64);
        });
        assertThat(provider.requests.getFirst().systemPrompt()).contains(
                "For FLOW_INSTANCE_HISTORY_QUERY use exactly operation,instanceId,limit",
                "Runtime account, tenant, system, member, permissions, module",
                "ask for clarification in plain language");
        assertThat(provider.requests.getLast().phase())
                .isEqualTo(AiProviderClient.Phase.SUMMARY);
        assertThat(provider.requests.getLast().userContent()).contains(
                "FLOW_HISTORY_COMMENT_SECRET_100", ROUTE,
                "COMPLETION_EXECUTION", "SUCCEEDED");

        var persisted = repository.messageValues + " " + repository.turnValues
                + " " + repository.toolValues + " " + repository.usageValues;
        assertThat(persisted)
                .doesNotContain("FLOW_HISTORY_COMMENT_SECRET_100")
                .doesNotContain(ROUTE);
    }

    private static AiRecordCommentReadFacade unusedComments() {
        return request -> {
            throw new AssertionError("comment owner must not execute");
        };
    }

    private static AiRecordHistoryReadFacade unusedHistory() {
        return request -> {
            throw new AssertionError("record-history owner must not execute");
        };
    }

    private static AiRecordFileReadFacade unusedFiles() {
        return request -> {
            throw new AssertionError("file owner must not execute");
        };
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-read", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7));
        var operations = Set.of(
                "RECORD_QUERY", "FLOW_INSTANCE_HISTORY_QUERY");
        repository.versions.put(91L, new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-read", Set.of("orders"),
                Map.of("orders", Set.of("status")), operations,
                20, true, AiPolicy.RedactionMode.STRICT, "v100",
                "b".repeat(64), NOW, 7));
        repository.insertPolicyDraft(new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")), operations,
                Map.of(), 20, AiPolicy.ConfirmationMode.REQUIRED, 600, true,
                AiPolicy.RedactionMode.STRICT, "v100", "a".repeat(64),
                91L, NOW, 7));
        return repository;
    }

    private static AiActor actor() {
        return new AiActor(
                5, 1, 2, 7,
                Set.of("system.runtime.access", "ai.agent.use",
                        "flow.instance.read", "module.orders.view"),
                42, "request-100", "trace-100");
    }

    private static AiProviderClient.Completion completion(String content) {
        return new AiProviderClient.Completion(
                content, 3, 2, 9, AiSupport.sha256(content));
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
}
