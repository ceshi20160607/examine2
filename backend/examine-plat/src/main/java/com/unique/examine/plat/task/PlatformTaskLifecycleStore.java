package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformTaskFacade;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Account-scoped persistence boundary for native task reads and transitions. */
interface PlatformTaskLifecycleStore {
    long countOwn(long accountId, PlatformTaskStatusFilter status);

    List<PlatformTaskApi.TaskView> findOwnPage(
            long accountId, PlatformTaskStatusFilter status, int offset, int size);

    Optional<PlatformTaskApi.TaskView> findOwnById(long accountId, long taskId);

    int transition(
            long accountId,
            long taskId,
            long expectedVersion,
            Set<PlatformTaskFacade.Status> expectedStatuses,
            PlatformTaskFacade.Status targetStatus,
            Instant transitionAt);
}
