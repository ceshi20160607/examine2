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
 * record-节点设置（setting；可覆盖模板/实例）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_record_node_setting")
@Schema(name = "FlowRecordNodeSetting对象", description = "record-节点设置（setting；可覆盖模板/实例）")
public class FlowRecordNodeSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实例节点策略ID")
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

    @Schema(description = "节点标识（同实例内）")
    private String nodeKey;

    @Schema(description = "异常模式：fallback_admin|end_record")
    private String exceptionMode;

    @Schema(description = "异常兜底审批人 platId（可选，覆盖实例全局）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long exceptionAdminPlatId;

    @Schema(description = "异常直接结束原因（可选）")
    private String exceptionEndReason;

    @Schema(description = "状态：1=有效 2=作废")
    private Integer status;

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
