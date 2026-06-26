package com.unique.examine.plat.manage.permission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Permission API models.
 */
public final class PermissionModels {

    private PermissionModels() {
    }

    public record RolePermissionSaveRequest(Map<String, Boolean> menuPermissions,
                                            Map<String, Boolean> modulePermissions,
                                            Map<String, Boolean> actionPermissions,
                                            Map<String, String> fieldPermissions,
                                            List<Map<String, Object>> dataScopeRules,
                                            List<String> denyPolicies) {
    }

    public record RolePermissionVO(String roleId, String permissionVersion,
                                   Map<String, Boolean> actionPermissions,
                                   Map<String, String> fieldPermissions,
                                   List<Map<String, Object>> dataScopeRules,
                                   List<String> denyPolicies, LocalDateTime updatedAt) {
    }

    public record PermissionPreviewRequest(String systemMemberId, String tenantId, List<String> roleIds,
                                           String moduleId, String recordId, String actionCode) {
    }

    public record PermissionDecisionVO(boolean allowed, String disabledReason, List<String> missingPermissions,
                                       Map<String, Object> dataScopeExpression, Map<String, String> fieldMaskRules,
                                       List<Map<String, Object>> explain, String permissionVersion,
                                       String traceId, String auditLogId) {
    }

    public record EffectivePermissionSnapshot(String snapshotId, String permissionVersion, String systemMemberId,
                                              String tenantId, List<String> sourceRoleIds, List<String> denyPolicyIds,
                                              Map<String, String> field, Map<String, Boolean> action,
                                              Map<String, Object> dataScope, String disabledReason,
                                              List<Map<String, Object>> explain) {
    }
}
