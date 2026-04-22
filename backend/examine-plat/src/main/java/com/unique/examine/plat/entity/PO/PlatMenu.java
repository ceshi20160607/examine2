package com.unique.examine.plat.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * <p>
 * 平台控制台菜单/权限项
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_menu")
@Schema(name = "PlatMenu对象", description = "平台控制台菜单/权限项")
public class PlatMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "菜单ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "父级菜单；0=根")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "菜单名称")
    private String menuName;

    @Schema(description = "1=目录 2=菜单 3=按钮")
    private Integer menuType;

    @Schema(description = "前端路由或标识")
    private String path;

    @Schema(description = "权限码（与接口/按钮一致）；目录可为空")
    private String permCode;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sortNo;

    @Schema(description = "1=显示 0=隐藏")
    private Integer visibleFlag;

    @Schema(description = "1=启用 2=停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
