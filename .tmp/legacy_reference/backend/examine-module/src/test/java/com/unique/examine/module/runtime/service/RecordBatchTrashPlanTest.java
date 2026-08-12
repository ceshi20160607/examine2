package com.unique.examine.module.runtime.service;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecordBatchTrashPlanTest {
    private static final Set<String> TRASH_SOURCES =
            Set.of("DRAFT", "ACTIVE", "ARCHIVED", "EXPIRED");

    @Test
    void acceptsZeroVersionButRejectsNegativeVersion() {
        var plan = RecordBatchMutationPlan.from(request(ref("7", 0)), 0, "trash");
        assertThat(plan.requestOrder().getFirst().expectedVersion()).isZero();

        var exception = catchThrowableOfType(
                () -> RecordBatchMutationPlan.from(request(ref("7", -1)), 0, "trash"),
                BusinessException.class);
        assertThat(exception.code()).isEqualTo("BATCH_INVALID");
        assertThat(exception.status().value()).isEqualTo(422);
    }

    @Test
    void acceptsEveryTrashSourceAndAppliesInLockOrderButRespondsInRequestOrder() {
        var plan = RecordBatchMutationPlan.from(request(
                ref("20", 0),
                ref("3", 4),
                ref("11", 6),
                ref("8", 2)), 0, "trash");
        var prepared = plan.preflight(List.of(
                state(20, 0, "DRAFT"),
                state(3, 4, "ACTIVE"),
                state(11, 6, "ARCHIVED"),
                state(8, 2, "EXPIRED")), TRASH_SOURCES);
        var appliedOrder = new ArrayList<Long>();

        var response = prepared.apply("TRASHED", recordId -> {
            appliedOrder.add(recordId);
            return recordId + 1;
        });

        assertThat(appliedOrder).containsExactly(3L, 8L, 11L, 20L);
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3", "11", "8");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::status)
                .containsOnly("TRASHED");
    }

    @Test
    void oneMissingStaleOrInvalidRecordPreventsEveryApply() {
        var plan = RecordBatchMutationPlan.from(request(
                ref("20", 0),
                ref("3", 4),
                ref("11", 6),
                ref("8", 2)), 0, "trash");
        var writes = new AtomicInteger();

        var exception = catchThrowableOfType(
                () -> {
                    var prepared = plan.preflight(List.of(
                            state(20, 9, "DRAFT"),
                            state(11, 6, "TRASHED"),
                            state(8, 2, "ACTIVE")), TRASH_SOURCES);
                    prepared.apply("TRASHED", recordId -> {
                        writes.incrementAndGet();
                        return 1L;
                    });
                },
                BusinessException.class);

        assertThat(writes).hasValue(0);
        assertThat(exception.code()).isEqualTo("BATCH_PRECONDITION_FAILED");
        var response = (RecordRuntimeViews.BatchMutationResponse) exception.data();
        assertThat(response.allApplied()).isFalse();
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3", "11");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::resultCode)
                .containsExactly("VERSION_STALE", "RECORD_NOT_FOUND", "STATE_INVALID");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::currentVersion)
                .containsExactly(9L, null, 6L);
    }

    private static RecordRuntimeViews.BatchCommandRequest request(RecordRuntimeViews.BatchRecordRef... refs) {
        return new RecordRuntimeViews.BatchCommandRequest(List.of(refs));
    }

    private static RecordRuntimeViews.BatchRecordRef ref(String id, long version) {
        return new RecordRuntimeViews.BatchRecordRef(id, version);
    }

    private static RecordBatchMutationPlan.RecordState state(long id, long version, String status) {
        return new RecordBatchMutationPlan.RecordState(id, version, status);
    }
}
