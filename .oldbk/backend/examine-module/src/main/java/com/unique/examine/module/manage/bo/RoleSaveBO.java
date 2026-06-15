package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统角色保存入参。
 */
@Data
@Schema(description = "系统角色保存入参")
public class RoleSaveBO {

    @Schema(description = "租户 ID，0 表示系统级角色")
    private String tenantId;

    @NotBlank(message = "角色编码不能为空")
    @Schema(description = "角色编码")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称")
    private String name;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "排序")
    private Integer sortOrder;
}
