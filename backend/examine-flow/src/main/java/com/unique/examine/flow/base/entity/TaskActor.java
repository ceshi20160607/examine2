package com.unique.examine.flow.base.entity;

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
 * 任务候选人、处理人和转交目标。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_task_actor")
@Schema(name = "TaskActor", description = "任务候选人、处理人和转交目标。")
public class TaskActor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "流程任务 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @Schema(description = "候选或处理成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorMemberId;

    @Schema(description = "CANDIDATE、CLAIMER、HANDLER、TRANSFER_TARGET。")
    private String actorType;

    @Schema(description = "来源，如角色、部门、成员、发起人。")
    private String sourceType;

    @Schema(description = "ACTIVE、INACTIVE。")
    private String status;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
