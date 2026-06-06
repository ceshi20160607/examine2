package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("module")
@Schema(description = "module 表实体")
public class Module {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "app_id")
    @TableField("app_id")
    private Long appId;

    @Schema(description = "module_name")
    @TableField("module_name")
    private String moduleName;

    @Schema(description = "module_code")
    @TableField("module_code")
    private String moduleCode;

    @Schema(description = "module_type")
    @TableField("module_type")
    private String moduleType;

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
