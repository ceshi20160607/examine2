package com.unique.examine.flow.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, bounded and authorization-safe input snapshot captured before
 * gateway selection. Services decide which already-authorized values may enter
 * this snapshot; the domain canonicalizes and bounds the durable representation.
 */
public record ApprovalStartContext(
        long requesterMemberId,
        String moduleCode,
        Long recordId,
        Map<String, String> valuesJson,
        Long rootInstanceId,
        int subflowDepth
) {
    public static final int MAX_VALUES = 200;
    public static final int MAX_JSON_CHARS = 262_144;
    public static final int MAX_SUBFLOW_DEPTH = 8;
    private static final String CODE_PATTERN = "^[A-Za-z][A-Za-z0-9_]{0,63}$";
    private static final ObjectMapper JSON = new ObjectMapper();

    public ApprovalStartContext {
        if (requesterMemberId <= 0) {
            throw new IllegalArgumentException(
                    "Approval start-context requester must be positive");
        }
        if ((moduleCode == null) != (recordId == null)) {
            throw new IllegalArgumentException(
                    "Approval start-context record identity must be all present or all absent");
        }
        if (moduleCode != null && !moduleCode.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException(
                    "Approval start-context module code is invalid");
        }
        if (recordId != null && recordId <= 0) {
            throw new IllegalArgumentException(
                    "Approval start-context record id must be positive");
        }
        if (rootInstanceId != null && rootInstanceId <= 0
                || subflowDepth < 0 || subflowDepth > MAX_SUBFLOW_DEPTH
                || subflowDepth > 0 && rootInstanceId == null) {
            throw new IllegalArgumentException(
                    "Approval subflow root/depth context is invalid");
        }
        valuesJson = canonicalValues(valuesJson);
    }

    public ApprovalStartContext(
            long requesterMemberId,
            String moduleCode,
            Long recordId,
            Map<String, String> valuesJson
    ) {
        this(requesterMemberId, moduleCode, recordId, valuesJson, null, 0);
    }

    public ApprovalStartContext(long requesterMemberId) {
        this(requesterMemberId, null, null, Map.of(), null, 0);
    }

    public boolean hasRecord() {
        return moduleCode != null;
    }

    /** Binds a top-level context to its immutable root after the instance id is allocated. */
    public ApprovalStartContext root(long instanceId) {
        if (instanceId <= 0 || subflowDepth != 0
                || rootInstanceId != null && rootInstanceId != instanceId) {
            throw new IllegalArgumentException(
                    "Approval root instance context is invalid");
        }
        return new ApprovalStartContext(
                requesterMemberId, moduleCode, recordId, valuesJson,
                instanceId, 0);
    }

    /** Copies all immutable requester/record facts into a bounded child context. */
    public ApprovalStartContext child(long rootInstanceId, int nextDepth) {
        if (rootInstanceId <= 0
                || this.rootInstanceId != null
                && this.rootInstanceId != rootInstanceId
                || nextDepth != subflowDepth + 1
                || nextDepth > MAX_SUBFLOW_DEPTH) {
            throw new IllegalArgumentException(
                    "Approval subflow depth exceeds the governed hierarchy");
        }
        return new ApprovalStartContext(
                requesterMemberId, moduleCode, recordId, valuesJson,
                rootInstanceId, nextDepth);
    }

    private static Map<String, String> canonicalValues(
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        if (values.size() > MAX_VALUES) {
            throw new IllegalArgumentException(
                    "Approval start context contains too many values");
        }
        var result = new LinkedHashMap<String, String>();
        var totalChars = 0;
        for (var entry : values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            var code = entry.getKey();
            if (code == null || !code.matches(CODE_PATTERN)
                    || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Approval start-context value is invalid");
            }
            try {
                var value = JSON.readTree(entry.getValue());
                if (value == null || value.isMissingNode()) {
                    throw new IllegalArgumentException(
                            "Approval start-context value must be valid JSON");
                }
                var canonical = value.toString();
                totalChars = Math.addExact(
                        totalChars,
                        Math.addExact(code.length(), canonical.length())
                );
                if (totalChars > MAX_JSON_CHARS) {
                    throw new IllegalArgumentException(
                            "Approval start-context JSON exceeds its bound");
                }
                result.put(code, canonical);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException(
                        "Approval start-context value must be valid JSON",
                        exception
                );
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
