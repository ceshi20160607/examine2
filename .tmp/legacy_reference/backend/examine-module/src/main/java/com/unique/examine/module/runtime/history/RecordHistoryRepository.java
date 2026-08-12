package com.unique.examine.module.runtime.history;

public interface RecordHistoryRepository {
    String append(RecordHistoryAppend history);

    RecordHistoryPage page(long systemId, long tenantId, long recordId, int page, int size);
}
