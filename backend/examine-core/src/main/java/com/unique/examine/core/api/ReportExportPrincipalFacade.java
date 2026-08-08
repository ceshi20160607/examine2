package com.unique.examine.core.api;

import java.util.Optional;
import java.util.Set;

/** Resolves the requester's current active tenant identity for report workers. */
public interface ReportExportPrincipalFacade {
    Optional<Principal> current(
            long systemId,
            long tenantId,
            long memberId);

    record Principal(
            long accountId,
            long memberId,
            long permissionEpoch,
            Set<String> permissions
    ) {
        public Principal {
            if (accountId <= 0 || memberId <= 0 || permissionEpoch <= 0
                    || permissions == null) {
                throw new IllegalArgumentException(
                        "Report export principal is invalid");
            }
            permissions = Set.copyOf(permissions);
        }
    }
}
