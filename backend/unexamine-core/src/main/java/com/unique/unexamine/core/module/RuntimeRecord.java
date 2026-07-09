package com.unique.unexamine.core.module;

import java.util.Map;

public record RuntimeRecord(
        String recordId,
        String moduleCode,
        String title,
        String status,
        Map<String, String> values,
        String updatedAt
) {
}