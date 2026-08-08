package com.unique.examine.work.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.adapter.memory.InMemoryWorkDailyReportRepository;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.service.WorkDailyReportService;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkDailyReportControllerTest {
    private ObjectMapper json;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        WorkMemberDirectory members = (systemId, tenantId, memberId) ->
                systemId == 10 && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId);
        var service = new WorkDailyReportService(
                new InMemoryWorkDailyReportRepository(members), members,
                Clock.fixed(
                        Instant.parse("2026-07-31T08:00:00Z"),
                        ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(
                        new WorkDailyReportController(service))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void exactHttpRoundTripSupportsSubmitReplayManagerReopenAndSummary()
            throws Exception {
        var author = session(
                10, 20, 100,
                WorkDailyReportService.ACCESS,
                WorkDailyReportService.CREATE);
        var createdBody = mvc.perform(
                        post("/api/v1/systems/10/work/reports")
                                .requestAttr(
                                        RequestSession.REQUEST_ATTRIBUTE,
                                        author)
                                .contentType("application/json")
                                .content("""
                                        {
                                          "workDate":"2026-07-31",
                                          "completedWork":"Completed",
                                          "plannedWork":"Planned",
                                          "blockers":null
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var created = json.readTree(createdBody).path("data");
        var reportId = created.path("id").asLong();
        assertThat(created.path("systemId").asText()).isEqualTo("10");
        assertThat(created.path("tenantId").asText()).isEqualTo("20");
        assertThat(created.path("authorMemberId").asText()).isEqualTo("100");
        assertThat(created.path("workDate").asText())
                .isEqualTo("2026-07-31");
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("submittedAt").isNull()).isTrue();

        var updatedBody = mvc.perform(put(
                        "/api/v1/systems/10/work/reports/{id}", reportId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, author)
                        .contentType("application/json")
                        .content("""
                                {
                                  "completedWork":"Completed 2",
                                  "plannedWork":"Planned 2",
                                  "blockers":"Waiting",
                                  "version":1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var updated = json.readTree(updatedBody).path("data");
        assertThat(updated.path("completedWork").asText())
                .isEqualTo("Completed 2");
        assertThat(updated.path("blockers").asText()).isEqualTo("Waiting");
        assertThat(updated.path("version").asLong()).isEqualTo(2L);

        var submittedBody = mvc.perform(post(
                        "/api/v1/systems/10/work/reports/{id}:submit",
                        reportId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, author)
                        .contentType("application/json")
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var submitted = json.readTree(submittedBody).path("data");
        assertThat(submitted.path("status").asText())
                .isEqualTo("SUBMITTED");
        assertThat(submitted.path("submittedAt").isTextual()).isTrue();
        assertThat(submitted.path("version").asLong()).isEqualTo(3L);

        var replayBody = mvc.perform(post(
                        "/api/v1/systems/10/work/reports/{id}:submit",
                        reportId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, author)
                        .contentType("application/json")
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replayBody).path("data").path("version")
                .asLong()).isEqualTo(3L);

        mvc.perform(put(
                        "/api/v1/systems/10/work/reports/{id}", reportId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, author)
                        .contentType("application/json")
                        .content("""
                                {
                                  "completedWork":"No",
                                  "plannedWork":"No",
                                  "version":3
                                }
                                """))
                .andExpect(status().isConflict());
        mvc.perform(get(
                        "/api/v1/systems/10/work/reports/{id}", reportId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101,
                                        WorkDailyReportService.ACCESS)))
                .andExpect(status().isNotFound());
        mvc.perform(get(
                        "/api/v1/systems/10/work/reports/{id}", reportId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 21, 100,
                                        WorkDailyReportService.ACCESS)))
                .andExpect(status().isNotFound());

        var manager = session(
                10, 20, 102,
                WorkDailyReportService.ACCESS,
                WorkDailyReportService.MANAGE);
        var pageBody = mvc.perform(get(
                        "/api/v1/systems/10/work/reports")
                        .param("scope", "ALL")
                        .param("memberId", "100")
                        .param("dateFrom", "2026-07-25")
                        .param("dateTo", "2026-07-31")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(pageBody).path("data").path("items"))
                .hasSize(1);

        var reopenedBody = mvc.perform(post(
                        "/api/v1/systems/10/work/reports/{id}:reopen",
                        reportId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, manager)
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(reopenedBody).path("data").path("status")
                .asText()).isEqualTo("DRAFT");

        var summaryBody = mvc.perform(get(
                        "/api/v1/systems/10/work/reports/summary")
                        .param("endDate", "2026-07-31")
                        .param("memberId", "100")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var summary = json.readTree(summaryBody).path("data");
        assertThat(summary.path("memberId").asText()).isEqualTo("100");
        assertThat(summary.path("dateFrom").asText())
                .isEqualTo("2026-07-25");
        assertThat(summary.path("dateTo").asText())
                .isEqualTo("2026-07-31");
        assertThat(summary.path("draftCount").asInt()).isEqualTo(1);
        assertThat(summary.path("missingCount").asInt()).isEqualTo(6);
        assertThat(summary.path("days")).hasSize(7);
    }

    @Test
    void reportAccessPermissionAndCurrentSystemContextFailClosed()
            throws Exception {
        mvc.perform(get("/api/v1/systems/10/work/reports")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 100)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/systems/11/work/reports")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 100,
                                        WorkDailyReportService.ACCESS)))
                .andExpect(status().isForbidden());
    }

    private static TestSession session(
            long systemId,
            long tenantId,
            long memberId,
            String... permissions
    ) {
        return new TestSession(
                systemId, tenantId, memberId, Set.of(permissions));
    }

    private record TestSession(
            long requestedSystemId,
            long requestedTenantId,
            long requestedMemberId,
            Set<String> requestedPermissions
    ) implements RequestSession {
        @Override
        public long sessionId() {
            return 1;
        }

        @Override
        public long accountId() {
            return requestedMemberId;
        }

        @Override
        public ContextType contextType() {
            return ContextType.SYSTEM;
        }

        @Override
        public Long systemId() {
            return requestedSystemId;
        }

        @Override
        public Long tenantId() {
            return requestedTenantId;
        }

        @Override
        public Long memberId() {
            return requestedMemberId;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }

        @Override
        public Set<String> permissions() {
            return requestedPermissions;
        }
    }

    @RestControllerAdvice
    private static final class TestExceptionAdvice {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<Map<String, Object>> business(BusinessException error) {
            return ResponseEntity.status(error.status()).body(Map.of(
                    "code", error.code(),
                    "message", error.getMessage()));
        }
    }
}
