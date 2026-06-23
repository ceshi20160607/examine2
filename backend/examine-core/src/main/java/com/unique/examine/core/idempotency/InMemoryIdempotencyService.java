package com.unique.examine.core.idempotency;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.unique.examine.core.common.response.ApiErrorDetail;
import com.unique.examine.core.common.response.ApiResponse;
import com.unique.examine.core.common.response.ApiResponseFactory;
import com.unique.examine.core.common.response.ApiResponseMeta;
import com.unique.examine.core.error.CommonErrorCode;
import org.springframework.util.Assert;

/**
 * 基于内存的幂等基础实现，供本地自检和无持久化场景复用。
 */
public class InMemoryIdempotencyService implements IdempotencyService {

    private final Map<String, IdempotencyRecord<?>> records = new ConcurrentHashMap<>();

    /**
     * 检查幂等状态并尝试创建处理中记录。
     *
     * @param context 幂等上下文
     * @return 幂等判定结果
     * @param <T> 快照响应数据类型
     */
    @Override
    public <T> IdempotencyDecision<T> checkOrStart(IdempotencyContext context) {
        validate(context);
        cleanupExpired();
        String mapKey = mapKey(context);
        IdempotencyRecord<?> existing = records.get(mapKey);
        if (existing == null) {
            IdempotencyRecord<T> record = IdempotencyRecord.<T>builder()
                    .scope(context.getScope())
                    .idempotencyKey(context.getIdempotencyKey())
                    .requestHash(context.getRequestHash())
                    .resultSnapshotId(newSnapshotId())
                    .status(IdempotencyRecordStatus.PROCESSING)
                    .expiresAt(Instant.now().plus(context.getTtl()))
                    .build();
            IdempotencyRecord<?> previous = records.putIfAbsent(mapKey, record);
            if (previous == null) {
                return IdempotencyDecision.proceed(record.getResultSnapshotId());
            }
            existing = previous;
        }
        return decide(context, existing);
    }

    /**
     * 保存成功响应快照。
     *
     * @param context 幂等上下文
     * @param response 成功响应
     * @param <T> 快照响应数据类型
     */
    @Override
    public <T> void complete(IdempotencyContext context, ApiResponse<T> response) {
        saveSnapshot(context, response, IdempotencyRecordStatus.COMPLETED);
    }

    /**
     * 保存失败响应快照。
     *
     * @param context 幂等上下文
     * @param response 失败响应
     * @param <T> 快照响应数据类型
     */
    @Override
    public <T> void fail(IdempotencyContext context, ApiResponse<T> response) {
        saveSnapshot(context, response, IdempotencyRecordStatus.FAILED);
    }

    /**
     * 释放处理中记录。
     *
     * @param context 幂等上下文
     */
    @Override
    public void release(IdempotencyContext context) {
        validate(context);
        records.remove(mapKey(context));
    }

    @SuppressWarnings("unchecked")
    private static <T> IdempotencyDecision<T> decide(IdempotencyContext context, IdempotencyRecord<?> existing) {
        if (!existing.getRequestHash().equals(context.getRequestHash())) {
            return IdempotencyDecision.<T>builder()
                    .status(IdempotencyDecisionStatus.CONFLICT)
                    .executable(false)
                    .response((ApiResponse<T>) conflictResponse(context, existing))
                    .resultSnapshotId(existing.getResultSnapshotId())
                    .build();
        }
        if (existing.getStatus() == IdempotencyRecordStatus.PROCESSING) {
            return IdempotencyDecision.<T>builder()
                    .status(IdempotencyDecisionStatus.PROCESSING)
                    .executable(false)
                    .response((ApiResponse<T>) processingResponse(context, existing))
                    .resultSnapshotId(existing.getResultSnapshotId())
                    .retryAfterSeconds(context.getRetryAfter().toSeconds())
                    .build();
        }
        ApiResponse<T> snapshot = (ApiResponse<T>) existing.getResultSnapshot();
        snapshot.getMeta().setIdempotencyReplay(true);
        return IdempotencyDecision.<T>builder()
                .status(IdempotencyDecisionStatus.REPLAY)
                .executable(false)
                .response(snapshot)
                .resultSnapshotId(existing.getResultSnapshotId())
                .build();
    }

