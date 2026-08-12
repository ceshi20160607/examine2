package com.unique.examine.core.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lazy cross-module port for dispatching deterministic runtime-record events to Flow.
 */
public interface RuntimeRecordFlowTriggerFacade {

    TriggerResult trigger(TriggerRequest request);

    enum TriggerEvent {
        RECORD_ACTIVATED,
        RECORD_CREATED,
        RECORD_UPDATED,
        RECORD_DELETED,
        RECORD_STATUS_CHANGED,
        IMPORT_COMPLETED
    }

    record TriggerRequest(
            long systemId,
            long tenantId,
            long actorMemberId,
            Set<String> effectivePermissions,
            String moduleCode,
            long recordId,
            long recordVersion,
            String businessKey,
            TriggerEvent event,
            String eventKey,
            Map<String, String> recordValuesJson
    ) {
        private static final int MAX_BUSINESS_KEY_CODE_POINTS = 160;
        private static final int MAX_EVENT_KEY_CODE_POINTS = 200;
        private static final int MAX_RECORD_VALUES = 256;
        private static final int MAX_JSON_VALUE_BYTES = 16 * 1024;

        public TriggerRequest {
            if (systemId <= 0 || tenantId <= 0 || actorMemberId <= 0 || recordId <= 0) {
                throw new IllegalArgumentException("Record Flow trigger IDs must be positive");
            }
            if (recordVersion < 0) {
                throw new IllegalArgumentException("Record Flow trigger version must not be negative");
            }
            if (effectivePermissions == null
                    || effectivePermissions.stream().anyMatch(
                    permission -> permission == null || permission.isBlank())) {
                throw new IllegalArgumentException("Record Flow trigger permissions are invalid");
            }
            effectivePermissions = Set.copyOf(effectivePermissions);
            if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Record Flow trigger module code is invalid");
            }
            businessKey = compactText(
                    businessKey,
                    MAX_BUSINESS_KEY_CODE_POINTS,
                    "business key");
            Objects.requireNonNull(event, "event");
            eventKey = compactText(eventKey, MAX_EVENT_KEY_CODE_POINTS, "event key");
            recordValuesJson = copyRecordValues(recordValuesJson);
        }

        public TriggerRequest(
                long systemId,
                long tenantId,
                long actorMemberId,
                Set<String> effectivePermissions,
                String moduleCode,
                long recordId,
                long recordVersion,
                String businessKey,
                TriggerEvent event,
                String eventKey
        ) {
            this(
                    systemId,
                    tenantId,
                    actorMemberId,
                    effectivePermissions,
                    moduleCode,
                    recordId,
                    recordVersion,
                    businessKey,
                    event,
                    eventKey,
                    Map.of());
        }

        private static String compactText(String value, int maximum, String label) {
            if (value == null) {
                throw new IllegalArgumentException("Record Flow trigger " + label + " is invalid");
            }
            var compact = value.strip();
            var length = compact.codePointCount(0, compact.length());
            if (length < 1 || length > maximum) {
                throw new IllegalArgumentException("Record Flow trigger " + label + " is invalid");
            }
            return compact;
        }

        private static Map<String, String> copyRecordValues(Map<String, String> values) {
            if (values == null || values.size() > MAX_RECORD_VALUES) {
                throw new IllegalArgumentException("Record Flow trigger value snapshot is invalid");
            }
            var copy = new LinkedHashMap<String, String>();
            var mapper = new ObjectMapper();
            for (var entry : values.entrySet()) {
                var key = entry.getKey();
                var json = entry.getValue();
                if (key == null || !key.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                        || json == null
                        || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_VALUE_BYTES) {
                    throw new IllegalArgumentException("Record Flow trigger value snapshot is invalid");
                }
                try {
                    var parsed = mapper.reader()
                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(json);
                    if (parsed == null || containsJsonWhitespaceOutsideString(json)) {
                        throw new IllegalArgumentException(
                                "Record Flow trigger value snapshot must contain compact canonical JSON");
                    }
                } catch (JsonProcessingException exception) {
                    throw new IllegalArgumentException(
                            "Record Flow trigger value snapshot contains invalid JSON",
                            exception);
                }
                copy.put(key, json);
            }
            return Collections.unmodifiableMap(copy);
        }

        private static boolean containsJsonWhitespaceOutsideString(String json) {
            var quoted = false;
            var escaped = false;
            for (var index = 0; index < json.length(); index++) {
                var character = json.charAt(index);
                if (quoted) {
                    if (escaped) {
                        escaped = false;
                    } else if (character == '\\') {
                        escaped = true;
                    } else if (character == '"') {
                        quoted = false;
                    }
                } else if (character == '"') {
                    quoted = true;
                } else if (character == ' '
                        || character == '\t'
                        || character == '\r'
                        || character == '\n') {
                    return true;
                }
            }
            return false;
        }
    }

    record TriggeredInstance(
            long definitionId,
            int definitionVersion,
            long instanceId
    ) {
        public TriggeredInstance {
            if (definitionId <= 0 || definitionVersion <= 0 || instanceId <= 0) {
                throw new IllegalArgumentException("Triggered Flow instance IDs and version must be positive");
            }
        }
    }

    record TriggerResult(List<TriggeredInstance> instances) {
        public TriggerResult {
            if (instances == null || instances.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Triggered Flow instances are invalid");
            }
            instances = List.copyOf(instances);
        }
    }
}
