package com.unique.examine.openapi.security;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public final class OpenApiSecurityErrors {
    private OpenApiSecurityErrors() {
    }

    public static BusinessException authenticationRequired() {
        return error("OPENAPI_AUTH_REQUIRED", "OpenAPI authentication headers are required",
                HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException appUnavailable() {
        return error("OPENAPI_APP_UNAVAILABLE", "OpenAPI application is unavailable",
                HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException credentialUnavailable() {
        return error("OPENAPI_CREDENTIAL_UNAVAILABLE", "OpenAPI credential is unavailable",
                HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException signatureInvalid() {
        return error("OPENAPI_SIGNATURE_INVALID", "OpenAPI signature is invalid",
                HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException timestampInvalid() {
        return error("OPENAPI_TIMESTAMP_INVALID", "OpenAPI timestamp is outside the accepted window",
                HttpStatus.UNAUTHORIZED);
    }

    public static BusinessException replayDetected() {
        return error("OPENAPI_REPLAY_DETECTED", "OpenAPI nonce has already been used",
                HttpStatus.CONFLICT);
    }

    public static BusinessException scopeDenied() {
        return error("OPENAPI_SCOPE_DENIED", "OpenAPI application scope is insufficient",
                HttpStatus.FORBIDDEN);
    }

    public static BusinessException permissionDenied() {
        return error("OPENAPI_PERMISSION_DENIED", "OpenAPI service member permission is insufficient",
                HttpStatus.FORBIDDEN);
    }

    public static BusinessException ipDenied() {
        return error("OPENAPI_IP_DENIED", "OpenAPI source IP is not allowed",
                HttpStatus.FORBIDDEN);
    }

    public static BusinessException rateLimited(long retryAfterSeconds, int limit) {
        return new BusinessException(
                "OPENAPI_RATE_LIMITED",
                "OpenAPI application rate limit was exceeded",
                HttpStatus.TOO_MANY_REQUESTS,
                java.util.List.of(),
                Map.of("retryAfterSeconds", retryAfterSeconds, "limit", limit)
        );
    }

    private static BusinessException error(String code, String message, HttpStatus status) {
        return new BusinessException(code, message, status);
    }
}
