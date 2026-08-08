package com.unique.examine.collab.recordcomment;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

class RecordCommentControllerTest {
    private static final String BASE =
            "/api/v1/systems/1/runtime/modules/work_order/records/3/comments";
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access",
            "module.work_order.view");
    private static final Set<String> MANAGE = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.update");

    private InMemoryRecordCommentRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecordCommentRepository();
        mvc = mvc(repository, request -> {
            if (!request.effectivePermissions().contains("system.runtime.access")
                    || !request.effectivePermissions().contains("module.work_order.view")) {
                throw new BusinessException(
                        "PERMISSION_DENIED",
                        "runtime and module VIEW permissions are required",
                        HttpStatus.FORBIDDEN);
            }
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess("3", 7, true);
        });
    }

    @Test
    void createsListsUpdatesAndTombstonesWithStringIdsAndCapabilityFlags() throws Exception {
        mvc.perform(authenticated(
                        post(BASE)
                                .header("Idempotency-Key", "request-1")
                                .contentType("application/json")
                                .content("""
                                        {"body":"  first comment  "}
                                        """),
                        session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.commentId").value("1"))
                .andExpect(jsonPath("$.data.recordId").value("3"))
                .andExpect(jsonPath("$.data.parentCommentId").doesNotExist())
                .andExpect(jsonPath("$.data.authorMemberId").value("10"))
                .andExpect(jsonPath("$.data.body").value("first comment"))
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.canEdit").value(true))
                .andExpect(jsonPath("$.data.canDelete").value(true));

        mvc.perform(authenticated(get(BASE), session(1L, 2L, 12L, VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].commentId").value("1"))
                .andExpect(jsonPath("$.data.items[0].canEdit").value(false))
                .andExpect(jsonPath("$.data.items[0].canDelete").value(false));

        mvc.perform(authenticated(
                        put(BASE + "/1")
                                .contentType("application/json")
                                .content("""
                                        {"body":"manager edit","version":1}
                                        """),
                        session(1L, 2L, 11L, MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("manager edit"))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.canEdit").value(true));

        mvc.perform(authenticated(
                        delete(BASE + "/1")
                                .contentType("application/json")
                                .content("""
                                        {"version":2}
                                        """),
                        session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value("1"))
                .andExpect(jsonPath("$.data.body").doesNotExist())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.canEdit").value(false))
                .andExpect(jsonPath("$.data.canDelete").value(false));

        mvc.perform(authenticated(get(BASE), session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].deleted").value(true));
    }

    @Test
    void repeatedPostReturnsTheSameCommentWithoutCreatingAnotherRow() throws Exception {
        var request = post(BASE)
                .header("Idempotency-Key", "same-request")
                .contentType("application/json")
                .content("""
                        {"body":"same body"}
                        """);
        mvc.perform(authenticated(request, session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentId").value("1"));

        mvc.perform(authenticated(
                        post(BASE)
                                .header("Idempotency-Key", "same-request")
                                .contentType("application/json")
                                .content("""
                                        {"body":"same body"}
                                        """),
                        session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value("1"));

        assertThat(repository.findPage(
                new RecordCommentKey("1", "2", "3"), 1, 20).total()).isEqualTo(1);
    }

    @Test
    void replyCarriesAStringParentIdAndNestedRepliesAreRejected() throws Exception {
        create("root", null, "root", 10L);

        mvc.perform(authenticated(
                        post(BASE)
                                .header("Idempotency-Key", "reply")
                                .contentType("application/json")
                                .content("""
                                        {"body":"reply","parentCommentId":"1"}
                                        """),
                        session(1L, 2L, 11L, VIEW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentId").value("2"))
                .andExpect(jsonPath("$.data.parentCommentId").value("1"));

        mvc.perform(authenticated(
                        post(BASE)
                                .header("Idempotency-Key", "nested")
                                .contentType("application/json")
                                .content("""
                                        {"body":"nested","parentCommentId":"2"}
                                        """),
                        session(1L, 2L, 12L, VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_COMMENT_REPLY_DEPTH_EXCEEDED"));
    }

    @Test
    void anotherAuthorWithoutModuleUpdateCannotEditOrDelete() throws Exception {
        create("owned", null, "root", 10L);

        mvc.perform(authenticated(
                        put(BASE + "/1")
                                .contentType("application/json")
                                .content("""
                                        {"body":"denied","version":1}
                                        """),
                        session(1L, 2L, 11L, VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECORD_COMMENT_PERMISSION_DENIED"));

        mvc.perform(authenticated(
                        delete(BASE + "/1")
                                .contentType("application/json")
                                .content("""
                                        {"version":1}
                                        """),
                        session(1L, 2L, 11L, VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECORD_COMMENT_PERMISSION_DENIED"));
    }

    @Test
    void requiresSystemTenantMemberAndCanonicalViewScopeBeforeRepositoryAccess() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mvc.perform(authenticated(
                        get(BASE),
                        new TestSession(ContextType.PLATFORM, 1L, 2L, 10L, VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_SYSTEM_MISMATCH"));

        mvc.perform(authenticated(get(BASE), session(9L, 2L, 10L, VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_SYSTEM_MISMATCH"));

        mvc.perform(authenticated(get(BASE), session(1L, null, 10L, VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_TENANT_REQUIRED"));

        mvc.perform(authenticated(get(BASE), session(1L, 2L, null, VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTEXT_MEMBER_REQUIRED"));

        mvc.perform(authenticated(
                        get(BASE),
                        session(1L, 2L, 10L, Set.of("system.runtime.access"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assertThat(repository.findPage(
                new RecordCommentKey("1", "2", "3"), 1, 20).total()).isZero();
    }

    @Test
    void commentsDisabledAndOutOfScopeRecordFailuresDoNotTouchPersistence() throws Exception {
        var disabled = mvc(
                repository,
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("3", 1, false));
        disabled.perform(authenticated(get(BASE), session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECORD_COMMENTS_DISABLED"));

        var notFound = mvc(repository, request -> {
            throw new BusinessException(
                    "RECORD_NOT_FOUND",
                    "missing or outside VIEW scope",
                    HttpStatus.NOT_FOUND);
        });
        notFound.perform(authenticated(get(BASE), session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));

        assertThat(repository.findPage(
                new RecordCommentKey("1", "2", "3"), 1, 20).total()).isZero();
    }

    @Test
    void validatesRequiredIdempotencyBodyVersionAndPageBounds() throws Exception {
        mvc.perform(authenticated(
                        post(BASE)
                                .contentType("application/json")
                                .content("""
                                        {"body":"missing key"}
                                        """),
                        session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("RECORD_COMMENT_IDEMPOTENCY_KEY_REQUIRED"));

        mvc.perform(authenticated(
                        post(BASE)
                                .header("Idempotency-Key", "blank-body")
                                .contentType("application/json")
                                .content("""
                                        {"body":"  "}
                                        """),
                        session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_COMMENT_BODY_INVALID"));

        create("owned", null, "root", 10L);
        mvc.perform(authenticated(
                        put(BASE + "/1")
                                .contentType("application/json")
                                .content("""
                                        {"body":"no version"}
                                        """),
                        session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_COMMENT_VERSION_INVALID"));

        mvc.perform(authenticated(
                        get(BASE + "?page=1&size=101"),
                        session(1L, 2L, 10L, VIEW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORD_COMMENT_SIZE_INVALID"));
    }

    private void create(String body, String parentId, String idempotencyKey, long memberId)
            throws Exception {
        var parentProperty = parentId == null
                ? ""
                : ",\"parentCommentId\":\"" + parentId + "\"";
        mvc.perform(authenticated(
                        post(BASE)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType("application/json")
                                .content("{\"body\":\"" + body + "\"" + parentProperty + "}"),
                        session(1L, 2L, memberId, VIEW)))
                .andExpect(status().isCreated());
    }

    private static MockMvc mvc(
            RecordCommentRepository repository,
            RuntimeRecordAccessFacade access
    ) {
        var service = new RecordCommentService(repository, access);
        return MockMvcBuilders
                .standaloneSetup(new RecordCommentController(service))
                .setControllerAdvice(new TestBusinessExceptionHandler())
                .build();
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
            Long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) {
        return new TestSession(ContextType.SYSTEM, systemId, tenantId, memberId, permissions);
    }

    private record TestSession(
            ContextType contextType,
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
