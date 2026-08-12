package com.unique.examine.event.service;

import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.job.DurableJobFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Claims and executes bounded retries for Event-owned delivery logs. */
@Component
public final class EventDeliveryRetryWorker {
    public static final String JOB_TYPE = "EVENT_DELIVERY_RETRY";
    public static final String OWNER_TYPE = "EVENT_DELIVERY";
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private static final String SAFE_FAILURE = "Event delivery retry failed safely";
    private static final Set<String> SAFE_STATUSES = Set.of(
            "PENDING", "DELIVERED", "SKIPPED", "FAILED");

    private final DurableJobFacade jobs;
    private final RetryPort retries;

    @Autowired
    public EventDeliveryRetryWorker(
            DurableJobFacade jobs,
            MessageTemplateService templates
    ) {
        this(jobs, templates == null ? null : templates::retry);
    }

    EventDeliveryRetryWorker(DurableJobFacade jobs, RetryPort retries) {
        if (jobs == null || retries == null) {
            throw new IllegalArgumentException(
                    "Event delivery retry worker dependencies are required");
        }
        this.jobs = jobs;
        this.retries = retries;
    }

    @Scheduled(fixedDelayString =
            "${examine.jobs.event-delivery-retry.poll-delay-ms:250}")
    public void poll() {
        jobs.claim(JOB_TYPE, CLAIM_LEASE).ifPresent(this::executeSafely);
    }

    private void executeSafely(DurableJobFacade.JobRecord job) {
        final MessageTemplateService.RetryOutcome outcome;
        try {
            var decoded = requireJob(job);
            outcome = retries.retry(
                    decoded.deliveryId(),
                    decoded.command(),
                    job.attemptCount()
            );
            requireOutcome(decoded.deliveryId(), outcome);
        } catch (RuntimeException unexpected) {
            jobs.fail(job.id(), job.version(), SAFE_FAILURE, RETRY_DELAY);
            return;
        }
        if (outcome.retryable()) {
            jobs.fail(job.id(), job.version(), SAFE_FAILURE, RETRY_DELAY);
            return;
        }
        jobs.succeed(job.id(), job.version(), safeResult(outcome));
    }

    private static EventDeliveryRetryPayload.Decoded requireJob(
            DurableJobFacade.JobRecord job
    ) {
        if (job == null || !JOB_TYPE.equals(job.jobType())
                || !OWNER_TYPE.equals(job.ownerType())
                || job.attemptCount() <= 0
                || job.systemId() == null || job.systemId() <= 0
                || job.tenantId() == null || job.tenantId() <= 0
                || job.requestedBy() == null || job.requestedBy() <= 0) {
            throw new IllegalArgumentException("Unsupported Event delivery retry job");
        }
        var decoded = EventDeliveryRetryPayload.decode(job.input());
        final long ownerDeliveryId;
        try {
            ownerDeliveryId = Long.parseLong(job.ownerId());
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException(
                    "Event delivery retry owner is invalid");
        }
        var command = decoded.command();
        if (ownerDeliveryId <= 0 || ownerDeliveryId != decoded.deliveryId()
                || job.systemId() != command.systemId()
                || job.tenantId() != command.tenantId()
                || job.requestedBy() != command.senderMemberId()) {
            throw new IllegalArgumentException(
                    "Event delivery retry scope is inconsistent");
        }
        return decoded;
    }

    private static void requireOutcome(
            long deliveryId,
            MessageTemplateService.RetryOutcome outcome
    ) {
        if (outcome == null || outcome.deliveryId() != deliveryId
                || outcome.attemptCount() <= 0 || outcome.attemptCount() > 3
                || !SAFE_STATUSES.contains(outcome.status())
                || outcome.messageId() != null && outcome.messageId() <= 0) {
            throw new IllegalArgumentException(
                    "Event delivery retry outcome is invalid");
        }
    }

    private static Map<String, Object> safeResult(
            MessageTemplateService.RetryOutcome outcome
    ) {
        var result = new LinkedHashMap<String, Object>();
        result.put("deliveryId", Long.toString(outcome.deliveryId()));
        result.put("status", outcome.status());
        result.put("attemptCount", outcome.attemptCount());
        if (outcome.messageId() != null) {
            result.put("messageId", Long.toString(outcome.messageId()));
        }
        return Map.copyOf(result);
    }

    @FunctionalInterface
    interface RetryPort {
        MessageTemplateService.RetryOutcome retry(
                long deliveryId,
                ResultNotificationFacade.Command command,
                int expectedAttemptCount
        );
    }
}
