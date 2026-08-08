package com.unique.examine.openapi.api;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.openapi.service.OpenApiApplicationUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiControllerTest {
    @Test
    void constructsWithoutDatabaseAndDelegatesCreateWithSystemSession() {
        var useCase = new StubUseCase();
        var controller = new OpenApiApplicationController(useCase);
        var servletRequest = request();
        var body = new OpenApiRequests.CreateApplication(
                "30",
                "40",
                "machine client",
                Set.of("openapi.ping"),
                List.of("127.0.0.1"),
                60,
                "env://OPENAPI_TEST_SECRET"
        );

        var response = controller.create(
                20,
                body,
                "operation-1",
                session(Set.of(OpenApiAdminSession.MANAGE_PERMISSION)),
                servletRequest
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().requestId()).isEqualTo("request-1");
        assertThat(useCase.receivedSession.systemId()).isEqualTo(20);
        assertThat(useCase.receivedSession.tenantId()).isEqualTo(30);
        assertThat(useCase.receivedKey).isEqualTo("operation-1");
    }

    @Test
    void rejectsMissingPermissionBeforeCallingUseCase() {
        var useCase = new StubUseCase();
        var controller = new OpenApiApplicationController(useCase);

        assertThatThrownBy(() -> controller.list(
                20, 1, 20, session(Set.of()), request()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PERMISSION_DENIED")
                );
        assertThat(useCase.receivedSession).isNull();
    }

    @Test
    void delegatesApplicationScopedCallLogFiltersWithoutAnyMutationInput() {
        var useCase = new StubUseCase();
        var controller = new OpenApiApplicationController(useCase);

        var response = controller.callLogs(
                20, 10, "SIGNATURE_REJECTED", "POST", 2, 25,
                session(Set.of(OpenApiAdminSession.MANAGE_PERMISSION)), request());

        assertThat(response.data().page()).isEqualTo(2);
        assertThat(response.data().size()).isEqualTo(25);
        assertThat(response.data().total()).isOne();
        assertThat(response.data().items()).singleElement()
                .extracting(OpenApiViews.CallLog::observedIp)
                .isEqualTo("2001:db8::1");
        assertThat(useCase.receivedSession.systemId()).isEqualTo(20);
        assertThat(useCase.receivedApplicationId).isEqualTo(10);
        assertThat(useCase.receivedResultCategory)
                .isEqualTo("SIGNATURE_REJECTED");
        assertThat(useCase.receivedRequestMethod).isEqualTo("POST");
        assertThat(useCase.receivedPage).isEqualTo(2);
        assertThat(useCase.receivedSize).isEqualTo(25);
    }

    @Test
    void pingControllerAlsoConstructsWithoutDatabase() {
        var controller = new OpenApiPingController();

        assertThatThrownBy(() -> controller.ping(null, request()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("OPENAPI_AUTH_REQUIRED")
                );
    }

    private static MockHttpServletRequest request() {
        var value = new MockHttpServletRequest();
        value.setAttribute(WebRequestAttributes.REQUEST_ID, "request-1");
        value.setAttribute(WebRequestAttributes.TRACE_ID, "trace-1");
        return value;
    }

    private static RequestSession session(Set<String> permissions) {
        return new TestSession(90, 700, 20L, 30L, 80L, permissions);
    }

    private record TestSession(
            long sessionId,
            long accountId,
            Long systemId,
            Long tenantId,
            Long memberId,
            Set<String> permissions
    ) implements RequestSession {
        @Override
        public ContextType contextType() {
            return ContextType.SYSTEM;
        }

        @Override
        public long permissionVersion() {
            return 1;
        }
    }

    private static final class StubUseCase implements OpenApiApplicationUseCase {
        private OpenApiAdminSession receivedSession;
        private String receivedKey;
        private long receivedApplicationId;
        private String receivedResultCategory;
        private String receivedRequestMethod;
        private int receivedPage;
        private int receivedSize;

        @Override
        public OpenApiViews.Application create(
                OpenApiAdminSession session,
                OpenApiRequests.CreateApplication request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            receivedSession = session;
            receivedKey = idempotencyKey;
            return view();
        }

        @Override
        public OpenApiViews.ApplicationPage list(
                OpenApiAdminSession session, int page, int size) {
            receivedSession = session;
            return new OpenApiViews.ApplicationPage(List.of(view()), page, size, 1);
        }

        @Override
        public OpenApiViews.Application detail(
                OpenApiAdminSession session, long applicationId) {
            return view();
        }

        @Override
        public OpenApiViews.CallLogPage callLogs(
                OpenApiAdminSession session,
                long applicationId,
                String resultCategory,
                String requestMethod,
                int page,
                int size) {
            receivedSession = session;
            receivedApplicationId = applicationId;
            receivedResultCategory = resultCategory;
            receivedRequestMethod = requestMethod;
            receivedPage = page;
            receivedSize = size;
            return new OpenApiViews.CallLogPage(
                    List.of(new OpenApiViews.CallLog(
                            "101", 1, "/openapi/v1/ping", "POST",
                            "SIGNATURE_REJECTED", 401, 4,
                            "request-1", "trace-1", "2001:db8::1",
                            "2026-08-05T04:00:00Z")),
                    page, size, 1);
        }

        @Override
        public OpenApiViews.Application updatePolicy(
                OpenApiAdminSession session,
                long applicationId,
                OpenApiRequests.UpdatePolicy request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return view();
        }

        @Override
        public OpenApiViews.Application rotateSecretRef(
                OpenApiAdminSession session,
                long applicationId,
                OpenApiRequests.RotateSecretRef request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return view();
        }

        @Override
        public OpenApiViews.Application enable(
                OpenApiAdminSession session,
                long applicationId,
                OpenApiRequests.ChangeStatus request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return view();
        }

        @Override
        public OpenApiViews.Application disable(
                OpenApiAdminSession session,
                long applicationId,
                OpenApiRequests.ChangeStatus request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return view();
        }

        private static OpenApiViews.Application view() {
            return new OpenApiViews.Application(
                    "10",
                    "20",
                    "30",
                    "40",
                    "application-key-1234567890",
                    "machine client",
                    "ACTIVE",
                    Set.of("openapi.ping"),
                    List.of("127.0.0.1"),
                    60,
                    1,
                    "env://OPENAPI_TEST_SECRET",
                    0,
                    "2026-07-28T08:00:00Z",
                    "2026-07-28T08:00:00Z"
            );
        }
    }
}
