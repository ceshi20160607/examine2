package com.unique.examine.plat.manage.role;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Role management API models.
 */
public final class RoleModels {

    private RoleModels() {
    }

    public record RoleQueryRequest(String roleType, Integer status, String keyword) {
    }

    public record RoleSaveRequest(String roleName, String roleCode, String roleType, Integer status,
                                  String description) {
    }

    public record RoleVO(String roleId, String scope, String systemId, String tenantId, String roleCode,
                         String roleName, String roleType, boolean builtin, Integer status, String description,
                         LocalDateTime updatedAt) {
    }

    public record RoleMemberAssignRequest(List<String> systemMemberIds, List<String> accountIds) {
    }

    public record RoleMemberAssignResult(String roleId, int assignedCount, String traceId, String auditLogId) {
    }
}
