package com.unique.examine.web.security;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.security.SessionPayload;
import com.unique.examine.core.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

import java.io.IOException;

public class TokenAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private SessionService sessionService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        try {
            if (isPublic(uri)) {
                filterChain.doFilter(request, response);
                return;
            }
            // 对外开放：已由 OpenApiAuthenticationFilter 写入 AuthContextHolder（Bearer 非必需）
            if (uri.startsWith("/v1/open/") && AuthContextHolder.getPlatId() != null) {
                filterChain.doFilter(request, response);
                return;
            }
            if (uri.startsWith("/v1/open/")) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"开放接口未通过 AK/SK 认证\"}");
                return;
            }
            String token = resolveToken(request);
            if (token == null) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"未登录或 token 缺失\"}");
                return;
            }
            var session = sessionService.getSession(token);
            if (session.isEmpty()) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"登录已过期\"}");
                return;
            }
            SessionPayload p = session.get();
            AuthContextHolder.setPlatId(p.platId());
            AuthContextHolder.setUsername(p.username());
            AuthContextHolder.setSystemId(p.systemId() == null ? 0L : p.systemId());
            AuthContextHolder.setTenantId(p.tenantId() == null ? 0L : p.tenantId());
            MDC.put("platId", String.valueOf(p.platId()));
            if (p.username() != null) {
                MDC.put("username", p.username());
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
            MDC.remove("platId");
            MDC.remove("username");
        }
    }

    private static String resolveToken(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring(7).trim();
        }
        return null;
    }

    private static boolean isPublic(String uri) {
        if (uri.startsWith("/v1/platform/auth/register") || uri.startsWith("/v1/platform/auth/login")) {
            return true;
        }
        if (uri.startsWith("/actuator") || uri.startsWith("/error")) {
            return true;
        }
        if (uri.startsWith("/v3/api-docs") || uri.startsWith("/doc.html") || uri.startsWith("/swagger-ui") || uri.startsWith("/webjars/")) {
            return true;
        }
        return false;
    }

}
