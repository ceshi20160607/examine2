package com.unique.examine.plat.manage.service;

import java.util.List;
import java.util.Set;

public record AuthorizationSnapshot(
        long epoch,
        Set<String> permissions,
        List<RoleSource> roles,
        List<DataScopeSource> dataScopes
) {
    public AuthorizationSnapshot {
        permissions = Set.copyOf(permissions);
        roles = List.copyOf(roles);
        dataScopes = List.copyOf(dataScopes);
    }

    public record RoleSource(String id, String code, String name, String publishedVersion) {
    }

    public record DataScopeSource(String roleId, String id, String code, String kind) {
    }
}
