package com.unique.examine.web.vnext.auth;

import com.unique.examine.core.context.RequestSession;
import com.unique.examine.plat.vnext.manage.auth.VNextSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("vnext")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class VNextAuthenticationFilter extends OncePerRequestFilter {
    private final VNextSessionService sessionService;

    public VNextAuthenticationFilter(VNextSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/refresh".equals(path)
                || path.startsWith("/openapi/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var token = VNextSessionCookieSupport.value(request, VNextSessionCookieSupport.ACCESS_COOKIE);
        sessionService.resolve(token)
                .ifPresent(session -> request.setAttribute(RequestSession.REQUEST_ATTRIBUTE, session));
        filterChain.doFilter(request, response);
    }
}
