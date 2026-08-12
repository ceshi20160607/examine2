package com.unique.examine.openapi.service;

import com.unique.examine.core.job.DurableJobFacade;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class OpenApiCallbackRetryWorker {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final DurableJobFacade jobs;
    private final DeliveryPort deliveries;

    public OpenApiCallbackRetryWorker(DurableJobFacade jobs, OpenApiCallbackDeliveryService deliveries) {
        this(jobs, deliveries == null ? null : deliveries::execute);
    }

    OpenApiCallbackRetryWorker(DurableJobFacade jobs, DeliveryPort deliveries) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
    }

    @Scheduled(fixedDelayString = "${examine.jobs.openapi-callback.poll-delay-ms:250}")
    public void poll() {
        jobs.claim(OpenApiCallbackDeliveryService.JOB_TYPE, LEASE).ifPresent(this::executeSafely);
    }

    void executeSafely(DurableJobFacade.JobRecord job) {
        try {
            var deliveryId = require(job);
            var outcome = deliveries.execute(deliveryId, job.attemptCount(),
                    job.attemptCount() >= job.maxAttempts());
            if (outcome.retryable()) {
                jobs.fail(job.id(), job.version(), "Callback delivery retry is pending",
                        backoff(outcome.baseBackoffSeconds(), outcome.attemptCount()));
            } else {
                jobs.succeed(job.id(), job.version(), Map.of(
                        "deliveryId", Long.toString(deliveryId),
                        "status", outcome.status().name(),
                        "attemptCount", outcome.attemptCount()));
            }
        } catch (RuntimeException failure) {
            jobs.fail(job.id(), job.version(), "Callback delivery failed safely", Duration.ofSeconds(5));
        }
    }

    static Duration backoff(int baseSeconds, int attemptCount) {
        var factor = 1L << Math.min(10, Math.max(0, attemptCount - 1));
        return Duration.ofSeconds(Math.min(3600L, Math.multiplyExact(baseSeconds, factor)));
    }

    private static long require(DurableJobFacade.JobRecord job) {
        if (job == null || !OpenApiCallbackDeliveryService.JOB_TYPE.equals(job.jobType())
                || !OpenApiCallbackDeliveryService.OWNER_TYPE.equals(job.ownerType())
                || job.attemptCount() < 1 || job.attemptCount() > job.maxAttempts()
                || job.systemId() == null || job.systemId() <= 0
                || job.tenantId() == null || job.tenantId() <= 0
                || job.requestedBy() == null || job.requestedBy() <= 0) {
            throw new IllegalArgumentException("OpenAPI callback job is invalid");
        }
        var encoded = job.input().get("deliveryId");
        var value = Long.parseLong(String.valueOf(encoded));
        if (value <= 0 || !Long.toString(value).equals(job.ownerId())
                || !job.input().keySet().equals(java.util.Set.of("deliveryId"))) {
            throw new IllegalArgumentException("OpenAPI callback job scope is invalid");
        }
        return value;
    }

    @FunctionalInterface
    interface DeliveryPort {
        OpenApiCallbackDeliveryService.Execution execute(
                long deliveryId, int attemptNumber, boolean finalAttempt);
    }
}
