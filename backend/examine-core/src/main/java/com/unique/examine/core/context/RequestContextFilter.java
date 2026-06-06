package com.unique.examine.core.context;

import java.io.IOException;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * requestId/traceId 上下文过滤器。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RequestContext context = buildContext(request);
        RequestContextHolder.set(context);
        putMdc(context);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, context.getRequestId());
        response.setHeader(RequestContext.TRACE_ID_HEADER, context.getTraceId());
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContextHolder.clear();
            MDC.clear();
        }
    }

    /**
     * 根据 HTTP 请求建立请求上下文。
     *
     * @param request HTTP 请求
     * @return 请求上下文
     */
    protected RequestContext buildContext(HttpServletRequest request) {
        String requestId = firstText(request.getHeader(RequestContext.REQUEST_ID_HEADER),
                TraceIdGenerator.newRequestId());
        String traceId = firstText(request.getHeader(RequestContext.TRACE_ID_HEADER), TraceIdGenerator.newTraceId());
        return RequestContext.builder()
                .requestId(requestId)
                .traceId(traceId)
                .tenantId(request.getHeader(RequestContext.TENANT_ID_HEADER))
                .idempotencyKey(request.getHeader(RequestContext.IDEMPOTENCY_KEY_HEADER))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .startedAt(Instant.now())
                .build();
    }

    private static String firstText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.strip() : defaultValue;
    }

    private static void putMdc(RequestContext context) {
        MDC.put(RequestContext.REQUEST_ID, context.getRequestId());
        MDC.put(RequestContext.TRACE_ID, context.getTraceId());
        putIfText("tenantId", context.getTenantId());
        putIfText("systemId", context.getSystemId());
        putIfText("accountId", context.getAccountId());
        putIfText("memberId", context.getMemberId());
        putIfText("clientId", context.getClientId());
    }

    private static void putIfText(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value);
        }
    }
}
