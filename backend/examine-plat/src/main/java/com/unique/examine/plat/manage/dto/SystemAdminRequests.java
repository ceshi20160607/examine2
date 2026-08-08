package com.unique.examine.plat.manage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SystemAdminRequests {
    private static final String ID = "[1-9][0-9]*";
    private static final String VERSION = "[0-9]+";

    private SystemAdminRequests() {
    }

    public record UpdateSettings(
            @NotBlank @Size(max = 160) String name,
            @NotNull @Size(max = 2000) String description,
            @NotBlank @Pattern(regexp = "SINGLE|MULTI") String tenantMode,
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record CreateTenant(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String code,
            @NotBlank @Size(max = 160) String name
    ) {
    }

    public record UpdateTenant(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record LifecycleCommand(
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Pattern(regexp = VERSION) String version,
            boolean impactConfirmed
    ) {
    }

    public record CreateDepartment(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String code,
            @Pattern(regexp = ID) String parentId
    ) {
    }

    public record UpdateDepartment(
            @NotBlank @Size(max = 160) String name,
            @Pattern(regexp = ID) String parentId,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record UpdateDepartmentLeader(
            @Pattern(regexp = ID) String leaderMemberId,
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record UpdateMember(
            @Size(max = 160) String displayName,
            @Pattern(regexp = "ACTIVE|DISABLED|PENDING") String status,
            @Pattern(regexp = ID) String primaryDepartmentId,
            @Size(max = 200) List<@Pattern(regexp = ID) String> departmentIds,
            @Size(max = 200) List<@Pattern(regexp = ID) String> tenantIds,
            @Size(max = 200) List<@Pattern(regexp = ID) String> roleIds,
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record UpdateMemberManager(
            @Pattern(regexp = ID) String managerMemberId,
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record CreateRole(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description
    ) {
    }

    public record SaveRoleDraft(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotNull @Size(max = 300) List<@NotBlank @Size(max = 128) String> permissionCodes,
            @Size(max = 300) List<@NotBlank @Size(max = 128) String> deniedPermissionCodes,
            @Pattern(regexp = ID) String dataScopeId,
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record DraftCommand(
            @NotBlank @Pattern(regexp = VERSION) String version
    ) {
    }

    public record ReviewAccessRequest(
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Pattern(regexp = VERSION) String version,
            @Size(max = 200) List<@Pattern(regexp = ID) String> tenantIds,
            @Size(max = 200) List<@Pattern(regexp = ID) String> roleIds
    ) {
    }
}
