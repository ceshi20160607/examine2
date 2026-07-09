package com.unique.unexamine.core.admin;

import java.util.List;

public record AdminConfigItem(
        String code,
        String label,
        String group,
        String description,
        String status,
        List<String> fields,
        String updatedAt
) {
}