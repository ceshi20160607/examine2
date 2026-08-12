package com.unique.examine.core.api;

import java.util.List;
import java.util.Map;

public interface DynamicPermissionFacade {
    SyncResult synchronizeSystem(
            long systemId,
            long actorAccountId,
            String namespace,
            List<Definition> definitions
    );

    record Definition(String code, String name, String resourceType) {
        public Definition {
            if (code == null || code.isBlank() || name == null || name.isBlank()
                    || resourceType == null || resourceType.isBlank()) {
                throw new IllegalArgumentException("Dynamic permission definition must be complete");
            }
        }
    }

    record SyncResult(Map<String, String> permissionIds, long authzEpoch) {
        public SyncResult {
            permissionIds = Map.copyOf(permissionIds);
        }
    }
}
