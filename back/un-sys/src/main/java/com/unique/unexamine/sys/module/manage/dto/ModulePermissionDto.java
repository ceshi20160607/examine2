package com.unique.unexamine.sys.module.manage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** ModulePermission 的请求与返回模型。 */
public final class ModulePermissionDto {
    private ModulePermissionDto() {}

    /** 租户字段上限，null 集合表示不额外限制，普通角色许可逐字段存储。 */
    public record FieldAccess(Set<String> readable,Set<String> writable,Set<String> exportable) {}

    /** 菜单功能授权与负责人范围，字段许可在独立接口配置。 */
    public record GrantRequest(boolean allowed,@NotBlank String scope,Set<Long> accountIds,Set<Long> departmentIds) {}

    /** 每个稳定字段 ID 的正向许可，不授权的标记不否决其他角色。 */
    public record FieldGrant(@NotNull Long fieldId,boolean readable,boolean writable,boolean exportable) {}

    /** 替换指定角色在当前模块的全部字段许可。 */
    public record FieldsRequest(@NotNull List<@Valid FieldGrant> fields) {}

    /** 管理员添加或调整租户成员，不能提升管理身份。 */
    public record MemberRequest(@NotNull Long accountId,Long departmentId) {}

    /** 普通角色资料，代码不是系统管理身份。 */
    public record RoleRequest(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=100) String name) {}

    /** 显式设置成员角色关系，撤销不删除其他角色。 */
    public record AssignRequest(@NotNull Long tenantMemberId,@NotNull Long roleId,boolean assigned) {}
}
