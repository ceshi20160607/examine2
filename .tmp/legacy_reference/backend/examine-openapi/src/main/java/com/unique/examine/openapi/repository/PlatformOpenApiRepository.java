package com.unique.examine.openapi.repository;

import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.OpenApiRateBucket;
import com.unique.examine.openapi.domain.PlatformOpenApiApplication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlatformOpenApiRepository {
    Optional<ApplicationBundle> findByAppKey(String appKey);
    Optional<ApplicationBundle> find(long applicationId);
    List<ApplicationBundle> list(int offset, int limit);
    long count();
    void insertApplication(PlatformOpenApiApplication application);
    void insertCredential(OpenApiCredential credential);
    boolean updatePolicy(PlatformOpenApiApplication application, long expectedVersion);
    boolean updateStatus(long applicationId, PlatformOpenApiApplication.Status status,
                         long actorId, Instant updatedAt, long expectedVersion);
    boolean rotateCredential(PlatformOpenApiApplication application,
                             OpenApiCredential previous, OpenApiCredential replacement,
                             long expectedVersion);
    boolean consumeNonce(long applicationId, int credentialVersion, String nonce,
                         Instant expiresAt, Instant createdAt);
    OpenApiRateBucket lockRateBucket(long applicationId, Instant windowStart);
    boolean incrementRateBucket(long applicationId, Instant windowStart,
                                int expectedCount, long expectedVersion);
    void insertCallLog(OpenApiCallLog callLog);
    long countCallLogs(long applicationId, String resultCategory, String method);
    List<OpenApiRepository.ApplicationCallLog> listCallLogs(long applicationId,
            String resultCategory, String method, int offset, int limit);

    record ApplicationBundle(PlatformOpenApiApplication application,
                             OpenApiCredential credential) {}
}
