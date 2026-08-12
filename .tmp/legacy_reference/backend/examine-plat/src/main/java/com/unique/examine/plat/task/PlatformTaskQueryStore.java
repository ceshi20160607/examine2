package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformOperationsQueryFacade;

import java.util.List;

/** Read-only, self-scoped projection boundary for personal platform tasks. */
interface PlatformTaskQueryStore {
    List<PlatformOperationsQueryFacade.PersonalTask> findOwnTasks(
            long accountId, int limit);
}
