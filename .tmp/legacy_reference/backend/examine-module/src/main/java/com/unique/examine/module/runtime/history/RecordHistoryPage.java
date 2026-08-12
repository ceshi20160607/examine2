package com.unique.examine.module.runtime.history;

import java.util.List;

public record RecordHistoryPage(
        List<RecordHistoryEntry> items,
        int page,
        int size,
        long total
) {
    public RecordHistoryPage {
        items = List.copyOf(items);
    }
}
