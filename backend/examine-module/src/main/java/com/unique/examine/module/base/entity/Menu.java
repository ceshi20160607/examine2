package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 运行菜单和模块入口配置。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_menu")
@Schema(name = "Menu", description = "运行菜单和模块入口配置。")
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "菜单 ID。")
    @TableId(value = "menu_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户；系统级菜单可为空。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "父菜单。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "应用入口。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "模块入口。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "菜单编码。")
    private String code;

    @Schema(description = "菜单名称。")
    private String name;

    @Schema(description = "菜单类型，如 CONFIG、RUNTIME。")
    private String menuType;

    @Schema(description = "前端路由。")
    private String routePath;

    @Schema(description = "图标。")
    private String icon;

    @Schema(description = "是否可见。")
    private Byte visibleFlag;

    @Schema(description = "是否启用。")
    private Byte enabledFlag;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "软删除唯一复用标记。")
    private String deleteMarker;

    @Schema(description = "创建成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @Schema(description = "更新成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
