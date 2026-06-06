package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("runtime_menu")
@Schema(description = "runtime_menu 表实体")
public class RuntimeMenu {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "parent_id")
    @TableField("parent_id")
    private Long parentId;

    @Schema(description = "app_id")
    @TableField("app_id")
    private Long appId;

    @Schema(description = "module_id")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "page_id")
    @TableField("page_id")
    private Long pageId;

    @Schema(description = "menu_name")
    @TableField("menu_name")
    private String menuName;

    @Schema(description = "menu_code")
    @TableField("menu_code")
    private String menuCode;

    @Schema(description = "permission_code")
    @TableField("permission_code")
    private String permissionCode;

    @Schema(description = "sort_order")
    @TableField("sort_order")
    private Integer sortOrder;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
