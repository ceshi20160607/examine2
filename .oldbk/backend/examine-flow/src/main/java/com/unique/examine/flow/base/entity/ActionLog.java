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
 * 审批动作日志。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_action_log")
@Schema(name = "ActionLog", description = "审批动作日志。")
public class ActionLog implements Serializable {

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

    @Schema(description = "流程任务 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @Schema(description = "APPROVE、REJECT、TRANSFER、RETURN、TERMINATE、WITHDRAW、CLAIM、UNCLAIM。")
    private String action;

    @Schema(description = "操作成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorMemberId;

    @Schema(description = "审批意见。")
    private String comment;

    @Schema(description = "来源节点。")
    private String fromNodeKey;

    @Schema(description = "目标节点。")
    private String toNodeKey;

    @Schema(description = "操作后的实例或任务状态。")
    private String resultStatus;

    @Schema(description = "请求追踪 ID。")
    private String requestId;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
