package com.unique.examine.web.filter;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.controller.SessionCookieSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CsrfFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> PUBLIC_MUTATIONS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register"
    );

    private final HandlerExceptionResolver exceptionResolver;

    public CsrfFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || PUBLIC_MUTATIONS.contains(request.getRequestURI())
                || request.getRequestURI().startsWith("/openapi/v1/")
                || readOnlyRecordQuery(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        var hasSession = request.getAttribute(AuthenticatedSession.REQUEST_ATTRIBUTE) != null;
        var refresh = "/api/v1/auth/refresh".equals(request.getRequestURI());
        if (!hasSession && !refresh) {
            filterChain.doFilter(request, response);
            return;
        }
        var cookie = SessionCookieSupport.value(request, SessionCookieSupport.CSRF_COOKIE);
        var header = request.getHeader("X-CSRF-Token");
        if (!same(cookie, header)) {
            exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new BusinessException(
                            "CSRF_INVALID",
                            "请求安全校验失败，请刷新后重试",
                            HttpStatus.FORBIDDEN
                    )
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean readOnlyRecordQuery(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && request.getRequestURI().matches(
                "^/api/v1/systems/[1-9][0-9]{0,18}/runtime/modules/[A-Za-z][A-Za-z0-9_]{0,63}"
                        + "/records:(query|my-drafts-query)$");
    }

    private static boolean same(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
