package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运行菜单配置保存入参。
 */
@Data
@Schema(description = "运行菜单配置保存入参")
public class MenuConfigSaveBO {

    @Schema(description = "父菜单 ID，0 表示根菜单")
    private String menuParentId;

    @NotBlank(message = "菜单编码不能为空")
    @Schema(description = "菜单编码")
    private String code;

    @NotBlank(message = "菜单名称不能为空")
    @Schema(description = "菜单名称")
    private String name;

    @Schema(description = "前端路由")
    private String routePath;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "是否可见")
    private Boolean visible;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sortOrder;
}
