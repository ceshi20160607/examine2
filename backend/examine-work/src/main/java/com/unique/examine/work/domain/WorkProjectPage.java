package com.unique.examine.work.domain;

import java.util.List;

public record WorkProjectPage(
        List<WorkProject> items,
        int page,
        int size,
        long total
) {
    public WorkProjectPage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || size > WorkProjectQuery.MAX_SIZE
                || total < 0) {
            throw new IllegalArgumentException("Project page metadata is invalid");
        }
    }
}
