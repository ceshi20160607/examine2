package com.unique.examine.core.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.unique.examine.core.common.response.ApiResponse;
import com.unique.examine.core.common.response.ApiResponseFactory;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InMemoryIdempotencyServiceTest {

    private final InMemoryIdempotencyService service = new InMemoryIdempotencyService();

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldReturnProcessingBeforeFirstRequestCompletes() {
        IdempotencyContext context = context("hash-a");

        IdempotencyDecision<String> first = service.checkOrStart(context);
        IdempotencyDecision<String> second = service.checkOrStart(context);

        assertThat(first.isExecutable()).isTrue();
        assertThat(first.getStatus()).isEqualTo(IdempotencyDecisionStatus.PROCEED);
        assertThat(second.isExecutable()).isFalse();
        assertThat(second.getStatus()).isEqualTo(IdempotencyDecisionStatus.PROCESSING);
        assertThat(second.getResponse().getCode()).isEqualTo("COMMON_IDEMPOTENCY_PROCESSING");
        assertThat(second.getRetryAfterSeconds()).isEqualTo(3);
    }

    @Test
    void shouldReplayCompletedSnapshotForSameHash() {
        RequestContextHolder.set(RequestContext.builder().requestId("req_idem").traceId("trc_idem").build());
        IdempotencyContext context = context("hash-a");
        service.checkOrStart(context);
        ApiResponse<String> response = ApiResponseFactory.success("created");

        service.complete(context, response);
        IdempotencyDecision<String> replay = service.checkOrStart(context);

        assertThat(replay.isExecutable()).isFalse();
        assertThat(replay.getStatus()).isEqualTo(IdempotencyDecisionStatus.REPLAY);
        assertThat(replay.getResponse().getData()).isEqualTo("created");
        assertThat(replay.getResponse().getMeta().getIdempotencyReplay()).isTrue();
        assertThat(replay.getResponse().getMeta().getResultSnapshotId()).isNotBlank();
    }

    @Test
    void shouldReturnConflictForSameKeyDifferentHash() {
        IdempotencyContext firstContext = context("hash-a");
        IdempotencyContext conflictContext = context("hash-b");

        service.checkOrStart(firstContext);
        IdempotencyDecision<String> conflict = service.checkOrStart(conflictContext);

        assertThat(conflict.isExecutable()).isFalse();
        assertThat(conflict.getStatus()).isEqualTo(IdempotencyDecisionStatus.CONFLICT);
        assertThat(conflict.getResponse().getCode()).isEqualTo("COMMON_IDEMPOTENCY_CONFLICT");
        assertThat(conflict.getResponse().getErrors()).hasSize(1);
        assertThat(conflict.getResponse().getErrors().getFirst().getReason())
                .isEqualTo("IDEMPOTENCY_HASH_CONFLICT");
    }

    private static IdempotencyContext context(String hash) {
        return IdempotencyContext.builder()
                .scope("INTERNAL:acct:sys:tenant:RUN-004:create:idem-1")
                .idempotencyKey("idem-1")
                .requestHash(hash)
                .ttl(Duration.ofHours(24))
                .retryAfter(Duration.ofSeconds(3))
                .build();
    }
}
