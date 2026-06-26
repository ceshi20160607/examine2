package com.unique.examine.core.api;

import java.util.List;

/**
 * Unified page request for list APIs.
 */
public record PageRequest(
        int pageNo,
        int pageSize,
        String keyword,
        List<FilterCriterion> filters,
        List<SortCriterion> sorts
) {
}

