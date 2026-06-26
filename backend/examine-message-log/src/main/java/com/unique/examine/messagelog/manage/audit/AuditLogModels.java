package com.unique.examine.messagelog.manage.audit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Audit log API models.
 */
public final class AuditLogModels {

    private AuditLogModels() {
    }

    public record AuditLogQueryRequest(String logType, String scope, String systemId, String tenantId,
                                       String operator, String action, String objectType, String result,
                                       String traceId, String keyword, String timeRange) {
    }

    public record AuditLogListItemVO(String logId, String logType, String scope, String systemId, String tenantId,
                                     String operator, String action, String objectType, String objectId,
                                     String result, String requestId, String traceId, String auditLogId,
                                     LocalDateTime createdAt) {
    }

    public record AuditLogDetailVO(String logId, String logType, String scope, String systemId, String tenantId,
                                   String operator, String action, String objectType, String objectId,
                                   String result, String requestId, String traceId, String auditLogId,
                                   String ip, String device, Map<String, Object> fieldDiff,
                                   Map<String, Object> desensitizeResult, Map<String, Object> permissionSnapshot,
                                   String failureReason, List<String> relatedTaskIds, LocalDateTime createdAt) {
    }
}
