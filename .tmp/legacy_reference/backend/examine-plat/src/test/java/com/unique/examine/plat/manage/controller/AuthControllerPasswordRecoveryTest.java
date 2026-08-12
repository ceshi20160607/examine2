package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.plat.manage.config.PasswordRecoveryProperties;
import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.manage.dto.PasswordRecoveryRequest;
import com.unique.examine.plat.manage.dto.PasswordRecoveryResetRequest;
import com.unique.examine.plat.manage.service.ClientRequest;
import com.unique.examine.plat.manage.service.PasswordRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerPasswordRecoveryTest {

    @Test
    void requestAlwaysReturnsTheSameEmptySuccessEnvelope() {
        var recovery = new RecordingRecoveryService();
        var controller = controller(recovery);

        var result = controller.requestPasswordRecovery(
                new PasswordRecoveryRequest("owner@example.com"), request());

        assertThat(result.code()).isEqualTo("OK");
        assertThat(result.data()).isNull();
        assertThat(result.requestId()).isEqualTo("request-1");
        assertThat(recovery.request.account()).isEqualTo("owner@example.com");
    }

    @Test
    void successfulResetClearsAllAuthenticationCookies() {
        var recovery = new RecordingRecoveryService();
        var controller = controller(recovery);
        var response = new MockHttpServletResponse();
        var body = new PasswordRecoveryResetRequest("A".repeat(43), "replacement-password");

        var result = controller.resetPassword(body, request(), response);

        assertThat(result.code()).isEqualTo("OK");
        assertThat(recovery.reset).isSameAs(body);
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

    private static AuthController controller(PasswordRecoveryService recovery) {
        return new AuthController(
                null, null, null, recovery, null,
                new SessionCookieSupport(new SecurityProperties(null, null, false, 0, null)),
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

    private static final class RecordingRecoveryService extends PasswordRecoveryService {
        private PasswordRecoveryRequest request;
        private PasswordRecoveryResetRequest reset;

        private RecordingRecoveryService() {
            super(null, null, null, null, null, null, null, null,
                    new PasswordRecoveryProperties(null, 0, 0), null, null, null);
        }

        @Override
        public void request(PasswordRecoveryRequest body, ClientRequest client) {
            request = body;
        }

        @Override
        public void reset(PasswordRecoveryResetRequest body, ClientRequest client) {
            reset = body;
        }
    }
}
