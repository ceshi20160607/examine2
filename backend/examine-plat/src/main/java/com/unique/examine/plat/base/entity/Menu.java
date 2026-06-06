package com.unique.examine.plat.base.entity;

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
 * 平台中心菜单树。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_menu")
@Schema(name = "Menu", description = "平台中心菜单树。")
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "父菜单 ID，0 表示根菜单。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "菜单编码，同父级唯一。")
    private String code;

    @Schema(description = "菜单名称。")
    private String name;

    @Schema(description = "前端路由或入口路径。")
    private String path;

    @Schema(description = "图标编码。")
    private String icon;

    @Schema(description = "菜单状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "菜单层级。")
    private Integer depthLevel;

    @Schema(description = "菜单路径，用于树查询。")
    private String depthPath;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
