package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("audit_log")
@Schema(description = "audit_log 表实体")
public class AuditLog {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "actor_type")
    @TableField("actor_type")
    private String actorType;

    @Schema(description = "actor_id")
    @TableField("actor_id")
    private String actorId;

    @Schema(description = "action_type")
    @TableField("action_type")
    private String actionType;

    @Schema(description = "target_type")
    @TableField("target_type")
    private String targetType;

    @Schema(description = "target_id")
    @TableField("target_id")
    private String targetId;

    @Schema(description = "result")
    @TableField("result")
    private String result;

    @Schema(description = "trace_id")
    @TableField("trace_id")
    private String traceId;

    @Schema(description = "detail_json")
    @TableField("detail_json")
    private String detailJson;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
