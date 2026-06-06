package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 角色权限授权入参。
 */
@Data
@Schema(description = "角色权限授权入参")
public class RolePermissionAssignBO {

    @Schema(description = "角色 ID")
    private Long roleId;

    @Schema(description = "授权权限 ID 列表")
    private List<Long> permissionIds;
}
