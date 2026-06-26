package com.unique.examine.messagelog.manage.notification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification template and delivery log API models.
 */
public final class NotificationModels {

    private NotificationModels() {
    }

    public record NotificationTemplateQueryRequest(String scope, String templateType, Integer status,
                                                   String keyword) {
    }

    public record NotificationTemplateSaveRequest(String templateCode, String scope, String templateType,
                                                  List<String> variables, List<String> channels,
                                                  NotificationTargetRuleVO targetRule, String dedupeKey,
                                                  Boolean readReceiptRequired, QuietPolicyVO quietPolicy,
                                                  RetryPolicyVO retryPolicy, Integer status) {
    }

    public record NotificationTargetRuleVO(String scope, String targetType, String targetId, String targetSystemId,
                                           String targetTenantId, Boolean requiresSystemSwitch,
                                           String fallbackAction) {
    }

    public record QuietPolicyVO(Boolean enabled, String quietTimeRange, List<String> bypassTypes) {
    }

    public record RetryPolicyVO(Integer maxRetryCount, Integer intervalSeconds, Boolean retryFailedOnly) {
    }

    public record NotificationTemplateVO(String templateId, String systemId, String templateCode, String scope,
                                         String templateType, List<String> variables, List<String> channels,
                                         NotificationTargetRuleVO targetRule, String dedupeKey,
                                         boolean readReceiptRequired, QuietPolicyVO quietPolicy,
                                         RetryPolicyVO retryPolicy, Integer status, String auditLogId,
                                         LocalDateTime updatedAt) {
    }

    public record NotificationTemplateDeleteResult(String templateCode, String systemId, String status,
                                                   String auditLogId, LocalDateTime deletedAt) {
    }

    public record PublishCheckItem(String itemCode, String level, String message, String suggestion) {
    }

    public record PublishCheckResultVO(boolean passed, List<PublishCheckItem> failureItems,
                                       List<PublishCheckItem> warningItems, List<String> impactRefs,
                                       String traceId, String auditLogId, LocalDateTime checkedAt) {
    }

    public record MessageDeliveryLogQueryRequest(String tenantId, String templateCode, String messageId,
                                                 String channel, String status, String archiveStatus,
                                                 String traceId, String timeRange, String keyword) {
    }

    public record MessageDeliveryLogVO(String deliveryLogId, String systemId, String tenantId, String templateCode,
                                       String messageId, String channel, String status, String failureReason,
                                       Integer retryCount, String readReceipt, boolean doNotDisturb,
                                       String archiveStatus, String traceId, LocalDateTime createdAt) {
    }
}
