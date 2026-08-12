package com.unique.examine.web.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityBoundaryFilterTest {
    private final SecurityBoundaryFilter filter = new SecurityBoundaryFilter();

    @Test
    void appliesGlobalBrowserSecurityHeaders() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/ping");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo(SecurityBoundaryFilter.CSP);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsMultipartGetBeforeDownstreamParameterParsing() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/ping");
        request.setContentType("multipart/form-data; boundary=test");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsBoundedMultipartPostToExistingUploadControls() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/files");
        request.setContentType("multipart/form-data; boundary=test");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
