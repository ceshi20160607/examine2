package com.unique.examine.web.security;

import com.unique.examine.core.security.AuthContextHolder;
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
 * 统一解析请求上下文（systemId / tenantId / requestId）。
 * 鉴权不在这里做，交给 TokenAuthenticationFilter。
 */
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String ATTR_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            long systemId = parseLongOrDefault(request.getHeader("X-System-Id"), 0L);
            long tenantId = parseLongOrDefault(request.getHeader("X-Tenant-Id"), 0L);
            AuthContextHolder.setSystemId(systemId);
            AuthContextHolder.setTenantId(tenantId);

            String requestId = UUID.randomUUID().toString().replace("-", "");
            request.setAttribute(ATTR_REQUEST_ID, requestId);
            response.setHeader("X-Request-Id", requestId);

            MDC.put("requestId", requestId);
            MDC.put("systemId", String.valueOf(systemId));
            MDC.put("tenantId", String.valueOf(tenantId));
            MDC.put("httpMethod", request.getMethod());
            MDC.put("uri", request.getRequestURI());

            filterChain.doFilter(request, response);
        } finally {
            // 不使用 set(null)，用 remove 避免线程复用串数据
            AuthContextHolder.clearTenantId();
            AuthContextHolder.clearSystemId();
            MDC.clear();
            CallChainHolder.clear();
        }
    }

    private static long parseLongOrDefault(String s, long def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

