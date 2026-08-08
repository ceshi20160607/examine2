package com.unique.examine.core.api;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyFacade {
    Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key);

    long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl);

    void complete(long id, int httpStatus, String responseCode, String responseBody);
}
