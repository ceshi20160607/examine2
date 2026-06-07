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
 * 流程推进轨迹日志。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_trace_log")
@Schema(name = "TraceLog", description = "流程推进轨迹日志。")
public class TraceLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "流程实例 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;

    @Schema(description = "来源节点。")
    private String fromNodeKey;

    @Schema(description = "目标节点。")
    private String toNodeKey;

    @Schema(description = "START、ENTER_NODE、LEAVE_NODE、FINISH、CANCEL。")
    private String eventType;

    @Schema(description = "节点变量、候选人和条件命中快照。")
    private String eventSnapshotJson;

    @Schema(description = "请求追踪 ID。")
    private String requestId;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
