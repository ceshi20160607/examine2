package com.unique.examine.event.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.service.DeliveryPreferenceService;
import com.unique.examine.event.service.MessageTemplateService;
import com.unique.examine.event.support.TestDeliveryPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeliveryPreferenceControllerTest {
    private ObjectMapper json;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper().registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var service = new DeliveryPreferenceService(new TestDeliveryPreferenceRepository(),
                new CatalogTemplateService(),
                Clock.fixed(Instant.parse("2026-08-05T08:30:00Z"), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new DeliveryPreferenceController(service))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void currentMemberListsAnExactSafeArrayIncludingNullUpdatedAt() throws Exception {
        var body = mvc.perform(get("/api/v1/systems/10/event/delivery-preferences")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, EventRequestSession.ACCESS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = json.readTree(body).path("data");

        assertThat(data.isArray()).isTrue();
        assertThat(data).hasSize(2);
        assertThat(data.get(0).propertyStream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("templateCode", "eventType", "name", "channel",
                        "enabled", "version", "updatedAt");
        assertThat(data.get(0).path("templateCode").asText()).isEqualTo("MODULE_EXPORT_FAILED");
        assertThat(data.get(0).path("channel").asText()).isEqualTo("INBOX");
        assertThat(data.get(0).path("enabled").asBoolean()).isTrue();
        assertThat(data.get(0).path("version").asLong()).isZero();
        assertThat(data.get(0).has("updatedAt")).isTrue();
        assertThat(data.get(0).path("updatedAt").isNull()).isTrue();
        assertThat(body).doesNotContain("systemId", "tenantId", "memberId",
                "titleTemplate", "bodyTemplate", "allowedVariables", "deliveryLog");
    }

    @Test
    void exactPutDisablesAndReturnsTheSingleOwnerView() throws Exception {
        var body = mvc.perform(put("/api/v1/systems/10/event/delivery-preferences/"
                                + "MODULE_EXPORT_SUCCEEDED")
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"expectedVersion\":0}")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, EventRequestSession.ACCESS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = json.readTree(body).path("data");

        assertThat(data.path("templateCode").asText()).isEqualTo("MODULE_EXPORT_SUCCEEDED");
        assertThat(data.path("channel").asText()).isEqualTo("INBOX");
        assertThat(data.path("enabled").asBoolean()).isFalse();
        assertThat(data.path("version").asLong()).isEqualTo(1);
        assertThat(data.path("updatedAt").asText()).isEqualTo("2026-08-05T08:30:00Z");
    }

    @Test
    void channelQualifiedPutKeepsTemplateAndChannelInTheResourceIdentity() throws Exception {
        var body = mvc.perform(put("/api/v1/systems/10/event/delivery-preferences/"
                                + "MODULE_EXPORT_SUCCEEDED/INBOX")
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"expectedVersion\":0}")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, EventRequestSession.ACCESS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).path("data").path("channel").asText()).isEqualTo("INBOX");
    }

    @Test
    void putRejectsExtraChannelLowercaseAndStaleVersion() throws Exception {
        var owner = session(10, 20, 101, EventRequestSession.ACCESS);
        mvc.perform(put("/api/v1/systems/10/event/delivery-preferences/MODULE_EXPORT_SUCCEEDED")
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"expectedVersion\":0,\"channel\":\"INBOX\"}")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, owner))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/systems/10/event/delivery-preferences/module_export_succeeded")
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"expectedVersion\":0}")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, owner))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/systems/10/event/delivery-preferences/MODULE_EXPORT_SUCCEEDED")
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"expectedVersion\":0}")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, owner))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/systems/10/event/delivery-preferences/MODULE_EXPORT_SUCCEEDED")
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"expectedVersion\":0}")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, owner))
                .andExpect(status().isConflict());
    }

    @Test
    void authenticationSystemContextAndPermissionRetainExistingFailures() throws Exception {
        mvc.perform(get("/api/v1/systems/10/event/delivery-preferences"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/systems/11/event/delivery-preferences")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101, EventRequestSession.ACCESS)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/systems/10/event/delivery-preferences")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 20, 101)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/systems/10/event/delivery-preferences/MODULE_EXPORT_SUCCEEDED")
                        .contentType("application/json")
                        .content("{\"channel\":\"EMAIL\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static TestSession session(long systemId, long tenantId, long memberId,
                                       String... permissions) {
        return new TestSession(systemId, tenantId, memberId, Set.of(permissions));
    }

    private record TestSession(long requestedSystemId, long requestedTenantId,
                               long requestedMemberId, Set<String> requestedPermissions)
            implements RequestSession {
        @Override public long sessionId() { return 1; }
        @Override public long accountId() { return requestedMemberId; }
        @Override public ContextType contextType() { return ContextType.SYSTEM; }
        @Override public Long systemId() { return requestedSystemId; }
        @Override public Long tenantId() { return requestedTenantId; }
        @Override public Long memberId() { return requestedMemberId; }
        @Override public long permissionVersion() { return 1; }
        @Override public Set<String> permissions() { return requestedPermissions; }
    }

    private static final class CatalogTemplateService extends MessageTemplateService {
        private CatalogTemplateService() { super(null, null, null, null, null); }

        @Override
        public List<PreferenceTemplate> preferenceCatalog(long systemId, long actorId) {
            return List.of(
                    new PreferenceTemplate("MODULE_EXPORT_FAILED", "MODULE_EXPORT_FAILED", "Export failed"),
                    new PreferenceTemplate("MODULE_EXPORT_SUCCEEDED", "MODULE_EXPORT_SUCCEEDED", "Export done"));
        }

        @Override
        public PreferenceTemplate requirePreferenceTemplate(long systemId, long actorId, String code) {
            return preferenceCatalog(systemId, actorId).stream()
                    .filter(value -> value.templateCode().equals(code)).findFirst()
                    .orElseThrow(() -> new BusinessException("MESSAGE_TEMPLATE_NOT_FOUND",
                            "Message template was not found", org.springframework.http.HttpStatus.NOT_FOUND));
        }
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
