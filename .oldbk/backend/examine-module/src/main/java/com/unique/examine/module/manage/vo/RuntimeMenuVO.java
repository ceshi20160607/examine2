package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行台菜单节点。
 */
@Data
@Builder
@Schema(description = "运行台菜单节点")
public class RuntimeMenuVO {

    @Schema(description = "菜单 ID")
    private String menuId;

    @Schema(description = "父菜单 ID")
    private String parentId;

    @Schema(description = "应用 ID")
    private String appId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "菜单编码")
    private String code;

    @Schema(description = "菜单名称")
    private String name;

    @Schema(description = "前端路由")
    private String routePath;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "子节点")
    private List<RuntimeMenuVO> children;
}
