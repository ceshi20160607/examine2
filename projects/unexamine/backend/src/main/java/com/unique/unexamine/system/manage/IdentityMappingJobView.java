package com.unique.unexamine.system.manage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 身份映射同步结果；进度由最终逐项结果数推导。 */
public record IdentityMappingJobView(
        Long id,
        String status,
        long progressCurrent,
        long progressTotal,
        Map<String, Object> summary,
        List<IdentityMappingJobItemView> items,
        LocalDateTime createTime,
        LocalDateTime finishTime) {
}
