package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.manage.dto.ChangePasswordRequest;
import com.unique.examine.plat.manage.service.AccountPasswordService;
import com.unique.examine.plat.manage.service.ClientRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthControllerPasswordChangeTest {

    @Test
    void delegatesAuthenticatedChangeAndClearsAllAuthenticationCookies() {
        var passwordService = new RecordingPasswordService();
        var controller = controller(passwordService);
        var request = request();
        var response = new MockHttpServletResponse();
        var session = session();
        var body = new ChangePasswordRequest("current-password", "replacement-password");

        var result = controller.changePassword(body, session, request, response);

        assertThat(result.code()).isEqualTo("OK");
        assertThat(result.requestId()).isEqualTo("request-1");
        assertThat(result.traceId()).isEqualTo("trace-1");
        assertThat(passwordService.session).isSameAs(session);
        assertThat(passwordService.request).isSameAs(body);
        assertThat(passwordService.client.requestId()).isEqualTo("request-1");
        assertThat(passwordService.client.traceId()).isEqualTo("trace-1");
        assertThat(response.getHeaders("Set-Cookie"))
                .hasSize(3)
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("EXAMINE_ACCESS=", "Max-Age=0", "Path=/", "HttpOnly"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("EXAMINE_REFRESH=", "Max-Age=0", "Path=/api/v1/auth", "HttpOnly"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains("EXAMINE_CSRF=", "Max-Age=0", "Path=/")
                        .doesNotContain("HttpOnly"));
    }

    @Test
    void rejectsMissingSessionBeforePasswordWorkOrCookieClearing() {
        var passwordService = new RecordingPasswordService();
        var controller = controller(passwordService);
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.changePassword(
                new ChangePasswordRequest("current-password", "replacement-password"),
                null,
                request(),
                response
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.code()).isEqualTo("AUTH_REQUIRED"));

        assertThat(passwordService.request).isNull();
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    private static AuthController controller(AccountPasswordService passwordService) {
        var properties = new SecurityProperties(null, null, false, 0, null);
        return new AuthController(
                null,
                null,
                passwordService,
                null,
                null,
                new SessionCookieSupport(properties),
                null
        );
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-1");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "owner-test");
        return request;
    }

    private static AuthenticatedSession session() {
        return new AuthenticatedSession(
                7L,
                17L,
                ContextType.PLATFORM,
                null,
                null,
                null,
                1L,
                Set.of("platform.runtime.access")
        );
    }

    private static final class RecordingPasswordService extends AccountPasswordService {
        private AuthenticatedSession session;
        private ChangePasswordRequest request;
        private ClientRequest client;

        private RecordingPasswordService() {
            super(null, null, null, null, null);
        }

        @Override
        public void changePassword(
                AuthenticatedSession current,
                ChangePasswordRequest body,
                ClientRequest clientRequest
        ) {
            session = current;
            request = body;
            client = clientRequest;
        }
    }
}
