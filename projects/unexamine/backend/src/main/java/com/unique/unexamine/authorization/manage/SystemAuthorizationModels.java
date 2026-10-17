package com.unique.unexamine.authorization.manage;

import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class SystemAuthorizationModels {
    private SystemAuthorizationModels() {
    }

    public record Overview(
            List<DepartmentView> departments,
            List<MemberView> members,
            List<RoleView> roles,
            List<ResourceView> resources,
            long permissionVersion) {
    }

    public record DepartmentView(Long id, Long parentId, String code, String name, String pathCode,
                                 Integer sortOrder, String status, Integer version) {
    }

    /** 部门停用前的实时引用范围；可转移只表示该类引用支持业务转移。 */
    public record DepartmentImpact(Long departmentId, String departmentName,
                                   List<DepartmentReference> references,
                                   boolean canDeactivate, boolean canTransfer) {
    }

    /** 当前租户内使用部门的业务位置和数量。 */
    public record DepartmentReference(String key, String label, long count, boolean transferable) {
    }

    /** 有可转移引用时须指定同租户有效目标部门，避免留下失效引用。 */
    public record DepartmentDeactivateRequest(@NotNull Integer expectedVersion,
                                               Long replacementDepartmentId) {
    }

    /** 恢复停用部门时校验当前版本，避免覆盖并发组织调整。 */
    public record DepartmentActivateRequest(@NotNull Integer expectedVersion) {
    }

    public record MemberView(Long tenantMemberId, Long systemMemberId, Long accountId, String displayName,
                             String employeeNumber, Long departmentId, String departmentName,
                             Long managerTenantMemberId, String managerName, String positionTitle,
                             boolean tenantAdmin, String status, List<Long> roleIds, List<String> roleNames,
                             Integer version) {
    }

    public record RoleView(Long id, String code, String name, String description, boolean builtIn, String status,
                           List<PermissionInput> permissions, List<FieldPolicyInput> fieldPolicies,
                           Integer version, boolean draftPending) {
    }

    public record ResourceView(String resourceType, String resourceCode, String name,
                               List<String> actions, List<String> fields,
                               Map<String, String> actionNames, Map<String, String> fieldNames) {
    }

    public record DepartmentRequest(
            Long id,
            Long parentId,
            @Pattern(regexp = "[a-z][a-z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            Integer sortOrder,
            Integer expectedVersion) {
    }

    /** 拖拽部门时，INSIDE 表示成为目标下级，BEFORE/AFTER 表示目标的同级前后。 */
    public record DepartmentMoveRequest(@NotNull Long targetDepartmentId,
                                        @NotBlank String placement,
                                        @NotNull Integer expectedVersion) {
    }

    public record MemberAssignmentRequest(
            Long departmentId,
            Long managerTenantMemberId,
            @Size(max = 100) String positionTitle,
            @NotNull List<Long> roleIds,
            @NotNull Integer expectedVersion) {
    }

    public record AddMemberRequest(
            @NotBlank @Size(max = 200) String account,
            @Size(max = 100) String employeeNumber,
            Long departmentId,
            Long managerTenantMemberId,
            @Size(max = 100) String positionTitle,
            @NotNull List<Long> roleIds) {
    }

    public record PermissionInput(
            @NotBlank String resourceType,
            @NotBlank String resourceCode,
            @NotBlank String actionCode,
            @NotBlank String dataScopeType,
            String dataScopeJson) {
    }

    public record FieldPolicyInput(
            @NotBlank String resourceCode,
            @NotBlank String fieldCode,
            @NotBlank String channel,
            boolean readable,
            boolean writable,
            String maskStrategy) {
    }

    public record SaveRoleRequest(
            Long id,
            // 已发布角色允许沿用原有大写编码；编码创建后不可修改。
            @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,99}") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotEmpty List<@Valid PermissionInput> permissions,
            @NotNull List<@Valid FieldPolicyInput> fieldPolicies,
            Integer expectedVersion) {
    }

    public record PublishRoleRequest(
            @NotBlank @Size(max = 500) String reason,
            @NotNull Integer expectedVersion) {
    }

    public record PreviewRequest(
            @NotNull Long tenantMemberId,
            String resourceCode,
            String actionCode,
            @NotNull Long expectedPermissionVersion) {
    }

    public record RoleContribution(Long roleId, String roleCode, String roleName,
                                   List<PermissionInput> contributedPermissions) {
    }

    public record ActionDecision(String resourceType, String resourceCode, String resourceName, String actionCode,
                                 boolean allowed, List<Long> contributingRoleIds,
                                 DataScopeExpression dataScope, String reason) {
    }

    public record FieldDecision(String resourceCode, String actionCode, String fieldCode, String channel,
                                boolean readable, boolean writable, List<String> maskStrategies,
                                List<Long> restrictingRoleIds, String reason) {
    }

    public record PermissionPreview(long permissionVersion, boolean systemOwner, MemberView targetMember,
                                    List<RuntimeModuleCatalogItem> visibleMenus,
                                    List<RoleContribution> roles, List<ActionDecision> actions,
                                    List<FieldDecision> fields, Map<String, String> mergeRules) {
    }
}
