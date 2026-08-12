package com.unique.examine.web.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.ai.AiActor;
import com.unique.examine.ai.AiAdminFacade;
import com.unique.examine.ai.AiAgentFacade;
import com.unique.examine.ai.plan.AiRecordQueryPlanParser;
import com.unique.examine.ai.repository.JdbcAiRepository;
import com.unique.examine.ai.service.AiPolicyService;
import com.unique.examine.ai.service.AiProviderService;
import com.unique.examine.core.ai.AiRecordPolicyCatalogFacade;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

class AiControllersTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void adminRoutesKeepStringIdsPolicyMapCasAndCorrelation() throws Exception {
        var ai = new StubAdminFacade();
        var mvc = admin(ai);
        var request = session(Set.of(
                "ai.policy.manage", "system.runtime.access", "module.orders.view"));

        var providerBody = mvc.perform(post(
                        "/api/v1/systems/10/admin/ai/providers")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-ai-1")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-ai-1")
                        .contentType("application/json")
                        .content("""
                                {"code":"primary","name":"Primary",
                                 "baseUrl":"http://127.0.0.1:19001","model":"fixture",
                                 "secretRef":"env://AI_TEST_KEY","timeoutSeconds":3,
                                 "enabled":true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var provider = JSON.readTree(providerBody);
        assertThat(provider.path("data").path("id").asText()).isEqualTo("9001");
        assertThat(provider.path("requestId").asText()).isEqualTo("req-ai-1");
        assertThat(ai.actor.requestId()).isEqualTo("req-ai-1");

        var policyBody = mvc.perform(put(
                        "/api/v1/systems/10/admin/ai/policy")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-ai-policy")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-ai-policy")
                        .contentType("application/json")
                        .content("""
                                {"expectedVersion":0,"providerId":"9001",
                                 "moduleCodes":["orders"],
                                 "outboundFields":{"orders":["name"]},
                                 "maxRows":10,"redactionMode":"STRICT",
                                 "promptVersion":"v1","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(policyBody).path("data")
                .path("outboundFields").path("orders").get(0).asText())
                .isEqualTo("name");
        assertThat(ai.policy.outboundFields())
                .isEqualTo(Map.of("orders", Set.of("name")));
        assertThat(ai.expectedVersion).isZero();

        mvc.perform(post("/api/v1/systems/10/admin/ai/policy:publish")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void runtimeRoutesExposeBoundedPageAndSuccessfulTurn() throws Exception {
        var ai = new StubAgentFacade();
        var mvc = runtime(ai);
        var request = session(Set.of("system.runtime.access", "ai.agent.use"));

        var pageBody = mvc.perform(get("/api/v1/systems/10/ai/sessions")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-ai-page")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-ai-page"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var page = JSON.readTree(pageBody).path("data");
        assertThat(page.path("rows").get(0).path("id").asText()).isEqualTo("7001");
        assertThat(page.path("hasMore").asBoolean()).isFalse();

        var turnBody = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/messages")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-ai-2")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-ai-2")
                        .contentType("application/json")
                        .content("{\"content\":\"查询订单\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var turn = JSON.readTree(turnBody).path("data");
        assertThat(turn.path("id").asText()).isEqualTo("8001");
        assertThat(turn.path("tool").path("rows").get(0)
                .path("recordId").asText()).isEqualTo("6001");
        assertThat(ai.actor.authorizationEpoch()).isEqualTo(12);
        assertThat(ai.actor.requestId()).isEqualTo("req-ai-2");
        assertThat(ai.message.content()).isEqualTo("查询订单");

        var pendingBody = mvc.perform(get(
                        "/api/v1/systems/10/ai/confirmations/9002")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-ai-confirm-get")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-ai-confirm-get"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(pendingBody).at("/data/state").asText())
                .isEqualTo("PENDING");

        var confirmedBody = mvc.perform(post(
                        "/api/v1/systems/10/ai/confirmations/9002/confirm")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "req-ai-confirm")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-ai-confirm")
                        .header("Idempotency-Key", "confirm-key-1")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(confirmedBody).at("/data/result/recordId").asText())
                .isEqualTo("6002");
        assertThat(ai.idempotencyKey).isEqualTo("confirm-key-1");
        assertThat(ai.expectedConfirmationVersion).isZero();

        var rejectedBody = mvc.perform(post(
                        "/api/v1/systems/10/ai/confirmations/9003/reject")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "request-ai-reject")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-ai-reject")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(rejectedBody).at("/data/state").asText())
                .isEqualTo("REJECTED");

        var configurationBody = mvc.perform(get(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "configuration-proposals/9101")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-config-get")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-config-get"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(configurationBody).at("/data/preview/fieldCode")
                .asText()).isEqualTo("ai_priority");

        var confirmedConfiguration = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "configuration-proposals/9101/confirm")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-config-confirm")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-config-confirm")
                        .header("Idempotency-Key", "config-confirm-key-1")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(confirmedConfiguration)
                .at("/data/result/draftStatus").asText()).isEqualTo("DRAFT");
        assertThat(ai.configurationIdempotencyKey)
                .isEqualTo("config-confirm-key-1");
        assertThat(ai.expectedConfigurationRevision).isZero();

