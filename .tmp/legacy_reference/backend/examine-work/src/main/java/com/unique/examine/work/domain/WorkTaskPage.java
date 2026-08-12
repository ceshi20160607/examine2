package com.unique.examine.work.domain;

import java.util.List;

public record WorkTaskPage(
        List<WorkTask> items,
        int page,
        int size,
        long total
) {
    public WorkTaskPage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || size > WorkTaskQuery.MAX_SIZE || total < 0) {
            throw new IllegalArgumentException("Task page metadata is invalid");
        }
    }
}
