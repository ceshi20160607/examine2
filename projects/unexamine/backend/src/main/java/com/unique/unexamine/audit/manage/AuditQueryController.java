package com.unique.unexamine.audit.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit-events")
@RequirePermission(resourceType = "AUDIT", resourceCode = "EVENT", actionCode = "VIEW")
public class AuditQueryController {
    private final AuditQueryService auditQueryService;
    private final AuditGovernanceService governanceService;

    public AuditQueryController(AuditQueryService auditQueryService, AuditGovernanceService governanceService) {
        this.auditQueryService = auditQueryService;
        this.governanceService = governanceService;
    }

    @GetMapping
    public ApiResult<AuditEventList> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Long actorAccountId,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredTo) {
        return ApiResult.ok(auditQueryService.list(AuthenticationContextHolder.require(), page, pageSize,
                traceId, requestId, actorAccountId, memberId, eventCode, objectType, objectId, resultCode,
                occurredFrom, occurredTo));
    }

    @GetMapping("/{eventId}")
    public ApiResult<AuditGovernanceModels.EventDetail> detail(@PathVariable Long eventId) {
        return ApiResult.ok(governanceService.detail(AuthenticationContextHolder.require(), eventId));
    }

    @PostMapping("/retention/preflight")
    public ApiResult<AuditGovernanceModels.RetentionPreflight> retentionPreflight(
            @Valid @RequestBody AuditGovernanceModels.RetentionPreflightRequest input) {
        return ApiResult.ok(governanceService.retentionPreflight(AuthenticationContextHolder.require(), input));
    }

    @PostMapping("/retention")
    @RequirePermission(resourceType = "AUDIT", resourceCode = "EVENT", actionCode = "PURGE")
    public ApiResult<AuditGovernanceModels.RetentionMarkerView> retainPermanently(
            @Valid @RequestBody AuditGovernanceModels.CreateRetentionMarkerRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(governanceService.retainPermanently(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }
}
