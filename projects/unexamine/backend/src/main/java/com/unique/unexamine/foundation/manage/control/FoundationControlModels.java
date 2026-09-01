package com.unique.unexamine.foundation.manage.control;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FoundationControlModels {
    private FoundationControlModels() {
    }

    public record SubmitCommandRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{3,200}") String idempotencyKey,
            @Size(max = 100) @Pattern(regexp = "[a-zA-Z0-9._-]*") String applicationCode,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,99}") String operationCode,
            @NotNull Map<String, Object> payload) {
    }

    public record CommandResult(
            long idempotencyRecordId,
            long outboxId,
            String idempotencyKey,
            String operationCode,
            String resultReference,
            String status,
            boolean replayed,
            String committedAt,
            String extensionStatus) {
    }

    public record CommandRecordView(
            long id,
            String contextKey,
            String operationCode,
            String idempotencyKey,
            String status,
            String responseCode,
            LocalDateTime expiresAt,
            int version) {
    }

    public record CacheProbe(
            long epoch,
            String source,
            String cacheKey,
            List<Long> roleIds,
            int permissionCount) {
    }

    public record InvalidateCacheRequest(
            @NotBlank @Size(max = 500) String reason) {
    }

    public record ControlOverview(
            String applicationCode,
            String operationCode,
            RedisRateLimiter.RateLimitState rateLimit,
            CacheProbe permissionCache,
            List<CommandRecordView> recentCommands) {
    }

    public record SaveFeatureFlagRequest(
            Long id,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9._-]{2,149}") String flagKey,
            boolean enabled,
            @NotBlank @Pattern(regexp = "SYSTEM|APPLICATION|MODULE|ROLE") String targetType,
            @NotNull List<@NotBlank String> targetCodes,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean fallbackEnabled,
            @Size(max = 100) String stableVariant,
            Integer expectedVersion) {
    }

    public record FeatureFlagView(
            long id,
            String flagKey,
            boolean enabled,
            String targetType,
            List<String> targetCodes,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            boolean fallbackEnabled,
            String stableVariant,
            String status,
            int version,
            LocalDateTime updatedAt) {
    }

    public record FeatureFlagResolution(
            String flagKey,
            boolean enabled,
            boolean permissionStillRequired,
            String source,
            String reason,
            int version) {
    }

    public record SaveQuotaRequest(
            Long id,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,99}") String quotaCode,
            @NotBlank @Pattern(regexp = "TOTAL|DAY|MONTH") String periodType,
            @Min(1) long hardLimit,
            @Min(0) Long warningThreshold,
            Integer expectedVersion) {
    }

    public record QuotaView(
            long id,
            String quotaCode,
            String periodType,
            long hardLimit,
            Long warningThreshold,
            String periodKey,
            long usedValue,
            long reservedValue,
            long remaining,
            String status,
            int version) {
    }

    public record ConsumeQuotaRequest(
            @Min(1) @Max(1000000) long amount,
            @Size(max = 200) String reference) {
    }

    public record SaveSequenceRequest(
            Long id,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{2,99}") String sequenceCode,
            @NotBlank @Size(max = 255) String pattern,
            @NotBlank @Pattern(regexp = "NONE|DAY|MONTH|YEAR") String resetPeriod,
            @Min(1) @Max(1000) int stepValue,
            Integer expectedVersion) {
    }

    public record SequenceView(
            long id,
            String sequenceCode,
            String pattern,
            String resetPeriod,
            String periodKey,
            long currentValue,
            int stepValue,
            String status,
            int version) {
    }

    public record SequenceValue(
            String sequenceCode,
            String value,
            String periodKey,
            long numericValue,
            int version) {
    }
}
