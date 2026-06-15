package com.unique.examine.module.manage.vo;

import java.util.List;

import com.unique.examine.core.permission.DataScopeRuleVO;
import com.unique.examine.core.permission.FieldPermissionVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统角色权限详情。
 */
@Data
@Builder
@Schema(description = "系统角色权限详情")
public class RolePermissionDetailVO {

    @Schema(description = "角色 ID")
    private String roleId;

    @Schema(description = "菜单 ID")
    private List<String> menuIds;

    @Schema(description = "操作编码")
    private List<String> operationCodes;

    @Schema(description = "字段权限")
    private List<FieldPermissionVO> fieldPermissions;

    @Schema(description = "数据范围")
    private List<DataScopeRuleVO> dataScopes;

    @Schema(description = "OpenAPI scope 编码")
    private List<String> openapiScopes;
}
