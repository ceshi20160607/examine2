package com.unique.examine.work.port;

import com.unique.examine.work.domain.WorkTaskReminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkTaskReminderRepository {
    WorkTaskReminder schedule(
            long systemId,
            long tenantId,
            long taskId,
            Instant scheduledAt,
            Instant now);

    Optional<WorkTaskReminder> find(
            long systemId,
            long tenantId,
            long taskId,
            int generation);

    Optional<WorkTaskReminder> findLatest(
            long systemId,
            long tenantId,
            long taskId);

    Optional<WorkTaskReminder> cancelLatestLive(
            long systemId,
            long tenantId,
            long taskId,
            Instant now);

    WorkTaskReminder save(WorkTaskReminder reminder);

    List<WorkTaskReminder> claimDue(
            String owner,
            String tokenHash,
            Instant now,
            Instant leaseUntil,
            int limit);
}
