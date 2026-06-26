package com.unique.examine.messagelog.manage.message;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Message center API models.
 */
public final class MessageModels {

    private MessageModels() {
    }

    public record MessageQueryRequest(String systemId, String tenantId, String templateCode, String type,
                                      String readStatus, String archiveStatus, String timeRange, String keyword) {
    }

    public record MessageLoadMoreRequest(String cursor, Integer size, String systemId, String tenantId,
                                         String templateCode, String type, String readStatus, String archiveStatus,
                                         String timeRange, String keyword) {
    }

    public record MessageBulkActionRequest(List<String> messageIds, String tenantId, String reason) {
    }

    public record MessageMarkAllReadRequest(String tenantId, String templateCode, String type, String timeRange,
                                            String keyword) {
    }

    public record MessageTargetVO(String scope, String targetType, String targetId, String targetSystemId,
                                  String targetTenantId, boolean requiresSystemSwitch, String fallbackAction,
                                  String disabledReason) {
    }

    public record MessageCardVO(String messageId, String scope, String systemId, String tenantId,
                                String templateCode, String type, String title, String content,
                                String readStatus, String archiveStatus, MessageTargetVO target, String traceId,
                                LocalDateTime createdAt, LocalDateTime readAt, LocalDateTime archivedAt) {
    }

    public record MessageLoadMoreResult(List<MessageCardVO> records, String nextCursor, boolean hasNext,
                                        String status, LocalDateTime loadedAt) {
    }

    public record MessageActionResult(String scope, String systemId, String tenantId, String action,
                                      int affectedCount, String status, String traceId, String auditLogId,
                                      LocalDateTime operatedAt) {
    }
}
