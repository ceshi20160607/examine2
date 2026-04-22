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
 * 流转轨迹日志（log_trace；原 instance_trace）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_log_trace")
@Schema(name = "FlowLogTrace对象", description = "流转轨迹日志（log_trace；原 instance_trace）")
public class FlowLogTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "轨迹ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "record ID（un_flow_record.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "事件类型：enter|leave|branch|join|end|error")
    private String eventType;

    @Schema(description = "来源节点 node_key（可选）")
    private String fromNodeKey;

    @Schema(description = "目标节点 node_key（可选）")
    private String toNodeKey;

    @Schema(description = "详情（JSON）")
    private String detailJson;

    @Schema(description = "事件时间")
    private LocalDateTime eventTime;

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
