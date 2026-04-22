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
 * record-连线（line；原 edge；关系表版）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_record_line")
@Schema(name = "FlowRecordLine对象", description = "record-连线（line；原 edge；关系表版）")
public class FlowRecordLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实例边ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_flow_record.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "起点 node_key")
    private String fromNodeKey;

    @Schema(description = "终点 node_key")
    private String toNodeKey;

    @Schema(description = "优先级（越小越优先）")
    private Integer priority;

    @Schema(description = "是否默认边：1=条件都不满足时默认走向（非异常兜底）")
    private Integer isDefault;

    @Schema(description = "状态：1=有效 2=作废")
    private Integer status;

    @Schema(description = "来源 temp_ver_id（追溯）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceTempVerId;

    @Schema(description = "来源 def_ver_edge.id（追溯）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceDefEdgeId;

    @Schema(description = "备注（可选）")
    private String remark;

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
