package com.unique.examine.core.ai;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Platform owner boundary exposing only systems the current platform account
 * can enter. This boundary has no system, tenant, member or business-record
 * input and cannot be used as a system-context data owner.
 */
public interface PlatformAuthorizedSystemFacade {

    Result authorizedSystems(Request request);

    record Request(
            long accountId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String requestId,
            String traceId
    ) {
        public Request {
            positive(accountId, "accountId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
        }
    }

    record Result(List<SystemAccess> systems) {
        public Result {
            systems = List.copyOf(Objects.requireNonNull(systems, "systems"));
            var ids = systems.stream().map(SystemAccess::systemId).toList();
            if (Set.copyOf(ids).size() != ids.size()) {
                throw new IllegalArgumentException("systems contain duplicate ids");
            }
        }
    }

    record SystemAccess(
            String systemId,
            String systemCode,
            String systemName,
            String status,
            String membershipState,
            String accessState,
            String switchTarget
    ) {
        public SystemAccess {
            systemId = positiveDecimal(systemId, "systemId");
            systemCode = code(systemCode, "systemCode");
            systemName = required(systemName, "systemName", 160);
            if (!Set.of("ACTIVE", "INITIALIZING").contains(status)) {
                throw new IllegalArgumentException("status is invalid");
            }
            if (!"ACTIVE".equals(membershipState)) {
                throw new IllegalArgumentException("membershipState is invalid");
            }
            if (!"AUTHORIZED".equals(accessState)) {
                throw new IllegalArgumentException("accessState is invalid");
            }
            var expectedTarget = "/api/v1/context/systems/" + systemId + ":switch";
            if (!expectedTarget.equals(switchTarget)) {
                throw new IllegalArgumentException("switchTarget is invalid");
            }
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static String code(String value, String name) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static String required(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
