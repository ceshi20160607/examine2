package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统菜单树节点。
 */
@Data
@Builder
@Schema(description = "系统菜单树节点")
public class SystemMenuTreeVO {

    @Schema(description = "菜单 ID")
    private String menuId;

    @Schema(description = "父菜单 ID")
    private String parentId;

    @Schema(description = "菜单编码")
    private String code;

    @Schema(description = "菜单名称")
    private String name;

    @Schema(description = "菜单类型")
    private String menuType;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "前端路由")
    private String path;

    @Schema(description = "菜单状态")
    private String status;

    @Schema(description = "子菜单")
    private List<SystemMenuTreeVO> children;
}
