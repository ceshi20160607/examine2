package com.unique.examine.core.job;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

public interface DurableJobFacade {
    JobRecord enqueue(EnqueueCommand command);

    Optional<JobRecord> claim(String jobType, Duration lease);

    JobRecord succeed(long jobId, long claimVersion, Map<String, Object> result);

    JobRecord fail(long jobId, long claimVersion, String error, Duration retryDelay);

    JobRecord require(long jobId);

    record EnqueueCommand(
            String jobType,
            String ownerType,
            String ownerId,
            Long systemId,
            Long tenantId,
            Long requestedBy,
            Map<String, Object> input,
            int maxAttempts
    ) { }

    record JobRecord(
            long id,
            String jobType,
            String ownerType,
            String ownerId,
            Long systemId,
            Long tenantId,
            Long requestedBy,
            String status,
            int progressPercent,
            Map<String, Object> input,
            Map<String, Object> result,
            int attemptCount,
            int maxAttempts,
            LocalDateTime availableAt,
            LocalDateTime leaseUntil,
            String lastError,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version
    ) { }
}
