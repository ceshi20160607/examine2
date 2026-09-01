package com.unique.unexamine.backgroundjobs.manage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class BackgroundJobModels {
    private BackgroundJobModels() {
    }

    public record SubmitJobRequest(
            @NotBlank String jobType,
            @NotBlank String sourceType,
            String sourceId,
            @NotNull Map<String, Object> parameters,
            @Min(1) @Max(5) Integer maxAttempts) {
    }

    public record RetryJobRequest(@NotBlank String reason) {
    }

    public record HandlerView(String jobType, String name, String description) {
    }

    public record RedisState(
            String status,
            long progressCurrent,
            long progressTotal,
            int attemptCount,
            String updatedAt,
            boolean queued,
            boolean locked,
            String source) {
    }

    public record JobView(
            long id,
            String contextType,
            Long systemId,
            Long tenantId,
            String jobType,
            String sourceType,
            String sourceId,
            String status,
            long progressCurrent,
            long progressTotal,
            int maxAttempts,
            int attemptCount,
            String nextRunAt,
            String heartbeatAt,
            Map<String, Object> resultSummary,
            String errorCode,
            String errorMessage,
            String createdAt,
            String startedAt,
            String finishedAt,
            RedisState redisState) {
    }

    public record AttemptView(
            long id,
            int attemptNumber,
            String workerId,
            String status,
            String startedAt,
            String finishedAt,
            String errorCode,
            String errorMessage) {
    }

    public record ItemView(
            long id,
            String itemKey,
            Long rowNumber,
            String status,
            Map<String, Object> result,
            String errorCode,
            String errorMessage,
            String updatedAt) {
    }

    public record JobDetail(JobView job, List<AttemptView> attempts, List<ItemView> items,
                            Map<String, Object> authorizationSnapshot) {
    }
}
