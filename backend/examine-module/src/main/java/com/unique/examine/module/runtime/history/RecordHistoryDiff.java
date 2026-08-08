package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.Objects;

public record RecordHistoryDiff(
        String fieldCode,
        JsonNode beforeValue,
        JsonNode afterValue,
        boolean masked
) {
    public RecordHistoryDiff {
        if (fieldCode == null || fieldCode.isBlank() || fieldCode.length() > 128) {
            throw new IllegalArgumentException("fieldCode must contain 1..128 characters");
        }
        beforeValue = copy(beforeValue);
        afterValue = copy(afterValue);
        if (Objects.equals(beforeValue, afterValue) && !masked) {
            throw new IllegalArgumentException("A history diff must contain a changed value");
        }
    }

    private static JsonNode copy(JsonNode value) {
        return value == null ? NullNode.getInstance() : value.deepCopy();
    }
}
