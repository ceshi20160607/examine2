package com.unique.examine.ai;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.Set;

public record AiActor(
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        Set<String> effectivePermissions,
        long authorizationEpoch,
        String requestId,
        String traceId
) {
    public AiActor {
        if (accountId <= 0 || systemId <= 0 || tenantId <= 0 || memberId <= 0
                || authorizationEpoch <= 0) {
            throw new IllegalArgumentException("AI actor ids and epoch must be positive");
        }
        Objects.requireNonNull(effectivePermissions, "effectivePermissions");
        if (effectivePermissions.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("AI actor permissions are invalid");
        }
        effectivePermissions = Set.copyOf(effectivePermissions);
        requestId = correlation(requestId, "requestId");
        traceId = correlation(traceId, "traceId");
    }

    public void require(String permission) {
        if (!effectivePermissions.contains(permission)) {
            throw new BusinessException(
                    "AI_PERMISSION_DENIED",
                    "AI permission is required",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static String correlation(String value, String field) {
        if (value == null
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
