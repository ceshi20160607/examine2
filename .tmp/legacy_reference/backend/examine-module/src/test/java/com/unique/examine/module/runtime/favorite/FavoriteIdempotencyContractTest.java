package com.unique.examine.module.runtime.favorite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordMutationSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteIdempotencyContractTest {
    @Test
    void sameKeyReplaysCreatedFlagAndItemWithoutRerunningMutation() {
        var idempotency = new InMemoryIdempotency();
        var recordMutations = new RecordMutationSupport(
                idempotency, null, null, new ObjectMapper(), null);
        var support = new FavoriteMutationSupport(recordMutations, null, null);
        var session = new RuntimeSession(1, 2, 3, 4L, Set.of());
        var request = new FavoriteRequestParser.CreateRequest("MODULE", "work_order", null);
        var runs = new AtomicInteger();

        var first = support.idempotent(
                session, "POST", "/runtime/favorites", "favorite-key", request,
                FavoriteService.CreateResult.class, 201,
                () -> {
                    runs.incrementAndGet();
                    return new FavoriteService.CreateResult(new FavoriteViews.FavoriteItem(
                            "9", 0, "MODULE", "work_order", null,
                            "work_order", null, "2026-07-27T10:00:00"), true);
                });
        var replay = support.idempotent(
                session, "POST", "/runtime/favorites", "favorite-key",
                new FavoriteRequestParser.CreateRequest("MODULE", "work_order", null),
                FavoriteService.CreateResult.class, 201,
                () -> {
                    runs.incrementAndGet();
                    throw new AssertionError("replay must not create another favorite");
                });

        assertThat(first.created()).isTrue();
        assertThat(replay).isEqualTo(first);
        assertThat(runs).hasValue(1);
        assertThat(idempotency.completions).hasValue(1);
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
            record = new IdempotencyRecord(91, requestHash, "IN_PROGRESS", null);
            return 91;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            completions.incrementAndGet();
            record = new IdempotencyRecord(record.id(), record.requestHash(), "COMPLETED", responseBody);
        }
    }
}
