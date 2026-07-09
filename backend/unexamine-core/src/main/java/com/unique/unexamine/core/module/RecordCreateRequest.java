package com.unique.unexamine.core.module;

import java.util.Map;

public record RecordCreateRequest(
        String title,
        Map<String, String> values
) {
}