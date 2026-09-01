package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeRecordTimelineChange(
        String fieldCode,
        String fieldName,
        String valueType,
        JsonNode beforeValue,
        JsonNode afterValue,
        boolean masked) {
}
