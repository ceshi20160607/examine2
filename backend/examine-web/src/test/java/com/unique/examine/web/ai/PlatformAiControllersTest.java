package com.unique.examine.web.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.ai.PlatformAiActor;
import com.unique.examine.ai.PlatformAiAdminFacade;
import com.unique.examine.ai.PlatformAiAgentFacade;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.plan.PlatformAiPlanParser;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.ai.service.PlatformAiPolicyService;
import com.unique.examine.ai.service.PlatformAiProviderService;
import com.unique.examine.ai.service.PlatformAiTaskProposalService;
import com.unique.examine.core.ai.PlatformAuthorizedSystemFacade;
import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformAiControllersTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void adminRoutesUseOnlyPlatformActorAndKeepPolicyCas() throws Exception {
        var ai = new StubAdminFacade();
        var mvc = mvc(new PlatformAiAdminController(ai));
        var session = platform(Set.of(
                "platform.runtime.access", "platform.ai.policy.manage"));

        var providerBody = mvc.perform(post("/api/v1/platform/admin/ai/providers")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "request-platform-ai-1")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-platform-ai-1")
                        .contentType("application/json")
                        .content("""
                                {"code":"platform_main","name":"Platform model",
                                 "baseUrl":"http://127.0.0.1:19001","model":"fixture",
                                 "secretRef":"env://PLATFORM_AI_TEST_KEY",
                                 "timeoutSeconds":3,"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(providerBody).at("/data/id").asText())
                .isEqualTo("9001");
        assertThat(JSON.readTree(providerBody).path("requestId").asText())
                .isEqualTo("request-platform-ai-1");
        assertThat(ai.actor.accountId()).isEqualTo(2);
        assertThat(ai.provider.secretRef())
                .isEqualTo("env://PLATFORM_AI_TEST_KEY");

        var policyBody = mvc.perform(put("/api/v1/platform/admin/ai/policy")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "request-platform-policy")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-platform-policy")
                        .contentType("application/json")
                        .content("""
                                {"expectedVersion":0,"providerId":"9001",
                                 "allowedOperations":["AUTHORIZED_SYSTEMS_QUERY",
                                   "SYSTEM_SWITCH_GUIDANCE"],
                                 "maxSystems":50,"dailyRequestQuota":100,
                                 "dailyTokenQuota":100000,"maxConcurrency":2,
                                 "strictRedaction":true,
                                 "dataResidency":"PLATFORM_METADATA_ONLY",
                                 "promptVersion":"platform:v1","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(policyBody).at("/data/draftVersion").asLong())
                .isEqualTo(1);
        assertThat(ai.expectedVersion).isZero();
        assertThat(ai.policy.allowedOperations())
                .containsExactlyInAnyOrder(
                        "AUTHORIZED_SYSTEMS_QUERY", "SYSTEM_SWITCH_GUIDANCE");
        assertThat(ai.policy.settings().strictRedaction()).isTrue();

        mvc.perform(post("/api/v1/platform/admin/ai/policy:publish")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runtimeRoutesExposeAuthorizedSystemProjectionWithoutSystemPath() throws Exception {
        var ai = new StubAgentFacade();
        var mvc = mvc(new PlatformAiAgentController(ai));
        var session = platform(Set.of(
                "platform.runtime.access", "platform.ai.agent.use"));

        var pageBody = mvc.perform(get("/api/v1/platform/ai/sessions")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "request-platform-page")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-platform-page"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(pageBody).at("/data/rows/0/id").asText())
                .isEqualTo("7001");

        var turnBody = mvc.perform(post(
                        "/api/v1/platform/ai/sessions/7001/messages")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "request-platform-turn")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-platform-turn")
                        .contentType("application/json")
                        .content("{\"content\":\"Which systems may I enter?\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var turn = JSON.readTree(turnBody).path("data");
        assertThat(turn.path("operation").asText())
                .isEqualTo("AUTHORIZED_SYSTEMS_QUERY");
        assertThat(turn.at("/systems/0/systemCode").asText())
                .isEqualTo("orders");
        assertThat(turn.at("/systems/0/switchTarget").asText())
                .isEqualTo("/api/v1/context/systems/10:switch");
        assertThat(ai.actor.accountId()).isEqualTo(2);
        assertThat(ai.message.content()).isEqualTo("Which systems may I enter?");

        var proposalBody = mvc.perform(get(
                        "/api/v1/platform/ai/sessions/7001/proposals/8101")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "request-platform-proposal")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-platform-proposal"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(proposalBody).at("/data/state").asText())
                .isEqualTo("PENDING");

        var confirmedBody = mvc.perform(post(
                        "/api/v1/platform/ai/sessions/7001/proposals/8101/confirm")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "request-platform-confirm")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-platform-confirm")
                        .header("Idempotency-Key", "confirm-platform-task-1")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(confirmedBody).at("/data/result/taskId").asText())
                .isEqualTo("9101");
        assertThat(ai.proposalRevision).isEqualTo(1);
        assertThat(ai.proposalIdempotencyKey)
                .isEqualTo("confirm-platform-task-1");

        mvc.perform(post(
                        "/api/v1/platform/ai/sessions/7001/proposals/8102/reject")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "request-platform-reject")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-platform-reject")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void bothRouteGroupsRejectMissingOrSystemContext() throws Exception {
        mvc(new PlatformAiAdminController(new StubAdminFacade()))
                .perform(get("/api/v1/platform/admin/ai/providers"))
                .andExpect(status().isUnauthorized());
        mvc(new PlatformAiAgentController(new StubAgentFacade()))
                .perform(get("/api/v1/platform/ai/capability")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                new TestSession(
                                        ContextType.SYSTEM, 10L, 20L, 30L,
                                        Set.of("platform.runtime.access",
                                                "platform.ai.agent.use"))))
                .andExpect(status().isForbidden());
    }

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    private static TestSession platform(Set<String> permissions) {
        return new TestSession(
                ContextType.PLATFORM, null, null, null, permissions);
    }

    private static PlatformAiRepository repository() {
        return (PlatformAiRepository) Proxy.newProxyInstance(
                PlatformAiRepository.class.getClassLoader(),
                new Class<?>[]{PlatformAiRepository.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static PlatformTaskFacade taskOwner() {
        return (PlatformTaskFacade) Proxy.newProxyInstance(
                PlatformTaskFacade.class.getClassLoader(),
                new Class<?>[]{PlatformTaskFacade.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class StubAdminFacade extends PlatformAiAdminFacade {
        private PlatformAiActor actor;
        private PlatformAiProviderService.Command provider;
        private PlatformAiPolicyService.DraftCommand policy;
        private long expectedVersion;

        private StubAdminFacade() {
            this(repository());
        }

        private StubAdminFacade(PlatformAiRepository repository) {
            super(new PlatformAiProviderService(repository, new IdService(), CLOCK),
                    new PlatformAiPolicyService(
                            repository, new IdService(), CLOCK, JSON),
                    repository);
        }

        @Override
        public ProviderView createProvider(
                PlatformAiActor actor,
                PlatformAiProviderService.Command command) {
            this.actor = actor;
            this.provider = command;
            return new ProviderView(
                    "9001", command.code(), command.name(), command.baseUrl(),
                    command.model(), command.secretRef(), command.timeoutSeconds(),
                    command.enabled(), 0);
        }

        @Override
        public PolicyView savePolicy(
                PlatformAiActor actor,
                long expectedVersion,
                PlatformAiPolicyService.DraftCommand command) {
            this.actor = actor;
            this.expectedVersion = expectedVersion;
            this.policy = command;
            return new PolicyView(
                    1, "DRAFT", null, Long.toString(command.providerId()), 0,
                    command.allowedOperations().stream().sorted().toList(),
                    command.maxSystems(), command.dailyRequestQuota(),
                    command.dailyTokenQuota(), command.maxConcurrency(),
                    command.strictRedaction(), command.dataResidency().name(),
                    command.promptVersion(), command.enabled());
        }
    }

    private static class StubAgentFacade extends PlatformAiAgentFacade {
        private PlatformAiActor actor;
        private SubmitMessage message;
        private long proposalRevision;
        private String proposalIdempotencyKey;

        private StubAgentFacade() {
            this(repository());
        }

        private StubAgentFacade(PlatformAiRepository repository) {
            super(repository,
                    request -> new PlatformAuthorizedSystemFacade.Result(List.of()),
                    request -> new PlatformOperationsQueryFacade
                            .PersonalTasksResult(List.of()),
                    (provider, request) -> {
                        throw new UnsupportedOperationException();
                    },
                    new PlatformAiPlanParser(),
                    new PlatformAiTaskProposalService(
                            repository, taskOwner(), new IdService(), CLOCK),
                    new IdService(), CLOCK, JSON);
        }

        @Override
        public SessionPage sessions(
                PlatformAiActor actor, int page, int size) {
            this.actor = actor;
            return new SessionPage(List.of(new SessionView(
                    "7001", "[title:redacted]", "ACTIVE",
                    CLOCK.instant(), CLOCK.instant())), page, size, false);
        }

        @Override
        public TurnView submit(
                PlatformAiActor actor, String sessionId,
                SubmitMessage command) {
            this.actor = actor;
            this.message = command;
            return new TurnView(
                    "8001", "SUCCEEDED", "AUTHORIZED_SYSTEMS_QUERY",
                    "One permission-projected system.", null, false,
                    List.of(new AuthorizedSystemView(
                            "10", "orders", "Orders", "ACTIVE", "ACTIVE",
                            "AUTHORIZED", "/api/v1/context/systems/10:switch")),
                    null, null, null, actor.requestId(), actor.traceId());
        }

        @Override
        public TaskProposalView proposal(
                PlatformAiActor actor, String sessionId, String proposalId) {
            this.actor = actor;
            return pendingProposal(proposalId, actor);
        }

        @Override
        public TaskProposalView confirmTask(
                PlatformAiActor actor, String sessionId, String proposalId,
                ConfirmTaskProposal command, String idempotencyKey) {
            this.actor = actor;
            this.proposalRevision = command.expectedRevision();
            this.proposalIdempotencyKey = idempotencyKey;
            return new TaskProposalView(
                    proposalId, "SUCCEEDED", 2,
                    new TaskPreviewView(
                            "Review quota", null, null, "HIGH", true),
                    0.97, null, CLOCK.instant().plusSeconds(600),
                    new TaskResultView(
                            "9101", "Review quota", null, null, "HIGH",
                            "OPEN", "AGENT", CLOCK.instant()),
                    null, actor.requestId(), actor.traceId());
        }

        @Override
        public TaskProposalView rejectTask(
                PlatformAiActor actor, String sessionId, String proposalId,
                RejectTaskProposal command) {
            this.actor = actor;
            this.proposalRevision = command.expectedRevision();
            return new TaskProposalView(
                    proposalId, "REJECTED", 2,
                    new TaskPreviewView(
                            "Review quota", null, null, "HIGH", true),
                    0.97, null, CLOCK.instant().plusSeconds(600), null,
                    null, actor.requestId(), actor.traceId());
        }

        private static TaskProposalView pendingProposal(
                String proposalId, PlatformAiActor actor) {
            return new TaskProposalView(
                    proposalId, "PENDING", 1,
                    new TaskPreviewView(
                            "Review quota", null, null, "HIGH", true),
                    0.97, null, CLOCK.instant().plusSeconds(600), null,
                    null, actor.requestId(), actor.traceId());
        }

        @Override
        public CapabilityView capability(PlatformAiActor actor) {
            this.actor = actor;
            return new CapabilityView(true, null, "5001");
        }
    }

    private record TestSession(
            ContextType requestedContext,
            Long requestedSystemId,
            Long requestedTenantId,
            Long requestedMemberId,
            Set<String> requestedPermissions
    ) implements RequestSession {
        @Override public long sessionId() { return 1; }
        @Override public long accountId() { return 2; }
        @Override public ContextType contextType() { return requestedContext; }
        @Override public Long systemId() { return requestedSystemId; }
        @Override public Long tenantId() { return requestedTenantId; }
        @Override public Long memberId() { return requestedMemberId; }
        @Override public long permissionVersion() { return 12; }
        @Override public Set<String> permissions() { return requestedPermissions; }
    }

    @RestControllerAdvice
    private static final class TestExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(), "message", error.getMessage()));
        }
    }
}
