package com.unique.examine.collab.recordteam;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordTeamControllerTest {
    private static final String BASE =
            "/api/v1/systems/1/runtime/modules/work_order/records/3/team";
    private static final String INITIALIZE_BASE =
            "/api/v1/systems/1/runtime/modules/work_order/records/4/team:initialize";
    private static final Set<String> ALL_PERMISSIONS = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.update",
            "module.work_order.action.transfer");

    private MockMvc mvc;
    private InMemoryRecordTeamRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecordTeamRepository();
        repository.create(RecordTeam.initialize(
                new RecordTeamKey("1", "2", "3"),
                "10"));
        var service = new RecordTeamService(
                repository,
                new CapabilityRecordTeamAuthorizationPolicy());
        mvc = MockMvcBuilders
                .standaloneSetup(new RecordTeamController(service))
                .setControllerAdvice(new TestBusinessExceptionHandler())
                .build();
    }

    @Test
    void initializesFromTheAuthenticatedMemberAndReturnsAnExplicitIdempotentResult() throws Exception {
        mvc.perform(authenticated(
                        post(INITIALIZE_BASE),
                        session(1, 2L, 12L, ALL_PERMISSIONS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.team.systemId").value("1"))
                .andExpect(jsonPath("$.data.team.tenantId").value("2"))
                .andExpect(jsonPath("$.data.team.recordId").value("4"))
                .andExpect(jsonPath("$.data.team.ownerMemberId").value("12"))
                .andExpect(jsonPath("$.data.team.version").value(1))
                .andExpect(jsonPath("$.data.team.members.length()").value(1))
                .andExpect(jsonPath("$.data.team.members[0].role").value("OWNER"));

        mvc.perform(authenticated(
                        post(INITIALIZE_BASE),
                        session(1, 2L, 13L, ALL_PERMISSIONS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.team.ownerMemberId").value("12"))
                .andExpect(jsonPath("$.data.team.version").value(1));

        assertThat(repository.find(new RecordTeamKey("1", "2", "4")).orElseThrow()
                .owner().memberId()).isEqualTo("12");
    }

    @Test
    void initializationRequiresTheModuleUpdatePermissionBeforeCreatingAnything() throws Exception {
        var viewOnly = Set.of("system.runtime.access", "module.work_order.view");
        var path = "/api/v1/systems/1/runtime/modules/work_order/records/5/team:initialize";

        mvc.perform(authenticated(post(path), session(1, 2L, 12L, viewOnly)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECORD_TEAM_PERMISSION_DENIED"));

        assertThat(repository.find(new RecordTeamKey("1", "2", "5"))).isEmpty();
    }

    @Test
    void initializationRequiresAnAuthenticatedMatchingSystemTenantMemberContext() throws Exception {
        mvc.perform(post(INITIALIZE_BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mvc.perform(authenticated(
                        post(INITIALIZE_BASE),
                        session(9, 2L, 12L, ALL_PERMISSIONS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_SYSTEM_MISMATCH"));

        mvc.perform(authenticated(
                        post(INITIALIZE_BASE),
                        session(1, 2L, null, ALL_PERMISSIONS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_MEMBER_REQUIRED"));

        assertThat(repository.find(new RecordTeamKey("1", "2", "4"))).isEmpty();
    }

    @Test
    void getsAddsChangesTransfersAndRemovesUsingOnlySessionTenantAndMember() throws Exception {
        mvc.perform(authenticated(get(BASE), session(1, 2L, 10L, ALL_PERMISSIONS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.systemId").value("1"))
                .andExpect(jsonPath("$.data.tenantId").value("2"))
                .andExpect(jsonPath("$.data.recordId").value("3"))
                .andExpect(jsonPath("$.data.ownerMemberId").value("10"))
                .andExpect(jsonPath("$.data.members[0].role").value("OWNER"));

        mvc.perform(authenticated(
                        post(BASE + "/members")
                                .contentType("application/json")
                                .content("""
                                        {"memberId":"11","role":"VIEWER"}
                                        """),
                        session(1, 2L, 10L, ALL_PERMISSIONS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.members[1].memberId").value("11"));

        mvc.perform(authenticated(
                        put(BASE + "/members/11/role")
                                .contentType("application/json")
                                .content("""
                                        {"role":"COLLABORATOR"}
                                        """),
                        session(1, 2L, 10L, ALL_PERMISSIONS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.members[1].role").value("COLLABORATOR"));

        mvc.perform(authenticated(
                        post(BASE + "/transfer")
                                .contentType("application/json")
                                .content("""
                                        {"targetMemberId":"11"}
                                        """),
                        session(1, 2L, 10L, ALL_PERMISSIONS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.ownerMemberId").value("11"));

        mvc.perform(authenticated(
                        delete(BASE + "/members/10"),
                        session(1, 2L, 11L, ALL_PERMISSIONS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(5))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].memberId").value("11"));
    }

    @Test
    void failsClosedWhenTheModulePermissionCannotMapToACapability() throws Exception {
        var viewOnly = Set.of("system.runtime.access", "module.work_order.view");

        mvc.perform(authenticated(
                        post(BASE + "/members")
                                .contentType("application/json")
                                .content("""
                                        {"memberId":"11","role":"VIEWER"}
                                        """),
                        session(1, 2L, 10L, viewOnly)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECORD_TEAM_PERMISSION_DENIED"));
    }

    @Test
    void rejectsSystemMismatchAndMissingTenantBeforeReadingTheRepository() throws Exception {
        mvc.perform(authenticated(get(BASE), session(9, 2L, 10L, ALL_PERMISSIONS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_SYSTEM_MISMATCH"));

        mvc.perform(authenticated(get(BASE), session(1, null, 10L, ALL_PERMISSIONS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_TENANT_REQUIRED"));
    }

    @Test
    void returnsStableDomainCodesWithoutLeakingTheAggregate() throws Exception {
        var request = post(BASE + "/members")
                .contentType("application/json")
                .content("""
                        {"memberId":"10","role":"VIEWER"}
                        """);
        mvc.perform(authenticated(request, session(1, 2L, 10L, ALL_PERMISSIONS)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORD_TEAM_MEMBER_DUPLICATE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request,
            RequestSession session
    ) {
        return request
                .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                .requestAttr(WebRequestAttributes.REQUEST_ID, "request-1")
                .requestAttr(WebRequestAttributes.TRACE_ID, "trace-1");
    }

    private static RequestSession session(
            long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) {
        return new TestSession(systemId, tenantId, memberId, permissions);
    }

    private record TestSession(
            Long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) implements RequestSession {
        @Override
        public long sessionId() {
            return 100;
        }

        @Override
        public long accountId() {
            return 200;
        }

        @Override
        public ContextType contextType() {
            return ContextType.SYSTEM;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }
    }

    @RestControllerAdvice
    static final class TestBusinessExceptionHandler {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<ApiResponse<Void>> handle(BusinessException exception) {
            return ResponseEntity.status(exception.status()).body(ApiResponse.failure(
                    exception.code(),
                    exception.getMessage(),
                    "request-1",
                    "trace-1",
                    List.of()));
        }
    }
}
