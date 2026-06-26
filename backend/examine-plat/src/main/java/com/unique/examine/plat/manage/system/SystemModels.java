package com.unique.examine.plat.manage.system;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Platform system lifecycle API models.
 */
public final class SystemModels {

    private SystemModels() {
    }

    public record SystemQueryRequest(String status, Integer tenantMode, String owner, String keyword,
                                     String recentAccessRange) {
    }

    public record SystemCreateRequest(String systemName, String systemCode, Integer tenantMode, String templateCode) {
    }

    public record SystemUpdateRequest(String systemName, Integer tenantMode, String disabledReason) {
    }

    public record SystemLifecycleRequest(String reason, String impactScope, String idempotencyKey) {
    }

    public record SystemVO(String systemId, String systemCode, String systemName, Integer tenantMode,
                           String ownerAccountId, Integer status, String disabledReason,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record LifecycleResult(String result, String systemId, String traceId, String auditLogId,
                                  String asyncTaskId, LocalDateTime operatedAt) {
    }

    public record PlatformHealthVO(String status, List<String> checks, List<String> warnings,
                                   String traceId, LocalDateTime checkedAt) {
    }
}
