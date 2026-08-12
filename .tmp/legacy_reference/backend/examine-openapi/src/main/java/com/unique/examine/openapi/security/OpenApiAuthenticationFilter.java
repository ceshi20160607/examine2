package com.unique.examine.openapi.security;

import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.service.OpenApiCallLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class OpenApiAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(OpenApiAuthenticationFilter.class);

    private final OpenApiAuthenticator authenticator;
    private final OpenApiCallLogService callLogs;
    private final HandlerExceptionResolver exceptions;

    public OpenApiAuthenticationFilter(
            OpenApiAuthenticator authenticator,
            OpenApiCallLogService callLogs,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptions
    ) {
        this.authenticator = authenticator;
        this.callLogs = callLogs;
        this.exceptions = exceptions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/openapi/v1/")
                || request.getRequestURI().startsWith("/openapi/v1/platform/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var started = System.nanoTime();
        var attempt = new OpenApiAttempt();
        var rawAppKey = request.getHeader("X-App-Key");
        var observedIp = observedIp(request);
        var route = OpenApiRoutePolicy.resolve(request.getMethod(), request.getRequestURI());
        var routeTemplate = route.map(OpenApiRoutePolicy::routeTemplate)
                .orElse(request.getRequestURI());
        var category = OpenApiCallLog.ResultCategory.FAILED;
        var status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            if (route.isEmpty()) {
                throw new BusinessException(
                        "OPENAPI_ROUTE_NOT_FOUND",
                        "OpenAPI route was not found",
                        org.springframework.http.HttpStatus.NOT_FOUND
                );
            }
            var buffered = new BufferedOpenApiRequest(request);
            var headers = OpenApiHeaders.require(buffered);
            var authentication = authenticator.authenticate(
                    new OpenApiAuthenticator.Request(
                            buffered.getMethod(),
                            buffered.getRequestURI(),
                            buffered.getQueryString(),
                            buffered.bodyBytes(),
                            observedIp,
                            headers,
                            route.get()
                    ),
                    attempt
            );
            buffered.setAttribute(RequestSession.REQUEST_ATTRIBUTE, authentication.session());
            buffered.setAttribute(OpenApiAuthentication.REQUEST_ATTRIBUTE, authentication);
            filterChain.doFilter(buffered, response);
            status = response.getStatus();
            category = status < 400
                    ? OpenApiCallLog.ResultCategory.SUCCESS
                    : OpenApiCallLog.ResultCategory.FAILED;
        } catch (BusinessException exception) {
            status = exception.status().value();
            category = category(exception.code());
            retryHeaders(response, exception);
            exceptions.resolveException(request, response, null, exception);
        } finally {
            try {
                callLogs.record(new OpenApiCallLogService.Attempt(
                        attempt.applicationId(),
                        rawAppKey,
                        attempt.credentialVersion(),
                        routeTemplate,
                        request.getMethod(),
                        category,
                        status,
                        Math.max(0, (System.nanoTime() - started) / 1_000_000),
                        attribute(request, WebRequestAttributes.REQUEST_ID),
                        attribute(request, WebRequestAttributes.TRACE_ID),
                        observedIp
                ));
            } catch (RuntimeException logFailure) {
                LOGGER.error("Failed to persist sanitized OpenAPI call log", logFailure);
            }
        }
    }

    private static OpenApiCallLog.ResultCategory category(String code) {
        return switch (code) {
            case "OPENAPI_SIGNATURE_INVALID", "OPENAPI_TIMESTAMP_INVALID",
                    "OPENAPI_CREDENTIAL_UNAVAILABLE" ->
                    OpenApiCallLog.ResultCategory.SIGNATURE_REJECTED;
            case "OPENAPI_REPLAY_DETECTED" -> OpenApiCallLog.ResultCategory.REPLAY_REJECTED;
            case "OPENAPI_SCOPE_DENIED" -> OpenApiCallLog.ResultCategory.SCOPE_REJECTED;
            case "OPENAPI_PERMISSION_DENIED" -> OpenApiCallLog.ResultCategory.PERMISSION_REJECTED;
            case "OPENAPI_IP_DENIED" -> OpenApiCallLog.ResultCategory.IP_REJECTED;
            case "OPENAPI_RATE_LIMITED" -> OpenApiCallLog.ResultCategory.RATE_REJECTED;
            default -> OpenApiCallLog.ResultCategory.AUTH_REJECTED;
        };
    }

    private static void retryHeaders(HttpServletResponse response, BusinessException exception) {
        if (!"OPENAPI_RATE_LIMITED".equals(exception.code())
                || !(exception.data() instanceof Map<?, ?> data)) {
            return;
        }
        response.setHeader("Retry-After", String.valueOf(data.get("retryAfterSeconds")));
        response.setHeader("X-RateLimit-Limit", String.valueOf(data.get("limit")));
        response.setHeader("X-RateLimit-Remaining", "0");
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? UUID.randomUUID().toString() : String.valueOf(value);
    }

    private static String observedIp(HttpServletRequest request) {
        var value = request.getRemoteAddr();
        return value == null || value.isBlank() ? "0.0.0.0" : value;
    }
}
