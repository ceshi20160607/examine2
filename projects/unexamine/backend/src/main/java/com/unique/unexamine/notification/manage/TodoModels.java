package com.unique.unexamine.notification.manage;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TodoModels {
    private TodoModels() {
    }

    public record HandleRequest(
            @NotBlank @Size(max = 64) String actionCode,
            @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey,
            @JsonAlias("targetAccountId") Long targetTenantMemberId) {
    }

    public record TodoView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            Long assigneeTenantMemberId, String assigneeName,
            String todoType, String sourceType, String sourceId,
            String sourceLabel, String objectName, String initiatorName,
            String title, String summary, String targetRoute, String priority,
            LocalDateTime dueAt, String status, LocalDateTime completedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt, Integer version,
            List<String> availableActions, String freshnessReason) {
    }

    public record HandleResult(TodoView todo, String resultReference, Object targetResult) {
    }
}
