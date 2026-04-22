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
 * temp_ver-连线条件（line_cond；关系表版）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_temp_ver_line_cond")
@Schema(name = "FlowTempVerLineCond对象", description = "temp_ver-连线条件（line_cond；关系表版）")
public class FlowTempVerLineCond implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本边条件ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "un_flow_temp_ver_line.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long lineId;

    @Schema(description = "分组号（用于 AND/OR 组合；语义由引擎固定）")
    private Integer groupNo;

    @Schema(description = "逻辑：AND|OR（MVP 可固定同组 AND、组间 OR）")
    private String logicOp;

    @Schema(description = "变量名（从 un_flow_record_var.var_key 取值）")
    private String leftVar;

    @Schema(description = "比较符：EQ|NE|GT|GE|LT|LE|IN|EXISTS")
    private String cmpOp;

    @Schema(description = "右值类型：string|number|bool|json|null")
    private String rightType;

    @Schema(description = "右值（字符串；按 right_type 解析）")
    private String rightValue;

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