        var rejectedConfiguration = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "configuration-proposals/9102/reject")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-config-reject")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-config-reject")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(rejectedConfiguration).at("/data/state").asText())
                .isEqualTo("REJECTED");

        var artifactBody = mvc.perform(get(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "configuration-artifact-proposals/9201")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-artifact-get")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-artifact-get"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(artifactBody).at("/data/artifactKind").asText())
                .isEqualTo("SELECTION_FIELD");

        var confirmedArtifact = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "configuration-artifact-proposals/9201/confirm")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-artifact-confirm")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-artifact-confirm")
                        .header("Idempotency-Key", "artifact-confirm-key-1")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(confirmedArtifact).at("/data/state").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(ai.artifactIdempotencyKey)
                .isEqualTo("artifact-confirm-key-1");
        assertThat(ai.expectedArtifactRevision).isZero();

        var rejectedArtifact = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "configuration-artifact-proposals/9202/reject")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-artifact-reject")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-artifact-reject")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(rejectedArtifact).at("/data/state").asText())
                .isEqualTo("REJECTED");

        var workBody = mvc.perform(get(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "work-proposals/9301")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-work-get")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-work-get"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(workBody).at("/data/operation").asText())
                .isEqualTo("WORK_TASK_DRAFT");
        assertThat(JSON.readTree(workBody).at("/data/preview/task/status")
                .isMissingNode()).isTrue();

        var confirmedWork = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "work-proposals/9301/confirm")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-work-confirm")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-work-confirm")
                        .header("Idempotency-Key", "work-confirm-key-1")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(confirmedWork).at("/data/state").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(JSON.readTree(confirmedWork)
                .at("/data/result/task/status").asText()).isEqualTo("OPEN");
        assertThat(ai.workIdempotencyKey).isEqualTo("work-confirm-key-1");
        assertThat(ai.expectedWorkRevision).isZero();

        var rejectedWork = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "work-proposals/9302/reject")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-work-reject")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-work-reject")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(rejectedWork).at("/data/state").asText())
                .isEqualTo("REJECTED");

        var generatedBody = mvc.perform(get(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "generated-draft-proposals/9501")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-generated-get")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-generated-get"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(generatedBody).at("/data/operation").asText())
                .isEqualTo("FLOW_DEFINITION_DRAFT");
        assertThat(JSON.readTree(generatedBody)
                .at("/data/preview/flowDefinition/name").asText())
                .isEqualTo("AI approval flow");

        var confirmedGenerated = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "generated-draft-proposals/9501/confirm")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-generated-confirm")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-generated-confirm")
                        .header("Idempotency-Key", "generated-confirm-key-1")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(confirmedGenerated).at("/data/state").asText())
                .isEqualTo("SUCCEEDED");
        assertThat(JSON.readTree(confirmedGenerated)
                .at("/data/result/flowDefinition/published").asBoolean())
                .isFalse();
        assertThat(ai.generatedDraftIdempotencyKey)
                .isEqualTo("generated-confirm-key-1");
        assertThat(ai.expectedGeneratedDraftRevision).isZero();

        var rejectedGenerated = mvc.perform(post(
                        "/api/v1/systems/10/ai/sessions/7001/"
                                + "generated-draft-proposals/9502/reject")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, request)
                        .requestAttr(WebRequestAttributes.REQUEST_ID,
                                "req-ai-generated-reject")
                        .requestAttr(WebRequestAttributes.TRACE_ID,
                                "trace-ai-generated-reject")
                        .contentType("application/json")
                        .content("{\"expectedRevision\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(rejectedGenerated).at("/data/state").asText())
                .isEqualTo("REJECTED");
    }

    @Test
    void bothRouteGroupsRejectMissingOrMismatchedSystemContext() throws Exception {
        admin(new StubAdminFacade())
                .perform(get("/api/v1/systems/10/admin/ai/providers"))
                .andExpect(status().isUnauthorized());
        runtime(new StubAgentFacade())
                .perform(get("/api/v1/systems/10/ai/capability")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                new TestSession(11, 20, 30, Set.of())))
                .andExpect(status().isForbidden());
    }

    private static MockMvc admin(AiAdminFacade ai) {
        return mvc(new AiAdminController(ai));
    }

    private static MockMvc runtime(AiAgentFacade ai) {
        return mvc(new AiAgentController(ai));
    }

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JSON))
                .build();
    }

    private static TestSession session(Set<String> permissions) {
        return new TestSession(10, 20, 30, permissions);
    }

    private static JdbcAiRepository unusedRepository() {
        return new JdbcAiRepository(new JdbcTemplate(), JSON);
    }

    private static final class StubAdminFacade extends AiAdminFacade {
        private AiActor actor;
        private AiPolicyService.DraftCommand policy;
        private long expectedVersion;

        private StubAdminFacade() {
            this(unusedRepository());
        }

        private StubAdminFacade(JdbcAiRepository repository) {
            super(new AiProviderService(repository, new IdService(), CLOCK),
                    new AiPolicyService(
                            repository,
                            request -> new AiRecordPolicyCatalogFacade.Result(
                                    request.moduleCode(), "1", 12, Set.of("name")),
                            new IdService(), CLOCK, JSON),
                    repository);
        }

        @Override
        public ProviderView createProvider(
                AiActor actor, AiProviderService.Command command) {
            this.actor = actor;
            return new ProviderView(
                    "9001", command.code(), command.name(), command.baseUrl(),
                    command.model(), command.secretRef(), command.timeoutSeconds(),
                    command.enabled(), 0);
        }

        @Override
        public PolicyView savePolicy(
                AiActor actor,
                long expectedVersion,
                AiPolicyService.DraftCommand command) {
            this.actor = actor;
            this.expectedVersion = expectedVersion;
            this.policy = command;
            return new PolicyView(
                    1, null, Long.toString(command.providerId()),
                    command.moduleCodes().stream().sorted().toList(),
                    Map.of("orders", List.of("name")), command.maxRows(),
                    command.redactionMode().name(), command.promptVersion(),
                    command.enabled());
        }
    }

    private static final class StubAgentFacade extends AiAgentFacade {
        private AiActor actor;
        private SubmitMessage message;
        private String idempotencyKey;
        private long expectedConfirmationVersion;
        private String configurationIdempotencyKey;
        private long expectedConfigurationRevision;
        private String artifactIdempotencyKey;
        private long expectedArtifactRevision;
        private String workIdempotencyKey;
        private long expectedWorkRevision;
        private String generatedDraftIdempotencyKey;
        private long expectedGeneratedDraftRevision;

        private StubAgentFacade() {
            super(unusedRepository(),
                    (provider, request) -> {
                        throw new UnsupportedOperationException();
                    },
                    new AiRecordQueryPlanParser(),
                    request -> {
                        throw new UnsupportedOperationException();
                    },
                    new IdService(), CLOCK, JSON);
        }

        @Override
        public SessionPage sessions(AiActor actor, int page, int size) {
            this.actor = actor;
            return new SessionPage(List.of(new SessionView(
                    "7001", "[title:redacted]", "ACTIVE",
                    CLOCK.instant(), CLOCK.instant())), page, size, false);
        }

        @Override
        public TurnView submit(
                AiActor actor, String sessionId, SubmitMessage command) {
            this.actor = actor;
            this.message = command;
            return new TurnView(
                    "8001", "SUCCEEDED", "找到 1 条记录", null, false,
                    new ToolView("orders", 1, 1,
                            List.of(Map.of(
                                    "recordId", "6001", "name", "已脱敏展示"))),
                    actor.requestId(), actor.traceId());
        }

        @Override
        public CapabilityView capability(AiActor actor) {
            this.actor = actor;
            return new CapabilityView(true, null, "5001");
        }

        @Override
        public ConfirmationView confirmation(AiActor actor, String confirmationId) {
            this.actor = actor;
            return confirmation(confirmationId, "PENDING", null, null);
        }

        @Override
        public ConfirmationView confirm(
                AiActor actor,
                String confirmationId,
                long expectedVersion,
                String idempotencyKey) {
            this.actor = actor;
            this.expectedConfirmationVersion = expectedVersion;
            this.idempotencyKey = idempotencyKey;
            return confirmation(confirmationId, "SUCCEEDED",
                    new ConfirmationResult(
                            "6002", "ORD-2", 0, "DRAFT", "AI record",
                            "4001", Map.of("name", "AI record"), CLOCK.instant()),
                    null);
        }

        @Override
        public ConfirmationView reject(
                AiActor actor, String confirmationId, long expectedVersion) {
            this.actor = actor;
            this.expectedConfirmationVersion = expectedVersion;
            return confirmation(confirmationId, "REJECTED", null,
                    "AI_CONFIRMATION_REJECTED");
        }

        @Override
        public ConfigurationProposalView configurationProposal(
                AiActor actor, String sessionId, String proposalId) {
            this.actor = actor;
            return configurationProposal(proposalId, "PENDING", null, null);
        }

        @Override
        public ConfigurationProposalView confirmConfigurationField(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision,
                String idempotencyKey) {
            this.actor = actor;
            this.expectedConfigurationRevision = expectedRevision;
            this.configurationIdempotencyKey = idempotencyKey;
            return configurationProposal(proposalId, "SUCCEEDED",
                    new ConfigurationResult(
                            "1001", "2001", "orders", 8, "3001",
                            "ai_priority", "AI Priority", "INTEGER", false,
                            new FieldSettingsView(null, 38, 0, "0", "100"),
                            50, 0, "DRAFT"), null);
        }

        @Override
        public ConfigurationProposalView rejectConfigurationField(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision) {
            this.actor = actor;
            this.expectedConfigurationRevision = expectedRevision;
            return configurationProposal(
                    proposalId, "REJECTED", null,
                    "AI_CONFIG_FIELD_REJECTED");
        }

        @Override
        public ArtifactProposalView artifactProposal(
                AiActor actor, String sessionId, String proposalId) {
            this.actor = actor;
            return artifactProposal(proposalId, "PENDING", null);
        }

        @Override
        public ArtifactProposalView confirmArtifact(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision,
                String idempotencyKey) {
            this.actor = actor;
            this.expectedArtifactRevision = expectedRevision;
            this.artifactIdempotencyKey = idempotencyKey;
            return artifactProposal(proposalId, "SUCCEEDED", null);
        }

        @Override
        public ArtifactProposalView rejectArtifact(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision) {
            this.actor = actor;
            this.expectedArtifactRevision = expectedRevision;
            return artifactProposal(
                    proposalId, "REJECTED", "AI_CONFIG_ARTIFACT_REJECTED");
        }

        @Override
        public WorkProposalView workProposal(
                AiActor actor, String sessionId, String proposalId) {
            this.actor = actor;
            return workProposal(proposalId, "PENDING", null, null);
        }

        @Override
        public WorkProposalView confirmWorkProposal(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision,
                String idempotencyKey) {
            this.actor = actor;
            this.expectedWorkRevision = expectedRevision;
            this.workIdempotencyKey = idempotencyKey;
            return workProposal(proposalId, "SUCCEEDED",
                    new WorkResultView(
                            "WORK_TASK_DRAFT",
                            new WorkTaskResultView(
                                    "9401", 1, "AI Work Task", null,
                                    "OPEN", "30", null, null,
                                    CLOCK.instant(), CLOCK.instant()),
                            null), null);
        }

        @Override
        public WorkProposalView rejectWorkProposal(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision) {
            this.actor = actor;
            this.expectedWorkRevision = expectedRevision;
            return workProposal(
                    proposalId, "REJECTED", null, "AI_WORK_REJECTED");
        }

        @Override
        public GeneratedDraftProposalView generatedDraftProposal(
                AiActor actor, String sessionId, String proposalId) {
            this.actor = actor;
            return generatedDraftProposal(
                    proposalId, "PENDING", null, null);
        }

        @Override
        public GeneratedDraftProposalView confirmGeneratedDraftProposal(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision,
                String idempotencyKey) {
            this.actor = actor;
            this.expectedGeneratedDraftRevision = expectedRevision;
            this.generatedDraftIdempotencyKey = idempotencyKey;
            return generatedDraftProposal(
                    proposalId, "SUCCEEDED",
                    new GeneratedDraftResultView(
                            new FlowDefinitionResultView(
                                    "9601", "AI approval flow", List.of("30"),
                                    0, CLOCK.instant(), false),
                            null, null), null);
        }

        @Override
        public GeneratedDraftProposalView rejectGeneratedDraftProposal(
                AiActor actor,
                String sessionId,
                String proposalId,
                long expectedRevision) {
            this.actor = actor;
            this.expectedGeneratedDraftRevision = expectedRevision;
            return generatedDraftProposal(
                    proposalId, "REJECTED", null,
                    "AI_GENERATED_DRAFT_REJECTED");
        }

        private static ArtifactProposalView artifactProposal(
                String id, String state, String errorCode) {
            return new ArtifactProposalView(
                    id, "7001", "8004", state,
                    "PENDING".equals(state) ? 0 : 1,
                    "CONFIG_SELECTION_FIELD_DRAFT", "SELECTION_FIELD",
                    "orders", null, 0.99, null,
                    CLOCK.instant().plusSeconds(600), null, errorCode,
                    "req-ai-artifact", "trace-ai-artifact");
        }

        private static WorkProposalView workProposal(
                String id,
                String state,
                WorkResultView result,
                String errorCode) {
            return new WorkProposalView(
                    id, "7001", "8005", state,
                    "PENDING".equals(state) ? 0 : 1,
                    "WORK_TASK_DRAFT",
                    new WorkPreviewView(
                            "WORK_TASK_DRAFT",
                            new WorkTaskPreviewView(
                                    "AI Work Task", null, "30", null,
                                    null, null, null),
                            null),
                    0.98, null, CLOCK.instant().plusSeconds(600), result,
                    errorCode, "req-ai-work", "trace-ai-work");
        }

        private static GeneratedDraftProposalView generatedDraftProposal(
                String id,
                String state,
                GeneratedDraftResultView result,
                String errorCode) {
            return new GeneratedDraftProposalView(
                    id, "7001", "8006", state,
                    "PENDING".equals(state) ? 0 : 1,
                    "FLOW_DEFINITION_DRAFT",
                    new GeneratedDraftPreviewView(
                            new FlowDefinitionPreviewView(
                                    "AI approval flow", List.of("30")),
                            null, null),
                    0.98, null, CLOCK.instant().plusSeconds(600), result,
                    errorCode, "req-ai-generated", "trace-ai-generated");
        }

        private static ConfigurationProposalView configurationProposal(
                String id,
                String state,
                ConfigurationResult result,
                String errorCode) {
            return new ConfigurationProposalView(
                    id, "7001", "8003", state,
                    "PENDING".equals(state) ? 0 : 1, "orders",
                    new ConfigurationPreview(
                            "1001", "2001", 7, 8, "orders",
                            "ai_priority", "AI Priority", "INTEGER", false,
                            new FieldSettingsView(null, 38, 0, "0", "100")),
                    0.98, null, CLOCK.instant().plusSeconds(600), result,
                    errorCode, "req-ai-config", "trace-ai-config");
        }

        private static ConfirmationView confirmation(
                String id,
                String state,
                ConfirmationResult result,
                String errorCode) {
            return new ConfirmationView(
                    id, "7001", "8002", state, "RECORD_CREATE", "orders",
                    null, null, null, "AI record",
                    List.of(new FieldPreview(
                            "name", "Name", "TEXT", null, "AI record",
                            false, 0.99)),
                    List.of(), CLOCK.instant().plusSeconds(600),
                    "PENDING".equals(state) ? 0 : 1, result, errorCode);
        }
    }

    private record TestSession(
            long requestedSystemId,
            long requestedTenantId,
            long requestedMemberId,
            Set<String> requestedPermissions
    ) implements RequestSession {
        @Override public long sessionId() { return 1; }
        @Override public long accountId() { return 2; }
        @Override public ContextType contextType() { return ContextType.SYSTEM; }
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
