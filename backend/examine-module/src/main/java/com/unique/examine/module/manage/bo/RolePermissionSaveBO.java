package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统角色权限保存入参。
 */
@Data
@Schema(description = "系统角色权限保存入参")
public class RolePermissionSaveBO {

    @Schema(description = "菜单 ID 集合")
    private List<String> menuIds;

    @Schema(description = "操作编码集合")
    private List<String> operationCodes;

    @Schema(description = "字段权限集合")
    private List<RoleFieldPermissionBO> fieldPermissions;

    @Schema(description = "数据范围规则集合")
    private List<DataScopeRuleBO> dataScopes;

    @Schema(description = "OpenAPI scope 授权集合")
    private List<OpenapiScopeBO> openapiScopes;
}
