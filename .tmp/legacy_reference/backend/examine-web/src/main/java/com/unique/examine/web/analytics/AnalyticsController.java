package com.unique.examine.web.analytics;

import com.unique.examine.analytics.domain.AnalyticsActor;
import com.unique.examine.analytics.service.OperationsDashboardService;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public final class AnalyticsController {
    private final OperationsDashboardService dashboards;

    public AnalyticsController(OperationsDashboardService dashboards) {
        if (dashboards == null) throw new IllegalArgumentException("Analytics service is required");
        this.dashboards = dashboards;
    }

    @GetMapping("/api/v1/systems/{systemId}/analytics/operations")
    public ApiResponse<AnalyticsApiModels.Snapshot> operations(
            @PathVariable long systemId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        var result = dashboards.snapshot(actor(session, systemId), from, to);
        return ApiResponse.success(AnalyticsApiModels.Snapshot.from(result),
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static AnalyticsActor actor(Object value, long systemId) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException("AUTH_REQUIRED", "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null || session.systemId() != systemId
                || session.tenantId() == null || session.memberId() == null) {
            throw new BusinessException("CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system member context does not match the request",
                    HttpStatus.FORBIDDEN);
        }
        return new AnalyticsActor(session.accountId(), systemId,
                session.tenantId(), session.memberId(), session.permissions());
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
