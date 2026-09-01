package com.unique.unexamine.authentication.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationFilter extends OncePerRequestFilter {
    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(AuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/refresh")
                || path.startsWith("/api/auth/sso/")
                || path.startsWith("/api/application-access/")
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            writeFailure(response, request, new DomainException("AUTHENTICATION_REQUIRED", "请先登录", org.springframework.http.HttpStatus.UNAUTHORIZED));
            return;
        }
        try {
            AuthenticatedContext context = authenticationService.authenticateAccessToken(
                    authorization.substring(7), TraceIdFilter.current(request));
            AuthenticationContextHolder.set(context);
            putContext("systemId", context.systemId());
            putContext("tenantId", context.tenantId());
            putContext("userId", context.accountId());
            filterChain.doFilter(request, response);
        } catch (DomainException exception) {
            writeFailure(response, request, exception);
        } finally {
            MDC.remove("systemId");
            MDC.remove("tenantId");
            MDC.remove("userId");
            AuthenticationContextHolder.clear();
        }
    }

    private void putContext(String key, Object value) {
        if (value != null) MDC.put(key, String.valueOf(value));
    }

    private void writeFailure(HttpServletResponse response, HttpServletRequest request, DomainException exception) throws IOException {
        response.setStatus(exception.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiResult<>(exception.code(), exception.getMessage(),
                Map.of("traceId", TraceIdFilter.current(request)), TraceIdFilter.current(request)));
    }
}
