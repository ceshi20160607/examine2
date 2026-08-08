package com.unique.examine.event.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.adapter.jdbc.JdbcMessageTemplateRepository;
import com.unique.examine.event.service.MessageDeliveryLogService;
import com.unique.examine.event.service.MessageTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageDeliveryLogControllerTest {
    private MockMvc mvc;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        var controller = new MessageDeliveryLogController(
                new MessageDeliveryLogService(new FakeRepository()));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Advice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void administratorListsAndReadsTenantScopedSafeLogs() throws Exception {
        var list = mvc.perform(get("/api/v1/systems/10/event/delivery-logs")
                        .param("channel", "WEBHOOK").param("status", "FAILED")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 20,
                                MessageTemplateAdminSession.MANAGE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(list).path("data").path("items")).hasSize(1);
        assertThat(list).contains("hook.example/***")
                .doesNotContain("secret@example.com", "provider-response", "raw-dedupe");

        var detail = mvc.perform(get("/api/v1/systems/10/event/delivery-logs/501")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 20,
                                MessageTemplateAdminSession.MANAGE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("attempts", "trace-webhook-1", "Delivery failed safely")
                .doesNotContain("provider-response", "raw-dedupe", "?token=");
    }

    @Test
    void authenticationContextPermissionAndTenantScopeFailClosed() throws Exception {
        mvc.perform(get("/api/v1/systems/10/event/delivery-logs"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/systems/11/event/delivery-logs")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 20,
                                MessageTemplateAdminSession.MANAGE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/systems/10/event/delivery-logs")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 20)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/systems/10/event/delivery-logs/501")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session(10, 21,
                                MessageTemplateAdminSession.MANAGE)))
                .andExpect(status().isNotFound());
    }

    private static TestSession session(long systemId, long tenantId, String... permissions) {
        return new TestSession(systemId, tenantId, Set.of(permissions));
    }

    private record TestSession(long requestedSystemId, long requestedTenantId,
                               Set<String> requestedPermissions) implements RequestSession {
        @Override public long sessionId() { return 1; }
        @Override public long accountId() { return 101; }
        @Override public ContextType contextType() { return ContextType.SYSTEM; }
        @Override public Long systemId() { return requestedSystemId; }
        @Override public Long tenantId() { return requestedTenantId; }
        @Override public Long memberId() { return 101L; }
        @Override public long permissionVersion() { return 1; }
        @Override public Set<String> permissions() { return requestedPermissions; }
    }

    private static final class FakeRepository extends JdbcMessageTemplateRepository {
        private static final LocalDateTime NOW = LocalDateTime.parse("2026-08-06T08:00:00");
        private final DeliveryRecord delivery = new DeliveryRecord(501, 10, 20, 101,
                "MODULE_EXPORT_FAILED", 301L, "WEBHOOK", "raw-dedupe", "TASK", "42",
                "/systems/10/workbench?token=secret", "FAILED", 1, null,
                "WEBHOOK_4XX", "provider-response", "https://hook.example/***", 8L,
                "trace-webhook-1", NOW.minusSeconds(8), NOW);

        private FakeRepository() { super(null, null); }

        @Override
        public DeliveryPage deliveryLogs(long systemId, long tenantId, int page, int size,
                                         String channel, String status, String templateCode) {
            return new DeliveryPage(systemId == 10 && tenantId == 20 ? List.of(delivery) : List.of(),
                    systemId == 10 && tenantId == 20 ? 1 : 0, page, size);
        }

        @Override
        public Optional<DeliveryRecord> scopedDelivery(long systemId, long tenantId, long deliveryId) {
            return systemId == 10 && tenantId == 20 && deliveryId == 501
                    ? Optional.of(delivery) : Optional.empty();
        }

        @Override
        public List<AttemptRecord> deliveryAttempts(long systemId, long tenantId, long deliveryId) {
            return List.of(new AttemptRecord(601, 501, 10, 20, 1, "FAILED", 8L,
                    "trace-webhook-1", "WEBHOOK_4XX", "provider-response",
                    NOW.minusSeconds(8), NOW));
        }
    }

    @RestControllerAdvice
    private static final class Advice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(), "message", error.getMessage()));
        }
    }
}
