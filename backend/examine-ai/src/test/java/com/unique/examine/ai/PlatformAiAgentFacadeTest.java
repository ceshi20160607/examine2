package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.domain.PlatformAiProvider;
import com.unique.examine.ai.plan.PlatformAiPlanParser;
import com.unique.examine.ai.provider.AiProviderClient;
import com.unique.examine.ai.provider.PlatformAiProviderClient;
import com.unique.examine.ai.service.PlatformAiTaskProposalService;
import com.unique.examine.core.ai.PlatformAuthorizedSystemFacade;
import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAiAgentFacadeTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void authorizedSystemsQueryPersistsOnlySafeProjectionAndRedactedContent() {
        var repository = repository(100, 100_000, 2);
        var provider = new Provider(
                "{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}",
                "You can currently enter Sales and Operations.");
        var directory = new Directory();
        var facade = facade(repository, provider, directory);
        var session = facade.createSession(actor(),
                new PlatformAiAgentFacade.CreateSession("My platform helper"));

        var turn = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage(
                        "Which systems can I enter? hidden-question"));
        var detail = facade.detail(actor(), session.id());

        assertThat(turn.status()).isEqualTo("SUCCEEDED");
        assertThat(turn.operation()).isEqualTo("AUTHORIZED_SYSTEMS_QUERY");
        assertThat(turn.systems()).extracting(
                PlatformAiAgentFacade.AuthorizedSystemView::systemCode)
                .containsExactly("sales", "operations");
        assertThat(turn.answer()).startsWith(
                "Based only on your current authorized-system directory:");
        assertThat(provider.calls).isEqualTo(2);
        assertThat(directory.calls).isOne();
        assertThat(detail.messages()).allSatisfy(message ->
                assertThat(message.status()).isEqualTo("REDACTED"));
        assertThat(detail.turns().getFirst().evidence().systems()).hasSize(2);
        assertThat(repository.evidenceValues.toString())
                .contains("/api/v1/context/systems/11:switch")
                .doesNotContain("hidden-question")
                .doesNotContain("You can currently enter")
                .doesNotContain("env://PLATFORM_AI_KEY");
        assertThat(repository.messageValues.toString())
                .doesNotContain("hidden-question")
                .doesNotContain("You can currently enter");
    }

    @Test
    void businessRecordIntentReturnsOnlySwitchGuidanceAndHasNoRecordOwner() {
        var repository = repository(100, 100_000, 2);
        var provider = new Provider("""
                {"operation":"SYSTEM_SWITCH_GUIDANCE",
                 "requestedSystemCode":"sales"}
                """);
        var directory = new Directory();
        var facade = facade(repository, provider, directory);
        var session = facade.createSession(actor(),
                new PlatformAiAgentFacade.CreateSession("Switch helper"));

        var turn = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage(
                        "Read sales order record 501 and update its amount"));

        assertThat(turn.status()).isEqualTo("SUCCEEDED");
        assertThat(turn.operation()).isEqualTo("SYSTEM_SWITCH_GUIDANCE");
        assertThat(turn.guidance().switchTarget())
                .isEqualTo("/api/v1/context/systems/11:switch");
        assertThat(turn.answer()).contains("Switch to Sales");
        assertThat(provider.calls).isOne();
        assertThat(directory.calls).isOne();
        assertThat(repository.evidenceValues.getFirst().evidenceType())
                .isEqualTo("SWITCH_GUIDANCE");
        assertThat(repository.evidenceValues.toString())
                .doesNotContain("record 501").doesNotContain("amount");
        assertThat(Arrays.stream(PlatformAiAgentFacade.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .noneMatch(name -> name.contains("AiRecordQueryFacade")
                        || name.contains("AiRecordMutationFacade")
                        || name.contains("AiFieldFillFacade"));
        assertThat(Arrays.stream(PlatformAiActor.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("systemId", "tenantId", "memberId");
    }

    @Test
    void liveRevocationAndProviderFailureBecomeAuditedTerminalFailures() {
        var revokedRepository = repository(100, 100_000, 2);
        var provider = new Provider(
                "{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}");
        PlatformAuthorizedSystemFacade revoked = request -> {
            throw new BusinessException(
                    "PLATFORM_AUTHORIZATION_CHANGED",
                    "Platform authorization changed", HttpStatus.FORBIDDEN);
        };
        var revokedFacade = facade(revokedRepository, provider, revoked);
        var revokedSession = revokedFacade.createSession(actor(),
                new PlatformAiAgentFacade.CreateSession("Revoked"));

        var revokedTurn = revokedFacade.submit(actor(), revokedSession.id(),
                new PlatformAiAgentFacade.SubmitMessage("List my systems"));

        assertThat(revokedTurn.status()).isEqualTo("FAILED");
        assertThat(revokedTurn.errorCode())
                .isEqualTo("PLATFORM_AUTHORIZATION_CHANGED");
        assertThat(revokedRepository.evidenceValues).isEmpty();
        assertThat(revokedRepository.usageValues).hasSize(1);
        assertThat(revokedRepository.auditValues.getLast().resultCode())
                .isEqualTo("PLATFORM_AUTHORIZATION_CHANGED");

        var failedRepository = repository(100, 100_000, 2);
        PlatformAiProviderClient unavailable = (configured, request) -> {
            throw new AiProviderClient.ProviderFailure(
                    "PLATFORM_AI_PROVIDER_TIMEOUT", true);
        };
        var failedFacade = facade(
                failedRepository, unavailable, new Directory());
        var failedSession = failedFacade.createSession(actor(),
                new PlatformAiAgentFacade.CreateSession("Provider failure"));
        var failed = failedFacade.submit(actor(), failedSession.id(),
                new PlatformAiAgentFacade.SubmitMessage("List my systems"));

        assertThat(failed.status()).isEqualTo("RETRYABLE");
        assertThat(failed.errorCode()).isEqualTo("PLATFORM_AI_PROVIDER_TIMEOUT");
        assertThat(failedRepository.quotas.values().iterator().next().running)
                .isZero();

        var overageRepository = repository(100, 100_000, 2);
        PlatformAiProviderClient overageProvider = (configured, request) ->
                new AiProviderClient.Completion(
                        "{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}",
                        10_001, 1, 7, "f".repeat(64));
        var overageFacade = facade(
                overageRepository, overageProvider, new Directory());
        var overageSession = overageFacade.createSession(actor(),
                new PlatformAiAgentFacade.CreateSession("Usage guard"));
        var overage = overageFacade.submit(actor(), overageSession.id(),
                new PlatformAiAgentFacade.SubmitMessage("List my systems"));

        assertThat(overage.status()).isEqualTo("FAILED");
        assertThat(overage.errorCode())
                .isEqualTo("PLATFORM_AI_PROVIDER_USAGE_INVALID");
        assertThat(overageRepository.usageValues.getFirst().totalTokens())
                .isEqualTo(10_000);
        var overageQuota = overageRepository.quotas.values().iterator().next();
        assertThat(overageQuota.usedTokens).isEqualTo(10_000);
        assertThat(overageQuota.reservedTokens).isZero();
        assertThat(overageQuota.running).isZero();
    }

    @Test
    void requestQuotaAndConcurrencyAreRejectedBeforeProviderOrOwnerCalls() {
        var quotaRepository = repository(1, 100_000, 1);
        var quotaProvider = new Provider("""
                {"operation":"SYSTEM_SWITCH_GUIDANCE",
                 "requestedSystemCode":null}
                """);
        var quotaDirectory = new Directory();
        var quotaFacade = facade(quotaRepository, quotaProvider, quotaDirectory);
        var quotaSession = quotaFacade.createSession(actor(),
                new PlatformAiAgentFacade.CreateSession("Quota"));
        quotaFacade.submit(actor(), quotaSession.id(),
                new PlatformAiAgentFacade.SubmitMessage("Open a business record"));

        assertThatThrownBy(() -> quotaFacade.submit(actor(), quotaSession.id(),
                new PlatformAiAgentFacade.SubmitMessage("Again")))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("PLATFORM_AI_REQUEST_QUOTA_EXCEEDED");
        assertThat(quotaProvider.calls).isOne();
        assertThat(quotaDirectory.calls).isOne();

        var concurrentRepository = repository(100, 100_000, 1);
        var quota = new MemoryPlatformAiRepository.Quota();
        quota.running = 1;
        concurrentRepository.quotas.put(
                "7:91:2026-08-04T00:00:00Z", quota);
        var concurrentProvider = new Provider(
                "{\"operation\":\"AUTHORIZED_SYSTEMS_QUERY\"}");
        var concurrentDirectory = new Directory();
        var concurrentFacade = facade(
                concurrentRepository, concurrentProvider, concurrentDirectory);
        var concurrentSession = concurrentFacade.createSession(actor(),
                new PlatformAiAgentFacade.CreateSession("Concurrency"));

        assertThatThrownBy(() -> concurrentFacade.submit(
                actor(), concurrentSession.id(),
                new PlatformAiAgentFacade.SubmitMessage("List")))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("PLATFORM_AI_CONCURRENCY_EXCEEDED");
        assertThat(concurrentProvider.calls).isZero();
        assertThat(concurrentDirectory.calls).isZero();
    }

    @Test
    void taskDraftBindsProposalToActorSessionAndTurnWithoutOwnerWrite() {
        var repository = repository(100, 100_000, 4);
        var provider = new Provider("""
                {"operation":"PLATFORM_TASK_DRAFT","title":"Call finance",
                 "description":"Review budget","dueAt":null,
                 "priority":"NORMAL","confidence":0.9,"clarification":null}
                """);
        var directory = new Directory();
        var owner = new TaskOwner();
        var facade = facade(repository, provider, directory, owner);
        var actor = new PlatformAiActor(
                7, Set.of("platform.runtime.access", "platform.ai.agent.use",
                "platform.task.create"), 41, "request-1", "trace-1");
        var session = facade.createSession(
                actor, new PlatformAiAgentFacade.CreateSession("Task helper"));

        var turn = facade.submit(actor, session.id(),
                new PlatformAiAgentFacade.SubmitMessage(
                        "Remind me to call finance"));

        assertThat(turn.operation()).isEqualTo("PLATFORM_TASK_DRAFT");
        assertThat(turn.proposal().state()).isEqualTo("PENDING");
        assertThat(turn.proposal().preview().title()).isEqualTo("Call finance");
        var stored = repository.taskProposalValues.get(
                Long.parseLong(turn.proposal().id()));
        assertThat(stored.sessionId()).isEqualTo(Long.parseLong(session.id()));
        assertThat(stored.turnId()).isEqualTo(Long.parseLong(turn.id()));
        assertThat(owner.executeCalls).isZero();
        assertThat(directory.calls).isZero();
    }

    @Test
    void operationsQueriesReturnTypedBoundedAndRedactedEvidence() {
        var repository = repository(100, 100_000, 4);
        var provider = new Provider(
                operationsPlan("PERSONAL_TASKS", 10),
                operationsPlan("AI_QUOTA", 1),
                operationsPlan("SERVICE_HEALTH", 4),
                operationsPlan("AGENT_ACTIVITY", 5),
                """
                {"operation":"PLATFORM_OPERATIONS_QUERY","queryKind":null,
                 "limit":null,"confidence":0.4,
                 "clarification":"Which operation should I inspect?"}
                """);
        var directory = new Directory();
        var owner = new OperationsOwner();
        var facade = facade(
                repository, provider, directory, new UnusedTaskOwner(), owner);
        var session = facade.createSession(
                actor(), new PlatformAiAgentFacade.CreateSession("Operations"));

        var tasks = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage("Show my tasks"));
        var quota = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage("Show my quota"));
        var health = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage("Show service health"));
        var activity = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage("Show my activity"));
        var clarification = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage("Show operations"));

        assertThat(tasks.operations().personalTasks()).hasSize(1);
        assertThat(tasks.operations().personalTasks().getFirst().title())
                .isEqualTo("Call finance");
        assertThat(quota.operations().quota().policyVersion()).isEqualTo("91");
        assertThat(quota.operations().quota().remainingTokens()).isEqualTo(89_000);
        assertThat(health.operations().serviceHealth().status())
                .isEqualTo("DEGRADED");
        assertThat(activity.operations().agentActivity().getFirst().event())
                .isEqualTo("TURN_SUCCEEDED");
        assertThat(clarification.operations().queryKind()).isNull();
        assertThat(clarification.operations().clarification()).isNotBlank();
        assertThat(owner.calls).isEqualTo(4);
        assertThat(directory.calls).isZero();
        assertThat(repository.evidenceValues.getFirst().projectionJson())
                .contains("[title:redacted]")
                .doesNotContain("Call finance");
        var stored = facade.detail(actor(), session.id()).turns().getFirst();
        assertThat(stored.operations().personalTasks().getFirst().title())
                .isEqualTo("[title:redacted]");
    }

    @Test
    void liveOperationsPermissionDenialReturnsNoProjection() {
        var repository = repository(100, 100_000, 4);
        var owner = new DeniedOperationsOwner();
        var facade = facade(
                repository, new Provider(operationsPlan("SERVICE_HEALTH", 4)),
                new Directory(), new UnusedTaskOwner(), owner);
        var session = facade.createSession(
                actor(), new PlatformAiAgentFacade.CreateSession("Denied"));

        var turn = facade.submit(actor(), session.id(),
                new PlatformAiAgentFacade.SubmitMessage("Show service health"));

        assertThat(turn.status()).isEqualTo("FAILED");
        assertThat(turn.errorCode()).isEqualTo("PLATFORM_PERMISSION_DENIED");
        assertThat(turn.operations()).isNull();
        assertThat(repository.evidenceValues).isEmpty();
        assertThat(owner.calls).isOne();
    }

    private static String operationsPlan(String kind, int limit) {
        return "{\"operation\":\"PLATFORM_OPERATIONS_QUERY\",\"queryKind\":\""
                + kind + "\",\"limit\":" + limit
                + ",\"confidence\":0.9,\"clarification\":null}";
    }

    private static PlatformAiAgentFacade facade(
            MemoryPlatformAiRepository repository,
            PlatformAiProviderClient provider,
            PlatformAuthorizedSystemFacade directory) {
        return facade(repository, provider, directory, new UnusedTaskOwner(),
                new UnusedOperationsOwner());
    }

    private static PlatformAiAgentFacade facade(
            MemoryPlatformAiRepository repository,
            PlatformAiProviderClient provider,
            PlatformAuthorizedSystemFacade directory,
            PlatformTaskFacade taskOwner) {
        return facade(repository, provider, directory, taskOwner,
                new UnusedOperationsOwner());
    }

    private static PlatformAiAgentFacade facade(
            MemoryPlatformAiRepository repository,
            PlatformAiProviderClient provider,
            PlatformAuthorizedSystemFacade directory,
            PlatformTaskFacade taskOwner,
            PlatformOperationsQueryFacade operationsOwner) {
        var ids = new SequenceIdService(1_000);
        return new PlatformAiAgentFacade(
                repository, directory, operationsOwner, provider,
                new PlatformAiPlanParser(),
                new PlatformAiTaskProposalService(
                        repository, taskOwner, ids, CLOCK),
                ids, CLOCK, new ObjectMapper());
    }

    private static MemoryPlatformAiRepository repository(
            int requestQuota, int tokenQuota, int concurrency) {
        var repository = new MemoryPlatformAiRepository();
        repository.providerValues.put(80L, new PlatformAiProvider(
                80, "openai", "OpenAI", "https://api.example.test/v1",
                "gpt-platform", "env://PLATFORM_AI_KEY", 10, true, 3,
                NOW, 7, NOW, 7));
        var settings = new PlatformAiPolicy.Settings(
                PlatformAiPolicy.SUPPORTED_OPERATIONS, 50, requestQuota,
                tokenQuota, concurrency, true,
                PlatformAiPolicy.DataResidency.PLATFORM_METADATA_ONLY,
                "v1", true);
        repository.versionValues.put(91L, new PlatformAiPolicy.Version(
                91, 90, 1, 80, 3, "gpt-platform", settings,
                "b".repeat(64), NOW, 7));
        repository.draftValue = new PlatformAiPolicy.Draft(
                90, 1, PlatformAiPolicy.DraftStatus.PUBLISHED, 80, 3,
                settings, "a".repeat(64), 91L, NOW, 7);
        return repository;
    }

    private static PlatformAiActor actor() {
        return new PlatformAiActor(
                7, Set.of("platform.runtime.access", "platform.ai.agent.use"),
                41, "request-1", "trace-1");
    }

    private static final class Provider implements PlatformAiProviderClient {
        private final ArrayDeque<String> values;
        private int calls;

        private Provider(String... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public AiProviderClient.Completion complete(
                PlatformAiProvider provider, AiProviderClient.Request request) {
            calls++;
            var value = values.removeFirst();
            return new AiProviderClient.Completion(
                    value, 12, 5, 7, AiSupport.sha256(value));
        }
    }

    private static final class Directory
            implements PlatformAuthorizedSystemFacade {
        private int calls;

        @Override
        public Result authorizedSystems(Request request) {
            calls++;
            return new Result(List.of(
                    new SystemAccess(
                            "11", "sales", "Sales", "ACTIVE", "ACTIVE",
                            "AUTHORIZED", "/api/v1/context/systems/11:switch"),
                    new SystemAccess(
                            "12", "operations", "Operations", "INITIALIZING",
                            "ACTIVE", "AUTHORIZED",
                            "/api/v1/context/systems/12:switch")));
        }
    }

    private static final class UnusedTaskOwner implements PlatformTaskFacade {
        @Override
        public PreparedTask prepare(PrepareRequest request) {
            throw new AssertionError("Task owner must not be called");
        }

        @Override
        public TaskView execute(ExecuteRequest request) {
            throw new AssertionError("Task owner must not be called");
        }
    }

    private static final class TaskOwner implements PlatformTaskFacade {
        private int executeCalls;

        @Override
        public PreparedTask prepare(PrepareRequest request) {
            return new PreparedTask(
                    new TaskPreview(
                            request.accountId(), request.draft().title(),
                            request.draft().description(), request.draft().dueAt(),
                            request.draft().priority(), Status.OPEN, Source.AGENT),
                    NOW.plusSeconds(600), new SealedCommand(
                    "sealed-command", "key-v1",
                    AiSupport.sha256("sealed-command")));
        }

        @Override
        public TaskView execute(ExecuteRequest request) {
            executeCalls++;
            throw new AssertionError("Submit must not execute a platform task");
        }
    }

    private static final class UnusedOperationsOwner
            implements PlatformOperationsQueryFacade {
        @Override
        public Result query(Request request) {
            throw new AssertionError("Operations owner must not be called");
        }
    }

    private static final class OperationsOwner
            implements PlatformOperationsQueryFacade {
        private int calls;

        @Override
        public Result query(Request request) {
            calls++;
            assertThat(request.accountId()).isEqualTo(7);
            assertThat(request.authorizationEpoch()).isEqualTo(41);
            return switch (request.queryKind()) {
                case PERSONAL_TASKS -> new PersonalTasksResult(List.of(
                        new PersonalTask(
                                "9001", "Call finance", NOW.plusSeconds(3_600),
                                PlatformTaskFacade.Priority.NORMAL,
                                PlatformTaskFacade.Status.OPEN,
                                PlatformTaskFacade.Source.AGENT,
                                NOW.minusSeconds(60))));
                case AI_QUOTA -> new AiQuotaResult(
                        NOW, NOW.plusSeconds(86_400), 100, 5, 95,
                        100_000, 1_000, 10_000, 89_000,
                        4, 1, 3);
                case SERVICE_HEALTH -> new ServiceHealthResult(
                        NOW, List.of(
                        new ServiceHealth(ServiceName.DATABASE, HealthState.UP),
                        new ServiceHealth(ServiceName.REDIS, HealthState.UP),
                        new ServiceHealth(
                                ServiceName.PLATFORM_AI_CONFIGURATION,
                                HealthState.UP),
                        new ServiceHealth(
                                ServiceName.PLATFORM_AI_PROVIDER,
                                HealthState.DEGRADED)));
                case AGENT_ACTIVITY -> new AgentActivityResult(List.of(
                        new AgentActivity(
                                "TURN_SUCCEEDED", NOW.minusSeconds(10),
                                "PLATFORM_OPERATIONS_QUERY", "OK",
                                "request-1", "trace-1")));
            };
        }
    }

    private static final class DeniedOperationsOwner
            implements PlatformOperationsQueryFacade {
        private int calls;

        @Override
        public Result query(Request request) {
            calls++;
            throw new BusinessException(
                    "PLATFORM_PERMISSION_DENIED",
                    "Platform permission was revoked", HttpStatus.FORBIDDEN);
        }
    }
}
