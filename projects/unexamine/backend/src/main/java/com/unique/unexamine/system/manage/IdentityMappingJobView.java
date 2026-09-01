package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record IdentityMappingJobView(
        Long id,
        String status,
        long progressCurrent,
        long progressTotal,
        Map<String, Object> summary,
        List<IdentityMappingJobItemView> items,
        LocalDateTime createdAt,
        LocalDateTime finishedAt) {
}
