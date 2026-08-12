package com.unique.examine.work.port;

import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskPage;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.domain.WorkTaskMetricFacts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkTaskRepository {
    long nextId();

    Optional<WorkTask> findById(long systemId, long tenantId, long id);

    List<WorkTask> findAll(long systemId, long tenantId);

    List<WorkTask> findParticipating(long systemId, long tenantId, long memberId);

    WorkTaskPage findPage(
            long systemId,
            long tenantId,
            long memberId,
            WorkTaskQuery query);

    WorkTaskMetricFacts metrics(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now);

    default WorkTaskMetricFacts metrics(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Long projectId,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        if (projectId != null) {
            throw new UnsupportedOperationException(
                    "Project-scoped metrics are not implemented");
        }
        return metrics(
                systemId, tenantId, memberId, tenantWide,
                fromInclusive, toExclusive, now);
    }

    WorkTask save(WorkTask task);
}
