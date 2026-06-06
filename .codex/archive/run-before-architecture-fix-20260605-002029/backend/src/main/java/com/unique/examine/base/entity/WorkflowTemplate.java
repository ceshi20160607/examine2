package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("workflow_template")
@Schema(description = "workflow_template 表实体")
public class WorkflowTemplate {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "module_id")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "template_name")
    @TableField("template_name")
    private String templateName;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "current_version_id")
    @TableField("current_version_id")
    private Long currentVersionId;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
