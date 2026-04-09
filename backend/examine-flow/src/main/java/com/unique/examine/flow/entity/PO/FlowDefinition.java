package com.unique.examine.flow.entity.PO;

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
 * 流程定义
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_definition")
@Schema(name = "FlowDefinition对象", description = "流程定义")
public class FlowDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程定义ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId；无多租户时固定 0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "流程编码（同 system 内唯一）")
    private String defCode;

    @Schema(description = "流程名称")
    private String defName;

    @Schema(description = "分类编码（可选）")
    private String categoryCode;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

    @Schema(description = "最新发布版本号（0=未发布）")
    private Integer latestVersionNo;

    @Schema(description = "备注")
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
