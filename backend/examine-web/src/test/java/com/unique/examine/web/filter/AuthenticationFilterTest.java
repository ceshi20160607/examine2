package com.unique.examine.web.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {
    @Test
    void staleCookiesCannotBlockFreshAuthenticationEntries() {
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/auth/login")).isTrue();
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/auth/register")).isTrue();
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/auth/password-recovery/reset")).isTrue();
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/auth/sso/callback")).isTrue();
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/auth/sso/mfa:verify")).isTrue();
    }

    @Test
    void authenticatedAndManagementRoutesStillResolveAndValidateTheSession() {
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/auth/me")).isFalse();
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/auth/sso/mfa:enroll-authenticated")).isFalse();
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/api/v1/platform/admin/systems")).isFalse();
        assertThat(AuthenticationFilter.bypassAuthenticationResolution("/openapi/v1/records")).isTrue();
    }
}
