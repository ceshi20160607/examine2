package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecordBatchTransferIdempotencyTest {
    @Test
    void sameKeyAndWholeTransferReplayOnceWhileDifferentTargetConflicts() {
        var idempotency = new InMemoryIdempotency();
        var support = new RecordMutationSupport(
                idempotency,
                null,
                null,
                new ObjectMapper(),
                null);
        var runs = new AtomicInteger();
        var request = request("50");
        var first = support.idempotent(
                "1:2:3:work_order:batch-transfer",
                "batch-key",
                request,
                RecordRuntimeViews.BatchMutationResponse.class,
                200,
                () -> {
                    runs.incrementAndGet();
                    return response();
                });
        var replay = support.idempotent(
                "1:2:3:work_order:batch-transfer",
                "batch-key",
                request("50"),
                RecordRuntimeViews.BatchMutationResponse.class,
                200,
                () -> {
                    runs.incrementAndGet();
                    throw new AssertionError("replay must not execute the transfer");
                });
        var conflict = catchThrowableOfType(
                () -> support.idempotent(
                        "1:2:3:work_order:batch-transfer",
                        "batch-key",
                        request("51"),
                        RecordRuntimeViews.BatchMutationResponse.class,
                        200,
                        () -> response()),
                BusinessException.class);

        assertThat(replay).isEqualTo(first);
        assertThat(runs).hasValue(1);
        assertThat(conflict.code()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(idempotency.completions).hasValue(1);
    }

    private static RecordRuntimeViews.BatchTransferRequest request(String targetMemberId) {
        return new RecordRuntimeViews.BatchTransferRequest(
                List.of(
                        new RecordRuntimeViews.BatchRecordRef("20", 2),
                        new RecordRuntimeViews.BatchRecordRef("3", 4)),
                targetMemberId);
    }

    private static RecordRuntimeViews.BatchMutationResponse response() {
        return new RecordRuntimeViews.BatchMutationResponse(true, List.of(
                RecordRuntimeViews.BatchMutationItem.applied("20", 3, "ACTIVE"),
                RecordRuntimeViews.BatchMutationItem.applied("3", 5, "ACTIVE")));
    }

    private static final class InMemoryIdempotency implements IdempotencyFacade {
        private IdempotencyRecord record;
        private final AtomicInteger completions = new AtomicInteger();

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(record);
        }

        @Override
        public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) {
            record = new IdempotencyRecord(81, requestHash, "IN_PROGRESS", null);
            return 81;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            completions.incrementAndGet();
            record = new IdempotencyRecord(record.id(), record.requestHash(), "COMPLETED", responseBody);
        }
    }
}
