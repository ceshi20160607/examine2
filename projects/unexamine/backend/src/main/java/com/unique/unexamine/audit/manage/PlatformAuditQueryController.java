package com.unique.unexamine.audit.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 平台后台审计入口，仅开放本平台事件的查询和详情。 */
@RestController
@RequestMapping("/api/admin/platform/audit-events")
@RequirePermission(resourceType = "PLATFORM", resourceCode = "AUDIT", actionCode = "VIEW")
public class PlatformAuditQueryController {
    private final AuditQueryService queryService;
    private final AuditGovernanceService governanceService;

    public PlatformAuditQueryController(AuditQueryService queryService, AuditGovernanceService governanceService) {
        this.queryService = queryService;
        this.governanceService = governanceService;
    }

    /** 使用账号、事件、对象、结果和时间筛选本平台事件。 */
    @GetMapping
    public ApiResult<AuditEventList> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Long actorAccountId,
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredTo) {
        return ApiResult.ok(queryService.listPlatform(AuthenticationContextHolder.require(), page, pageSize,
                traceId, requestId, actorAccountId, eventCode, objectType, objectId, resultCode,
                occurredFrom, occurredTo));
    }

    /** 单条详情二次校验平台范围，字段前后值按敏感权限遮罩。 */
    @GetMapping("/{eventId}")
    public ApiResult<AuditGovernanceModels.EventDetail> detail(@PathVariable Long eventId) {
        return ApiResult.ok(governanceService.platformDetail(AuthenticationContextHolder.require(), eventId));
    }
}
