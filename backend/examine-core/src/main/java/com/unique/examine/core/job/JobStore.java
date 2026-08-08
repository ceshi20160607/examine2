package com.unique.examine.core.job;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobStore {
    void insert(DurableJobFacade.JobRecord job);

    List<DurableJobFacade.JobRecord> claimCandidates(String jobType, LocalDateTime now, int limit);

    Optional<DurableJobFacade.JobRecord> claim(
            long jobId, long expectedVersion, LocalDateTime now, LocalDateTime leaseUntil);

    Optional<DurableJobFacade.JobRecord> finish(
            long jobId, long claimVersion, String status, int progressPercent,
            String resultJson, String error, LocalDateTime availableAt,
            LocalDateTime finishedAt, LocalDateTime now);

    Optional<DurableJobFacade.JobRecord> find(long jobId);
}
