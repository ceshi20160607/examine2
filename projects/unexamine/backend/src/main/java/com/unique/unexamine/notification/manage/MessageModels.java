package com.unique.unexamine.notification.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MessageModels {
    private MessageModels() {
    }

    public record SaveTemplateRequest(
            Long id,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "IN_APP|EMAIL|SMS|WEBHOOK") String channel,
            @Size(max = 500) String subjectTemplate,
            @NotBlank @Size(max = 20000) String contentTemplate,
            List<@Pattern(regexp = "[a-z][a-zA-Z0-9_]{0,99}") String> requiredVariables,
            Integer expectedVersion) {
    }

    public record TemplateView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            String code, String name, String channel, Integer draftRevision,
            String subjectTemplate, String contentTemplate, List<String> requiredVariables,
            String status, Integer publishedVersion, Long publishedVersionId,
            LocalDateTime publishedAt, Integer version, LocalDateTime updatedAt) {
    }

    public record SendEventRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,99}") String templateCode,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String sourceType,
            @NotBlank @Size(max = 100) String dedupKey,
            @NotEmpty List<Long> recipientAccountIds,
            Map<String, Object> variables,
            @Pattern(regexp = "PLATFORM_ROUTE|SYSTEM_ROUTE|FLOW_INSTANCE|RUNTIME_RECORD|WORK_TASK|APPLICATION|FILE_OBJECT") String targetType,
            @Size(max = 100) String targetId,
            @Size(max = 1000) String targetRoute,
            @Pattern(regexp = "NORMAL|IMPORTANT|SENSITIVE") String sensitivity) {
    }

    public record DeliveryView(
            Long id, String channel, String destinationMasked, String status,
            Integer attemptCount, LocalDateTime nextAttemptAt, String providerMessageId,
            String providerReceipt, String lastError, LocalDateTime sentAt,
            LocalDateTime deliveredAt, Integer version) {
    }

    public record MessageView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            Long templateVersionId, String sourceType, String sourceId,
            String subject, String content, String targetType, String targetId,
            String targetRoute, String sensitivity, String recipientStatus,
            LocalDateTime readAt, LocalDateTime archivedAt, Integer recipientVersion,
            LocalDateTime createdAt, boolean targetCurrentlyAccessible,
            List<DeliveryView> deliveries) {
    }

    public record InboxView(List<MessageView> messages, long unreadCount) {
    }

    public record UnreadCount(long count) {
    }

    public record OpenResult(Long messageId, String route, Long switchSystemId, String targetType, String targetId) {
    }
}
