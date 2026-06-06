package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("role_permission")
@Schema(description = "role_permission 表实体")
public class RolePermission {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "role_id")
    @TableField("role_id")
    private Long roleId;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "resource_type")
    @TableField("resource_type")
    private String resourceType;

    @Schema(description = "resource_id")
    @TableField("resource_id")
    private Long resourceId;

    @Schema(description = "action_code")
    @TableField("action_code")
    private String actionCode;

    @Schema(description = "field_access")
    @TableField("field_access")
    private String fieldAccess;

    @Schema(description = "data_scope")
    @TableField("data_scope")
    private String dataScope;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
