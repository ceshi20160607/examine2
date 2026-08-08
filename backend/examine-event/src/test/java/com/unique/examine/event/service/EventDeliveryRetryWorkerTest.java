package com.unique.examine.event.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.job.DurableJobFacade;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeliveryRetryWorkerTest {
    @Test
    void claimsThirtySecondsRetriesWithClaimAttemptAndSucceedsSafeResult() {
        var jobs = new FakeJobs(job("42", input(), 1));
        var worker = new EventDeliveryRetryWorker(jobs,
                (deliveryId, command, expectedAttemptCount) -> {
                    assertThat(deliveryId).isEqualTo(42);
                    assertThat(command).isEqualTo(command());
                    assertThat(expectedAttemptCount).isEqualTo(1);
                    return new MessageTemplateService.RetryOutcome(
                            42, 42L, "DELIVERED", 2, false);
                });

        worker.poll();

        assertThat(jobs.claimType).isEqualTo(EventDeliveryRetryWorker.JOB_TYPE);
        assertThat(jobs.claimLease).isEqualTo(Duration.ofSeconds(30));
        assertThat(jobs.succeeded).isEqualTo(700);
        assertThat(jobs.successVersion).isEqualTo(8);
        assertThat(jobs.result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "deliveryId", "42",
                "messageId", "42",
                "status", "DELIVERED",
                "attemptCount", 2
        ));
        assertThat(jobs.failed).isZero();
    }

    @Test
    void retryableOutcomeFailsJobWithFixedSafeFiveSecondDelay() {
        var jobs = new FakeJobs(job("42", input(), 1));
        var worker = new EventDeliveryRetryWorker(jobs,
                (deliveryId, command, expectedAttemptCount) ->
                        new MessageTemplateService.RetryOutcome(
                                42, null, "FAILED", 2, true));

        worker.poll();

        assertThat(jobs.failed).isEqualTo(700);
        assertThat(jobs.failureVersion).isEqualTo(8);
        assertThat(jobs.failureMessage)
                .isEqualTo("Event delivery retry failed safely");
        assertThat(jobs.retryDelay).isEqualTo(Duration.ofSeconds(5));
        assertThat(jobs.succeeded).isZero();
    }

    @Test
    void malformedOwnerAndPayloadFailSafelyWithoutCallingRetry() {
        var calls = new AtomicInteger();
        var jobs = new FakeJobs(job("43", input(), 1));
        var worker = new EventDeliveryRetryWorker(jobs,
                (deliveryId, command, expectedAttemptCount) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("retry must not be called");
                });

        worker.poll();

        assertThat(calls).hasValue(0);
        assertThat(jobs.failed).isEqualTo(700);
        assertThat(jobs.failureMessage)
                .isEqualTo("Event delivery retry failed safely")
                .doesNotContain("owner", "payload", "43");
        assertThat(jobs.retryDelay).isEqualTo(Duration.ofSeconds(5));

        var malformed = new LinkedHashMap<>(input());
        malformed.remove("dedupeKey");
        var malformedJobs = new FakeJobs(job("42", malformed, 1));
        new EventDeliveryRetryWorker(malformedJobs,
                (deliveryId, command, expectedAttemptCount) -> {
                    calls.incrementAndGet();
                    throw new AssertionError("retry must not be called");
                }).poll();
        assertThat(calls).hasValue(0);
        assertThat(malformedJobs.failed).isEqualTo(700);
        assertThat(malformedJobs.failureMessage)
                .isEqualTo("Event delivery retry failed safely");
    }

    @Test
    void unexpectedRetryFailureNeverLeaksItsMessageIntoDurableJob() {
        var jobs = new FakeJobs(job("42", input(), 2));
        var worker = new EventDeliveryRetryWorker(jobs,
                (deliveryId, command, expectedAttemptCount) -> {
                    throw new IllegalStateException("secret-provider-token");
                });

        worker.poll();

        assertThat(jobs.failed).isEqualTo(700);
        assertThat(jobs.failureMessage)
                .isEqualTo("Event delivery retry failed safely")
                .doesNotContain("secret-provider-token");
        assertThat(jobs.retryDelay).isEqualTo(Duration.ofSeconds(5));
    }

    private static Map<String, Object> input() {
        return EventDeliveryRetryPayload.encode(42, command());
    }

    private static ResultNotificationFacade.Command command() {
        return new ResultNotificationFacade.Command(
                10, 20, 30, 40,
                "MODULE_EXPORT_SUCCEEDED",
                Map.of("moduleCode", "purchase_order", "rows", "3"),
                new AggregateRef("MODULE_EXPORT_TASK", "50"),
                "/systems/10/workbench?module=purchase_order&task=50",
                "job-result:export:50:SUCCEEDED"
        );
    }

    private static DurableJobFacade.JobRecord job(
            String ownerId,
            Map<String, Object> input,
            int attemptCount
    ) {
        var now = LocalDateTime.of(2026, 8, 5, 8, 0);
        return new DurableJobFacade.JobRecord(
                700,
                EventDeliveryRetryWorker.JOB_TYPE,
                EventDeliveryRetryWorker.OWNER_TYPE,
                ownerId,
                10L,
                20L,
                30L,
                "RUNNING",
                1,
                input,
                Map.of(),
                attemptCount,
                2,
                now,
                now.plusSeconds(30),
                null,
                now,
                null,
                now.minusSeconds(1),
                now,
                8
        );
    }

    private static final class FakeJobs implements DurableJobFacade {
        private final JobRecord claimed;
        private String claimType;
        private Duration claimLease;
        private long succeeded;
        private long successVersion;
        private Map<String, Object> result;
        private long failed;
        private long failureVersion;
        private String failureMessage;
        private Duration retryDelay;

        private FakeJobs(JobRecord claimed) {
            this.claimed = claimed;
        }

        @Override
        public JobRecord enqueue(EnqueueCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<JobRecord> claim(String jobType, Duration lease) {
            claimType = jobType;
            claimLease = lease;
            return Optional.ofNullable(claimed);
        }

        @Override
        public JobRecord succeed(long jobId, long claimVersion,
                                 Map<String, Object> result) {
            succeeded = jobId;
            successVersion = claimVersion;
            this.result = result;
            return claimed;
        }

        @Override
        public JobRecord fail(long jobId, long claimVersion, String error,
                              Duration retryDelay) {
            failed = jobId;
            failureVersion = claimVersion;
            failureMessage = error;
            this.retryDelay = retryDelay;
            return claimed;
        }

        @Override
        public JobRecord require(long jobId) {
            return claimed;
        }
    }
}
