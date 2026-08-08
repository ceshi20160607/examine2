package com.unique.examine.module.runtime.history;

import java.time.LocalDateTime;
import java.util.List;

public record RecordHistoryEntry(
        String historyId,
        String recordId,
        long recordVersion,
        String action,
        String actorMemberId,
        LocalDateTime occurredAt,
        List<RecordHistoryDiff> diff
) {
    public RecordHistoryEntry {
        diff = List.copyOf(diff);
    }
}
