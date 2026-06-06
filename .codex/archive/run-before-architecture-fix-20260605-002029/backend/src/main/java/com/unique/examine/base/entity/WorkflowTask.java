package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("workflow_task")
@Schema(description = "workflow_task 表实体")
public class WorkflowTask {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "instance_id")
    @TableField("instance_id")
    private Long instanceId;

    @Schema(description = "task_status")
    @TableField("task_status")
    private String taskStatus;

    @Schema(description = "assignee_id")
    @TableField("assignee_id")
    private Long assigneeId;

    @Schema(description = "candidate_json")
    @TableField("candidate_json")
    private String candidateJson;

    @Schema(description = "comment")
    @TableField("comment")
    private String comment;

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
