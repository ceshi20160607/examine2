package com.unique.examine.core.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class DurableJobService implements DurableJobFacade {
    private static final Set<String> TERMINAL = Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED");

    private final JobStore store;
    private final IdService ids;
    private final ObjectMapper mapper;
    private final List<JobSignalPublisher> publishers;

    public DurableJobService(JobStore store, IdService ids, ObjectMapper mapper, List<JobSignalPublisher> publishers) {
        this.store = store;
        this.ids = ids;
        this.mapper = mapper;
        this.publishers = List.copyOf(publishers);
    }

    @Override
    @Transactional
    public JobRecord enqueue(EnqueueCommand command) {
        validate(command);
        var now = LocalDateTime.now();
        var job = new JobRecord(ids.nextId(), command.jobType().trim(), command.ownerType().trim(),
                blank(command.ownerId()), command.systemId(), command.tenantId(), command.requestedBy(),
                "QUEUED", 0, immutable(command.input()), Map.of(), 0, command.maxAttempts(), now,
                null, null, null, null, now, now, 0);
        store.insert(job);
        afterCommit(() -> publishers.forEach(publisher -> publisher.publish(job.id(), job.jobType())));
        return job;
    }

    @Override
    @Transactional
    public Optional<JobRecord> claim(String jobType, Duration lease) {
        if (jobType == null || jobType.isBlank() || lease == null || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("jobType and positive lease are required");
        }
        var now = LocalDateTime.now();
        for (var candidate : store.claimCandidates(jobType.trim(), now, 20)) {
            var claimed = store.claim(candidate.id(), candidate.version(), now, now.plus(lease));
            if (claimed.isPresent()) return claimed;
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public JobRecord succeed(long jobId, long claimVersion, Map<String, Object> result) {
        return store.finish(jobId, claimVersion, "SUCCEEDED", 100, write(result), null,
                        LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now())
                .orElseThrow(() -> stale(jobId));
    }

    @Override
    @Transactional
    public JobRecord fail(long jobId, long claimVersion, String error, Duration retryDelay) {
        var current = require(jobId);
        if (!"RUNNING".equals(current.status()) || current.version() != claimVersion) throw stale(jobId);
        var retry = current.attemptCount() < current.maxAttempts();
        var now = LocalDateTime.now();
        var status = retry ? "QUEUED" : "FAILED";
        var message = truncate(error == null || error.isBlank() ? "JOB_EXECUTION_FAILED" : error, 1000);
        var next = retry ? now.plus(retryDelay == null ? Duration.ofSeconds(5) : retryDelay) : now;
        return store.finish(jobId, claimVersion, status, retry ? Math.max(1, current.progressPercent()) : 100,
                        null, message, next, retry ? null : now, now)
                .orElseThrow(() -> stale(jobId));
    }

    @Override
    public JobRecord require(long jobId) {
        if (jobId <= 0) throw notFound();
        return store.find(jobId).orElseThrow(DurableJobService::notFound);
    }

    private static void validate(EnqueueCommand command) {
        Objects.requireNonNull(command, "command is required");
        if (command.jobType() == null || !command.jobType().matches("^[A-Z][A-Z0-9_]{1,63}$")
                || command.ownerType() == null || !command.ownerType().matches("^[A-Z][A-Z0-9_]{1,63}$")
                || command.maxAttempts() < 1 || command.maxAttempts() > 10) {
            throw new IllegalArgumentException("Durable job type, owner type and 1..10 attempts are required");
        }
    }

    private static Map<String, Object> immutable(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }

    private String write(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Durable job result must be JSON serializable", exception);
        }
    }

    private static void afterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { runnable.run(); }
        });
    }

    private static BusinessException stale(long jobId) {
        return new BusinessException("JOB_CLAIM_STALE", "Job claim is stale: " + jobId, HttpStatus.CONFLICT);
    }

    private static BusinessException notFound() {
        return new BusinessException("JOB_NOT_FOUND", "Job was not found", HttpStatus.NOT_FOUND);
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
