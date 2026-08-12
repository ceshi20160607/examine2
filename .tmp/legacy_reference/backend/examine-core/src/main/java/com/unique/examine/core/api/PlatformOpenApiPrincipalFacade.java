package com.unique.examine.core.api;

import java.util.Set;

/** Live platform-account authorization boundary used by platform OpenAPI. */
public interface PlatformOpenApiPrincipalFacade {
    Principal resolve(long accountId);

    record Principal(long accountId, long permissionVersion, boolean accountActive,
                     Set<String> permissions) {
        public Principal {
            permissions = Set.copyOf(permissions);
        }
    }
}
