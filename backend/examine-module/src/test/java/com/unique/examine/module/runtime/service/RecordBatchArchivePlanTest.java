package com.unique.examine.module.runtime.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecordBatchArchivePlanTest {
    @Test
    void validatesBoundsPositiveValuesAndUniqueness() {
        assertBatchInvalid(new RecordRuntimeViews.BatchCommandRequest(List.of()));
        assertBatchInvalid(new RecordRuntimeViews.BatchCommandRequest(List.of(
                new RecordRuntimeViews.BatchRecordRef("0", 1))));
        assertBatchInvalid(new RecordRuntimeViews.BatchCommandRequest(List.of(
                new RecordRuntimeViews.BatchRecordRef("1", 0))));
        assertBatchInvalid(new RecordRuntimeViews.BatchCommandRequest(List.of(
                new RecordRuntimeViews.BatchRecordRef("1", 1),
                new RecordRuntimeViews.BatchRecordRef("1", 2))));
        assertBatchInvalid(new RecordRuntimeViews.BatchCommandRequest(
                IntStream.rangeClosed(1, 201)
                        .mapToObj(id -> new RecordRuntimeViews.BatchRecordRef(
                                Integer.toString(id), 1))
                        .toList()));
    }

    @Test
    void locksAndAppliesInNumericOrderButReturnsInRequestOrder() {
        var plan = RecordBatchMutationPlan.from(request(
                ref("20", 2),
                ref("3", 4),
                ref("11", 6)), 1, "archive");
        assertThat(plan.lockOrder()).containsExactly(3L, 11L, 20L);

        var prepared = plan.preflight(List.of(
                state(20, 2, "ACTIVE"),
                state(3, 4, "ACTIVE"),
                state(11, 6, "ACTIVE")), Set.of("ACTIVE"));
        var appliedOrder = new ArrayList<Long>();
        var response = prepared.apply("ARCHIVED", recordId -> {
            appliedOrder.add(recordId);
            return recordId + 100;
        });

        assertThat(appliedOrder).containsExactly(3L, 11L, 20L);
        assertThat(response.allApplied()).isTrue();
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3", "11");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::newVersion)
                .containsExactly(120L, 103L, 111L);
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::status)
                .containsOnly("ARCHIVED");
    }

    @Test
    void completePreflightReportsOnlyFailuresInRequestOrderBeforeAnyApplyPhaseExists() {
        var plan = RecordBatchMutationPlan.from(request(
                ref("20", 2),
                ref("3", 4),
                ref("11", 6),
                ref("30", 9)), 1, "archive");
        var writes = new AtomicInteger();

        var exception = catchThrowableOfType(
                () -> {
                    var prepared = plan.preflight(List.of(
                            state(11, 6, "ARCHIVED"),
                            state(20, 8, "ACTIVE"),
                            state(30, 9, "ACTIVE")), Set.of("ACTIVE"));
                    prepared.apply("ARCHIVED", recordId -> {
                        writes.incrementAndGet();
                        return 10L;
                    });
                },
                BusinessException.class);

        assertThat(exception.code()).isEqualTo("BATCH_PRECONDITION_FAILED");
        assertThat(exception.status().value()).isEqualTo(409);
        assertThat(writes).hasValue(0);
        var response = (RecordRuntimeViews.BatchMutationResponse) exception.data();
        assertThat(response.allApplied()).isFalse();
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3", "11");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::resultCode)
                .containsExactly("VERSION_STALE", "RECORD_NOT_FOUND", "STATE_INVALID");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::currentVersion)
                .containsExactly(8L, null, 6L);
    }

    private static void assertBatchInvalid(RecordRuntimeViews.BatchCommandRequest request) {
        var exception = catchThrowableOfType(
                () -> RecordBatchMutationPlan.from(request, 1, "archive"),
                BusinessException.class);
        assertThat(exception.code()).isEqualTo("BATCH_INVALID");
        assertThat(exception.status().value()).isEqualTo(422);
    }

    private static RecordRuntimeViews.BatchCommandRequest request(
            RecordRuntimeViews.BatchRecordRef... items
    ) {
        return new RecordRuntimeViews.BatchCommandRequest(List.of(items));
    }

    private static RecordRuntimeViews.BatchRecordRef ref(String id, long version) {
        return new RecordRuntimeViews.BatchRecordRef(id, version);
    }

    private static RecordBatchMutationPlan.RecordState state(long id, long version, String status) {
        return new RecordBatchMutationPlan.RecordState(id, version, status);
    }
}
