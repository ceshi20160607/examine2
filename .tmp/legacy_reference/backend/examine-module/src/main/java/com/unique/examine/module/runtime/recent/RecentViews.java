package com.unique.examine.module.runtime.recent;

import java.util.List;

public final class RecentViews {
    private RecentViews() {
    }

    public record RecentItem(
            String recentId,
            String moduleCode,
            String recordId,
            String displayLabel,
            String status,
            long accessCount,
            String lastAccessedAt
    ) {
    }

    public record RecentPage(List<RecentItem> items, int page, int size, long total) {
        public RecentPage {
            items = List.copyOf(items);
        }
    }
}
