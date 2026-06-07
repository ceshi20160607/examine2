package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统角色信息。
 */
@Data
@Builder
@Schema(description = "系统角色信息")
public class RoleVO {

    @Schema(description = "角色 ID")
    private String roleId;

    @Schema(description = "所属系统 ID")
    private String systemId;

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "角色编码")
    private String code;

    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "角色状态")
    private String status;

    @Schema(description = "是否保护角色")
    private Boolean protectedFlag;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "菜单 ID")
    private List<String> menuIds;

    @Schema(description = "操作编码")
    private List<String> operationCodes;
}
