package com.unique.examine.messagelog.manage.audit;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogDetailVO;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogListItemVO;
import com.unique.examine.messagelog.manage.audit.AuditLogModels.AuditLogQueryRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit log API controller.
 */
@RestController
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping("/api/v1/platform/logs/search")
    public ApiResponse<PageResult<AuditLogListItemVO>> platformLogs(@RequestParam(defaultValue = "1") int pageNo,
                                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                                    @RequestBody AuditLogQueryRequest query) {
        return ApiResponse.success(auditLogService.platformLogs(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @GetMapping("/api/v1/platform/logs/{logId}")
    public ApiResponse<AuditLogDetailVO> platformLogDetail(@PathVariable String logId) {
        return ApiResponse.success(auditLogService.detail("PLATFORM", null, logId));
    }

    @PostMapping("/api/v1/systems/{systemId}/logs/search")
    public ApiResponse<PageResult<AuditLogListItemVO>> systemLogs(@PathVariable String systemId,
                                                                  @RequestParam(defaultValue = "1") int pageNo,
                                                                  @RequestParam(defaultValue = "20") int pageSize,
                                                                  @RequestBody AuditLogQueryRequest query) {
        return ApiResponse.success(auditLogService.systemLogs(systemId, new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @GetMapping("/api/v1/systems/{systemId}/logs/{logId}")
    public ApiResponse<AuditLogDetailVO> systemLogDetail(@PathVariable String systemId, @PathVariable String logId) {
        return ApiResponse.success(auditLogService.detail("SYSTEM", systemId, logId));
    }
}
