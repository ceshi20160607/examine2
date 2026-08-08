package com.unique.examine.openapi.service;

import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiCallbackRetryWorkerTest {
    @Test
    void retryableExecutionRequeuesWithBoundedExponentialBackoff() {
        var jobs = new Jobs();
        var worker = new OpenApiCallbackRetryWorker(jobs,
                (id, attempt, last) -> new OpenApiCallbackDeliveryService.Execution(
                        id, OpenApiCallbackDelivery.Status.RETRYING, attempt, 7, 503,
                        "CALLBACK_HTTP_RETRYABLE"));

        worker.executeSafely(job(2, 4));

        assertThat(jobs.failed).isTrue();
        assertThat(jobs.delay).isEqualTo(Duration.ofSeconds(14));
        assertThat(jobs.succeeded).isFalse();
    }

    @Test
    void successfulOrTerminalDeliveryCompletesJobWithRedactedResult() {
        var jobs = new Jobs();
        var worker = new OpenApiCallbackRetryWorker(jobs,
                (id, attempt, last) -> new OpenApiCallbackDeliveryService.Execution(
                        id, OpenApiCallbackDelivery.Status.FAILED, attempt, 5, 400,
                        "CALLBACK_HTTP_REJECTED"));

        worker.executeSafely(job(1, 3));

        assertThat(jobs.succeeded).isTrue();
        assertThat(jobs.result).containsEntry("status", "FAILED")
                .containsEntry("attemptCount", 1)
                .doesNotContainKeys("failureCode", "httpStatus", "endpoint", "secretRef");
    }

    @Test
    void malformedScopeFailsSafelyWithoutCallingDeliveryPort() {
        var jobs = new Jobs();
        var calls = new int[1];
        var worker = new OpenApiCallbackRetryWorker(jobs, (id, attempt, last) -> {
            calls[0]++;
            throw new AssertionError("must not execute");
        });
        var malformed = new DurableJobFacade.JobRecord(9,
                OpenApiCallbackDeliveryService.JOB_TYPE, OpenApiCallbackDeliveryService.OWNER_TYPE,
                "77", 10L, 20L, 40L, "RUNNING", 0, Map.of("deliveryId", "78"), Map.of(),
                1, 3, LocalDateTime.now(), LocalDateTime.now().plusSeconds(30), null,
                LocalDateTime.now(), null, LocalDateTime.now(), LocalDateTime.now(), 2);

        worker.executeSafely(malformed);

        assertThat(calls[0]).isZero();
        assertThat(jobs.failed).isTrue();
        assertThat(jobs.delay).isEqualTo(Duration.ofSeconds(5));
    }

    private static DurableJobFacade.JobRecord job(int attempt, int maxAttempts) {
        var now = LocalDateTime.now();
        return new DurableJobFacade.JobRecord(9, OpenApiCallbackDeliveryService.JOB_TYPE,
                OpenApiCallbackDeliveryService.OWNER_TYPE, "77", 10L, 20L, 40L,
                "RUNNING", 0, Map.of("deliveryId", "77"), Map.of(), attempt, maxAttempts,
                now, now.plusSeconds(30), null, now, null, now, now, 2);
    }

    private static final class Jobs implements DurableJobFacade {
        private boolean failed;
        private boolean succeeded;
        private Duration delay;
        private Map<String, Object> result = Map.of();
        @Override public JobRecord enqueue(EnqueueCommand command) { throw new UnsupportedOperationException(); }
        @Override public Optional<JobRecord> claim(String type, Duration lease) { return Optional.empty(); }
        @Override public JobRecord succeed(long id, long version, Map<String, Object> result) {
            succeeded = true;
            this.result = Map.copyOf(result);
            return job(1, 3);
        }
        @Override public JobRecord fail(long id, long version, String error, Duration delay) {
            failed = true;
            this.delay = delay;
            return job(1, 3);
        }
        @Override public JobRecord require(long id) { throw new UnsupportedOperationException(); }
    }
}
