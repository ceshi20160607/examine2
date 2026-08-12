package com.unique.examine.web.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.analytics.domain.OperationsDashboard;
import com.unique.examine.analytics.service.OperationsDashboardService;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsControllerTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void returnsNormalizedSnapshotAndStringMemberIds() throws Exception {
        var service = service();
        var json = new ObjectMapper();
        var mvc = mvc(service, json);

        var response = mvc.perform(get(
                        "/api/v1/systems/10/analytics/operations"
                                + "?from=2026-07-31&to=2026-08-02")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = json.readTree(response).path("data");

        assertThat(data.path("generatedAt").asText()).isEqualTo(CLOCK.instant().toString());
        assertThat(data.path("range").path("days").asInt()).isEqualTo(2);
        assertThat(data.path("work").path("openCount").asLong()).isEqualTo(2);
        assertThat(data.path("work").path("daily")).hasSize(2);
        assertThat(data.path("work").path("topAssignees").get(0)
                .path("memberId").asText()).isEqualTo("30");
        assertThat(data.path("flow").path("available").asBoolean()).isFalse();
        assertThat(data.path("flow").path("unavailableReason").asText())
                .isEqualTo("PERMISSION_DENIED");
        assertThat(data.path("todo").path("openCount").asLong()).isEqualTo(3);
    }

    @Test
    void rejectsMissingContextMismatchedSystemAndPartialRange() throws Exception {
        var json = new ObjectMapper();
        var mvc = mvc(service(), json);
        var root = "/api/v1/systems/10/analytics/operations";

        mvc.perform(get(root)).andExpect(status().isUnauthorized());
        mvc.perform(get(root).requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                        session(11, 20)))
                .andExpect(status().isForbidden());
        var invalid = mvc.perform(get(root + "?from=2026-08-01")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(invalid).path("code").asText())
                .isEqualTo("ANALYTICS_RANGE_INVALID");
    }

    private static OperationsDashboardService service() {
        return new OperationsDashboardService(
                (actor, range) -> new OperationsDashboard.WorkSection(
                        true, null,
                        2, route("tasks?status=OPEN"),
                        1, route("tasks?status=OPEN&time=OVERDUE"),
                        1, route("tasks?status=OPEN&time=RANGE"),
                        1, route("tasks?status=COMPLETED"),
                        range.dates().stream().map(date ->
                                new OperationsDashboard.WorkDaily(date, 1, 0)).toList(),
                        List.of(new OperationsDashboard.AssigneeOpen(
                                30, 2, route("tasks?assigneeMemberId=30"))),
                        route("tasks")),
                (actor, range) -> OperationsDashboard.FlowSection.unavailable(
                        "PERMISSION_DENIED", route("flows")),
                (actor, range) -> new OperationsDashboard.TodoSection(
                        true, null, 3, 2, 1, 1, 0, route("todos")),
                CLOCK);
    }

    private static MockMvc mvc(OperationsDashboardService service, ObjectMapper json) {
        return MockMvcBuilders.standaloneSetup(new AnalyticsController(service))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    private static TestSession session(long systemId, long tenantId) {
        return new TestSession(systemId, tenantId, 30,
                Set.of("system.runtime.access"));
    }

    private static String route(String suffix) {
        return "/systems/10/" + suffix;
    }

    private record TestSession(long requestedSystemId, long requestedTenantId,
                               long requestedMemberId, Set<String> requestedPermissions)
            implements RequestSession {
        @Override public long sessionId() { return 1; }
        @Override public long accountId() { return 2; }
        @Override public ContextType contextType() { return ContextType.SYSTEM; }
        @Override public Long systemId() { return requestedSystemId; }
        @Override public Long tenantId() { return requestedTenantId; }
        @Override public Long memberId() { return requestedMemberId; }
        @Override public long permissionVersion() { return 1; }
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
