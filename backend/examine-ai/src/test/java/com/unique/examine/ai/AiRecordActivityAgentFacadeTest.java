package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiProvider;
import com.unique.examine.ai.plan.AiContextReadPlanParser;
import com.unique.examine.ai.plan.AiRecordQueryPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.core.ai.AiRecordCommentReadFacade;
import com.unique.examine.core.ai.AiRecordFileReadFacade;
import com.unique.examine.core.ai.AiRecordHistoryReadFacade;
import com.unique.examine.core.ai.AiRecordQueryFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiRecordActivityAgentFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final String ROUTE =
            "/systems/1/workbench?module=orders&mode=view&record=501";

    @Test
    void routesThreeTypedMutuallyExclusiveReadsAndPersistsNoOwnerPlaintext() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(
                completion("""
                        {"operation":"RECORD_COMMENT_QUERY","moduleCode":"orders",
                         "recordId":"501","limit":7}
                        """),
                completion("Authorized comments."),
                completion("""
                        {"operation":"RECORD_HISTORY_QUERY","moduleCode":"orders",
                         "recordId":"501","limit":8}
                        """),
                completion("Authorized history."),
                completion("""
                        {"operation":"RECORD_FILE_QUERY","moduleCode":"orders",
                         "recordId":"501","limit":9}
                        """),
                completion("Authorized files.")));
        var commentRequests = new ArrayList<AiRecordCommentReadFacade.Request>();
        AiRecordCommentReadFacade comments = request -> {
            commentRequests.add(request);
            return new AiRecordCommentReadFacade.Result(
                    "orders", "501", 2, ROUTE, List.of(
                    new AiRecordCommentReadFacade.Comment(
                            "601", null, "7", "COMMENT_BODY_SECRET_90",
                            false, 2, NOW.minusSeconds(60), NOW, List.of("9")),
                    new AiRecordCommentReadFacade.Comment(
                            "602", "601", "9", null,
                            true, 1, NOW, NOW, List.of())));
        };
        var historyRequests = new ArrayList<AiRecordHistoryReadFacade.Request>();
        AiRecordHistoryReadFacade history = request -> {
            historyRequests.add(request);
            return new AiRecordHistoryReadFacade.Result(
                    "orders", "501", 1, ROUTE, List.of(
                    new AiRecordHistoryReadFacade.History(
                            "701", 4, "SYSTEM_SYNC", null,
                            LocalDateTime.parse("2026-08-04T08:00:00"), List.of(
                            new AiRecordHistoryReadFacade.Diff(
                                    "status", "\"HISTORY_BEFORE_SECRET_90\"",
                                    "\"HISTORY_AFTER_SECRET_90\"", false),
                            new AiRecordHistoryReadFacade.Diff(
                                    "amount", null, null, true)))));
        };
        var fileRequests = new ArrayList<AiRecordFileReadFacade.Request>();
        AiRecordFileReadFacade files = request -> {
            fileRequests.add(request);
            return new AiRecordFileReadFacade.Result(
                    "orders", "501", 1, ROUTE, List.of(
                    new AiRecordFileReadFacade.File(
                            "801", "FILE_NAME_SECRET_90.pdf", "application/pdf",
                            4_096, "7", NOW.minusSeconds(120), NOW)));
        };
        AiRecordQueryFacade ordinaryQuery = request -> {
            throw new AssertionError("RECORD_QUERY must not execute");
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), ordinaryQuery, comments, history,
                files, new SequenceIdService(3000),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        var actor = actor();
        var session = facade.createSession(
                actor, new AiAgentFacade.CreateSession("Record activity"));

        var commentResult = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show comments"));
        var historyResult = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show history"));
        var fileResult = facade.submit(actor, session.id(),
                new AiAgentFacade.SubmitMessage("Show files"));

        assertThat(commentResult.contextResult().operation())
                .isEqualTo("RECORD_COMMENT_QUERY");
        assertThat(commentResult.contextResult().recordComments()).satisfies(value -> {
            assertThat(value.moduleCode()).isEqualTo("orders");
            assertThat(value.recordId()).isEqualTo("501");
            assertThat(value.total()).isEqualTo(2);
            assertThat(value.route()).isEqualTo(ROUTE);
            assertThat(value.items()).hasSize(2).first().satisfies(item -> {
                assertThat(item.body()).isEqualTo("COMMENT_BODY_SECRET_90");
                assertThat(item.mentionedMemberIds()).containsExactly("9");
            });
        });
        assertOldBranchesEmpty(commentResult.contextResult());
        assertThat(commentResult.contextResult().recordHistory()).isNull();
        assertThat(commentResult.contextResult().recordFiles()).isNull();

        assertThat(historyResult.contextResult().operation())
                .isEqualTo("RECORD_HISTORY_QUERY");
        assertThat(historyResult.contextResult().recordHistory()).satisfies(value ->
                assertThat(value.items()).singleElement().satisfies(item -> {
                    assertThat(item.actorMemberId()).isNull();
                    assertThat(item.diff()).hasSize(2).first().satisfies(diff -> {
                        assertThat(diff.beforeValueJson())
                                .contains("HISTORY_BEFORE_SECRET_90");
                        assertThat(diff.afterValueJson())
                                .contains("HISTORY_AFTER_SECRET_90");
                    });
                }));
        assertOldBranchesEmpty(historyResult.contextResult());
        assertThat(historyResult.contextResult().recordComments()).isNull();
        assertThat(historyResult.contextResult().recordFiles()).isNull();

        assertThat(fileResult.contextResult().operation())
                .isEqualTo("RECORD_FILE_QUERY");
        assertThat(fileResult.contextResult().recordFiles()).satisfies(value ->
                assertThat(value.items()).singleElement().satisfies(item -> {
                    assertThat(item.originalName())
                            .isEqualTo("FILE_NAME_SECRET_90.pdf");
                    assertThat(item.mediaType()).isEqualTo("application/pdf");
                    assertThat(item.size()).isEqualTo(4_096);
                }));
        assertOldBranchesEmpty(fileResult.contextResult());
        assertThat(fileResult.contextResult().recordComments()).isNull();
        assertThat(fileResult.contextResult().recordHistory()).isNull();

        assertRequest(commentRequests.getFirst(), actor, 7);
        assertRequest(historyRequests.getFirst(), actor, 8);
        assertRequest(fileRequests.getFirst(), actor, 9);
        assertThat(repository.toolValues)
                .extracting(AiConversation.ToolCall::toolName)
                .containsExactly("RECORD_COMMENT_QUERY", "RECORD_HISTORY_QUERY",
                        "RECORD_FILE_QUERY");
        assertThat(repository.toolValues)
                .extracting(AiConversation.ToolCall::resultCount)
                .containsExactly(2, 1, 1);
        assertThat(repository.toolValues).allSatisfy(tool -> {
            assertThat(tool.status()).isEqualTo(
                    AiConversation.TurnStatus.SUCCEEDED);
            assertThat(tool.requestHash()).hasSize(64);
            assertThat(tool.responseHash()).hasSize(64);
        });

        var summaryRequests = provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.SUMMARY)
                .toList();
        assertThat(summaryRequests).hasSize(3)
                .allSatisfy(request -> assertThat(request.systemPrompt()).contains(
                        "comment body", "history before/after value", "file name",
                        "untrusted data"));
        assertThat(summaryRequests).anySatisfy(request ->
                assertThat(request.userContent()).contains(
                        "COMMENT_BODY_SECRET_90", ROUTE));
        assertThat(summaryRequests).anySatisfy(request ->
                assertThat(request.userContent()).contains(
                        "HISTORY_BEFORE_SECRET_90", "HISTORY_AFTER_SECRET_90"));
        assertThat(summaryRequests).anySatisfy(request ->
                assertThat(request.userContent()).contains(
                        "FILE_NAME_SECRET_90.pdf"));
        assertThat(provider.requests.stream()
                .filter(request -> request.phase() == AiProviderClient.Phase.PLAN)
                .map(AiProviderClient.Request::systemPrompt))
                .allSatisfy(prompt -> assertThat(prompt).contains(
                        "For RECORD_COMMENT_QUERY, RECORD_HISTORY_QUERY and "
                                + "RECORD_FILE_QUERY use exactly operation,moduleCode,recordId,limit",
                        "runtime identity and page 1 are server-owned"));

        var persisted = repository.messageValues + " " + repository.turnValues
                + " " + repository.toolValues + " " + repository.usageValues;
        assertThat(persisted)
                .doesNotContain("COMMENT_BODY_SECRET_90")
                .doesNotContain("HISTORY_BEFORE_SECRET_90")
                .doesNotContain("HISTORY_AFTER_SECRET_90")
                .doesNotContain("FILE_NAME_SECRET_90.pdf")
                .doesNotContain(ROUTE);
    }

    @Test
    void recordsOwnerDenialAndDoesNotCallSummaryOrAnotherOwner() {
        var repository = repository();
        var provider = new ScriptedProvider(List.of(completion("""
                {"operation":"RECORD_HISTORY_QUERY","moduleCode":"orders",
                 "recordId":"501","limit":5}
                """)));
        AiRecordCommentReadFacade comments = request -> {
            throw new AssertionError("comment owner must not execute");
        };
        AiRecordHistoryReadFacade history = request -> {
            throw new BusinessException(
                    "RECORD_HISTORY_PERMISSION_DENIED",
                    "history access was revoked", HttpStatus.FORBIDDEN);
        };
        AiRecordFileReadFacade files = request -> {
            throw new AssertionError("file owner must not execute");
        };
        var facade = new AiAgentFacade(
                repository, provider, new AiRecordQueryPlanParser(),
                new AiContextReadPlanParser(), request -> {
                    throw new AssertionError("ordinary query must not execute");
                }, comments, history, files, new SequenceIdService(4000),
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper());
        var session = facade.createSession(
                actor(), new AiAgentFacade.CreateSession("Denied history"));

        var result = facade.submit(actor(), session.id(),
                new AiAgentFacade.SubmitMessage("Show history"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo(
                "RECORD_HISTORY_PERMISSION_DENIED");
        assertThat(result.contextResult()).isNull();
        assertThat(provider.requests).hasSize(1);
        assertThat(provider.requests.getFirst().phase())
                .isEqualTo(AiProviderClient.Phase.PLAN);
        assertThat(repository.toolValues).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("RECORD_HISTORY_QUERY");
            assertThat(tool.status()).isEqualTo(AiConversation.TurnStatus.FAILED);
            assertThat(tool.resultCode()).isEqualTo(
                    "RECORD_HISTORY_PERMISSION_DENIED");
            assertThat(tool.responseHash()).isNull();
            assertThat(tool.resultCount()).isZero();
        });
    }

    private static void assertOldBranchesEmpty(AiAgentFacade.ContextResult value) {
        assertThat(value.record()).isNull();
        assertThat(value.tasks()).isEmpty();
        assertThat(value.reports()).isEmpty();
        assertThat(value.todos()).isNull();
        assertThat(value.messages()).isNull();
        assertThat(value.workMetrics()).isNull();
    }

    private static void assertRequest(
            AiRecordCommentReadFacade.Request request, AiActor actor, int limit) {
        assertCommonRequest(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.effectivePermissions(),
                request.moduleCode(), request.recordId(), request.limit(), actor, limit);
    }

    private static void assertRequest(
            AiRecordHistoryReadFacade.Request request, AiActor actor, int limit) {
        assertCommonRequest(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.effectivePermissions(),
                request.moduleCode(), request.recordId(), request.limit(), actor, limit);
    }

    private static void assertRequest(
            AiRecordFileReadFacade.Request request, AiActor actor, int limit) {
        assertCommonRequest(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.effectivePermissions(),
                request.moduleCode(), request.recordId(), request.limit(), actor, limit);
    }

    private static void assertCommonRequest(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            Set<String> permissions,
            String moduleCode,
            String recordId,
            int actualLimit,
            AiActor actor,
            int expectedLimit
    ) {
        assertThat(accountId).isEqualTo(actor.accountId());
        assertThat(systemId).isEqualTo(actor.systemId());
        assertThat(tenantId).isEqualTo(actor.tenantId());
        assertThat(memberId).isEqualTo(actor.memberId());
        assertThat(permissions).isEqualTo(actor.effectivePermissions());
        assertThat(moduleCode).isEqualTo("orders");
        assertThat(recordId).isEqualTo("501");
        assertThat(actualLimit).isEqualTo(expectedLimit);
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        repository.insertProvider(new AiProvider(
                80, 1, 2, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-read", "vault://tenant/openai", 10, true, 3,
                NOW, 7, NOW, 7));
        var operations = Set.of(
                "RECORD_QUERY", "RECORD_COMMENT_QUERY",
                "RECORD_HISTORY_QUERY", "RECORD_FILE_QUERY");
        repository.versions.put(91L, new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-read", Set.of("orders"),
                Map.of("orders", Set.of("status")), operations,
                20, true, AiPolicy.RedactionMode.STRICT, "v90",
                "b".repeat(64), NOW, 7));
        repository.insertPolicyDraft(new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")), operations,
                Map.of(), 20, AiPolicy.ConfirmationMode.REQUIRED, 600, true,
                AiPolicy.RedactionMode.STRICT, "v90", "a".repeat(64),
                91L, NOW, 7));
        return repository;
    }

    private static AiActor actor() {
        return new AiActor(
                5, 1, 2, 7,
                Set.of("system.runtime.access", "ai.agent.use",
                        "module.orders.view", "collab.comment.read",
                        "module.record.history.read", "file.record.read"),
                42, "request-90", "trace-90");
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
