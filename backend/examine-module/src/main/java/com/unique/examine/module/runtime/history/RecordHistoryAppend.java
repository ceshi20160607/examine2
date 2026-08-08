package com.unique.examine.module.runtime.history;

import java.time.LocalDateTime;
import java.util.List;

public record RecordHistoryAppend(
        long historyId,
        long systemId,
        long tenantId,
        long recordId,
        long recordVersion,
        String action,
        Long actorMemberId,
        LocalDateTime occurredAt,
        List<RecordHistoryDiff> diff
) {
    public RecordHistoryAppend {
        if (historyId <= 0 || systemId <= 0 || tenantId <= 0 || recordId <= 0
                || recordVersion < 0) {
            throw new IllegalArgumentException("History scope IDs and record version are invalid");
        }
        if (action == null || !action.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
            throw new IllegalArgumentException("History action is invalid");
        }
        if (actorMemberId != null && actorMemberId <= 0) {
            throw new IllegalArgumentException("actorMemberId must be positive");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
        diff = List.copyOf(diff);
    }
}
