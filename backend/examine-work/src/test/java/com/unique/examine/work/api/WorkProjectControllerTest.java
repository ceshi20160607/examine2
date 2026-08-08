package com.unique.examine.work.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.adapter.memory.InMemoryWorkProjectRepository;
import com.unique.examine.work.service.WorkProjectService;
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

class WorkProjectControllerTest {
    private ObjectMapper json;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new WorkProjectService(
                new InMemoryWorkProjectRepository(),
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(
                        Instant.parse("2026-07-31T08:00:00Z"),
                        ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(
                        new WorkProjectController(service))
                .setControllerAdvice(new TestExceptionAdvice())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void projectAndMemberHttpRoundTripUsesCurrentContext() throws Exception {
        var owner = session(10, 20, 100, WorkRequestSession.ACCESS);
        var createdBody = mvc.perform(
                        post("/api/v1/systems/10/work/projects")
                                .requestAttr(
                                        RequestSession.REQUEST_ATTRIBUTE,
                                        owner)
                                .contentType("application/json")
                                .content("""
                                        {"title":"Release","description":"Ship safely"}
                                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var created = json.readTree(createdBody).path("data");
        var projectId = created.path("id").asLong();
        assertThat(created.path("systemId").asText()).isEqualTo("10");
        assertThat(created.path("tenantId").asText()).isEqualTo("20");
        assertThat(created.path("creatorMemberId").asText())
                .isEqualTo("100");
        assertThat(created.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.path("members").isMissingNode()).isTrue();

        mvc.perform(get("/api/v1/systems/10/work/projects/{id}", projectId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101,
                                        WorkRequestSession.ACCESS)))
                .andExpect(status().isNotFound());

        var addedBody = mvc.perform(post(
                        "/api/v1/systems/10/work/projects/{id}/members",
                        projectId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, owner)
                        .contentType("application/json")
                        .content("""
                                {"memberId":101,"role":"OWNER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var added = json.readTree(addedBody).path("data");
        assertThat(added.path("projectId").asText())
                .isEqualTo(Long.toString(projectId));
        assertThat(added.path("memberId").asText()).isEqualTo("101");
        assertThat(added.path("role").asText()).isEqualTo("OWNER");

        var membersBody = mvc.perform(get(
                        "/api/v1/systems/10/work/projects/{id}/members",
                        projectId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101,
                                        WorkRequestSession.ACCESS)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(membersBody).path("data")).hasSize(2);

        var updatedBody = mvc.perform(put(
                        "/api/v1/systems/10/work/projects/{id}", projectId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, owner)
                        .contentType("application/json")
                        .content("""
                                {"title":"Release 2","description":null,"version":1}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var updated = json.readTree(updatedBody).path("data");
        assertThat(updated.path("title").asText()).isEqualTo("Release 2");
        assertThat(updated.path("description").isNull()).isTrue();
        assertThat(updated.path("version").asLong()).isEqualTo(2L);

        var archivedBody = mvc.perform(post(
                        "/api/v1/systems/10/work/projects/{id}:archive",
                        projectId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101,
                                        WorkRequestSession.ACCESS))
                        .contentType("application/json")
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(archivedBody).path("data")
                .path("status").asText()).isEqualTo("ARCHIVED");

        mvc.perform(post(
                        "/api/v1/systems/10/work/projects/{id}:reopen",
                        projectId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 101,
                                        WorkRequestSession.ACCESS))
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void staleAndCrossTenantRequestsFailClosed() throws Exception {
        var created = json.readTree(mvc.perform(
                        post("/api/v1/systems/10/work/projects")
                                .requestAttr(
                                        RequestSession.REQUEST_ATTRIBUTE,
                                        session(10, 20, 100,
                                                WorkRequestSession.ACCESS))
                                .contentType("application/json")
                                .content("{\"title\":\"Release\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data");
        var projectId = created.path("id").asLong();

        mvc.perform(post(
                        "/api/v1/systems/10/work/projects/{id}:archive",
                        projectId)
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 100,
                                        WorkRequestSession.ACCESS))
                        .contentType("application/json")
                        .content("{\"version\":99}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/systems/10/work/projects")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 21, 100,
                                        WorkRequestSession.ACCESS)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/systems/11/work/projects")
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE,
                                session(10, 20, 100,
                                        WorkRequestSession.ACCESS)))
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
