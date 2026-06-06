package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("workflow_version")
@Schema(description = "workflow_version 表实体")
public class WorkflowVersion {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "template_id")
    @TableField("template_id")
    private Long templateId;

    @Schema(description = "version_no")
    @TableField("version_no")
    private Integer versionNo;

    @Schema(description = "node_json")
    @TableField("node_json")
    private String nodeJson;

    @Schema(description = "edge_json")
    @TableField("edge_json")
    private String edgeJson;

    @Schema(description = "condition_json")
    @TableField("condition_json")
    private String conditionJson;

    @Schema(description = "setting_json")
    @TableField("setting_json")
    private String settingJson;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "published_at")
    @TableField("published_at")
    private LocalDateTime publishedAt;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
