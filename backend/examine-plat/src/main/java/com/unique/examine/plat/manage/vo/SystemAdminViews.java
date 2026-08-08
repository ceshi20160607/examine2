package com.unique.examine.plat.manage.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemAdminViews {
    private SystemAdminViews() {
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public record Settings(
            String id,
            String systemId,
            String name,
            String code,
            String description,
            String tenantMode,
            String defaultTenantId,
            String status,
            String version
    ) {
    }

    public record Tenant(
            String id,
            String systemId,
            String code,
            String name,
            String status,
            @JsonProperty("isDefault") boolean isDefault,
            long memberCount,
            String version
    ) {
    }

    public record Department(
            String id,
            String parentId,
            String name,
            String code,
            String status,
            long memberCount,
            String version,
            String leaderMemberId
    ) {
    }

    public record Member(
            String id,
            String accountId,
            String username,
            String displayName,
            String email,
            String status,
            String primaryDepartmentId,
            List<String> departmentIds,
            List<String> tenantIds,
            List<String> roleIds,
            String version,
            String managerMemberId
    ) {
        public Member {
            departmentIds = List.copyOf(departmentIds);
            tenantIds = List.copyOf(tenantIds);
            roleIds = List.copyOf(roleIds);
        }
    }

    public record Permission(
            String id,
            String code,
            String name,
            String type,
            String description
    ) {
    }

    public record Role(
            String id,
            String code,
            String name,
            String description,
            String status,
            boolean builtin,
            long memberCount,
            List<String> permissionCodes,
            List<String> deniedPermissionCodes,
            String dataScopeId,
            String draftStatus,
            String publishedVersion,
            String version
    ) {
        public Role {
            permissionCodes = List.copyOf(permissionCodes);
            deniedPermissionCodes = List.copyOf(deniedPermissionCodes);
        }
    }

    public record DataScope(
            String id,
            String name,
            String kind,
            String description,
            List<String> targetIds,
            String version
    ) {
        public DataScope {
            targetIds = List.copyOf(targetIds);
        }
    }

    public record AccessRequest(
            String id,
            String systemId,
            String systemName,
            String accountId,
            String accountName,
            String targetTenantId,
            String targetTenantName,
            String reason,
            String status,
            String reviewReason,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            String version
    ) {
    }
}
