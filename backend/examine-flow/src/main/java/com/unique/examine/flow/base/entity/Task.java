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
 * 待办任务、领取、处理和并发版本控制。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_task")
@Schema(name = "Task", description = "待办任务、领取、处理和并发版本控制。")
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "流程实例 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    @Schema(description = "当前节点编码。")
    private String nodeKey;

    @Schema(description = "任务名称。")
    private String taskName;

    @Schema(description = "PENDING、DONE、CANCELED、TRANSFERRED、RETURNED。")
    private String status;

    @Schema(description = "领取人系统成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long claimMemberId;

    @Schema(description = "实际处理人系统成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long handlerMemberId;

    @Schema(description = "到期时间。")
    private LocalDateTime dueAt;

    @Schema(description = "领取时间。")
    private LocalDateTime claimedAt;

    @Schema(description = "处理时间。")
    private LocalDateTime handledAt;

    @Schema(description = "任务并发控制版本。")
    private Integer taskVersion;

    @Schema(description = "最近一次处理幂等键。")
    private String idempotencyKey;

    @Schema(description = "最近一次处理 requestId。")
    private String requestId;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
