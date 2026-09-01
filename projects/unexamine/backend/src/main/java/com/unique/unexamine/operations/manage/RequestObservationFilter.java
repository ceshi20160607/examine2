package com.unique.unexamine.operations.manage;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestObservationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservationFilter.class);
    private final JdbcTemplate jdbc;

    public RequestObservationFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Instant started = Instant.now();
        try {
            filterChain.doFilter(request, response);
        } finally {
            persist(request, response, Duration.between(started, Instant.now()).toMillis());
        }
    }

    private void persist(HttpServletRequest request, HttpServletResponse response, long durationMillis) {
        AuthenticatedContext context = AuthenticationContextHolder.currentOrNull();
        String requestId = TraceIdFilter.current(request);
        int status = response.getStatus();
        String resultCode = status < 400 ? "SUCCESS" : "HTTP_" + status;
        try {
            jdbc.update("insert into ops_request_log(request_id,trace_id,system_id,tenant_id,account_id,client_source,"
                            + "http_method,request_path,status_code,result_code,duration_millis) values(?,?,?,?,?,?,?,?,?,?,?)",
                    requestId, requestId,
                    context == null ? null : context.systemId(), context == null ? null : context.tenantId(),
                    context == null ? null : context.accountId(), clientSource(request),
                    request.getMethod(), request.getRequestURI(), status, resultCode, Math.max(0, durationMillis));
            LOGGER.info("http_request method={} path={} status={} durationMillis={}",
                    request.getMethod(), request.getRequestURI(), status, durationMillis);
        } catch (RuntimeException exception) {
            LOGGER.warn("request_observation_persist_failed requestId={} reason={}", requestId,
                    exception.getClass().getSimpleName());
        }
    }

    private String clientSource(HttpServletRequest request) {
        String value = request.getHeader("X-Client-Source");
        return value != null && value.matches("[A-Za-z0-9_-]{2,32}") ? value.toUpperCase() : "API";
    }
}
