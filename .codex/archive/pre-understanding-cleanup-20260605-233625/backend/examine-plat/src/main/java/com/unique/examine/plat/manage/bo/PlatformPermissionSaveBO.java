package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 权限点保存入参。
 */
@Data
@Schema(description = "权限点保存入参")
public class PlatformPermissionSaveBO {

    @Schema(description = "租户 ID，平台权限可为空")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "权限编码")
    private String permissionCode;

    @Schema(description = "权限名称")
    private String permissionName;

    @Schema(description = "权限类型：MENU、BUTTON、API、FIELD、DATA_SCOPE")
    private String permissionType;

    @Schema(description = "资源路径、接口路径或字段路径")
    private String resourcePath;
}
