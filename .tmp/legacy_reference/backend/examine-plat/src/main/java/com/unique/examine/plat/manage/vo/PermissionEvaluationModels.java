package com.unique.examine.plat.manage.vo;

import java.util.List;

public final class PermissionEvaluationModels {
    private PermissionEvaluationModels() {
    }

    public record Result(
            String principalType,
            String principalId,
            String systemId,
            String tenantId,
            String permissionVersion,
            List<RoleSource> sourceRoles,
            List<DataScopeSource> dataScopes,
            List<Decision> decisions
    ) {
        public Result {
            sourceRoles = List.copyOf(sourceRoles);
            dataScopes = List.copyOf(dataScopes);
            decisions = List.copyOf(decisions);
        }
    }

    public record RoleSource(String id, String code, String name, String publishedVersion) {
    }

    public record DataScopeSource(String roleId, String id, String code, String kind) {
    }

    public record Decision(
            String permissionCode,
            String result,
            String reason,
            List<String> sourceRoleIds
    ) {
        public Decision {
            sourceRoleIds = List.copyOf(sourceRoleIds);
        }
    }
}
