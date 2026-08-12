package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.plan.AiRecordQueryPlanParser;
import com.unique.examine.ai.plan.AiWorkDraftPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.ai.service.AiWorkProposalService;
import com.unique.examine.core.ai.AiRecordQueryFacade;
import com.unique.examine.core.ai.AiWorkDraftFacade;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiWorkAgentFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void producesNestedWorkProposalThenConfirmsWithoutProviderOrPrewriteExecution() {
        var repository = repository();
        var owner = new Owner();
        var provider = new ScriptedProvider("""
                {"operation":"WORK_TASK_DRAFT","title":"Close quarter",
                 "description":"Secret reconciliation","assigneeMemberId":"7",
                 "projectId":"701","dueAt":"2026-08-08T09:00:00Z",
                 "confidence":0.98,"clarification":null}
                """);
        var workService = new AiWorkProposalService(
                repository, owner, new SequenceIdService(5_000),
                Clock.fixed(NOW, ZoneOffset.UTC));
        AiRecordQueryFacade query = request -> {
            throw new AssertionError("Record query must not execute for Work draft");
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiWorkDraftPlanParser(), query, workService,
                new SequenceIdService(1_000), Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper());
        var actor = actor();
        var session = facade.createSession(
                actor, new AiAgentFacade.CreateSession("Work drafts"));

        var turn = facade.submit(
                actor, session.id(),
                new AiAgentFacade.SubmitMessage("Create a task"));

        assertThat(turn.status()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(turn.workProposal().operation()).isEqualTo("WORK_TASK_DRAFT");
        assertThat(turn.workProposal().preview().task().title())
                .isEqualTo("Close quarter");
        assertThat(turn.workProposal().preview().dailyReport()).isNull();
        assertThat(owner.prepareCalls).isOne();
        assertThat(owner.executeCalls).isZero();
        assertThat(provider.requests).singleElement().satisfies(request ->
                assertThat(request.phase()).isEqualTo(AiProviderClient.Phase.PLAN));
        assertThat(repository.usageValues).singleElement().satisfies(usage ->
                assertThat(usage.providerCalls()).isOne());
        assertThat(facade.detail(actor, session.id()).turns().getFirst()
                .workProposal().preview().task().title())
                .isEqualTo("[title:redacted]");

        var confirmed = facade.confirmWorkProposal(
                actor, session.id(), turn.workProposal().id(), 0, "agent-work-1");
        assertThat(confirmed.state()).isEqualTo("SUCCEEDED");
        assertThat(confirmed.result().task().taskId()).isEqualTo("601");
        assertThat(confirmed.result().task().status()).isEqualTo("OPEN");
        assertThat(confirmed.result().dailyReport()).isNull();
        assertThat(owner.executeCalls).isOne();

        var persisted = repository.workProposals.values() + " "
                + repository.workAttempts.values() + " " + repository.workEvents
                + " " + repository.messageValues + " " + repository.turnValues;
        assertThat(persisted)
                .doesNotContain("Close quarter")
                .doesNotContain("Secret reconciliation");
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-work", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7));
        repository.versions.put(91L, new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-work",
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "WORK_TASK_DRAFT",
                        "WORK_DAILY_REPORT_DRAFT"),
                10, true, AiPolicy.RedactionMode.STRICT, "v84",
                "a".repeat(64), NOW, 7));
        repository.insertPolicyDraft(new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "WORK_TASK_DRAFT",
                        "WORK_DAILY_REPORT_DRAFT"),
                Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED, 600, true,
                AiPolicy.RedactionMode.STRICT, "v84", "b".repeat(64),
                91L, NOW, 7));
        return repository;
    }

    private static AiActor actor() {
        return new AiActor(
                5, 1, 2, 7,
                Set.of("system.runtime.access", "ai.agent.use",
                        "work.task.create", "work.report.create"),
                41, "request-84", "trace-84");
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

    private static final class Owner implements AiWorkDraftFacade {
        private int prepareCalls;
        private int executeCalls;

        @Override
        public PreparedDraft prepare(PrepareRequest request) {
            prepareCalls++;
            return new PreparedDraft(
                    new DraftPreview(
                            request.operation(), request.task(), request.report()),
                    new SealedCommand(
                            "sealed_work_command", "key-v1", "c".repeat(64)),
                    NOW.plusSeconds(600));
        }

        @Override
        public DraftReadback execute(ExecuteRequest request) {
            executeCalls++;
            return new DraftReadback(request.operation(), new TaskReadback(
                    "601", 1, "Close quarter", "Secret reconciliation",
                    "OPEN", "7", "701", NOW.plusSeconds(86_400), NOW, NOW), null);
        }
    }
}
