package com.unique.examine.openapi.security;

import jakarta.servlet.http.HttpServletRequest;

public record OpenApiHeaders(
        String appKey,
        String timestamp,
        String nonce,
        String signature,
        String idempotencyKey
) {
    public static OpenApiHeaders require(HttpServletRequest request) {
        var value = new OpenApiHeaders(
                request.getHeader("X-App-Key"),
                request.getHeader("X-Timestamp"),
                request.getHeader("X-Nonce"),
                request.getHeader("X-Signature"),
                request.getHeader("Idempotency-Key")
        );
        if (value.appKey == null
                || !value.appKey.matches("^[A-Za-z0-9_-]{16,96}$")
                || value.timestamp == null
                || !value.timestamp.matches("^[1-9][0-9]{0,18}$")
                || value.nonce == null
                || !value.nonce.matches("^[A-Za-z0-9._~-]{16,128}$")
                || value.signature == null
                || !value.signature.matches("^[0-9a-f]{64}$")
                || value.idempotencyKey == null
                || value.idempotencyKey.isBlank()
                || value.idempotencyKey.length() > 128) {
            throw OpenApiSecurityErrors.authenticationRequired();
        }
        return value;
    }
}
