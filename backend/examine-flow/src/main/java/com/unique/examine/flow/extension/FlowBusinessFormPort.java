package com.unique.examine.flow.extension;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

public interface FlowBusinessFormPort {
    FormRecord read(Actor actor, String moduleCode, long recordId);

    FormRecord update(
            Actor actor,
            String moduleCode,
            long recordId,
            long expectedVersion,
            Map<String, JsonNode> values,
            String idempotencyKey,
            String requestId,
            String traceId);

    FormRecord create(
            Actor actor,
            String moduleCode,
            String title,
            Map<String, JsonNode> values,
            String idempotencyKey,
            String requestId,
            String traceId);

    record Actor(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            Set<String> permissions) {
        public Actor {
            if (accountId <= 0 || systemId <= 0 || tenantId <= 0 || memberId <= 0
                    || permissions == null || permissions.stream()
                    .anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("Flow business-form actor is invalid");
            }
            permissions = Set.copyOf(permissions);
        }
    }

    record FieldCapability(boolean readable, boolean writable) {
    }

    record FormRecord(
            String moduleCode,
            long recordId,
            long version,
            String title,
            String schemaVersionId,
            Map<String, JsonNode> values,
            Map<String, FieldCapability> capabilities) {
        public FormRecord {
            if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                    || recordId <= 0 || version < 0 || title == null
                    || schemaVersionId == null || schemaVersionId.isBlank()
                    || values == null || capabilities == null) {
                throw new IllegalArgumentException("Flow business-form record is invalid");
            }
            values = Map.copyOf(values);
            capabilities = Map.copyOf(capabilities);
        }
    }
}
