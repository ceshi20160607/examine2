package com.unique.examine.module.runtime.history;

import com.unique.examine.module.runtime.security.RuntimeSession;

import java.util.Set;

@FunctionalInterface
public interface RecordHistoryAccess {
    Set<String> requireViewAndCurrentProjection(
            RuntimeSession session,
            String moduleCode,
            long recordId
    );
}
