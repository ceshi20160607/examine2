package com.unique.unexamine.runtimedata.manage;

import java.time.LocalDateTime;
import java.util.List;

public record RuntimeListView(
        Long id,
        String code,
        String name,
        String search,
        List<RuntimeListFilter> filters,
        String sortField,
        String sortDirection,
        List<String> visibleFieldCodes,
        List<String> fixedFieldCodes,
        Integer pageSize,
        boolean defaultView,
        Integer version,
        LocalDateTime updatedAt) {
}
