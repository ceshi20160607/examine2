package com.unique.examine.core.api;

import java.util.List;
import java.util.Set;

/** Read-only cross-module port for evaluating a target member without impersonation. */
public interface EffectivePermissionFacade {
    Evaluation evaluateSystem(long systemId, long tenantId, long memberId);

    record Evaluation(long epoch, boolean root, Set<String> permissions,
                      List<RoleSource> roles, List<DataScopeSource> dataScopes) {
        public Evaluation {
            permissions = Set.copyOf(permissions);
            roles = List.copyOf(roles);
            dataScopes = List.copyOf(dataScopes);
        }
    }

    record RoleSource(String id, String code, String name, String publishedVersion) { }

    record DataScopeSource(String roleId, String id, String code, String kind) { }
}
