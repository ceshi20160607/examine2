package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;

public record RuntimeRecordConversionMapping(
        String targetFieldCode,
        String targetFieldName,
        boolean required,
        String sourceFieldCode,
        JsonNode value,
        String status,
        String message) {
}
