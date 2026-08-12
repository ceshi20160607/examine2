package com.unique.examine.core.runtime;

import java.util.Set;

/**
 * Narrow cross-module port for resolving a record through the canonical runtime VIEW path.
 *
 * <p>Implementations must preserve the runtime record service's not-found semantics for missing,
 * cross-tenant, and out-of-scope records. Callers receive no record field values.</p>
 */
public interface RuntimeRecordAccessFacade {

    RuntimeRecordAccess requireView(RuntimeRecordAccessRequest request);

    record RuntimeRecordAccessRequest(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> effectivePermissions,
            String moduleCode,
            long recordId
    ) {
        public RuntimeRecordAccessRequest {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0 || recordId <= 0) {
                throw new IllegalArgumentException("Runtime record access context IDs must be positive");
            }
            if (moduleCode == null || !moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException("Runtime record access module code is invalid");
            }
            if (effectivePermissions == null
                    || effectivePermissions.stream().anyMatch(
                    permission -> permission == null || permission.isBlank())) {
                throw new IllegalArgumentException("Runtime record access permissions are invalid");
            }
            effectivePermissions = Set.copyOf(effectivePermissions);
        }
    }

    record RuntimeRecordAccess(
            String recordId,
            long version,
            boolean allowComments
    ) {
        public RuntimeRecordAccess {
            if (recordId == null || !recordId.matches("^[1-9][0-9]{0,18}$")) {
                throw new IllegalArgumentException("Canonical runtime record ID is invalid");
            }
            if (version < 0) {
                throw new IllegalArgumentException("Canonical runtime record version must not be negative");
            }
        }
    }
}
