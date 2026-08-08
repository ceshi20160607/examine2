package com.unique.examine.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/** Global browser-edge policy and container-level multipart GET mitigation. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class SecurityBoundaryFilter extends OncePerRequestFilter {
    static final String CSP = "frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        applyHeaders(response);
        if (isMultipartReadRequest(request)) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static void applyHeaders(HttpServletResponse response) {
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
    }

    private static boolean isMultipartReadRequest(HttpServletRequest request) {
        var method = request.getMethod();
        if (!("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))) return false;
        var contentType = request.getContentType();
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
    }
}
