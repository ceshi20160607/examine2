package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("import_export_task")
@Schema(description = "import_export_task 表实体")
public class ImportExportTask {

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

    @Schema(description = "module_id")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "task_type")
    @TableField("task_type")
    private String taskType;

    @Schema(description = "template_id")
    @TableField("template_id")
    private Long templateId;

    @Schema(description = "task_status")
    @TableField("task_status")
    private String taskStatus;

    @Schema(description = "failure_reason")
    @TableField("failure_reason")
    private String failureReason;

    @Schema(description = "result_file_id")
    @TableField("result_file_id")
    private Long resultFileId;

    @Schema(description = "created_by")
    @TableField("created_by")
    private Long createdBy;

    @Schema(description = "completed_at")
    @TableField("completed_at")
    private LocalDateTime completedAt;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
