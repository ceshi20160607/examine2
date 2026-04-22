package com.unique.examine.flow.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * <p>
 * 流程任务（待办/已办）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_task")
@Schema(name = "FlowTask对象", description = "流程任务（待办/已办）")
public class FlowTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "任务ID（待办/已办）")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "流程实例记录ID（un_flow_record.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "节点 node_key（流程图节点标识）")
    private String nodeKey;

    @Schema(description = "节点名称（冗余，可选）")
    private String nodeName;

    @Schema(description = "任务类型：approve|cc|custom")
    private String taskType;

    @Schema(description = "处理人 platId（或签时为具体处理人）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long assigneePlatId;

    @Schema(description = "候选人/候选规则（会签/或签时可用）")
    private String candidateJson;

    @Schema(description = "状态：1=待处理 2=已同意 3=已拒绝 4=已转交 5=已取消/跳过")
    private Integer status;

    @Schema(description = "到期时间（可选）")
    private LocalDateTime dueTime;

    @Schema(description = "领取时间（可选）")
    private LocalDateTime claimTime;

    @Schema(description = "完成时间（可选）")
    private LocalDateTime finishTime;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
