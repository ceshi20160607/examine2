package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordMutationSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RecordHistoryIdempotencyTest {
    @Test
    void replayReturnsBeforeMutationAndDoesNotAppendAnotherHistoryRow() throws Exception {
        var mapper = new ObjectMapper();
        var repository = new InMemoryRecordHistoryRepository();
        var writer = new RecordHistoryWriter(
                repository,
                new RecordHistoryDiffCodec(mapper),
                new IdService());
        var support = new RecordMutationSupport(
                new InMemoryIdempotency(),
                new NoopAudit(),
                event -> 1L,
                mapper,
                writer);
        var runs = new AtomicInteger();
        var session = new RuntimeSession(
                9,
                1,
                10,
                2L,
                Set.of("system.runtime.access", "module.work_order.update"));
        var before = record("old", "plaintext-before", "********");
        var after = record("new", "plaintext-after", "********");

        var first = support.idempotent(
                "1:2:3:update",
                "same-key",
                Map.of("title", "new"),
                String.class,
                200,
                () -> {
                    runs.incrementAndGet();
                    support.changed(
                            session,
                            3,
                            2,
                            "RECORD_UPDATED",
                            before,
                            after,
                            "request-1",
                            "trace-1",
                            Set.of("secret"));
                    return "done";
                });
        var replay = support.idempotent(
                "1:2:3:update",
                "same-key",
                Map.of("title", "new"),
                String.class,
                200,
                () -> {
                    runs.incrementAndGet();
                    return "must-not-run";
                });

        assertThat(first).isEqualTo("done");
        assertThat(replay).isEqualTo("done");
        assertThat(runs).hasValue(1);
        assertThat(repository.size()).isEqualTo(1);
        var persistedDiff = mapper.writeValueAsString(repository.entries().getFirst().diff());
        assertThat(persistedDiff)
                .contains("\"masked\":true")
                .doesNotContain("plaintext-before")
                .doesNotContain("plaintext-after");
    }

    private static RecordRuntimeViews.RecordDetail record(
            String title,
            String secret,
            String display
    ) {
        return new RecordRuntimeViews.RecordDetail(
                "3",
                "WO-3",
                1,
                "ACTIVE",
                title,
                "100",
                List.of(new RecordRuntimeViews.FieldValue(
                        "secret",
                        "Secret",
                        "SECRET",
                        secret,
                        display)),
                List.of());
    }

    private static final class InMemoryIdempotency implements IdempotencyFacade {
        private IdempotencyRecord record;

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(record);
        }

        @Override
        public long begin(
                String scopeType,
                String scopeKey,
                String key,
                String requestHash,
                Duration ttl
        ) {
            record = new IdempotencyRecord(51, requestHash, "IN_PROGRESS", null);
            return 51;
        }

        @Override
        public void complete(
                long id,
                int httpStatus,
                String responseCode,
                String responseBody
        ) {
            record = new IdempotencyRecord(
                    record.id(),
                    record.requestHash(),
                    "COMPLETED",
                    responseBody);
        }
    }

    private static final class NoopAudit implements OperationAuditFacade {
        @Override
        public void recordSuccess(OperationAudit audit) {
        }

        @Override
        public void recordDenied(OperationAudit audit) {
        }

        @Override
        public void recordFailed(OperationAudit audit) {
        }
    }
}
