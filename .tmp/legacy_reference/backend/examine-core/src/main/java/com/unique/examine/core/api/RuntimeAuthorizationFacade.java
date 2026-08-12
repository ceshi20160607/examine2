package com.unique.examine.core.api;

import java.util.List;
import java.util.Set;

/** Cross-module port that resolves role-bound runtime record scopes without exposing platform tables. */
public interface RuntimeAuthorizationFacade {
    RuntimeGrant resolve(RuntimeAuthorizationRequest request);

    /** Returns the latest system authorization epoch in an independent read transaction. */
    long currentSystemEpoch(long systemId);

    record RuntimeAuthorizationRequest(
            long systemId,
            long tenantId,
            long memberId,
            String modulePermissionCode
    ) {
        public RuntimeAuthorizationRequest {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0
                    || modulePermissionCode == null || modulePermissionCode.isBlank()) {
                throw new IllegalArgumentException("Runtime authorization request is incomplete");
            }
        }
    }

    record RuntimeGrant(
            boolean denied,
            long authzEpoch,
            boolean allRecords,
            Set<Long> ownerMemberIds,
            Set<Long> ownerDepartmentIds,
            List<String> fieldRuleAsts
    ) {
        public RuntimeGrant {
            ownerMemberIds = Set.copyOf(ownerMemberIds);
            ownerDepartmentIds = Set.copyOf(ownerDepartmentIds);
            fieldRuleAsts = List.copyOf(fieldRuleAsts);
        }

        public static RuntimeGrant denied(long authzEpoch) {
            return new RuntimeGrant(true, authzEpoch, false, Set.of(), Set.of(), List.of());
        }
    }
}
