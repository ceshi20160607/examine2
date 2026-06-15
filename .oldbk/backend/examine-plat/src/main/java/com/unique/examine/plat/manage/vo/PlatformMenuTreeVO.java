package com.unique.examine.plat.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台菜单树返回对象。
 */
@Data
@Builder
@Schema(description = "平台菜单树返回对象")
public class PlatformMenuTreeVO {

    @Schema(description = "菜单 ID")
    private String menuId;

    @Schema(description = "父菜单 ID")
    private String parentId;

    @Schema(description = "菜单编码")
    private String code;

    @Schema(description = "菜单名称")
    private String name;

    @Schema(description = "前端路径")
    private String path;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "子菜单")
    private List<PlatformMenuTreeVO> children;
}