    private <T> void saveSnapshot(IdempotencyContext context, ApiResponse<T> response, IdempotencyRecordStatus status) {
        validate(context);
        records.computeIfPresent(mapKey(context), (key, existing) -> {
            response.getMeta().setIdempotencyKey(context.getIdempotencyKey());
            response.getMeta().setRequestHash(context.getRequestHash());
            response.getMeta().setResultSnapshotId(existing.getResultSnapshotId());
            response.getMeta().setIdempotencyReplay(false);
            return IdempotencyRecord.<T>builder()
                    .scope(context.getScope())
                    .idempotencyKey(context.getIdempotencyKey())
                    .requestHash(context.getRequestHash())
                    .resultSnapshotId(existing.getResultSnapshotId())
                    .status(status)
                    .resultSnapshot(response)
                    .expiresAt(existing.getExpiresAt())
                    .build();
        });
    }

    private static ApiResponse<Object> conflictResponse(IdempotencyContext context, IdempotencyRecord<?> existing) {
        ApiErrorDetail error = ApiErrorDetail.builder()
                .targetType("REQUEST")
                .reason("IDEMPOTENCY_HASH_CONFLICT")
                .expected(existing.getRequestHash())
                .actual(context.getRequestHash())
                .retryable(false)
                .userMessage(CommonErrorCode.IDEMPOTENCY_CONFLICT.getMessage())
                .build();
        ApiResponse<Object> response = ApiResponseFactory.failure(CommonErrorCode.IDEMPOTENCY_CONFLICT,
                CommonErrorCode.IDEMPOTENCY_CONFLICT.getMessage(), java.util.List.of(error));
        enrichMeta(response.getMeta(), context, existing, false);
        return response;
    }

    private static ApiResponse<Object> processingResponse(IdempotencyContext context, IdempotencyRecord<?> existing) {
        ApiErrorDetail error = ApiErrorDetail.builder()
                .targetType("REQUEST")
                .reason("IDEMPOTENCY_PROCESSING")
                .retryable(true)
                .userMessage(CommonErrorCode.IDEMPOTENCY_PROCESSING.getMessage())
                .build();
        ApiResponse<Object> response = ApiResponseFactory.failure(CommonErrorCode.IDEMPOTENCY_PROCESSING,
                CommonErrorCode.IDEMPOTENCY_PROCESSING.getMessage(), java.util.List.of(error));
        enrichMeta(response.getMeta(), context, existing, false);
        return response;
    }

    private static void enrichMeta(ApiResponseMeta meta, IdempotencyContext context, IdempotencyRecord<?> existing,
            boolean replay) {
        meta.setIdempotencyKey(context.getIdempotencyKey());
        meta.setRequestHash(context.getRequestHash());
        meta.setIdempotencyReplay(replay);
        meta.setResultSnapshotId(existing.getResultSnapshotId());
    }

    private static void validate(IdempotencyContext context) {
        Assert.hasText(context.getScope(), "idempotency scope must not be blank");
        Assert.hasText(context.getIdempotencyKey(), "idempotency key must not be blank");
        Assert.hasText(context.getRequestHash(), "idempotency requestHash must not be blank");
        Assert.notNull(context.getTtl(), "idempotency ttl must not be null");
        Assert.notNull(context.getRetryAfter(), "idempotency retryAfter must not be null");
    }

    private static String mapKey(IdempotencyContext context) {
        return context.getScope() + ":" + context.getIdempotencyKey();
    }

    private static String newSnapshotId() {
        return "snap_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        records.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(now));
    }
}
