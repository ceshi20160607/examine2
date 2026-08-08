package com.unique.examine.web.filter;

import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.controller.SessionCookieSupport;
import com.unique.examine.plat.manage.service.SessionService;
import com.unique.examine.core.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationFilter extends OncePerRequestFilter {
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final java.util.Set<String> ANONYMOUS_AUTH_PATHS = java.util.Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/password-recovery/request",
            "/api/v1/auth/password-recovery/reset",
            "/api/v1/auth/sso/start",
            "/api/v1/auth/sso/callback",
            "/api/v1/auth/sso/directory",
            "/api/v1/auth/sso/mfa:verify",
            "/api/v1/auth/sso/mfa:enroll"
    );
    private final SessionService sessionService;
    private final HandlerExceptionResolver exceptionResolver;

    public AuthenticationFilter(
            SessionService sessionService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver
    ) {
        this.sessionService = sessionService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return bypassAuthenticationResolution(request.getRequestURI());
    }

    static boolean bypassAuthenticationResolution(String path) {
        return REFRESH_PATH.equals(path)
                || ANONYMOUS_AUTH_PATHS.contains(path)
                || path.startsWith("/openapi/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var token = SessionCookieSupport.value(request, SessionCookieSupport.ACCESS_COOKIE);
        try {
            sessionService.resolve(token)
                    .ifPresent(session -> request.setAttribute(AuthenticatedSession.REQUEST_ATTRIBUTE, session));
        } catch (BusinessException exception) {
            exceptionResolver.resolveException(request, response, null, exception);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
