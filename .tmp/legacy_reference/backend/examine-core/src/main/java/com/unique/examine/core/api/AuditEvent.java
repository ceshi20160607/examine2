package com.unique.examine.core.api;

public record AuditEvent(
        String eventType,
        Long accountId,
        String accountHint,
        Long systemId,
        Long tenantId,
        String sourceType,
        String remoteAddress,
        String userAgent,
        String requestId,
        String traceId,
        String result,
        String failureCode,
        String detailJson
) {
}
