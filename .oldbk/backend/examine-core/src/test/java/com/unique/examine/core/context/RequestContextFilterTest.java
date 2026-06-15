package com.unique.examine.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextFilterTest {

    private final RequestContextFilter filter = new RequestContextFilter();

    @Test
    void shouldPropagateRequestIdAndTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/demo");
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "req_in");
        request.addHeader(RequestContext.TRACE_ID_HEADER, "trc_in");
        request.addHeader(RequestContext.TENANT_ID_HEADER, "300");
        request.addHeader(RequestContext.SYSTEM_ID_HEADER, "100");
        request.addHeader(RequestContext.MEMBER_ID_HEADER, "200");
        request.addHeader(RequestContext.CLIENT_ID_HEADER, "client-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            RequestContext context = RequestContextHolder.get();
            assertThat(context.getRequestId()).isEqualTo("req_in");
            assertThat(context.getTraceId()).isEqualTo("trc_in");
            assertThat(context.getTenantId()).isEqualTo("300");
            assertThat(context.getSystemId()).isEqualTo("100");
            assertThat(context.getMemberId()).isEqualTo("200");
            assertThat(context.getClientId()).isEqualTo("client-1");
            assertThat(context.getPath()).isEqualTo("/api/v1/demo");
            assertThat(MDC.get(RequestContext.REQUEST_ID)).isEqualTo("req_in");
            assertThat(MDC.get("systemId")).isEqualTo("100");
            assertThat(MDC.get("memberId")).isEqualTo("200");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).isEqualTo("req_in");
        assertThat(response.getHeader(RequestContext.TRACE_ID_HEADER)).isEqualTo("trc_in");
        assertThat(RequestContextHolder.get()).isNull();
        assertThat(MDC.get(RequestContext.REQUEST_ID)).isNull();
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/demo");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(RequestContextHolder.get().getRequestId()).startsWith("req_");
            assertThat(RequestContextHolder.get().getTraceId()).startsWith("trc_");
        });

        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).startsWith("req_");
        assertThat(response.getHeader(RequestContext.TRACE_ID_HEADER)).startsWith("trc_");
    }
}
