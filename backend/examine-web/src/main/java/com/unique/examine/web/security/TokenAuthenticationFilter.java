package com.unique.examine.web.security;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.security.SessionPayload;
import com.unique.examine.core.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;

    public TokenAuthenticationFilter(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        try {
            if (isPublic(uri)) {
                filterChain.doFilter(request, response);
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
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
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
        if (uri.startsWith("/api/ping")) {
            return true;
        }
        if (uri.startsWith("/api/v1/platform/auth/register") || uri.startsWith("/api/v1/platform/auth/login")) {
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
