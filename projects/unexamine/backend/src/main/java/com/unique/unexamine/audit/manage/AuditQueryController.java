package com.unique.unexamine.audit.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-events")
@RequirePermission(resourceType = "AUDIT", resourceCode = "EVENT", actionCode = "VIEW")
public class AuditQueryController {
    private final AuditQueryService auditQueryService;

    public AuditQueryController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public ApiResult<AuditEventList> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String resultCode) {
        return ApiResult.ok(auditQueryService.list(AuthenticationContextHolder.require(), page, pageSize,
                traceId, eventCode, objectType, objectId, resultCode));
    }
}
