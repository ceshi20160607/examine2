package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record RuntimeImportValidation(
        Long recordId,
        Integer recordVersion,
        Map<String, JsonNode> normalizedFields,
        CreateRuntimeRecordRequest beforeState) {
}
