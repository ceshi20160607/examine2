package com.unique.examine.ai;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Objects;
import java.util.Set;

/** Platform-authenticated AI identity. It deliberately has no system scope. */
public record PlatformAiActor(
        long accountId,
        Set<String> effectivePermissions,
        long authorizationEpoch,
        String requestId,
        String traceId
) {
    public PlatformAiActor {
        if (accountId <= 0 || authorizationEpoch <= 0) {
            throw new IllegalArgumentException(
                    "Platform AI account id and epoch must be positive");
        }
        effectivePermissions = Set.copyOf(Objects.requireNonNull(
                effectivePermissions, "effectivePermissions"));
        if (effectivePermissions.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "Platform AI permissions are invalid");
        }
        requestId = correlation(requestId, "requestId");
        traceId = correlation(traceId, "traceId");
    }

    public void require(String permission) {
        if (!effectivePermissions.contains(permission)) {
            throw new BusinessException(
                    "PLATFORM_AI_PERMISSION_DENIED",
                    "Platform AI permission is required",
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
