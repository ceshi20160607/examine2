package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("workflow_instance")
@Schema(description = "workflow_instance 表实体")
public class WorkflowInstance {

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

    @Schema(description = "record_id")
    @TableField("record_id")
    private Long recordId;

    @Schema(description = "template_id")
    @TableField("template_id")
    private Long templateId;

    @Schema(description = "version_id")
    @TableField("version_id")
    private Long versionId;

    @Schema(description = "started_by")
    @TableField("started_by")
    private Long startedBy;

    @Schema(description = "business_snapshot")
    @TableField("business_snapshot")
    private String businessSnapshot;

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
