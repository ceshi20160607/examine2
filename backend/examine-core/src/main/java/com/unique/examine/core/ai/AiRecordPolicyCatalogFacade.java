package com.unique.examine.core.ai;

import java.util.Objects;
import java.util.Set;

/**
 * Narrow owner port exposing the current member's readable record-policy
 * catalog without exposing module persistence or runtime schema internals.
 */
public interface AiRecordPolicyCatalogFacade {

    Result catalog(Request request);

    record Request(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode
    ) {
        public Request {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0) {
                throw new IllegalArgumentException(
                        "AI record policy catalog context IDs must be positive");
            }
            if (effectivePermissions == null
                    || effectivePermissions.stream().anyMatch(
                    permission -> permission == null || permission.isBlank())) {
                throw new IllegalArgumentException(
                        "AI record policy catalog permissions are invalid");
            }
            effectivePermissions = Set.copyOf(effectivePermissions);
            requireCode(moduleCode, "module code");
        }
    }

    record Result(
            String moduleCode,
            String schemaVersionId,
            long authzEpoch,
            Set<String> readableFieldCodes
    ) {
        public Result {
            requireCode(moduleCode, "module code");
            if (!positiveLong(schemaVersionId)) {
                throw new IllegalArgumentException(
                        "AI record policy catalog schema version ID is invalid");
            }
            if (authzEpoch < 0) {
                throw new IllegalArgumentException(
                        "AI record policy catalog authorization epoch must not be negative");
            }
            Objects.requireNonNull(readableFieldCodes, "readableFieldCodes");
            readableFieldCodes.forEach(code -> requireCode(code, "readable field code"));
            readableFieldCodes = Set.copyOf(readableFieldCodes);
        }
    }

    private static void requireCode(String value, String label) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(
                    "AI record policy catalog " + label + " is invalid");
        }
    }

    private static boolean positiveLong(String value) {
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 && Long.toString(parsed).equals(value);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
