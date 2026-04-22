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
 * temp_ver-全局设置（setting；异常兜底等）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_temp_ver_setting")
@Schema(name = "FlowTempVerSetting对象", description = "temp_ver-全局设置（setting；异常兜底等）")
public class FlowTempVerSetting implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本策略ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_flow_temp_ver.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tempVerId;

    @Schema(description = "异常模式：fallback_admin|end_record")
    private String exceptionMode;

    @Schema(description = "异常兜底审批人 platId（mode=fallback_admin 时必填）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long exceptionAdminPlatId;

    @Schema(description = "异常直接结束原因（可选）")
    private String exceptionEndReason;

    @Schema(description = "状态：1=有效 2=失效")
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
