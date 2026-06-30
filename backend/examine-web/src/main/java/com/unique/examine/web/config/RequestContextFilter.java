package com.unique.examine.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.manage.auth.AuthTokenService;
import com.unique.examine.plat.manage.common.PlatformAccessGuard;
import com.unique.examine.plat.manage.common.SystemAccessGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds requestId and traceId for every HTTP request.
 */
@Component("examineRequestContextFilter")
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/api/v1/health",
            "/api/v1/auth/login",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/register-with-system",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm"
    );

    private final AuthTokenService authTokenService;
    private final PlatformAccessGuard platformAccessGuard;
    private final SystemAccessGuard systemAccessGuard;
    private final ObjectMapper objectMapper;

    public RequestContextFilter(AuthTokenService authTokenService,
                                PlatformAccessGuard platformAccessGuard,
                                SystemAccessGuard systemAccessGuard,
                                ObjectMapper objectMapper) {
        this.authTokenService = authTokenService;
        this.platformAccessGuard = platformAccessGuard;
        this.systemAccessGuard = systemAccessGuard;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RequestContext context = RequestContext.create(
                request.getHeader(REQUEST_ID_HEADER),
                request.getHeader(TRACE_ID_HEADER)
        );
        if (requiresAudit(request.getMethod())) {
            context = context.withAuditLogId("aud_" + context.traceId());
        }
        Long accountId;
        try {
            accountId = authTokenService.resolveAccessToken(AuthTokenService.bearerToken(request));
        } catch (RuntimeException ex) {
            RequestContext.bind(context);
            response.setHeader(REQUEST_ID_HEADER, context.requestId());
            response.setHeader(TRACE_ID_HEADER, context.traceId());
            writeFailure(response, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.OPS_INTERNAL_ERROR.code(),
                    "登录会话存储不可用");
            RequestContext.clear();
            return;
        }
        if (accountId != null) {
            context = context.withAccountId(accountId);
        }
        RequestContext.bind(context);
        response.setHeader(REQUEST_ID_HEADER, context.requestId());
        response.setHeader(TRACE_ID_HEADER, context.traceId());
        if (requiresAuthentication(request) && accountId == null) {
            writeFailure(response, HttpStatus.UNAUTHORIZED, CommonErrorCode.AUTH_UNAUTHORIZED.code(),
                    CommonErrorCode.AUTH_UNAUTHORIZED.message());
            RequestContext.clear();
            return;
        }
        if (requiresPlatformAdmin(request) && !platformAccessGuard.canManagePlatform(accountId)) {
            writeFailure(response, HttpStatus.FORBIDDEN, CommonErrorCode.PERMISSION_DENIED.code(),
                    CommonErrorCode.PERMISSION_DENIED.message());
            RequestContext.clear();
            return;
        }
        String systemId = systemIdFromPath(request.getRequestURI());
        if (requiresSystemAdmin(request) && !systemAccessGuard.canManageSystem(accountId, systemId)) {
            writeFailure(response, HttpStatus.FORBIDDEN, CommonErrorCode.PERMISSION_DENIED.code(),
                    CommonErrorCode.PERMISSION_DENIED.message());
            RequestContext.clear();
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/") && !PUBLIC_API_PATHS.contains(path);
    }

    private boolean requiresPlatformAdmin(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/platform/")) {
            return false;
        }
        return !path.startsWith("/api/v1/platform/system-switch")
                && !path.startsWith("/api/v1/platform/todos")
                && !path.startsWith("/api/v1/platform/messages");
    }

    private boolean requiresSystemAdmin(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String remainder = systemRemainder(request.getRequestURI());
        if (remainder == null || remainder.startsWith("tenant-switch")
                || remainder.startsWith("runtime/") || remainder.startsWith("todos/")
                || remainder.startsWith("messages/") || remainder.startsWith("flow-instances/")) {
            return false;
        }
        String method = request.getMethod();
        if (remainder.startsWith("members") || remainder.startsWith("member-bindings")
                || remainder.startsWith("roles") || remainder.startsWith("permissions")
                || remainder.startsWith("org/") || remainder.startsWith("flows")
                || remainder.startsWith("sso/") || remainder.startsWith("openapi/")
                || remainder.startsWith("notification-templates")
                || remainder.startsWith("message-delivery-logs") || remainder.startsWith("logs/")
                || remainder.startsWith("agent/policies") || remainder.startsWith("work/config")
                || remainder.startsWith("work/home-page-config")
                || remainder.startsWith("ops/")) {
            return true;
        }
        if (remainder.startsWith("tenants")) {
            return !"GET".equalsIgnoreCase(method);
        }
        if (remainder.startsWith("module-groups")) {
            return !"GET".equalsIgnoreCase(method);
        }
        if (remainder.startsWith("modules")) {
            return !("GET".equalsIgnoreCase(method) && ("modules".equals(remainder) || remainder.startsWith("modules?")));
        }
        if (remainder.startsWith("dict-types")) {
            return !"GET".equalsIgnoreCase(method);
        }
        return false;
    }

    private String systemIdFromPath(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 5 && "api".equals(parts[1]) && "v1".equals(parts[2]) && "systems".equals(parts[3])) {
            return parts[4];
        }
        return null;
    }

    private String systemRemainder(String path) {
        String[] parts = path.split("/", 6);
        if (parts.length < 6 || !"api".equals(parts[1]) || !"v1".equals(parts[2]) || !"systems".equals(parts[3])) {
            return null;
        }
        return parts[5];
    }

    private void writeFailure(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(code, message, null, null));
    }

    private boolean requiresAudit(String method) {
        return !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method);
    }
}
