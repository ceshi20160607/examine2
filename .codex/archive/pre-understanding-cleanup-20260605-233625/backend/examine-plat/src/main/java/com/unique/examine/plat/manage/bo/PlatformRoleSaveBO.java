package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色保存入参。
 */
@Data
@Schema(description = "角色保存入参")
public class PlatformRoleSaveBO {

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "应用 ID，平台或租户角色为空")
    private Long appId;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色类型：PLATFORM、TENANT、APP")
    private String roleType;
}
