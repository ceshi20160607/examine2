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
 * 系统管理菜单和运行菜单授权目录。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_system_menu")
@Schema(name = "SystemMenu", description = "系统管理菜单和运行菜单授权目录。")
public class SystemMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户级菜单归属；0 表示系统级。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "父菜单 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "菜单编码。")
    private String code;

    @Schema(description = "菜单名称。")
    private String name;

    @Schema(description = "菜单类型：ADMIN、RUNTIME、APP。")
    private String menuType;

    @Schema(description = "来源：SYSTEM、MODULE、PAGE、FLOW、OPENAPI。")
    private String sourceType;

    @Schema(description = "关联来源对象 ID，模块/页面由 DBA-003 设计。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceId;

    @Schema(description = "前端路由。")
    private String path;

    @Schema(description = "状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "层级。")
    private Integer depthLevel;

    @Schema(description = "菜单路径。")
    private String depthPath;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
