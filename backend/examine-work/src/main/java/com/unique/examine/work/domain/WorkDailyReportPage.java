package com.unique.examine.work.domain;

import java.util.List;

public record WorkDailyReportPage(
        List<WorkDailyReport> items,
        int page,
        int size,
        long total
) {
    public WorkDailyReportPage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || size > WorkDailyReportQuery.MAX_SIZE
                || total < 0) {
            throw new IllegalArgumentException(
                    "Daily report page metadata is invalid");
        }
    }
}
