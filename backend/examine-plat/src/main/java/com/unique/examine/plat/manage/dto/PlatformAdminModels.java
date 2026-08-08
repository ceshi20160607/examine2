package com.unique.examine.plat.manage.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class PlatformAdminModels {
    private PlatformAdminModels() {
    }

    public record PageResult<T>(List<T> items, int page, int size, long total) {
        public PageResult {
            items = List.copyOf(items);
        }
    }

    public record SystemCreate(
            String code,
            String name,
            String description,
            String tenantMode
    ) {
    }

    public record SystemUpdate(String name, String description, String version) {
    }

    public record LifecycleCommand(String reason, String version, Boolean impactConfirmed) {
    }

    public record TombstoneRestore(String reason, String version) { }

    public record SystemView(
            String id,
            String code,
            String name,
            String description,
            String status,
            String tenantMode,
            String ownerAccountId,
            String version,
            LocalDateTime createdAt,
            String initFailureCode,
            LocalDateTime initFailedAt
    ) {
    }

    public record SystemTombstoneView(
            String id,
            String code,
            String name,
            String description,
            String tenantMode,
            String ownerAccountId,
            String version,
            LocalDateTime deletedAt,
            String reason
    ) { }

    public record AccountUpdate(
            String displayName,
            String email,
            String mobile,
            String status,
            List<String> departmentIds,
            List<String> roleIds,
            String version
    ) {
    }

    public record AccountView(
            String id,
            String username,
            String displayName,
            String email,
            String mobile,
            String status,
            List<String> departmentIds,
            List<String> roleIds,
            String version
    ) {
        public AccountView {
            departmentIds = List.copyOf(departmentIds);
            roleIds = List.copyOf(roleIds);
        }
    }

    public record DepartmentCreate(String name, String code, String parentId) {
    }

    public record DepartmentUpdate(String name, String parentId, String status, String version) {
    }

    public record DepartmentView(
            String id,
            String parentId,
            String name,
            String code,
            String status,
            long memberCount,
            String version
    ) {
    }

    public record RoleCreate(String code, String name, String description) {
    }

    public record RoleDraftInput(
            String name,
            String description,
            List<String> permissionCodes,
            List<String> deniedPermissionCodes,
            String dataScopeId,
            String version
    ) {
    }

    public record VersionInput(String version) {
    }

    public record RoleView(
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
        public RoleView {
            permissionCodes = List.copyOf(permissionCodes);
            deniedPermissionCodes = List.copyOf(deniedPermissionCodes);
        }
    }

    public record PermissionView(
            String id,
            String code,
            String name,
            String type,
            String description
    ) {
    }
}
