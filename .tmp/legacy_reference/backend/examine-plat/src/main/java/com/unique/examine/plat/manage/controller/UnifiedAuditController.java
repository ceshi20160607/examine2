package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.audit.UnifiedAuditModels;
import com.unique.examine.plat.manage.audit.UnifiedAuditQuery;
import com.unique.examine.plat.manage.audit.OperationsHealthModels;
import com.unique.examine.plat.manage.audit.OperationsHealthQuery;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class UnifiedAuditController {
    private final UnifiedAuditQuery query;
    private final OperationsHealthQuery health;

    public UnifiedAuditController(UnifiedAuditQuery query, OperationsHealthQuery health) {
        this.query = query;
        this.health = health;
    }

    @GetMapping("/api/v1/platform/admin/audit-health")
    public ApiResponse<OperationsHealthModels.Summary> platformHealth(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.audit.view");
        return success(health.inspect(UnifiedAuditModels.Scope.platform()), request);
    }

    @GetMapping("/api/v1/platform/admin/audit-logs")
    public ApiResponse<UnifiedAuditModels.Page> platform(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String object,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.audit.view");
        return success(query.search(UnifiedAuditModels.Scope.platform(), filters(
                requestId, traceId, actor, object, result, category, from, to, page, size)), request);
    }

    @GetMapping("/api/v1/systems/{systemId}/admin/audit-logs")
    public ApiResponse<UnifiedAuditModels.Page> system(
            @PathVariable long systemId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String object,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.audit.view");
        return success(query.search(UnifiedAuditModels.Scope.system(systemId, session.tenantId()), filters(
                requestId, traceId, actor, object, result, category, from, to, page, size)), request);
    }

    @GetMapping("/api/v1/systems/{systemId}/admin/audit-health")
    public ApiResponse<OperationsHealthModels.Summary> systemHealth(
            @PathVariable long systemId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.audit.view");
        return success(health.inspect(
                UnifiedAuditModels.Scope.system(systemId, session.tenantId())), request);
    }

    private static UnifiedAuditModels.Query filters(
            String requestId, String traceId, String actor, String object, String result,
            String category, Instant from, Instant to, int page, int size
    ) {
        return new UnifiedAuditModels.Query(
                requestId, traceId, actor, object, result, category, from, to, page, size);
    }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(value, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}
