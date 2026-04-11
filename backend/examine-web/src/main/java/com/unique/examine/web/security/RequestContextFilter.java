package com.unique.examine.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import com.unique.examine.web.logging.CallChainHolder;

import java.io.IOException;
import java.util.UUID;

/**
 * 统一解析请求上下文（requestId）。
 * 鉴权不在这里做，交给 TokenAuthenticationFilter。
 */
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String ATTR_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestId = UUID.randomUUID().toString().replace("-", "");
            request.setAttribute(ATTR_REQUEST_ID, requestId);
            response.setHeader("X-Request-Id", requestId);

            MDC.put("requestId", requestId);
            MDC.put("httpMethod", request.getMethod());
            MDC.put("uri", request.getRequestURI());

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
            CallChainHolder.clear();
        }
    }
}

