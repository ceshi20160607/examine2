package com.unique.examine.module.runtime.history;

import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RuntimeRecordHistoryAccess implements RecordHistoryAccess {
    private final RecordRuntimeService records;

    public RuntimeRecordHistoryAccess(RecordRuntimeService records) {
        this.records = records;
    }

    @Override
    public Set<String> requireViewAndCurrentProjection(
            RuntimeSession session,
            String moduleCode,
            long recordId
    ) {
        // Detail owns the canonical tenant and data-scope not-found semantics.
        records.detail(session, moduleCode, recordId);
        return records.schema(session, moduleCode).fields().stream()
                .map(field -> field.fieldCode())
                .collect(Collectors.toUnmodifiableSet());
    }
}
