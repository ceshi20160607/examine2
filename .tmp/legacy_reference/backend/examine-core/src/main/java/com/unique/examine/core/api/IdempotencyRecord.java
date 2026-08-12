package com.unique.examine.core.api;

public record IdempotencyRecord(
        long id,
        String requestHash,
        String status,
        String responseBody
) {
}
