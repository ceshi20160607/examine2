package com.unique.unexamine.application.manage;

import java.time.LocalDateTime;
import java.util.Map;

public final class ApplicationBridgeModels {
    private ApplicationBridgeModels() {
    }

    public record CallRequest(
            String resourceType,
            String resourceId,
            String actionCode,
            Long targetSystemId,
            Long targetTenantId,
            Map<String, Object> requestedDataScope,
            Map<String, Object> payload) {
    }

    public record CallResult(
            String requestId,
            boolean replayed,
            Long applicationId,
            Long grantId,
            Integer credentialVersion,
            String resourceType,
            String resourceId,
            String actionCode,
            String targetReference,
            Object result,
            Map<String, Object> source) {
    }

    public record CallLogView(
            Long id,
            String requestId,
            String traceId,
            Long applicationVersionId,
            Integer credentialVersion,
            Long grantId,
            String sourceAddress,
            Long targetSystemId,
            Long targetTenantId,
            String resourceType,
            String resourceId,
            String actionCode,
            String status,
            String responseCode,
            Long durationMillis,
            String errorMessage,
            String targetReference,
            Integer replayCount,
            Map<String, Object> permissionSnapshot,
            Object response,
            LocalDateTime calledAt,
            LocalDateTime finishedAt) {
    }
}
