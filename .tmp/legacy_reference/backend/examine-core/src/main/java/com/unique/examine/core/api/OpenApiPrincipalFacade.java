package com.unique.examine.core.api;

import java.util.Set;

/**
 * Read-only cross-module port for resolving the service member behind an
 * OpenAPI application without creating an interactive session.
 */
public interface OpenApiPrincipalFacade {
    Principal resolve(long systemId, long tenantId, long memberId);

    record Principal(
            long accountId,
            long permissionVersion,
            boolean systemActive,
            boolean tenantActive,
            boolean memberActive,
            Set<String> permissions
    ) {
        public Principal {
            permissions = Set.copyOf(permissions);
        }
    }
}
