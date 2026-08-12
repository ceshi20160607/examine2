package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class RecordFlowEventValueSnapshot {
    private static final Set<String> SENSITIVE_TYPES = Set.of("IDENTITY", "SECRET");
    private static final int MAX_FIELDS = 256;
    private static final int MAX_JSON_BYTES = 16 * 1024;

    private RecordFlowEventValueSnapshot() {
    }

    static Map<String, String> from(ObjectMapper mapper, List<Value> values) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(values, "values");
        var snapshot = new LinkedHashMap<String, String>();
        var compactWriter = mapper.writer().without(SerializationFeature.INDENT_OUTPUT);
        for (var value : values) {
            Objects.requireNonNull(value, "value");
            if (SENSITIVE_TYPES.contains(value.fieldType())) {
                continue;
            }
            final String json;
            try {
                json = compactWriter.writeValueAsString(value.value());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "Persisted runtime value cannot be encoded as canonical JSON",
                        exception);
            }
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
                throw new IllegalArgumentException(
                    "A record Flow event trigger value exceeds 16 KiB");
            }
            snapshot.put(value.fieldCode(), json);
        }
        if (snapshot.size() > MAX_FIELDS) {
            throw new IllegalArgumentException(
                    "A record Flow event trigger snapshot exceeds 256 fields");
        }
        return Collections.unmodifiableMap(snapshot);
    }

    record Value(String fieldCode, String fieldType, Object value) {
        Value {
            if (fieldCode == null || !fieldCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Trigger snapshot field code is invalid");
            }
            if (fieldType == null || fieldType.isBlank()) {
                throw new IllegalArgumentException("Trigger snapshot field type is invalid");
            }
        }
    }
}
