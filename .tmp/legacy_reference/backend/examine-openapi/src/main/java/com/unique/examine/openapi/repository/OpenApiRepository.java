package com.unique.examine.openapi.repository;

import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.OpenApiRateBucket;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OpenApiRepository {
    Optional<ApplicationBundle> findByAppKey(String appKey);

    Optional<ApplicationBundle> find(long systemId, long tenantId, long applicationId);

    List<ApplicationBundle> list(long systemId, long tenantId, int offset, int limit);

    long count(long systemId, long tenantId);

    void insertApplication(OpenApiApplication application);

    void insertCredential(OpenApiCredential credential);

    boolean updatePolicy(OpenApiApplication application, long expectedVersion);

    boolean updateStatus(
            long systemId,
            long tenantId,
            long applicationId,
            OpenApiApplication.Status status,
            long actorId,
            Instant updatedAt,
            long expectedVersion
    );

    boolean rotateCredential(
            OpenApiApplication application,
            OpenApiCredential previous,
            OpenApiCredential replacement,
            long expectedVersion
    );

    boolean consumeNonce(
            long applicationId,
            int credentialVersion,
            String nonce,
            Instant expiresAt,
            Instant createdAt
    );

    OpenApiRateBucket lockRateBucket(long applicationId, Instant windowStart);

    boolean incrementRateBucket(
            long applicationId,
            Instant windowStart,
            int expectedCount,
            long expectedVersion
    );

    void insertCallLog(OpenApiCallLog callLog);

    default long countApplicationCallLogs(
            long applicationId,
            CallLogResultFilter resultCategory,
            CallLogMethodFilter requestMethod) {
        throw new UnsupportedOperationException("Application call-log reads are unavailable");
    }

    default List<ApplicationCallLog> listApplicationCallLogs(
            long applicationId,
            CallLogResultFilter resultCategory,
            CallLogMethodFilter requestMethod,
            int offset,
            int limit) {
        throw new UnsupportedOperationException("Application call-log reads are unavailable");
    }

    record ApplicationBundle(OpenApiApplication application, OpenApiCredential credential) {
    }

    enum CallLogResultFilter {
        ALL,
        SUCCESS,
        AUTH_REJECTED,
        SIGNATURE_REJECTED,
        REPLAY_REJECTED,
        SCOPE_REJECTED,
        PERMISSION_REJECTED,
        IP_REJECTED,
        RATE_REJECTED,
        FAILED
    }

    enum CallLogMethodFilter {
        ALL, GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS
    }

    /** Safe application-scoped row; intentionally excludes app-key hash. */
    record ApplicationCallLog(
            long id,
            Integer credentialVersion,
            String routeTemplate,
            String requestMethod,
            OpenApiCallLog.ResultCategory resultCategory,
            int httpStatus,
            long latencyMs,
            String requestId,
            String traceId,
            String observedIp,
            Instant createdAt
    ) {
        public ApplicationCallLog {
            if (id <= 0 || credentialVersion != null && credentialVersion < 1
                    || httpStatus < 100 || httpStatus > 599
                    || latencyMs < 0 || latencyMs > 86_400_000) {
                throw new IllegalArgumentException("Application call-log row is invalid");
            }
            if (routeTemplate == null || routeTemplate.isBlank()
                    || requestMethod == null || requestMethod.isBlank()
                    || resultCategory == null
                    || requestId == null || requestId.isBlank()
                    || traceId == null || traceId.isBlank()
                    || observedIp == null || observedIp.isBlank()
                    || createdAt == null) {
                throw new IllegalArgumentException(
                        "Application call-log projection is invalid");
            }
        }
    }
}
